# 阿里云新加坡 2 核 4 GB 部署指南

本文面向 Ubuntu 24.04 轻量应用服务器，目标是验证 EduMind 的公网功能与安全配置。该方案不启动 MinerU、OneBot、Prometheus 或 Grafana。

## 1. 阿里云控制台设置

在轻量应用服务器防火墙中只放行：

| 端口 | 用途 | 来源 |
| --- | --- | --- |
| `22/tcp` | SSH | 优先限制为自己的公网 IP |
| `80/tcp` | HTTP 与证书签发 | 全部 IPv4 |
| `443/tcp` | HTTPS | 全部 IPv4 |

不要放行 `5432`、`6379`、`9000`、`9001`、`9090` 或 `3002`。

复制服务器公网 IPv4。为域名添加一条 `A` 记录并指向该地址，等待解析生效：

```sh
nslookup edu.example.com
```

## 2. 登录并获取代码

在本机执行：

```sh
ssh root@<服务器公网IP>
```

在服务器执行：

```sh
git clone https://github.com/LKL-ZREO/EduMind.git
cd EduMind/edumind
```

如果仓库尚未包含本次新增的部署文件，应先在本机提交并推送，再在服务器克隆或拉取对应分支。不要把 `.env` 提交到 Git。

## 3. 初始化 Ubuntu

下面的脚本安装 Docker 官方版本和 Compose plugin，并创建 4 GiB Swap：

```sh
chmod +x scripts/*.sh
sudo /bin/sh scripts/prepare-ubuntu.sh
```

确认结果中同时存在约 4 GiB 内存和 4 GiB Swap：

```sh
free -h
docker compose version
```

## 4. 配置生产变量

```sh
cp .env.example .env
chmod 600 .env
nano .env
```

至少完成以下配置：

```dotenv
DOMAIN=edu.example.com
CERTBOT_EMAIL=your-email@example.com
CERTBOT_STAGING=false

DB_PASS=<至少16字符的独立随机密码>
LIVE_SESSION_TOKEN_SECRET=<随机密钥>
ENCRYPT_AES_KEY=<随机密钥>
MCP_API_KEY=<随机密钥>

LLM_API_KEY=<文本模型API Key>
LLM_VISION_API_KEY=<视觉模型API Key>

STORAGE_TYPE=local
S3_ACCESS_KEY=<独立随机用户名>
S3_SECRET_KEY=<至少16字符的独立随机密码>

ENABLE_OBSERVABILITY=false
GRAFANA_PASSWORD=<至少12字符的独立随机密码>
ONEBOT_WS_ENABLED=false
```

可在服务器上生成密钥：

```sh
openssl rand -base64 48
openssl rand -base64 32
```

每次生成的值只用于一个变量，不要复用。虽然当前不启动 Grafana 和 MinIO 控制台不对公网开放，预检仍要求设置对应密码。

确认域名已经解析到本机公网 IP 后使用 `CERTBOT_STAGING=false` 直接申请正式证书。只有反复调试证书流程时才使用 staging；staging 证书不受浏览器信任，不能用于最终验收。

## 5. 预检和部署

```sh
/bin/sh scripts/preflight.sh
/bin/sh scripts/deploy-low-memory.sh
```

首次构建需要下载 Maven、npm、Docker 和 Embedding 模型依赖，耗时取决于服务器网络。部署脚本串行构建前后端，以降低 4 GB 主机上的内存峰值。

## 6. 上线验收

```sh
export COMPOSE_FILE=docker-compose.yml:docker-compose.low-memory.yml
docker compose ps
docker compose logs --tail=100 app nginx
curl -fsS "https://${DOMAIN}/health"
free -h
df -h /
```

然后在浏览器逐项验证：

1. 注册、登录、退出和刷新后的登录状态。
2. AI SSE 流式回复和模型调用失败提示。
3. 小文件及大于 1 MB 文件上传。
4. 教师课堂和学生课堂 WebSocket 连接。
5. 未登录用户不能访问教师资源，普通用户不能读取其他用户资源。
6. HTTPS 生效，HTTP 自动跳转 HTTPS。

## 7. 日常操作

所有操作都应带低内存覆盖文件：

```sh
export COMPOSE_FILE=docker-compose.yml:docker-compose.low-memory.yml
docker compose ps
docker compose logs -f --tail=200 app
docker compose restart app
```

更新代码：

```sh
git pull --ff-only
/bin/sh scripts/deploy-low-memory.sh
```

备份仍位于同一台服务器。功能验证阶段至少手动验证一次备份：

```sh
export COMPOSE_FILE=docker-compose.yml:docker-compose.low-memory.yml
docker compose exec pgbackup /bin/sh /backup.sh
docker compose exec pgbackup sh -c 'ls -lh /backups && pg_restore --list /backups/*.dump >/dev/null'
```

## 8. 资源边界

- Java 堆限制为 1 GiB，应用容器总内存限制为 1900 MiB。
- 数据库连接池缩小为最多 10 个连接、最少 2 个空闲连接。
- PostgreSQL 使用适合小内存主机的基础参数。
- 不要在该规格上启动 `observability` profile 或 MinerU。
- 若持续发生 Swap 使用量增长、容器重启或 OOM，应升级到 8 GB，而不是继续提高 Swap。
