#!/bin/sh
set -eu

DOMAIN="${DOMAIN:-localhost}"
CERT_DIR="/etc/letsencrypt/live/${DOMAIN}"

if [ -s "${CERT_DIR}/fullchain.pem" ] && [ -s "${CERT_DIR}/privkey.pem" ]; then
    TEMPLATE="/etc/nginx/edumind/nginx.https.conf.template"
    echo "Using HTTPS configuration for ${DOMAIN}"
else
    TEMPLATE="/etc/nginx/edumind/nginx.http.conf.template"
    echo "TLS certificate not found for ${DOMAIN}; using HTTP bootstrap configuration"
fi

envsubst '${DOMAIN}' < "${TEMPLATE}" > /etc/nginx/nginx.conf
nginx -t

reload_nginx_periodically() {
    while sleep 6h; do
        if nginx -t; then
            nginx -s reload
        fi
    done
}

reload_nginx_periodically &
exec nginx -g 'daemon off;'
