#!/bin/sh
set -eu

cd "$(dirname "$0")/.."

/bin/sh scripts/preflight.sh

set -a
# shellcheck disable=SC1091
. ./.env
set +a

echo "Building backend and frontend images..."
docker compose build app nginx

echo "Starting data services and backend..."
docker compose up -d --wait --wait-timeout 600 postgres redis minio app

echo "Configuring TLS and starting Nginx..."
/bin/sh scripts/setup-ssl.sh

echo "Starting backup and certificate renewal services..."
docker compose up -d pgbackup
docker compose --profile tls up -d certbot

if [ "${ENABLE_OBSERVABILITY:-false}" = "true" ]; then
    echo "Starting Prometheus and Grafana..."
    docker compose --profile observability up -d prometheus grafana
fi

docker compose ps
echo "Deployment completed: https://${DOMAIN}"
