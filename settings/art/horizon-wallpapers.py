#!/usr/bin/env python3
"""Draws the ten extra wallpapers of amadeOS's Horizon look. Flat layers with soft gradients, the Horizon
colours (night blue, teal, violet) and one bright horizon in each picture. Everything is computed here
(original art, no source pictures, no fonts).

    python3 settings/art/horizon-wallpapers.py OUTDIR [width height] [--only NAME] [--png]

Writes OUTDIR/horizon-<name>.jpg for every picture (or PNG with --png). The default size is 2560x1440;
the pictures are resolution independent, so 1920x1080, 2560x1600 or 3840x2160 come out just as sharp.
Needs Pillow and NumPy. The shipped files are settings/packaging/root/usr/share/backgrounds/amadeos/.
"""
import os
import sys

import numpy as np
from PIL import Image

args = [a for a in sys.argv[1:] if not a.startswith("--")]
OUT = args[0] if args else "."
W, H = (int(args[1]), int(args[2])) if len(args) > 2 else (2560, 1440)
ONLY = sys.argv[sys.argv.index("--only") + 1] if "--only" in sys.argv else None
PNG = "--png" in sys.argv

y, x = np.mgrid[0:H, 0:W].astype(np.float32)
U = x / W          # 0 left .. 1 right
V = y / H          # 0 top .. 1 bottom
PX = 1.0 / H       # one pixel in V units: edges are anti-aliased over one pixel
ASPECT = W / H


def rgb(hexcode):
    hexcode = hexcode.lstrip("#")
    return np.array([int(hexcode[i:i + 2], 16) for i in (0, 2, 4)], np.float32)


def mix(a, b, t):
    t = np.asarray(t, np.float32)
    if t.ndim:
        t = t[..., None]
    return a + (b - a) * t


def vgrad(*stops):
    """A vertical gradient through (position, colour) stops."""
    img = np.zeros((H, W, 3), np.float32)
    pos = [p for p, _ in stops]
    for ch in range(3):
        img[..., ch] = np.interp(V, pos, [c[ch] for _, c in stops])
    return img


def ridge(seed, base, amps, freqs):
    """A ridge line in V units over U: a sum of sines with fixed random phases (always the same picture)."""
    rng = np.random.default_rng(seed)
    h = np.full(W, base, np.float32)
    u = np.arange(W, dtype=np.float32) / W
    for a, f in zip(amps, freqs):
        h += a * np.sin(u * f * np.pi * 2 + rng.uniform(0, 6.3))
    return h


def peaks(seed, base, height, count, sharp=1.0):
    """A mountain ridge: overlapping triangular peaks, softened a little, plus small detail."""
    rng = np.random.default_rng(seed)
    u = np.arange(W, dtype=np.float32) / W
    h = np.full(W, base, np.float32)
    for _ in range(count):
        c, wdt, ht = rng.uniform(-0.1, 1.1), rng.uniform(0.08, 0.22), rng.uniform(0.35, 1.0) * height
        tri = np.clip(1 - np.abs(u - c) / wdt, 0, 1) ** sharp
        h = np.minimum(h, base - ht * tri)
    h += 0.004 * np.sin(u * 90 + seed) + 0.002 * np.sin(u * 230 + 2 * seed)
    return h


def fill_below(img, line, colour, top_colour=None, depth=0.25):
    """Fills everything below the line (V units per column); optionally a gradient from top_colour down."""
    cover = np.clip((V - line[None, :]) / PX + 0.5, 0, 1)
    if top_colour is not None:
        t = np.clip((V - line[None, :]) / depth, 0, 1)
        colour = mix(top_colour, colour, t)
    return mix(img, colour, cover) if np.ndim(colour) == 1 else img + (colour - img) * cover[..., None]


def disc(img, cx, cy, r, colour, alpha=1.0):
    """A filled circle; cx in U, cy and r in V units."""
    d = np.sqrt(((U - cx) * ASPECT) ** 2 + (V - cy) ** 2) - r
    cover = np.clip(0.5 - d / PX, 0, 1) * alpha
    return mix(img, colour, cover)


def glow(img, cx, cy, r, colour, strength=1.0):
    """A soft additive glow around a point."""
    d2 = ((U - cx) * ASPECT) ** 2 + (V - cy) ** 2
    g = np.exp(-d2 / (r * r)) * strength
    return img + (colour[None, None, :] * g[..., None])


def stars(img, seed, count, top=0.0, bottom=0.6, colour=rgb("#ffffff"), size=1.0):
    rng = np.random.default_rng(seed)
    out = img.copy()
    for _ in range(count):
        sx, sy = rng.uniform(0, 1), rng.uniform(top, bottom)
        r = (rng.uniform(0.4, 1.3) ** 2) * size * PX
        a = rng.uniform(0.35, 1.0) * (1 - (sy - top) / (bottom - top + 1e-6)) ** 0.7
        x0, y0 = int(sx * W), int(sy * H)
        rad = int(max(3, r * H * 4))
        ys, xs = slice(max(0, y0 - rad), min(H, y0 + rad)), slice(max(0, x0 - rad), min(W, x0 + rad))
        d = np.sqrt(((U[ys, xs] - sx) * ASPECT) ** 2 + (V[ys, xs] - sy) ** 2)
        cover = np.clip(1.2 - d / max(r, 0.6 * PX), 0, 1) * a
        out[ys, xs] = mix(out[ys, xs], colour, cover)
    return out


def finish(img, name, grain=1.1):
    rng = np.random.default_rng(len(name))
    img = img + rng.normal(0, grain, img.shape).astype(np.float32)  # against banding in the gradients
    out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")
    path = os.path.join(OUT, f"horizon-{name}." + ("png" if PNG else "jpg"))
    if PNG:
        out.save(path, optimize=True)
    else:
        out.save(path, quality=92, subsampling=0, optimize=True, progressive=True)
    print(path)


NIGHT = rgb("#0c0e1e")
DEEP = rgb("#1e1a3a")
TEAL = rgb("#2fb8b0")
VIOLET = rgb("#8a6ee6")
WHITE = rgb("#ffffff")


# 1. Dunes: layered sand dunes at dusk, a low sun on the horizon
def dunes():
    img = vgrad((0, rgb("#1b1840")), (0.45, rgb("#6b4f9c")), (0.62, rgb("#f0a37a")), (1, rgb("#f6c79a")))
    img = glow(img, 0.62, 0.60, 0.18, rgb("#ffd8a0"), 0.55)
    img = disc(img, 0.62, 0.585, 0.055, rgb("#fff1d6"))
    layers = [("#c97a6a", "#a8606a", 0.61, 11), ("#a25a70", "#7e4468", 0.67, 12),
              ("#7a3f68", "#5a2e5c", 0.74, 13), ("#53295a", "#3a1f4a", 0.83, 14), ("#33193f", "#22112e", 0.92, 15)]
    for top, bottom, base, seed in layers:
        line = ridge(seed, base, (0.035, 0.015, 0.006), (0.6, 1.4, 3.1))
        img = fill_below(img, line, rgb(bottom), rgb(top), 0.12)
    return img


# 2. Peaks: mountain ranges in morning mist, light scheme
def peaks_morning():
    img = vgrad((0, rgb("#cfd8f2")), (0.55, rgb("#eef1fb")), (1, rgb("#d6dcf2")))
    img = glow(img, 0.30, 0.42, 0.22, rgb("#ffffff"), 0.35)
    img = disc(img, 0.30, 0.36, 0.045, rgb("#ffe6bf"), 0.95)
    ranges = [("#b8c0e8", 0.62, 0.30, 21), ("#9aa6e0", 0.70, 0.26, 22), ("#7d86d2", 0.78, 0.22, 23),
              ("#5f62b8", 0.88, 0.20, 24), ("#41418f", 1.00, 0.18, 25)]
    for colour, base, height, seed in ranges:
        line = peaks(seed, base, height, 9, 1.15)
        mist = mix(rgb(colour), rgb("#eef1fb"), 0.35)
        img = fill_below(img, line, rgb(colour), mist, 0.10)
    return img


# 3. Lake: dark mountains mirrored in a still lake, teal light on the water line
def lake():
    img = vgrad((0, NIGHT), (0.50, rgb("#24326a")), (0.58, rgb("#3c8f9a")))
    img = stars(img, 31, 260, 0, 0.45)
    shore = 0.58
    line = peaks(32, shore, 0.20, 10, 1.1)
    hills = peaks(33, shore, 0.11, 12, 1.0)
    sky = img.copy()
    img = fill_below(img, line, rgb("#141a3c"), rgb("#1c2550"), 0.2)
    img = fill_below(img, hills, rgb("#0d1230"), rgb("#121a40"), 0.1)
    # mirror the upper half into the water, darker and slightly rippled
    rows = np.arange(H)
    water = rows >= int(shore * H)
    src = np.clip((2 * shore * H - rows).astype(int), 0, H - 1)
    ripple = (np.sin(rows * 0.9) * 0.0 + np.sin(rows * 0.35) * 2.0).astype(int)
    mirrored = np.empty_like(img)
    for r in np.nonzero(water)[0]:
        mirrored[r] = np.roll(img[src[r]], ripple[r], axis=0)
    t = np.clip((V - shore) / (1 - shore), 0, 1)[..., None]
    reflection = mirrored * (0.62 - 0.25 * t) + NIGHT * (0.38 + 0.25 * t)
    img[water] = reflection[water]
    # the bright water line and a few streaks of light
    img = img + (TEAL * np.exp(-((V - shore) / 0.004) ** 2)[..., None] * 0.9)
    for k in range(7):
        sy = shore + 0.03 + k * 0.05
        streak = np.exp(-((V - sy) / 0.0016) ** 2) * np.exp(-((U - 0.5 - 0.07 * np.sin(k * 2.3)) / (0.30 - 0.03 * k)) ** 2)
        img = img + TEAL[None, None, :] * (streak * (0.35 - 0.04 * k))[..., None]
    del sky
    return img


# 4. Aurora: curtains of teal and violet light over snowy hills
def aurora():
    img = vgrad((0, rgb("#070a1a")), (0.6, rgb("#141c3c")), (1, rgb("#1d2a52")))
    img = stars(img, 41, 420, 0, 0.75)
    for k, (colour, base, amp, freq, width, strength) in enumerate([
            (TEAL, 0.30, 0.08, 0.9, 0.16, 0.85), (rgb("#5fe0b8"), 0.40, 0.06, 1.3, 0.12, 0.6),
            (VIOLET, 0.22, 0.07, 0.7, 0.14, 0.55)]):
        curve = base + amp * np.sin(U * np.pi * 2 * freq + k * 1.7) + 0.02 * np.sin(U * 17 + k)
        above = curve - V  # curtains hang down from the curve, with light rising above it
        body = np.where(above > 0, np.exp(-(above / width) ** 2), np.exp(-(above / 0.012) ** 2))
        rays = 0.55 + 0.45 * np.sin(U * 260 + 3 * np.sin(U * 13 + k)) ** 2
        fade = np.clip(np.sin(U * np.pi * (0.9 + 0.2 * k) + k * 0.4), 0, 1) ** 0.8
        img = img + colour[None, None, :] * (body * rays * fade * strength)[..., None] * 0.55
    snow = [("#cfd9f0", "#8e9cc8", 0.76, 42), ("#aab8e0", "#6f7fb2", 0.84, 43), ("#8796c8", "#4d5a8e", 0.93, 44)]
    for top, bottom, base, seed in snow:
        img = fill_below(img, ridge(seed, base, (0.03, 0.012), (0.7, 1.9)), rgb(bottom), rgb(top), 0.14)
    return img


# 5. Waves: bands of sea in teal and blue under a pale sky, light scheme
def waves():
    img = vgrad((0, rgb("#dfe8f6")), (0.45, rgb("#f4f6fc")), (1, rgb("#e6edf8")))
    img = disc(img, 0.78, 0.22, 0.07, rgb("#ffe2b8"), 0.9)
    colours = ["#a6e3dc", "#72cfc8", "#3fb7b1", "#2a93a8", "#2c6f9e", "#33508c", "#2b3570"]
    for k, colour in enumerate(colours):
        base = 0.42 + k * 0.085
        line = base + 0.025 * np.sin(np.arange(W) / W * np.pi * 2 * (1.2 + 0.25 * k) + k * 1.3) \
            + 0.010 * np.sin(np.arange(W) / W * np.pi * 2 * (3.5 + k) + k)
        foam = mix(rgb(colour), WHITE, 0.45)
        img = fill_below(img, line.astype(np.float32), rgb(colour), foam, 0.03 + 0.004 * k)
    return img


# 6. Forest: rows of pines in teal mist, a pale moon
def forest():
    img = vgrad((0, rgb("#0d1a2a")), (0.55, rgb("#1d4a55")), (0.75, rgb("#3c8a86")), (1, rgb("#2a5f63")))
    img = glow(img, 0.72, 0.28, 0.16, rgb("#9fe8de"), 0.30)
    img = disc(img, 0.72, 0.28, 0.05, rgb("#e8fbf6"))
    u = np.arange(W, dtype=np.float32) / W
    rows = [("#2d6a6c", 0.64, 0.10, 61, 0.045), ("#22545a", 0.72, 0.15, 62, 0.060),
            ("#183f48", 0.82, 0.21, 63, 0.080), ("#0f2b36", 0.95, 0.30, 64, 0.110)]
    for colour, base, height, seed, spacing in rows:
        rng = np.random.default_rng(seed)
        ground = ridge(seed, base, (0.025, 0.008), (0.7, 2.1))
        line = ground.copy()
        c = rng.uniform(0, spacing)
        while c < 1 + spacing:
            if rng.uniform() < 0.8:
                foot = np.interp(c, u, ground)
                ht = height * rng.uniform(0.5, 1.0)
                half = 0.30 * ht / ASPECT
                dx = np.abs(u - c)
                # a pine: three stacked triangles of branches, narrower towards the top
                for tier in range(3):
                    bottom = foot - ht * (0.12 + tier * 0.27)
                    top = bottom - ht * 0.42
                    hw = half * (1 - tier * 0.26)
                    tri = np.where(dx < hw, top + (bottom - top) * dx / hw, 9)
                    line = np.minimum(line, tri)
                trunk = np.where(dx < half * 0.08, foot - ht * 0.15, 9)
                line = np.minimum(line, trunk)
            c += spacing * rng.uniform(0.6, 1.3)
        mist = mix(rgb(colour), rgb("#7cc4bc"), 0.30)
        img = fill_below(img, line.astype(np.float32), rgb(colour), mist, 0.30)
    return img


# 7. Bloom: large flowing shapes in teal, violet and coral, light scheme (abstract)
def bloom():
    img = vgrad((0, rgb("#f2f0fb")), (1, rgb("#e4e8f7")))
    shapes = [(0.18, 0.80, 0.55, "#b7a7f0", 0.92), (0.88, 0.15, 0.45, "#9ee0d8", 0.92),
              (0.70, 0.95, 0.42, "#f6b9a8", 0.85), (0.35, 0.10, 0.30, "#cfc4f6", 0.85),
              (0.55, 0.55, 0.20, "#ffffff", 0.55)]
    for cx, cy, r, colour, alpha in shapes:
        ang = np.arctan2(V - cy, (U - cx) * ASPECT)
        wobble = 1 + 0.06 * np.sin(3 * ang + cx * 9) + 0.03 * np.sin(5 * ang + cy * 7)
        d = np.sqrt(((U - cx) * ASPECT) ** 2 + (V - cy) ** 2) - r * wobble
        cover = np.clip(0.5 - d / PX, 0, 1) * alpha
        shade = mix(rgb(colour), mix(rgb(colour), WHITE, 0.35), np.clip(1 - (V - cy + r) / (2 * r), 0, 1))
        img = img + (shade - img) * cover[..., None]
    # a thin horizon of light across the middle, as in the default Horizon picture
    curve = 0.56 - 0.10 * U + 0.03 * np.sin(U * np.pi * 1.5)
    img = img + (np.full(3, 255.0) - img) * (np.exp(-((V - curve) / 0.0025) ** 2) * 0.8)[..., None]
    return img


# 8. Orbit: a ringed planet rising over a moon's horizon, deep space
def orbit():
    img = vgrad((0, rgb("#05060f")), (0.7, rgb("#141432")), (1, rgb("#1c1640")))
    img = stars(img, 81, 600, 0, 0.95)
    img = glow(img, 0.36, 0.42, 0.30, VIOLET, 0.22)
    cx, cy, r = 0.36, 0.44, 0.20
    # ring behind the planet
    ry = (V - cy) * np.cos(-0.25) - (U - cx) * ASPECT * np.sin(-0.25)
    rx = (U - cx) * ASPECT * np.cos(-0.25) + (V - cy) * np.sin(-0.25)
    ring = np.sqrt(rx ** 2 + (ry / 0.22) ** 2)
    band = ((ring > 0.29) & (ring < 0.40)).astype(np.float32) * (0.55 + 0.45 * np.sin(ring * 90) ** 2)
    behind = ry < 0
    img = mix(img, rgb("#c9b8ff"), band * behind * 0.55)
    d = np.sqrt(((U - cx) * ASPECT) ** 2 + (V - cy) ** 2)
    planet = np.clip(0.5 - (d - r) / PX, 0, 1)
    light = np.clip(0.5 + ((cx - U) * ASPECT * 0.6 + (cy - V) * 0.8) / r, 0, 1)
    stripes = 0.5 + 0.5 * np.sin((V - cy) / r * 9 + np.sin((U - cx) * 14))
    body = mix(mix(rgb("#2b2160"), rgb("#7a62d6"), light), mix(rgb("#3a8e9a"), rgb("#8ee6dc"), light), stripes * 0.45)
    img = mix(img, body, planet)
    img = mix(img, rgb("#d9ccff"), band * (~behind) * 0.75)
    # the moon's surface in the foreground, with the bright rim of the horizon
    line = ridge(82, 0.84, (0.018, 0.006), (0.5, 2.2)) - 0.10 * np.exp(-((np.arange(W) / W - 0.78) / 0.12) ** 2)
    img = img + TEAL[None, None, :] * np.exp(-((V - line[None, :]) / 0.010) ** 2)[..., None] * 0.6
    img = fill_below(img, line.astype(np.float32), rgb("#0b0b1d"), rgb("#2a2f58"), 0.06)
    return img


# 9. City: a skyline at night on a teal horizon, lit windows
def city():
    img = vgrad((0, rgb("#0a0b1e")), (0.55, rgb("#2a2358")), (0.72, rgb("#5a3f8e")), (0.76, rgb("#2fb8b0")))
    img = stars(img, 91, 160, 0, 0.45)
    img = disc(img, 0.20, 0.20, 0.035, rgb("#f1ecff"))
    u = np.arange(W)
    for layer, (colour, base, hmin, hmax, wmin, wmax, seed, lit) in enumerate([
            ("#2c2560", 0.76, 0.06, 0.20, 0.018, 0.040, 92, 0.0),
            ("#1b1840", 0.80, 0.08, 0.32, 0.020, 0.050, 93, 0.25),
            ("#0e0d24", 0.86, 0.10, 0.40, 0.025, 0.060, 94, 0.35)]):
        rng = np.random.default_rng(seed)
        line = np.full(W, base, np.float32)
        boxes = []
        c = 0.0
        while c < 1:
            bw = rng.uniform(wmin, wmax)
            ht = rng.uniform(hmin, hmax) * (0.6 + 0.4 * np.sin(c * np.pi) ** 0.5)
            a, b = int(c * W), int(min(1, c + bw) * W)
            line[a:b] = np.minimum(line[a:b], base - ht)
            if rng.uniform() < 0.25:  # a spire on some towers
                m = (a + b) // 2
                line[m - 2:m + 2] = np.minimum(line[m - 2:m + 2], base - ht - 0.03)
            boxes.append((a, b, base - ht))
            c += bw * rng.uniform(0.85, 1.05)
        img = fill_below(img, line, rgb(colour), mix(rgb(colour), rgb("#3a3270"), 0.5), 0.08)
        if lit:
            win = np.zeros((H, W), np.float32)
            for a, b, topv in boxes:
                for wy in np.arange(topv + 0.02, 1.0, 0.022):
                    for wx in range(a + 6, b - 6, max(10, (b - a) // 6)):
                        if rng.uniform() < lit:
                            y0, x0 = int(wy * H), wx
                            win[y0:y0 + max(2, H // 300), x0:x0 + max(2, W // 640)] = rng.uniform(0.5, 1)
            warm = mix(rgb("#ffd59a"), TEAL, (np.arange(W) / W)[None, :] * 0.0)
            img = mix(img, warm, win)
    # the water in front, with the teal horizon light on its edge
    img = img + TEAL[None, None, :] * np.exp(-((V - 0.93) / 0.006) ** 2)[..., None] * 0.8
    img = fill_below(img, np.full(W, 0.93, np.float32), rgb("#0a0a1c"), rgb("#1f5a66"), 0.07)
    return img


# 10. Fields: rolling hills under a warm sun, pastel light scheme
def fields():
    img = vgrad((0, rgb("#c9d6f4")), (0.5, rgb("#f3eefa")), (1, rgb("#f8e9dc")))
    img = glow(img, 0.50, 0.50, 0.25, rgb("#fff0d0"), 0.35)
    img = disc(img, 0.50, 0.47, 0.07, rgb("#ffe4b0"))
    hills = [("#bfe3d6", "#a6d5c6", 0.55, 101), ("#92cdbd", "#78bba9", 0.62, 102),
             ("#62b2a4", "#4d9a90", 0.71, 103), ("#3f8f8a", "#2f7676", 0.81, 104), ("#2f6a72", "#244f5c", 0.92, 105)]
    for top, bottom, base, seed in hills:
        line = ridge(seed, base, (0.04, 0.012), (0.55, 1.6))
        img = fill_below(img, line, rgb(bottom), rgb(top), 0.10)
        # rows of the field follow the hill line
        rows = np.sin((V - line[None, :]) * 260) ** 8 * np.clip((V - line[None, :]) / 0.04, 0, 1)
        below = V > line[None, :]
        img = mix(img, rgb(bottom) * 0.9, rows * below * 0.25)
    return img


PICTURES = {"dunes": dunes, "peaks": peaks_morning, "lake": lake, "aurora": aurora, "waves": waves,
            "forest": forest, "bloom": bloom, "orbit": orbit, "city": city, "fields": fields}

os.makedirs(OUT, exist_ok=True)
for name, draw in PICTURES.items():
    if ONLY is None or ONLY == name:
        finish(draw(), name)
