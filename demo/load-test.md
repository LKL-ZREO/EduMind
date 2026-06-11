# 压测指南

## 前提

1. 下载 JMeter：https://jmeter.apache.org/download_jmeter.cgi（选 Binaries 的 zip）
2. 解压后运行 `bin/jmeter.bat`（Windows）或 `bin/jmeter`（Mac/Linux）
3. 项目已启动（`mvnw spring-boot:run`），并且你有登录 token

## 获取 token

登录后去浏览器 F12 → Application → Local Storage → 找到 `token` 的值复制出来。

或者调登录接口拿：

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"xxx","password":"xxx"}'
```

## 运行

```bash
# 非 GUI 模式运行（推荐，不弹窗口）
jmeter -n -t load-test.jmx -Jtoken=你的token -l result.jtl -e -o report/

# 生成 HTML 报告后打开
start report/index.html
```

参数说明：

| 参数 | 含义 |
|------|------|
| `-n` | 非 GUI 模式 |
| `-t` | 测试计划文件 |
| `-Jtoken=xxx` | 传入 token 变量 |
| `-l result.jtl` | 原始结果文件 |
| `-e -o report/` | 生成 HTML 报告 |

## 看什么

### 核心指标（在聚合报告里看）

```
Label                     样本  平均   中位  90%行  95%行  99%行  吞吐量/s  错误率
限流测试（60秒发100次）     100   1200  800  2500  3500  5000    1.6       40%
```

**重点看三个数字：**

| 指标 | 怎么看 | 正常值参考 |
|------|--------|-----------|
| **错误率** | 如果 > 0%，去「查看结果树」看是哪些请求返回了 429（被限流了）还是 5xx | 限流测试预期 60% 成功 40% 被限流 → **限流生效** |
| **90% 行** | 90% 的请求在多少毫秒内完成 | 流式接口 2-3s 正常，非流式 < 500ms |
| **吞吐量** | 每秒能处理多少请求 | `/api/chat/stream` 受限于 LLM 速度，不会高 |

### 面试官关心的两个结论

**① 限流是否准确**

```
限流配置 60r/m（每分钟 60 次），
60 秒内发了 100 次，
成功 61 次，被限流 39 次（HTTP 429）→ 限流在 60 附近生效
→ 结论：Bucket4j 分布式令牌桶在 60r/m 阈值准确拦截
```

**② 并发下系统是否稳定**

```
5 线程持续发 30 秒，错误率 0%，无 5xx
→ 结论：5 并发下系统稳定，无崩溃
```

## 常见问题

**Q：JMeter 启动很慢？**
第一次启动要加载插件，正常。等一会就好。

**Q：报告里全是 401？**
token 不对或已过期。重新登录拿 token。

**Q：跑完没有 report 目录？**
先手动创建 `mkdir report`，或用 `-o report` 会自动创建。

**Q：`/api/chat/stream` 一直 pending 不返回？**
SSE 流式接口在接收到 `[DONE]` 之前不会结束。可以在 JMeter 的超时设置里加一个最大值（Tools → Options → HTTP Request Defaults → Timeout），设为 10000ms（10秒），超时后 JMeter 就不再等。
