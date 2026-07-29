#!/bin/sh
set -eu

cd "$(dirname "$0")/.."

fail() {
    echo "ERROR: $*" >&2
    exit 1
}

warn() {
    echo "WARN: $*" >&2
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "required command not found: $1"
}

require_value() {
    KEY="$1"
    MIN_LENGTH="$2"
    eval "VALUE=\${${KEY}:-}"
    [ -n "${VALUE}" ] || fail "${KEY} is empty"
    [ "${#VALUE}" -ge "${MIN_LENGTH}" ] || fail "${KEY} must be at least ${MIN_LENGTH} characters"
    LOWER_VALUE="$(printf '%s' "${VALUE}" | tr '[:upper:]' '[:lower:]')"
    case "${LOWER_VALUE}" in
        *change-me*|*minioadmin*|*example.com*|password|admin)
            fail "${KEY} still contains a development placeholder"
            ;;
    esac
}

require_command docker
require_command curl

docker info >/dev/null 2>&1 || fail "Docker daemon is not available"
docker compose version >/dev/null 2>&1 || fail "Docker Compose plugin is not available"

[ -f .env ] || fail "edumind/.env does not exist; copy .env.example first"

set -a
# shellcheck disable=SC1091
. ./.env
set +a

[ -n "${DOMAIN:-}" ] || fail "DOMAIN is empty"
[ "${DOMAIN}" != "localhost" ] || fail "DOMAIN must be a public domain name"
case "${DOMAIN}" in
    *.*) ;;
    *) fail "DOMAIN does not look like a fully qualified domain name" ;;
esac

case "${CERTBOT_EMAIL:-}" in
    *@*.*) ;;
    *) fail "CERTBOT_EMAIL is invalid" ;;
esac

require_value DB_PASS 16
require_value LIVE_SESSION_TOKEN_SECRET 32
require_value ENCRYPT_AES_KEY 32
require_value LLM_API_KEY 8
require_value LLM_VISION_API_KEY 8
require_value MCP_API_KEY 32
require_value S3_ACCESS_KEY 3
require_value S3_SECRET_KEY 16
require_value GRAFANA_PASSWORD 12

case "${LLM_BASE_URL:-https://api.deepseek.com}" in
    http://*|https://*) ;;
    *) fail "LLM_BASE_URL must start with http:// or https://" ;;
esac

if [ -n "${LLM_VISION_BASE_URL:-}" ]; then
    case "${LLM_VISION_BASE_URL}" in
        http://*|https://*) ;;
        *) fail "LLM_VISION_BASE_URL must start with http:// or https://" ;;
    esac
fi

if [ "${ONEBOT_WS_ENABLED:-false}" = "true" ]; then
    require_value ONEBOT_WS_TOKEN 16
fi

RERANKER_DIR="${RERANKER_MODEL_DIR:-./models/bge-reranker-base}"
if [ ! -f "${RERANKER_DIR}/onnx/model.onnx" ]; then
    warn "reranker model not found at ${RERANKER_DIR}/onnx/model.onnx; reranking will be disabled"
fi

if command -v getent >/dev/null 2>&1 && ! getent ahosts "${DOMAIN}" >/dev/null 2>&1; then
    fail "${DOMAIN} does not resolve from this server"
fi

if [ -r /proc/meminfo ]; then
    MEMORY_KB="$(awk '/MemTotal/ {print $2}' /proc/meminfo)"
    if [ -n "${MEMORY_KB}" ] && [ "${MEMORY_KB}" -lt 6000000 ]; then
        warn "less than 6 GB RAM detected; disable observability or add swap before deployment"
    fi
fi

docker compose config --quiet

echo "Preflight passed. Secrets were validated but not printed."
