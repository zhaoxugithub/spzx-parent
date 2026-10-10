#!/usr/bin/env bash
# =============================================================================
# remote-deploy.sh —— spzx 前后端一键远程部署（gateway / auth / system / ui）
#
#   本地 Maven/npm 打包  →  上传产物  →  远程 docker build 生成镜像
#   →  docker compose 启动容器  →  验证 Nacos 注册与外网端到端访问
#
# 服务组成
#   后端  spzx-gateway(容器 8080) / spzx-auth(9200) / spzx-system(9201)  ← Maven jar
#   前端  spzx-ui（Vite 打包的 dist，由 nginx:alpine 托管，容器 80，
#                 /prod-api/ 反向代理到 spzx-gateway:8080）
#
# 前置条件
#   本机：JDK 21 + Maven 3.9 + Node/npm + ssh + scp（或 rsync）
#         本机无需安装 Docker，镜像在服务器上构建
#   服务器：Docker + Docker Compose v2，且已运行 Nacos/MySQL/Redis
#           （本项目 pom.xml 中 nacos.addr 指向的机器）
#
# 用法（完整命令操作文档：doc/远程部署-脚本使用说明.md）
#   ./docker/remote-deploy.sh [命令] [服务...]
#     all(默认)   对选中服务执行 build → pack → image → up → verify
#     build       本地打包（后端 mvn package；前端 npm run build:prod）
#     pack        上传产物 + 生成 Dockerfile/nginx.conf/compose 到服务器
#     image       远程 docker compose build
#     up          远程 docker compose up -d
#     stop        远程 docker compose stop
#     down        远程 docker compose down（指定服务时为 rm -sf）
#     restart     远程 docker compose restart
#     status      容器状态 + Nacos 注册状态
#     logs <svc>  查看日志（gateway|auth|system|product|ui，第二个参数 -f 跟随）
#     verify      验证（注册状态 + 外网接口 + 可选完整登录链路）
#     clean       清理服务器上悬空镜像
#     help        帮助
#
# 指定服务（三种等价写法；只影响本次操作，远程 compose 文件始终包含全部服务）
#   ./docker/remote-deploy.sh build auth          # 只打包 auth
#   ./docker/remote-deploy.sh up auth ui          # 只启动 auth 和 ui
#   ONLY="auth ui" ./docker/remote-deploy.sh all  # 只处理 auth 与 ui
#   服务名：gateway | auth | system | product | ui
#
# 部署注意事项
#   1) 默认端口：前端 ui 占用 80，网关让到 19080（前端 nginx 内 /prod-api/ 反代到 spzx-gateway:8080）
#      若本次不部署前端，可用 GATEWAY_PUBLISH="80:8080 19080:8080" 把网关放回 80
#   2) 每次 pack 都会重新生成服务器上的 docker-compose.yml，覆盖前会自动备份为
#      docker-compose.yml.bak.<时间戳>；如有人工改动请在备份文件里找
#   3) CDS=1（默认）会给三个 Java 服务挂载 $CDS_DIR/<服务> → /home/spzx/cds 存放类共享归档
#   4) spzx-product 依赖 RabbitMQ（Nacos 配置里是 150.158.27.19:5672）。
#      服务器上目前没有 RabbitMQ，商品服务的 @RabbitListener 会一直重连报错（服务仍能启动、
#      仍会注册到 Nacos，但库存解锁/扣减相关消息不可用）。要完整可用需先起一个 RabbitMQ：
#        docker run -d --name rabbitmq --restart unless-stopped -p 5672:5672 -p 15672:15672 rabbitmq:3-management
#      若不需要 MQ，也可以只部署 product 做接口联调，忽略连接报错日志。
#   5) 网关路由 /product/** 已在 Nacos（spzx-gateway-dev.yml）里配好，product 注册后即可通过
#      网关访问：http://<host>:19080/product/brand/list（需要 token；白名单只放行 /product/test/**）
#
# 换一台服务器部署（所有地址都能外部指定，无需改脚本）
#   1) SSH_HOST=root@<新IP>  HOST_IP=<新IP>              # HOST_IP 默认从 SSH_HOST 推导，走跳板机时要显式指定
#   2) NACOS_ADDR=<新IP>:8848                            # 会被 -Dnacos.addr 打进 jar，必须重新 build（不能用 SKIP_MAVEN=1）
#   3) NACOS_API=http://<新IP>:8848                       # 仅脚本查注册状态用；Nacos 在目标机本机就保持默认
#   4) 目标机的 Nacos 里必须已有 application-dev.yml 和各 spzx-<服务>-dev.yml，
#      且其中的 MySQL/Redis/RabbitMQ 地址指向目标机自己的基础设施（脚本不生成/不修改这些配置）
#   5) 目标机需有 Docker + Compose v2；端口/目录/镜像名分别用 GATEWAY_PUBLISH、UI_PUBLISH、
#      REMOTE_DIR、TAG 覆盖；SSH 需免密（BatchMode，不接受交互输入）
#   例：SSH_HOST=root@10.0.0.9 HOST_IP=10.0.0.9 NACOS_ADDR=10.0.0.9:8848 SENTINEL_DASHBOARD=10.0.0.9:8858 \
#        ./docker/remote-deploy.sh all gateway auth system ui
#
# 环境变量（均可覆盖）
#   SSH_HOST=root@150.158.27.19   SSH_PORT=22   REMOTE_DIR=/opt/spzx
#   HOST_IP=150.158.27.19         对外访问验证用的公网 IP（默认取 SSH_HOST 的 @ 后半段）
#   TAG=latest                    IMAGE_PREFIX=spzx
#   GATEWAY_PUBLISH="19080:8080"  网关对外端口（80 默认留给前端）
#   UI_PUBLISH="80:80"            前端对外端口
#   INTERNAL_BIND=127.0.0.1       auth/system 绑定地址，默认只绑回环不对外暴露
#   ONLY="auth ui"                SKIP_MAVEN=1   SKIP_JARS=1   XFER=auto|scp|rsync
#   NACOS_ADDR=150.158.27.19:8848 打包时 -Dnacos.addr 写进 jar + 上传前预检
#   NACOS_API=http://127.0.0.1:8848   脚本查注册状态用的 Nacos 地址（在目标机上 curl）
#   SENTINEL_DASHBOARD=               可选，打包时 -Dsentinel.dashboard 写入；留空用 pom 默认值
#   ---- 内存相关（服务器内存紧张时的重点）----
#   GATEWAY_HEAP=192m AUTH_HEAP=192m SYSTEM_HEAP=320m PRODUCT_HEAP=384m   各 JVM -Xmx
#   GATEWAY_MEM= AUTH_MEM= SYSTEM_MEM= PRODUCT_MEM=      可选 cgroup 硬上限（默认不限制）
#   UI_MEM=64m                                          前端 nginx 硬上限
#   JVM_OPTS_EXTRA=""                                   追加到 JAVA_OPTS 末尾
#   CDS=1                                               启用 CDS 类数据共享（-XX:+AutoCreateSharedArchive）
#   CDS_DIR=/opt/spzx/deploy/cds                         CDS 归档在宿主机的存放目录
#   UI_BUILD_CMD=build:prod   SKIP_UI_BUILD=1
#   ---- verify 完整登录链路（第⑦步）需要 ----
#   REDIS_PASSWORD=xxx  REDIS_CONTAINER=容器名  或  REDIS_ADDR=127.0.0.1:6379 REDIS_IMAGE=redis:8.8.0
#   LOGIN_USER=admin    LOGIN_PASSWORD=admin123
#
# 内存参考（默认参数下每个容器的粗略 RSS）
#   gateway ≈ 330MB   auth ≈ 330MB   system ≈ 480MB   product ≈ 520MB   ui(nginx) ≈ 15MB
#   四个一起约 1.15GB。内存更紧时：调小 *_HEAP（如 160m），用 *_MEM 设 cgroup 上限，
#   或只部署本次需要的服务（如 ./docker/remote-deploy.sh all ui auth）。
#
# 例：
#   ./docker/remote-deploy.sh                      # 全部服务走一遍全流程
#   ./docker/remote-deploy.sh all ui               # 只部署前端
#   ONLY=system SKIP_MAVEN=1 ./docker/remote-deploy.sh all
#   TAG=v1.0 ./docker/remote-deploy.sh all
#   ./docker/remote-deploy.sh logs ui
# =============================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# ⚠ 兼容性提醒（踩过的坑，勿回退）：
#   消息字符串里的变量一律写成 ${VAR}，不要写成 $VAR 直接跟中文/全角符号。
#   在 UTF-8 locale 的 bash 下，$VAR（/ $VAR： 会被解析成变量名的一部分，
#   配合 set -u 直接报 "unbound variable" 并中止脚本（例：warn "...$BACKUP_NAME（...）"）。

SSH_HOST="${SSH_HOST:-root@150.158.27.19}"
SSH_PORT="${SSH_PORT:-22}"
REMOTE_DIR="${REMOTE_DIR:-/opt/spzx}"
TAG="${TAG:-latest}"
IMAGE_PREFIX="${IMAGE_PREFIX:-spzx}"
GATEWAY_PUBLISH="${GATEWAY_PUBLISH:-19080:8080}"
UI_PUBLISH="${UI_PUBLISH:-80:80}"
INTERNAL_BIND="${INTERNAL_BIND:-127.0.0.1}"
ONLY="${ONLY:-}"
SKIP_MAVEN="${SKIP_MAVEN:-0}"
SKIP_JARS="${SKIP_JARS:-0}"
NACOS_ADDR="${NACOS_ADDR:-150.158.27.19:8848}"
# 脚本查注册状态用的 Nacos 地址（在目标机上执行 curl，默认走本机回环）
NACOS_API="${NACOS_API:-http://127.0.0.1:8848}"
# Sentinel 控制台地址（仅打包时写入配置，留空则用 pom 里的默认值）
SENTINEL_DASHBOARD="${SENTINEL_DASHBOARD:-}"
XFER="${XFER:-auto}"   # 传输方式：auto|scp|rsync

# 内存相关
GATEWAY_HEAP="${GATEWAY_HEAP:-192m}"
AUTH_HEAP="${AUTH_HEAP:-192m}"
SYSTEM_HEAP="${SYSTEM_HEAP:-320m}"
GATEWAY_MEM="${GATEWAY_MEM:-}"
AUTH_MEM="${AUTH_MEM:-}"
SYSTEM_MEM="${SYSTEM_MEM:-}"
PRODUCT_HEAP="${PRODUCT_HEAP:-384m}"
PRODUCT_MEM="${PRODUCT_MEM:-}"
UI_MEM="${UI_MEM:-64m}"
JVM_OPTS_EXTRA="${JVM_OPTS_EXTRA:-}"

# CDS（Class Data Sharing）：把类元数据做成归档，降低启动时间与元数据内存占用
CDS="${CDS:-1}"
CDS_DIR="${CDS_DIR:-$REMOTE_DIR/deploy/cds}"

# 前端
UI_DIR="${UI_DIR:-spzx-ui}"
UI_BUILD_CMD="${UI_BUILD_CMD:-build:prod}"
SKIP_UI_BUILD="${SKIP_UI_BUILD:-0}"

# verify 第⑦步所需
REDIS_PASSWORD="${REDIS_PASSWORD:-}"
REDIS_ADDR="${REDIS_ADDR:-127.0.0.1:6379}"
REDIS_CONTAINER="${REDIS_CONTAINER:-}"
REDIS_IMAGE="${REDIS_IMAGE:-redis:8.8.0}"
LOGIN_USER="${LOGIN_USER:-admin}"
LOGIN_PASSWORD="${LOGIN_PASSWORD:-admin123}"

GATEWAY_EXTERNAL_PORT="$(echo "$GATEWAY_PUBLISH" | awk '{print $1}' | cut -d: -f1)"
GATEWAY_EXTERNAL_PORT="${GATEWAY_EXTERNAL_PORT:-19080}"
UI_EXTERNAL_PORT="$(echo "$UI_PUBLISH" | awk '{print $1}' | cut -d: -f1)"
UI_EXTERNAL_PORT="${UI_EXTERNAL_PORT:-80}"
HOST_IP="${HOST_IP:-${SSH_HOST#*@}}"

SSH_OPTS=(-n -o BatchMode=yes -o StrictHostKeyChecking=accept-new -o ConnectTimeout=15 -p "$SSH_PORT")
RSYNC_SSH="ssh -p $SSH_PORT -o BatchMode=yes -o StrictHostKeyChecking=accept-new"
SCP_SSH_OPTS=(-o BatchMode=yes -o StrictHostKeyChecking=accept-new -P "$SSH_PORT")

# 服务定义：名称|类型|Maven 模块|产物路径|容器端口|堆
ALL_SERVICES=(
  "gateway|maven|:spzx-gateway|spzx-gateway/target/spzx-gateway.jar|8080|${GATEWAY_HEAP}"
  "auth|maven|:spzx-auth|spzx-auth/target/spzx-auth.jar|9200|${AUTH_HEAP}"
  "system|maven|:spzx-system|spzx-modules/spzx-system/target/spzx-system.jar|9201|${SYSTEM_HEAP}"
  "product|maven|:spzx-product|spzx-modules/spzx-product/target/spzx-product.jar|9205|${PRODUCT_HEAP}"
  "ui|ui||${UI_DIR}/dist|80|"
)

C_RED=$'\033[31m'; C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'; C_BLUE=$'\033[36m'; C_END=$'\033[0m'
log()  { printf '%s[remote-deploy]%s %s\n' "$C_BLUE" "$C_END" "$*"; }
ok()   { printf '%s[ok]%s %s\n' "$C_GREEN" "$C_END" "$*"; }
warn() { printf '%s[warn]%s %s\n' "$C_YELLOW" "$C_END" "$*"; }
die()  { printf '%s[error]%s %s\n' "$C_RED" "$C_END" "$*" >&2; exit 1; }

ssh_do() { ssh "${SSH_OPTS[@]}" "$SSH_HOST" "$@"; }
field() { echo "$1" | cut -d'|' -f"$2"; }

# ------------------------------------------------------------------ 服务选择
# 用法：remote-deploy.sh [命令] [服务...]；也支持 ONLY="auth ui" 环境变量
ONLY="${ONLY:-}"
if [ $# -gt 0 ]; then
  _cmd="$1"; shift || true
  case "$_cmd" in
    logs) CMD="logs"; LOG_SVC="${1:-gateway}"; LOG_FOLLOW="${2:-}" ;;
    all|build|pack|image|up|stop|down|restart|status|verify|clean|help|-h|--help)
      CMD="$_cmd"
      if [ $# -gt 0 ]; then ONLY="${ONLY:+$ONLY }$*"; fi ;;
    *) CMD="all"; ONLY="${ONLY:+$ONLY }$_cmd $*" ;;
  esac
else
  CMD="all"
fi
set --   # 清空位置参数，避免后续 $1 等歧义

selected_services() {
  local s name want=" ${ONLY//,/ } " found=0
  [ -n "$ONLY" ] || { printf '%s\n' "${ALL_SERVICES[@]}"; return; }
  for s in "${ALL_SERVICES[@]}"; do
    name="${s%%|*}"
    if [[ "$want" == *" $name "* ]]; then printf '%s\n' "$s"; found=1; fi
  done
  [ "$found" = "1" ] || die "未匹配到服务：'$ONLY'（可用：gateway auth system ui）"
}

selected_names() { while read -r s; do echo "$(field "$s" 1)"; done < <(selected_services); }
selected_compose() { while read -r n; do printf 'spzx-%s ' "$n"; done < <(selected_names); }
is_selected() { [ -z "$ONLY" ] && return 0; [[ " ${ONLY//,/ } " == *" $1 "* ]]; }

# ------------------------------------------------------------------ 传输
detect_xfer() {
  [ "$XFER" = "auto" ] || return 0
  if command -v rsync >/dev/null 2>&1 && ssh_do "command -v rsync >/dev/null 2>&1"; then
    XFER=rsync
  else
    XFER=scp
  fi
}

upload_file() {  # $1=本地文件 $2=远程目录
  if [ "$XFER" = "rsync" ]; then
    rsync -a --progress -e "$RSYNC_SSH" "$1" "$SSH_HOST:$2/" < /dev/null
  else
    scp "${SCP_SSH_OPTS[@]}" "$1" "$SSH_HOST:$2/" < /dev/null
  fi
}

# ------------------------------------------------------------------ JVM / 端口 / 内存
# 小内存优化：SerialGC + 只编译到 C1 + 限制元空间/代码缓存/直接内存
java_opts_for() {  # $1=堆大小
  local base="-Xms96m -Xmx$1 -Xss512k -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=48m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp -Duser.timezone=Asia/Shanghai -Dfile.encoding=UTF-8"
  local cds=""
  [ "$CDS" = "1" ] && cds="-XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=/home/spzx/cds/app.jsa"
  echo "$base${cds:+ $cds}${JVM_OPTS_EXTRA:+ $JVM_OPTS_EXTRA}"
}

publish_for() {  # $1=服务名 $2=容器端口
  case "$1" in
    gateway) echo "$GATEWAY_PUBLISH" ;;
    ui)      echo "$UI_PUBLISH" ;;
    *)       echo "$INTERNAL_BIND:$2:$2" ;;
  esac
}

mem_for() {  # $1=服务名
  case "$1" in
    gateway) echo "$GATEWAY_MEM" ;;
    auth)    echo "$AUTH_MEM" ;;
    system)  echo "$SYSTEM_MEM" ;;
    product) echo "$PRODUCT_MEM" ;;
    ui)      echo "$UI_MEM" ;;
  esac
}

# ------------------------------------------------------------------ 生成文件
gen_files() {  # $1=stage 目录（生成 Dockerfile / nginx.conf / docker-compose.yml）
  local stage="$1" s name kind module artifact port heap
  while read -r s; do
    name="$(field "$s" 1)"; kind="$(field "$s" 2)"; artifact="$(field "$s" 4)"
    port="$(field "$s" 5)"; heap="$(field "$s" 6)"
    mkdir -p "$stage/$name"
    case "$kind" in
      maven)
        cat > "$stage/$name/Dockerfile" <<DOCKERFILE
# 由 docker/remote-deploy.sh 自动生成，请勿手工修改
FROM eclipse-temurin:21-jre

ENV TZ=Asia/Shanghai \\
    JAVA_OPTS="$(java_opts_for "$heap")"

WORKDIR /home/spzx
RUN mkdir -p /home/spzx/cds
COPY $(basename "$artifact") /home/spzx/app.jar
EXPOSE ${port}
# exec 让 java 成为 PID 1，docker stop 时能收到 SIGTERM 优雅停机
ENTRYPOINT ["sh", "-c", "exec java \$JAVA_OPTS -jar /home/spzx/app.jar"]
DOCKERFILE
        ;;
      ui)
        cat > "$stage/$name/Dockerfile" <<'DOCKERFILE'
# 由 docker/remote-deploy.sh 自动生成，请勿手工修改
FROM nginx:alpine

ENV TZ=Asia/Shanghai
# 仅 1 个 worker，最小化内存占用
COPY nginx.conf /etc/nginx/nginx.conf
# ADD 会自动解压 tar.gz，产物落在 /usr/share/nginx/html/dist
ADD ui-dist.tar.gz /usr/share/nginx/html/
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
DOCKERFILE
        cat > "$stage/$name/nginx.conf" <<'NGINXCONF'
# 由 docker/remote-deploy.sh 自动生成，请勿手工修改
worker_processes  1;

events {
    worker_connections  512;
}

http {
    include       mime.types;
    default_type  application/octet-stream;
    sendfile        on;
    keepalive_timeout  65;
    access_log      off;
    # Vite 已产出 .gz，直接发送预压缩文件，省 CPU/内存
    gzip_static on;
    gzip on;
    gzip_vary on;
    gzip_min_length 1k;
    gzip_comp_level 4;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml image/svg+xml;

    server {
        listen       80;
        server_name  _;
        root   /usr/share/nginx/html/dist;
        index  index.html;

        location / {
            try_files $uri $uri/ /index.html;
        }

        # 前端 /prod-api/xxx → 网关 /xxx
        # 用变量 + Docker 内置 DNS：请求时才解析 spzx-gateway，
        # 这样即使网关还没启动（例如只部署 ui），nginx 也能正常启动
        location /prod-api/ {
            resolver 127.0.0.11 valid=10s ipv6=off;
            set $gw_upstream http://spzx-gateway:8080;
            rewrite ^/prod-api/(.*)$ /$1 break;
            proxy_set_header Host $http_host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header REMOTE-HOST $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_pass $gw_upstream;
        }

        # 避免 actuator 暴露
        if ($request_uri ~ "/actuator") {
            return 403;
        }

        error_page   500 502 503 504  /50x.html;
        location = /50x.html {
            root   html;
        }
    }
}
NGINXCONF
        ;;
      *) die "未知服务类型：$kind" ;;
    esac
  done < <(printf '%s\n' "${ALL_SERVICES[@]}")

  gen_compose "$stage"
}

gen_compose() {  # $1=stage（始终生成全部服务，便于按需 up 单个服务）
  local stage="$1" s name port heap mem ports limit_line
  {
    echo "# 由 docker/remote-deploy.sh 自动生成，请勿手工修改"
    echo "name: spzx"
    echo "services:"
    while read -r s; do
      name="$(field "$s" 1)"; port="$(field "$s" 5)"
      heap="$(field "$s" 6)"
      ports=""
      for p in $(publish_for "$name" "$port"); do ports="$ports\n      - \"$p\""; done
      mem="$(mem_for "$name")"
      limit_line=""
      if [ -n "$mem" ]; then limit_line="\n    mem_limit: $mem"; fi
      cat <<SVC
  spzx-$name:
    image: $IMAGE_PREFIX/spzx-$name:$TAG
    build:
      context: ./$name
    container_name: spzx-$name
    restart: unless-stopped
    ports:$(printf "$ports")$(printf "$limit_line")
    environment:
      - TZ=Asia/Shanghai
SVC
      [ "$(field "$s" 2)" = "maven" ] && printf '      - "JAVA_OPTS=%s"\n' "$(java_opts_for "$heap")"
      if [ "$(field "$s" 2)" = "maven" ] && [ "$CDS" = "1" ]; then
        printf '    volumes:\n      - %s/%s:/home/spzx/cds\n' "$CDS_DIR" "$name"
      fi
      cat <<SVC2
    logging:
      driver: json-file
      options:
        max-size: 20m
        max-file: "2"
SVC2
    done < <(printf '%s\n' "${ALL_SERVICES[@]}")
  } > "$stage/docker-compose.yml"
}

# ------------------------------------------------------------------ build
cmd_build() {
  local s kind did=0
  # 后端：一次性 mvn 打包（含依赖模块）
  local modules=""
  while read -r s; do
    [ "$(field "$s" 2)" = "maven" ] || continue
    modules="$modules,$(field "$s" 3)"
  done < <(selected_services)
  if [ -n "$modules" ]; then
    if [ "$SKIP_MAVEN" = "1" ]; then
      warn "SKIP_MAVEN=1，跳过后端 Maven 打包"
    else
      modules="${modules#,}"
      log "Maven 打包：$modules （含依赖模块，跳过测试）"
      local mvn_opts=(-Dnacos.addr="$NACOS_ADDR")
      [ -n "$SENTINEL_DASHBOARD" ] && mvn_opts+=(-Dsentinel.dashboard="$SENTINEL_DASHBOARD")
      log "写入 jar 的 Nacos 地址：$NACOS_ADDR"
      ( cd "$ROOT_DIR" && mvn -B -q -DskipTests -pl "$modules" -am package "${mvn_opts[@]}" )
      ok "后端打包完成"
    fi
    did=1
  fi
  # 前端：npm 构建
  if is_selected ui; then
    if [ "$SKIP_UI_BUILD" = "1" ]; then
      warn "SKIP_UI_BUILD=1，跳过前端构建"
    else
      command -v npm >/dev/null 2>&1 || die "未找到 npm，无法构建前端"
      log "前端构建：cd $UI_DIR && npm run $UI_BUILD_CMD"
      ( cd "$ROOT_DIR/$UI_DIR" && npm run "$UI_BUILD_CMD" )
      ok "前端构建完成 → $UI_DIR/dist"
    fi
    did=1
  fi
  [ "$did" = "1" ] || die "没有选中任何服务"
}

# ------------------------------------------------------------------ pack
cmd_pack() {
  local s name kind artifact
  detect_xfer
  log "文件传输方式：$XFER"
  ssh_do "mkdir -p '$REMOTE_DIR/deploy'"
  STAGE_DIR="$(mktemp -d)"; trap 'rm -rf "${STAGE_DIR:-}"' EXIT
  gen_files "$STAGE_DIR"

  while read -r s; do
    name="$(field "$s" 1)"; kind="$(field "$s" 2)"; artifact="$(field "$s" 4)"
    ssh_do "mkdir -p '$REMOTE_DIR/deploy/$name'"
    if [ "$kind" = "maven" ] && [ "$CDS" = "1" ]; then ssh_do "mkdir -p '$CDS_DIR/$name'"; fi

    if [ "$SKIP_JARS" != "1" ]; then
      if [ "$kind" = "maven" ]; then
        [ -f "$ROOT_DIR/$artifact" ] || die "缺少 jar：$ROOT_DIR/${artifact}（先执行 build，或去掉 SKIP_MAVEN=1）"
        # 预检：jar 内 bootstrap.yml 的 Nacos 地址是否与预期一致
        local baked
        baked="$(unzip -p "$ROOT_DIR/$artifact" BOOT-INF/classes/bootstrap.yml 2>/dev/null | grep -m1 'server-addr' | sed 's/.*server-addr: *//' | tr -d '\r' || true)"
        [ "$baked" = "$NACOS_ADDR" ] \
          && ok "${name}：Nacos 地址 = $baked" \
          || warn "${name}：jar 内 Nacos 地址 = '${baked:-未读取到}'，预期 '$NACOS_ADDR'（检查根 pom 的 nacos.addr）"
        log "上传 ${artifact}（$(du -h "$ROOT_DIR/$artifact" | cut -f1)）"
        upload_file "$ROOT_DIR/$artifact" "$REMOTE_DIR/deploy/$name"
      else
        [ -d "$ROOT_DIR/$artifact" ] || die "缺少前端产物：$ROOT_DIR/${artifact}（先执行 build，或 npm run ${UI_BUILD_CMD}）"
        tar -czf "$STAGE_DIR/$name/ui-dist.tar.gz" -C "$ROOT_DIR/$UI_DIR" dist
        log "上传前端产物（$(du -h "$STAGE_DIR/$name/ui-dist.tar.gz" | cut -f1)，tar.gz）"
        upload_file "$STAGE_DIR/$name/ui-dist.tar.gz" "$REMOTE_DIR/deploy/$name"
      fi
    else
      warn "SKIP_JARS=1，跳过 $name 的产物上传（沿用服务器上已有文件）"
    fi

    upload_file "$STAGE_DIR/$name/Dockerfile" "$REMOTE_DIR/deploy/$name"
    [ -f "$STAGE_DIR/$name/nginx.conf" ] && upload_file "$STAGE_DIR/$name/nginx.conf" "$REMOTE_DIR/deploy/$name"
  done < <(selected_services)

  # 覆盖前先备份服务器上已有的 compose，避免手工改动被冲掉
  if ssh_do "[ -f '$REMOTE_DIR/deploy/docker-compose.yml' ]"; then
    BACKUP_NAME="docker-compose.yml.bak.$(date +%Y%m%d-%H%M%S)"
    ssh_do "cp -a '$REMOTE_DIR/deploy/docker-compose.yml' '$REMOTE_DIR/deploy/$BACKUP_NAME'"
    warn "服务器已有 compose，已备份为 ${BACKUP_NAME}（随后会被新生成的文件覆盖）"
  fi
  upload_file "$STAGE_DIR/docker-compose.yml" "$REMOTE_DIR/deploy"
  ok "上传完成：$REMOTE_DIR/deploy（compose 含全部服务，本次处理：$(selected_names | tr '\n' ' '))"
}

# ------------------------------------------------------------------ image / up / stop / down
remote_compose() { ssh_do "cd '$REMOTE_DIR/deploy' && docker compose $*"; }

cmd_image() {
  log "远程构建镜像：$(selected_compose)"
  remote_compose "build $(selected_compose)"
  ok "镜像构建完成"
  ssh_do "docker images --format '{{.Repository}}:{{.Tag}} {{.Size}}' | grep '^$IMAGE_PREFIX/' || echo '（暂无 spzx 镜像）'"
}

cmd_up() {
  log "启动容器：$(selected_compose)"
  remote_compose "up -d $(selected_compose)"
  ok "已启动"
  cmd_status
}

cmd_stop()    { remote_compose "stop $(selected_compose)"; ok "已停止：$(selected_compose)"; }
cmd_restart() { remote_compose "restart $(selected_compose)"; ok "已重启：$(selected_compose)"; }

cmd_down() {
  if [ -n "$ONLY" ]; then
    remote_compose "rm -sf $(selected_compose)"
    ok "已移除：$(selected_compose)"
  else
    remote_compose "down"
    ok "已移除全部 spzx 容器"
  fi
}

cmd_clean() { ssh_do "docker image prune -f" >/dev/null; ok "已清理悬空镜像"; ssh_do "df -h / | tail -1"; }

# ------------------------------------------------------------------ status / logs
nacos_instances() {  # $1=serviceName
  ssh_do "curl -s -m 6 '$NACOS_API/nacos/v3/client/ns/instance/list?serviceName=$1&groupName=DEFAULT_GROUP&namespaceId=public'"
}

cmd_status() {
  log "容器状态"
  ssh_do "docker ps -a --filter name=spzx- --format '{{.Names}}\t{{.Status}}\t{{.Ports}}'" || true
  echo
  log "Nacos 注册状态"
  local s name kind ip
  while read -r s; do
    name="$(field "$s" 1)"; kind="$(field "$s" 2)"
    [ "$kind" = "maven" ] || { log "spzx-${name}：前端无需注册 Nacos"; continue; }
    ip="$(nacos_instances "spzx-$name" | tr ',' '\n' | grep -m1 '"ip"' | sed 's/.*"ip":"//;s/".*//' || true)"
    [ -n "$ip" ] && ok "spzx-$name 已注册（${ip}）" || warn "spzx-$name 未注册"
  done < <(selected_services)
  echo
  log "内存占用（容器）"
  ssh_do "docker stats --no-stream --format '{{.Name}}\t{{.MemUsage}}' \$(docker ps --filter name=spzx- --format '{{.Names}}' | tr '\n' ' ')" 2>/dev/null || true
}

cmd_logs() {
  local svc="${LOG_SVC:-gateway}" follow="${LOG_FOLLOW:-}"
  case "$svc" in gateway|auth|system|product|ui) ;; *) die "svc 只能是 gateway|auth|system|product|ui" ;; esac
  log "docker logs spzx-$svc $follow （Ctrl+C 退出）"
  ssh -t -o BatchMode=yes -o StrictHostKeyChecking=accept-new -p "$SSH_PORT" \
      "$SSH_HOST" "docker logs -n 200 $follow spzx-$svc"
}

# ------------------------------------------------------------------ verify
# 在服务器上轮询某服务是否注册到 Nacos
wait_registered() {
  local svc="$1" i
  for i in $(seq 1 60); do
    nacos_instances "$svc" | grep -q '"ip":"' && return 0
    sleep 5
  done
  return 1
}

# 通过容器名取该容器绑定端口，避免依赖固定的对外端口
http_get() { curl -sS -m 20 "$@" 2>/dev/null || true; }

cmd_verify() {
  local failed=0 s name kind r

  log "① 等待服务注册到 Nacos"
  while read -r s; do
    name="$(field "$s" 1)"; kind="$(field "$s" 2)"
    [ "$kind" = "maven" ] || continue
    if wait_registered "spzx-$name"; then ok "spzx-$name 已注册"; else warn "spzx-$name 未在 Nacos 注册"; failed=1; fi
  done < <(selected_services)

  log "② 容器状态"
  ssh_do "docker ps --filter name=spzx- --format '{{.Names}}\t{{.Status}}'" || true

  if is_selected gateway; then
    log "③ 外网访问网关：DELETE /auth/logout（白名单接口，应返回 code=200）"
    r="$(http_get -X DELETE "http://$HOST_IP:$GATEWAY_EXTERNAL_PORT/auth/logout")"
    echo "   $r"
    grep -q '"code":200' <<<"$r" && ok "网关 → spzx-auth 端到端通" || { warn "网关 → spzx-auth 未通过"; failed=1; }

    log "④ 外网访问网关：POST /auth/login（无验证码，应返回验证码提示）"
    r="$(http_get -X POST -H 'Content-Type: application/json' \
          -d '{"username":"admin","password":"admin123"}' \
          "http://$HOST_IP:$GATEWAY_EXTERNAL_PORT/auth/login")"
    echo "   $r"
    grep -q '验证码' <<<"$r" && ok "网关验证码过滤器生效" || { warn "未返回验证码提示，请检查网关路由"; failed=1; }
  fi

  if is_selected system; then
    log "⑤ 服务器内部直连 spzx-system：GET /user/info/admin（@InnerAuth 内部接口）"
    r="$(ssh_do "curl -s -m 15 -H 'from-source: inner' http://127.0.0.1:9201/user/info/admin" || true)"
    echo "   ${r:0:160}"
    grep -q '"code":200' <<<"$r" && ok "spzx-system 正常（已连通 MySQL/Redis）" || { warn "spzx-system 响应异常"; failed=1; }
  fi

  if is_selected ui; then
    log "⑥ 外网访问前端：GET / 与 GET /prod-api/auth/logout（验证 nginx 托管 + 反代网关）"
    r="$(http_get "http://$HOST_IP:$UI_EXTERNAL_PORT/")"
    if grep -q '<div id="app">' <<<"$r"; then ok "前端页面可访问（$(printf '%s' "$r" | wc -c) 字节）"; else warn "前端页面异常：${r:0:120}"; failed=1; fi
    r="$(http_get -X DELETE "http://$HOST_IP:$UI_EXTERNAL_PORT/prod-api/auth/logout")"
    echo "   /prod-api/auth/logout → ${r:0:120}"
    grep -q '"code":200' <<<"$r" && ok "前端 → nginx → 网关 → spzx-auth 反向代理通" || { warn "前端反代未通过"; failed=1; }
  fi

  # ⑦ 完整登录链路（需网关+认证+系统，且提供 Redis 口令）
  if is_selected gateway && is_selected auth && is_selected system; then
    if [ -z "$REDIS_PASSWORD" ]; then
      warn "⑦ 跳过完整登录验证（未设置 REDIS_PASSWORD，无法写入验证码）"
    else
      log "⑦ 完整登录链路：写入验证码 → POST /auth/login（${LOGIN_USER}）→ 取 access_token"
      local uuid answer
      uuid="$(ssh_do "date +%s%N")"
      answer="1234"
      redis_set_captcha "$uuid" "$answer"
      r="$(http_get -X POST -H 'Content-Type: application/json' \
            -d "{\"username\":\"$LOGIN_USER\",\"password\":\"$LOGIN_PASSWORD\",\"code\":\"$answer\",\"uuid\":\"$uuid\"}" \
            "http://$HOST_IP:$GATEWAY_EXTERNAL_PORT/auth/login")"
      echo "   ${r:0:160}"
      if grep -q 'access_token' <<<"$r"; then
        ok "登录成功：网关 → 验证码 → spzx-auth → spzx-system → JWT/Redis 会话全链路通"
        # 若本次包含 product：带 token 走网关访问商品接口，验证路由与数据库
        if is_selected product; then
          local token
          token="$(sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p' <<<"$r")"
          r="$(http_get -H "Authorization: Bearer $token" "http://$HOST_IP:$GATEWAY_EXTERNAL_PORT/product/brand/list?pageNum=1&pageSize=5")"
          echo "   GET /product/brand/list → ${r:0:140}"
          grep -q '"code":200' <<<"$r" && ok "网关 → spzx-product 通（已连通 spzx-product 库）" || { warn "product 接口异常"; failed=1; }
        fi
      else
        warn "登录未成功（密码不对可用 LOGIN_USER/LOGIN_PASSWORD 覆盖）"
        failed=1
      fi
    fi
  fi

  echo
  if [ "$failed" = "0" ]; then
    ok "验证通过。入口：前端 http://$HOST_IP:$UI_EXTERNAL_PORT/ ，网关 http://$HOST_IP:$GATEWAY_EXTERNAL_PORT/"
  else
    warn "存在未通过项，用 ./docker/remote-deploy.sh logs <gateway|auth|system|product|ui> 查看日志"
    return 1
  fi
}

# ------------------------------------------------------------------ Redis（verify 第⑦步）
redis_cli() {  # $* = redis-cli 子命令与参数
  local auth=""
  [ -n "$REDIS_PASSWORD" ] && auth="-a $REDIS_PASSWORD --no-auth-warning"
  if [ -n "$REDIS_CONTAINER" ]; then
    ssh_do "docker exec $REDIS_CONTAINER redis-cli $auth $*"
  else
    ssh_do "docker run --rm $REDIS_IMAGE redis-cli -h ${REDIS_ADDR%:*} -p ${REDIS_ADDR#*:} $auth $*"
  fi | tr -d '\r'
}

# 写入验证码：值必须带 JSON 引号——网关 RedisTemplate 用 fastjson2 序列化，
# 裸值 1234 会被读成 Integer，导致 ValidateCodeServiceImpl 抛 ClassCastException
redis_set_captcha() { redis_cli set "captcha_codes:$1" "\\\"$2\\\"" EX 300 >/dev/null; }

# ------------------------------------------------------------------ main
usage() { sed -n '2,100p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

case "$CMD" in
  all)     cmd_build; cmd_pack; cmd_image; cmd_up; cmd_verify ;;
  build)   cmd_build ;;
  pack)    cmd_pack ;;
  image)   cmd_image ;;
  up)      cmd_up ;;
  stop)    cmd_stop ;;
  down)    cmd_down ;;
  restart) cmd_restart ;;
  status)  cmd_status ;;
  logs)    cmd_logs "${LOG_SVC:-gateway}" "${LOG_FOLLOW:-}" ;;
  verify)  cmd_verify ;;
  clean)   cmd_clean ;;
  help|-h|--help) usage ;;
  *) die "未知命令：$CMD" ;;
esac
