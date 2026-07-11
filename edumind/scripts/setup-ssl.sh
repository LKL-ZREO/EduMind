#!/bin/bash
# ==============================================================================
# Let's Encrypt SSL 证书首次获取脚本
# ==============================================================================
# 前置条件：
#   1. 域名 DNS 已解析到当前服务器 IP
#   2. 服务器 80 端口可从公网访问（Let's Encrypt HTTP 验证需要）
#   3. .env 文件中已配置 DOMAIN 和 CERTBOT_EMAIL
#
# 使用方法：
#   chmod +x scripts/setup-ssl.sh
#   ./scripts/setup-ssl.sh
# ==============================================================================

set -e

cd "$(dirname "$0")/.."

# 加载 .env
if [ -f .env ]; then
    export $(grep -v '^#' .env | xargs)
fi

if [ -z "${DOMAIN}" ] || [ "${DOMAIN}" = "localhost" ]; then
    echo "错误: 请先在 .env 文件中设置 DOMAIN=你的真实域名"
    echo "例如: DOMAIN=edumind.example.com"
    exit 1
fi

echo "=========================================="
echo "  为 ${DOMAIN} 申请 Let's Encrypt SSL 证书"
echo "=========================================="

# 第一步：仅启动 Nginx（HTTP 验证模式）
echo "[1/3] 启动 Nginx（HTTP 验证）..."
docker compose up -d nginx
sleep 3

# 第二步：用 webroot 模式获取证书
# certbot 容器共享 named volume，证书写入 certbot_conf 卷
echo "[2/3] 申请证书（域名为 ${DOMAIN}）..."
docker compose run --rm certbot certonly \
    --webroot \
    --webroot-path=/var/www/certbot \
    --email "${CERTBOT_EMAIL:-admin@${DOMAIN}}" \
    --agree-tos \
    --no-eff-email \
    -d "${DOMAIN}"

# 第三步：重载 Nginx 使 HTTPS 生效
echo "[3/3] 重载 Nginx（启用 HTTPS）..."
docker compose exec nginx nginx -s reload

echo ""
echo "=========================================="
echo "  ✓ SSL 证书配置完成！"
echo "  https://${DOMAIN}"
echo "=========================================="
echo ""
echo "后续自动续期："
echo "  - certbot 容器每 12 小时自动检查"
echo "  - 手动测试续期：docker compose exec certbot certbot renew --dry-run"
