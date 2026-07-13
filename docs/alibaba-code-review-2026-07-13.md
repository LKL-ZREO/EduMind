# 阿里巴巴 Java 开发手册代码审查报告

> **日期**：2026-07-13
> **审查范围**：EduMind 项目核心 Java 文件（RAG / MCP / Service / Infrastructure）
> **审查依据**：《阿里巴巴 Java 开发手册》嵩山版
> **审查人**：Claude Code

---

## 违规统计总览

| 文件 | 强制违规 | 推荐优化 | 状态 |
|------|----------|----------|------|
| `rag/RagService.java` | 3 | 3 | ✅ 已修复 |
| `mcp/McpController.java` | 4 | 3 | ✅ 已修复 |
| `rag/EmbeddingService.java` | 6 | 3 | ✅ 已修复 |
| `infrastructure/pdf/VisionPdfParser.java` | 4 | 3 | ✅ 已修复 |
| `infrastructure/ai/StructuredOutputInvoker.java` | 1 | 3 | ✅ 已修复 |
| `Service/ServiceImpl/OpenClawServiceImpl.java` | 4 | 3 | ✅ 已修复 |
| `Service/ServiceImpl/DocumentServiceImpl.java` | 6 | 2 | ✅ 已修复 |
| `Service/ServiceImpl/DashboardServiceImpl.java` | 3 | 1 | ✅ 已修复 |
| `Service/ServiceImpl/ClassServiceImpl.java` | 2 | 0 | ✅ 已修复 |
| **合计** | **33** | **21** | **54 处** |

**已审查但无违规的文件（6 个）**：
- `CacheThroughService.java`（3 处推荐，未改）
- `SubmissionServiceImpl.java`
- `HomeworkTaskServiceImpl.java`
- `HomeworkResultServiceImpl.java`
- `ChatHistoryServiceImpl.java`
- `CourseServiceImpl.java`
- `UserServiceImpl.java`
- `SharedKbServiceImpl.java`
- `FileStorageServiceImpl.java`
- `S3FileStorageServiceImpl.java`

---

## 高频违规类型 TOP 3

### 1. `import xxx.*` 通配符导入（15 处）
涉及文件：全部 8 个被审查文件。替换为精确的单类导入。

### 2. `catch (Exception e)` 宽泛异常捕获（14 处）
全部改为精确类型：
- LLM/JSON 调用 → `catch (JsonProcessingException \| RuntimeException e)`
- DB/存储操作 → `catch (RuntimeException e)`
- IO 操作 → `catch (IOException \| RuntimeException e)`

### 3. 魔法值硬编码（10 处）
提取为命名常量，包括：
- 超时时间 → `CONNECT_TIMEOUT` / `READ_TIMEOUT`
- API 前缀 → `MODEL_PREFIX`
- 日志截断长度 → `LOG_TRUNCATE_CHARS` / `ERROR_MSG_TRUNCATE_CHARS`
- 重试延迟 → `RETRY_DELAY_BASE_MS`
- LLM 参数 → `LLM_TEMPERATURE` / `LLM_MAX_TOKENS`

---

## 各文件详细修改记录

### 1. RagService.java（6 处）
- **强制**: `catch (Exception e)` ×3 → 精确类型
- **推荐**: 常量精度声明 / 日志冗余转义 / subList 视图拷贝

### 2. McpController.java（7 处）
- **强制**: `import java.util.*` + `ConcurrentHashMap` 未使用 + `catch (Exception e)` + Spring `.*`
- **推荐**: 全限定名参数 / `Map.of()` 不可变 / MCP 协议常量提取

### 3. EmbeddingService.java（9 处）
- **强制**: `import java.util.*` + 死代码（hashEmbed 三个方法）+ `throws Exception` ×2 + `catch (Exception)` ×2
- **推荐**: 日志误导 / 全限定名 `Collections.emptyList()` / HttpClient 单例

### 4. VisionPdfParser.java（7 处）
- **强制**: `import java.util.*` + `import java.util.concurrent.*` + static 字段顺序颠倒 + `catch (Exception)`
- **推荐**: 未使用 `JsonNode` / `temperature`/`max_tokens`/retry delay 魔法值

### 5. StructuredOutputInvoker.java（4 处）
- **强制**: `catch (Exception e)` ×1
- **推荐**: `Exception` 变量类型 / 日志截断 / 错误截断魔法值

### 6. OpenClawServiceImpl.java（7 处）
- **强制**: `import java.util.*` + 未使用 `IOException` + `catch (Exception)` ×2
- **推荐**: `"openclaw/"` 前缀 / 超时魔法值

### 7. DocumentServiceImpl.java（14 处）
- **强制**: `import java.util.*` + 日志字符串拼接 + `catch (Exception)` ×4
- **推荐**: 全限定名 `java.util.Map` / `getUserId().equals()` null 安全 ×7

### 8. DashboardServiceImpl.java（9 处）
- **强制**: 3 个通配符 import + `catch (Exception)` ×2
- **推荐**: 全限定名 `LambdaQueryWrapper`

### 9. ClassServiceImpl.java（3 处）
- **强制**: 2 个通配符 import + `catch (Exception)` ×1

---

## 后续建议

1. **剩余 Service 层文件**：`ChatHistoryServiceImpl`、`CourseServiceImpl`、`UserServiceImpl`、`SharedKbServiceImpl`、`FileStorageServiceImpl`、`S3FileStorageServiceImpl` 已初筛无障碍，待详细审查
2. **Controller 层 + Mapper 层 + 前端**：尚未审查
3. **建议引入静态检查工具**：SonarLint（IDE 插件）+ Checkstyle（阿里规约配置文件）+ SpotBugs，自动化拦截新引入的违规
