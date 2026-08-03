# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is the **SPZX (尚硅谷甄选)** microservice platform — a fork of RuoYi-Cloud v3.6.3. It is a Spring Cloud Alibaba-based distributed e-commerce platform with a Vue.js frontend and management console.

## Build & Run

```bash
# Build the entire project (Java 21, Maven)
mvn clean install -DskipTests

# Build a specific module
mvn clean package -pl spzx-modules/spzx-product -am -DskipTests

# Run a single service (from the module directory)
mvn spring-boot:run -pl spzx-modules/spzx-system

# Or run the JAR directly
java -jar spzx-modules/spzx-system/target/spzx-system-*.jar
```

**Prerequisites:** Java 21, Maven, Docker (for infrastructure). Start infrastructure first:

```bash
cd docker
docker-compose up -d spzx-mysql spzx-redis spzx-nacos spzx-nginx
```

Then start individual services via Maven or IDE.

## Service Architecture

```
Nginx (80) → Gateway (8080) → internal services
                                   ├── spzx-auth (9200)      — Authentication center (JWT login/token)
                                   ├── spzx-system (9201)     — System management (users, roles, menus, depts)
                                   ├── spzx-gen (9202)        — Code generation
                                   ├── spzx-job (9203)        — Scheduled task execution
                                   ├── spzx-file (9300)       — File upload/storage (FastDFS)
                                   ├── spzx-monitor (9100)    — Spring Boot Admin monitoring
                                   ├── spzx-product (9205)    — Product catalog service
                                   ├── spzx-user (9206)       — Frontend user/member service
                                   ├── spzx-order (9207)      — Order management service
                                   ├── spzx-channel (9208)    — BFF/Aggregation gateway for H5 (no DB)
                                   ├── spzx-cart (9209)       — Shopping cart service
                                   └── spzx-payment (9210)    — Payment service (Alipay)
```

All services register with Nacos (192.168.6.100:8848) and pull shared config from Nacos (`application-dev.yml`).

## Module Organization

```
spzx-parent (pom)
├── spzx-gateway     — Spring Cloud Gateway (reactive), AuthFilter, XSS filter, validation code, Sentinel
├── spzx-auth        — Login/logout, JWT token creation & refresh
├── spzx-api/        — Feign client interfaces (shared between services)
│   ├── spzx-api-system   — RemoteUserService, RemoteLogService, RemoteFileService
│   ├── spzx-api-product, spzx-api-user, spzx-api-cart, spzx-api-order
├── spzx-common/     — Shared libraries
│   ├── spzx-common-core       — Core utils, JWT, Jackson, MyBatis-Plus, Knife4j, pagehelper
│   ├── spzx-common-security   — Auth annotations, Feign interceptor, security aspects (AOP)
│   ├── spzx-common-redis      — Redis config, @GuiguCache aspect for method-level caching
│   ├── spzx-common-datasource — Dynamic multi-datasource
│   ├── spzx-common-datascope  — Data permission scoping
│   ├── spzx-common-log        — Operation/audit logging
│   ├── spzx-common-seata      — Distributed transaction
│   └── spzx-common-rabbit     — RabbitMQ messaging
├── spzx-modules/    — Business services (each is a standalone Spring Boot app)
│   ├── spzx-system, spzx-product, spzx-user, spzx-order
│   ├── spzx-channel, spzx-cart, spzx-payment
│   ├── spzx-gen, spzx-job, spzx-file
├── spzx-visual/
│   └── spzx-monitor  — Spring Boot Admin
└── docker/           — Docker Compose for Nacos, MySQL, Redis, Nginx, plus per-service Dockerfiles
```

## Key Architecture Patterns

### Authentication flow
1. Client sends credentials to `spzx-auth:9200/login` (admin/backend) or `/h5/login` (frontend user) → gets JWT token
2. Client includes `Authorization: Bearer <token>` header on requests
3. Gateway `AuthFilter` (GlobalFilter, order -200) validates JWT, checks Redis for active session, forwards `user_id`, `user_key`, `username` as request headers to downstream services
4. Whitelisted URLs (configured in Nacos under `security.ignore.whites`) skip auth entirely
5. **Captcha validation** (`ValidateCodeFilter`) is applied as a GatewayFilter on `/auth/login` and `/auth/register`; can be disabled via `captchaProperties.enabled`
6. Downstream services use `HeaderInterceptor` (from spzx-common-security) to extract user headers into `SecurityContextHolder` (a Transmittable ThreadLocal), and auto-refresh the Redis session if expiry < 2 hours (total session TTL: 12 hours)
7. **Dual auth endpoints**: `TokenController` handles admin/backend users (calls `spzx-system` via Feign), `H5TokenController` handles mobile/H5 users (calls `spzx-user` via Feign, supports SMS verification codes)
8. **Inter-service calls**: `FeignRequestInterceptor` propagates user context across Feign calls; `@InnerAuth` annotation on the server side checks that `from-source` header equals `"inner"` — the gateway strips this header from external requests to prevent spoofing
9. `PreAuthorizeAspect` (AOP) intercepts `@RequiresPermissions`, `@RequiresRoles`, `@RequiresLogin` annotations for method-level authorization

### RBAC authorization
- `@RequiresPermissions("system:user:list")` — method-level permission checks
- `@RequiresRoles("admin")` — role-based checks
- `@PreAuthorizeAspect` enforces these via AOP, looking up permissions from `spzx-system`
- `@Logical.OR` / `@Logical.AND` controls multi-value logic

### Configuration
- Every service has a `bootstrap.yml` with `spring.application.name`, port, Nacos discovery + config URLs
- Database, Redis, Sentinel, and other runtime configs live in Nacos Config Center under `application-dev.yml` (shared across all services)
- Sentinel gateway rules stored in Nacos (`sentinel-spzx-gateway` dataId)
- No `application.yml` files locally — runtime config is fully Nacos-managed

### Data access
- MyBatis-Plus 3.5.3.1 for ORM (not plain MyBatis)
- `MybatisPlusConfig` configures pagination (`PaginationInnerInterceptor` with MySQL dialect) and enables `@MapperScan("com.spzx.**.mapper")`
- PageHelper for additional pagination support
- Dynamic datasource (`spzx-common-datasource`) for multi-database routing
- `@DataScope` annotation in spzx-common-datascope for row-level data permission filtering (dept-based)
- Modules that exclude DataSource (no DB dependency): gateway, auth, file, channel, cart — these use Redis or are purely aggregating/caching layers

### RabbitMQ messaging
Key exchanges and queues (defined in `MqConst` in spzx-common-rabbit):
- `spzx.product` → `spzx.unlock` / `spzx.minus` — inventory lock/release
- `spzx.payment` → `spzx.payment.pay` / `spzx.payment.close` — payment processing
- `spzx.cancel.order` → `spzx.cancel.order` — delayed order cancellation (1 min TTL dead-letter pattern)

`RabbitService` provides a wrapper utility for sending messages with confirm callback support.

### Custom annotations
- `@GuiguCache` (spzx-common-redis) — method-level Redis caching via AOP (`GuiguCacheAspect`)
- `@InnerAuth` — marks endpoints intended only for internal inter-service calls
- `@EnableCustomConfig` / `@EnableRyFeignClients` — composite annotations to bootstrap security + Feign configuration

### Docker deployment
- `docker-compose.yml` orchestrates: Nacos, MySQL (ry-cloud DB), Redis, Nginx (frontend), Gateway, Auth, System, Gen, Job, File, Monitor
- Nginx serves the Vue frontend from `/home/spzx/projects/spzx-ui` and proxies `/prod-api/` → Gateway

## Package Naming Convention

Base package: `com.spzx`. Service packages follow `com.spzx.<module>` (e.g., `com.spzx.product`, `com.spzx.channel`). Common packages: `com.spzx.common.*`.

## Repository Structure Note

This repo was recently initialized from a template. The `spzx-parent/` nested directory is a git-tracked output artifact — work in the root `F:\shangguigu\spzx-parent\` directory. The active branch is `dev`; `master` is the main branch.
