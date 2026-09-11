# -*- coding: utf-8 -*-
"""Generate a draw.io Cross-Functional Flowchart (swimlane) for the SPZX
下单 / 结算 / 付款 / 扣库存 business flow. Follows the drawio-skill
xml-authoring conventions (mxGeometry on every cell, relative geometry on
edges, &#xa; line breaks, coordinates snapped to 10px)."""

LANE_H = 150
LANE_W = 1880
HEADER = 30

# lanes: id, label, sublabel, fill
lanes = [
    {"id": "lane_h5",      "label": "H5 前端",       "sub": "spzx-ui / H5",       "fill": "#d5e8d4", "stroke": "#82b366"},
    {"id": "lane_order",   "label": "订单服务",       "sub": "spzx-order",          "fill": "#dae8fc", "stroke": "#6c8ebf"},
    {"id": "lane_product", "label": "商品服务",       "sub": "spzx-product",        "fill": "#dae8fc", "stroke": "#6c8ebf"},
    {"id": "lane_pay",     "label": "支付服务",       "sub": "spzx-payment",        "fill": "#dae8fc", "stroke": "#6c8ebf"},
    {"id": "lane_mq",      "label": "RabbitMQ",      "sub": "异步消息",            "fill": "#fff2cc", "stroke": "#d6b656"},
    {"id": "lane_alipay",  "label": "支付宝",         "sub": "Alipay",             "fill": "#f5f5f5", "stroke": "#666666"},
]

# steps: id, lane, seq, label, shape-style override
STEP_W, STEP_H = 170, 60
steps = [
    {"id": "s_settle",  "lane": "lane_h5",     "seq": 0, "label": "去结算&#xa;getOrderTrade",              "fill": "#d5e8d4", "stroke": "#82b366", "dashed": 0},
    {"id": "s_submit",  "lane": "lane_h5",     "seq": 1, "label": "提交订单&#xa;POST submitOrder",          "fill": "#d5e8d4", "stroke": "#82b366", "dashed": 0},
    {"id": "s_alipaygo","lane": "lane_h5",     "seq": 2, "label": "发起支付&#xa;POST submitAlipay",         "fill": "#d5e8d4", "stroke": "#82b366", "dashed": 0},
    {"id": "s_pre",     "lane": "lane_order",  "seq": 0, "label": "去重+价格校验&#xa;Lua + getSkuPrice",      "fill": "#dae8fc", "stroke": "#6c8ebf", "dashed": 0},
    {"id": "s_save",    "lane": "lane_order",  "seq": 1, "label": "保存订单+发延迟关单&#xa;order_info/item/log", "fill": "#dae8fc", "stroke": "#6c8ebf", "dashed": 0},
    {"id": "s_paid",    "lane": "lane_order",  "seq": 2, "label": "支付成功→已支付&#xa;→ 发扣库存消息",        "fill": "#dae8fc", "stroke": "#6c8ebf", "dashed": 0},
    {"id": "s_lock",    "lane": "lane_product","seq": 0, "label": "锁定库存&#xa;checkAndLock",               "fill": "#dae8fc", "stroke": "#6c8ebf", "dashed": 0},
    {"id": "s_minus",   "lane": "lane_product","seq": 1, "label": "扣减/解锁库存&#xa;minus | unlock",         "fill": "#dae8fc", "stroke": "#6c8ebf", "dashed": 0},
    {"id": "s_psave",   "lane": "lane_pay",    "seq": 0, "label": "保存支付单+发起支付宝&#xa;savePayment",    "fill": "#dae8fc", "stroke": "#6c8ebf", "dashed": 0},
    {"id": "s_pnotify", "lane": "lane_pay",    "seq": 1, "label": "回调验签+更新状态&#xa;callback/notify",     "fill": "#dae8fc", "stroke": "#6c8ebf", "dashed": 0},
    {"id": "s_mqc",     "lane": "lane_mq",     "seq": 0, "label": "延迟取消订单&#xa;(15min 未支付)",         "fill": "#fff2cc", "stroke": "#d6b656", "dashed": 1},
    {"id": "s_mqp",     "lane": "lane_mq",     "seq": 1, "label": "支付成功消息&#xa;payment.pay",            "fill": "#fff2cc", "stroke": "#d6b656", "dashed": 1},
    {"id": "s_mqm",     "lane": "lane_mq",     "seq": 2, "label": "扣库存消息&#xa;spzx.minus",               "fill": "#fff2cc", "stroke": "#d6b656", "dashed": 1},
    {"id": "s_wap",     "lane": "lane_alipay", "seq": 0, "label": "WAP 支付&#xa;alipay.trade.wap.pay",       "fill": "#f5f5f5", "stroke": "#666666", "dashed": 1},
    {"id": "s_cb",      "lane": "lane_alipay", "seq": 1, "label": "异步回调&#xa;callback/notify",            "fill": "#f5f5f5", "stroke": "#666666", "dashed": 1},
]

# edges: from, to, label, dashed
edges = [
    ("s_settle", "s_submit", "", 0),
    ("s_submit", "s_pre",    "提交", 0),
    ("s_pre",    "s_lock",   "锁库存", 0),
    ("s_lock",   "s_pre",    "库存不足→拒绝", 0),
    ("s_pre",    "s_save",   "", 0),
    ("s_save",   "s_mqc",    "延迟关单", 1),
    ("s_save",   "s_alipaygo", "返回 orderId", 0),
    ("s_alipaygo","s_psave", "发起支付", 0),
    ("s_psave",  "s_wap",    "WAP 支付", 0),
    ("s_wap",    "s_cb",     "", 0),
    ("s_cb",     "s_pnotify","回调", 0),
    ("s_pnotify","s_mqp",    "支付成功", 1),
    ("s_mqp",    "s_paid",   "推送支付成功", 1),
    ("s_paid",   "s_mqm",    "发扣库存", 1),
    ("s_mqm",    "s_minus",  "扣减库存", 1),
    ("s_mqc",    "s_minus",  "超时→解锁", 1),
]

def esc(t):
    return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

cells = []
cid = [1]  # start at 2
def nid():
    cid[0] += 1
    return str(cid[0])

# root cells 0 and 1 already written; we append user shapes.

def add_vertex(idv, val, style, parent, x, y, w, h):
    cells.append(
        '      <mxCell id="%s" value="%s" style="%s" vertex="1" parent="%s">\n'
        '        <mxGeometry x="%d" y="%d" width="%d" height="%d" as="geometry"/>\n'
        '      </mxCell>' % (idv, esc(val), style, parent, x, y, w, h))

def add_edge(ide, val, style, source, target):
    cells.append(
        '      <mxCell id="%s" value="%s" style="%s" edge="1" parent="1" source="%s" target="%s">\n'
        '        <mxGeometry relative="1" as="geometry"/>\n'
        '      </mxCell>' % (ide, esc(val), style, source, target))

# ---- title ----
add_vertex(nid(), "SPZX 下单 / 结算 / 付款 业务流程", "text;html=1;align=left;verticalAlign=top;fontSize=22;fontStyle=1;", "1", 40, 10, 800, 30)
add_vertex(nid(), "尚硅谷甄选 · Spring Cloud 微服务（泳道图：按服务划分；虚线=异步 MQ 消息）", "text;html=1;align=left;verticalAlign=top;fontSize=12;fontColor=#666666;", "1", 40, 40, 900, 20)

# ---- lanes ----
lane_ids = {}
lane_y = 70
for ln in lanes:
    style = ("swimlane;html=1;startSize=%d;horizontal=1;collapsible=0;whiteSpace=wrap;"
             "fillColor=%s;strokeColor=%s;fontStyle=1;fontSize=14;" % (HEADER, ln["fill"], ln["stroke"]))
    value = "%s&#xa;%s" % (ln["label"], ln["sub"])
    add_vertex(nid(), value, style, "1", 40, lane_y, LANE_W, LANE_H)
    lane_ids[ln["id"]] = str(cid[0])
    lane_y += LANE_H + 40

# ---- steps (children of lanes, relative coords) ----
step_ids = {}
for s in steps:
    lane = next(l for l in lanes if l["id"] == s["lane"])
    rx = 30 + s["seq"] * 240
    ry = (LANE_H - HEADER - STEP_H) // 2 + 4
    style = ("rounded=1;whiteSpace=wrap;html=1;fillColor=%s;strokeColor=%s;fontSize=12;"
             % (s["fill"], s["stroke"]))
    if s["dashed"]:
        style += "dashed=1;"
    add_vertex(nid(), s["label"], style, lane_ids[s["lane"]], rx, ry, STEP_W, STEP_H)
    step_ids[s["id"]] = str(cid[0])

# ---- edges ----
for (f, t, label, dashed) in edges:
    style = ("edgeStyle=orthogonalEdgeStyle;rounded=1;orthogonalLoop=1;jettySize=auto;html=1;"
             "fontSize=11;fontColor=#333333;labelBackgroundColor=#ffffff;")
    if dashed:
        style += "dashed=1;strokeColor=#c92a2a;"
    add_edge(nid(), label, style, step_ids[f], step_ids[t])

body = "\n".join(cells)

xml = (
'<?xml version="1.0" encoding="UTF-8"?>\n'
'<mxfile host="app.diagrams.net" version="26.0.0">\n'
'  <diagram name="交易流程" id="trade-flow">\n'
'    <mxGraphModel dx="1200" dy="700" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="1169" pageHeight="827" math="0" shadow="0">\n'
'      <root>\n'
'        <mxCell id="0"/>\n'
'        <mxCell id="1" parent="0"/>\n'
+ body + "\n"
'      </root>\n'
'    </mxGraphModel>\n'
'  </diagram>\n'
'</mxfile>\n'
)

out = r"C:\Users\11931\IdeaProjects\spzx-parent\doc\drawio\spzx-trade-flow.drawio"
with open(out, "w", encoding="utf-8") as f:
    f.write(xml)

print("Wrote", out)
print("cells (non-root):", len(cells))
