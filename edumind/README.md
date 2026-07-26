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
│:3000     │
└──────────┘
```

本项目（docker-compose）自动启动：PostgreSQL、Redis、MinIO、Nginx、应用。

以下服务需要**另行启动**：

| 服务 | 说明 | 安装指南 |
|------|------|---------|
| **OneBot / NapCat** | QQ 机器人客户端，将 QQ 消息转发到 HTTP | 见下方 |

> **LLM 调用**：默认直连模型 API（Kimi 等），无需额外网关。也可通过 `edumind.llm.backend=openclaw` 切回 OpenClaw 网关。

---

## 🚀 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/LKL-ZREO/EduMind.git
cd EduMind/edumind
```

### 2. 配置环境变量

```bash
cp .env.example .env
```

编辑 `.env`，填入真实值：

```bash
DB_PASS=你的数据库密码
LIVE_SESSION_TOKEN_SECRET=$(openssl rand -base64 32)   # 学生课堂范围令牌
LLM_API_KEY=你的LLM API密钥（如 Kimi API Key）
ONEBOT_TOKEN=你的QQ机器人token
```

### 3. 启动基础设施

```bash
docker compose up -d
```

自动启动：PostgreSQL (+pgvector) + Redis + MinIO + Nginx + 应用。

### 4. （可选）使用 OpenClaw 网关

默认使用直连 LLM API。如需通过 OpenClaw 网关，在 `.env` 中设置：

```bash
EDUMIND_LLM_BACKEND=openclaw
OPENCLAW_API_KEY=你的OpenClaw API Key
OPENCLAW_BASE_URL=http://localhost:18789/v1
```

然后启动 OpenClaw 网关，确保能访问：

```bash
curl http://localhost:18789/v1/models
```

### 5. 启动 OneBot / NapCat（QQ 机器人）

1. 下载 [NapCat](https://github.com/NapNeko/NapCatQQ)
2. 配置 HTTP 服务端：
   - 地址：`http://127.0.0.1:3000`
   - Token：与 `.env` 中 `ONEBOT_TOKEN` 一致
3. 扫码登录 QQ

### 6. 导入数据库

Flyway 会在应用启动时自动执行迁移脚本，无需手动导入。

### 7. 开始使用

- **Web 前端**：打开 `http://localhost`（Vue 项目需单独启动，见下方）
- **QQ 群**：将机器人拉入群，@它提问
- **Swagger 文档**：`http://localhost:8080/swagger-ui.html`

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
| `DB_HOST` | 数据库地址 | `localhost` |
| `REDIS_HOST` | Redis 地址 | `localhost` |
| `REDIS_PORT` | Redis 端口 | `6379` |
| `LIVE_SESSION_TOKEN_SECRET` | 学生课堂范围令牌签名密钥（至少 32 字节） | **必填** |
| `LLM_API_KEY` | LLM API Key（直连模型） | **必填** |
| `LLM_BASE_URL` | LLM API 地址 | `https://api.moonshot.cn/v1` |
| `LLM_MODEL` | LLM 模型名 | `kimi-k2-0701-preview` |
| `EDUMIND_LLM_BACKEND` | LLM 后端模式（`built-in`/`openclaw`） | `built-in` |
| `OPENCLAW_API_KEY` | OpenClaw API Key（仅 openclaw 模式） | 可选 |
| `OPENCLAW_BASE_URL` | OpenClaw 地址（仅 openclaw 模式） | `http://localhost:18789/v1` |
| `ONEBOT_TOKEN` | QQ 机器人 Token | **必填** |
| `ONEBOT_HTTP_URL` | OneBot HTTP 地址 | `http://127.0.0.1:3000` |
| `STORAGE_TYPE` | 文件存储类型 | `local`（`s3` 用 MinIO） |
| `S3_ENDPOINT` | S3 端点 | `http://localhost:9000` |
| `S3_ACCESS_KEY` | S3 Access Key | `minioadmin` |
| `S3_SECRET_KEY` | S3 Secret Key | `minioadmin` |
| `S3_BUCKET` | S3 存储桶 | `homework-files` |
| `AI_MODEL_DIR` | ONNX 本地模型路径 | 下载 bge-reranker-base 的目录 |

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
├── docker-compose.yml      # 本地基础设施编排
├── Dockerfile              # 多阶段构建镜像
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
