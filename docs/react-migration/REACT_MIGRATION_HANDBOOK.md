# EduMind Vue 3 → React 19 迁移复习手册

> 本手册把阶段 0–9 的教学文档重组为一条完整学习路径，适合迁移后复习、面试准备和后续 React 开发。原阶段文档仍保留，便于追溯每一阶段的完整实现记录。

## 1. 最终成果

EduMind 前端已经从 Vue 3 完整迁移到 React 19：

- `react-project/` 是唯一开发、CI 和生产前端；
- 17 条业务路由全部由 React 接管；
- 教师 Cookie Session、CSRF、学生课堂 Token 等安全契约保持不变；
- AI SSE、STOMP/WebSocket、文件上传、异步批改等协议保持不变；
- `vue-project/` 只作为只读迁移参考，不再参与发布；
- 最终 React 门禁为 25 个测试文件、71 个测试、2,445 个构建模块；
- `npm audit --audit-level=moderate` 为 0 个漏洞。

最终技术栈：

| 类别           | 技术                                    |
| -------------- | --------------------------------------- |
| UI 框架        | React 19.2 + React DOM                  |
| 语言与构建     | TypeScript 6 + Vite 8                   |
| 路由           | React Router 8 Data Mode                |
| 服务端状态     | TanStack Query 5                        |
| 客户端共享状态 | Zustand 5                               |
| UI 组件        | Ant Design 6                            |
| 富文本         | Tiptap 3 + KaTeX                        |
| Markdown       | Marked + highlight.js + DOMPurify       |
| 图表           | ECharts 6                               |
| 实时通信       | `fetch` SSE + STOMP/WebSocket           |
| 测试           | Vitest + React Testing Library + MSW    |
| 质量门禁       | Prettier + TypeScript + oxlint + ESLint |

开发入口：

```bash
cd react-project
npm install
npm run dev
```

默认地址为 `http://localhost:5174`，开发代理为：

```text
/api -> http://localhost:8080
/ws  -> http://localhost:8080
```

## 2. 十阶段知识地图

| 阶段 | 迁移内容             | 核心 React 知识                         | 主要工程价值                     |
| ---: | -------------------- | --------------------------------------- | -------------------------------- |
|    0 | 冻结 Vue 行为基线    | 先定义行为契约                          | 防止“页面长得像，但行为已经变了” |
|    1 | React 基础设施       | Provider、Query、API 边界、工程门禁     | 建立可独立运行的 React 应用      |
|    2 | 登录、路由、教师布局 | Loader、重定向、Outlet、Mutation        | 建立真正的认证与导航边界         |
|    3 | 班级管理             | 垂直切片、Query Key、表单、URL 状态     | 打通第一个完整业务闭环           |
|    4 | AI 对话              | SSE、Reducer、AbortController、导航阻塞 | 掌握 AI 应用的流式生命周期       |
|    5 | 知识库               | 递归树、标识符状态、上传、权限          | 处理复杂层级资源和人机协作       |
|    6 | 作业                 | 可编辑快照、Tiptap、轮询、可恢复错误    | 处理长表单和异步 AI 业务         |
|    7 | 教学数据             | 多 Query、ECharts、草稿持久化           | 包装命令式库并组合分析资源       |
|    8 | 实时课堂             | STOMP、Zustand、重连、绝对时间          | 管理长连接和实时共享状态         |
|    9 | 生产切换             | Docker、Nginx、CI、CSP、路由契约        | 让 React 成为唯一发布源          |

建议复习顺序：

```text
先掌握状态分类
-> 再理解 Query 与 Mutation
-> 再理解 Effect 与外部系统
-> 然后复习 SSE / WebSocket
-> 最后复习构建、测试和生产切换
```

## 3. Vue 与 React 思维对照

迁移不是把 `.vue` 改成 `.tsx`，而是重新确定数据流、状态所有权和生命周期边界。

| Vue 常见写法            | React 中的对应思路                       | 迁移时的注意点                         |
| ----------------------- | ---------------------------------------- | -------------------------------------- |
| `ref` / `reactive`      | `useState`、`useReducer` 或外部 Store    | React 状态更新必须产生新引用           |
| `computed`              | 渲染时直接计算，必要时 `useMemo`         | 不要把可计算值复制到 State             |
| `watch`                 | 通常改为派生值；外部同步才用 `useEffect` | 不要用 Effect 模拟所有响应式关系       |
| Pinia                   | TanStack Query、Zustand、局部 State      | 先按状态类型分类，不能机械替换         |
| `v-model`               | 受控值 `value + onChange`                | 表单库也可以拥有自己的受控状态         |
| `onMounted/onUnmounted` | `useEffect` 和 cleanup                   | 开发 Strict Mode 会重复验证副作用      |
| Vue Router 守卫         | React Router Loader / `useBlocker`       | 进入前鉴权与离开时阻塞是两类问题       |
| `<router-view>`         | `<Outlet />`                             | 父布局和子路由通过嵌套路由组合         |
| 动态组件                | `React.lazy`、路由 `lazy`                | 在路由或低频功能边界分包               |
| 直接修改响应式对象      | 不可变更新                               | 数组、对象、树和嵌套表单都要创建新路径 |

最关键的思维变化：

```text
Vue 响应式常问：“修改哪个响应式对象？”
React 更常问：“这个状态是谁拥有的，下一份快照是什么？”
```

## 4. 全项目最重要的状态分类

在写 `useState` 之前，先判断状态属于哪一类。

| 状态类别         | 推荐所有者         | 项目示例                                 |
| ---------------- | ------------------ | ---------------------------------------- |
| 服务端资源       | TanStack Query     | 班级、聊天记录、知识树、作业、统计数据   |
| 服务端命令状态   | Mutation           | 登录、创建、删除、上传、发布、生成材料   |
| 跨组件客户端状态 | Zustand            | AI 正在回答、实时课堂快照                |
| 路由与可分享状态 | URL                | 班级筛选、知识库邀请 Token、选中班级     |
| 未保存的用户输入 | 组件 State         | 作业草稿、聊天输入、课堂答案、弹窗表单   |
| 可推导数据       | 渲染时计算或纯函数 | 过滤结果、合格率、剩余时间、树路径       |
| 外部系统状态     | Effect + Adapter   | STOMP 连接、ECharts 实例、Tiptap、定时器 |

判断流程：

```text
数据是否来自后端？
├─ 是 -> Query；用户触发写操作 -> Mutation
└─ 否
   ├─ 是否应出现在 URL？ -> URL State
   ├─ 是否是未保存输入？ -> Local State
   ├─ 是否被远距离组件共同消费？ -> Zustand
   ├─ 是否能从现有值算出？ -> 不存 State，直接派生
   └─ 是否同步浏览器/第三方系统？ -> Effect
```

这一分类解决了 React 项目里最常见的三类问题：重复状态、同步 Effect 和无边界的全局 Store。

---

# 第一部分：按阶段复习迁移

## 5. 阶段 0：冻结 Vue 行为基线

### 为什么先做基线

迁移最危险的不是编译失败，而是功能看起来已经迁移，路由、安全、异常恢复或协议行为却悄悄改变。

因此迁移前先把 Vue 版本定义为“可执行行为契约”。React 页面只有同时满足以下条件，才算完成：

- 路由和访问权限一致；
- 请求格式、Result 包装和错误处理一致；
- Session、CSRF 和 Token 行为一致；
- SSE/WebSocket 协议一致；
- 离开页面时正确清理外部资源；
- 关键用户流程可测试；
- 不降低 HTML、上传和代理安全性。

### 17 条业务路由

| 路由                      | 访问者       | 功能                         |
| ------------------------- | ------------ | ---------------------------- |
| `/`                       | 公开         | 学生提交作业                 |
| `/login`                  | 游客         | 教师登录                     |
| `/register`               | 游客         | 教师注册                     |
| `/teacher/chat`           | 教师         | AI 对话                      |
| `/teacher/docs`           | 教师         | 知识库                       |
| `/teacher/classes`        | 教师         | 班级列表                     |
| `/teacher/classes/:id`    | 教师         | 班级详情                     |
| `/teacher/tasks`          | 教师         | 作业管理                     |
| `/teacher/tasks/:id`      | 教师         | 作业详情                     |
| `/view/submission/:id`    | 教师         | 原始提交查看，不使用教师侧栏 |
| `/teacher/data`           | 教师         | 教学数据中心                 |
| `/teacher/live/:classId`  | 教师         | 教师实时课堂                 |
| `/live/join`              | 公开         | 课堂码入口                   |
| `/live/:sessionCode`      | 学生课堂身份 | 学生实时课堂                 |
| `/teacher/pre-lesson`     | 教师         | 课前准备                     |
| `/teacher/preview/create` | 教师         | 创建预习任务                 |
| `/preview/:taskId`        | 公开         | 学生预习                     |

未知路由回到 `/`。访问受保护路由前先探测 `/auth/me`；游客访问教师路由跳转登录；已登录教师访问登录/注册页跳转对话页。

### 安全和协议基线

教师认证不是浏览器 JWT，而是服务端 Cookie Session：

```text
Axios baseURL = /api
withCredentials = true
GET /auth/me 恢复用户
POST /auth/logout 清理会话和客户端缓存
```

非安全 HTTP 方法需要 CSRF：

```text
GET /auth/csrf
-> X-XSRF-TOKEN
-> 403 + 40301 时刷新 Token
-> 原请求最多重试一次
```

AI SSE 必须支持 POST、请求体、CSRF、多行 `data:`、任意网络分块和 Abort。实时课堂 STOMP 必须支持教师 Session、学生课堂 Token、5 秒重连和 10 秒双向心跳。

### 本阶段学到什么

- 框架迁移首先是行为迁移，不是语法迁移。
- 路由、安全、协议和 cleanup 都属于前端契约。
- 先记录复杂度和风险，再按风险安排迁移顺序。
- 纯 TypeScript 模块应优先复用，框架组件应重新设计。

## 6. 阶段 1：React 基础设施

### 为什么单独建立基础阶段

如果一开始同时迁移登录和业务页面，Provider、构建、类型、请求拦截器等基础错误会与业务错误混在一起。先让空的 React 应用独立运行，可以把基础设施问题隔离出来。

### Provider 组合

```text
index.html #root
-> createRoot().render()
-> StrictMode
-> Ant Design ConfigProvider
-> QueryClientProvider
-> Ant Design App
-> RouterProvider
-> route component
```

每个 Provider 只承担一个职责：

- `StrictMode` 检查不安全的渲染和 Effect 假设；
- `ConfigProvider` 提供主题 Token 和中文语言；
- `QueryClientProvider` 提供服务端缓存；
- Ant Design `App` 提供 message、modal 等上下文；
- `RouterProvider` 管理 URL、Loader、错误页和导航。

Provider 顺序是一种依赖关系：路由页面可以消费主题和 Query Client，而全局 Provider 不需要了解业务路由。

### 共享 API 边界

```text
shared/api/client.ts -> Axios 与拦截器
shared/api/csrf.ts   -> Token 缓存、并发去重、刷新
shared/api/errors.ts -> unknown/Axios 错误归一化
shared/api/types.ts  -> Result 和错误类型
```

Axios 拦截器在 React 树外运行，因此不能调用 Hook。它通过可配置 callback 把 401 通知给应用启动层，这是“框架外部模块如何与 React 集成”的重要模式。

### 第一个 TanStack Query

后端健康状态使用：

```text
key: ['system', 'health']
queryFn: checkHealth
staleTime: 3 分钟
```

组件不再手写 loading/error/cache，只描述各状态如何渲染。

### 工程边界

前端采用：

```text
src/
├─ app/      # 启动、Provider、路由、全局布局
├─ features/ # 按业务能力组织
└─ shared/   # 跨业务 API、UI、编辑器、工具
```

架构测试禁止 React 代码导入 Vue、Vue Router 或 Pinia，并限制页面只能属于 `app` 或某个 feature。

### 本阶段学到什么

- Provider 是运行能力的依赖注入边界。
- 服务端状态不需要手写缓存。
- 网络层不能调用 Hook，需要显式适配接口。
- TypeScript 类型应从请求边界开始，避免 `any` 向组件传播。
- 质量门禁应在业务代码变多前建立。

## 7. 阶段 2：认证、路由和教师布局

### 为什么认证放在业务页面之前

所有教师业务都依赖同一登录状态、重定向规则和布局。如果各页面自己判断登录，就会产生闪屏、重复请求和不同的重定向行为。

### 当前用户属于服务端状态

```text
打开游客或受保护路由
-> React Router Loader
-> queryClient.ensureQueryData(currentUserQueryOptions)
-> GET /auth/me
-> ['auth', 'current-user'] = user 或 null
-> 放行或 redirect
```

认证不会因为经过几十秒自动过期，所以 Query 使用无限 stale/gc 时间。登录、退出、401 或主动探测才会改变它。

不需要再把用户复制到 Context 或 Zustand。

### Loader 与安全重定向

`requireAuthLoader` 在受保护页面渲染前运行：

```text
/teacher/classes?tab=active
-> /login?redirect=%2Fteacher%2Fclasses%3Ftab%3Dactive
```

登录后的 `redirect` 是不可信 URL 输入，只允许：

- 以单个 `/` 开头；
- 不是 `//evil.example`；
- 解析后仍属于当前 Origin。

否则回退到 `/teacher/chat`，防止 Open Redirect。

### Query 与 Mutation 的分工

```text
GET /auth/me        -> Query
POST /auth/login    -> Mutation，成功后 setQueryData(user)
POST /auth/register -> Mutation，成功后跳转登录
POST /auth/logout   -> Mutation，清理 Session、CSRF 和缓存
```

Mutation 表达“用户要求服务端执行命令”，Query 表达“服务端资源当前是什么”。

### 嵌套路由布局

```text
/teacher
└─ TeacherLayout
   ├─ Header
   ├─ Sider/Menu
   └─ Outlet
      └─ 当前教师子页面
```

`/view/submission/:id` 仍要求教师登录，但放在教师布局外，保持原行为。

### 本阶段学到什么

- Loader 适合“进入路由前必须完成”的鉴权。
- 用户身份是服务端资源，可以由 Query 缓存。
- `<Outlet />` 构建稳定父布局和变化子页面。
- Query 参数必须按不可信输入处理。
- Axios 401、Loader 重定向和页面 Mutation 是不同层次的职责。
- 路由 `lazy` 可以按页面拆分生产包。

## 8. 阶段 3：班级管理垂直切片

### 为什么采用垂直切片

垂直切片一次打通完整用户旅程：

```text
route
-> page
-> query/mutation hook
-> typed API
-> Spring Boot endpoint
-> cache invalidation
-> updated UI
```

它能尽早证明 React 基础设施可以处理真实鉴权、后端契约、错误展示和数据刷新，而不是先转换所有模板、最后才发现架构不适用。

### Feature-first 分层

```text
features/classroom/
├─ api/        # HTTP 和 Query Options
├─ components/ # 卡片、表格、编辑/邀请/导入弹窗
├─ hooks/      # Mutation 协调
├─ model/      # 类型、分组、过滤等纯函数
└─ pages/      # 班级列表和详情用例
```

页面协调用例；组件负责局部呈现；Hook 协调缓存；API 了解 HTTP；Model 不依赖 React。

### 类型化 API 与 Result 解包

```text
AxiosResponse<ApiResponse<ClassGroupResponse[]>>
-> unwrapApiResponse()
-> Promise<ClassGroupResponse[]>
```

组件不依赖 `response.data.data`。即使 HTTP 是 200，只要业务 Result 失败，也会变成 Query/Mutation 可处理的异常。

后端兼容字段如 `createdAt/joinedAt` 只在 Adapter 中归一化一次，组件始终消费稳定 Domain 类型。

### Query Key 与精确失效

```text
['classroom']
├─ ['classroom', 'class-groups']
├─ ['classroom', 'class-detail', classId]
├─ ['classroom', 'courses']
└─ ['classroom', 'course-presets']
```

创建班级只让班级组失效；删除课程同时影响课程和班级组；导入学生只刷新当前班级详情及聚合计数。

此处选择“失效后重新请求”，没有盲目做乐观更新，因为后端还负责权限、重复检查、人数统计和导入限制。

### URL 状态与输入草稿

搜索词适合进入 URL，便于刷新和分享；但输入框不能直接被每次 URL 导航反向控制。

```text
键盘输入 -> local draft，立即过滤
         -> 安静 200 ms 后 replace URL 参数
```

这样不会让快速中文输入被旧 URL 值覆盖，也不会为每个字符制造一条浏览历史。

### 表格导入与动态加载

XLS/XLSX/CSV 在客户端先验证字段、重复学号、空行和 200 人上限，只把有效对象发给后端。`xlsx` 只有在解析或下载模板时才 `import()`，普通班级页面不下载表格运行时。

### 本阶段学到什么

- Feature-first 让业务能力拥有自己的垂直层次。
- Query Key 是服务端状态的数据模型。
- 失效范围应与变更范围一致。
- URL State 与即时输入 State 可以有不同更新节奏。
- 动态 `import()` 适合低频且体积大的功能。
- 前端校验改善体验，后端校验才是数据安全边界。

## 9. 阶段 4：AI 流式对话

### 为什么这是 AI 前端的核心阶段

普通 CRUD 是一次请求对应一次响应。AI 对话是一条可取消、持续产生事件并不断改变 UI 的长请求：

```text
发送消息
-> 创建/复用会话
-> 插入用户消息和空助手消息
-> 打开流式响应
-> 解析任意网络分块
-> 将事件归并为新快照
-> 渲染局部结果
-> 完成、停止或失败
```

### 为什么不用 `EventSource`

后端要求 POST、JSON 或 FormData、CSRF Header 和 AbortSignal。浏览器 `EventSource` 主要用于 GET，无法满足这些请求控制要求。

正确实现是：

```text
fetch()
-> Response.body
-> ReadableStream reader
-> TextDecoder
-> 自定义 SSE frame parser
```

SSE 是响应格式，不等于必须使用 `EventSource` 类。

### 网络 Chunk 不等于 SSE Event

一次 `reader.read()` 可能只拿到半个 JSON，也可能拿到多个事件。解析器必须：

- 保留未完成 Buffer；
- 增量解码 UTF-8；
- 以空行识别完整 Frame；
- 合并多个 `data:` 行；
- 处理默认 `message` 事件；
- 在流结束时冲刷未带终止空行的最后 Frame。

主要事件：`token`、`tool_started`、`tool_completed`、`citation`、`artifact`、`done`、`error`。

### 流式请求也要遵守 CSRF

因为 `fetch` 不经过 Axios 拦截器，聊天 API 必须显式实现：

```text
取得 CSRF
-> POST + Cookie + X-XSRF-TOKEN
-> 40301 时刷新 Token
-> 最多重试一次
```

401 则复用统一 Unauthorized Handler，清理认证缓存并保留当前内部路径跳转登录。

### 不可变事件归并

Vue 代码可以对响应式消息执行 `content += token`。React 中 Reducer 要产生新路径：

```text
previous messages
-> 新数组
-> 克隆目标 assistant message
-> 创建新的 content/tool/citation
-> Query Cache 发布新快照
```

Reducer 是纯函数，因此可以在没有 React、DOM 和网络的情况下测试。

### 为什么消息放在 Query Cache

每个会话的消息是一个服务端资源：

```text
['assistant', 'messages', sessionId]
```

历史记录和流式中的临时消息都使用同一份缓存快照。切换会话可以复用缓存，不需要再维护一份可能漂移的本地消息数组。

Zustand 只保存远距离组件共同需要的 `responding` 标志，用于发送/停止按钮、上下文禁用、导航保护和清理。

### 自定义 Hook 与取消

`useChatStreamRunner` 拥有：

- 活跃 `AbortController`；
- 流式状态机；
- 临时消息 ID；
- Query Cache 更新；
- 错误归一化；
- 卸载 cleanup。

停止生成时保留部分结果并标记 `stopped`。`useBlocker` 管理 SPA 内部导航，`useBeforeUnload` 管理刷新、关页和离站，两者不能互相替代。

### 安全 Markdown

流式阶段先显示纯文本；结束后再执行：

```text
Markdown
-> Marked
-> 已注册语言的 highlight.js
-> DOMPurify
-> dangerouslySetInnerHTML
```

`dangerouslySetInnerHTML` 不是天然错误；真正的要求是所有不可信 HTML 必须在最终插入边界消毒。

### 本阶段学到什么

- SSE 格式和 EventSource API 是两个概念。
- 长请求应封装为有 cleanup 的自定义 Hook。
- Abort 是正常业务终态，不应显示成普通故障。
- Reducer 适合把事件流归并为不可变状态机。
- Query 可以暂存服务端资源正在生成的中间快照。
- AI 内容必须在最终 HTML 边界消毒。

## 10. 阶段 5：知识库工作台

### 为什么知识库不能当作普通表格

一个知识库页面同时协调：

```text
个人目录树
多个团队知识库目录树
文档内容缓存
文档生成材料缓存
成员缓存
知识空间列表
```

它展示了复杂 React 页面不需要把所有数据塞进一个巨大 Store。

### 扁平数据构造递归树

后端返回 `id + parentId`，UI 需要 children：

1. 第一遍为每一行创建唯一节点并放入 `Map`；
2. 第二遍把节点挂到父节点或根集合；
3. 返回新的递归排序树，文件夹在文件前。

不能原地修改 Query Cache 中的后端数组，否则引用相等性会失去意义，其他消费者也会收到意外变更。

### 存 ID，派生对象

本地状态保存 `selectedNodeId`，而不是复制一份 `TreeNode`：

```text
selectedNodeId + 最新 Query Tree
-> findTreeNode()
-> selectedNode
```

节点重命名、移动或重新请求后，派生对象自然来自最新树，不会继续引用旧快照。

### `undefined | null | value` 三态

上传弹窗目标使用：

| 值          | 含义                     |
| ----------- | ------------------------ |
| `undefined` | 弹窗关闭                 |
| `null`      | 弹窗打开，目标是根目录   |
| `TreeNode`  | 弹窗打开，目标是指定目录 |

这说明不能总用 Truthy/Falsy 压缩状态；`null` 本身可能是合法业务值。

### 资源作用域进入 Query Key

```text
['knowledge', 'tree', 'personal']
['knowledge', 'tree', kbId]
['knowledge', 'content', docId]
['knowledge', 'materials', docId]
['knowledge', 'members', kbId]
```

所有会改变后端返回结果的参数都应进入 Key，避免个人树与团队树互相覆盖。

### 受控上传

Ant Design Upload 只负责选择和展示，功能模块决定什么时候上传：

```text
UploadFile[]
-> originFileObj
-> File[]
-> 每个文件一个 FormData 请求
-> 合并当前文件和已完成文件进度
```

浏览器负责 multipart boundary，代码不要手写 `Content-Type`。顺序上传便于展示可预测进度，也避免同时触发大量解析任务。

### 权限与邀请

只为 Owner 显示管理入口属于 UX，不是权限边界；后端仍必须检查所有成员、移动、删除和生成请求。

邀请由 URL 驱动：

```text
/teacher/docs?joinToken=...
-> 打开加入弹窗
-> 成功后 replace 清理 Token
```

这样刷新不会重复加入，历史记录也不会残留已消费动作。

### 人机协作 AI 流程

材料生成是 Mutation：教师主动选择 PPT 并触发长请求。成功部分复制到受控草稿，教师检查、修改、选择班级后再保存。预习和题目可以部分成功，UI 不能因另一部分失败而丢弃成功结果。

“AI 生成 ≠ 自动发布”是产品安全边界。

### 本阶段学到什么

- 递归数据应在纯函数中标准化。
- 本地状态优先保存稳定标识符，而不是缓存对象副本。
- Query Key 必须表达资源作用域。
- 隐藏按钮不能代替后端授权。
- URL 可以表达一次性的深链 UI 意图。
- AI 结果进入业务前应由人确认。

## 11. 阶段 6：作业编辑、提交和异步批改

### 为什么可编辑草稿可以复制服务端数据

一般不应把 Query 数据复制到 State，但正在编辑且尚未保存的草稿是有意创建的客户端事务缓冲区：

```text
Query 中保存的 HomeworkDraft
-> 克隆到本地 DraftEditor
-> 用户修改本地不可变快照
-> Save Mutation
-> 后端返回规范版本
-> 更新本地快照并失效相关 Query
```

这不是无意义重复，而是区分“服务器已保存事实”和“用户未提交意图”。

### 嵌套表单不可变更新

修改一道题：

```text
new draft object
-> new questions array
-> new edited question object
-> 其他 question 保留引用
```

从题库加入时也要克隆源对象，防止编辑草稿时修改 Query Cache 中的题库对象。

### 派生默认值，不用同步 Effect

班级列表异步到达时：

```text
selectedClassIds === null -> 用户还没选择，派生首个班级
selectedClassIds === []   -> 用户明确清空，保留空选择
```

不需要 Effect 把“首个班级”复制进 State。能从当前 Props/State 计算出的值应在渲染时计算。

### 发布前先保存

一次“发布”用户意图包含两个有顺序依赖的 Mutation：

```text
校验本地表单
-> 保存最新草稿
-> 获得规范 draft ID/content
-> 发布到选中班级
-> 失效草稿和任务 Query
```

保存失败时绝不能继续发布旧版本。

### Tiptap 是命令式外部系统

React 对外只暴露：

```ts
value: string
onChange(nextHtml: string): void
```

Tiptap 内部拥有 ProseMirror 文档。Effect 负责在父组件切题时同步外部 Editor 实例；最新 `onChange` 放入 Ref，避免长寿命回调捕获旧 Props。

所有 Tiptap 包必须锁定同版本，否则不同 `@tiptap/core` 副本可能产生 TypeScript 不兼容类型。

### 公式节点与双重消毒

公式保存为语义 HTML：

```html
<span class="math-inline" data-latex="x^2"></span>
```

学生侧渲染顺序：

```text
作业 HTML
-> 第一次消毒
-> 找到 math-inline 并生成 KaTeX
-> 对生成 HTML 再消毒并允许必要 MathML
-> 渲染
```

### 公开页面也需要 CSRF

“公开”只代表不要求教师 Session，不代表 POST 可以跳过 CSRF。学生上传仍发送 CSRF Token，并让浏览器设置 multipart boundary。

文件名协议为：

```text
学号_姓名_班级_作业名称.扩展名
```

前端提前提示格式和选择不匹配，后端重复验证并保持权威。

### 可恢复错误是 UI 状态

| 业务码 | 含义              | UI 转移                  |
| -----: | ----------------- | ------------------------ |
|    300 | 文件名/选择不匹配 | 展示差异，请求确认后重试 |
|    428 | 需要绑定 QQ       | 打开绑定弹窗，完成后重试 |
|    429 | 重复/限流         | 保留文件并提示等待       |

Mutation Error 有时不是终点，而是状态机的下一条有效分支。

### 条件轮询

Redis Stream 批改是最终一致的：

```text
PENDING / PROCESSING -> 2 秒后继续 Query
COMPLETED / FAILED   -> refetchInterval 返回 false
```

TanStack Query 负责定时器、请求重叠、错误和最新快照。页面不再手写轮询循环。

### 本阶段学到什么

- 可编辑草稿是合理的本地事务副本。
- 嵌套表单必须沿修改路径创建新引用。
- 依赖 Mutation 应显式按顺序执行。
- 第三方 Editor 应包装成受控 React Adapter。
- 业务错误可以驱动恢复流程。
- Query 的条件轮询适合最终一致任务。

## 12. 阶段 7：教学数据、课前准备和预习

### 多个 Query 组成一个 Dashboard

指标、成绩分布、知识点、错误、学生和困惑信号有不同缓存身份，不应该为了页面方便伪装成一个大请求。

```text
['teaching', 'dashboard', classId, 'metrics']
['teaching', 'dashboard', classId, 'distribution']
['teaching', 'dashboard', classId, 'knowledge']
['teaching', 'dashboard', classId, 'errors', knowledgePoint]
['teaching', 'dashboard', classId, 'students']
['teaching', 'dashboard', classId, 'confusions']
```

多个 `useQuery` 在同一次渲染中自然并行，一个困惑接口失败不需要清空已经成功的成绩数据。

### URL、Query、Local 和 Derived State

- 选中班级进入 `?classId=`，因为它可刷新、收藏和分享；
- 服务端统计由 Query 管理；
- 搜索、弹窗和测验答案属于 Local State；
- 合格率、薄弱知识点和过滤学生是 Derived State。

这是面试中非常好用的一套状态解释框架。

### 把 ECharts 包装成声明式组件

ECharts 是命令式 API：

```text
init(container)
setOption(option)
resize()
dispose()
```

共享 `<EChart option={...} />` Adapter 使用：

- DOM Ref 保存容器；
- Instance Ref 保存 ECharts 实例；
- Mount Effect 创建实例和 ResizeObserver；
- Option Effect 更新已存在实例；
- Cleanup 断开 Observer 并 Dispose。

页面只描述图表 Option，不重复处理实例生命周期。这个模式同样适用于地图、播放器、WebGL 和其他命令式 SDK。

### 后台任务轮询与失效

修改知识点后，历史错误重新分类在后台执行：

```text
Mutation 返回 taskId
-> Query 每 2 秒查询任务
-> PENDING/RUNNING 继续
-> 终态停止
-> invalidate dashboard 前缀
```

Mutation 表示“已请求修改”，Query 表示“后台任务当前状态”，Invalidation 表示“旧派生资源可能过期”。

### 课前教案是本地事务

教案根据服务端证据初始化一次，然后由本地 `LessonDraft` 拥有。父级使用 `key={classId}` 创建新的编辑生命周期，避免 Effect 在班级切换后异步覆盖上一班的未保存内容。

Effect 只同步 `localStorage`：

```text
draft 改变
-> 取消旧计时器
-> 等待 700 ms
-> 保存 class-scoped JSON
```

准备度、总时长和导出文本都直接派生，不存第二份 State。

### AI 内容安全与依赖安全

AI 生成的教案 HTML 和预习 Markdown 都在 `dangerouslySetInnerHTML` 前通过 DOMPurify。测试使用恶意 `<script>` 验证最终 DOM 中不存在脚本。

ECharts 初始版本存在 Moderate XSS 公告，因此升级并固定到 6.1.0，最终审计为 0。依赖版本也是前端安全边界的一部分。

### 本阶段学到什么

- Dashboard 不需要人工合并为一个 Mega Query。
- Query Key 前缀可以表达资源层次并批量失效。
- 命令式库通过 Ref + Effect + Cleanup 适配 React。
- `key` 可以明确重建以身份为边界的编辑器。
- Effect 用于外部同步，不用于计算派生值。
- 依赖审计和内容消毒同样属于前端安全。

## 13. 阶段 8：实时课堂和 STOMP 生命周期

### 为什么实时页面比 HTTP 页面复杂

实时页面拥有一个长期外部进程：

- 连接可能处于 connecting、connected、disconnected、reconnecting；
- 重连产生新 Broker Session，必须重新订阅；
- 事件从 React 事件处理器之外到达；
- 初始 HTTP 快照可能与实时增量竞态；
- 教师和学生身份权限不同；
- 路由、身份或课堂变化时必须停止旧连接和计时器。

### HTTP 快照 + STOMP 增量

教师启动顺序：

```text
GET active session
-> 恢复或创建 Session
-> Promise.all(history, board, presence, stats)
-> 一次写入完整 Zustand 快照
-> Render
-> 建立 STOMP 并接收增量
```

先完成 HTTP Hydration 再连接，可以防止较旧 HTTP 响应覆盖已经到达的实时事件。

```text
HTTP  = 命令确认和可刷新的权威快照
STOMP = 低延迟广播增量
```

### 为什么 Zustand 管理实时快照

Socket Callback 位于组件树外，多个相距很远的面板需要原子更新同一课堂状态，因此 Zustand 保存：

- 角色、Session 和连接状态；
- 当前互动、问题板、历史；
- 在线/缺席学生和统计；
- 匿名问题、reaction、举手队列；
- 教师在线与课堂结束状态。

学生未提交答案、弹窗开关、筛选条件仍留在 Local State。不是“全部塞进全局 Store”，而是“一个外部 Session 边界对应一个 Store”。

### STOMP Adapter

页面不直接创建 Client。Adapter 接收：

```ts
{
  (role, sessionId, token, onStatus, onEvent);
}
```

根据页面协议选择：

```text
http  -> ws://host/ws/live
https -> wss://host/ws/live
```

教师使用登录 Session 和 `X-Session-Id`；学生额外使用课堂级 `Authorization: Bearer <token>`。学生 Token 不是教师凭据。

### 重连、重新订阅和旧实例隔离

配置为 5 秒重连、10 秒入站/出站心跳。每次 `onConnect` 都重新创建订阅，因为订阅属于 Broker Connection，不会自动跨连接存活。

Adapter 忽略已经不是活跃实例的旧 Client 回调，防止旧连接的迟到 Close 事件把新连接错误标记成断线。

### React 生命周期

`useLiveSocketLifecycle` 的 Effect 依赖角色和课堂身份：

```text
有效身份 -> connect
cleanup  -> deactivate
```

HTTP 初始化用 `cancelled` 标记避免卸载后继续更新。路由组件以课堂码或班级 ID 为 `key`，身份变化时明确创建新的 Session 生命周期。

### 事件归并

STOMP Frame 先解析成类型化事件：

```text
stats | interaction | timing | qa |
presence | reaction | handQueue | teacherStatus
```

Store 把事件归并成新数组和新对象。测试不仅验证 Callback 被调用，还验证最终 Observable Store Snapshot 真正发生变化。

### 绝对截止时间

后端发送 `deadlineEpochMs`：

```text
remaining = ceil((deadlineEpochMs - Date.now()) / 1000)
```

截止时间是唯一事实。Interval 只更新当前时钟，不持久化一个不断减一且可能漂移的 `remainingSeconds`。

### 学生答案按互动 ID 保存

```ts
Record<interactionId, answer>;
```

切题自然选择不同草稿，不用 Effect 在 `currentInteraction` 变化时清空答案。显示优先级为本地草稿、已提交本地答案、服务端历史答案、空值。

### 本阶段学到什么

- WebSocket 是需要明确建立和清理的外部进程。
- 重连成功不代表原订阅仍有效。
- HTTP 和实时传输可以分别承担快照与增量。
- Zustand 适合组件树外事件驱动的共享快照。
- Effect 依赖应表达外部资源身份。
- 绝对时间比递减状态可靠。
- 测试应验证最终状态变化，而不仅是函数调用。

## 14. 阶段 9：生产切换

### 为什么功能完成还不等于迁移完成

如果 Docker 仍构建 Vue、CI 仍保护 Vue、README 仍让开发者启动 Vue，仓库就有两个来源不同的“主前端”。

生产切换必须原子改变：

| 关注点        | 切换结果                    |
| ------------- | --------------------------- |
| Source        | React 是唯一产品源码        |
| Lockfile      | React Lockfile 决定依赖图   |
| CI            | React `ci:check` 是前端门禁 |
| Docker        | React `dist/` 进入 Nginx    |
| Runtime Shell | HTML 包含 React `id="root"` |
| Documentation | 默认命令全部指向 React      |
| Vue           | 只读历史参考                |

### 多阶段 Docker 构建

```text
Node stage
-> 复制 React manifest + lockfile
-> npm ci
-> npm run build
-> dist/

Nginx stage
-> 只复制 dist/ 和运行配置
```

`npm ci` 使用锁文件提供可复现依赖。最终镜像不需要 Node 和构建工具。

### CI 验证真正运行的制品

React CI 顺序为：

```text
npm ci
-> Prettier
-> TypeScript
-> oxlint
-> ESLint
-> Vitest
-> Vite build
```

Docker Job 构建真实 Nginx 镜像、启动容器并检查：

- `/health`；
- `/` 中的 `id="root"`；
- `/teacher/chat` 深层路由返回 React Shell。

只静态检查 Dockerfile 不足以发现入口脚本、Nginx 模板或资产复制错误。

### SPA Fallback

直接访问 `/teacher/chat` 时，请求先到 Nginx，React 尚未运行：

```text
GET /teacher/chat
-> Nginx try_files
-> index.html
-> React 启动
-> React Router 匹配 /teacher/chat
```

没有 `try_files $uri $uri/ /index.html` 时，页面内跳转可能正常，但刷新、书签和分享链接会 404。

### 两套 Nginx 模式

```text
无证书 -> HTTP bootstrap template
有证书 -> HTTPS production template
```

两套配置都必须保留：

- React Router SPA Fallback；
- `/api` 普通反向代理；
- SSE 关闭 Buffer/Gzip；
- `/ws/live` Upgrade Header 和长超时；
- `/mcp` 流式代理；
- 指纹资产长期 immutable cache；
- 公网阻止 `/actuator`。

### CSP 收紧

HTTPS 使用：

```text
script-src 'self'
```

React/Vite 使用外部指纹脚本，不需要旧版本的 `'unsafe-inline'` Script。移除它可以降低 HTML 注入后执行内联脚本的风险。

`style-src 'unsafe-inline'` 暂时保留，因为 Ant Design 和图表仍依赖运行时内联样式。安全策略应基于真实运行产物逐步收紧。

### 路由契约测试

`cutover.spec.ts` 递归遍历导出的 React Route Objects，确认全部 17 条业务路由存在，并拒绝临时 `/migration/foundation` 路由。

这是一个关键设计：路由配置既可以交给 `useRoutes` 渲染，也可以当作普通数据进行架构测试。

### 为什么保留 Vue

保留 Vue 可以提供行为参考、迁移证据、面试对照和首发观察期回退依据。但它不再被任何默认命令、CI、Docker 或部署文档消费。

“保留归档”和“继续双前端维护”是两件不同的事。

### 本阶段学到什么

- 迁移完成意味着开发、验证和发布只有一个 Owner。
- React Router 仍依赖服务器的深层路由回退。
- SSE 和 WebSocket 需要不同代理语义。
- CI 应检查实际交付制品，而不只是源码。
- CSP 应根据当前构建产物制定。
- 临时迁移脚手架必须有明确删除条件。

---

# 第二部分：跨阶段 React 核心知识

## 15. Effect 的正确使用

React Effect 的定义不是“数据变化后做点什么”，而是“让 React 状态与外部系统保持同步”。

### 项目中合理的 Effect

| 外部系统       | Effect 工作               | Cleanup                |
| -------------- | ------------------------- | ---------------------- |
| STOMP          | 建立连接和订阅            | deactivate/unsubscribe |
| ECharts        | 创建实例和 ResizeObserver | dispose/disconnect     |
| Tiptap         | 同步外部 Editor 实例      | 销毁 Editor            |
| `localStorage` | 防抖持久化草稿            | 取消旧 Timer           |
| Blob URL       | 创建文件预览              | `URL.revokeObjectURL`  |
| Deadline Clock | 更新时间                  | `clearInterval`        |
| 浏览器导航     | 注册离站阻塞              | 移除 Listener          |

### 不应该使用 Effect 的情况

- 从 Query Data 计算过滤列表；
- 从总数计算通过率；
- 根据班级列表派生默认班级；
- 根据 ID 在树中找到对象；
- 根据 Deadline 计算剩余时间；
- 把 Props 复制进 State，仅为了“保持同步”。

如果一个值能在渲染时由当前 Props 和 State 算出，就直接计算。`useMemo` 是性能工具，不是语义必需品。

### Strict Mode 的意义

开发环境中 Strict Mode 会强化 Mount/Cleanup 检查。重复请求、重复订阅、两个图表实例或没有释放的 Blob URL，往往说明副作用没有建立正确边界，而不是 Strict Mode 本身有问题。

## 16. TanStack Query 复习

### Query Key 原则

Key 必须包含所有会改变后端结果的参数：

```ts
["classroom", "class-detail", classId][("assistant", "messages", sessionId)][
  ("knowledge", "tree", kbId)
][("teaching", "dashboard", classId, "errors", knowledgePoint)][
  ("homework", "grading", submissionId)
];
```

好的 Key 同时承担：

- 资源唯一身份；
- 缓存隔离；
- 精确更新；
- 前缀失效；
- 测试可观察性。

### Query、Mutation、Invalidation

```text
Query        = 服务端资源当前是什么
Mutation     = 用户要求服务端做什么
Invalidation = 哪些旧资源可能已经过期
```

不要在 Mutation 后无差别刷新全站数据，也不要为每个服务端数组建立第二份 Local State。

### 适合直接 `setQueryData` 的情况

- 登录成功后服务端已经返回完整用户；
- AI 流式消息需要立即渲染中间快照；
- 删除会话后明确知道要移除哪个 Cache。

### 适合 Invalidate + Refetch 的情况

- 后端会重新计算人数和聚合值；
- 权限、重复检查或排序由后端决定；
- Mutation 返回值不包含完整新资源；
- 后台任务完成后多个派生资源都可能过期。

### 条件 Query 和轮询

使用 `enabled` 表达依赖是否就绪，使用 `refetchInterval(data)` 表达任务是否仍处于非终态。不要在组件里再创建一份轮询 Timer 和 Snapshot。

## 17. 不可变数据复习

React 把 State 视为时间上的快照。修改嵌套值时，只复制到目标的路径：

```ts
setDraft((previous) => ({
  ...previous,
  questions: previous.questions.map((question) =>
    question.id === changedId ? { ...question, title: nextTitle } : question,
  ),
}));
```

注意：

- `sort()`、`splice()` 会修改原数组，先复制；
- 从 Query Cache 放入表单的对象要克隆；
- 树构建不能给后端对象直接追加 children；
- Reducer 必须返回新状态，不能修改 Previous State；
- 稳定业务 ID 应作为 React Key，不要使用会变化的数组下标。

不可变更新的价值不仅是“触发渲染”，还包括：可预测历史快照、可靠引用比较、无副作用纯函数和更容易测试。

## 18. 自定义 Hook、Adapter 和纯函数的边界

### 自定义 Hook

适合拥有一段完整 React 生命周期：

- `useChatStreamRunner`：流式请求、Abort、Cache 更新；
- `useLiveSocketLifecycle`：连接、事件、Cleanup；
- Mutation Hook：命令和 Cache Invalidation。

不要只为了缩短组件就把几行代码包装成没有独立职责的 Hook。

### Adapter Component

适合把命令式第三方库转换为声明式 Props：

```text
React Props
-> Adapter
-> init/update/dispose 第三方实例
```

项目实例包括 ECharts 和 Tiptap。

### 纯函数

适合所有无外部副作用的转换：

- SSE Frame 解析；
- 消息事件 Reducer；
- 班级和作业分组；
- 递归树构建；
- 文件名解析；
- Dashboard 指标计算；
- Deadline 推导。

纯函数不需要 React 测试环境，测试速度快，失败定位清晰。

## 19. 安全知识复习

### 认证凭据分离

| 凭据             | 作用域           | 保存/传输                     |
| ---------------- | ---------------- | ----------------------------- |
| 教师 Session     | 教师后台         | HttpOnly Cookie，由浏览器携带 |
| CSRF Token       | 非安全方法       | `X-XSRF-TOKEN`                |
| 聊天 `sessionId` | 对话上下文       | Local Storage，不是认证凭据   |
| 学生课堂 Token   | 单一学生课堂身份 | STOMP/学生 API Bearer Header  |

### XSS 边界

- 普通用户文本使用 React Text Node，依赖自动转义；
- Markdown 先转 HTML，再 DOMPurify；
- Tiptap/KaTeX 转换前后都考虑消毒边界；
- 学生源码用 `<pre><code>` 文本显示，不作为 HTML；
- 服务端报告下载为 Blob，不用 `document.write` 注入当前 Origin；
- CSP 禁止生产内联脚本。

### 权限原则

前端隐藏按钮只改善 UX，后端授权才保护资源。公开页面不等于可以跳过 CSRF，Bearer Token 也不能与教师 Session 混用。

### 依赖安全

阶段中曾主动替换存在公告的旧 SheetJS 包，并升级存在 XSS 公告的 ECharts。最终 `npm audit --audit-level=moderate` 为 0。

## 20. 性能与代码分割

本项目使用两层边界：

```text
Route Lazy
-> 用户访问业务路由时才加载页面

Feature Lazy
-> 用户打开低频弹窗/抽屉/文件解析器时才加载
```

实例：

- 登录和注册独立路由 Chunk；
- AI 成果抽屉单独加载；
- 知识库设置/生成材料弹窗单独加载；
- `xlsx` 仅在导入/模板下载时加载；
- Tiptap/KaTeX 只进入作业相关路径；
- STOMP 只进入实时课堂；
- ECharts 只进入教学 Dashboard。

最终 ECharts Chunk 约 554.98 kB，gzip 后约 187.12 kB。它已经位于懒加载路由中，因此保留 Vite Warning 作为可见性能债务，没有抬高阈值掩盖问题。

性能优化顺序应是：先确认用户是否会下载，再分析 Chunk 内部组成，最后才决定进一步拆包或替换依赖。

## 21. 测试策略

### 测试分层

| 层次          | 测试对象                  | 项目例子                               |
| ------------- | ------------------------- | -------------------------------------- |
| 纯单元测试    | 无框架转换和 Reducer      | Tree、SSE Parser、Grouping、Deadline   |
| Store 测试    | 外部事件后的可观察快照    | STOMP Event -> Zustand State           |
| 组件测试      | 用户输入和 UI 变化        | 表单、弹窗、编辑器、倒计时             |
| 路由测试      | Loader、布局、导航和深链  | 认证重定向、邀请 Token、公开页面       |
| HTTP 集成测试 | 真实请求形状和 Cache 行为 | MSW + Axios/fetch + Query Client       |
| Adapter 测试  | 外部库生命周期            | ECharts init/dispose、STOMP subscribe  |
| 架构测试      | 跨目录和发布契约          | 禁止 Vue Import、17 路由、Docker/Nginx |

### 为什么使用 MSW

MSW 在网络边界拦截请求，测试仍会经过真实 API 函数、Axios/fetch、Result 解包、CSRF 和 Query。它比 Mock Axios 内部方法更接近应用实际行为。

### 测试可观察结果

不要只验证：

```text
callback 被调用
```

还要验证：

```text
事件到达
-> Store/Cache 产生正确新快照
-> 用户最终看到正确结果
```

阶段 8 就通过 Store 结果测试发现了 Helper 字段名与 Store 字段名不一致的问题。

## 22. 每阶段累计验收

|       阶段 | 测试文件 | 测试数 | Vite 模块 | 说明                 |
| ---------: | -------: | -----: | --------: | -------------------- |
| 0 Vue 基线 |        4 |     19 |     2,432 | 冻结旧行为           |
| 1 基础设施 |        3 |      9 |     1,642 | React 可独立运行     |
| 2 认证路由 |        5 |     16 |     1,657 | 17 路由骨架          |
| 3 班级管理 |        7 |     21 |     1,702 | 首个真实垂直切片     |
|  4 AI 对话 |       11 |     32 |     1,740 | SSE 与取消           |
|   5 知识库 |       14 |     40 |     1,758 | 递归资源与上传       |
|     6 作业 |       17 |     50 |     1,833 | 富文本与批改轮询     |
|     7 教学 |       21 |     60 |     2,438 | ECharts 与课前工作流 |
| 8 实时课堂 |       25 |     69 |     2,448 | STOMP 生命周期       |
| 9 生产切换 |       25 |     71 |     2,445 | 路由和发布契约       |

最终质量命令：

```bash
cd react-project
npm run ci:check
npm audit --audit-level=moderate
```

`ci:check` 顺序执行：

```text
Prettier -> TypeScript -> oxlint -> ESLint -> Vitest -> Vite build
```

---

# 第三部分：面试复习

## 23. 一分钟项目介绍

可以这样介绍：

> 我把一个 Spring Boot + Vue 3 的 AI 教学系统分十个阶段迁移到了 React 19。迁移不是逐文件翻译，而是先冻结 17 条路由以及 Session、CSRF、SSE、WebSocket 等行为契约，再按垂直业务切片迁移。服务端状态统一使用 TanStack Query，实时课堂的外部事件快照使用 Zustand，未保存表单留在局部 State，ECharts、Tiptap 和 STOMP 都通过带 Cleanup 的 Adapter/Hook 接入 React。最后同时切换 CI、Docker、Nginx 和文档，让 React 成为唯一生产前端。最终有 25 个测试文件、71 个测试，依赖审计无中高危漏洞。

## 24. 高频问题与回答要点

### 为什么选择 TanStack Query，而不是全部放 Zustand？

班级、任务、消息历史等具有服务端身份、缓存、过期、重试和失效语义，Query 能直接表达这些问题。Zustand 只用于实时 Socket 从组件树外推入、多个面板要共同消费的客户端快照，以及 AI 是否正在回答这类跨组件状态。

### 为什么认证用户也放 Query？

用户由 `/auth/me` 决定，是服务端 Session 的投影。它不是纯客户端偏好。设置无限 stale 后，只在登录、退出、401 或显式探测时变化，避免再复制到 Context/Store。

### 为什么有些服务端数据又复制到了 Local State？

作业和教案编辑器需要保存未提交修改。本地副本是有意的事务缓冲区；列表、统计和未编辑资源仍然只由 Query 拥有。

### 什么时候使用 Effect？

只在同步外部系统时使用，例如 STOMP、ECharts、Tiptap、Local Storage、Timer、Blob URL 和浏览器事件。过滤列表、默认值、合格率等都在渲染中派生。

### 为什么 AI SSE 不用 EventSource？

接口需要 POST、请求体、CSRF Header 和 AbortSignal，而 EventSource 不满足这些控制要求，所以使用 `fetch + ReadableStream + TextDecoder` 自己解析 SSE Frame。

### 如何处理网络分块？

维护 Buffer，增量解码 UTF-8，只在空行边界处分发完整 Frame，同时支持一个 Event 跨多个 Chunk、多个 Event 位于同一 Chunk 和多行 `data:`。

### 如何避免 WebSocket 重连后的状态问题？

初始 HTTP 快照完成后再连接；每次 `onConnect` 重新订阅；旧 Client Callback 要检查实例身份；Effect Cleanup 停止旧连接；事件通过 Store Reducer 不可变归并。

### 为什么倒计时不用每秒把剩余时间减一？

浏览器后台休眠会导致 Interval 漂移。服务端绝对 Deadline 是唯一事实，界面每次用 `deadline - Date.now()` 推导剩余时间。

### 如何保证 AI 生成内容安全？

普通文本依赖 React 转义；Markdown 转 HTML 后在最终插入 DOM 前使用 DOMPurify；公式转换前后都消毒；生产 CSP 禁止内联脚本；服务端权限仍保持权威。

### 为什么迁移最后必须改 CI 和 Nginx？

页面实现完成只代表源码存在。只有开发命令、测试、Docker 构建源、SPA Fallback、SSE/WebSocket 代理和文档同时指向 React，才真正结束双前端状态。

## 25. 可以重点讲的五个项目故事

### 故事一：从状态混乱到明确所有权

说明如何把服务器资源放 Query、实时快照放 Zustand、未保存草稿放 Local State、筛选和默认值直接派生，并举一个“不再使用同步 Effect”的具体例子。

### 故事二：实现可取消的 POST SSE

说明 EventSource 的限制、Chunk/Frame 区别、CSRF 重试、Abort 后保留部分答案、导航阻塞和纯 Reducer 测试。

### 故事三：可靠的实时课堂

说明 HTTP Hydration + STOMP 增量、教师/学生权限分离、重连重订阅、旧实例隔离、Effect Cleanup 和绝对 Deadline。

### 故事四：把命令式库接入 React

以 ECharts 为例讲 Ref 保存容器和实例、Mount Effect、Option Effect、ResizeObserver、Dispose；再说明同一思路如何用于 Tiptap 和 STOMP。

### 故事五：完成真正的生产切换

说明为什么同时切换 React Lockfile、CI、Docker、Nginx 和文档；如何测试 17 条路由、深层路由 Fallback、SSE、WebSocket 与 CSP。

## 26. 复习检查清单

如果下面的问题都能独立回答，就已经掌握了这次迁移的核心：

- [ ] 能解释为什么 Server State 不等于 Global Client State。
- [ ] 能为一个新接口设计 Query Key 和失效范围。
- [ ] 能判断一个值应放 URL、Query、Zustand、Local State 还是直接派生。
- [ ] 能解释 Effect 的 Cleanup 为什么在 Strict Mode 下重要。
- [ ] 能手写一个嵌套数组的不可变更新。
- [ ] 能解释 Loader 鉴权与组件内鉴权的区别。
- [ ] 能解释 SSE Event 与 Network Chunk 的区别。
- [ ] 能解释 Abort、失败和完成三种流式终态。
- [ ] 能解释 HTTP Snapshot 与 WebSocket Increment 的组合。
- [ ] 能解释为什么重连后必须重新订阅。
- [ ] 能把 ECharts 一类命令式库包装成 React Adapter。
- [ ] 能解释为什么公开 POST 仍然需要 CSRF。
- [ ] 能说明 DOMPurify 应放在哪个渲染边界。
- [ ] 能解释 SPA 深层路由为什么需要 Nginx Fallback。
- [ ] 能解释为什么 CI 要验证最终 Docker Artifact。

## 27. 后续学习方向

迁移阶段已经结束，后续应以正常 React 产品开发的方式继续演进：

1. 使用真实 PostgreSQL、Redis、MinIO、LLM 和双浏览器执行端到端 Smoke Test；
2. 为关键用户旅程加入 Playwright；
3. 为 ECharts、Tiptap、XLSX 等大 Chunk 建立明确性能预算；
4. 做键盘操作、焦点管理、颜色对比和屏幕阅读器检查；
5. 增加前端 Error Boundary、日志和 Web Vitals；
6. 在真实网络条件下验证 SSE 中断、STOMP 重连和文件上传恢复；
7. 新功能只在 `react-project/` 开发，不再同步维护 Vue。

## 28. 原始阶段文档

- [阶段 0：Vue 行为基线](./00-vue-baseline.md)
- [阶段 1：React 基础设施](./01-react-foundation.md)
- [阶段 2：认证与路由](./02-auth-routing.md)
- [阶段 3：班级管理垂直切片](./03-classroom-vertical-slice.md)
- [阶段 4：AI 流式对话](./04-ai-chat.md)
- [阶段 5：知识库](./05-knowledge-base.md)
- [阶段 6：作业](./06-homework.md)
- [阶段 7：教学数据](./07-teaching.md)
- [阶段 8：实时课堂](./08-live-classroom.md)
- [阶段 9：生产切换](./09-cutover.md)

原始文档记录了每一阶段当时的实现边界、详细测试清单和构建数据；本手册用于建立跨阶段知识体系和快速复习。
