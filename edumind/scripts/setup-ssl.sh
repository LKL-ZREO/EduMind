#!/bin/sh
set -eu

cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
    echo "ERROR: edumind/.env does not exist. Copy .env.example and fill it first." >&2
    exit 1
fi

set -a
# shellcheck disable=SC1091
. ./.env
set +a

if [ -z "${DOMAIN:-}" ] || [ "${DOMAIN}" = "localhost" ]; then
    echo "ERROR: DOMAIN must be a public domain name, not localhost." >&2
    exit 1
fi

if [ -z "${CERTBOT_EMAIL:-}" ]; then
    echo "ERROR: CERTBOT_EMAIL is required." >&2
    exit 1
fi

if command -v getent >/dev/null 2>&1 && ! getent ahosts "${DOMAIN}" >/dev/null 2>&1; then
    echo "ERROR: ${DOMAIN} does not resolve from this server." >&2
    exit 1
fi

echo "Starting HTTP bootstrap for ${DOMAIN}..."
docker compose up -d --build --no-deps nginx

echo "Requesting or reusing the Let's Encrypt certificate..."
STAGING_ARG=""
if [ "${CERTBOT_STAGING:-false}" = "true" ]; then
    STAGING_ARG="--staging"
fi

# Word splitting for STAGING_ARG is intentional: it is either empty or --staging.
# shellcheck disable=SC2086
docker compose --profile tls run --rm --no-deps --entrypoint certbot certbot \
    certonly \
    --webroot \
    --webroot-path=/var/www/certbot \
    --email "${CERTBOT_EMAIL}" \
    --agree-tos \
    --no-eff-email \
    --non-interactive \
    --keep-until-expiring \
    ${STAGING_ARG} \
    -d "${DOMAIN}"

echo "Restarting Nginx with the HTTPS configuration..."
docker compose restart nginx
docker compose --profile tls up -d certbot

if [ "${CERTBOT_STAGING:-false}" != "true" ]; then
    ATTEMPT=0
    until curl --fail --silent --show-error "https://${DOMAIN}/health" >/dev/null; do
        ATTEMPT=$((ATTEMPT + 1))
        if [ "${ATTEMPT}" -ge 30 ]; then
            echo "ERROR: HTTPS health check did not become ready." >&2
            exit 1
        fi
        sleep 2
    done
fi

echo "TLS is ready: https://${DOMAIN}"
