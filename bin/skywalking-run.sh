#!/bin/sh
###############################################################################
# 本地(非 Docker)启动单个微服务并接入 SkyWalking 全链路追踪
#
# 用法:
#   sh bin/skywalking-run.sh spzx-gateway
#   sh bin/skywalking-run.sh spzx-modules/spzx-system
#   sh bin/skywalking-run.sh spzx-modules/spzx-product
#
# 说明:
#   1. 先启动 SkyWalking OAP/UI:
#        cd docker && docker-compose up -d spzx-skywalking-oap spzx-skywalking-ui
#   2. 本脚本用 mvn spring-boot:run 启动服务，并通过
#      -Dspring-boot.run.jvmArguments 注入 javaagent(只作用于应用进程)。
#   3. 服务名取自模块名，在 SkyWalking UI 中即显示为该名称。
#   4. 如需使用 IDEA 启动：在 Run Configuration 的 VM options 中填入:
#        -javaagent:<项目根>/docker/skywalking/agent/skywalking-agent.jar
#        -Dskywalking.agent.service_name=<服务名>
#      (collector 默认 127.0.0.1:11800，无需配置)
###############################################################################
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AGENT="${ROOT}/docker/skywalking/agent/skywalking-agent.jar"

if [ $# -lt 1 ]; then
    echo "用法: sh bin/skywalking-run.sh <模块路径>  例如: sh bin/skywalking-run.sh spzx-gateway"
    exit 1
fi
MODULE="$1"
shift

if [ ! -f "${AGENT}" ]; then
    echo "[skywalking] 未找到 agent，请先执行: sh docker/skywalking/download-agent.sh"
    exit 1
fi

# 服务名 = 模块路径最后一段
SERVICE_NAME="$(basename "${MODULE}")"

echo "[skywalking] 启动 ${SERVICE_NAME} (agent=${AGENT})"
echo "[skywalking] 追踪后端: 127.0.0.1:11800   UI: http://localhost:18080"

exec mvn -f "${ROOT}/pom.xml" spring-boot:run -pl "${MODULE}" \
    -Dspring-boot.run.jvmArguments="-javaagent:${AGENT} -Dskywalking.agent.service_name=${SERVICE_NAME} -Dskywalking.collector.backend_service=127.0.0.1:11800" \
    "$@"
