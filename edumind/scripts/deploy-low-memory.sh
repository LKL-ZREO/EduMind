#!/bin/sh
set -eu

cd "$(dirname "$0")/.."

# The override is inherited by setup-ssl.sh and every nested Compose command.
export COMPOSE_FILE="docker-compose.yml:docker-compose.low-memory.yml"

/bin/sh scripts/preflight.sh

set -a
# shellcheck disable=SC1091
. ./.env
set +a

echo "Building the backend image..."
docker compose build app

echo "Building the frontend/Nginx image..."
docker compose build nginx

echo "Starting data services and backend..."
docker compose up -d --wait --wait-timeout 600 postgres redis minio app

echo "Configuring TLS and starting Nginx..."
/bin/sh scripts/setup-ssl.sh

echo "Starting database backups and certificate renewal..."
docker compose up -d pgbackup
docker compose --profile tls up -d certbot

docker compose ps
echo "Low-memory deployment completed: https://${DOMAIN}"
