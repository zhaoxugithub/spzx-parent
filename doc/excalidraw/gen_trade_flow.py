# -*- coding: utf-8 -*-
"""Generate an Excalidraw business-flow (swimlane) diagram for SPZX
结算 -> 下单 -> 付款 -> 扣库存/解锁. Follows the excalidraw-diagram-generator
skill (fontFamily 5 for all text, orthogonal arrows)."""
import json

# ---- canvas / lane geometry ----
LANE_W = 200
LANE_H = 430
HEADER_H = 56
HDR_Y = 118
BODY_Y = HDR_Y + HEADER_H + 2
BOX_W = 150
BOX_H = 64

lanes = [
    {"id": "h5",     "label": "H5 前端",        "sub": "spzx-ui",  "x": 100,  "color": "#d0ebff"},
    {"id": "order",  "label": "订单服务",        "sub": "spzx-order", "x": 300,  "color": "#d0ebff"},
    {"id": "product","label": "商品服务",        "sub": "spzx-product", "x": 500,  "color": "#d0ebff"},
    {"id": "pay",    "label": "支付服务",        "sub": "spzx-payment", "x": 700,  "color": "#d0ebff"},
    {"id": "mq",     "label": "RabbitMQ",       "sub": "异步消息", "x": 900,  "color": "#ffe3e3"},
    {"id": "alipay", "label": "支付宝",         "sub": "Alipay",  "x": 1100, "color": "#fff3bf"},
]

# box colors by role
C_FRONT = "#b2f2bb"     # frontend green
C_BACK  = "#a5d8ff"     # backend service blue
C_EXT   = "#ffd43b"     # external / alipay yellow
C_MQ    = "#ffc9c9"     # mq red

# boxes: id, lane, (x=center derived), y, text, color, dashed(optional)
boxes = [
    # H5 前端
    {"id": "b_settle",  "lane": "h5",  "y": 200, "text": "去结算\n getOrderTrade", "color": C_FRONT},
    {"id": "b_submit",  "lane": "h5",  "y": 320, "text": "提交订单\n submitOrder", "color": C_FRONT},
    {"id": "b_alipaygo","lane": "h5",  "y": 440, "text": "发起支付\n submitAlipay", "color": C_FRONT},
    # 订单服务
    {"id": "b_order_pre","lane": "order", "y": 200, "text": "去重+价格校验\n Lua + getSkuPrice", "color": C_BACK},
    {"id": "b_order_save","lane": "order", "y": 320, "text": "保存订单(三表)\n order_info/item/log", "color": C_BACK},
    {"id": "b_order_paid","lane": "order", "y": 440, "text": "支付成功→已支付\n →发扣库存消息", "color": C_BACK},
    # 商品服务
    {"id": "b_prod_lock","lane": "product", "y": 200, "text": "锁定库存\n checkAndLock", "color": C_BACK},
    {"id": "b_prod_minus","lane": "product", "y": 320, "text": "扣减库存\n spzx.minus", "color": C_BACK},
    {"id": "b_prod_unlock","lane": "product", "y": 440, "text": "解锁库存\n spzx.unlock", "color": C_BACK},
    # 支付服务
    {"id": "b_pay_save","lane": "pay", "y": 200, "text": "保存支付单\n + 发起支付宝", "color": C_BACK},
    {"id": "b_pay_notify","lane": "pay", "y": 320, "text": "异步验签\n 更新支付状态", "color": C_BACK},
    # RabbitMQ
    {"id": "b_mq_cancel","lane": "mq", "y": 200, "text": "延迟取消订单\n (15min 未支付)", "color": C_MQ},
    {"id": "b_mq_pay","lane": "mq", "y": 320, "text": "支付成功消息\n payment.pay", "color": C_MQ},
    {"id": "b_mq_minus","lane": "mq", "y": 440, "text": "扣库存消息\n spzx.minus", "color": C_MQ},
    # 支付宝
    {"id": "b_ali_wap","lane": "alipay", "y": 200, "text": "WAP 支付\n alipay.trade.wap.pay", "color": C_EXT},
    {"id": "b_ali_cb","lane": "alipay", "y": 320, "text": "异步回调\n callback/notify", "color": C_EXT},
]

# connections: (from_id, to_id, label, dashed)  -- label 只在关键/异步箭头标注
conns = [
    ("b_settle", "b_submit", "", False),
    ("b_submit", "b_order_pre", "", False),
    ("b_order_pre", "b_prod_lock", "checkAndLock 锁库存", False),
    ("b_prod_lock", "b_order_pre", "库存不足→拒绝", False),
    ("b_order_pre", "b_order_save", "", False),
    ("b_order_save", "b_mq_cancel", "发送延迟关单(15min)", True),
    ("b_order_save", "b_submit", "", False),
    ("b_submit", "b_alipaygo", "", False),
    ("b_alipaygo", "b_pay_save", "", False),
    ("b_pay_save", "b_ali_wap", "发起 WAP 支付", False),
    ("b_ali_wap", "b_ali_cb", "", False),
    ("b_ali_cb", "b_pay_notify", "回调(RSA2验签)", False),
    ("b_pay_notify", "b_mq_pay", "发送支付成功消息", True),
    ("b_mq_pay", "b_order_paid", "", True),
    ("b_order_paid", "b_mq_minus", "发送扣库存消息", True),
    ("b_mq_minus", "b_prod_minus", "", True),
    ("b_mq_cancel", "b_prod_unlock", "超时→解锁库存", True),
]

# ---------------------------------------------------------------------------
ELEMENTS = []
counter = [0]

def nid(prefix):
    counter[0] += 1
    return "%s_%d" % (prefix, counter[0])

def esc(t):
    return t

def text_el(x, y, w, h, text, size, color, align="center", valign="middle"):
    return {
        "id": nid("txt"), "type": "text", "x": x, "y": y, "width": w, "height": h,
        "angle": 0, "strokeColor": "#1e1e1e", "backgroundColor": "transparent",
        "fillStyle": "solid", "strokeWidth": 2, "strokeStyle": "solid", "roughness": 1,
        "opacity": 100, "groupIds": [], "frameId": None, "index": "a%d" % counter[0],
        "roundness": None, "seed": 300000 + counter[0], "version": 1,
        "versionNonce": 400000 + counter[0], "isDeleted": False, "boundElements": None,
        "updated": 1700000000000 + counter[0], "link": None, "locked": False,
        "text": text, "fontSize": size, "fontFamily": 5, "textAlign": align,
        "verticalAlign": valign,
    }

def rect_el(x, y, w, h, bg, text=None, dashed=False, rounded=True, tsize=16):
    el = {
        "id": nid("rect"), "type": "rectangle", "x": x, "y": y, "width": w, "height": h,
        "angle": 0, "strokeColor": "#1e1e1e", "backgroundColor": bg, "fillStyle": "solid",
        "strokeWidth": 2, "strokeStyle": "dashed" if dashed else "solid", "roughness": 1,
        "opacity": 100, "groupIds": [], "frameId": None, "index": "a%d" % counter[0],
        "roundness": {"type": 3} if rounded else None, "seed": 500000 + counter[0],
        "version": 1, "versionNonce": 600000 + counter[0], "isDeleted": False,
        "boundElements": None, "updated": 1700000000000 + counter[0], "link": None,
        "locked": False,
    }
    if text:
        el["text"] = text
        el["fontSize"] = tsize
        el["fontFamily"] = 5
        el["textAlign"] = "center"
        el["verticalAlign"] = "middle"
    return el

def box_center(box_id):
    for b in boxes:
        if b["id"] == box_id:
            lane = next(l for l in lanes if l["id"] == b["lane"])
            cx = lane["x"] + LANE_W / 2
            cy = b["y"] + BOX_H / 2
            return cx, cy
    raise KeyError(box_id)

def edge_point(box_id, side):
    cx, cy = box_center(box_id)
    half_w = BOX_W / 2
    half_h = BOX_H / 2
    if side == "right":  return cx + half_w, cy
    if side == "left":   return cx - half_w, cy
    if side == "top":    return cx, cy - half_h
    if side == "bottom": return cx, cy + half_h
    return cx, cy

def arrow_el(frm, to, label, dashed):
    fx, fy = box_center(frm)
    tx, ty = box_center(to)
    # choose exit/entry sides
    if abs(fx - tx) > 1 and abs(fx - tx) > abs(fy - ty):
        # horizontal crossing
        sside = "right" if tx > fx else "left"
        tside = "left" if tx > fx else "right"
    elif abs(fy - ty) > 1:
        sside = "bottom" if ty > fy else "top"
        tside = "top" if ty > fy else "bottom"
    else:
        sside = tside = "right"
    sx, sy = edge_point(frm, sside)
    ex, ey = edge_point(to, tside)
    # orthogonal route (one bend)
    pts = [[0.0, 0.0]]
    if abs(fx - tx) > 1 and abs(fx - tx) > abs(fy - ty):
        # go horizontally to target x, then vertically to target y
        bendx = ex
        pts.append([bendx - sx, sy - sy])     # (bendx, sy)
        pts.append([ex - sx, ey - sy])
    else:
        # vertical route: go vertically to target y then horizontally
        bendy = ey
        pts.append([sx - sx, bendy - sy])     # (sx, bendy)
        pts.append([ex - sx, ey - sy])
    el = {
        "id": nid("arrow"), "type": "arrow", "x": sx, "y": sy,
        "width": abs(ex - sx), "height": abs(ey - sy), "angle": 0,
        "strokeColor": "#c92a2a" if dashed else "#1e1e1e", "backgroundColor": "transparent",
        "fillStyle": "solid", "strokeWidth": 2,
        "strokeStyle": "dashed" if dashed else "solid", "roughness": 1, "opacity": 100,
        "groupIds": [], "frameId": None, "index": "a%d" % counter[0],
        "roundness": {"type": 2}, "seed": 700000 + counter[0], "version": 1,
        "versionNonce": 800000 + counter[0], "isDeleted": False, "boundElements": None,
        "updated": 1700000000000 + counter[0], "link": None, "locked": False,
        "points": pts, "startBinding": None, "endBinding": None,
    }
    if label:
        # label near arrow midpoint
        midx = sx + (pts[-1][0]) / 2
        midy = sy + (pts[-1][1]) / 2
        ELEMENTS.append(text_el(midx - 50, midy - 30, 100, 18, label, 11, "#c92a2a" if dashed else "#343a40"))
    return el

# ---- title ----
ELEMENTS.append(text_el(200, 40, 900, 34,
    "SPZX 下单 / 结算 / 付款 业务流程图", 26, "#1e1e1e", align="left", valign="top"))
ELEMENTS.append(text_el(200, 78, 900, 20,
    "尚硅谷甄选 · Spring Cloud 微服务 ｜ 泳道图(按服务划分) ｜ 虚线=异步 MQ 消息", 12, "#868e96", align="left", valign="top"))

# ---- lane headers + bodies ----
for lane in lanes:
    # header
    header = rect_el(lane["x"], HDR_Y, LANE_W, HEADER_H, lane["color"], None, rounded=False)
    ELEMENTS.append(header)
    ELEMENTS.append(text_el(lane["x"], HDR_Y + 6, LANE_W, 22, lane["label"], 16, "#1e1e1e"))
    ELEMENTS.append(text_el(lane["x"], HDR_Y + 28, LANE_W, 18, lane["sub"], 11, "#495057"))
    # body
    body = rect_el(lane["x"], BODY_Y, LANE_W, LANE_H, "transparent", None, rounded=False)
    ELEMENTS.append(body)

# ---- boxes ----
lane_of = {b["id"]: b["lane"] for b in boxes}
for b in boxes:
    lane = next(l for l in lanes if l["id"] == b["lane"])
    bx = lane["x"] + (LANE_W - BOX_W) / 2
    ELEMENTS.append(rect_el(bx, b["y"], BOX_W, BOX_H, b["color"], b["text"]))

# ---- arrows ----
for c in conns:
    ELEMENTS.append(arrow_el(c[0], c[1], c[2], c[3]))

# ---- phase banner ----
ELEMENTS.append(text_el(100, BODY_Y + LANE_H + 12, 200, 18, "阶段① 结算 → 阶段② 下单 → 阶段③ 付款/扣库存", 12, "#495057", align="left", valign="top"))

data = {
    "type": "excalidraw",
    "version": 2,
    "source": "https://excalidraw.com",
    "elements": ELEMENTS,
    "appState": {"viewBackgroundColor": "#ffffff", "gridSize": 20},
    "files": {},
}

out = r"C:\Users\11931\IdeaProjects\spzx-parent\doc\excalidraw\spzx-trade-flow.excalidraw"
with open(out, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

print("Wrote", out)
print("shapes:", sum(1 for e in ELEMENTS if e["type"] == "rectangle"),
      "arrows:", sum(1 for e in ELEMENTS if e["type"] == "arrow"),
      "texts:", sum(1 for e in ELEMENTS if e["type"] == "text"),
      "total:", len(ELEMENTS))
