# -*- coding: utf-8 -*-
"""Assemble the final KnowledgeGraph JSON (nodes/edges/layers/tour) from the
merged assembled-graph.json, run an inline validation, and save
knowledge-graph.json + meta.json into .ua/. Deterministic layers by top-level
module and a deterministic tour by entry point."""
import json, os, datetime, sys, hashlib

ROOT = sys.argv[1]
UA = os.path.join(ROOT, ".ua")
INTER = os.path.join(UA, "intermediate")

g = json.load(open(os.path.join(INTER, "assembled-graph.json"), encoding="utf-8"))
nodes = g["nodes"]
edges = g["edges"]

FILE_LEVEL = {"file", "config", "document", "service", "table", "schema", "resource", "endpoint", "pipeline"}
id2node = {n["id"]: n for n in nodes}

# ---- Layers: group file-level nodes by top-level module directory ----
def layer_name_for(fpath):
    # fpath like "spzx-modules/spzx-system/src/..." → top segment
    seg = fpath.split("/")[0] if "/" in fpath else "(root)"
    mapping = {
        "spzx-gateway": "网关层",
        "spzx-auth": "认证层",
        "spzx-api": "接口层(Feign)",
        "spzx-common": "公共组件层",
        "spzx-modules": "业务服务层",
        "spzx-visual": "可视化/监控层",
    }
    base = mapping.get(seg, "其它/根")
    return seg, base

layer_map = {}  # base -> {id, name, description, nodeIds}
for n in nodes:
    if n.get("type") in FILE_LEVEL and n.get("filePath"):
        seg, base = layer_name_for(n["filePath"])
        if base not in layer_map:
            layer_map[base] = {"id": "layer:" + base.replace("/", "-").replace("(", "").replace(")", ""),
                               "name": base, "description": f"SPZX 微服务中属于 {base} 的文件/配置/文档集合。",
                               "nodeIds": []}
        layer_map[base]["nodeIds"].append(n["id"])

# Also ensure every FILE_LEVEL node is in exactly one layer (fallback).
assigned = set()
for L in layer_map.values():
    for i in L["nodeIds"]:
        assigned.add(i)
for n in nodes:
    if n.get("type") in FILE_LEVEL and n.get("filePath") and n["id"] not in assigned:
        seg, base = layer_name_for(n["filePath"])
        if base not in layer_map:
            layer_map[base] = {"id": "layer:" + base.replace("/", "-").replace("(", "").replace(")", ""),
                               "name": base, "description": f"SPZX 微服务中属于 {base} 的文件集合。", "nodeIds": []}
        layer_map[base]["nodeIds"].append(n["id"])
        assigned.add(n["id"])

layers = list(layer_map.values())

# ---- Tour: deterministic steps ----
def find_node(pred, prefer_contains=None):
    for n in nodes:
        if pred(n):
            return n["id"]
    return None

def file_id_containing(sub):
    for n in nodes:
        if n.get("filePath") and sub in n["filePath"]:
            return n["id"]
    return None

tour_steps = []
def add_step(order, title, desc, node_ids):
    node_ids = [x for x in node_ids if x]
    if node_ids:
        tour_steps.append({"order": order, "title": title, "description": desc, "nodeIds": node_ids})

add_step(1, "项目概览", "从 README/CLAUDE 文档了解项目定位、技术栈与架构.", [file_id_containing("README.md"), file_id_containing("CLAUDE.md")])
add_step(2, "网关入口", "Spring Cloud Gateway 作为唯一入口(端口 8080),统一鉴权/验证码/流控.", [file_id_containing("spzx-gateway")])
add_step(3, "认证中心", "spzx-auth 提供 JWT 登录/注册(管理端与 H5 双端).", [file_id_containing("spzx-auth")])
add_step(4, "安全与公共组件", "spzx-common 的安全/核心/Redis/Rabbit 等公共库.", [file_id_containing("spzx-common-core"), file_id_containing("spzx-common-security")])
add_step(5, "Feign 接口层", "spzx-api 定义服务间远程调用接口与领域对象.", [file_id_containing("spzx-api-system"), file_id_containing("spzx-api-product")])
add_step(6, "业务服务层", "spzx-modules 各微服务: 系统/商品/用户/订单/购物车/支付/文件等.", [file_id_containing("spzx-system"), file_id_containing("spzx-product"), file_id_containing("spzx-order")])
add_step(7, "监控中心", "spzx-visual 的 Spring Boot Admin 监控、SkyWalking 全链路.", [file_id_containing("spzx-monitor")])

# ---- Assemble KnowledgeGraph ----
project = {
    "name": "尚硅谷甄选 SPZX (微服务电商平台)",
    "languages": ["java", "xml", "yaml", "markdown"],
    "frameworks": ["Spring Boot", "Spring Cloud Alibaba", "Nacos", "MyBatis-Plus", "RabbitMQ"],
    "description": "基于若依 RuoYi-Cloud v3.6.3 改造的分布式微服务电商平台: 网关/认证/系统/商品/用户/订单/购物车/支付等。",
    "analyzedAt": datetime.datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%S.000Z"),
    "gitCommitHash": open(os.path.join(ROOT, ".git", "HEAD"), encoding="utf-8").read().strip().split()[-1] if os.path.exists(os.path.join(ROOT, ".git", "HEAD")) else "unknown",
}

graph = {"version": "1.0.0", "project": project, "nodes": nodes, "edges": edges, "layers": layers, "tour": tour_steps}

# ---- Inline validation ----
issues, warnings = [], []
node_ids = set()
seen = set()
for i, n in enumerate(nodes):
    if not n.get("id"): issues.append(f"Node[{i}] missing id"); continue
    for f in ("type", "name", "summary", "tags"):
        if not n.get(f): issues.append(f"Node '{n['id']}' missing {f}")
    if n["id"] in seen: issues.append(f"Duplicate node id '{n['id']}'")
    seen.add(n["id"]); node_ids.add(n["id"])
for i, e in enumerate(edges):
    if e.get("source") not in node_ids: issues.append(f"Edge[{i}] source '{e.get('source')}' not found")
    if e.get("target") not in node_ids: issues.append(f"Edge[{i}] target '{e.get('target')}' not found")
assigned = set()
for L in layers:
    for x in L.get("nodeIds", []):
        if x not in node_ids: issues.append(f"Layer '{L['id']}' refs missing node '{x}'")
        if x in assigned: issues.append(f"Node '{x}' in multiple layers")
        assigned.add(x)
for n in nodes:
    if n.get("type") in FILE_LEVEL and n.get("filePath") and n["id"] not in assigned:
        issues.append(f"File node '{n['id']}' not in any layer")
for step in tour_steps:
    for x in step.get("nodeIds", []):
        if x not in node_ids: issues.append(f"Tour step '{step['title']}' refs missing node '{x}'")
with_edges = set()
for e in edges:
    with_edges.add(e.get("source")); with_edges.add(e.get("target"))
for n in nodes:
    if n["id"] not in with_edges:
        warnings.append(f"Node '{n['id']}' has no edges (orphan)")

# ---- Save ----
os.makedirs(UA, exist_ok=True)
with open(os.path.join(UA, "knowledge-graph.json"), "w", encoding="utf-8") as f:
    json.dump(graph, f, ensure_ascii=False)
with open(os.path.join(UA, "meta.json"), "w", encoding="utf-8") as f:
    json.dump({"lastAnalyzedAt": project["analyzedAt"], "gitCommitHash": project["gitCommitHash"],
               "version": "1.0.0", "analyzedFiles": len(nodes)}, f, ensure_ascii=False)

print("SAVED knowledge-graph.json + meta.json")
print("nodes:", len(nodes), "edges:", len(edges), "layers:", len(layers), "tourSteps:", len(tour_steps))
print("validation issues:", len(issues), "warnings:", len(warnings))
for i in issues[:15]: print("  ISSUE:", i)
for w in warnings[:5]: print("  WARN:", w)
