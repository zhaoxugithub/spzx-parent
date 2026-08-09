# SkyWalking 全链路改造文档

> 版本: 3.6.3 | 日期: 2026-08-07 | 技术栈: SkyWalking 9.6.0 (OAP + UI + Java Agent)

---

## 一、改造目标

SPZX 微服务平台（Nginx → Gateway → Auth/System/Product/User/Order/Cart/Payment...）调用链长、中间件多（Nacos、Redis、MySQL、RabbitMQ、Feign）。本次改造引入 **Apache SkyWalking** 全链路监控，实现：

- **链路追踪（Tracing）**：一次用户请求跨多个微服务的完整调用链（拓扑 + 时间线）
- **性能剖析**：每个 Span 的耗时、参数、异常
- **拓扑自动发现**：自动绘制服务间调用关系图（HTTP/Feign/RabbitMQ/Redis/MySQL）
- **零侵入接入**：基于字节码增强（Java Agent），**不改一行业务代码**

---

## 二、架构总览

```
                        ┌──────────────────────────┐
                        │  SkyWalking UI :18080    │
                        │  (apache/skywalking-ui)  │
                        └────────────┬─────────────┘
                                     │ HTTP :12800 (GraphQL 查询)
                                     ▼
                        ┌──────────────────────────┐
                        │  SkyWalking OAP :11800   │
                        │  (apache/skywalking-oap) │
                        │  存储: H2(演示)/ES(生产)  │
                        └────────────┬─────────────┘
                                     │ gRPC :11800
          ┌──────────┬───────────┬───┴────┬───────────┬───────────┐
          ▼          ▼           ▼        ▼           ▼           ▼
   spzx-gateway  spzx-auth  spzx-system  spzx-gen  spzx-job  spzx-file ...
   (Java Agent) (Java Agent) (Java Agent) (Java Agent)(Java Agent)(Java Agent)

   每个服务通过环境变量注入 -javaagent，共享同一份 agent 目录
   SW_AGENT_SERVICE_NAME          = 链路中的服务名
   SW_COLLECTOR_BACKEND_SERVICES  = spzx-skywalking-oap:11800
```

### 新增/修改文件清单

| 文件 | 说明 |
|------|------|
| `docker/skywalking/download-agent.sh` | 下载 SkyWalking Java Agent 9.6.0 并启用本项目所需插件 |
| `docker/docker-compose.yml` | 新增 `spzx-skywalking-oap` / `spzx-skywalking-ui`；为全部 7 个容器服务注入 agent |
| `docker/deploy.sh` | 新增 `skywalking` 启动目标与端口(11800/12800/18080) |
| `docker/ruoyi/*/dockerfile` | 基础镜像 `openjdk:8-jre` → `eclipse-temurin:21-jre`（项目为 Java 21 编译，原镜像无法运行） |
| `bin/skywalking-run.sh` | 本地 `mvn spring-boot:run` 启动时注入 agent 的辅助脚本 |
| `.gitignore` | 忽略 agent 二进制与 OAP 数据目录 |
| `doc/skywalking-全链路改造.md` | 本文档 |

---

## 三、快速开始（Docker 一键部署）

### 3.1 下载 Agent（一次性）

```bash
# 下载 agent 到 docker/skywalking/agent，并自动启用网关/WebFlux/MVC6/Nacos/MyBatis/Sentinel/Quartz 插件
sh docker/skywalking/download-agent.sh
```

> agent 为二进制产物(~50MB)，已加入 `.gitignore`，不随仓库提交。

### 3.2 启动基础环境 + SkyWalking

```bash
cd docker
sh deploy.sh base         # MySQL + Redis + Nacos
sh deploy.sh skywalking   # OAP + UI（校验 agent 是否存在）
```

或直接：

```bash
docker-compose up -d spzx-mysql spzx-redis spzx-nacos spzx-skywalking-oap spzx-skywalking-ui
```

### 3.3 启动业务服务

```bash
sh deploy.sh modules      # nginx + gateway + auth + system
```

所有容器服务已通过 `JAVA_TOOL_OPTIONS=-javaagent:/skywalking/agent/skywalking-agent.jar` 自动挂载 agent（`docker-compose.yml` 中 `./skywalking/agent` 只读挂载，多容器共享一份）。

### 3.4 查看监控

浏览器打开 **http://localhost:18080**（UI 端口映射为 18080，避免与网关 8080 冲突）：

- 仪表盘（Dashboard）：服务吞吐/时延/成功率
- 拓扑图（Topology）：自动绘制调用链关系
- 追踪（Trace）：按服务/接口/关键字查询完整链路

---

## 四、本地开发接入（IDE / mvn）

本地运行的服务（含未容器化的业务模块 product/user/order/channel/cart/payment）通过 `bin/skywalking-run.sh` 或 IDE 配置接入。

### 4.1 方式一：辅助脚本（推荐）

```bash
# 先启动 SkyWalking
cd docker && docker-compose up -d spzx-skywalking-oap spzx-skywalking-ui

# 启动单个服务（agent 只作用于应用进程）
sh bin/skywalking-run.sh spzx-gateway
sh bin/skywalking-run.sh spzx-modules/spzx-system
sh bin/skywalking-run.sh spzx-modules/spzx-product
sh bin/skywalking-run.sh spzx-modules/spzx-order
...
```

### 4.2 方式二：IDEA Run Configuration

在启动类的 VM options 中添加（collector 默认 `127.0.0.1:11800`，无需配置）：

```
-javaagent:/Users/zhaoxu/IdeaProjects/spzx-parent/docker/skywalking/agent/skywalking-agent.jar
-Dskywalking.agent.service_name=spzx-system
```

> 注意：`-javaagent` 路径需为绝对路径；IDEA 中每个服务都要填（服务名不能重复）。

### 4.3 方式三：java -jar

```bash
export JAVA_TOOL_OPTIONS="-javaagent:$(pwd)/docker/skywalking/agent/skywalking-agent.jar -Dskywalking.agent.service_name=spzx-gateway"
java -jar spzx-gateway/target/spzx-gateway.jar
```

---

## 五、已启用的 Agent 插件（对应本项目技术栈）

| 插件 | 作用 | 说明 |
|------|------|------|
| `apm-spring-cloud-gateway-4.x` | 网关入口 Span | 本仓库 BOM 实际解析为 Gateway **4.0.4**，故启用 4.x 插件（3.x/4.x 互斥） |
| `apm-spring-webflux-6.x` | WebFlux Span | 网关依赖（可选插件已启用） |
| `apm-springmvc-annotation-6.x` | MVC 入口 Span | Spring Boot 3.0.5 → Spring Framework 6.x（可选插件已启用） |
| `apm-nacos-client-2.x` | Nacos 注册/配置 | 服务发现、配置拉取（可选插件已启用） |
| `apm-mybatis-3.x` | MyBatis SQL Span | MyBatis-Plus 3.5.3.1（可选插件已启用） |
| `apm-sentinel-1.x` | Sentinel 限流 | 网关/服务限流（可选插件已启用） |
| `apm-quartz-scheduler-2.x` | 定时任务 Span | spzx-job（可选插件已启用） |
| `apm-rabbitmq` | RabbitMQ 生产者/消费者 | 默认已启用，跨服务 MQ 链路自动串联 |
| `apm-spring-cloud-feign-2.x` / `apm-feign-default-http-9.x` | Feign 调用 Span | `spzx-api` 的 `RemoteXxxService` 跨服务调用 |
| `apm-lettuce-*` | Redis Span | 会话/缓存读写 |
| `apm-mysql-8.x` | MySQL JDBC Span | 数据库操作 |
| `apm-jdk-http-plugin` | JDK HttpClient | 默认已启用 |

> 全链路跨进程传播依赖 agent 自动注入的 `sw8` 请求头（HTTP/Feign）与 MQ 消息头，无需业务代码参与。

---

## 六、链路效果示例

以「用户登录」为例（`POST /auth/login`）：

```
Trace
├─ spzx-gateway   POST /auth/login                       (入口 Span)
│   ├─ Redis       GET/SET 会话/验证码                     (Redis Span)
│   ├─ Feign       RemoteUserService::getUserInfo        (跨服务 Span)
│   │   └─ spzx-system  GET /system/user/info            (下游入口 Span)
│   │       └─ MySQL     SELECT sys_user ...             (SQL Span)
│   └─ Feign       RemoteRecordLogService::saveLogininfor (审计日志)
│       └─ spzx-system  POST /system/logininfor
└─ spzx-auth    (独立段，经网关转发后由 auth 服务继续)
```

以「下单」为例（HTTP → Feign → RabbitMQ → 消费者）：

```
spzx-order (HTTP 入口) → Feign 调 spzx-cart/spzx-product → RabbitMQ 发送
    └─ spzx-product 消费者(@RabbitListener) → MySQL 扣减库存(SQL Span)
```

---

## 七、常见问题（FAQ）

### 7.1 为什么 SkyWalking UI 用 18080 而不是 8080？
网关占用 8080，UI 容器内 8080 映射到宿主机 `18080`，避免冲突。

### 7.2 Agent 连不上 OAP？
- Docker 部署：检查 `SW_COLLECTOR_BACKEND_SERVICES=spzx-skywalking-oap:11800` 是否正确注入，OAP 是否已启动（agent 会自动重试）。
- 本地部署：默认连 `127.0.0.1:11800`，需先启动 OAP；OAP 日志位于 `docker/skywalking/oap-data/logs`。

### 7.3 拓扑图里看不到某个服务？
- 确认该服务 JVM 启动参数里带有 `-javaagent`，且 `SW_AGENT_SERVICE_NAME`（或 `-Dskywalking.agent.service_name`）各不相同。
- SkyWalking 采用采样策略，低流量时拓扑可能延迟出现，多刷几次请求即可。

### 7.4 存储选型？
当前 OAP 使用 **H2 文件存储**（`docker/skywalking/oap-data`，重启不丢）。生产环境建议改用 Elasticsearch：

```yaml
environment:
  - SW_STORAGE=elasticsearch
  - SW_STORAGE_ES_CLUSTER_NODES=es01:9200
```

或复用本项目 MySQL（需执行 SkyWalking 官方建表脚本，初始化较慢，不推荐入门使用）。

### 7.5 采样率如何调整？
agent 默认 `agent.sample_n_per_3_secs=-1`（全量采样）。压测或高流量环境可调低：

```
# 每 3 秒采样 1 条
SW_AGENT_SAMPLE=1
```

---

## 八、端口汇总

| 端口 | 用途 |
|------|------|
| 11800 | OAP gRPC（agent 上报，Docker 内部 + 宿主机） |
| 12800 | OAP HTTP（UI 查询接口） |
| 18080 | SkyWalking UI（Web 控制台） |
| 8080 | 网关（原有，未被占用） |
