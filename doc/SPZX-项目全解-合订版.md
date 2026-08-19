# SPZX 尚硅谷甄选 · 项目全解（合订版）

> **本文档整合自 `doc/` 目录全部文档 + 代码深度解读（聊天讨论内容）**
>
> 整合来源：
> - `doc/架构文档.md` — 架构全景
> - `doc/请求链路与权限认证分析.md` — 全链路前置处理 + 权限认证
> - `doc/网关调用链路分析.md` — 网关过滤器详解
> - `doc/spzx-system-架构梳理.md` — 系统管理模块 + ER 图
> - `doc/系统管理后台-架构图说明.md` — 管理后台架构图
> - `doc/skywalking-全链路改造.md` — SkyWalking 监控
> - `doc/学习路线与项目全解.md` — 学习路线
> - 【聊天讨论】CacheRequestFilter 工厂模式、cacheRequestBodyAndRequest 源码解读、验证码 uuid 语义、SysLoginService 重构
>
> 版本：3.6.3（基于 RuoYi-Cloud v3.6.3 改造的电商微服务项目）｜分支：dev

---

## 目录

**第一部分 项目总览**
1. [项目定位与技术栈](#一项目定位与技术栈)
2. [服务清单与架构全景](#二服务清单与架构全景)
3. [请求流转全景](#三请求流转全景)

**第二部分 网关层深度剖析**
4. [网关过滤器总览](#四网关过滤器总览)
5. [CacheRequestFilter 深度解读（含聊天讨论）](#五cacherequestfilter-深度解读含聊天讨论)
   - 5.1 为什么这样写：工厂模式
   - 5.2 为什么用内部类 CacheRequestGatewayFilter
   - 5.3 网关框架调用哪个方法执行
   - 5.4 cacheRequestBodyAndRequest 源码逐行解读（`==` 判断的含义）
6. [AuthFilter 详解](#六authfilter-详解)
7. [验证码体系深度解读（含聊天讨论）](#七验证码体系深度解读含聊天讨论)
8. [其他过滤器与网关异常处理](#八其他过滤器与网关异常处理)

**第三部分 权限认证模块**
9. [JWT + Redis 双会话体系](#九jwt--redis-双会话体系)
10. [管理端登录与 SysLoginService 重构（含聊天讨论）](#十管理端登录与-sysloginservice-重构含聊天讨论)
11. [H5 登录 / 注册 / 注销 / 刷新](#十一h5-登录--注册--注销--刷新)
12. [密码策略 / RBAC 鉴权 / @InnerAuth / 会话续期](#十二密码策略--rbac-鉴权--innerauth--会话续期)

**第四部分 核心业务链路（电商主链路）**
13. [商品服务：缓存三兄弟与库存模型](#十三商品服务缓存三兄弟与库存模型)
14. [购物车：纯 Redis 实现](#十四购物车纯-redis-实现)
15. [订单：下单主流程与延迟关单](#十五订单下单主流程与延迟关单)
16. [支付：支付宝流程](#十六支付支付宝流程)
17. [Channel BFF 聚合层](#十七channel-bff-聚合层)
18. [RabbitMQ 消息架构](#十八rabbitmq-消息架构)

**第五部分 spzx-system 系统管理模块**
19. [系统管理模块架构与数据表](#十九系统管理模块架构与数据表)

**第六部分 运维与监控**
20. [SkyWalking 全链路监控](#二十skywalking-全链路监控)
21. [环境搭建与启动指南](#二十一环境搭建与启动指南)

**第七部分 学习路线与 FAQ**
22. [学习路线（7 个阶段）](#二十二学习路线7-个阶段)
23. [代码优化与待办清单（含本次重构）](#二十三代码优化与待办清单含本次重构)
24. [常见问题 FAQ](#二十四常见问题-faq)

---

# 第一部分 项目总览

## 一、项目定位与技术栈

**SPZX（尚硅谷甄选）** 是一个基于 **RuoYi-Cloud v3.6.3** 改造的**分布式微服务电商平台**，覆盖电商核心链路：

```
用户浏览商品(H5) → 加入购物车 → 提交订单 → 支付宝支付 → 扣减库存
                                     └→ 未支付 15 分钟自动取消(演示 1 分钟) → 解锁库存
```

同时保留了若依的**后台管理系统**（用户/角色/菜单/字典/日志/代码生成等）。

**技术栈**：

| 分类 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 21 |
| 框架 | Spring Boot | 3.0.5 |
| 微服务 | Spring Cloud | 2022.0.2 |
| 微服务组件 | Spring Cloud Alibaba | 2022.0.0.0-RC2 |
| 注册/配置中心 | Nacos | 2.x（gRPC 9848） |
| 网关 | Spring Cloud Gateway | **4.0.4**（WebFlux 响应式） |
| 流量控制 | Sentinel | Dashboard + Nacos 持久化 |
| ORM | MyBatis-Plus | 3.5.3.1 |
| 连接池 | Druid / HikariCP | 1.2.21 |
| 缓存 | Redis | — |
| MQ | RabbitMQ | — |
| 认证 | JWT (jjwt 0.9.1) + Redis 会话 | — |
| API 文档 | Knife4j (OpenAPI3) | 4.1.0 |
| 支付 | 支付宝 SDK | 4.8.73.ALL |
| 监控 | Spring Boot Admin | 3.0.4 |
| 链路追踪 | SkyWalking | 9.6.0 |

> ⚠️ 父 POM 用 Maven 属性占位符注入 Nacos 地址（`bootstrap.yml` 中的 `${nacos.addr}`），改地址只需改 `pom.xml` 顶部属性。

---

## 二、服务清单与架构全景

### 2.1 服务清单（13 个服务）

| 序号 | 服务名 | 端口 | 数据库 | 核心职责 |
|------|--------|------|--------|----------|
| 1 | spzx-gateway | 8080 | ✗ | API 网关：统一鉴权、验证码、XSS、Sentinel 流控 |
| 2 | spzx-auth | 9200 | ✗ | 认证中心：后台 `/login` + H5 `/h5/login` 双端登录 |
| 3 | spzx-system | 9201 | ✓ | 系统管理（RuoYi 后台：用户/角色/菜单/日志...） |
| 4 | spzx-gen | 9202 | ✓ | 代码生成器 |
| 5 | spzx-job | 9203 | ✓ | 定时任务（Quartz） |
| 6 | spzx-product | 9205 | ✓ | **商品服务**：SPU/SKU/分类/品牌/规格/库存锁定扣减 |
| 7 | spzx-user | 9206 | ✓ | 会员服务：H5 用户/收货地址/短信验证码/省市区 |
| 8 | spzx-order | 9207 | ✓ | **订单服务**：结算/下单/关闭订单/支付回调处理 |
| 9 | spzx-channel | 9208 | ✗ | **H5 BFF 聚合层**：无 DB，Feign 聚合商品数据 |
| 10 | spzx-cart | 9209 | ✗ | 购物车（纯 Redis Hash） |
| 11 | spzx-payment | 9210 | ✓ | 支付服务（支付宝 WAP 支付 + 异步回调） |
| 12 | spzx-file | 9300 | ✗ | 文件服务（FastDFS + MinIO） |
| 13 | spzx-monitor | 9100 | ✗ | Spring Boot Admin 监控 |

### 2.2 服务间调用关系（Feign 矩阵）

```
                    auth  system  product  user  order  channel  cart  payment
RemoteUserService      ✓                                              （spzx-system）
RemoteLogService       ✓
RemoteUserInfoService  ✓
RemoteFileService            ✓
RemoteCategoryService                          ✓  ← channel 调 product
RemoteBrandService                             ✓
RemoteProductService                           ✓   ✓(order)
RemoteUserAddressService  （auth 也调）          ✓  ← order 调 user
RemoteCartService                               ✓  ← order 调 cart
RemoteOrderInfoService                                        ✓  ← payment 调 order
```

**核心调用场景**：
- 后台登录：auth → system（查用户）→ system（写登录日志）
- H5 首页：channel → product（分类树 + 热销商品）
- 商品详情：channel → product（6 个接口并行聚合）
- 下单：order → cart（选中商品）+ product（价格/库存）+ user（收货地址）
- 支付：payment → order（查订单），支付成功经 MQ 通知 order，order 再经 MQ 通知 product 扣库存

### 2.3 模块组织

```
spzx-parent (pom)
├── spzx-gateway    — 网关 (8080)
├── spzx-auth       — 认证中心 (9200)
├── spzx-api/       — Feign 接口层（system/product/user/cart/order 5 个 API 模块）
├── spzx-common/    — 公共组件层（core/security/redis/datasource/datascope/log/seata/rabbit）
├── spzx-modules/   — 业务模块（system/gen/job/file/product/user/order/channel/cart/payment）
├── spzx-visual/    — spzx-monitor 监控中心
└── docker/         — Docker Compose（Nacos/MySQL/Redis/Nginx/SkyWalking + 服务镜像）
```

---

## 三、请求流转全景

```
浏览器/App
   │
   ▼
Nginx(80) ── /prod-api/* ──► Gateway(8080)
                               │ 过滤器链（按顺序）：
                               │  TraceIdFilter      (Global, order=-300) 全链路 traceId
                               │  CacheRequestFilter (Gateway) 缓存 POST/PUT 请求体
                               │  ValidateCodeFilter (Gateway) 登录/注册图形验证码校验
                               │  BlackListUrlFilter (Gateway) URL 黑名单拦截
                               │  XssFilter          (Global, order=-100) XSS 清洗
                               │  AuthFilter         (Global, order=-200) ★ JWT 鉴权+Redis 会话
                               │  Sentinel 网关流控  限流/降级
                               │  路由转发 lb://服务名 负载均衡
                               │
                               │  鉴权通过后注入请求头：x-user-id / x-username / x-user-key
                               │  并移除 from-source 头（防伪造内部调用）
                               ▼
                    ┌───────────┴────────────┐
                    │  路由: /auth/** /product/** │
                    │  /order/** /channel/** ...  │
                    └───────────┬────────────┘
                                ▼
             各业务服务 → MySQL / Redis / RabbitMQ / Seata / Sentinel
```

**一句话总结**：一个前端请求从发出到执行业务逻辑，依次经过 **axios 拦截器 → Nginx → 网关 6 类过滤器 → 下游服务拦截器/AOP 切面** 共约 10 层前置处理，其中 5 层与安全认证相关。

---

# 第二部分 网关层深度剖析

## 四、网关过滤器总览

网关是唯一外部入口（8080），基于 **Spring Cloud Gateway**（WebFlux 响应式）构建。先区分两种过滤器：

| 类型 | 实现方式 | 触发方式 | 本网关例子 |
|------|---------|---------|-----------|
| `GlobalFilter` | 直接实现接口 + `@Component` | 所有请求都经过 | `AuthFilter`、`XssFilter`、`TraceIdFilter` |
| `GatewayFilter` | 由**工厂**创建，按名字配置 | 只在配置了它的**路由**上生效 | `CacheRequestFilter`、`ValidateCodeFilter`、`StripPrefix` |

### 4.1 过滤器清单（按执行顺序）

| 顺序 | 过滤器 | 实现类型 | 优先级(Order) | 核心职责 |
|------|--------|----------|---------------|----------|
| 1 | `TraceIdFilter` | GlobalFilter | **-300** | 生成 traceId → `X-Trace-Id` 头 + MDC，`doFinally(MDC.clear())` 防泄漏 |
| 2 | `CacheRequestFilter` | GatewayFilter | — | 缓存 POST/PUT 请求体，解决流不可重复读 |
| 3 | `ValidateCodeFilter` | GatewayFilter | — | 仅 `/auth/login`、`/auth/register` 校验图形验证码 |
| 4 | `BlackListUrlFilter` | GatewayFilter | — | 正则匹配拦截黑名单 URL |
| 5 | `XssFilter` | GlobalFilter | **-100** | 清洗 JSON 请求体中的 XSS 脚本（条件启用） |
| 6 | `AuthFilter` | GlobalFilter | **-200** | ★ JWT 鉴权 + Redis 会话校验 + 注入用户头 |
| 7 | Sentinel 流控 | — | — | 网关级别限流（规则持久化 Nacos） |
| 8 | 路由转发 | — | — | `lb://` 负载均衡转发到下游微服务 |

> 优先级规则：**值越小越先执行**。`AuthFilter`(-200) 在 `XssFilter`(-100) 之后执行是合理的——XssFilter 需要读 Body 清洗，AuthFilter 只校验请求头不读 Body。

### 4.2 网关异常处理

| 处理器 | 触发条件 | 返回 |
|--------|----------|------|
| `GatewayExceptionHandler` | NotFoundException | "服务未找到" |
| `GatewayExceptionHandler` | ResponseStatusException | 异常 message |
| `GatewayExceptionHandler` | 其他 | "内部服务器错误" |
| `SentinelFallbackHandler` | BlockException（限流/降级） | "请求超过最大数，请稍候再试" |

### 4.3 辅助端点

- `GET /code`（RouterFunction 函数式路由）→ `ValidateCodeHandler` → kaptcha 生成图形验证码（`char`/`math` 两种模式）→ Base64 返回 `{uuid, img, captchaEnabled}`

---

## 五、CacheRequestFilter 深度解读（含聊天讨论）

> 📌 本节内容来自聊天中对 `CacheRequestFilter.java` 的逐行分析（结合 spring-cloud-gateway 4.0.4 源码）

### 5.1 为什么这样写：工厂模式（Factory Pattern）

```java
@Component
public class CacheRequestFilter extends AbstractGatewayFilterFactory<CacheRequestFilter.Config> {
```

网关路由是**声明式配置**（Nacos 的 `spzx-gateway-dev.yml`）：

```yaml
filters:
  - CacheRequestFilter      # ← 配置里写的是"名字"字符串
  - ValidateCodeFilter
  - StripPrefix=1
```

配置里写的是**字符串名字**，但真正执行过滤逻辑的是一个**对象**。从"名字"到"对象"需要一个桥——这就是 **`GatewayFilterFactory`（过滤器工厂）**。框架按名字从 Spring 容器找 `xxxGatewayFilterFactory` Bean，调用它的 `apply(config)` 方法创建真正的过滤器实例：

```java
@Override
public GatewayFilter apply(Config config) {          // 工厂方法：生产过滤器实例
    CacheRequestGatewayFilter cacheRequestGatewayFilter = new CacheRequestGatewayFilter();
    Integer order = config.getOrder();
    if (order == null) {
        return cacheRequestGatewayFilter;
    }
    return new OrderedGatewayFilter(cacheRequestGatewayFilter, order);  // 带执行顺序的包装
}
```

配套机制：
- `name()` 返回 `"CacheRequestFilter"`（默认规则：类名去掉 `GatewayFilterFactory` 后缀）→ YAML 里就能用这个名字匹配
- `Config` 泛型 + `shortcutFieldOrder()` → 支持 `- CacheRequestFilter=order=10` 快捷配置
- `@Component` 注册成 Spring Bean

**一句话**：`CacheRequestFilter` 是"工厂"，负责按配置**生产过滤器实例**；生产出来的 `CacheRequestGatewayFilter` 才是真正干活的"过滤器"。

### 5.2 为什么用内部类 CacheRequestGatewayFilter

1. **职责分离（工厂 vs 实例）**
   - 工厂类 `CacheRequestFilter`：注册 Bean、接收配置、按需包装（`OrderedGatewayFilter`）
   - 过滤器 `CacheRequestGatewayFilter`：只干一件事——缓存请求体
   - 这是 Spring Cloud Gateway 官方推荐的"工厂模式"写法

2. **内部类表达强绑定关系**
   - 这个过滤器**只属于**这个工厂，做成 `static` 内部类避免类文件爆炸，也表明"这是工厂的专属产品"
   - `static` 修饰保证不持有外部类引用，可独立 `new`，和普通类无异

3. **与 ValidateCodeFilter 的写法对比**
   - `ValidateCodeFilter` 是偷懒写法：`apply()` 里直接返回 lambda `(exchange, chain) -> {...}`
   - `CacheRequestFilter` 用正式写法：单独定义内部类实现 `GatewayFilter` 接口
   - 两者本质一样，但内部类写法更清晰、可维护、可复用（如需要 order 包装时 lambda 无法直接包装）

### 5.3 网关框架调用哪个方法执行？—— 分两个阶段

| 阶段 | 调用方法 | 触发时机 | 说明 |
|------|---------|---------|------|
| 阶段一 | `apply(Config)` | **启动时**解析路由 | `RouteDefinitionRouteLocator` 读 YAML 路由 → 按过滤器名找 `GatewayFilterFactory` Bean → 调 `apply()` 创建过滤器实例 |
| 阶段二 | `filter(exchange, chain)` | **请求到达时** | `DispatcherHandler` → `RoutePredicateHandlerMapping` 匹配路由 → `FilteringWebHandler` 把所有 GlobalFilter + 路由 GatewayFilter 按 order 排序 → 构建 `DefaultGatewayFilterChain` → 逐个调用 `filter()` |

完整执行入口（阶段二）：

```
请求到达
  └─ DispatcherHandler.handle(exchange)
       └─ RoutePredicateHandlerMapping 匹配路由（Path=/auth/** 命中）
            └─ FilteringWebHandler.handle(exchange)
                 ├─ 所有过滤器按 order 排序
                 └─ DefaultGatewayFilterChain
                      └─ chain.filter(exchange)
                           └─ 逐个调用 filter(exchange, chain)
                                ├─ CacheRequestGatewayFilter.filter()   ← 先执行（order 最小）
                                ├─ ValidateCodeFilter.filter()
                                └─ ... 最终 NettyRoutingFilter 转发下游
```

**直接回答**：执行入口是 **`CacheRequestGatewayFilter.filter(ServerWebExchange, GatewayFilterChain)`**，由框架的 **`FilteringWebHandler`**（通过 `DefaultGatewayFilterChain`）按责任链模式逐个调用。每个过滤器干完自己的活后调用 `chain.filter(exchange)` 把请求交给下一个。

### 5.4 cacheRequestBodyAndRequest 源码逐行解读（`==` 判断的含义）

> 📌 聊天中的核心问题：`serverHttpRequest == exchange.getRequest()` 不是恒为 true 吗？

**先回答**：这里的 `==` 是**对象引用比较**（判断"是不是同一个对象"），不是内容比较。在 4.0.x 中它**几乎恒为 false**——框架回调给你的 `serverHttpRequest` 大部分时候是一个**新建的装饰器对象**。

**框架源码**（`ServerWebExchangeUtils.cacheRequestBody`，gateway 4.0.4）：

```java
private static <T> Mono<T> cacheRequestBody(ServerWebExchange exchange, boolean cacheDecoratedRequest,
        Function<ServerHttpRequest, Mono<T>> function) {
    ServerHttpResponse response = exchange.getResponse();
    DataBufferFactory factory = response.bufferFactory();
    // ① 把请求体流聚合成一个 DataBuffer
    return DataBufferUtils.join(exchange.getRequest().getBody())
            // ② body 为空时兜底一个空 DataBuffer（避免流 empty）
            .defaultIfEmpty(factory.wrap(EMPTY_BYTES))
            // ③ 调用 decorate() 创建"装饰器"
            .map(dataBuffer -> decorate(exchange, dataBuffer, cacheDecoratedRequest))
            // ④ 兜底：万一上面流空了，才传原始 request
            .switchIfEmpty(Mono.just(exchange.getRequest()))
            // ⑤ 把结果（装饰器 or 原始 request）交给回调
            .flatMap(function);
}

private static ServerHttpRequest decorate(ServerWebExchange exchange, DataBuffer dataBuffer,
        boolean cacheDecoratedRequest) {
    if (dataBuffer.readableByteCount() > 0) {          // 有 body 才缓存
        exchange.getAttributes().put(CACHED_REQUEST_BODY_ATTR, dataBuffer);
    }
    // ★ 重点：无论有没有 body，都 new 一个装饰器（匿名内部类）
    ServerHttpRequest decorator = new ServerHttpRequestDecorator(exchange.getRequest()) {
        @Override
        public Flux<DataBuffer> getBody() {
            // getBody() 返回缓存的内容 → 可重复读取
            return Mono.fromSupplier(() -> { ... 从缓存取 ... }).flux();
        }
    };
    return decorator;   // 返回的是【新对象】
}
```

**关键点**：`decorate()` **无条件 `new` 一个新的 `ServerHttpRequestDecorator`**（有 body 时才额外把 body 存进 exchange attribute）。所以：

| 场景 | 回调收到的 `serverHttpRequest` | `== exchange.getRequest()` |
|------|------------------------------|---------------------------|
| POST 带 body（常规） | **新建的装饰器对象** | **false** |
| POST 空 body（Content-Length: 0） | 也是新建的装饰器 | false |
| 极少见（join 后流整体为空触发 `switchIfEmpty`） | **原始 request**（同一个引用） | **true** |

**结论：`==` 判断几乎恒为 false**，真正走的是第二个分支。

**两个分支的语义**：

```java
if (serverHttpRequest == exchange.getRequest()) {
    return chain.filter(exchange);      // 分支 A：原样继续链路（无 body 无需处理）
}
return chain.filter(exchange.mutate().request(serverHttpRequest).build());  // 分支 B：换请求后继续
```

- **分支 B（实际执行）**：框架创建了装饰器 → 用 `exchange.mutate().request(装饰器).build()` 生成**新的 exchange 副本**，把 request 换成装饰器。后续 `ValidateCodeFilter`（读 body）和 `NettyRoutingFilter`（转发 body）通过 `getBody()` 拿到**缓存内容，可反复读**
- **分支 A（防御性保留）**：回调收到原始 request → 无需 mutate，直接放行
- `ServerWebExchange` 是**不可变对象**，request/response 都是 final 的，只能 `mutate()` 复制一份再替换——所以分支 B 必须这么写

**为什么保留这个"恒为 false"的判断？**
1. **框架的设计契约**：`cacheRequestBodyAndRequest` 的 javadoc 明确写回调"可能是装饰器，**也可能在没有 body 时是原始 request**"；`switchIfEmpty(Mono.just(exchange.getRequest()))` 就是为"传原始 request"预留的通道
2. **早期版本行为不同**：旧版 Spring Cloud Gateway 在无 body 时确实直接回调原始 request，那时 `==` 判断**真有意义**
3. **4.x 改了实现**：`decorate()` 无条件创建装饰器，第一条路径几乎走不到，但留着它**没有副作用**（只多一次恒为 false 的比较），还能保证框架版本回退时逻辑依然正确——典型的防御性框架适配代码

**为什么这个过滤器"必须"存在（流只能读一次）**：
WebFlux 中请求体是 `Flux<DataBuffer>` **一次性流**，读一次就没了。如果 `ValidateCodeFilter` 先读了 body，后面的 `NettyRoutingFilter` 就转发空 body 给下游，登录直接失败。`CacheRequestFilter` 在过滤器链**最前面**把 body 缓存进 exchange attribute，`ValidateCodeFilter` 读一次、转发再读一次，各取所需。

**为什么 GET/DELETE 跳过**：GET/DELETE 通常没有 body（或 body 无意义），直接放行减少无谓开销。只有 POST/PUT/PATCH 这类带 body 的请求才处理。

---

## 六、AuthFilter 详解

**类定义**：`@Component public class AuthFilter implements GlobalFilter, Ordered`（order=-200）

**依赖注入**：

| 依赖 | 用途 |
|------|------|
| `IgnoreWhiteProperties ignoreWhite` | 白名单 URL（Nacos `security.ignore.whites`，带 `@RefreshScope` 热刷新） |
| `RedisService redisService` | 校验用户会话是否存在 |

**鉴权六步**：

```
① 白名单判断   StringUtils.matches(url, ignoreWhite.whites) → 命中直接放行
② 提取 Token   Authorization 头去掉 "Bearer " 前缀，为空 → 401 "令牌不能为空"
③ 解析 JWT     JwtUtils.parseToken() → Claims {user_id, user_key, username}
               解析失败/过期 → 401 "令牌已过期或验证不正确"
④ Redis 会话   校验 login_tokens:<user_key> 是否存在 → 不存在 → 401 "登录状态已过期"
⑤ 声明完整性   user_id/username 任意为空 → 401 "令牌验证失败"
⑥ 注入请求头   addHeader: user_id / username / user_key（URL 编码）
   安全清理     removeHeader("from-source") —— 防外部伪造内部调用
```

**JWT 令牌格式**：

```
请求头:  Authorization: Bearer eyJhbGciOiJIUzUxMiJ9.eyJ1c2VyX2lkIjoxLCJ1c2VyX2tleSI6...
解析后:  { "user_id": 1, "user_key": "5026f1e6-...", "username": "admin" }
会话键:  login_tokens:5026f1e6-...（Redis）
```

**设计要点**：
- **JWT + Redis 双重校验**：JWT 保证令牌可信（HS512 签名），Redis 保证会话真实存在（支持注销/踢人）
- **白名单热刷新**：`@RefreshScope`，Nacos 改配置即时生效
- **防伪造**：外部请求即使带 `from-source: inner` 头也会被网关剥离

---

## 七、验证码体系深度解读（含聊天讨论）

### 7.1 图形验证码（管理端登录/注册）

| 环节 | 实现 |
|------|------|
| 生成 | 网关 `GET /code` → kaptcha（`char`/`math` 两种模式，Nacos 可配）→ Base64 返回 `{uuid, img}` → 明文 code 存 Redis `captcha_codes:<uuid>`（2 分钟） |
| 校验 | 网关 `ValidateCodeFilter` 拦截 `/auth/login` `/auth/register`（受 `captchaProperties.enabled` 开关控制）→ 比对后**立即删除**（一次性防重放） |
| 开关 | `captchaProperties.enabled`（Nacos 动态控制，可临时关闭） |

### 7.2 短信验证码（H5 注册）

- `spzx-user GET /sms/sendCode/{phone}` → 生成 4 位随机码 → 存 Redis `phone:code:<phone>`（5 分钟）→ 调短信服务商发送（阿里云市场 API，模板 id 可配）
- H5 注册时比对 Redis 中的验证码

### 7.3 【聊天讨论】ValidateCodeServiceImpl 的 uuid 什么时候为空？

> 📌 聊天中的核心问题：`if (StringUtils.isEmpty(uuid)) throw new CaptchaException("验证码已失效")` 什么时候 uuid 是空的？

**uuid 从哪来——它是"前端回传"的，不是网关自己生成的**：

```java
// ValidateCodeFilter（网关过滤器）→ 从登录请求 body 中取出 code / uuid
JSONObject obj = JSON.parseObject(rspStr);
validateCodeService.checkCaptcha(obj.getString(CODE), obj.getString(UUID));   // CODE="code", UUID="uuid"

// uuid 的"出生地"在生成接口 GET /code（createCaptcha()）
String uuid = IdUtils.simpleUUID();                                  // ① 服务端生成随机 uuid
String verifyKey = CacheConstants.CAPTCHA_CODE_KEY + uuid;          // ② key = "captcha_codes:" + uuid
redisService.setCacheObject(verifyKey, code, 2, TimeUnit.MINUTES);  // ③ code 存 Redis，2 分钟过期
ajax.put("uuid", uuid);                                             // ④ 把 uuid 返回给前端
```

正常流程：**前端先调 `/code` 拿到 uuid+图片 → 用户看图输答案 → 登录时把 code 和 uuid 一起放进 body 提交**。

**uuid 为空 = 登录请求 body 里没有 `uuid` 字段（或值为空）**，即"前端没把验证码标识回传"：

| 场景 | 原因 |
|------|------|
| 前端**没先调 `/code`** 就直接登录 | 跳过了获取验证码步骤，body 里自然没有 uuid |
| 前端调了 `/code` 但**没把 uuid 回传** | 前端代码 bug，只传了 code 没传 uuid |
| **字段名/大小写写错** | 如传 `"UUID"`、`"Uuid"`、`"uuid "`（带空格） |
| **手动测试**（Postman/curl） | 自己拼的 body 里忘了带 uuid |
| body **不是合法 JSON** 或结构不对 | `obj.getString("uuid")` 返回 **null** |

`StringUtils.isEmpty()` 对 **null 和空串 `""`** 都返回 true。

### 7.4 【聊天讨论】报错文案的"小瑕疵"

"验证码已失效"这个提示其实**放错了位置**——它和真正"失效"的场景是错位的：

```
uuid 为空          → 报 "验证码已失效"   ← 实际是"没带"，不是"失效"
uuid 非空但过期    → Redis 查不到 → captcha = null
                     → code.equalsIgnoreCase(null) = false → 报 "验证码错误"  ← 真正"失效"却报"错误"
```

真正的"失效"（2 分钟 TTL 到期）会落到 `"验证码错误"` 分支，而 `"验证码已失效"` 分支捕获的其实是"参数缺失"。这是若依原版代码的不严谨之处。

### 7.5 验证码是一次性的（防重放）

```java
String captcha = redisService.getCacheObject(verifyKey);
redisService.deleteObject(verifyKey);    // ★ 读出来立刻删，无论校验结果如何
```

同一个 uuid 只能用一次；第二次复用必失败（Redis 已删）。

---

## 八、其他过滤器与网关异常处理

### 8.1 XssFilter（GlobalFilter, order=-100，条件启用）

- `@ConditionalOnProperty(security.xss.enabled=true)` 才注册
- 跳过条件：GET/DELETE、非 JSON Content-Type、命中 `excludeUrls`
- 处理：读 Body → `EscapeUtil.clean()` 清洗 HTML/JS 脚本 → 写回 → 删除 Content-Length、改 chunked 编码
- **依赖 `CacheRequestFilter` 先缓存 Body**（原始 Body 只能读一次）

### 8.2 BlackListUrlFilter（GatewayFilter）

- 黑名单 URL 正则匹配（支持 `**` 通配符 → `(.*?)`），命中返回 "请求地址不允许访问"
- 规则通过 Nacos 动态配置，支持热更新（`@RefreshScope`）

### 8.3 TraceIdFilter（GlobalFilter, order=-300）

- 为每个请求生成 UUID 形式 `traceId` → `X-Trace-Id` 头 + MDC
- `doFinally(MDC.clear())` 保证 MDC 不泄漏到其他请求
- 下游 Servlet Filter 透传 → Feign 透传 → 全链路日志串联（配合 SkyWalking）

### 8.4 路由配置（Nacos）

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: spzx-auth
          uri: lb://spzx-auth
          predicates: [Path=/auth/**]
          filters: [CacheRequestFilter, ValidateCodeFilter, StripPrefix=1]
        - id: spzx-product
          uri: lb://spzx-product
          predicates: [Path=/product/**]
          filters: [StripPrefix=1]
        # ... /code /schedule /system /file /user /order /channel /cart /payment /report
```

> `lb://` 前缀表示使用 Spring Cloud LoadBalancer 从 Nacos 注册中心获取服务实例负载均衡；`StripPrefix=1` 去掉匹配前缀（如 `/product/brand/list` → `spzx-product` 的 `/brand/list`）。

---

# 第三部分 权限认证模块

## 九、JWT + Redis 双会话体系

```
┌───────────────────────────────────────────────────────────────────┐
│  spzx-auth (9200) 认证中心                                         │
│                                                                    │
│  TokenController   → /login /logout /refresh /register  (管理端)   │
│  H5TokenController → /h5/login /h5/logout /h5/register (H5端)     │
│                                                                    │
│  登录成功 → LoginUser 对象                                          │
│    ① 生成 UUID 作为 user_key（会话唯一标识）                        │
│    ② 将 LoginUser(含权限/角色集合) 缓存 Redis 12 小时               │
│       key: login_tokens:<uuid>                                     │
│    ③ 签发 JWT（HS512，载荷仅含 user_key/user_id/username，无敏感）  │
│    ④ 返回 {access_token, expires_in}                               │
│                                                                    │
│  后续请求 → 网关 AuthFilter 解析 JWT + 校验 Redis → 注入请求头      │
│            → 下游 HeaderInterceptor 绑定上下文 + 会话续期           │
└───────────────────────────────────────────────────────────────────┘
```

**为什么 JWT 之外还要 Redis？**
- JWT 无状态不可吊销；Redis 会话可支持：注销立即失效、强制踢人、会话续期、权限变更即时生效（权限集合存 Redis 而非写进 JWT）

**下游服务四层防护**：

```
[第一层] 网关 AuthFilter      — JWT 令牌解析 + Redis 会话校验，白名单跳过
[第二层] HeaderInterceptor    — 提取请求头 → SecurityContextHolder (TransmittableThreadLocal)，剩余 TTL<2h 自动续期（总 12h）
[第三层] @PreAuthorizeAspect  — AOP 拦截 @RequiresLogin/@RequiresPermissions/@RequiresRoles
[第四层] @InnerAuthAspect     — 校验 from-source="inner"，外部无法伪造
```

---

## 十、管理端登录与 SysLoginService 重构（含聊天讨论）

### 10.1 登录链路

```
POST /auth/login（网关白名单放行 + 图形验证码校验）
  → TokenController.login
  → SysLoginService.login
  → Feign RemoteUserService.getUserInfo(username, "inner") → spzx-system
  → 密码校验（BCrypt + 错误次数锁定）
  → Feign RemoteLogService.saveLogininfor() 记录日志
  → TokenService.createToken() 签发 JWT
```

### 10.2 【聊天讨论】SysLoginService 重构记录

> 📌 聊天中对 `SysLoginService.java` 做了"优化代码逻辑和写法"的重构，核心内容如下。

**重构 1：修复 R.FAIL 判断顺序（真实逻辑缺陷）**

原代码（有 bug）：
```java
if (StringUtils.isNull(userResult) || StringUtils.isNull(userResult.getData())) {
    throw new ServiceException("登录用户：" + username + " 不存在");   // ← 先判 data 为 null
}
if (R.FAIL == userResult.getCode()) {
    throw new ServiceException(userResult.getMsg());                    // ← 后判 R.FAIL
}
```

问题：当 `spzx-system` 服务不可用时，`RemoteUserService` 的 fallback 返回 `R.fail("远程调用失败:xxx")`，此时 `data == null` → 走第一个分支，**把"服务挂了"误报成"用户不存在"**；且 `R.FAIL` 分支**漏记了失败日志**。

新代码（修正后）：
```java
if (StringUtils.isNull(userResult)) {           // ① R 本身为 null（极端情况）
    loginFail(username, "登录服务调用失败");
}
if (R.FAIL == userResult.getCode()) {           // ② 先判 R.FAIL → 返回真实降级原因 + 补记日志
    loginFail(username, userResult.getMsg());
}
if (StringUtils.isNull(userResult.getData())) { // ③ 最后才判"用户不存在"
    loginFail(username, "登录用户：" + username + " 不存在");
}
```

**重构 2：抽取统一失败处理方法，消除 6 处重复代码**

```java
private void loginFail(String username, String message) {
    recordLogService.recordLogininfor(username, Constants.LOGIN_FAIL, message);  // 记失败日志
    throw new ServiceException(message);                                         // 抛业务异常
}
```

**重构 3：代码风格** — `login()` 内步骤改为编号注释（1~9）、`userInfo` 变量复用、失败消息统一带用户名、补充 javadoc。

**刻意保留的部分**：
- `register()` 逻辑原样保留——注册失败不记"登录失败"日志（语义上注册失败≠登录失败），仍用原生 `throw`
- 所有异常消息文案、`SysPasswordService` 密码校验、`SysRecordLogService` 行为均未变

**行为对比**：

| 场景 | 原来 | 现在 |
|------|------|------|
| system 服务挂了 | 误报"用户不存在" | 报真实降级原因 + 记日志 |
| 用户不存在 | "登录用户：xxx 不存在" | 不变 |
| 账号停用 | 日志"用户已停用" / 异常带用户名 | 两者统一带用户名 |
| 参数校验失败 | 每处两行重复代码 | 统一走 `loginFail` |

### 10.3 登录校验步骤（重构后）

```
① 参数校验        用户名/密码非空、密码 5~20 位、用户名 2~20 位
② IP 黑名单       Redis 中 sys.login.blackIPList 正则匹配当前 IP
③ 查用户信息       Feign → spzx-system /user/info/{username}（@InnerAuth）
                   ↓ 返回 LoginUser（含 SysUser、roles、permissions 集合）
④ 账号状态        删除账号(delFlag) → "账号已被删除"
                  停用(status=1)   → "用户已停用，请联系管理员"
⑤ 密码校验        SysPasswordService（BCrypt + 错误次数锁定）
⑥ 记录日志        Feign → spzx-system 记录登录成功/失败日志
⑦ 签发令牌        TokenService.createToken() 返回 {access_token, expires_in}
```

---

## 十一、H5 登录 / 注册 / 注销 / 刷新

### 11.1 H5 登录（`POST /h5/login`）

链路：`H5TokenController.login` → `H5LoginService.login` → Feign 调 `spzx-user`

与管理端差异：

| 维度 | 管理端 (spzx-system) | H5 端 (spzx-user) |
|------|---------------------|-------------------|
| 用户表 | `sys_user` | `user_info` |
| 用户名规则 | 2~20 位 | 11 位手机号 |
| 权限/角色 | 有（RBAC 菜单权限） | 无（仅普通会员） |
| 登录后动作 | 直接签发 | 额外调 `updateUserLogin` 更新最近登录 IP/时间 |
| 会话内容 | LoginUser + sysUser + roles + permissions | LoginUser（userid/username/password/status） |

### 11.2 注册

**管理端注册（`POST /auth/register`）**：
```
① 参数校验        用户名 2~20 位、密码 5~20 位
② 组装 SysUser    password = BCrypt 加密
③ Feign 调 spzx-system /user/register（@InnerAuth）
   后端再次校验: 配置开关 sys.account.registerUser != true → 未开启注册；checkUserNameUnique 用户名唯一
④ 记录注册日志
```

**H5 注册（`POST /h5/register`）—— 手机号 + 短信验证码**：
```
① 参数校验        用户名必须 11 位（手机号）、密码 5~20 位、验证码必填
② 短信验证码校验   redisTemplate.get("phone:code:" + 手机号) 与提交 code 比对（5 分钟有效）
③ 组装 UserInfo   password = BCrypt 加密
④ Feign 调 spzx-user /userInfo/register（@InnerAuth）→ 唯一性校验 → 入库
⑤ 记录注册日志
```

### 11.3 注销 / 刷新

| 端点 | 逻辑 |
|------|------|
| `DELETE /auth/logout` | 取 token → `AuthUtil.logoutByToken(token)` 删 Redis 会话（**立即失效**）→ 记录退出日志 |
| `DELETE /h5/logout` | 同上（不记日志） |
| `POST /auth/refresh` | `tokenService.getLoginUser(request)` 从 Redis 取会话 → `refreshToken` 刷新有效期 → 无感续期 |

---

## 十二、密码策略 / RBAC 鉴权 / @InnerAuth / 会话续期

### 12.1 密码安全策略（SysPasswordService）

```
密码校验:
  ① 从 Redis 取 pwd_err_cnt:<username> 错误次数
  ② 次数 >= 5 → "密码输入错误5次，帐户锁定10分钟"（拒绝校验）
  ③ BCrypt matches 比对:
       失败 → 错误次数+1，写 Redis（TTL 10 分钟），提示"用户不存在/密码错误"
       成功 → 清除错误次数缓存
```

| 配置项 | 值 | 来源 |
|--------|-----|------|
| 最大重试次数 | 5 | `CacheConstants.PASSWORD_MAX_RETRY_COUNT` |
| 锁定时间 | 10 分钟 | `CacheConstants.PASSWORD_LOCK_TIME` |
| 加密算法 | BCrypt（`BCryptPasswordEncoder`） | 不可逆、自带盐，同一密码两次加密结果不同 |

> 登录失败统一提示"用户不存在/密码错误"，避免用户名枚举攻击。

### 12.2 RBAC 注解鉴权（AOP）

**权限模型**：`sys_user` ↔ `sys_user_role` ↔ `sys_role` ↔ `sys_role_menu` ↔ `sys_menu(perms)`

| 注解 | 校验内容 | 示例 |
|------|----------|------|
| `@RequiresLogin` | 是否已登录（Redis 会话存在） | 通用 |
| `@RequiresPermissions` | 权限标识，支持 `Logical.AND/OR` | `@RequiresPermissions("system:user:list")` |
| `@RequiresRoles` | 角色标识，支持 `Logical.AND/OR` | `@RequiresRoles("admin")` |

执行链：`PreAuthorizeAspect`（AOP）→ `AuthUtil` → `AuthLogic`：
```
① 从 SecurityContextHolder 中的 LoginUser（Redis 缓存）取权限/角色集合（登录时已组装，鉴权零 DB 查询）
② hasPermi: 含 "*:*:*" 或 PatternMatchUtils.simpleMatch(权限, 注解值) → 通过
③ hasRole:  含 "admin" 或 simpleMatch(角色, 注解值) → 通过
④ AND 逻辑：全部满足；OR 逻辑：任一满足，否则抛 NotPermissionException/NotRoleException
```

### 12.3 内部调用保护（@InnerAuth）

```
外部请求: 网关 AuthFilter 强制 removeHeader("from-source") → 无法携带 inner
内部调用: Feign 调用方显式传 @RequestHeader(FROM_SOURCE)="inner"
          + FeignRequestInterceptor 透传用户上下文
服务端:   InnerAuthAspect 校验 from-source=="inner"；isUser=true 还需 user_id/username 非空
```

被保护端点示例：`spzx-system /user/info/{username}`、`/user/register`、`spzx-user /userInfo/register` 等——均为 Feign 专用，外部不可达。

### 12.4 会话续期机制

```
登录:   LoginUser 缓存 Redis，expireTime = now + 720 分钟（12 小时）
请求:   下游 HeaderInterceptor → verifyToken
        若 expireTime - now <= 120 分钟 → refreshToken 重置 12 小时
效果:   活跃用户会话永不失效（滑动过期），非活跃用户 12 小时后需重新登录
```

---

# 第四部分 核心业务链路（电商主链路）

## 十三、商品服务：缓存三兄弟与库存模型

### 13.1 缓存三兄弟（ProductServiceImpl 是最好的教材）

- **穿透**（查不存在的数据）：`ItemServiceImpl` 用 **Redis Bitmap**（`sku:product:data`，bit=1 表示 SKU 存在）查不到直接拒绝；`getProductSku` 连 null 也缓存（短 TTL）
- **击穿**（热点 key 过期）：`getProductSku` 用 `setIfAbsent` 分布式锁，抢锁失败自旋重试；释放锁用 **Lua 保证原子性**（只释放自己的锁）
- **雪崩**（大量 key 同时过期）：过期时间 = 基础值 + `Random(0~5)` 分钟
- **一致性**：`updateProduct` 用**延时双删**（先删缓存 → 更新 DB → 睡 500ms → 再删缓存），配合短 TTL 兜底

### 13.2 库存模型（sku_stock 表四字段）

| 字段 | 含义 | 关系 |
|------|------|------|
| total_num | 总库存 | = available + lock + sale |
| lock_num | 锁定库存（下单未支付） | 下单 +，支付/取消 - |
| available_num | 可用库存 | 下单 -，解锁/取消 + |
| sale_num | 已售 | 支付 + |

三个关键 SQL（`SkuStockMapper.xml`）：
- `check`：`SELECT ... FOR UPDATE`（行锁）
- `lock`：`available_num = available_num - n, lock_num = lock_num + n WHERE available_num >= n`
- `minus`：`lock_num = lock_num - n, sale_num = sale_num + n`
- `unlock`：`available_num = available_num + n, lock_num = lock_num - n`

### 13.3 checkAndLock（库存锁定）

```
① Redis setnx "sku:checkAndLock:{orderNo}"（1h）防 Feign 重试
② 逐 SKU check（for update 行锁）→ 库存不足则返回错误串
③ 逐 SKU lock（乐观更新 available_num >= n）
④ 锁定明细存 Redis "sku:lock:data:{orderNo}"（不设过期，由解锁/扣减业务删除）
```

> 作业：检查+锁定可合并为一条 SQL —— `UPDATE sku_stock SET available_num=available_num-#{num}, lock_num=lock_num+#{num} WHERE sku_id=#{skuId} AND available_num >= #{num}`

## 十四、购物车：纯 Redis 实现

- Redis Hash：大 key `user:cart:{userId}`，小 key `{skuId}`，value 是 `CartInfo` JSON
- 上限：50 个商品/购物车，单商品 99 件
- 加购时 Feign 调 product 拿 SKU 信息；列表页远程拉最新价格对比提示
- `@InnerAuth` 接口：`getCartCheckedList` / `updateCartPrice` / `deleteCartCheckedList`（供 order 调用）

## 十五、订单：下单主流程与延迟关单

### 15.1 下单主流程（OrderInfoServiceImpl.submitOrder）

```
1. 防重复提交：Lua 脚本原子 "GET user:tradeNo:{userId} == tradeNo ? DEL : 0"
2. 校验订单项非空
3. 价格校验：Feign 批量查最新价，与提交价比对，变化则提示并刷新购物车价格
4. 库存锁定：Feign RemoteProductService.checkAndLock(orderNo, skuList)（见 13.3）
5. 保存订单：order_info + order_item + order_log（事务）
   → 异常则发 MQ 消息解锁库存
6. 删除购物车选中商品（Feign）
7. 发延迟消息：15 分钟后（演示 1 分钟）自动关单 → 消费者关单 + 解锁库存
```

### 15.2 延迟关单（RabbitMQ 死信队列方案）

RabbitMQ 原生不支持定时消息。方案：消息发到 `spzx.cancel.order` 交换机 → 队列设 TTL=1 分钟且无消费者 → 到期进入死信队列 → 死信消费者执行关单（`OrderReceiver.processCloseOrder` → `orderInfoService.processCloseOrder` → 状态置 -1 + 发 MQ 解锁库存）。

## 十六、支付：支付宝流程

```
用户点支付
  → /payment/alipay/submitAlipay/{orderNo}
  → 保存 payment_info（Feign 查订单）
  → 返回支付宝 WAP 支付表单（H5 跳转支付宝）
支付宝扣款成功
  → 异步回调 /payment/alipay/callback/notify
  → AlipaySignature.rsaCheckV1 验签（失败记 ERROR 日志，返回 failure）
  → 更新 payment_info 状态（TRADE_SUCCESS）
  → 发 MQ：spzx.payment → spzx.payment.pay → 订单号
订单服务监听
  → processPaySucess：order_status 0 → 1，记录支付时间
  → 发 MQ：spzx.product → spzx.minus → 订单号
商品服务监听
  → minus(orderNo)：setnx 去重 → 按锁定明细扣减库存 → 删除锁定缓存
```

> 注意：`AlipayServiceImpl` 中 `total_amount` 目前写死 `0.01`（教学演示用），生产应改为订单真实金额。

## 十七、Channel BFF 聚合层

**设计意图**：给 H5 前端提供"一次请求拿全"的接口，屏蔽后端微服务拆分细节。不连数据库，纯 Feign 聚合。

`ItemServiceImpl.item()` 用 `CompletableFuture` 编排 6 个 Feign 调用：

```
productSku（先取 SKU，拿到 productId）
 ├─ thenAccept → product（商品信息）
 ├─ thenAccept → productDetails（详情图）
 ├─ thenAccept → skuSpecValue（规格映射）
 └─ thenAccept → skuStock（库存）
runAsync → skuPrice（最新价格）
allOf(...).join() 阻塞等待全部完成
```

配套 `ThreadPoolConfig` 自定义线程池（核心/最大线程数、队列、拒绝策略），避免用公共 ForkJoinPool。

## 十八、RabbitMQ 消息架构

| 交换机 | 路由键 | 队列 | 用途 |
|--------|--------|------|------|
| `spzx.test` | `spzx.test` | `spzx.test` | 测试消息 |
| `spzx.test` | `spzx.confirm` | `spzx.confirm` | 确认回调测试 |
| `spzx.product` | `spzx.unlock` | `spzx.unlock` | 解锁库存 |
| `spzx.product` | `spzx.minus` | `spzx.minus` | 扣减库存 |
| `spzx.payment` | `spzx.payment.pay` | `spzx.payment.pay` | 支付处理 |
| `spzx.payment` | `spzx.payment.close` | `queue.payment.close` | 关闭支付 |
| `spzx.cancel.order` | `spzx.cancel.order` | `spzx.cancel.order` | 延迟取消订单（TTL 1min） |

**可靠性设计**：
- 生产端：`publisher-confirm-type: CORRELATED` + `publisher-returns: true`
- 消费端：`acknowledge-mode: manual` 手动 ack，业务成功才 `basicAck`
- 幂等：消费前 `setnx` 去重标记（`sku:unlock:{orderNo}` / `sku:minus:{orderNo}`，1h）

---

# 第五部分 spzx-system 系统管理模块

## 十九、系统管理模块架构与数据表

### 19.1 调用关系

```
管理后台 Vue (spzx-ui) ──► 网关 /system/** (AuthFilter 鉴权 + StripPrefix=1) ──► spzx-system :9201
spzx-auth :9200 ──Feign @InnerAuth──► spzx-system（登录查用户/权限、写操作与登录日志）
SysProfileController ──RemoteFileService Feign──► spzx-file :9300（头像上传）
spzx-system ──► Nacos（注册+配置）/ Redis（会话/在线用户）/ MySQL spzx-system 库
```

### 19.2 数据表总览（15 张）

| # | 表名 | 说明 | 主键 | 关键外键 |
|---|---|---|---|---|
| 1 | `sys_user` | 用户表 | `user_id` | `dept_id → sys_dept` |
| 2 | `sys_dept` | 部门表（树形） | `dept_id` | `parent_id` 自关联 |
| 3 | `sys_role` | 角色表 | `role_id` | — |
| 4 | `sys_menu` | 菜单/权限表（树形） | `menu_id` | `parent_id` 自关联 |
| 5 | `sys_post` | 岗位表 | `post_id` | — |
| 6 | `sys_config` | 参数配置表 | `config_id` | — |
| 7 | `sys_dict_type` | 字典类型表 | `dict_id` | — |
| 8 | `sys_dict_data` | 字典数据表 | `dict_code` | `dict_type` |
| 9 | `sys_notice` | 通知公告表 | `notice_id` | — |
| 10 | `sys_oper_log` | 操作日志表 | `oper_id` | — |
| 11 | `sys_logininfor` | 登录日志表 | `info_id` | — |
| 12 | `sys_user_role` | 用户-角色关联 | `(user_id, role_id)` | 双外键 |
| 13 | `sys_role_menu` | 角色-菜单关联 | `(role_id, menu_id)` | 双外键 |
| 14 | `sys_user_post` | 用户-岗位关联 | `(user_id, post_id)` | 双外键 |
| 15 | `sys_role_dept` | 角色-部门关联（数据权限） | `(role_id, dept_id)` | 双外键 |

### 19.3 ER 关系（Mermaid）

```mermaid
erDiagram
    sys_dept ||--o{ sys_user : "dept_id 归属"
    sys_dept ||--o{ sys_dept : "parent_id 自关联"
    sys_user ||--o{ sys_user_role : "user_id"
    sys_role ||--o{ sys_user_role : "role_id"
    sys_user ||--o{ sys_user_post : "user_id"
    sys_post ||--o{ sys_user_post : "post_id"
    sys_role ||--o{ sys_role_menu : "role_id"
    sys_menu ||--o{ sys_role_menu : "menu_id"
    sys_menu ||--o{ sys_menu : "parent_id 自关联"
    sys_role ||--o{ sys_role_dept : "role_id 数据权限"
    sys_dept ||--o{ sys_role_dept : "dept_id"
    sys_dict_type ||--o{ sys_dict_data : "dict_type 类型"
```

### 19.4 内部接口（@InnerAuth，仅 Feign 可调）

| 端点 | 调用方 | 功能 |
|---|---|---|
| `GET /user/info/{username}` | spzx-auth `RemoteUserService` | 查询用户 + 角色 + 权限，构造 LoginUser |
| `POST /user/register` | spzx-auth `RemoteUserService` | 注册用户 |
| `POST /operlog` | spzx-auth `RemoteLogService` | 落库操作日志 |
| `POST /logininfor` | spzx-auth `RemoteLogService` | 落库登录日志 |

### 19.5 关键设计要点

1. **RBAC 权限模型**：用户→角色(N:M)→菜单/按钮权限(N:M)，`perms` 配合 `@RequiresPermissions` 由 `PreAuthorizeAspect` 校验
2. **数据权限（行级）**：`@DataScope` + `sys_role_dept` + `sys_dept.ancestors` 递归查询实现部门级过滤
3. **逻辑删除**：`del_flag`（0存在/2删除），MyBatis-Plus 全局逻辑删除（`logic-delete-value: 2`）
4. **在线用户**：`sys_user_online` 为 Redis 虚拟表（无 mapper），由网关/Auth 写入 `login_tokens:*`，`SysUserOnlineController` 读取
5. **动态路由**：`/menu/getRouters` 根据用户权限生成前端路由（RouterVo/MetaVo/TreeSelect）
6. **配置/字典缓存**：config 与 dict 支持 `refreshCache` 手动刷新 Redis 缓存

---

# 第六部分 运维与监控

## 二十、SkyWalking 全链路监控

### 20.1 架构

```
SkyWalking UI :18080 (apache/skywalking-ui)
      │ HTTP :12800 (GraphQL)
      ▼
SkyWalking OAP :11800 (apache/skywalking-oap, 存储 H2/ES)
      │ gRPC :11800
      ▼
所有 Java 服务（Java Agent 注入，零代码侵入）
  JAVA_TOOL_OPTIONS=-javaagent:/skywalking/agent/skywalking-agent.jar
  SW_AGENT_SERVICE_NAME = 链路中的服务名
```

### 20.2 已启用插件（对应本项目技术栈）

| 插件 | 作用 |
|------|------|
| `apm-spring-cloud-gateway-4.x` | 网关入口 Span（BOM 实际解析为 Gateway **4.0.4**） |
| `apm-spring-webflux-6.x` / `apm-springmvc-annotation-6.x` | WebFlux/MVC Span |
| `apm-nacos-client-2.x` | Nacos 注册/配置 |
| `apm-mybatis-3.x` | MyBatis SQL Span |
| `apm-sentinel-1.x` / `apm-quartz-scheduler-2.x` | 限流/定时任务 |
| `apm-rabbitmq` / `apm-spring-cloud-feign-2.x` | MQ / Feign 跨服务链路 |
| `apm-lettuce-*` / `apm-mysql-8.x` | Redis / MySQL |

### 20.3 本地接入方式

```bash
# 方式一：辅助脚本
sh bin/skywalking-run.sh spzx-gateway
# 方式二：IDEA VM options
-javaagent:/绝对路径/docker/skywalking/agent/skywalking-agent.jar -Dskywalking.agent.service_name=spzx-system
# 方式三：java -jar
export JAVA_TOOL_OPTIONS="-javaagent:$(pwd)/docker/skywalking/agent/skywalking-agent.jar -Dskywalking.agent.service_name=spzx-gateway"
```

---

## 二十一、环境搭建与启动指南

### 21.1 基础设施清单

| 组件 | 地址（当前 pom 配置） | 备注 |
|------|----------------------|------|
| Nacos | 192.168.31.93:8848（LAN）/ 150.158.27.19:8848（云端，配置全） | 修改 `pom.xml` 的 `nacos.addr` |
| MySQL | 150.158.27.19:3306（root/940101zx） | 库：spzx-system / spzx-product / spzx-user / spzx-order / spzx-payment |
| Redis | 150.158.27.19:6379 | 网关/商品/购物车等 |
| RabbitMQ | 150.158.27.19:5672（guest/guest） | 订单/商品/支付 |

> ⚠️ 数据源、Redis、RabbitMQ 连接信息都来自 **Nacos 配置中心**（`spzx-xxx-dev.yml`），改配置在 Nacos 控制台改，不用改代码。

### 21.2 首次启动步骤

```bash
# 1. 编译（已修复 JDK21 兼容问题）
mvn clean install -DskipTests

# 2. 启动顺序（各自开终端，或 IDEA 逐个 Run）
mvn spring-boot:run -pl spzx-modules/spzx-system   # 9201
mvn spring-boot:run -pl spzx-auth                  # 9200
mvn spring-boot:run -pl spzx-gateway               # 8080

# 3. 验证
#    Nacos 控制台看到服务注册
#    后台登录（验证码需先调用 GET /code 获取）
curl -X POST http://localhost:8080/auth/login -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"admin123","code":"xxx","uuid":"xxx"}'
```

### 21.3 已知环境坑

1. **JDK 21 编译报 `com.sun:tools:1.8` 找不到** ✅已修复
   - 原因：seata-all 1.7.0 传递依赖 druid 1.2.6，其 POM 带 `com.sun:tools` system 依赖
   - 修复：父 POM `dependencyManagement` 统一 druid 版本为 1.2.21
2. **RabbitMQ 未启动**：订单/库存 MQ 链路不可用（服务能启动，监听器后台重连）
3. **Nacos 地址切换**：`pom.xml` 的 `<nacos.addr>` 决定所有服务连哪个 Nacos

---

# 第七部分 学习路线与 FAQ

## 二十二、学习路线（7 个阶段）

### 阶段 0：前置知识自查（约 1 周）
Java 21 + Lambda/Stream、Spring Boot 3 基础、Spring Cloud 核心概念、MySQL/Redis 基础、RabbitMQ 基础概念。
> 自查方式：能独立解释"服务注册到 Nacos 后，网关怎么把请求转发给它"即可进入下一阶段。

### 阶段 1：先跑起来（1~2 天）
启动 `spzx-system` → `spzx-auth` → `spzx-gateway`，验证 Nacos 注册 + 登录接口；对照 `seq.json` 时序图走一遍登录。
**里程碑**：理解"网关 → 认证中心 → 系统服务 → Redis 会话"主链。

### 阶段 2：基础设施组件逐个吃透（1 周）
| 组件 | 读什么 | 问自己 |
|------|--------|--------|
| Nacos | 各服务 `bootstrap.yml` | 配置中心与注册中心各解决什么问题？`shared-configs` 是什么？ |
| Gateway | `AuthFilter`、`ValidateCodeFilter`、`CacheRequestFilter` | 为什么 JWT 校验放网关？`StripPrefix=1` 作用？为什么 body 要缓存？ |
| Redis | `spzx-common-redis`、`GuiguCacheAspect` | `@GuiguCache` AOP 缓存原理？key 怎么生成？ |
| RabbitMQ | `MqConst`、`OrderReceiver`、`ProductReceiver` | 手动 ack 为什么必要？延迟消息如何用死信队列实现？ |
| Sentinel | `spzx-gateway-dev.yml` | 网关流控规则怎么持久化到 Nacos？ |

### 阶段 3：核心业务链路逐个攻破（2~3 周）
1. **认证链路**：`/login` 与 `/h5/login` 双端对照
2. **商品浏览**：channel 的 Index/Item/List/Category/Brand
3. **购物车**：Redis Hash 结构、数量上限、价格刷新
4. **下单结算**：防重复提交 → 价格校验 → 库存锁定 → 落库 → 删购物车 → 延迟关单
5. **支付**：支付宝 WAP 下单 → 异步回调验签 → MQ 通知订单 → MQ 扣库存
6. **库存**：sku_stock 四字段模型、锁定/解锁/扣减三张 SQL

### 阶段 4：公共模块源码精读（1 周）
按依赖层次自底向上：common-core（地基）→ common-redis → common-security（**重点**：HeaderInterceptor / TokenService / PreAuthorizeAspect / InnerAuthAspect / FeignRequestInterceptor）→ common-rabbit → 其余。

### 阶段 5：进阶主题
1. **缓存三兄弟**：读 `ProductServiceImpl.getProductSku()`（缓存+分布式锁+随机过期）、`ItemServiceImpl.item()`（bitmap 防穿透）、`updateProduct()`（延时双删）
2. **分布式锁**：`checkAndLock` 的 setnx 幂等标记、Lua 释放锁——思考锁过期怎么办？（引出 Redisson 看门狗）
3. **消息可靠性**：confirm 回调 + 手动 ack + 消费者幂等
4. **BFF 聚合**：`ItemServiceImpl` 6 个 Feign 并行
5. **接口幂等**：下单防重复提交的 Lua 脚本
6. **延迟队列**：TTL + 死信队列
7. **SkyWalking**：见第二十章

### 阶段 6：动手优化练习
1. **库存一步到位**（ProductServiceImpl 注释作业）：检查+锁定合并为一条 UPDATE
2. **二级缓存**：Redis 缓存加 Caffeine 本地缓存
3. **Redisson 替换自研分布式锁**（解决锁过期问题）
4. **支付金额**：`0.01` → 订单真实金额
5. **单元测试**：库存锁定并发场景

---

## 二十三、代码优化与待办清单（含本次重构）

> 本轮已完成的优化 ✅：

| 位置 | 优化内容 |
|------|----------|
| `pom.xml` | ✅ 修复 JDK21 下 seata/druid 1.2.6 依赖解析失败（统一 druid 1.2.21） |
| `CategoryServiceImpl.treeSelect` | ✅ 消除 N+1 查询：一次查询全部 id/parentId，用 Set 判断 hasChildren |
| `AlipayController.alipayNotify` | ✅ 补齐验签失败/交易状态异常的 ERROR 日志（原 TODO） |
| `OrderInfoServiceImpl` | ✅ 清理陈旧 TODO 注释（库存校验实际已实现） |
| `MqConst` | ✅ 说明延迟 1 分钟是演示配置，生产建议 15 分钟 |
| `RemoteOrderInfoService` | ✅ 修正 contextId 复制粘贴错误（remoteUserInfoService → remoteOrderInfoService） |
| `SysLoginService` | ✅ 重构：修复 R.FAIL 判断顺序（Feign 降级误报"用户不存在"）、抽取 `loginFail` 统一处理、步骤化注释（详见 10.2） |

> 建议继续做（学习练习）📌：

| 位置 | 建议 |
|------|------|
| `ProductServiceImpl.checkAndLock` | 按注释作业：检查+锁定合并为一条 UPDATE ... WHERE available_num >= n |
| `AlipayServiceImpl` | `total_amount` 从写死 0.01 改为订单真实金额 |
| `getProductSku` 分布式锁 | 替换为 Redisson，解决锁过期业务未完成的问题 |
| `CartServiceImpl` | 加购时校验 SKU 是否上架/库存，避免买下架商品 |
| 全局 | 给 Feign 接口补充统一配置（超时、重试） |
| 文档 | `doc/架构文档.md` 中标注 RemoteCartService/RemoteOrderInfoService "空壳待实现" 已过时（均已实现） |

---

## 二十四、常见问题 FAQ

**Q1：为什么 JWT 校验放网关而不是各服务？**
统一入口做一次鉴权，下游服务无需重复解析；同时集中管理白名单、验证码、XSS 等横切逻辑。

**Q2：JWT 本身不可撤销，怎么实现"退出登录立即失效"？**
JWT 只携带用户标识，真正的会话在 Redis（`login_tokens:{token}`）。退出时删 Redis 即可，网关校验 Redis 存在性，所以"不可撤销"问题被绕过了。

**Q3：Feign 调用为什么都带 `@RequestHeader(FROM_SOURCE)`？**
服务间调用必须带 `from-source: inner` 头才能通过 `@InnerAuth` 校验；该头在网关入口被强制移除，防止外部伪造。

**Q4：为什么下单选中的购物车商品在 Redis 而不在 MySQL？**
购物车是高频、低一致性的数据，Redis Hash 天然适合；订单系统通过 `@InnerAuth` 接口一次性取走选中项。

**Q5：库存锁定失败怎么办？**
`checkAndLock` 返回非空错误串 → 订单服务抛异常终止下单；Feign 重试被 Redis setnx 标记拦截（1h 内不重复锁定）；订单保存失败/超时未支付都会发 `unlock` 消息解锁库存。

**Q6：消息重复消费怎么处理？**
消费者用 `setnx "sku:unlock:{orderNo}"` 做幂等标记，重复消息直接跳过；配合手动 ack，业务成功才确认。

**Q7：延迟关单为什么用 RabbitMQ 死信队列？**
RabbitMQ 原生不支持定时消息。方案：消息发到 `spzx.cancel.order` 交换机，队列设 TTL=1 分钟且无消费者 → 到期进入死信队列 → 死信消费者执行关单。

**Q8：`@InnerAuth(isUser=true)` 和 `@InnerAuth` 区别？**
默认 `@InnerAuth` 只校验 `from-source: inner`；`isUser=true` 时额外从请求头解析用户信息放入 SecurityContext（看 CartController 中被注释的示例）。

**Q9：启动报 Nacos 连接失败？**
检查 `pom.xml` 的 `nacos.addr` 是否可达（`telnet 地址 8848`），并确认 Nacos 控制台已导入 `doc/nacos_config_export_*.zip`。

**Q10：从哪里看接口文档？**
服务启动后访问 `http://localhost:端口/doc.html`（Knife4j），网关聚合文档在 `http://localhost:8080/v3/api-docs`。

**Q11：CacheRequestFilter 里 `serverHttpRequest == exchange.getRequest()` 恒为 true 吗？**
不是，几乎恒为 false。`==` 是引用比较，框架的 `decorate()` 无条件 `new` 装饰器，回调收到的几乎总是新对象；该分支是防御性代码（详见 5.4）。

**Q12：验证码 uuid 什么时候为空？**
前端没先调 `/code`、调了没回传、字段名写错、手动测试没带、body 非合法 JSON 时，`obj.getString("uuid")` 返回 null/空（详见 7.3）。

---

## 附：核心类速查表

| 类 | 位置 | 职责 |
|----|------|------|
| `AuthFilter` | spzx-gateway/filter | 网关 JWT 鉴权 + Redis 会话校验 |
| `TraceIdFilter` | spzx-gateway/filter | 全链路 traceId 生成 |
| `CacheRequestFilter` / `CacheRequestGatewayFilter` | spzx-gateway/filter | 请求体缓存（工厂 + 内部过滤器） |
| `ValidateCodeFilter` / `ValidateCodeServiceImpl` | spzx-gateway | 图形验证码校验 / 生成 |
| `XssFilter` / `BlackListUrlFilter` | spzx-gateway/filter | XSS 清洗 / URL 黑名单 |
| `TokenController` / `H5TokenController` | spzx-auth/controller | 管理端 / H5 端认证端点 |
| `SysLoginService` / `H5LoginService` | spzx-auth/service | 登录/注册业务校验 |
| `SysPasswordService` | spzx-auth/service | 密码校验 + 错误锁定 |
| `SysRecordLogService` | spzx-auth/service | 登录日志（Feign 异步） |
| `TokenService` | spzx-common-security/service | 令牌创建/刷新/删除、会话缓存 |
| `HeaderInterceptor` | spzx-common-security/interceptor | 用户上下文绑定 + 会话续期 |
| `PreAuthorizeAspect` / `AuthLogic` / `AuthUtil` | spzx-common-security | 注解鉴权（权限/角色/登录） |
| `InnerAuthAspect` | spzx-common-security/aspect | 内部调用校验 |
| `FeignRequestInterceptor` | spzx-common-security/feign | Feign 调用上下文透传 |
| `SecurityContextHolder` | spzx-common-core/context | TransmittableThreadLocal 用户上下文 |
| `JwtUtils` | spzx-common-core/utils | JWT 创建/解析（HS512） |
| `ProductServiceImpl` | spzx-modules/spzx-product | 商品 CRUD + 库存锁定/扣减 + 缓存优化 |
| `OrderInfoServiceImpl` | spzx-modules/spzx-order | 下单主流程 + 关单 |
| `ItemServiceImpl` | spzx-modules/spzx-channel | BFF 聚合（CompletableFuture 并行 Feign） |
| `CartServiceImpl` | spzx-modules/spzx-cart | Redis Hash 购物车 |
| `AlipayController` / `PaymentInfoServiceImpl` | spzx-modules/spzx-payment | 支付宝支付 / 支付记录 |
| `RabbitService` / `MqConst` | spzx-common-rabbit | 消息发送封装 / 交换机队列常量 |
| `RedisService` / `GuiguCacheAspect` | spzx-common-redis | Redis 封装 / 方法级缓存 |
