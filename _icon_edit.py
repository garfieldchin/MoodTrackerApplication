# -*- coding: utf-8 -*-
# 心锚图标三改 v2：① 白边变透明并裁掉 ② 层层叠叠主体缩小30% ③ 红点放大2倍、80%不透明度
import numpy as np
from PIL import Image, ImageFilter, ImageDraw

SRC = r"C:/Users/81449/.workbuddy/clipboard-images/clipboard-2026-10-15T19-42-20-078Z-a800fbbd.jpg"
OUT_FULL = r"C:/Users/81449/WorkBuddy/2026-09-20-15-26-09/MoodTracker/icon_v2_full.png"
OUT_PREV = r"C:/Users/81449/WorkBuddy/2026-09-20-15-26-09/MoodTracker/icon_v2_preview.png"


def flood(mask, seeds, max_iter=None):
    """在 mask 内从种子点泛洪，返回连通区域（numpy 迭代膨胀实现）。
    max_iter 限制扩散步数，防止顺着渐变漏进远处同色区。"""
    cur = np.zeros_like(mask)
    for x, y in seeds:
        cur[y, x] = mask[y, x]
    n = 0
    while True:
        grow = mask & (
            cur
            | np.roll(cur, 1, 0) | np.roll(cur, -1, 0)
            | np.roll(cur, 1, 1) | np.roll(cur, -1, 1)
        )
        if (grow == cur).all():
            return cur
        cur = grow
        n += 1
        if max_iter is not None and n >= max_iter:
            return cur


img = Image.open(SRC).convert("RGB")
a0 = np.asarray(img).astype(np.float32)
H, W = a0.shape[:2]

# ---------- ① 白边：四角泛洪近白像素 → 透明 ----------
white = (a0[..., 0] >= 244) & (a0[..., 1] >= 244) & (a0[..., 2] >= 244)
seed = np.zeros((H, W), bool)
seed[0, :] = white[0, :]; seed[-1, :] = white[-1, :]
seed[:, 0] = white[:, 0]; seed[:, -1] = white[:, -1]
while True:
    grow = white & (
        seed
        | np.roll(seed, 1, 0) | np.roll(seed, -1, 0)
        | np.roll(seed, 1, 1) | np.roll(seed, -1, 1)
    )
    if (grow == seed).all():
        break
    seed = grow
border = seed
keep = ~border
ys, xs = np.where(keep)
x0, x1, y0, y1 = int(xs.min()), int(xs.max()) + 1, int(ys.min()), int(ys.max()) + 1
print("crop", x0, y0, x1, y1)

crop = img.crop((x0, y0, x1, y1))
a = np.asarray(crop).astype(np.float32)
h, w = a.shape[:2]
R, G, B = a[..., 0], a[..., 1], a[..., 2]
border_c = border[y0:y1, x0:x1]

# ---------- 圆形主体 ----------
orange = (R - B > 100) & (R > 160)
oy, ox = np.where(orange)
cx0 = (np.percentile(ox, 0.1) + np.percentile(ox, 99.9)) / 2
cy0 = (np.percentile(oy, 0.1) + np.percentile(oy, 99.9)) / 2
rad_x = (np.percentile(ox, 99.9) - np.percentile(ox, 0.1)) / 2
rad_y = (np.percentile(oy, 99.9) - np.percentile(oy, 0.1)) / 2
rad = min(rad_x, rad_y)
print("circle", round(cx0, 1), round(cy0, 1), "rad", round(rad, 1))

Y, X = np.mgrid[0:h, 0:w]
dist = np.sqrt((X - cx0) ** 2 + (Y - cy0) ** 2)

# ---------- ② 羽翼：只认白色本体（收紧阈值，不带光晕），多种子泛洪 ----------
light = (R - B < 50) & (R > 195)
cand = light & (dist < rad * 0.85)
cy_, cx_ = np.where(cand)
seeds = [(int(cx_.mean()), int(cy_.mean()))]
for idx in [np.argmax(cx_), np.argmin(cx_), np.argmax(cy_), np.argmin(cy_)]:
    seeds.append((int(cx_[idx]), int(cy_[idx])))
wing = flood(light, seeds)
print("wing seeds", seeds, "wing px", wing.sum())
wy, wx = np.where(wing)
print("wing bbox", wx.min(), wy.min(), wx.max(), wy.max(), "centroid", round(wx.mean(), 1), round(wy.mean(), 1))

# ---------- ③ 红点：先验位置（圆心正上方 0.46r 处）就近取种子，限步泛洪 ----------
strict_red = (R > 170) & (G < 155) & (B < 125) & (R - G > 75)
px_prior, py_prior = cx0, cy0 - rad * 0.46
ry_, rx_ = np.where(strict_red)
i_nearest = np.argmin((rx_ - px_prior) ** 2 + (ry_ - py_prior) ** 2)
dseed = [(int(rx_[i_nearest]), int(ry_[i_nearest]))]
dot = flood(strict_red, dseed, max_iter=120)
dy_, dx_ = np.where(dot)
rcx, rcy = dx_.mean(), dy_.mean()
rrad = np.sqrt(dot.sum() / np.pi)
assert 8 < rrad < 70, f"红点半径异常: {rrad}"
print("dot px", dot.sum(), "center", round(rcx, 1), round(rcy, 1), "radius", round(rrad, 1))

# ---------- 背景：金字塔插值。只认圆内强橙色像素，整片羽翼+光晕+旧红点区域重建 ----------
inside95 = dist < rad * 0.975
# 旧羽翼外扩 100px 的矩形框：框内的中等橙色光晕环也一并吃掉，框外不动
box = np.zeros((h, w), bool)
box[
    max(wy.min() - 100, 0): min(wy.max() + 100, h),
    max(wx.min() - 100, 0): min(wx.max() + 100, w),
] = True
hole0 = (
    ((R - B < 115) & (R > 190) & inside95)
    | ((R - B < 170) & (R > 185) & inside95 & box)
    | (dot & inside95)
)
hole = hole0.copy()
for _ in range(45):  # 再膨胀 45px，抹掉残边
    hole = (
        hole
        | np.roll(hole, 1, 0) | np.roll(hole, -1, 0)
        | np.roll(hole, 1, 1) | np.roll(hole, -1, 1)
    )
hole &= inside95
valid = (~hole) & (R - B > 115) & (dist < rad * 1.02)


def pyramid_fill(img, valid_mask):
    """pull-push 金字塔插值：逐层平均有效像素，再逐层回填无效区。"""
    im = img.astype(np.float32).copy()
    v = valid_mask.copy()
    levels = []
    while min(im.shape[0], im.shape[1]) > 4:
        levels.append((im, v))
        H2 = (im.shape[0] + 1) // 2 * 2
        W2 = (im.shape[1] + 1) // 2 * 2
        pad = np.zeros((H2, W2, 3), np.float32)
        pad[: im.shape[0], : im.shape[1]] = im
        padv = np.zeros((H2, W2), bool)
        padv[: v.shape[0], : v.shape[1]] = v
        b = pad.reshape(H2 // 2, 2, W2 // 2, 2, 3)
        bv = padv.reshape(H2 // 2, 2, W2 // 2, 2)
        cnt = bv.sum(axis=(1, 3))
        s = (b * bv[..., None]).sum(axis=(1, 3))
        newv = cnt > 0
        im2 = np.zeros((H2 // 2, W2 // 2, 3), np.float32)
        im2[newv] = s[newv] / cnt[newv, None]
        im, v = im2, newv
    mean = im[v].mean(axis=0) if v.any() else np.array([255.0, 200.0, 120.0])
    im[~v] = mean
    filled = im
    for im_f, v_f in reversed(levels):
        up = np.asarray(
            Image.fromarray(np.clip(filled, 0, 255).astype(np.uint8)).resize(
                (v_f.shape[1], v_f.shape[0]), Image.BILINEAR
            ),
            dtype=np.float32,
        )
        im_f = im_f.copy()
        im_f[~v_f] = up[~v_f]
        filled = im_f
    return filled


filled = pyramid_fill(a, valid)
bgimg = a.copy()
bgimg[hole] = filled[hole]
print("inpaint done (pyramid)")

# ---------- 羽翼缩小30%：用「原图 - 无翼背景」的亮度差定 alpha，本体+投影一起抠 ----------
diff = a.mean(2) - bgimg.mean(2)
alpha_light = np.clip((diff - 12.0) / 45.0, 0, 1)
alpha_shadow = np.clip((-diff - 20.0) / 70.0, 0, 1) * 0.85
alpha = np.maximum(alpha_light, alpha_shadow)
# 只保留旧羽翼附近区域（bbox 外扩 50px），排除红点与圆外
mgn = 50
region = np.zeros_like(alpha)
region[
    max(wy.min() - mgn, 0): min(wy.max() + mgn, h),
    max(wx.min() - mgn, 0): min(wx.max() + mgn, w),
] = 1.0
alpha *= region * inside95
dot_d = dot.copy()
for _ in range(8):
    dot_d = dot_d | np.roll(dot_d, 1, 0) | np.roll(dot_d, -1, 0) | np.roll(dot_d, 1, 1) | np.roll(dot_d, -1, 1)
alpha[dot_d] = 0.0
alpha = np.asarray(
    Image.fromarray((alpha * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(2.0))
).astype(np.float32) / 255.0
print("layer alpha px(>0.3)", int((alpha > 0.3).sum()))
wy0, wy1 = int(wy.min()), int(wy.max()) + 1
wx0, wx1 = int(wx.min()), int(wx.max()) + 1
pad = 55  # 连软光晕和投影一起带走
bx0, by0 = max(wx0 - pad, 0), max(wy0 - pad, 0)
bx1, by1 = min(wx1 + pad, w), min(wy1 + pad, h)
layer_rgba = np.dstack([a, alpha * 255.0]).astype(np.uint8)
layer = Image.fromarray(layer_rgba, "RGBA").crop((bx0, by0, bx1, by1))
scale = 0.70
nw, nh = int(round(layer.width * scale)), int(round(layer.height * scale))
layer_s = layer.resize((nw, nh), Image.LANCZOS)
bcx, bcy = (bx0 + bx1) / 2.0, (by0 + by1) / 2.0
paste_x, paste_y = int(round(bcx - nw / 2.0)), int(round(bcy - nh / 2.0))
print("wing layer", layer.size, "->", layer_s.size, "paste", paste_x, paste_y)

base = Image.fromarray(np.clip(bgimg, 0, 255).astype(np.uint8), "RGB").convert("RGBA")
tmp = Image.new("RGBA", base.size, (0, 0, 0, 0))
tmp.paste(layer_s, (paste_x, paste_y), layer_s)
base = Image.alpha_composite(base, tmp)

# ---------- 红点放大2倍、80%不透明度 ----------
m = int(round(rrad * 1.3))
px0, py0 = int(rcx - m), int(rcy - m)
px1, py1 = int(rcx + m) + 1, int(rcy + m) + 1
patch_src = img.crop((x0 + px0, y0 + py0, x0 + px1, y0 + py1))  # 原图对应块（含旧红点渐变）
pw, ph = patch_src.size
big = patch_src.resize((pw * 2, ph * 2), Image.LANCZOS)

ss = 4
cm = Image.new("L", (pw * 2 * ss, ph * 2 * ss), 0)
d = ImageDraw.Draw(cm)
new_r = rrad * 2 * ss
ccx, ccy = (pw * 2 * ss) / 2.0, (ph * 2 * ss) / 2.0
d.ellipse((ccx - new_r, ccy - new_r, ccx + new_r, ccy + new_r), fill=255)
cm = cm.resize((pw * 2, ph * 2), Image.LANCZOS)

r_, g_, b_ = big.split()
alpha_dot = Image.fromarray((np.asarray(cm).astype(np.float32) * 0.80).astype(np.uint8), "L")
dot_final = Image.merge("RGBA", (r_, g_, b_, alpha_dot))

tmp2 = Image.new("RGBA", base.size, (0, 0, 0, 0))
dxp = int(round(rcx - (pw * 2) / 2.0))
dyp = int(round(rcy - (ph * 2) / 2.0))
tmp2.paste(dot_final, (dxp, dyp), dot_final)
base = Image.alpha_composite(base, tmp2)
print("dot new radius", round(rrad * 2, 1), "alpha 0.80")

# ---------- 收尾：白边位置转透明，存 RGBA ----------
corner_alpha = (~border_c).astype(np.float32) * 255.0
corner_alpha = np.asarray(
    Image.fromarray(corner_alpha.astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.0))
)
out_rgba = np.dstack([np.asarray(base.convert("RGB")), corner_alpha]).astype(np.uint8)
out = Image.fromarray(out_rgba, "RGBA")
out.save(OUT_FULL)
out.resize((1080, 1080), Image.LANCZOS).save(OUT_PREV)
print("saved", OUT_FULL, out.size)
