#!/usr/bin/env python3
"""Draws the two amadeOS Christmas backdrops. christmas.png: a snowy village on a winter night with the nativity
scene (the Christ child in the manger in a stable, ox and donkey, Mary and Joseph, shepherds with sheep and
the three kings under the star), little angels in the sky, a decorated Christmas tree, a snowman,
gingerbread men, a little red-haired elf and presents. christmas-santa.png, the American tradition: Santa
Claus's sleigh with his reindeer, Rudolph in front, crossing the moon over a street of houses trimmed with
coloured lights, candy canes, a snowman and a tree on the lawn. Everything is drawn here from
polygons, lines and ellipses (original art, no source pictures), at twice the size and then scaled down
for smooth edges.

    python3 settings/art/christmas-backdrop.py christmas.png [width height]
    python3 settings/art/christmas-backdrop.py --santa christmas-santa.png [width height]

The results are settings/packaging/root/usr/share/backgrounds/amadeos/christmas.png and christmas-santa.png,
the backdrops that go with the Christmas palette (settings/packaging/root/etc/amadeos/themes/christmas/theme.properties).
"""
import math
import random
import sys

from PIL import Image, ImageDraw, ImageFilter

SANTA = "--santa" in sys.argv
ARGS = [a for a in sys.argv[1:] if a != "--santa"]
OUT = ARGS[0] if ARGS else ("christmas-santa.png" if SANTA else "christmas.png")
W, H = (int(ARGS[1]), int(ARGS[2])) if len(ARGS) > 2 else (1920, 1080)
S = 2  # supersampling
K = W / 1920.0  # the scene is laid out for 1920 x 1080

MOON = (430, 250, 120)
STAR = (985, 560)
NIGHT = (16, 26, 52)
SNOW = (232, 238, 248)
SNOW_SHADE = (176, 192, 222)
PINE = (22, 52, 40)
WINDOW = (255, 196, 90)


def sc(x, y):
    return (x * K * S, y * (H / 1080.0) * S)


def P(pts):
    return [sc(x, y) for x, y in pts]


def tf(pts, ox, oy, s=1.0, flip=False):
    return [(ox + (-x if flip else x) * s, oy + y * s) for x, y in pts]


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


def glow(img, x, y, r, color, alpha, blur):
    g = layer()
    ellipse(ImageDraw.Draw(g), x, y, r, r, (color[0], color[1], color[2], alpha))
    img.alpha_composite(g.filter(ImageFilter.GaussianBlur(blur * S)))


# ---------------------------------------------------------------- sky

def sky(img):
    d = ImageDraw.Draw(img)
    top, mid, low = (8, 12, 30), (20, 36, 72), (58, 86, 130)
    for y in range(H * S):
        t = y / (H * S * 0.7)
        c = lerp(top, mid, min(1, t / 0.6)) if t < 0.6 else lerp(mid, low, min(1, (t - 0.6) / 0.4))
        d.line([(0, y), (W * S, y)], fill=c)
    # a faint green-blue aurora band
    a = layer()
    ad = ImageDraw.Draw(a)
    for k in range(60):
        x = 900 + k * 18
        y = 170 + 60 * math.sin(k / 9) + k * 1.2
        ellipse(ad, x, y, 26, 90, (80, 200, 160, 22))
    img.alpha_composite(a.filter(ImageFilter.GaussianBlur(28 * S)))
    rnd = random.Random(5)
    mx, my, mr = MOON
    for _ in range(380):
        x, y = rnd.uniform(0, 1920), rnd.uniform(0, 640)
        if math.hypot(x - mx, y - my) < mr * 1.6:
            continue
        r = rnd.choice([0.6, 0.8, 0.8, 1.1, 1.5])
        b = rnd.randint(150, 255)
        ellipse(d, x, y, r, r, (b, b, 255))


def star_of_bethlehem(img):
    # the star of Bethlehem over the stable, with its tail and a soft beam down to the stable
    sx, sy = STAR
    tail = layer()
    td = ImageDraw.Draw(tail)
    # the tail: layered wedges that thin out and fade towards the upper right
    ang = math.radians(-28)
    ux, uy = math.cos(ang), math.sin(ang)
    nx, ny = -uy, ux
    for length, width, alpha in [(420, 26, 40), (360, 16, 70), (300, 9, 110), (220, 4, 170)]:
        tip = (sx + ux * length, sy + uy * length)
        td.polygon(P([(sx + nx * width, sy + ny * width), tip, (sx - nx * width, sy - ny * width)]),
                   fill=(255, 238, 196, alpha))
    td.polygon(P([(sx - 5, sy), (sx + 5, sy), (sx + 70, 730), (sx - 70, 730)]), fill=(255, 236, 190, 34))
    img.alpha_composite(tail.filter(ImageFilter.GaussianBlur(4 * S)))
    glow(img, sx, sy, 50, (255, 240, 200), 150, 18)
    d = ImageDraw.Draw(img)
    for ang, length in [(0, 46), (90, 60), (45, 20), (135, 20)]:
        a = math.radians(ang)
        line(d, (sx - math.cos(a) * length, sy - math.sin(a) * length),
             (sx + math.cos(a) * length, sy + math.sin(a) * length), 2.2, (255, 248, 225))
    ellipse(d, sx, sy, 5, 5, (255, 255, 240))


def moon(img):
    mx, my, mr = MOON
    for i in range(10, 0, -1):
        glow(img, mx, my, mr * (1 + i * 0.3), (190, 210, 255), 9, 40)
    m = layer()
    d = ImageDraw.Draw(m)
    ellipse(d, mx, my, mr, mr, (240, 244, 252, 255))
    for cx, cy, rx, ry in [(-40, -30, 34, 24), (28, -50, 24, 16), (36, 20, 40, 30), (-30, 52, 28, 18)]:
        ellipse(d, mx + cx, my + cy, rx, ry, (222, 228, 242, 255))
    img.alpha_composite(m.filter(ImageFilter.GaussianBlur(1.2 * S)))


# ---------------------------------------------------------------- land

def hill(x, base, amp, f, ph):
    return base - amp * math.sin(x / f + ph) - amp * 0.4 * math.sin(x / (f * 0.37) + ph * 2)


def snowy_ground(img, base, amp, f, ph, top, bottom):
    pts = [(0, 1080)] + [(x, hill(x, base, amp, f, ph)) for x in range(0, 1921, 12)] + [(1920, 1080)]
    g = layer()
    ImageDraw.Draw(g).polygon(P(pts), fill=(255, 255, 255, 255))
    grad = Image.new("RGBA", g.size)
    gd = ImageDraw.Draw(grad)
    y0 = int(sc(0, base - amp * 1.5)[1])
    for y in range(y0, H * S):
        t = (y - y0) / max(1, H * S - y0)
        gd.line([(0, y), (W * S, y)], fill=lerp(top, bottom, min(1, t)))
    img.paste(grad, (0, 0), g)


def pine(d, x, y, h, fill=PINE, snow=SNOW, tiers=4):
    w = h * 0.42
    line(d, (x, y), (x, y - h * 0.15), h * 0.06, (40, 30, 26))
    for i in range(tiers):
        t = i / tiers
        top = y - h * (0.28 + 0.72 * (i + 1) / tiers)
        bot = y - h * (0.1 + 0.62 * i / tiers)
        half = w * (1 - t * 0.75) / 2 + h * 0.04
        d.polygon(P([(x - half, bot), (x, top), (x + half, bot)]), fill=fill)
        # snow on the upper part of each tier, with a scalloped lower edge
        sh = (bot - top) * 0.45
        sw = half * 0.45
        cap = [(x - sw, top + sh), (x, top), (x + sw, top + sh)]
        d.polygon(P(cap), fill=snow)
        for k in range(3):
            ellipse(d, x - sw + sw * k, top + sh, sw * 0.42, sh * 0.18, snow)


def house(img, x, y, w, h, roof, color, seed, chimney=True):
    d = ImageDraw.Draw(img)
    rnd = random.Random(seed)
    d.polygon(P([(x - w / 2, y), (x - w / 2, y - h), (x + w / 2, y - h), (x + w / 2, y)]), fill=color)
    if chimney:
        cx = x + w * 0.22
        d.polygon(P([(cx, y - h - roof * 0.4), (cx, y - h - roof * 0.95), (cx + w * 0.12, y - h - roof * 0.95),
                     (cx + w * 0.12, y - h - roof * 0.3)]), fill=lerp(color, (60, 30, 30), 0.4))
        ellipse(d, cx + w * 0.06, y - h - roof * 0.97, w * 0.08, 3, SNOW)
        sm = layer()
        sd = ImageDraw.Draw(sm)
        for k in range(6):
            ellipse(sd, cx + w * 0.06 + k * 7 + rnd.uniform(-3, 3), y - h - roof - 10 - k * 14, 6 + k * 2.5, 5 + k * 2,
                    (200, 210, 230, 70 - k * 9))
        img.alpha_composite(sm.filter(ImageFilter.GaussianBlur(4 * S)))
        d = ImageDraw.Draw(img)
    # roof with thick snow
    d.polygon(P([(x - w / 2 - 8, y - h + 2), (x, y - h - roof), (x + w / 2 + 8, y - h + 2)]), fill=(70, 46, 44))
    d.polygon(P([(x - w / 2 - 10, y - h + 2), (x, y - h - roof - 4), (x + w / 2 + 10, y - h + 2),
                 (x + w / 2 + 4, y - h + 8), (x, y - h - roof + 8), (x - w / 2 - 4, y - h + 8)]), fill=SNOW)
    for k in range(5):
        ellipse(d, x - w / 2 + k * w / 4, y - h + 6, w / 10, 4, SNOW)
    # windows and door
    for wx in (-0.28, 0.28):
        cx, cy = x + wx * w, y - h * 0.62
        glow(img, cx, cy, w * 0.22, WINDOW, 60, 10)
        d = ImageDraw.Draw(img)
        d.rectangle([sc(cx - w * 0.1, cy - h * 0.14), sc(cx + w * 0.1, cy + h * 0.14)], fill=WINDOW)
        line(d, (cx, cy - h * 0.14), (cx, cy + h * 0.14), 1.6, color)
        line(d, (cx - w * 0.1, cy), (cx + w * 0.1, cy), 1.6, color)
        ellipse(d, cx, cy + h * 0.16, w * 0.12, 2.5, SNOW)
    d.rectangle([sc(x - w * 0.08, y - h * 0.42), sc(x + w * 0.08, y)], fill=lerp(color, (40, 20, 16), 0.5))
    # a wreath on the door
    ellipse(d, x, y - h * 0.3, w * 0.05, w * 0.05, (30, 90, 50))
    ellipse(d, x, y - h * 0.3, w * 0.025, w * 0.025, lerp(color, (40, 20, 16), 0.5))
    ellipse(d, x, y - h * 0.3 + w * 0.045, w * 0.015, w * 0.015, (200, 30, 40))



# ---------------------------------------------------------------- the tree, the snowman, presents

def christmas_tree(img, x, y, h):
    d = ImageDraw.Draw(img)
    rnd = random.Random(12)
    line(d, (x, y), (x, y - h * 0.12), h * 0.05, (70, 44, 30))
    w = h * 0.56
    tiers = 5
    outline = []
    for i in range(tiers):
        bot = y - h * (0.08 + 0.17 * i)
        top = y - h * (0.36 + 0.16 * i) if i < tiers - 1 else y - h * 0.98
        half = w * (1 - i / tiers * 0.8) / 2
        d.polygon(P([(x - half, bot), (x, top), (x + half, bot)]), fill=(26, 80, 52))
        # a darker underside and lighter needles on the right, where the moonlight falls
        d.polygon(P([(x - half, bot), (x - half * 0.5, bot - (bot - top) * 0.15), (x + half * 0.6, bot - (bot - top) * 0.1),
                     (x + half, bot)]), fill=(18, 58, 40))
        d.polygon(P([(x, top), (x + half * 0.55, bot - (bot - top) * 0.45), (x + half * 0.25, bot - (bot - top) * 0.5)]),
                  fill=(40, 104, 68))
        for k in range(7):
            sx = x - half + (k + 0.5) * half * 2 / 7
            ellipse(d, sx, bot - 2, half * 0.09, 4, SNOW_SHADE if k % 2 else SNOW)
        outline.append((half, bot, top))
    # garland of lights, swinging across each tier
    colors = [(255, 70, 70), (255, 210, 80), (90, 180, 255), (120, 255, 140), (255, 140, 220)]
    lights = []
    for i, (half, bot, top) in enumerate(outline):
        yb = bot - (bot - top) * 0.3
        for k in range(13):
            t = k / 12
            lx = x - half * 0.85 + half * 1.7 * t
            ly = yb + 14 * math.sin(t * math.pi) - (bot - top) * 0.12 * t
            lights.append((lx, ly, colors[(k + i) % len(colors)]))
    gl = layer()
    gd = ImageDraw.Draw(gl)
    for lx, ly, c in lights:
        ellipse(gd, lx, ly, 10, 10, c + (110,))
    img.alpha_composite(gl.filter(ImageFilter.GaussianBlur(5 * S)))
    d = ImageDraw.Draw(img)
    for i, (half, bot, top) in enumerate(outline):
        yb = bot - (bot - top) * 0.3
        pts = [(x - half * 0.85 + half * 1.7 * k / 30, yb + 14 * math.sin(k / 30 * math.pi) - (bot - top) * 0.12 * k / 30)
               for k in range(31)]
        d.line(P(pts), fill=(210, 180, 90), width=int(1.4 * K * S))
    for lx, ly, c in lights:
        ellipse(d, lx, ly, 2.6, 2.6, lerp(c, (255, 255, 255), 0.45))
    # baubles
    for i, (half, bot, top) in enumerate(outline):
        for k in range(3 if i < 4 else 1):
            bx = x + rnd.uniform(-half * 0.6, half * 0.6)
            by = bot - (bot - top) * rnd.uniform(0.05, 0.22)
            c = [(196, 30, 44), (220, 170, 60), (40, 90, 180), (230, 230, 240)][(i + k) % 4]
            line(d, (bx, by - 9), (bx, by - 4), 1, (200, 180, 120))
            ellipse(d, bx, by, 7, 7, c)
            ellipse(d, bx - 2.2, by - 2.4, 2, 2, lerp(c, (255, 255, 255), 0.7))
    # the star on top
    sx, sy = x, y - h * 0.98 - 8
    glow(img, sx, sy, 44, (255, 220, 120), 150, 16)
    d = ImageDraw.Draw(img)
    star = []
    for k in range(10):
        r = 22 if k % 2 == 0 else 9
        a = -math.pi / 2 + k * math.pi / 5
        star.append((sx + math.cos(a) * r, sy + math.sin(a) * r))
    d.polygon(P(star), fill=(255, 214, 90))


def present(d, x, y, w, h, box, ribbon):
    d.rectangle([sc(x - w / 2, y - h), sc(x + w / 2, y)], fill=box)
    d.rectangle([sc(x + w / 6, y - h), sc(x + w / 2, y)], fill=lerp(box, (0, 0, 0), 0.2))
    line(d, (x - w / 2, y - h * 0.55), (x + w / 2, y - h * 0.55), 3, ribbon)
    line(d, (x - w * 0.05, y - h), (x - w * 0.05, y), 3, ribbon)
    ellipse(d, x - w * 0.05 - 6, y - h - 4, 6, 4, ribbon)
    ellipse(d, x - w * 0.05 + 6, y - h - 4, 6, 4, ribbon)
    ellipse(d, x - w * 0.05, y - h + 6, w * 0.5, 3, SNOW)


def snowman(img, x, y, s):
    sh = layer()
    ellipse(ImageDraw.Draw(sh), x + 10 * s, y + 2, 60 * s, 10 * s, (120, 140, 180, 120))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(5 * S)))
    d = ImageDraw.Draw(img)
    for cy, r in [(-46, 48), (-118, 36), (-172, 26)]:
        ellipse(d, x, y + cy * s, r * s, r * s, (238, 242, 250))
        ellipse(d, x + r * 0.25 * s, y + (cy + r * 0.25) * s, r * 0.78 * s, r * 0.78 * s, (226, 232, 244))
        ellipse(d, x - r * 0.2 * s, y + (cy - r * 0.2) * s, r * 0.55 * s, r * 0.55 * s, (250, 252, 255))
    # coal buttons, eyes, mouth, carrot nose
    for by in (-128, -112, -96):
        ellipse(d, x, y + by * s, 3.4 * s, 3.4 * s, (30, 30, 36))
    for ex in (-9, 9):
        ellipse(d, x + ex * s, y - 178 * s, 3.4 * s, 3.4 * s, (30, 30, 36))
    for k in range(5):
        a = math.radians(30 + k * 30)
        ellipse(d, x + math.cos(a) * 12 * s, y + (-168 + math.sin(a) * 6) * s, 2 * s, 2 * s, (30, 30, 36))
    d.polygon(P([(x, y - 174 * s), (x + 30 * s, y - 168 * s), (x, y - 166 * s)]), fill=(236, 120, 30))
    # scarf in Christmas red
    d.polygon(P([(x - 26 * s, y - 150 * s), (x + 26 * s, y - 150 * s), (x + 24 * s, y - 140 * s), (x - 24 * s, y - 140 * s)]),
              fill=(186, 28, 38))
    d.polygon(P([(x + 10 * s, y - 144 * s), (x + 22 * s, y - 144 * s), (x + 26 * s, y - 106 * s), (x + 14 * s, y - 108 * s)]),
              fill=(166, 22, 32))
    for k in range(3):
        line(d, (x + 15 * s + k * 4 * s, y - 107 * s), (x + 14 * s + k * 4 * s, y - 100 * s), 1.4 * s, (166, 22, 32))
    # top hat with a holly sprig
    d.rectangle([sc(x - 28 * s, y - 200 * s), sc(x + 28 * s, y - 194 * s)], fill=(26, 26, 32))
    d.rectangle([sc(x - 18 * s, y - 240 * s), sc(x + 18 * s, y - 198 * s)], fill=(26, 26, 32))
    d.rectangle([sc(x - 18 * s, y - 208 * s), sc(x + 18 * s, y - 202 * s)], fill=(186, 28, 38))
    for lx in (-8, -2):
        d.polygon(P([(x + (lx - 6) * s, y - 206 * s), (x + lx * s, y - 214 * s), (x + (lx + 6) * s, y - 206 * s),
                     (x + lx * s, y - 202 * s)]), fill=(40, 120, 60))
    ellipse(d, x - 5 * s, y - 206 * s, 2.6 * s, 2.6 * s, (220, 30, 40))
    # stick arms
    line(d, (x - 30 * s, y - 124 * s), (x - 84 * s, y - 158 * s), 3 * s, (80, 56, 40))
    line(d, (x - 70 * s, y - 149 * s), (x - 78 * s, y - 170 * s), 2 * s, (80, 56, 40))
    line(d, (x + 30 * s, y - 120 * s), (x + 82 * s, y - 106 * s), 3 * s, (80, 56, 40))
    line(d, (x + 68 * s, y - 110 * s), (x + 84 * s, y - 124 * s), 2 * s, (80, 56, 40))


def gingerbread(img, x, y, s, wave=0.0, seed=0):
    """A gingerbread man standing in the snow, feet at (x, y)."""
    sh = layer()
    ellipse(ImageDraw.Draw(sh), x, y + 2, 26 * s, 5 * s, (120, 140, 180, 110))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(3 * S)))
    d = ImageDraw.Draw(img)
    dough, edge, icing = (176, 106, 52), (128, 72, 34), (252, 250, 246)
    for c, grow in ((edge, 1.5), (dough, 0)):
        line(d, (x - 6 * s, y - 30 * s), (x - 14 * s, y - 4 * s), (13 + grow) * s, c)          # legs
        line(d, (x + 6 * s, y - 30 * s), (x + 14 * s, y - 4 * s), (13 + grow) * s, c)
        ellipse(d, x, y - 40 * s, (15 + grow / 2) * s, (17 + grow / 2) * s, c)                # body
        line(d, (x - 10 * s, y - 50 * s), (x - 28 * s, y - (44 + wave * 20) * s), (11 + grow) * s, c)  # arms
        line(d, (x + 10 * s, y - 50 * s), (x + 28 * s, y - 44 * s), (11 + grow) * s, c)
        ellipse(d, x, y - 68 * s, (13 + grow / 2) * s, (13 + grow / 2) * s, c)                # head
    # icing: zigzags on wrists and ankles, eyes, smile, buttons
    for (ax, ay) in ((-24, -44 - wave * 18), (24, -44), (-12, -10), (12, -10)):
        pts = [(x + (ax - 5 + k * 2.5) * s, y + (ay + (2 if k % 2 else -2)) * s) for k in range(5)]
        d.line(P(pts), fill=icing, width=int(1.6 * s * K * S))
    for ex in (-4.5, 4.5):
        ellipse(d, x + ex * s, y - 71 * s, 2 * s, 2.2 * s, icing)
    d.arc([sc(x - 6 * s, y - 70 * s), sc(x + 6 * s, y - 60 * s)], 20, 160, fill=icing, width=int(1.6 * s * K * S))
    for k, c in enumerate([(200, 30, 40), (40, 140, 70), (200, 30, 40)]):
        ellipse(d, x, y - (50 - k * 9) * s, 2.8 * s, 2.8 * s, c)


def elf(img, x, y, s):
    """A little elf with red hair carrying a present, feet at (x, y)."""
    sh = layer()
    ellipse(ImageDraw.Draw(sh), x, y + 2, 34 * s, 6 * s, (120, 140, 180, 110))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(3 * S)))
    d = ImageDraw.Draw(img)
    green, dark_green, red = (40, 132, 70), (26, 96, 52), (196, 30, 40)
    skin, hair = (246, 206, 178), (214, 70, 28)
    # curled shoes
    for sx in (-1, 1):
        d.polygon(P([(x + sx * 4 * s, y), (x + sx * 4 * s, y - 8 * s), (x + sx * 16 * s, y - 6 * s),
                     (x + sx * 24 * s, y - 14 * s), (x + sx * 22 * s, y - 4 * s), (x + sx * 16 * s, y)]), fill=dark_green)
        ellipse(d, x + sx * 24 * s, y - 15 * s, 2.4 * s, 2.4 * s, (240, 200, 70))
    # striped stockings
    for sx in (-1, 1):
        for k in range(6):
            c = red if k % 2 == 0 else (250, 248, 244)
            line(d, (x + sx * 7 * s, y - (8 + k * 4) * s), (x + sx * 7 * s, y - (12 + k * 4) * s), 7 * s, c)
    # tunic with a zigzag hem and a belt
    hem = [(x - 22 * s + k * 5.5 * s, y - (32 if k % 2 else 28) * s) for k in range(9)]
    d.polygon(P([(x - 12 * s, y - 70 * s), (x + 12 * s, y - 70 * s)] + list(reversed(hem))), fill=green)
    d.rectangle([sc(x - 16 * s, y - 50 * s), sc(x + 16 * s, y - 44 * s)], fill=(60, 36, 24))
    d.rectangle([sc(x - 4 * s, y - 51 * s), sc(x + 4 * s, y - 43 * s)], fill=(236, 196, 70))
    d.rectangle([sc(x - 2 * s, y - 49 * s), sc(x + 2 * s, y - 45 * s)], fill=(60, 36, 24))
    # white collar
    for k in range(5):
        d.polygon(P([(x + (-14 + k * 7) * s, y - 70 * s), (x + (-10.5 + k * 7) * s, y - 63 * s),
                     (x + (-7 + k * 7) * s, y - 70 * s)]), fill=(250, 248, 244))
    # arms holding a present
    line(d, (x - 12 * s, y - 64 * s), (x - 14 * s, y - 48 * s), 7 * s, green)
    line(d, (x + 12 * s, y - 64 * s), (x + 14 * s, y - 48 * s), 7 * s, green)
    present(d, x, y - 36 * s, 26 * s, 20 * s, (236, 196, 70), red)
    ellipse(d, x - 14 * s, y - 46 * s, 4 * s, 4 * s, skin)
    ellipse(d, x + 14 * s, y - 46 * s, 4 * s, 4 * s, skin)
    # head: ears, face, freckles, red hair
    for sx in (-1, 1):
        d.polygon(P([(x + sx * 12 * s, y - 86 * s), (x + sx * 24 * s, y - 94 * s), (x + sx * 13 * s, y - 80 * s)]), fill=skin)
    for hx, hy, r in [(-13, -84, 6), (13, -84, 6), (-15, -78, 5), (15, -78, 5), (-11, -74, 4), (11, -74, 4)]:
        ellipse(d, x + hx * s, y + hy * s, r * s, r * s, hair)
    ellipse(d, x, y - 84 * s, 13 * s, 14 * s, skin)
    for k in range(4):
        ellipse(d, x + (-9 + k * 5) * s, y - 99 * s, 5 * s, 4 * s, hair)
    for ex in (-5, 5):
        ellipse(d, x + ex * s, y - 86 * s, 1.8 * s, 2.2 * s, (40, 30, 30))
        ellipse(d, x + ex * 1.4 * s, y - 79 * s, 3 * s, 2 * s, (240, 140, 130))
    for fx, fy in [(-7, -81), (-5, -79), (6, -81), (8, -79)]:
        ellipse(d, x + fx * s, y + fy * s, 0.7 * s, 0.7 * s, (190, 110, 70))
    d.arc([sc(x - 5 * s, y - 84 * s), sc(x + 5 * s, y - 75 * s)], 20, 160, fill=(150, 50, 50), width=int(1.4 * s * K * S))
    # pointed hat with a fur brim, bending to the side, and a bell
    d.polygon(P([(x - 14 * s, y - 96 * s), (x + 14 * s, y - 96 * s), (x + 10 * s, y - 118 * s), (x + 22 * s, y - 134 * s),
                 (x + 30 * s, y - 128 * s), (x + 16 * s, y - 126 * s), (x - 2 * s, y - 112 * s)]), fill=red)
    line(d, (x - 14 * s, y - 96 * s), (x + 14 * s, y - 96 * s), 6 * s, (250, 248, 244))
    ellipse(d, x + 31 * s, y - 127 * s, 4 * s, 4 * s, (240, 200, 70))


def angel(img, x, y, s, flip=False, item="star"):
    """A little angel with a halo, golden curls, small wings and a trumpet, a candle or a star; (x, y) is the
    centre of the head."""
    k = -1 if flip else 1
    glow(img, x, y + 20 * s, 60 * s, (255, 236, 190), 60, 20)
    d = ImageDraw.Draw(img)
    gold, robe, skin = (232, 186, 70), (252, 246, 236), (248, 214, 190)
    for side in (-1, 1):
        for i in range(6):
            t = i / 5
            fx = x + side * (6 + 34 * t) * s
            fy = y + (26 - 26 * math.sin(t * math.pi * 0.6)) * s
            r = 9 * (1 - t * 0.4) * s
            ellipse(d, fx, fy, r, r * 1.2, lerp((255, 255, 255), (238, 226, 190), t * 0.6))
    # a short robe, legs dangling as if flying
    d.polygon(P([(x - 9 * s, y + 14 * s), (x + 9 * s, y + 14 * s), (x + 20 * s, y + 52 * s), (x + 4 * s, y + 48 * s),
                 (x - 8 * s, y + 54 * s), (x - 18 * s, y + 46 * s)]), fill=robe)
    line(d, (x - 4 * s, y + 52 * s), (x - 12 * s, y + 64 * s), 4 * s, skin)
    line(d, (x + 8 * s, y + 50 * s), (x + 14 * s, y + 60 * s), 4 * s, skin)
    line(d, (x + k * 6 * s, y + 22 * s), (x + k * 20 * s, y + 26 * s), 6 * s, robe)
    hx, hy = x + k * 22 * s, y + 26 * s
    ellipse(d, hx, hy, 3.4 * s, 3.4 * s, skin)
    if item == "trumpet":
        d.polygon(P([(hx, hy - 2 * s), (hx + k * 22 * s, hy - 8 * s), (hx + k * 28 * s, hy - 15 * s), (hx + k * 30 * s, hy - 2 * s),
                     (hx + k * 22 * s, hy - 4 * s), (hx, hy + 2 * s)]), fill=gold)
    elif item == "candle":
        d.rectangle([sc(hx - 3 * s, hy - 18 * s), sc(hx + 3 * s, hy)], fill=(250, 244, 230))
        glow(img, hx, hy - 24 * s, 14 * s, (255, 200, 90), 170, 6)
        d = ImageDraw.Draw(img)
        ellipse(d, hx, hy - 23 * s, 2.6 * s, 5 * s, (255, 214, 100))
    else:
        star = [(hx + math.cos(-math.pi / 2 + j * math.pi / 5) * (9 if j % 2 == 0 else 3.8) * s,
                 hy - 10 * s + math.sin(-math.pi / 2 + j * math.pi / 5) * (9 if j % 2 == 0 else 3.8) * s) for j in range(10)]
        glow(img, hx, hy - 10 * s, 16 * s, (255, 226, 120), 150, 6)
        d = ImageDraw.Draw(img)
        d.polygon(P(star), fill=(255, 226, 110))
    # curls, face, halo
    for cx, cy, r in [(-10, 0, 6), (10, 0, 6), (-7, -9, 6), (7, -9, 6), (0, -11, 6), (-11, 8, 4.5), (11, 8, 4.5)]:
        ellipse(d, x + cx * s, y + cy * s, r * s, r * s, (238, 196, 84))
    ellipse(d, x, y + 2 * s, 9.5 * s, 10 * s, skin)
    for ex in (-3.5, 3.5):
        d.arc([sc(x + (ex - 2) * s, y), sc(x + (ex + 2) * s, y + 3.5 * s)], 200, 340, fill=(120, 80, 60),
              width=max(1, int(1.2 * s * K * S)))
        ellipse(d, x + ex * 1.4 * s, y + 5.5 * s, 2.2 * s, 1.5 * s, (244, 160, 150))
    d.arc([sc(x - 3 * s, y + 3 * s), sc(x + 3 * s, y + 8 * s)], 20, 160, fill=(190, 90, 90), width=max(1, int(1.2 * s * K * S)))
    halo = [sc(x - 11 * s, y - 24 * s), sc(x + 11 * s, y - 17 * s)]
    d.ellipse(halo, outline=(255, 214, 100), width=max(2, int(2.4 * s * K * S)))


# ---------------------------------------------------------------- the nativity

def stable(img, x, y):
    """The stable: back wall, posts, a snowy thatched roof, straw and a lantern; (x, y) is the middle of the
    floor."""
    d = ImageDraw.Draw(img)
    wood, dark = (84, 56, 36), (54, 36, 24)
    d.rectangle([sc(x - 135, y - 150), sc(x + 135, y)], fill=dark)
    d.polygon(P([(x - 135, y - 149), (x, y - 212), (x + 135, y - 149)]), fill=dark)
    for k in range(-6, 7):
        line(d, (x + k * 21, y - 150), (x + k * 21, y), 1.4, (44, 28, 18))
    glow(img, x, y - 60, 130, (255, 186, 96), 120, 30)
    d = ImageDraw.Draw(img)
    ellipse(d, x, y - 4, 146, 12, (206, 172, 94))
    for k in range(40):
        a = -1 + k / 20
        line(d, (x + a * 130, y - 4), (x + a * 136 + (k % 3 - 1) * 5, y - 14), 1.2, (226, 196, 112))
    for px in (-140, 140):
        d.rectangle([sc(x + px - 7, y - 150), sc(x + px + 7, y + 2)], fill=wood)
    d.polygon(P([(x - 170, y - 138), (x, y - 220), (x + 170, y - 138), (x + 170, y - 124), (x, y - 206),
                 (x - 170, y - 124)]), fill=(150, 110, 62))
    for k in range(30):
        t = k / 29
        line(d, (x - 168 + 336 * t, y - 124 - (82 if t < 0.5 else 82) * (1 - abs(t - 0.5) * 2) + 2),
             (x - 166 + 336 * t, y - 116 - 82 * (1 - abs(t - 0.5) * 2)), 1.2, (176, 134, 76))
    d.polygon(P([(x - 176, y - 136), (x, y - 228), (x + 176, y - 136), (x + 160, y - 134), (x, y - 214),
                 (x - 160, y - 134)]), fill=SNOW)
    for k in range(9):
        t = k / 8
        ellipse(d, x - 168 + 336 * t, y - 134 - 86 * (1 - abs(t - 0.5) * 2) + 4, 12, 6, SNOW)
    # the lantern on the beam
    line(d, (x + 96, y - 150), (x + 96, y - 134), 1.2, (40, 30, 20))
    glow(img, x + 96, y - 124, 26, (255, 200, 100), 160, 8)
    d = ImageDraw.Draw(img)
    d.rectangle([sc(x + 90, y - 134), sc(x + 102, y - 116)], fill=(255, 214, 120))
    d.rectangle([sc(x + 88, y - 136), sc(x + 104, y - 133)], fill=(40, 30, 20))


def ox(d, x, y):
    """The ox in profile, facing right: (x, y) is the middle of its head."""
    body, dark, light, horn = (138, 92, 56), (100, 64, 38), (214, 184, 150), (240, 230, 206)
    ellipse(d, x - 44, y + 62, 38, 30, body)                                        # body, behind the head
    for lx in (-68, -50, -30):
        line(d, (x + lx, y + 80), (x + lx, y + 116), 9, dark)
        ellipse(d, x + lx, y + 117, 6, 3, (60, 40, 26))
    d.polygon(P([(x - 30, y + 14), (x - 8, y - 10), (x + 6, y - 8), (x + 2, y + 40), (x - 20, y + 60), (x - 44, y + 40)]), fill=body)  # neck
    d.polygon(P([(x - 12, y - 14), (x + 12, y - 16), (x + 26, y + 2), (x + 26, y + 14), (x + 10, y + 18),
                 (x - 10, y + 10)]), fill=body)                                     # head
    ellipse(d, x + 22, y + 10, 10, 9, light)                                        # muzzle
    ellipse(d, x + 26, y + 9, 2, 1.6, (70, 46, 34))
    ellipse(d, x + 4, y - 5, 2.6, 2.6, (30, 20, 16))                               # eye
    ellipse(d, x + 4.8, y - 5.8, 0.9, 0.9, (255, 255, 255))
    d.polygon(P([(x - 12, y - 10), (x - 26, y - 4), (x - 14, y + 2)]), fill=dark)  # ear
    d.polygon(P([(x - 6, y - 14), (x - 12, y - 24), (x - 6, y - 34), (x - 2, y - 32), (x - 6, y - 24), (x, y - 15)]),
              fill=horn)                                                            # horn
    d.polygon(P([(x + 2, y - 15), (x + 8, y - 26), (x + 16, y - 32), (x + 18, y - 29), (x + 11, y - 24), (x + 7, y - 14)]),
              fill=horn)


def donkey(d, x, y):
    """The donkey in profile, facing left: (x, y) is the middle of its head."""
    body, dark, muzzle = (150, 144, 140), (96, 92, 90), (226, 222, 214)
    ellipse(d, x + 52, y + 36, 40, 28, body)
    for lx in (34, 52, 76):
        line(d, (x + lx, y + 52), (x + lx, y + 100), 7, dark)
        ellipse(d, x + lx, y + 101, 5, 3, (50, 46, 46))
    d.polygon(P([(x + 26, y + 18), (x + 8, y - 8), (x - 6, y - 6), (x - 2, y + 30), (x + 26, y + 40)]), fill=body)
    for k in range(6):
        line(d, (x + 8 + k * 3.5, y - 10 + k * 4.5), (x + 13 + k * 3.5, y - 14 + k * 4.5), 3, (60, 56, 56))   # mane
    d.polygon(P([(x + 10, y - 12), (x - 6, y - 14), (x - 24, y + 6), (x - 26, y + 18), (x - 10, y + 20), (x + 8, y + 10)]),
              fill=body)
    ellipse(d, x - 20, y + 13, 10, 9, muzzle)
    ellipse(d, x - 26, y + 11, 1.8, 1.4, (60, 56, 56))
    ellipse(d, x - 2, y - 4, 2.6, 2.6, (30, 20, 16))
    ellipse(d, x - 2.8, y - 4.8, 0.9, 0.9, (255, 255, 255))
    for ex, tilt in ((2, -6), (10, 4)):                                             # the long ears
        d.polygon(P([(x + ex - 4, y - 12), (x + ex + tilt - 4, y - 40), (x + ex + tilt, y - 46), (x + ex + tilt + 4, y - 40),
                     (x + ex + 4, y - 12)]), fill=body)
        d.polygon(P([(x + ex - 1.5, y - 16), (x + ex + tilt - 1.5, y - 38), (x + ex + tilt + 1.5, y - 38), (x + ex + 1.5, y - 16)]),
                  fill=dark)


def manger(img, x, y):
    d = ImageDraw.Draw(img)
    wood = (120, 82, 50)
    line(d, (x - 26, y), (x + 14, y - 30), 4, wood)
    line(d, (x + 26, y), (x - 14, y - 30), 4, wood)
    d.polygon(P([(x - 34, y - 46), (x + 34, y - 46), (x + 24, y - 24), (x - 24, y - 24)]), fill=wood)
    for k in range(18):
        a = -1 + k / 9
        line(d, (x + a * 30, y - 46), (x + a * 36 + (k % 3 - 1) * 4, y - 54), 1.4, (232, 200, 110))
    glow(img, x + 4, y - 56, 44, (255, 236, 170), 200, 14)
    d = ImageDraw.Draw(img)
    ellipse(d, x - 2, y - 54, 19, 9, (252, 250, 244))
    for k in range(3):
        line(d, (x - 14 + k * 8, y - 61), (x - 10 + k * 8, y - 47), 1, (220, 214, 200))
    ellipse(d, x + 16, y - 57, 8, 7.5, (248, 214, 190))
    ellipse(d, x + 18, y - 62, 6, 3.5, (200, 150, 90))
    d.ellipse([sc(x + 4, y - 74), sc(x + 28, y - 62)], outline=(255, 214, 100), width=max(2, int(2 * K * S)))


def mary(d, x, y):
    blue, white, skin = (46, 88, 168), (244, 240, 230), (240, 206, 182)
    d.polygon(P([(x - 34, y), (x + 26, y), (x + 20, y - 34), (x + 10, y - 66), (x - 8, y - 74), (x - 22, y - 52)]), fill=blue)
    d.polygon(P([(x + 4, y - 40), (x + 22, y - 36), (x + 24, y - 28), (x + 6, y - 30)]), fill=(36, 70, 140))
    ellipse(d, x + 2, y - 82, 15, 16, blue)
    ellipse(d, x + 4, y - 81, 11, 13, white)
    ellipse(d, x + 6, y - 80, 8.5, 10, skin)
    d.arc([sc(x + 4, y - 84), sc(x + 9, y - 80)], 200, 340, fill=(110, 70, 60), width=max(1, int(1.2 * K * S)))
    ellipse(d, x + 23, y - 50, 5, 6, skin)
    d.ellipse([sc(x - 14, y - 108), sc(x + 22, y - 96)], outline=(255, 214, 100), width=max(2, int(2 * K * S)))


def joseph(d, x, y):
    robe, cloak, skin, hair = (128, 88, 52), (82, 104, 66), (236, 198, 170), (116, 96, 80)
    d.polygon(P([(x - 20, y), (x + 20, y), (x + 14, y - 82), (x - 14, y - 82)]), fill=robe)
    d.polygon(P([(x - 18, y - 84), (x + 16, y - 84), (x + 22, y - 36), (x + 4, y - 14), (x - 22, y - 30)]), fill=cloak)
    line(d, (x - 18, y - 66), (x - 30, y - 54), 7, cloak)
    line(d, (x - 30, y), (x - 32, y - 132), 3, (100, 70, 44))
    ellipse(d, x - 31, y - 56, 5, 5, skin)
    ellipse(d, x, y - 98, 11, 12, skin)
    d.polygon(P([(x - 11, y - 96), (x + 11, y - 96), (x + 8, y - 82), (x, y - 78), (x - 8, y - 82)]), fill=hair)
    ellipse(d, x, y - 107, 11, 6, hair)
    for ex in (-4, 4):
        ellipse(d, x + ex, y - 99, 1.5, 1.5, (50, 40, 30))
    d.ellipse([sc(x - 15, y - 122), sc(x + 15, y - 110)], outline=(255, 214, 100), width=max(2, int(2 * K * S)))


def shepherd(d, x, y, robe, cloth, flip=False, kneel=False):
    k = -1 if flip else 1
    skin, beard = (226, 186, 150), (100, 80, 64)
    top = 56 if kneel else 84
    d.polygon(P([(x - 20, y), (x + 20, y), (x + 13, y - top), (x - 13, y - top)]), fill=robe)
    line(d, (x - 15, y - top * 0.55), (x + 15, y - top * 0.55), 3, (70, 50, 36))
    for sx in (-14, 14):
        d.polygon(P([(x + sx - 4, y - top), (x + sx + 4, y - top), (x + sx * 1.1 + 4, y - top * 0.4), (x + sx * 1.1 - 4, y - top * 0.4)]),
                  fill=lerp(robe, (255, 255, 255), 0.25))
    hx, hy = x, y - top - 13
    ellipse(d, hx, hy, 11, 12, skin)
    d.polygon(P([(hx - 13, hy - 2), (hx - 12, hy - 14), (hx, hy - 17), (hx + 12, hy - 14), (hx + 13, hy - 2), (hx + 14, hy + 16),
                 (hx + 9, hy + 2), (hx - 9, hy + 2), (hx - 14, hy + 16)]), fill=cloth)
    line(d, (hx - 12, hy - 8), (hx + 12, hy - 8), 2.4, lerp(cloth, (255, 255, 255), 0.5))
    d.polygon(P([(hx - 7, hy + 4), (hx + 7, hy + 4), (hx + 4, hy + 13), (hx - 4, hy + 13)]), fill=beard)
    for ex in (-4, 4):
        ellipse(d, hx + ex, hy - 1, 1.5, 1.5, (50, 40, 30))
    cx = x + k * 26
    line(d, (x + k * 12, y - top * 0.7), (cx, y - top * 0.75), 6, robe)
    line(d, (cx, y), (cx, y - top - 52), 3, (120, 86, 52))
    hook = [(cx + k * 9 * (1 - math.cos(a)) , y - top - 52 - 9 * math.sin(a)) for a in [i * math.pi / 8 for i in range(9)]]
    d.line(P(hook), fill=(120, 86, 52), width=int(3 * K * S))
    ellipse(d, cx, y - top * 0.75, 4.5, 4.5, skin)


def sheep(d, x, y, s=1.0, flip=False):
    k = -1 if flip else 1
    for lx in (-12, -5, 6, 13):
        line(d, (x + lx * s, y - 8 * s), (x + lx * s, y), 3 * s, (40, 36, 36))
    for cx, cy, r in [(-12, -18, 10), (0, -21, 11), (12, -18, 10), (-6, -12, 10), (8, -12, 10), (-16, -12, 8), (16, -13, 8),
                      (0, -28, 8), (-10, -26, 7), (10, -26, 7)]:
        ellipse(d, x + cx * s, y + cy * s, r * s, r * s, (236, 234, 226))
        ellipse(d, x + (cx - 2) * s, y + (cy - 3) * s, r * 0.5 * s, r * 0.5 * s, (250, 250, 246))
    hx = x + k * 23 * s
    ellipse(d, hx, y - 20 * s, 6.5 * s, 8 * s, (44, 40, 40))
    ellipse(d, hx - k * 6 * s, y - 24 * s, 5 * s, 2.4 * s, (44, 40, 40))
    ellipse(d, hx + k * 2 * s, y - 22 * s, 1.3 * s, 1.3 * s, (220, 220, 220))


def king(img, x, y, robe, cape, skin, beard, gift):
    d = ImageDraw.Draw(img)
    gold = (232, 186, 70)
    d.polygon(P([(x - 22, y), (x + 22, y), (x + 15, y - 92), (x - 15, y - 92)]), fill=robe)
    d.polygon(P([(x - 18, y - 94), (x + 18, y - 94), (x + 30, y - 4), (x + 10, y + 2), (x - 26, y + 2), (x - 30, y - 6)]), fill=cape)
    line(d, (x - 22, y - 2), (x + 26, y - 2), 3, gold)
    d.polygon(P([(x - 18, y - 94), (x + 18, y - 94), (x + 14, y - 84), (x - 14, y - 84)]), fill=(240, 236, 226))
    # arms forward with the gift
    line(d, (x - 12, y - 76), (x - 26, y - 58), 8, cape)
    gx, gy = x - 30, y - 58
    ellipse(d, gx + 4, gy + 2, 4.5, 4.5, skin)
    if gift == "chest":
        d.rectangle([sc(gx - 14, gy - 12), sc(gx + 8, gy + 2)], fill=gold)
        d.rectangle([sc(gx - 14, gy - 14), sc(gx + 8, gy - 10)], fill=(200, 150, 50))
        ellipse(d, gx - 3, gy - 5, 2, 2, (180, 30, 40))
    elif gift == "jar":
        ellipse(d, gx - 4, gy - 4, 9, 10, gold)
        d.rectangle([sc(gx - 7, gy - 20), sc(gx - 1, gy - 12)], fill=gold)
        ellipse(d, gx - 4, gy - 21, 5, 2.5, (200, 150, 50))
    else:
        d.polygon(P([(gx - 12, gy + 2), (gx + 4, gy + 2), (gx + 2, gy - 14), (gx - 4, gy - 22), (gx - 10, gy - 14)]), fill=gold)
        ellipse(d, gx - 4, gy - 22, 3, 3, (200, 150, 50))
    glow(img, gx - 4, gy - 6, 18, (255, 220, 120), 70, 6)
    d = ImageDraw.Draw(img)
    hx, hy = x, y - 106
    ellipse(d, hx, hy, 11, 12, skin)
    if beard:
        d.polygon(P([(hx - 10, hy + 2), (hx + 10, hy + 2), (hx + 6, hy + 16), (hx, hy + 20), (hx - 6, hy + 16)]), fill=beard)
    for ex in (-4, 4):
        ellipse(d, hx + ex - 2, hy - 1, 1.5, 1.5, (40, 30, 24))
    crown = [(hx - 12, hy - 8), (hx - 13, hy - 24), (hx - 6, hy - 16), (hx, hy - 28), (hx + 6, hy - 16), (hx + 13, hy - 24),
             (hx + 12, hy - 8)]
    d.polygon(P(crown), fill=gold)
    for cx, c in [(-6, (180, 30, 40)), (0, (40, 120, 200)), (6, (40, 150, 80))]:
        ellipse(d, hx + cx, hy - 11, 2, 2, c)


def snowfall(img, count, rmin, rmax, alpha, blur, seed):
    f = layer()
    d = ImageDraw.Draw(f)
    rnd = random.Random(seed)
    for _ in range(count):
        r = rnd.uniform(rmin, rmax)
        ellipse(d, rnd.uniform(0, 1920), rnd.uniform(0, 1080), r, r, (255, 255, 255, alpha))
    img.alpha_composite(f.filter(ImageFilter.GaussianBlur(blur * S)) if blur else f)


# ---------------------------------------------------------------- the scene

def main():
    img = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 255))
    sky(img)
    star_of_bethlehem(img)
    moon(img)
    for ax, ay, asc, flip, item in [(250, 430, 1.0, False, "trumpet"), (680, 200, 0.8, True, "star"),
                                    (1330, 250, 0.95, True, "candle"), (1620, 360, 0.85, False, "trumpet"),
                                    (760, 470, 0.75, False, "star"), (1110, 610, 0.7, True, "candle")]:
        angel(img, ax, ay, asc, flip, item)
    snowfall(img, 260, 1.0, 2.0, 120, 0.8, 1)

    # far hills with pine forest
    snowy_ground(img, 720, 26, 240, 0.5, (150, 168, 204), (120, 140, 182))
    d = ImageDraw.Draw(img)
    rnd = random.Random(3)
    for x in range(-20, 1960, 22):
        if rnd.random() < 0.7:
            pine(d, x + rnd.uniform(-8, 8), hill(x, 720, 26, 240, 0.5) + 8, rnd.uniform(40, 80), fill=(34, 56, 72),
                 snow=(170, 186, 216), tiers=3)
    snowy_ground(img, 800, 18, 300, 2.0, (196, 210, 234), (170, 186, 216))

    # the village
    for x, w, h, roof, color, seed in [(640, 110, 70, 46, (150, 70, 56), 1), (790, 90, 62, 40, (196, 160, 110), 2),
                                       (1210, 120, 76, 50, (110, 60, 66), 3), (1360, 96, 64, 42, (170, 120, 80), 4),
                                       (880, 80, 56, 34, (126, 92, 70), 5)]:
        house(img, x, hill(x, 800, 18, 300, 2.0) + 10, w, h, roof, color, seed)
    d = ImageDraw.Draw(img)
    for x, h in [(540, 120), (575, 90), (1450, 130), (1490, 96), (730, 70), (1300, 64)]:
        pine(d, x, hill(x, 800, 18, 300, 2.0) + 16, h)

    # foreground snow
    snowy_ground(img, 930, 22, 360, 4.0, (226, 234, 248), (198, 212, 238))
    d = ImageDraw.Draw(img)
    for x, h in [(80, 330), (190, 250), (1830, 360), (1730, 230)]:
        pine(d, x, hill(x, 930, 22, 360, 4.0) + 30, h, tiers=5)

    christmas_tree(img, 1520, hill(1520, 930, 22, 360, 4.0) + 20, 430)
    d = ImageDraw.Draw(img)
    gy = hill(1520, 930, 22, 360, 4.0) + 26
    for dx, w, h, box, ribbon in [(-90, 54, 40, (186, 28, 38), (230, 200, 90)), (-38, 40, 52, (40, 110, 70), (230, 60, 60)),
                                  (52, 60, 36, (230, 200, 90), (186, 28, 38)), (100, 36, 30, (50, 90, 170), (240, 240, 250))]:
        present(d, 1520 + dx, gy + 4, w, h, box, ribbon)

    # the nativity in the middle of the field
    def gy(x):
        return hill(x, 930, 22, 360, 4.0) + 34
    sx = 985
    stable(img, sx, gy(sx))
    d = ImageDraw.Draw(img)
    ox(d, sx - 78, gy(sx) - 122)
    donkey(d, sx + 80, gy(sx) - 104)
    mary(d, sx - 54, gy(sx))
    manger(img, sx, gy(sx))
    d = ImageDraw.Draw(img)
    joseph(d, sx + 46, gy(sx))
    sheep(d, 770, gy(770) + 4, 1.1)
    shepherd(d, 730, gy(730), (132, 104, 70), (196, 176, 140))
    shepherd(d, 810, gy(810) + 6, (96, 110, 80), (150, 60, 50), kneel=True)
    for x, sz, flip in [(660, 1.0, False), (700, 0.75, False), (850, 0.9, False)]:
        sheep(d, x, gy(x) + 24, sz, flip)
    for x, robe, cape, skin, beard, gift in [(1160, (150, 30, 40), (220, 170, 60), (240, 206, 180), (230, 226, 216), "chest"),
                                             (1225, (60, 40, 110), (46, 120, 90), (196, 146, 106), (60, 44, 34), "jar"),
                                             (1290, (40, 100, 70), (170, 40, 50), (110, 72, 50), None, "horn")]:
        king(img, x, gy(x) + 4, robe, cape, skin, beard, gift)

    snowman(img, 380, hill(380, 930, 22, 360, 4.0) + 34, 1.0)
    for gx, gs, wave in [(500, 1.2, 1.0), (570, 1.05, 0.0), (1690, 1.1, 1.0)]:
        gingerbread(img, gx, hill(gx, 930, 22, 360, 4.0) + 40, gs, wave)
    elf(img, 1785, hill(1785, 930, 22, 360, 4.0) + 52, 0.8)

    # a footpath of lanterns and the closest, softest snowflakes
    snowfall(img, 160, 2.0, 3.6, 200, 1.0, 2)
    snowfall(img, 40, 5, 8, 150, 3.0, 3)

    out = img.convert("RGB").resize((W, H), Image.LANCZOS)
    out.save(OUT, optimize=True)


# ---------------------------------------------------------------- the American picture

BULBS = [(255, 60, 60), (255, 200, 60), (60, 200, 90), (70, 140, 255), (255, 130, 40)]


def reindeer(d, ox, oy, s, phase, fill, nose=None):
    leg = 9 * math.sin(phase)
    ellipse(d, ox, oy, 17 * s, 8 * s, fill)
    d.polygon(P(tf([(10, -4), (18, -18), (22, -24), (26, -22), (20, -10), (16, 0)], ox, oy, s)), fill=fill)
    ellipse(d, ox + 26 * s, oy - 25 * s, 6 * s, 4 * s, fill)
    d.polygon(P(tf([(28, -27), (36, -24), (35, -21), (28, -22)], ox, oy, s)), fill=fill)
    for a, b in [((22, -28), (18, -40)), ((18, -40), (12, -46)), ((18, -40), (22, -48)),
                 ((24, -28), (26, -40)), ((26, -40), (22, -47)), ((26, -40), (31, -46))]:
        line(d, tf([a], ox, oy, s)[0], tf([b], ox, oy, s)[0], 1.3 * s, fill)
    for hx, sgn in [(12, 1), (8, -1), (-12, 1), (-15, -1)]:
        knee = (hx + sgn * leg * 0.6, 12)
        foot = (hx + sgn * leg * 1.2 + (6 if hx > 0 else -6), 20)
        line(d, tf([(hx, 4)], ox, oy, s)[0], tf([knee], ox, oy, s)[0], 3 * s, fill)
        line(d, tf([knee], ox, oy, s)[0], tf([foot], ox, oy, s)[0], 2.2 * s, fill)
    d.polygon(P(tf([(-16, -4), (-24, -10), (-20, -2)], ox, oy, s)), fill=fill)


def santa_sleigh(img, x, y, s):
    """Santa's sleigh and nine reindeer flying up to the right across the moon, Rudolph's red nose in front."""
    d = ImageDraw.Draw(img)
    fill = (12, 18, 36)
    rise = -0.22
    spots = []
    for i in range(9):
        lead = i == 8
        rx = x + 70 * s + (i // 2) * 50 * s + (i % 2) * 10 * s + (20 * s if lead else 0)
        ry = y + rise * (rx - x) - (i % 2) * 6 * s
        reindeer(d, rx, ry, s * 0.9, i * 1.3, fill)
        if lead:
            spots.append((rx + 35 * s * 0.9, ry - 22.5 * s * 0.9))
    line(d, (x + 20 * s, y - 14 * s), (spots[0][0] - 30 * s, spots[0][1] + 20 * s), 1.0 * s, fill)
    body = [(-36, -18), (20, -18), (30, -30), (38, -30), (32, -12), (22, 0), (-30, 0), (-42, -10)]
    d.polygon(P(tf(body, x, y, s)), fill=fill)
    line(d, tf([(-40, 8)], x, y, s)[0], tf([(34, 8)], x, y, s)[0], 2.4 * s, fill)
    line(d, tf([(34, 8)], x, y, s)[0], tf([(42, 0)], x, y, s)[0], 2.4 * s, fill)
    for px in (-24, 10):
        line(d, tf([(px, 0)], x, y, s)[0], tf([(px, 8)], x, y, s)[0], 2 * s, fill)
    ellipse(d, x - 2 * s, y - 28 * s, 13 * s, 14 * s, fill)
    ellipse(d, x + 2 * s, y - 46 * s, 7 * s, 7 * s, fill)
    d.polygon(P(tf([(-6, -50), (10, -52), (-4, -64), (-14, -60)], x, y, s)), fill=fill)
    ellipse(d, x - 15 * s, y - 60 * s, 3 * s, 3 * s, fill)
    line(d, tf([(4, -34)], x, y, s)[0], tf([(20, -26)], x, y, s)[0], 4 * s, fill)
    ellipse(d, x - 26 * s, y - 30 * s, 14 * s, 16 * s, fill)
    for nx, ny in spots:
        glow(img, nx, ny, 14 * s, (255, 40, 40), 200, 5)
        ellipse(ImageDraw.Draw(img), nx, ny, 2.6 * s, 2.6 * s, (255, 70, 60))


def light_string(img, pts, spacing=16, size=3.4, seed=0):
    """Big coloured bulbs along a polyline, each with its own glow."""
    marks = []
    for (x1, y1), (x2, y2) in zip(pts, pts[1:]):
        n = max(1, int(math.hypot(x2 - x1, y2 - y1) / spacing))
        for k in range(n):
            t = k / n
            marks.append((x1 + (x2 - x1) * t, y1 + (y2 - y1) * t + 3 * math.sin(t * math.pi)))
    g = layer()
    gd = ImageDraw.Draw(g)
    for i, (bx, by) in enumerate(marks):
        ellipse(gd, bx, by + 3, size * 3.2, size * 3.2, BULBS[(i + seed) % len(BULBS)] + (120,))
    img.alpha_composite(g.filter(ImageFilter.GaussianBlur(5 * S)))
    d = ImageDraw.Draw(img)
    d.line(P(pts), fill=(30, 40, 30), width=max(1, int(1.2 * K * S)))
    for i, (bx, by) in enumerate(marks):
        c = BULBS[(i + seed) % len(BULBS)]
        ellipse(d, bx, by + 3, size * 0.8, size * 1.2, lerp(c, (255, 255, 255), 0.35))


def us_house(img, x, y, w, h, siding, seed, wreath=True):
    """A two-storey house trimmed with coloured lights along the roof and the porch; (x, y) is the middle of
    its front at the ground."""
    d = ImageDraw.Draw(img)
    roof = h * 0.55
    trim = (236, 236, 240)
    d.rectangle([sc(x - w / 2, y - h), sc(x + w / 2, y)], fill=siding)
    for k in range(1, int(h / 9)):
        line(d, (x - w / 2, y - k * 9), (x + w / 2, y - k * 9), 0.8, lerp(siding, (0, 0, 0), 0.12))
    d.polygon(P([(x - w / 2 - 14, y - h + 2), (x, y - h - roof), (x + w / 2 + 14, y - h + 2)]), fill=(58, 52, 60))
    d.polygon(P([(x - w / 2 - 16, y - h + 2), (x, y - h - roof - 6), (x + w / 2 + 16, y - h + 2),
                 (x + w / 2 + 6, y - h + 6), (x, y - h - roof + 10), (x - w / 2 - 6, y - h + 6)]), fill=SNOW)
    # chimney
    cx = x + w * 0.25
    d.rectangle([sc(cx, y - h - roof * 0.75), sc(cx + w * 0.09, y - h - roof * 0.2)], fill=(140, 62, 50))
    ellipse(d, cx + w * 0.045, y - h - roof * 0.76, w * 0.06, 3, SNOW)
    # windows: two up, two down, the door with a wreath in the middle
    for wx in (-0.3, 0.3):
        for wy in (0.72, 0.32):
            px, py = x + wx * w, y - h * wy
            glow(img, px, py, w * 0.12, WINDOW, 60, 10)
            d = ImageDraw.Draw(img)
            d.rectangle([sc(px - w * 0.08, py - h * 0.11), sc(px + w * 0.08, py + h * 0.11)], fill=WINDOW)
            line(d, (px, py - h * 0.11), (px, py + h * 0.11), 2, trim)
            line(d, (px - w * 0.08, py), (px + w * 0.08, py), 2, trim)
            d.rectangle([sc(px - w * 0.11, py - h * 0.12), sc(px - w * 0.085, py + h * 0.12)], fill=(40, 60, 80))
            d.rectangle([sc(px + w * 0.085, py - h * 0.12), sc(px + w * 0.11, py + h * 0.12)], fill=(40, 60, 80))
            ellipse(d, px, py + h * 0.13, w * 0.1, 3, SNOW)
    d.rectangle([sc(x - w * 0.07, y - h * 0.45), sc(x + w * 0.07, y)], fill=(150, 30, 40))
    ellipse(d, x + w * 0.04, y - h * 0.22, 2, 2, (230, 200, 90))
    if wreath:
        ellipse(d, x, y - h * 0.34, w * 0.06, w * 0.06, (30, 100, 50))
        ellipse(d, x, y - h * 0.34, w * 0.03, w * 0.03, (150, 30, 40))
        d.polygon(P([(x - 5, y - h * 0.34 + w * 0.05), (x + 5, y - h * 0.34 + w * 0.05), (x, y - h * 0.34 + w * 0.03)]),
                  fill=(220, 30, 40))
    # porch roof and steps
    d.polygon(P([(x - w * 0.2, y - h * 0.48), (x + w * 0.2, y - h * 0.48), (x + w * 0.16, y - h * 0.54), (x - w * 0.16, y - h * 0.54)]),
              fill=(58, 52, 60))
    for px in (-0.18, 0.18):
        d.rectangle([sc(x + px * w - 2, y - h * 0.48), sc(x + px * w + 2, y)], fill=trim)
    d.rectangle([sc(x - w * 0.12, y - 4), sc(x + w * 0.12, y + 4)], fill=(200, 200, 206))
    light_string(img, [(x - w / 2 - 14, y - h + 4), (x, y - h - roof - 2), (x + w / 2 + 14, y - h + 4)], seed=seed)
    light_string(img, [(x - w * 0.2, y - h * 0.47), (x + w * 0.2, y - h * 0.47)], spacing=12, size=2.6, seed=seed + 2)
    light_string(img, [(x - w / 2, y - h * 0.02), (x - w / 2, y - h + 6)], spacing=18, size=2.6, seed=seed + 1)
    light_string(img, [(x + w / 2, y - h * 0.02), (x + w / 2, y - h + 6)], spacing=18, size=2.6, seed=seed + 3)


def candy_cane(d, x, y, h, s=1.0):
    pts = [(x, y), (x, y - h)] + [(x + 9 * s - 9 * s * math.cos(a), y - h - 9 * s * math.sin(a))
                                  for a in [i * math.pi / 8 for i in range(9)]]
    d.line(P(pts), fill=(250, 248, 244), width=int(5 * s * K * S), joint="curve")
    for k in range(int(h / 9)):
        line(d, (x - 2.5 * s, y - k * 9 - 2), (x + 2.5 * s, y - k * 9 - 6), 2.2 * s, (210, 30, 40))
    for a in (0.3, 1.2, 2.2):
        px, py = x + 9 * s - 9 * s * math.cos(a), y - h - 9 * s * math.sin(a)
        ellipse(d, px, py, 2.2 * s, 2.2 * s, (210, 30, 40))


def lamp_post(img, x, y, h):
    d = ImageDraw.Draw(img)
    line(d, (x, y), (x, y - h), 5, (30, 34, 40))
    glow(img, x, y - h - 10, 40, (255, 210, 130), 130, 14)
    d = ImageDraw.Draw(img)
    d.polygon(P([(x - 10, y - h), (x + 10, y - h), (x + 7, y - h - 22), (x - 7, y - h - 22)]), fill=(255, 226, 150))
    d.polygon(P([(x - 12, y - h - 22), (x + 12, y - h - 22), (x, y - h - 32)]), fill=(30, 34, 40))
    # a red bow and a garland spiral on the post
    for k in range(6):
        t = k / 6
        line(d, (x - 4, y - h * (0.1 + t * 0.8)), (x + 4, y - h * (0.16 + t * 0.8)), 3, (40, 120, 60))
    ellipse(d, x - 6, y - h + 10, 5, 4, (210, 30, 40))
    ellipse(d, x + 6, y - h + 10, 5, 4, (210, 30, 40))


def main_santa():
    img = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 255))
    sky(img)
    moon(img)
    santa_sleigh(img, 360, 300, 1.15)
    snowfall(img, 260, 1.0, 2.0, 120, 0.8, 1)
    snowy_ground(img, 760, 24, 260, 1.0, (150, 168, 204), (120, 140, 182))
    d = ImageDraw.Draw(img)
    rnd = random.Random(4)
    for x in range(-20, 1960, 24):
        if rnd.random() < 0.6:
            pine(d, x + rnd.uniform(-8, 8), hill(x, 760, 24, 260, 1.0) + 8, rnd.uniform(40, 80), fill=(34, 56, 72),
                 snow=(170, 186, 216), tiers=3)
    snowy_ground(img, 860, 10, 400, 3.0, (210, 222, 242), (196, 210, 236))

    def gy(x):
        return hill(x, 860, 10, 400, 3.0) + 18
    for x, w, h, siding, seed in [(930, 200, 140, (120, 140, 170), 2), (420, 300, 190, (196, 180, 150), 0),
                                  (1460, 320, 196, (150, 70, 66), 1)]:
        us_house(img, x, gy(x), w, h, siding, seed)
    d = ImageDraw.Draw(img)
    for x, h in [(640, 160), (1200, 140), (1720, 210), (150, 240)]:
        pine(d, x, gy(x) + 10, h)

    # the lawn in front: a street of snow, a decorated tree, candy canes, lamp posts and a snowman
    snowy_ground(img, 950, 16, 360, 4.0, (230, 236, 250), (204, 216, 240))

    def fy(x):
        return hill(x, 950, 16, 360, 4.0) + 30
    christmas_tree(img, 760, fy(760) + 10, 300)
    d = ImageDraw.Draw(img)
    for dx, w, h, box, ribbon in [(-60, 46, 34, (186, 28, 38), (230, 200, 90)), (-14, 36, 44, (40, 110, 70), (230, 60, 60)),
                                  (40, 50, 30, (50, 90, 170), (240, 240, 250))]:
        present(d, 760 + dx, fy(760) + 14, w, h, box, ribbon)
    for k in range(6):
        candy_cane(d, 190 + k * 44, fy(190 + k * 44) + 8, 54, 1.0)
        candy_cane(d, 1520 + k * 44, fy(1520 + k * 44) + 8, 54, 1.0)
    lamp_post(img, 600, fy(600) + 12, 150)
    lamp_post(img, 1240, fy(1240) + 12, 150)
    snowman(img, 1080, fy(1080) + 8, 0.85)
    d = ImageDraw.Draw(img)
    for x, h in [(60, 300), (1860, 320)]:
        pine(d, x, fy(x) + 40, h, tiers=5)
    snowfall(img, 160, 2.0, 3.6, 200, 1.0, 2)
    snowfall(img, 40, 5, 8, 150, 3.0, 3)
    out = img.convert("RGB").resize((W, H), Image.LANCZOS)
    out.save(OUT, optimize=True)


if __name__ == "__main__":
    main_santa() if SANTA else main()
