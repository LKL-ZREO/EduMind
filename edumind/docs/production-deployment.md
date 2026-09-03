# EduMind 单机生产部署

本文适用于一台 Linux 服务器上的 Docker Compose 部署。Compose 会运行 React/Nginx、Spring Boot、PostgreSQL、Redis、MinIO 和备份任务；Prometheus/Grafana 与 Certbot 续期服务通过 profiles 管理。

## 1. 服务器前置条件

- 64 位 Linux，已安装 Docker Engine 和 Docker Compose plugin。
- 建议至少 4 核、8 GB 内存；低于 6 GB 时不要启用 observability profile，并配置足够的 swap。
- 至少 30 GB 可用磁盘，另行准备异机或对象存储备份空间。
- 域名的 A/AAAA 记录已经解析到服务器。
- 出站网络可以访问 Docker Hub、Maven、npm、模型下载源和配置的 LLM API。
- 云安全组和主机防火墙只公开 `80/tcp`、`443/tcp`，SSH 端口限制管理来源。

PostgreSQL、Redis、MinIO、Prometheus 和 Grafana 即使配置了宿主机端口，也只绑定 `127.0.0.1`。

## 2. 配置环境变量

```sh
cd EduMind/edumind
cp .env.example .env
chmod 600 .env
```

填入 `.env` 中所有 REQUIRED 项。生成随机密钥：

```sh
openssl rand -base64 48
openssl rand -base64 32
```

不要复用数据库、MinIO、Grafana、MCP 或 OneBot 的密码。`DOMAIN` 必须是不带协议和路径的域名，例如 `edu.example.com`。

默认模型路由为：文本与 Agent 调用通过 `LLM_BASE_URL`、`LLM_API_KEY` 使用 `deepseek-v4-flash`；图片和视觉 PDF 通过 `LLM_VISION_BASE_URL`、`LLM_VISION_API_KEY` 使用 `kimi-k2.5`。两套供应商 Key 都必须填写且不能复用。MCP `/mcp` 是可选的外部集成接口，不是 EduMind 自身运行的前置依赖。

如果部署 NapCat：

```dotenv
ONEBOT_WS_ENABLED=true
ONEBOT_WS_URL=ws://host.docker.internal:3001
ONEBOT_WS_TOKEN=<与 NapCat 一致的随机令牌>
```

Grafana 使用宿主机 `3002`，不会再与 NapCat 的 `3001` 冲突。

## 3. Reranker 模型

将模型放到：

```text
edumind/models/bge-reranker-base/
├── onnx/model.onnx
├── tokenizer.json
└── 其他 tokenizer 配置文件
```

或通过 `RERANKER_MODEL_DIR` 指定其他 Linux 宿主机目录。没有模型时部署仍可启动，但会禁用精排。Embedding 模型会在首次启动时下载并缓存在 `djl_models` Docker volume。

## 4. 部署

先运行只读预检。它检查变量、占位密码、模型端点格式、DNS、Docker、内存和 Compose 配置，但不会打印秘密：

```sh
/bin/sh scripts/preflight.sh
```

随后执行：

```sh
/bin/sh scripts/deploy.sh
```

部署顺序为：

1. 构建 Spring Boot 和 React/Nginx 镜像。
2. 启动 PostgreSQL、Redis、MinIO 和应用并等待健康。
3. 用 HTTP bootstrap Nginx 完成 ACME challenge。
4. 申请或复用证书并重启 Nginx 切换 HTTPS。
5. 启动 PostgreSQL 备份和 Certbot 续期循环。

首次调试证书流程时可在 `.env` 设置 `CERTBOT_STAGING=true`。验证成功后改回 `false` 并重新运行 `setup-ssl.sh`，不要长期使用 staging 证书。

## 5. 验证

```sh
docker compose ps
curl -fsS "https://${DOMAIN}/health"
curl -I "https://${DOMAIN}/"
docker compose logs --tail=100 app nginx
```

浏览器还应验证：

- 登录、注册与 Session Cookie。
- 文件上传大于 1 MB 的作业。
- AI SSE 流式回复。
- `/ws/live` 的课堂实时连接。
- 使用 `X-MCP-API-Key` 调用 `/mcp`。

## 6. 监控

```sh
docker compose --profile observability up -d prometheus grafana
```

两者只绑定本机。通过 SSH 隧道访问：

```sh
ssh -L 3002:127.0.0.1:3002 -L 9090:127.0.0.1:9090 user@server
```

- Grafana：`http://127.0.0.1:3002`
- Prometheus：`http://127.0.0.1:9090`

## 7. 备份与恢复演练

手动生成一次可验证的 PostgreSQL custom-format 备份：

```sh
docker compose exec pgbackup /bin/sh /backup.sh
docker compose exec pgbackup sh -c 'ls -lh /backups && pg_restore --list /backups/*.dump >/dev/null'
```

命名卷中的备份仍与主数据库位于同一服务器，必须定期复制到异机或对象存储。至少每月在独立测试数据库执行一次恢复演练：

```sh
docker compose exec postgres createdb -U postgres edumind_restore_test
docker compose exec pgbackup pg_restore \
  -h postgres -U postgres -d edumind_restore_test --clean --if-exists /backups/<backup>.dump
docker compose exec postgres dropdb -U postgres edumind_restore_test
```

不要对生产数据库直接运行示例恢复命令。

## 8. 更新与回滚

更新代码后：

```sh
git pull --ff-only
/bin/sh scripts/preflight.sh
docker compose build app nginx
docker compose up -d --wait --wait-timeout 600 app nginx
```

更新前记录当前 Git SHA 和镜像标签。数据库迁移由 Flyway 自动执行；包含不可逆迁移时，应先验证备份并制定对应回滚方案。

## 9. 常用排障

```sh
docker compose ps
docker compose logs -f --tail=200 app
docker compose logs -f --tail=200 nginx
docker compose exec app wget -qO- http://localhost:8080/actuator/health
docker compose exec postgres pg_isready -U postgres -d postgres
docker compose exec redis redis-cli ping
```

如果 Nginx 仍使用 HTTP bootstrap，确认：

```sh
docker compose --profile tls run --rm --entrypoint certbot certbot certificates
docker compose restart nginx
```

如果 Docker Hub 下载过慢，应先为服务器配置可信镜像加速或在可联网环境构建镜像后推送到私有镜像仓库。
如果 Maven Central 较慢，可通过 `.env` 中的 `MAVEN_MIRROR_URL` 指向可信的区域镜像；该设置只影响后端 Docker 构建。
