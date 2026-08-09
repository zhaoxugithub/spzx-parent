#!/bin/sh
###############################################################################
# 下载并安装 SkyWalking Java Agent（9.6.0，兼容 Java 8~21）
#
# 用法:
#   sh download-agent.sh            # 下载默认版本 9.6.0
#   SW_VERSION=9.6.0 sh download-agent.sh   # 指定版本
#
# 执行完成后 agent 解压到 ./agent 目录，并自动启用本项目所需的插件：
#   - apm-spring-cloud-gateway-4.x-plugin    (网关 Spring Cloud Gateway 4.0.x，3.x/4.x 互斥)
#   - apm-spring-webflux-6.x-plugin          (WebFlux 6.x, 网关依赖)
#   - apm-springmvc-annotation-6.x-plugin    (Spring MVC 6.x, Spring Boot 3)
#   - apm-nacos-client-2.x-plugin            (Nacos 注册/配置中心 2.x)
#   - apm-mybatis-3.x-plugin                 (MyBatis-Plus SQL 链路)
#   - apm-sentinel-1.x-plugin                (Sentinel 限流)
#   - apm-quartz-scheduler-2.x-plugin        (定时任务 Quartz)
###############################################################################
set -e

SW_VERSION="${SW_VERSION:-9.6.0}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
AGENT_DIR="${SCRIPT_DIR}/agent"
TARBALL="apache-skywalking-java-agent-${SW_VERSION}.tgz"
MIRRORS="https://dlcdn.apache.org/skywalking/java-agent/${SW_VERSION}/${TARBALL}
https://archive.apache.org/dist/skywalking/java-agent/${SW_VERSION}/${TARBALL}
https://mirrors.tuna.tsinghua.edu.cn/apache/skywalking/java-agent/${SW_VERSION}/${TARBALL}"

if [ -f "${AGENT_DIR}/skywalking-agent.jar" ]; then
    echo "[skywalking] agent 已存在: ${AGENT_DIR}/skywalking-agent.jar (如需重装请先删除 agent 目录)"
    exit 0
fi

echo "[skywalking] 开始下载 SkyWalking Java Agent ${SW_VERSION} ..."
for url in ${MIRRORS}; do
    echo "  -> ${url}"
    if curl -fsSL --connect-timeout 15 -o "${TARBALL}" "${url}"; then
        echo " 下载成功: ${url}"
        break
    fi
done

if [ ! -f "${TARBALL}" ]; then
    echo "[skywalking] 下载失败，请检查网络或手工下载后解压到 ${AGENT_DIR}"
    exit 1
fi

echo "[skywalking] 解压 agent ..."
tar xzf "${TARBALL}"
mv skywalking-agent "${AGENT_DIR}"
rm -f "${TARBALL}"

# 启用本项目需要的可选插件
echo "[skywalking] 启用可选插件 ..."
ENABLE_PLUGINS="apm-spring-cloud-gateway-4.x-plugin
apm-spring-webflux-6.x-plugin
apm-springmvc-annotation-6.x-plugin
apm-nacos-client-2.x-plugin
apm-mybatis-3.x-plugin
apm-sentinel-1.x-plugin
apm-quartz-scheduler-2.x-plugin"

for p in ${ENABLE_PLUGINS}; do
    jar="$(ls ${AGENT_DIR}/optional-plugins/${p}-*.jar 2>/dev/null | head -1 || true)"
    if [ -n "${jar}" ]; then
        mv "${jar}" "${AGENT_DIR}/plugins/"
        echo "  ENABLED: $(basename ${jar})"
    fi
done

echo "[skywalking] 安装完成 ✅"
echo "  agent: ${AGENT_DIR}/skywalking-agent.jar"
echo "  版本:  ${SW_VERSION}"
