# SkyWalking 组件目录（Docker）

| 内容 | 说明 |
|------|------|
| `download-agent.sh` | 下载 SkyWalking Java Agent 9.6.0 到 `agent/`，并启用本项目所需插件 |
| `agent/` | Java Agent（下载产物，已 gitignore，**不入库**） |
| `oap-data/` | OAP H2 存储数据（运行期生成，已 gitignore） |

## 使用

```bash
# 1. 下载 agent（一次性）
sh download-agent.sh

# 2. 启动 OAP + UI（在 docker/ 目录下）
docker-compose up -d spzx-skywalking-oap spzx-skywalking-ui
# 或
sh ../deploy.sh skywalking

# 3. 访问 UI
#    http://localhost:18080
```

> OAP 镜像 `apache/skywalking-oap-server:9.6.0`，UI 镜像 `apache/skywalking-ui:9.6.0`。
> 版本需与 agent 保持一致（9.6.0）。

详细说明见项目根目录 `doc/skywalking-全链路改造.md`。
