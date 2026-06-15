"""
gen_icons5.py — MealFlow launcher icons, iteration 5
Steam wisps: bezier-edge polygons with explicit semicircle tip caps.
100 px gap at base, tall prominent shapes (tip y≈120-148), looks like proper steam.
"""
import math, os
from PIL import Image, ImageDraw

SIZE = 1024
OUT_ROOT = r"app\src\main\res"

DENSITIES = {
    "mipmap-mdpi":    48,
    "mipmap-hdpi":    72,
    "mipmap-xhdpi":   96,
    "mipmap-xxhdpi":  144,
    "mipmap-xxxhdpi": 192,
}

ORANGE = (231, 76, 28)
DARK   = (36, 36, 42)
WHITE  = (255, 255, 255)


def cbez(p0, p1, p2, p3, n=60):
    pts = []
    for i in range(n + 1):
        t = i / n; u = 1 - t
        pts.append((
            u**3*p0[0] + 3*u**2*t*p1[0] + 3*u*t**2*p2[0] + t**3*p3[0],
            u**3*p0[1] + 3*u**2*t*p1[1] + 3*u*t**2*p2[1] + t**3*p3[1],
        ))
    return pts


def tip_arc(cx, cy, r):
    """
    Rounded cap going from (cx-r, cy) UPWARD through (cx, cy-r) to (cx+r, cy).
    In PIL's y-down system the upward arc sweeps from 180° → 270° → 360°
    (sin(270°)=-1 so y=cy-r is the TOP).
    step=+4 degrees, range 180..360.
    """
    pts = []
    d = 180
    while d <= 360:
        a = math.radians(d)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
        d += 4
    return pts


def make_wisp(left_segs, tip_cx, tip_cy, tip_r, right_segs):
    """
    Build closed wisp polygon:
      left_segs  = list of (p0,p1,p2,p3) bezier segs, going base→tip-left
      tip cap    = semicircle from tip-left to tip-right (going over the top)
      right_segs = list of (p0,p1,p2,p3) bezier segs, going tip-right→base
    """
    pts = []
    for seg in left_segs:
        pts += cbez(*seg)
    pts += tip_arc(tip_cx, tip_cy, tip_r)
    for seg in right_segs:
        pts += cbez(*seg)
    return pts


def render_icon():
    img  = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    cx = SIZE // 2  # 512

    # ── Background ──────────────────────────────────────────────────
    draw.rounded_rectangle([0, 0, SIZE-1, SIZE-1],
                           radius=int(SIZE * 0.22), fill=ORANGE)

    # ── Arc under bowl ──────────────────────────────────────────────
    acx, acy = cx, 468
    arx, ary = 340, 215
    arc_pts = []
    for deg in range(172, 6, -2):
        a = math.radians(deg)
        arc_pts.append((acx + arx * math.cos(a), acy + ary * math.sin(a)))
    draw.line(arc_pts, fill=WHITE, width=22)
    ea = math.radians(8)
    ax = acx + arx * math.cos(ea);  ay = acy + ary * math.sin(ea)
    tx = -arx * math.sin(ea);       ty =  ary * math.cos(ea)
    ln = math.hypot(tx, ty);        tx, ty = tx/ln, ty/ln
    nx, ny = -ty, tx
    draw.polygon([(ax+tx*90, ay+ty*90), (ax+nx*48, ay+ny*48), (ax-nx*48, ay-ny*48)], fill=WHITE)

    # ── Steam wisps ─────────────────────────────────────────────────
    # Bowl rim: y = 390.  Wisp bases are inside bowl (y ≥ 390, covered by bowl polygon).
    # Left wisp:  base 362–468 (106 px wide), center 415, tip center (415, 148) r=24
    # Right wisp: base 568–668 (100 px wide), center 618, tip center (625, 120) r=22
    # Gap at base: 568 – 468 = 100 px orange strip             ✓
    # Both wisps clearly visible above bowl rim (250–270 px tall)

    # ── Left wisp (slight left lean) ────────────────────────────────
    # Tip semicircle centre at (415, 148), r=24
    # → left edge ends at (391, 148), right edge starts at (439, 148)
    lw = make_wisp(
        left_segs=[
            ((362, 392), (344, 328), (340, 258), (356, 210)),
            ((356, 210), (368, 178), (386, 156), (391, 148)),
        ],
        tip_cx=415, tip_cy=148, tip_r=24,
        right_segs=[
            ((439, 148), (454, 160), (468, 192), (474, 234)),
            ((474, 234), (482, 294), (480, 352), (468, 392)),
        ],
    )
    draw.polygon(lw, fill=WHITE)

    # ── Right wisp (slight right lean) ──────────────────────────────
    # Tip semicircle centre at (625, 120), r=22
    # → left edge ends at (603, 120), right edge starts at (647, 120)
    rw = make_wisp(
        left_segs=[
            ((568, 390), (556, 322), (558, 248), (576, 196)),
            ((576, 196), (588, 160), (602, 130), (603, 120)),
        ],
        tip_cx=625, tip_cy=120, tip_r=22,
        right_segs=[
            ((647, 120), (660, 138), (676, 178), (682, 222)),
            ((682, 222), (690, 284), (682, 344), (668, 390)),
        ],
    )
    draw.polygon(rw, fill=WHITE)

    # ── Bowl (dark charcoal half-ellipse) ────────────────────────────
    bt  = 390
    brx = 368
    bry = 182
    bowl_pts = [(cx - brx, bt)]
    for deg in range(0, 181, 2):
        a = math.radians(deg)
        bowl_pts.append((cx + brx * math.cos(a), bt + bry * math.sin(a)))
    bowl_pts.append((cx + brx, bt))
    draw.polygon(bowl_pts, fill=DARK)

    rim_th = 18
    draw.ellipse([cx-brx, bt-14, cx+brx, bt+28], outline=WHITE, width=rim_th)

    inner_pts = [(cx-(brx-rim_th), bt+8)]
    for deg in range(0, 181, 2):
        a = math.radians(deg)
        inner_pts.append((cx+(brx-rim_th)*math.cos(a), bt+(bry-rim_th)*math.sin(a)))
    inner_pts.append((cx+(brx-rim_th), bt+8))
    draw.polygon(inner_pts, fill=DARK)

    return img


def circle_clip(img):
    mask = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, SIZE-1, SIZE-1], fill=255)
    out = img.copy()
    out.putalpha(mask)
    return out


if __name__ == "__main__":
    icon = render_icon()
    web_dir = os.path.join(OUT_ROOT, "drawable")
    os.makedirs(web_dir, exist_ok=True)
    icon.resize((512, 512), Image.LANCZOS).save(os.path.join(web_dir, "ic_launcher_web.png"))
    print("Saved drawable/ic_launcher_web.png")
    round_icon = circle_clip(icon)
    for folder, px in DENSITIES.items():
        dst = os.path.join(OUT_ROOT, folder)
        os.makedirs(dst, exist_ok=True)
        icon.resize((px, px), Image.LANCZOS).save(os.path.join(dst, "ic_launcher.png"))
        round_icon.resize((px, px), Image.LANCZOS).save(os.path.join(dst, "ic_launcher_round.png"))
        print(f"  {folder}: {px}×{px}")
    print("Done.")
