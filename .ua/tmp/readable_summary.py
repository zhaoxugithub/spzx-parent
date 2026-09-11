# -*- coding: utf-8 -*-
import json, sys, os
r = sys.argv[1]
d = json.load(open(os.path.join(r, ".ua", "knowledge-graph.json"), encoding="utf-8"))
nodes = {n["id"]: n for n in d["nodes"]}
print("=" * 60)
print("知识图谱可读摘要 |", d["project"]["name"])
print("节点 %d | 边 %d | 层 %d | 导览 %d" % (len(d["nodes"]), len(d["edges"]), len(d["layers"]), len(d["tour"])))
print("=" * 60)

print("\n【导览 Tour】")
for s in d["tour"]:
    print("  %d. %s — %s" % (s["order"], s["title"], s["description"]))

print("\n【架构层 Layers】(每层展示代表性 file/config 节点)")
for L in d["layers"]:
    ids = L.get("nodeIds", [])
    print("\n  ◆ %s (%d 节点)" % (L["name"], len(ids)))
    # show a few notable file/config nodes
    shown = 0
    for nid in ids[:200]:
        n = nodes.get(nid)
        if n and n.get("type") in ("file", "config", "document", "service") and shown < 5:
            fp = n.get("filePath", nid)
            print("     - %s  [%s] %s" % (fp, n.get("type"), n.get("summary", "")[:70]))
            shown += 1
    if len(ids) > 5:
        print("     ... 共 %d 节点" % len(ids))

print("\n【节点类型分布】")
from collections import Counter
print("  ", dict(Counter(n["type"] for n in d["nodes"])))
print("\n【边类型分布】")
print("  ", dict(Counter(e["type"] for e in d["edges"])))
