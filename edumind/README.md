# 🎓 EduMind — AI 驱动的智能教学助手

一站式 AI 教学平台，连接教师与学生。支持多课程知识库 RAG 检索、C 语言作业自动批改、QQ 机器人答疑。

## 🏗 系统架构

```
┌──────────────────────────────────────────────────────┐
│                    用户入口                            │
│   Web 前端 (Vue 3)  │  QQ 群 (OneBot)  │  MCP 工具    │
└────────┬─────────────────────┬───────────────────────┘
         │                     │
         ▼                     ▼
┌──────────────────────────────────────────────────────┐
│                  Nginx (负载均衡)                      │
└─────────────────────┬────────────────────────────────┘
                      │
                      ▼
┌──────────────────────────────────────────────────────┐
│              Spring Boot 应用                         │
│   Chat │ RAG检索 │ 作业批改 │ Dashboard │ 课程管理     │
└──┬────────┬──────────┬──────────┬───────────────────┘
   │        │          │          │
   ▼        ▼          ▼          ▼
┌──────┐ ┌──────┐ ┌────────┐
│PG +  │ │Redis │ │MinIO   │
│pgvec │ │Stream│ │(S3)    │
│tor   │ │      │ │        │
└──────┘ └──────┘ └────────┘
┌──────────┐
│ OneBot   │
│ (NapCat) │
│127.0.0.1 │
│:3001     │
└──────────┘
```

Docker Compose 默认启动 PostgreSQL、Redis、MinIO、应用、Nginx、备份任务。Vue 前端会在 Nginx 镜像中完成生产构建，不需要在服务器额外运行 Vite。

以下服务需要**另行启动**：

| 服务 | 说明 | 安装指南 |
|------|------|---------|
| **OneBot / NapCat** | QQ 机器人客户端，通过 WebSocket 与应用双向通信 | 见下方 |

> **LLM 调用**：应用使用内置 LangChain4j Agent，直接连接 `.env` 中配置的 OpenAI 兼容模型端点，不依赖 OpenClaw。

---

## 🚀 本地快速开始

### 1. 克隆项目

```bash
git clone https://github.com/LKL-ZREO/EduMind.git
cd EduMind/edumind
```

### 2. 配置环境变量

```bash
cp .env.example .env
```

编辑 `.env`，至少填入所有标记为 REQUIRED 的值。可使用以下命令生成随机密钥：

```bash
openssl rand -base64 48  # LIVE_SESSION_TOKEN_SECRET / MCP_API_KEY
openssl rand -base64 32  # ENCRYPT_AES_KEY
```

本地开发可以设置 `DOMAIN=localhost`，Nginx 会自动使用 HTTP 配置。

### 3. 构建并启动

```bash
docker compose up -d --build
```

访问 `http://localhost`。PostgreSQL、Redis 和 MinIO 端口只绑定 `127.0.0.1`，可供本机开发工具连接，但不会监听公网网卡。

### 4. （可选）启动监控

```sh
docker compose --profile observability up -d prometheus grafana
```

Prometheus 和 Grafana 分别只绑定本机 `9090` 和 `3002`，建议通过 SSH 隧道访问。

### 5. （可选）启动 OneBot / NapCat

1. 下载 [NapCat](https://github.com/NapNeko/NapCatQQ)
2. 在 NapCat WebUI 的网络配置中，新建并启用 **WebSocket 服务端（正向 WS）**：
   - 监听地址：`0.0.0.0`
   - 端口：`3001`
   - Access Token：与 `.env` 中 `ONEBOT_WS_TOKEN` 一致
3. 在 `.env` 中设置 `ONEBOT_WS_ENABLED=true` 和相同的 `ONEBOT_WS_TOKEN`
4. 扫码登录 QQ，并重启应用：`docker compose up -d app`

### 6. 导入数据库

Flyway 会在应用启动时自动执行迁移脚本，无需手动导入。

### 7. 开始使用

- **Web 前端**：打开 `http://localhost`
- **QQ 群**：将机器人拉入群，@它提问

## 🌐 单机服务器部署

服务器需要提前完成域名 DNS 解析，并只向公网开放 `80`、`443` 和受限的 SSH 端口。填好 `.env` 后执行：

```sh
/bin/sh scripts/preflight.sh
/bin/sh scripts/deploy.sh
```

部署脚本会依次构建前后端、等待数据服务和应用健康、申请 Let's Encrypt 证书、切换 HTTPS，并启动备份与证书续期服务。完整部署、更新、备份恢复和排障步骤见 [生产部署指南](docs/production-deployment.md)。

---

## 🧩 前端开发

```bash
cd vue-project
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`。

---

## 📦 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `DB_PASS` | 数据库密码 | **必填** |
| `DB_USER` | 数据库用户 | `postgres` |
| `REDIS_PORT` | Redis 端口 | `6379` |
| `LIVE_SESSION_TOKEN_SECRET` | 学生课堂范围令牌签名密钥（至少 32 字节） | **必填** |
| `ENCRYPT_AES_KEY` | PII 数据加密密钥（至少 32 字节） | **必填** |
| `MCP_API_KEY` | MCP 服务间认证密钥（至少 32 字节） | **必填** |
| `LLM_BASE_URL` | OpenAI 兼容模型端点 | `https://api.moonshot.cn/v1` |
| `LLM_API_KEY` | 模型供应商 API Key | **必填** |
| `LLM_VISION_BASE_URL` | 可选的视觉模型端点；留空复用文本端点 | 可选 |
| `LLM_VISION_API_KEY` | 可选的视觉模型 API Key；留空复用文本 Key | 可选 |
| `LLM_MODEL` | LLM 模型名 | `kimi-k2.5` |
| `ONEBOT_WS_ENABLED` | 是否连接 NapCat | `false` |
| `ONEBOT_WS_URL` | OneBot WebSocket 地址 | Compose：`ws://host.docker.internal:3001`；本地直跑：`ws://127.0.0.1:3001` |
| `ONEBOT_WS_TOKEN` | OneBot WebSocket Access Token | 启用 OneBot 时必填 |
| `STORAGE_TYPE` | 文件存储类型 | `local`（`s3` 用 MinIO） |
| `S3_ACCESS_KEY` | MinIO/S3 Access Key | **必填** |
| `S3_SECRET_KEY` | MinIO/S3 Secret Key | **必填** |
| `S3_BUCKET` | S3 存储桶 | `homework-files` |
| `RERANKER_MODEL_DIR` | 宿主机上的 bge-reranker-base 目录 | `./models/bge-reranker-base` |
| `GRAFANA_PASSWORD` | Grafana 管理员密码 | **必填** |

---

## 🔧 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Spring Boot 4 + Java 21 + 虚拟线程 |
| AI 框架 | DJL + ONNX Runtime（本地 Embedding / Reranker） |
| 数据库 | PostgreSQL 16 + pgvector 向量检索 |
| 缓存 | Redis 7（Stream / 分布式锁 / 缓存） |
| 文件存储 | MinIO（S3 兼容）/ 本地磁盘 |
| 前端 | Vue 3 + TypeScript + Vite |
| 部署 | Docker Compose + Nginx 反向代理 |

## 📂 项目结构

```
edumind/
├── src/main/java/com/firedemo/demo/
│   ├── agent/workflow/     # Agent 工作流引擎
│   ├── common/
│   │   ├── ai/             # 结构化输出调用器
│   │   ├── async/          # Redis Stream 消费者框架
│   │   ├── cache/          # Cache-Aside / 一致性服务
│   │   ├── limiter/        # 令牌桶限流
│   │   └── prompt/         # Prompt 模板加载器
│   ├── config/             # Spring 配置
│   ├── Controller/         # REST API
│   ├── DTO/                # 数据传输对象
│   ├── Entity/             # 实体类
│   ├── mapper/             # MyBatis-Plus Mapper
│   ├── mcp/                # MCP JSON-RPC 端点 + Tool Calling
│   ├── rag/                # RAG 检索管线（核心）
│   │   ├── EmbeddingService    # ONNX 文本嵌入
│   │   ├── VectorStoreService  # pgvector 向量存储
│   │   ├── RrfFusionService    # RRF 多路融合
│   │   ├── RerankerService     # Cross-Encoder 精排
│   │   ├── QueryRewriter       # LLM Query 改写
│   │   ├── SmartChunkService   # 文档智能切割
│   │   └── RagService          # 统一检索入口
│   ├── Service/            # 业务服务
│   └── utils/              # 工具类与 Session 请求过滤器
├── src/main/resources/
│   ├── db/migration/       # Flyway 数据库迁移脚本
│   ├── prompts/            # LLM Prompt 模板
│   └── application.properties
├── docker-compose.yml      # 本地与单机生产编排
├── Dockerfile              # 后端多阶段构建镜像
├── Dockerfile.nginx        # 前端构建 + Nginx 运行镜像
├── nginx*.conf.template    # HTTP 引导与 HTTPS 配置
├── scripts/
│   ├── preflight.sh        # 生产部署预检
│   ├── deploy.sh           # 单机部署入口
│   ├── setup-ssl.sh        # 首次签发/复用证书
│   └── backup.sh           # PostgreSQL 可验证备份
└── pom.xml
vue-project/                # Vue 3 前端
```

---

## 🧪 特性

- **RAG 知识库检索**：Embedding → pgvector 向量 + 关键词双路 → RRF 融合 → Reranker 精排
- **Agentic RAG**：LLM 自主决定何时调用 searchKnowledge 工具，无需人工规则
- **作业自动批改**：Redis Stream 异步处理，分布式锁防重复，结构化 JSON 输出
- **QQ 机器人答疑**：OneBot 协议，支持私聊 + 群聊
- **多课程多班级**：每个班级独立 Prompt 和知识库范围
- **共享知识库**：多人协作，邀请 token 加入
- **Dashboard 热力图**：知识点掌握度可视化，交互式错误聚合
- **RAG 评估框架**：faithfulness / relevance 自动打分

## 📄 License

MIT
