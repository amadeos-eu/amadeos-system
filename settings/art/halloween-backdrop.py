#!/usr/bin/env python3
"""Draws the amadeOS Halloween backdrop: a graveyard under a full moon with witches, werewolves,
skeletons and zombies. Everything is drawn here from polygons, lines and ellipses (original art,
no source pictures), at twice the size and then scaled down for smooth edges.

    python3 settings/art/halloween-backdrop.py halloween.png [width height]

The result is settings/packaging/root/usr/share/backgrounds/amadeos/halloween.png, the backdrop that goes
with the Halloween palette (settings/packaging/root/etc/amadeos/themes/halloween/theme.properties).
"""
import math
import random
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = sys.argv[1] if len(sys.argv) > 1 else "halloween.png"
W, H = (int(sys.argv[2]), int(sys.argv[3])) if len(sys.argv) > 3 else (1920, 1080)
S = 2  # supersampling
K = W / 1920.0  # the scene is laid out for 1920 x 1080
random.seed(31)

MOON = (1380, 330, 165)
SILHOUETTE = (14, 12, 16)


def sc(x, y):
    return (x * K * S, y * (H / 1080.0) * S)


def P(pts):
    return [sc(x, y) for x, y in pts]


def tf(pts, ox, oy, s=1.0, flip=False, rot=0.0):
    out = []
    c, n = math.cos(rot), math.sin(rot)
    for x, y in pts:
        if flip:
            x = -x
        x, y = x * c - y * n, x * n + y * c
        out.append((ox + x * s, oy + y * s))
    return out


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


def layer():
    return Image.new("RGBA", (W * S, H * S), (0, 0, 0, 0))


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def line(d, a, b, w, fill):
    (x1, y1), (x2, y2) = sc(*a), sc(*b)
    r = w * K * S / 2
    d.line([(x1, y1), (x2, y2)], fill=fill, width=max(1, int(w * K * S)))
    d.ellipse([x1 - r, y1 - r, x1 + r, y1 + r], fill=fill)
    d.ellipse([x2 - r, y2 - r, x2 + r, y2 + r], fill=fill)


def ellipse(d, cx, cy, rx, ry, fill):
    (x1, y1), (x2, y2) = sc(cx - rx, cy - ry), sc(cx + rx, cy + ry)
    d.ellipse([x1, y1, x2, y2], fill=fill)


def shaggy(pts, amount, seed):
    """Roughens an outline: fur for the werewolves, rags for the zombies."""
    rnd = random.Random(seed)
    out = []
    for i, (x, y) in enumerate(pts):
        out.append((x, y))
        nx, ny = pts[(i + 1) % len(pts)]
        for k in (1, 2):
            t = k / 3
            out.append((x + (nx - x) * t + rnd.uniform(-amount, amount),
                        y + (ny - y) * t + rnd.uniform(-amount, amount)))
    return out


# ---------------------------------------------------------------- sky, stars, moon

def sky(img):
    top, mid, low = (9, 8, 14), (28, 22, 34), (74, 40, 22)
    px = img.load()
    d = ImageDraw.Draw(img)
    for y in range(H * S):
        t = y / (H * S * 0.72)
        c = lerp(top, mid, min(1, t / 0.6)) if t < 0.6 else lerp(mid, low, min(1, (t - 0.6) / 0.4))
        d.line([(0, y), (W * S, y)], fill=c)
    # a warm glow around the moon, and an orange haze over the horizon
    glow = layer()
    g = ImageDraw.Draw(glow)
    mx, my, mr = MOON
    for i in range(12, 0, -1):
        r = mr * (1 + i * 0.35)
        ellipse(g, mx, my, r, r, (255, 170, 80, int(10 + (12 - i) * 1.5)))
    ellipse(g, 960, 760, 1200, 170, (255, 120, 40, 50))
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(60 * S)))
    stars = ImageDraw.Draw(img)
    rnd = random.Random(7)
    for _ in range(420):
        x, y = rnd.uniform(0, 1920), rnd.uniform(0, 620)
        if math.hypot(x - mx, y - my) < mr * 1.9:
            continue
        r = rnd.choice([0.6, 0.8, 0.8, 1.1, 1.5])
        b = rnd.randint(140, 255)
        ellipse(stars, x, y, r, r, (b, b, int(b * 0.9), 255))


def moon(img):
    mx, my, mr = MOON
    m = layer()
    d = ImageDraw.Draw(m)
    ellipse(d, mx, my, mr, mr, (250, 226, 170, 255))
    # maria and craters
    rnd = random.Random(3)
    for cx, cy, rx, ry in [(-60, -40, 45, 32), (25, -70, 32, 22), (50, 25, 50, 36), (-40, 65, 36, 24),
                           (-100, 10, 20, 28)]:
        ellipse(d, mx + cx, my + cy, rx, ry, (228, 198, 140, 90))
    for _ in range(28):
        a, rr = rnd.uniform(0, 6.28), rnd.uniform(0, mr * 0.85)
        x, y, r = mx + math.cos(a) * rr, my + math.sin(a) * rr, rnd.uniform(4, 13)
        ellipse(d, x, y, r, r, (220, 188, 130, 80))
        ellipse(d, x - r * 0.25, y - r * 0.25, r * 0.7, r * 0.7, (240, 214, 160, 120))
    m = m.filter(ImageFilter.GaussianBlur(1.5 * S))
    # cut to the disc again after the blur, with a soft limb darkening
    mask = Image.new("L", m.size, 0)
    ellipse(ImageDraw.Draw(mask), mx, my, mr, mr, 255)
    shade = layer()
    sd = ImageDraw.Draw(shade)
    for i in range(20):
        r = mr * (1 - i / 60)
        ellipse(sd, mx - 10 + i * 0.5, my - 10 + i * 0.5, r, r, (120, 60, 20, 6))
    m.alpha_composite(shade)
    clipped = Image.new("RGBA", m.size, (0, 0, 0, 0))
    clipped.paste(m, (0, 0), mask)
    img.alpha_composite(clipped)


def clouds(img):
    c = layer()
    d = ImageDraw.Draw(c)
    for cx, cy, rx, ry, a in [(1180, 420, 260, 20, 120), (1560, 390, 220, 16, 110), (1430, 455, 180, 14, 140),
                              (400, 300, 300, 18, 70), (760, 180, 240, 14, 60)]:
        ellipse(d, cx, cy, rx, ry, (40, 30, 44, a))
        ellipse(d, cx + 30, cy - ry * 0.6, rx * 0.6, ry * 0.8, (60, 44, 52, a // 2))
    img.alpha_composite(c.filter(ImageFilter.GaussianBlur(6 * S)))


# ---------------------------------------------------------------- flying things

WITCH = {
    "broom": [(-70, 10), (82, -6)],
    "bristles": [(-66, 6), (-80, 2), (-116, -6), (-124, 8), (-118, 24), (-80, 18), (-66, 14)],
    "skirt": [(-34, 0), (10, 0), (16, 8), (6, 24), (-2, 18), (-10, 28), (-18, 20), (-28, 30), (-36, 18)],
    "torso": [(-30, 2), (-24, -20), (-10, -38), (4, -44), (14, -38), (16, -26), (6, -10), (10, 2)],
    "cape": [(2, -44), (-20, -47), (-50, -45), (-80, -41), (-102, -35), (-88, -31), (-100, -23), (-76, -27),
             (-82, -17), (-54, -25), (-28, -24)],
    "arm": [(2, -36), (10, -41), (46, -6), (39, 0)],
    "leg": [(4, 6), (26, 13), (36, 11), (44, 15), (28, 21), (4, 16)],
    "hat": [(-6, -59), (30, -64), (30, -61), (19, -62), (12, -78), (2, -94), (-12, -104), (-26, -101),
            (-12, -98), (-2, -84), (2, -62)],
    "hair": [(6, -58), (-18, -56), (-34, -44), (-22, -50), (-30, -38), (-6, -46)],
    "nose": [(20, -57), (31, -50), (20, -50)],
    "cat": [(-58, 4), (-60, -6), (-56, -14), (-58, -22), (-56, -28), (-53, -23), (-49, -23), (-46, -28),
            (-45, -21), (-45, -12), (-44, -4), (-44, 4)],
    "tail": [(-58, 2), (-66, -4), (-70, -14), (-66, -22), (-64, -14), (-60, -6)],
}


def witch(d, ox, oy, s, flip=False, rot=-0.08, fill=SILHOUETTE):
    for key in ("cape", "bristles", "skirt", "torso", "arm", "leg", "hat", "hair", "nose", "cat", "tail"):
        d.polygon(P(tf(WITCH[key], ox, oy, s, flip, rot)), fill=fill)
    a, b = tf(WITCH["broom"], ox, oy, s, flip, rot)
    line(d, a, b, 3.4 * s, fill)
    hx, hy = tf([(13, -53)], ox, oy, s, flip, rot)[0]
    ellipse(d, hx, hy, 9 * s, 9 * s, fill)


def bat(d, ox, oy, s, flap=0.0, fill=SILHOUETTE):
    up = -14 * flap
    wing = [(0, -2), (10, -6 + up), (22, -12 + up * 1.4), (34, -6 + up), (30, 2), (24, -1), (19, 5),
            (13, 1), (8, 6), (3, 2)]
    d.polygon(P(tf(wing, ox, oy, s)), fill=fill)
    d.polygon(P(tf(wing, ox, oy, s, flip=True)), fill=fill)
    ellipse(d, ox, oy + 1 * s, 4 * s, 6 * s, fill)
    d.polygon(P(tf([(-3, -4), (-3, -10), (0, -6), (3, -10), (3, -4)], ox, oy, s)), fill=fill)


# ---------------------------------------------------------------- land

def far_hill(x):
    return 712 - 34 * math.sin(x / 260) - 22 * math.sin(x / 97 + 1) + (x / 1920) * 30


def hills(img):
    d = ImageDraw.Draw(img)
    far = [(0, 1080), (0, 712)]
    for x in range(0, 1921, 20):
        far.append((x, far_hill(x)))
    far.append((1920, 1080))
    d.polygon(P(far), fill=(33, 27, 36))
    # a far chapel with a lit window
    cx, cy = 760, 662
    d.polygon(P([(cx - 30, cy + 40), (cx - 30, cy), (cx, cy - 22), (cx + 30, cy), (cx + 30, cy + 40)]), fill=(26, 21, 28))
    d.polygon(P([(cx + 8, cy - 4), (cx + 8, cy - 46), (cx + 15, cy - 70), (cx + 22, cy - 46), (cx + 22, cy - 4)]), fill=(26, 21, 28))
    line(d, (cx + 15, cy - 70), (cx + 15, cy - 86), 2, (26, 21, 28))
    line(d, (cx + 10, cy - 80), (cx + 20, cy - 80), 2, (26, 21, 28))
    ellipse(d, cx - 10, cy + 14, 4, 6, (255, 170, 60))
    ellipse(d, cx + 15, cy - 30, 3, 5, (255, 170, 60))

    mid = [(0, 1080), (0, 820)]
    for x in range(0, 1921, 16):
        mid.append((x, 836 - 18 * math.sin(x / 210 + 0.6) - 10 * math.sin(x / 70)))
    mid.append((1920, 1080))
    d.polygon(P(mid), fill=(24, 22, 26))

    # the crag on the right that the werewolf howls from
    crag = [(1520, 1080), (1540, 860), (1585, 760), (1610, 700), (1650, 640), (1690, 600), (1745, 596),
            (1770, 612), (1800, 640), (1850, 690), (1890, 740), (1920, 760), (1920, 1080)]
    d.polygon(P(shaggy(crag, 6, 11)), fill=SILHOUETTE)


def ground(img):
    g = layer()
    d = ImageDraw.Draw(g)
    front = [(0, 1080), (0, 960)]
    for x in range(0, 1921, 12):
        front.append((x, 968 - 14 * math.sin(x / 160 + 2) - 6 * math.sin(x / 41)))
    front.append((1920, 1080))
    d.polygon(P(front), fill=(17, 16, 19, 255))
    img.alpha_composite(g)


def tree(d, x, y, length, angle, width, depth, rnd):
    if depth == 0 or length < 4:
        return
    nx = x + math.cos(angle) * length
    ny = y + math.sin(angle) * length
    line(d, (x, y), (nx, ny), width, SILHOUETTE)
    branches = 2 if depth > 1 else 1
    for i in range(branches + (1 if rnd.random() < 0.35 else 0)):
        spread = rnd.uniform(0.25, 0.75) * (1 if i % 2 == 0 else -1)
        tree(d, nx, ny, length * rnd.uniform(0.62, 0.8), angle + spread + rnd.uniform(-0.15, 0.15),
             width * 0.66, depth - 1, rnd)


def dead_tree(d, x, y, height, seed, lean=0.0):
    rnd = random.Random(seed)
    # a gnarled trunk out of a few bent pieces
    px, py = x, y
    w = height * 0.11
    a = -math.pi / 2 + lean
    for i in range(4):
        seg = height * 0.16
        nx, ny = px + math.cos(a) * seg, py + math.sin(a) * seg
        line(d, (px, py), (nx, ny), w, SILHOUETTE)
        px, py, w = nx, ny, w * 0.85
        a += rnd.uniform(-0.25, 0.25)
    for k in range(3):
        tree(d, px, py, height * 0.28, a + (k - 1) * 0.7 + rnd.uniform(-0.2, 0.2), w * 0.7, 6, rnd)
    # roots
    for k in (-1, 1):
        d.polygon(P([(x - k * 6, y - 20), (x + k * height * 0.12, y + 4), (x + k * 4, y + 4)]), fill=SILHOUETTE)


def fence(d, x1, x2, y, fill=(20, 18, 22)):
    line(d, (x1, y - 46), (x2, y - 46), 3, fill)
    line(d, (x1, y - 14), (x2, y - 14), 3, fill)
    x = x1
    i = 0
    while x <= x2:
        lean = math.sin(i * 1.7) * 2.5 if i % 7 == 3 else 0
        top = y - 66 - (8 if i % 2 == 0 else 0)
        line(d, (x, y), (x + lean, top), 2.6, fill)
        d.polygon(P([(x + lean - 4, top + 2), (x + lean, top - 9), (x + lean + 4, top + 2)]), fill=fill)
        x += 18
        i += 1


def tombstone(img, x, y, w, h, kind, tilt=0.0, text=None):
    g = layer()
    d = ImageDraw.Draw(g)
    stone, lit, dark = (70, 68, 74), (128, 122, 118), (40, 38, 44)
    if kind == "round":
        body = [(-w / 2, 0), (-w / 2, -h + w / 2)]
        for k in range(0, 181, 10):
            a = math.radians(180 + k)
            body.append((math.cos(a) * w / 2, -h + w / 2 + math.sin(a) * w / 2))
        body += [(w / 2, -h + w / 2), (w / 2, 0)]
    elif kind == "cross":
        t = w * 0.3
        body = [(-t / 2, 0), (-t / 2, -h * 0.62), (-w / 2, -h * 0.62), (-w / 2, -h * 0.62 - t), (-t / 2, -h * 0.62 - t),
                (-t / 2, -h), (t / 2, -h), (t / 2, -h * 0.62 - t), (w / 2, -h * 0.62 - t), (w / 2, -h * 0.62),
                (t / 2, -h * 0.62), (t / 2, 0)]
    else:  # slab with a pointed top
        body = [(-w / 2, 0), (-w / 2, -h * 0.8), (0, -h), (w / 2, -h * 0.8), (w / 2, 0)]
    pts = tf(body, x, y, 1, rot=tilt)
    d.polygon(P(pts), fill=stone)
    # moonlight from the upper right: a light right edge, a dark left one
    right = tf([(p[0] + (-3 if p[0] > 0 else 0), p[1]) for p in body], x, y, 1, rot=tilt)
    d.polygon(P(tf([(p[0] * 0.9 - w * 0.06, p[1] * 0.98 - 1) for p in body], x, y, 1, rot=tilt)), fill=lerp(stone, lit, 0.0))
    shine = [(p[0], p[1]) for p in body if p[0] >= -0.01]
    if len(shine) > 2:
        edge = tf(shine, x, y, 1, rot=tilt)
        d.line(P(edge), fill=lit, width=int(3 * K * S))
    shadowside = [(p[0], p[1]) for p in body if p[0] <= 0.01]
    if len(shadowside) > 2:
        d.line(P(tf(shadowside, x, y, 1, rot=tilt)), fill=dark, width=int(4 * K * S))
    if text:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf", int(w * 0.26 * K * S))
        tx, ty = tf([(0, -h * 0.55)], x, y, 1, rot=tilt)[0]
        d.text(sc(tx, ty), text, font=font, fill=(38, 36, 40), anchor="mm")
    # moss and cracks
    rnd = random.Random(int(x * 7 + y))
    for _ in range(3):
        cx, cy = tf([(rnd.uniform(-w / 3, w / 3), -rnd.uniform(h * 0.1, h * 0.4))], x, y, 1, rot=tilt)[0]
        line(d, (cx, cy), (cx + rnd.uniform(-6, 6), cy + rnd.uniform(4, 10)), 1.2, dark)
    img.alpha_composite(g)


def grave_mound(d, x, y, w, h=14):
    d.polygon(P([(x - w / 2, y)] + [(x + math.cos(math.radians(a)) * w / 2, y - math.sin(math.radians(a)) * h)
                                    for a in range(180, -1, -15)] + [(x + w / 2, y)]), fill=(34, 30, 30))
    for k in range(6):
        ellipse(d, x - w / 2 + k * w / 5, y - 4, 5, 3, (48, 42, 38))


# ---------------------------------------------------------------- werewolves

WOLF_HOWL = [(-48, 0), (-66, -22), (-66, -50), (-52, -74), (-38, -96), (-30, -118), (-26, -134),
             (-20, -146), (-14, -158), (-14, -178), (-2, -164), (2, -180), (8, -165), (18, -166), (34, -178),
             (54, -194), (58, -187), (42, -180), (54, -172), (46, -168), (26, -158), (18, -148), (24, -138),
             (16, -129), (23, -117), (16, -105), (20, -80), (22, -40), (26, -6), (38, -3), (38, 0), (8, 0),
             (6, -40), (-6, -46), (-14, -30), (-6, -8), (4, -4), (4, 0), (-30, 0)]
WOLF_TAIL = [(-48, -8), (-74, -18), (-96, -10), (-110, 2), (-84, 2), (-60, 2)]

WOLF_STAND = [(-30, 0), (-22, -12), (-38, -60), (-24, -96), (-34, -132), (-40, -166), (-30, -186),
              (-14, -194), (-14, -214), (-2, -200), (4, -216), (12, -198), (40, -192), (50, -184), (34, -178),
              (46, -172), (16, -164), (26, -156), (58, -124), (74, -104), (86, -96), (74, -97), (82, -86),
              (70, -91), (72, -80), (62, -95), (46, -120), (18, -140), (16, -104), (12, -72), (2, -50),
              (16, -12), (32, -2), (32, 0), (0, 0), (-4, -38), (-10, -12), (-14, 0)]


def werewolf(d, ox, oy, s, pose, flip=False, seed=1, eyes=True, rim=None):
    pts = WOLF_HOWL if pose == "howl" else WOLF_STAND
    if rim:
        # moonlight catching the fur on the side facing the moon
        rx, ry = rim
        d.polygon(P(tf(shaggy(pts, 2, seed), ox + rx, oy + ry, s, flip)), fill=(150, 110, 70))
        if pose == "howl":
            d.polygon(P(tf(shaggy(WOLF_TAIL, 4, seed + 1), ox + rx, oy + ry, s, flip)), fill=(150, 110, 70))
    d.polygon(P(tf(shaggy(pts, 2, seed), ox, oy, s, flip)), fill=SILHOUETTE)
    if pose == "howl":
        d.polygon(P(tf(shaggy(WOLF_TAIL, 4, seed + 1), ox, oy, s, flip)), fill=SILHOUETTE)
    if eyes:
        ex, ey = tf([(20, -170)] if pose == "howl" else [(24, -188)], ox, oy, s, flip)[0]
        ellipse(d, ex, ey, 2.6 * s, 1.8 * s, (255, 190, 60))


# ---------------------------------------------------------------- skeletons and zombies

def skeleton(img, j, s=1.0, bone=(222, 214, 196)):
    """Draws a skeleton from joint positions (screen coordinates)."""
    g = layer()
    d = ImageDraw.Draw(g)
    w = 4.2 * s
    dark = (30, 26, 30)
    for a, b in [("neck", "pelvis")]:
        # the spine as a row of vertebrae
        (x1, y1), (x2, y2) = j[a], j[b]
        for k in range(10):
            t = k / 9
            ellipse(d, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, 3.0 * s, 2.2 * s, bone)
    # ribs
    (nx, ny), (px, py) = j["neck"], j["pelvis"]
    for k in range(5):
        t = 0.12 + k * 0.11
        cx, cy = nx + (px - nx) * t, ny + (py - ny) * t
        rw = (13 - k * 0.8) * s
        box = [sc(cx - rw, cy - 4 * s), sc(cx + rw, cy + 9 * s)]
        d.arc([box[0], box[1]], 200, 340, fill=bone, width=int(2.4 * s * K * S))
        d.arc([box[0], box[1]], 20, 160, fill=bone, width=int(2.0 * s * K * S))
    # pelvis
    ellipse(d, px, py + 2 * s, 11 * s, 6 * s, bone)
    ellipse(d, px, py + 3 * s, 5 * s, 3 * s, dark)
    # collarbones and limbs
    line(d, j["lshoulder"], j["rshoulder"], 3 * s, bone)
    for side in ("l", "r"):
        line(d, j[side + "shoulder"], j[side + "elbow"], w, bone)
        line(d, j[side + "elbow"], j[side + "hand"], w * 0.85, bone)
        hx, hy = j[side + "hand"]
        ex, ey = j[side + "elbow"]
        a = math.atan2(hy - ey, hx - ex)
        for f in (-0.5, 0, 0.5):
            line(d, (hx, hy), (hx + math.cos(a + f) * 7 * s, hy + math.sin(a + f) * 7 * s), 1.6 * s, bone)
        line(d, (px + (-7 if side == "l" else 7) * s, py + 3 * s), j[side + "knee"], w * 1.1, bone)
        line(d, j[side + "knee"], j[side + "foot"], w, bone)
        fx, fy = j[side + "foot"]
        line(d, (fx, fy), (fx + j.get("toes", 1) * 8 * s, fy + 1 * s), 3 * s, bone)
        for joint in (side + "elbow", side + "knee"):
            ellipse(d, *j[joint], 3 * s, 3 * s, bone)
    # skull
    hx, hy = j["head"]
    ellipse(d, hx, hy, 11 * s, 12 * s, bone)
    d.polygon(P([(hx - 7 * s, hy + 6 * s), (hx + 7 * s, hy + 6 * s), (hx + 6 * s, hy + 15 * s), (hx - 6 * s, hy + 15 * s)]), fill=bone)
    ellipse(d, hx - 4.5 * s, hy + 1 * s, 3.4 * s, 3.8 * s, dark)
    ellipse(d, hx + 4.5 * s, hy + 1 * s, 3.4 * s, 3.8 * s, dark)
    d.polygon(P([(hx, hy + 5 * s), (hx - 1.6 * s, hy + 8.5 * s), (hx + 1.6 * s, hy + 8.5 * s)]), fill=dark)
    for k in range(-2, 3):
        line(d, (hx + k * 2.4 * s, hy + 11 * s), (hx + k * 2.4 * s, hy + 14 * s), 0.8 * s, dark)
    # a faint shadow below makes them stand in the scene
    img.alpha_composite(g)


def zombie(img, j, s=1.0, seed=0, shirt=(52, 56, 48), trousers=(36, 34, 40), skin=(118, 140, 96)):
    g = layer()
    d = ImageDraw.Draw(g)
    rnd = random.Random(seed)
    # legs
    for side in ("l", "r"):
        line(d, j[side + "hip"], j[side + "knee"], 13 * s, trousers)
        line(d, j[side + "knee"], j[side + "foot"], 11 * s, trousers)
        fx, fy = j[side + "foot"]
        ellipse(d, fx + j.get("toes", 1) * 5 * s, fy, 10 * s, 5 * s, (24, 22, 24))
    # torso with a ragged shirt
    ls, rs, lh, rh = j["lshoulder"], j["rshoulder"], j["lhip"], j["rhip"]
    torso = [ls, rs, (rh[0] + 4 * s, rh[1] + 6 * s)]
    hem = []
    for k in range(7):
        t = k / 6
        x = rh[0] + 4 * s + (lh[0] - rh[0] - 8 * s) * t
        y = rh[1] + 6 * s + (lh[1] - rh[1]) * t + (rnd.uniform(4, 12) * s if k % 2 else 0)
        hem.append((x, y))
    torso += hem + [(lh[0] - 4 * s, lh[1] + 6 * s)]
    d.polygon(P(torso), fill=shirt)
    # a torn hole showing ribs
    cx = (ls[0] + rh[0]) / 2
    cy = (ls[1] + rh[1]) / 2
    ellipse(d, cx, cy, 6 * s, 8 * s, (60, 40, 40))
    for k in range(3):
        line(d, (cx - 4 * s, cy - 4 * s + k * 4 * s), (cx + 4 * s, cy - 4 * s + k * 4 * s), 1.4 * s, (190, 184, 160))
    # arms, sleeves torn at the elbow
    for side in ("l", "r"):
        line(d, j[side + "shoulder"], j[side + "elbow"], 10 * s, shirt)
        line(d, j[side + "elbow"], j[side + "hand"], 7 * s, skin)
        hx, hy = j[side + "hand"]
        ex, ey = j[side + "elbow"]
        a = math.atan2(hy - ey, hx - ex)
        for f in (-0.45, -0.15, 0.15, 0.45):
            line(d, (hx, hy), (hx + math.cos(a + f) * 9 * s, hy + math.sin(a + f) * 9 * s), 2.2 * s, skin)
    # head, tilted, with dark hair and glowing eyes
    hx, hy = j["head"]
    line(d, ((ls[0] + rs[0]) / 2, (ls[1] + rs[1]) / 2), (hx, hy + 8 * s), 7 * s, skin)
    ellipse(d, hx, hy, 11 * s, 13 * s, skin)
    d.polygon(P([(hx - 12 * s, hy - 2 * s), (hx - 10 * s, hy - 12 * s), (hx, hy - 15 * s), (hx + 11 * s, hy - 10 * s),
                 (hx + 12 * s, hy - 3 * s), (hx + 6 * s, hy - 8 * s), (hx + 2 * s, hy - 4 * s), (hx - 4 * s, hy - 9 * s)]),
              fill=(30, 28, 26))
    f = j.get("toes", 1)
    ellipse(d, hx + f * 2 * s, hy + 1 * s, 2.6 * s, 2.2 * s, (230, 230, 120))
    ellipse(d, hx + f * 8 * s, hy + 1 * s, 2.2 * s, 2.0 * s, (230, 230, 120))
    line(d, (hx + f * 1 * s, hy + 8 * s), (hx + f * 8 * s, hy + 7 * s), 1.6 * s, (50, 30, 30))
    img.alpha_composite(g)


def pose(origin, s, flip, **joints):
    ox, oy = origin
    k = -1 if flip else 1
    out = {name: (ox + k * x * s, oy + y * s) for name, (x, y) in joints.items()}
    out["toes"] = k
    return out


# ---------------------------------------------------------------- pumpkins, fog

def pumpkin(img, x, y, r, face=True, seed=0):
    g = layer()
    d = ImageDraw.Draw(g)
    rnd = random.Random(seed)
    glow = layer()
    gd = ImageDraw.Draw(glow)
    ellipse(gd, x, y - r * 0.4, r * 2.6, r * 2.0, (255, 140, 30, 90))
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(30 * S)))
    body = (214, 104, 24)
    for k, (dx, w) in enumerate([(-0.55, 0.55), (0.55, 0.55), (-0.25, 0.6), (0.25, 0.6), (0, 0.62)]):
        c = lerp(body, (150, 64, 16), 0.45 if abs(dx) > 0.4 else 0.15 if dx else 0)
        ellipse(d, x + dx * r, y - r * 0.82, r * w, r * 0.82, c)
    line(d, (x, y - r * 1.6), (x + r * 0.15, y - r * 1.95), r * 0.16, (70, 80, 36))
    if face:
        lit = (255, 214, 90)
        d.polygon(P([(x - r * 0.5, y - r * 1.0), (x - r * 0.18, y - r * 1.0), (x - r * 0.34, y - r * 1.3)]), fill=lit)
        d.polygon(P([(x + r * 0.5, y - r * 1.0), (x + r * 0.18, y - r * 1.0), (x + r * 0.34, y - r * 1.3)]), fill=lit)
        d.polygon(P([(x, y - r * 0.95), (x - r * 0.1, y - r * 0.78), (x + r * 0.1, y - r * 0.78)]), fill=lit)
        mouth = [(x - r * 0.62, y - r * 0.62), (x - r * 0.4, y - r * 0.5), (x - r * 0.3, y - r * 0.62), (x - r * 0.12, y - r * 0.48),
                 (x, y - r * 0.62), (x + r * 0.12, y - r * 0.48), (x + r * 0.3, y - r * 0.62), (x + r * 0.4, y - r * 0.5),
                 (x + r * 0.62, y - r * 0.62), (x + r * 0.36, y - r * 0.3), (x + r * 0.2, y - r * 0.4), (x, y - r * 0.26),
                 (x - r * 0.2, y - r * 0.4), (x - r * 0.36, y - r * 0.3)]
        d.polygon(P(mouth), fill=lit)
    img.alpha_composite(g)


def fog(img, y, alpha, seed):
    f = layer()
    d = ImageDraw.Draw(f)
    rnd = random.Random(seed)
    for _ in range(26):
        x = rnd.uniform(-100, 2020)
        ellipse(d, x, y + rnd.uniform(-20, 20), rnd.uniform(120, 300), rnd.uniform(12, 30), (170, 160, 175, alpha))
    img.alpha_composite(f.filter(ImageFilter.GaussianBlur(18 * S)))


# ---------------------------------------------------------------- the scene

def main():
    img = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 255))
    sky(img)
    clouds(img)
    moon(img)
    d = ImageDraw.Draw(img)
    witch(d, 1395, 345, 1.45, flip=True, rot=0.1)
    witch(d, 1010, 560, 0.55, rot=-0.12)
    witch(d, 560, 470, 0.36, rot=-0.05)
    for bx, by, bs, fl in [(1190, 210, 0.9, 0.2), (1250, 160, 0.7, -0.6), (1565, 230, 0.8, 0.8), (1620, 300, 0.55, -0.3),
                           (1500, 520, 0.6, 0.5), (1130, 280, 0.5, -0.8), (640, 360, 0.5, 0.4), (300, 150, 0.45, -0.4)]:
        bat(d, bx, by, bs, fl)
    hills(img)
    d = ImageDraw.Draw(img)
    werewolf(d, 1716, 602, 1.05, "howl", flip=True, seed=5, rim=(-2.2, -1.6))
    werewolf(d, 940, far_hill(940) + 4, 0.36, "howl", flip=False, seed=9, eyes=False)  # far away on the hill
    fog(img, 820, 60, 1)
    d = ImageDraw.Draw(img)
    dead_tree(d, 110, 900, 640, seed=4, lean=0.05)
    dead_tree(d, 1510, 880, 330, seed=8, lean=-0.1)
    fence(d, 420, 1470, 862)

    # graves, back to front
    for x, y, w, h, kind, tilt, text in [
        (480, 880, 46, 70, "round", -0.05, None), (560, 872, 30, 64, "cross", 0.08, None),
        (700, 878, 50, 66, "slab", 0.0, None), (800, 870, 34, 70, "cross", -0.1, None),
        (1100, 876, 48, 72, "round", 0.06, None), (1180, 870, 30, 60, "cross", 0.0, None),
        (1320, 880, 52, 64, "slab", -0.06, None), (1410, 874, 40, 66, "round", 0.1, None),
    ]:
        tombstone(img, x, y, w, h, kind, tilt, text)
    d = ImageDraw.Draw(img)
    werewolf(d, 300, 930, 1.0, "stand", flip=False, seed=13, rim=(1.8, -1.2))
    for x, y, w, h, kind, tilt, text in [
        (560, 960, 70, 104, "round", -0.07, "R.I.P."), (760, 950, 46, 112, "cross", 0.05, None),
        (990, 952, 86, 118, "round", 0.0, "R.I.P."), (1360, 958, 70, 96, "slab", 0.08, None),
        (1440, 962, 40, 92, "cross", -0.12, None),
    ]:
        tombstone(img, x, y, w, h, kind, tilt, text)
    d = ImageDraw.Draw(img)
    grave_mound(d, 1210, 978, 150)
    grave_mound(d, 860, 990, 120)

    # a skeleton sitting on the big stone, waving
    skeleton(img, pose((990, 830), 1.15, False, head=(0, -30), neck=(0, -14), pelvis=(0, 34),
                       lshoulder=(-12, -10), rshoulder=(12, -10), lelbow=(-24, 12), lhand=(-14, 30),
                       relbow=(28, -14), rhand=(34, -42), lknee=(-12, 40), lfoot=(-14, 84),
                       rknee=(14, 40), rfoot=(18, 82)))
    # a skeleton climbing out of the fresh grave, arms up
    skeleton(img, pose((1214, 912), 1.1, True, head=(0, -34), neck=(0, -18), pelvis=(0, 30),
                       lshoulder=(-12, -14), rshoulder=(12, -14), lelbow=(-30, -34), lhand=(-34, -62),
                       relbow=(26, -38), rhand=(22, -68), lknee=(-14, 52), lfoot=(-10, 62),
                       rknee=(14, 52), rfoot=(10, 62)))
    d = ImageDraw.Draw(img)
    grave_mound(d, 1214, 990, 150, 46)
    # a dancing skeleton by the cross
    skeleton(img, pose((690, 900), 1.0, False, head=(2, -64), neck=(0, -48), pelvis=(0, 2),
                       lshoulder=(-12, -44), rshoulder=(12, -44), lelbow=(-30, -60), lhand=(-34, -84),
                       relbow=(28, -30), rhand=(46, -40), lknee=(-16, 24), lfoot=(-8, 50),
                       rknee=(20, 18), rfoot=(36, 36)))

    # zombies shambling towards the middle, arms out
    zombie(img, pose((630, 960), 1.25, False, head=(10, -128), lshoulder=(-12, -104), rshoulder=(14, -106),
                     lelbow=(16, -96), lhand=(44, -100), relbow=(40, -104), rhand=(66, -108),
                     lhip=(-8, -52), rhip=(10, -52), lknee=(-16, -26), lfoot=(-22, 0), rknee=(16, -24), rfoot=(22, 0)), seed=2)
    zombie(img, pose((1540, 976), 1.35, True, head=(12, -126), lshoulder=(-12, -104), rshoulder=(14, -102),
                     lelbow=(20, -90), lhand=(48, -92), relbow=(42, -96), rhand=(70, -100),
                     lhip=(-8, -52), rhip=(10, -52), lknee=(-4, -26), lfoot=(-16, 0), rknee=(22, -28), rfoot=(30, 0)),
           seed=5, shirt=(60, 46, 50), skin=(130, 146, 100))
    # one only half out of the ground
    zombie(img, pose((850, 1020), 1.15, False, head=(4, -112), lshoulder=(-14, -92), rshoulder=(14, -92),
                     lelbow=(-30, -116), lhand=(-26, -146), relbow=(32, -110), rhand=(44, -136),
                     lhip=(-10, -40), rhip=(10, -40), lknee=(-12, -30), lfoot=(-12, -28), rknee=(12, -30), rfoot=(12, -28)),
           seed=8, shirt=(48, 50, 62))
    d = ImageDraw.Draw(img)
    grave_mound(d, 850, 1000, 150)

    ground(img)
    fog(img, 985, 70, 2)
    for x, y, r, face, seed in [(360, 1050, 34, True, 1), (440, 1056, 22, False, 2), (1620, 1052, 30, True, 3),
                                (1700, 1044, 40, True, 4), (1790, 1054, 24, True, 5), (210, 1060, 26, True, 6)]:
        pumpkin(img, x, y, r, face, seed)

    out = img.convert("RGB").resize((W, H), Image.LANCZOS)
    out.save(OUT, optimize=True)


if __name__ == "__main__":
    main()
