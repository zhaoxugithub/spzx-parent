#!/bin/sh

# 复制项目的文件到对应docker路径，便于一键生成镜像。
# 用法: sh copy.sh   (在 docker/ 目录下执行)
usage() {
	echo "Usage: sh copy.sh"
	exit 1
}

# copy sql
echo "begin copy sql "
cp ../sql/ry_20230706.sql ./mysql/db
cp ../sql/ry_config_20220929.sql ./mysql/db

# copy html
echo "begin copy html "
cp -r ../spzx-ui/dist/** ./nginx/html/dist

# 每个服务的 jar 名以 pom 中 finalName=${project.artifactId} 为准
# copy jar + dockerfile（docker-compose 的 build.context 指向 ./spzx/...）
copy_service() {
	SRC_JAR="$1"
	DST_DIR="$2"
	mkdir -p "${DST_DIR}/jar"
	echo "begin copy $(basename "${SRC_JAR}") -> ${DST_DIR}"
	cp "${SRC_JAR}" "${DST_DIR}/jar/"
}

# gateway
copy_service ../spzx-gateway/target/spzx-gateway.jar ./spzx/gateway
cp ./ruoyi/gateway/dockerfile ./spzx/gateway/dockerfile

# auth
copy_service ../spzx-auth/target/spzx-auth.jar ./spzx/auth
cp ./ruoyi/auth/dockerfile ./spzx/auth/dockerfile

# visual-monitor
copy_service ../spzx-visual/spzx-monitor/target/spzx-monitor.jar ./spzx/visual/monitor
cp ./ruoyi/visual/monitor/dockerfile ./spzx/visual/monitor/dockerfile

# system
copy_service ../spzx-modules/spzx-system/target/spzx-system.jar ./spzx/modules/system
cp ./ruoyi/modules/system/dockerfile ./spzx/modules/system/dockerfile

# file
copy_service ../spzx-modules/spzx-file/target/spzx-file.jar ./spzx/modules/file
cp ./ruoyi/modules/file/dockerfile ./spzx/modules/file/dockerfile

# job
copy_service ../spzx-modules/spzx-job/target/spzx-job.jar ./spzx/modules/job
cp ./ruoyi/modules/job/dockerfile ./spzx/modules/job/dockerfile

# gen
copy_service ../spzx-modules/spzx-gen/target/spzx-gen.jar ./spzx/modules/gen
cp ./ruoyi/modules/gen/dockerfile ./spzx/modules/gen/dockerfile

echo "copy done."
