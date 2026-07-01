# RAG 上下文切割功能

基于语义分析和结构感知的智能文档切割，集成 OpenClaw 流式响应。

## 功能特性

- **多策略切割**: Markdown结构、代码函数、对话轮次、语义边界
- **嵌入向量**: 使用轻量级模型生成文本嵌入
- **向量存储**: Redis 存储文档块和向量
- **流式响应**: 支持 SSE 和 Flux 流式输出

## API 接口

### 1. 文档切割并保存
```bash
POST /api/rag/chunk
Content-Type: application/json

{
    "content": "你的长文档内容...",
    "documentId": "doc-001",
    "documentName": "示例文档",
    "maxTokens": 256,
    "overlapTokens": 50
}
```

### 2. 直接查询（不保存）
```bash
POST /api/rag/query
Content-Type: application/json

{
    "query": "你的问题",
    "document": "文档内容",
    "sessionId": "可选的会话ID",
    "topK": 3,
    "chunkConfig": {
        "maxTokens": 256,
        "overlapTokens": 50
    }
}
```

### 3. 流式查询 (SSE)
```bash
POST /api/rag/query/stream
Content-Type: application/json

{
    "query": "你的问题",
    "document": "文档内容",
    "sessionId": "可选的会话ID"
}
```

### 4. 从存储中查询
```bash
POST /api/rag/query/stored
Content-Type: application/json

{
    "query": "你的问题",
    "topK": 3
}
```

### 5. 测试嵌入
```bash
POST /api/rag/embed
Content-Type: application/json

{
    "text": "测试文本"
}
```

## 核心组件

| 组件 | 说明 |
|------|------|
| `SmartChunkService` | 智能切割服务，支持多种策略 |
| `EmbeddingService` | 文本嵌入向量生成 (DJL) |
| `VectorStoreService` | 向量存储 (Redis) |
| `RAGController` | REST API 接口 |

## 切割策略

1. **Markdown**: 按 ## 标题层级切割
2. **代码**: 按函数/类定义切割
3. **对话**: 按轮次保持完整性
4. **语义边界**: 使用嵌入向量检测主题转换

## 配置

在 `application.yml` 中添加：

```yaml
rag:
  chunk:
    max-tokens: 512
    overlap-tokens: 50
```

## 依赖

```xml
<!-- DJL - 深度学习框架 -->
<dependency>
    <groupId>ai.djl</groupId>
    <artifactId>api</artifactId>
    <version>0.28.0</version>
</dependency>
<dependency>
    <groupId>ai.djl.pytorch</groupId>
    <artifactId>pytorch-engine</artifactId>
    <version>0.28.0</version>
</dependency>
```

## 使用示例

```java
@Service
public class DocumentService {
    
    @Autowired
    private SmartChunkService chunkService;
    
    @Autowired
    private VectorStoreService vectorStoreService;
    
    public void processDocument(String docId, String content) {
        // 1. 智能切割
        List<DocumentChunk> chunks = chunkService.chunk(content, 
            SmartChunkService.ChunkConfig.defaultConfig());
        
        // 2. 保存到向量存储
        vectorStoreService.saveChunks(docId, chunks);
    }
}
```
