<p align="center">
  <h1 align="center">🎓 EduMind — AI 驱动的智能教学助手</h1>
  <p align="center">
    <img src="https://img.shields.io/badge/Spring_Boot-4.0.4-brightgreen" alt="Spring Boot">
    <img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21">
    <img src="https://img.shields.io/badge/Vue-3.5-blue" alt="Vue 3">
    <img src="https://img.shields.io/badge/license-MIT-green" alt="License">
  </p>
</p>

EduMind 是一个面向高校教学场景的 AI 智能助手，核心功能包括 **RAG 知识库检索**、**作业自动批改**、**教学数据仪表盘** 和 **QQ Bot 答疑**。基于 Spring Boot 4 + Vue 3 构建，内置 ONNX 本地推理、pgvector 向量检索、MCP 工具系统和 Redis Stream 异步批改引擎。

---

## ✨ 核心功能

| 模块 | 说明 |
|------|------|
| **RAG 知识库检索** | Embedding → pgvector 向量检索 → 关键词检索 → RRF 融合 → Reranker 精排，支持 LLM 查询改写和低置信度兜底 |
| **作业自动批改** | AI 驱动的作业批改，DAG 工作流引擎，Redis Stream 异步消费，支持逾期扣分 |
| **教学数据仪表盘** | 班级成绩分布热力图、知识点掌握度分析、高频错题统计、学生成长曲线 |
| **QQ Bot 答疑** | 基于 OneBot 协议的 QQ 机器人，学生在群里 @机器人 即可获得 RAG 检索结果 |
| **MCP 工具系统** | 实现 Model Context Protocol，暴露后端数据给 OpenClaw 的 AI Agent 调用 |
| **智能文档切割** | Markdown 结构感知切割、代码块保护、对话轮次识别、滑动窗口上下文拼接 |

---

## 🏗️ 技术架构

```
┌─────────────────────────────────────────────────────────────┐
│                       Nginx :80                             │
│              (反向代理 + 安全头 + Gzip)                       │
└──────────────────────────┬──────────────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        ▼                  ▼                  ▼
┌───────────────┐  ┌──────────────┐  ┌───────────────┐
│   Vue 3 前端   │  │ Spring Boot  │  │  Prometheus   │
│   Vite 7      │  │  4.0.4 :8080 │  │  Grafana      │
│   Element Plus│  │  虚拟线程     │  │  监控面板      │
└───────────────┘  └──────┬───────┘  └───────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        ▼                  ▼                  ▼
┌───────────┐  ┌───────────────┐  ┌───────────────────┐
│ PostgreSQL │  │    Redis 7    │  │  MinIO (S3)       │
│ + pgvector │  │  Stream/Cache │  │  对象存储          │
└───────────┘  └───────────────┘  └───────────────────┘
```

### RAG 检索管线

```
Query → Embedding(ONNX) → pgvector相似度  ─┐
                                            ├→ RRF融合 → Reranker精排 → 结果
        → LLM改写 → 关键词全文检索 ─────────┘
                                            ↓
                              低置信度(<0.3) → 追加检索
```

### 技术栈一览

| 层级 | 技术 | 版本 |
|------|------|------|
| 运行时 | Java + Spring Boot | 21 + 4.0.4 |
| 框架 | MyBatis-Plus + Spring Security | 3.5.15 |
| 数据库 | PostgreSQL + pgvector | 16 |
| 缓存 | Caffeine + Redis (Redisson) | 7 |
| AI/LLM | LangChain4j + OpenClaw Gateway | 1.13.0 |
| 本地推理 | DJL + ONNX Runtime | 0.28.0 |
| 文档解析 | Apache Tika + PDFBox | 2.9.1 / 3.0.4 |
| 限流熔断 | Bucket4j + Resilience4j | 8.10.1 / 2.3.0 |
| 前端 | Vue 3 + Vite + Element Plus + ECharts | 3.5 / 7 / 2.13 / 6.0 |
| 监控 | Prometheus + Grafana + Micrometer | 2.55 / 11.2 |
| CI/CD | GitHub Actions | — |

---

## 🚀 快速开始

### 前置条件

- JDK 21
- Node.js >= 20
- Docker Desktop
- [OpenClaw](https://github.com/nicepkg/openclaw)（AI Gateway，LLM API 管理）

### 1. 配置环境变量

```bash
cd edumind
cp .env.example .env
# 编辑 .env，填入 DB_PASS、JWT_SECRET、OPENCLAW_API_KEY 等
```

### 2. 启动基础设施

```bash
docker compose up -d
# 启动 PostgreSQL + Redis + MinIO + Nginx + 应用
```

### 3. 启动后端

```bash
./mvnw spring-boot:run
# 应用运行在 http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html
```

### 4. 启动前端

```bash
cd vue-project
npm install
npm run dev
# 开发服务器运行在 http://localhost:5173
```

### 5. 启动监控面板（可选）

```bash
# 或双击根目录的 grafana.bat
docker compose up prometheus grafana -d --no-deps
# Grafana: http://localhost:3001 (admin/admin)
# Prometheus: http://localhost:9090
```

---

## 📁 项目结构

```
├── edumind/                          # Spring Boot 后端
│   ├── src/main/java/.../
│   │   ├── Controller/              # REST 控制器
│   │   ├── Service/                 # 业务服务层
│   │   ├── DTO/ / VO/ / Entity/     # 数据传输 / 视图对象 / 实体
│   │   ├── rag/                     # RAG 检索管线
│   │   ├── mcp/                     # MCP 工具系统
│   │   ├── agent/workflow/          # DAG 工作流引擎
│   │   ├── infrastructure/          # 基础设施层
│   │   │   ├── async/              # Redis Stream 异步消费
│   │   │   ├── cache/              # Caffeine + Redis 缓存
│   │   │   ├── limiter/            # Bucket4j 限流
│   │   │   └── pdf/                # PDF 解析 (Mineru/Vision)
│   │   ├── common/                  # 公共组件
│   │   │   ├── exception/          # 全局异常处理
│   │   │   ├── result/             # 统一响应体
│   │   │   └── web/                # RequestId 过滤器
│   │   └── config/                  # Spring 配置
│   ├── src/main/resources/
│   │   ├── db/migration/           # Flyway 数据库迁移
│   │   └── prompts/                # AI Prompt 模板
│   ├── src/test/                    # 测试
│   ├── prometheus/                  # Prometheus 配置 + 告警规则
│   ├── grafana/                     # Grafana Dashboard + 数据源
│   └── docker-compose.yml
│
├── vue-project/                     # Vue 3 前端
│   ├── src/
│   │   ├── views/                  # 页面视图
│   │   ├── stores/                 # Pinia 状态管理
│   │   ├── api/                    # Axios API 封装
│   │   └── router/                 # Vue Router 配置
│   └── vitest.config.ts
│
├── .github/workflows/              # CI/CD
│   ├── ci.yml                      # 质量门禁（测试 + 构建）
│   └── deploy.yml                  # 自动部署
│
└── grafana.bat                     # 一键启动 Grafana
```

---

## 🧪 测试

```bash
# 后端
cd edumind
./mvnw test                                    # 全量测试
./mvnw test -Dtest="RagEvalRunner"             # RAG 评测脚本

# 前端
cd vue-project
npm run test                                   # Vitest 单元测试
```

---

## 🔍 可观测性

项目内置了完整的可观测性栈：

- **`/actuator/prometheus`** — JVM、HTTP、RAG 检索等指标
- **Grafana Dashboard** — JVM 内存/GC、API QPS/延迟、5xx 错误率、RAG 耗时
- **结构化日志** — JSON 格式，自动携带 `requestId` + `userId`
- **Request ID 追踪** — 每个请求贯穿 Nginx → 应用 → 响应头

```bash
# 一键拉起监控面板
grafana.bat
```

---

## 📊 RAG 评测

项目包含 15 条 C 语言知识点的检索评测数据集和自动化评测脚本：

```bash
cd edumind
EVALUATION_ENABLED=true ./mvnw test -Dtest="RagEvalRunner"
```

评测指标：Keyword Recall@5、Content Coverage@5、MRR（Mean Reciprocal Rank）。

---

## 🔒 安全

- JWT 无状态认证 + MCP API Key 双通道
- Bucket4j + Redisson 分布式限流（拦截器 + AOP 双层）
- Resilience4j 熔断保护 AI 调用
- BCrypt 密码哈希 + 常量时间 API Key 比对
- Nginx + Spring Security 双层安全头（CSP/HSTS/XSS）
- 全参数化 SQL 查询，防止注入

---

## 📄 License

MIT
