#!/usr/bin/env python3
"""Draws the backdrops of amadeOS's Horizon look: a calm sky over a glowing horizon line, with thin bands
of light that sweep in from the left and fade out towards the right. Everything is computed here (original
art, no source pictures).

    python3 settings/art/horizon-backdrop.py dark horizon-dark.png [width height]
    python3 settings/art/horizon-backdrop.py light horizon-light.png [width height]

Needs Pillow and NumPy. The results are settings/packaging/root/usr/share/backgrounds/amadeos/horizon-dark.png
and horizon-light.png; Settings > Desktop look puts the one of the chosen scheme on the desktop.
"""
import sys

import numpy as np
from PIL import Image

SCHEME = sys.argv[1] if len(sys.argv) > 1 else "dark"
OUT = sys.argv[2] if len(sys.argv) > 2 else f"horizon-{SCHEME}.png"
W, H = (int(sys.argv[3]), int(sys.argv[4])) if len(sys.argv) > 4 else (1920, 1080)

TEAL = np.array([47, 184, 176], float)
VIOLET = np.array([138, 110, 230], float)

if SCHEME == "dark":
    TOP, BOTTOM = np.array([12, 14, 30], float), np.array([30, 26, 58], float)
    GLOW, LINES, SUN = 0.55, 0.85, 0.45
else:
    TOP, BOTTOM = np.array([222, 228, 244], float), np.array([168, 178, 222], float)
    GLOW, LINES, SUN = 0.62, 0.95, 0.30

y, x = np.mgrid[0:H, 0:W].astype(float)
u = x / W  # 0 left .. 1 right
v = y / H  # 0 top .. 1 bottom


def horizon(u):
    """The horizon line: low on the left, rising gently to the right, with one long swell."""
    return 0.70 - 0.22 * u + 0.05 * np.sin(u * np.pi * 1.3 + 0.4)


img = TOP + (BOTTOM - TOP) * v[..., None]

# colour along the horizon: teal on the left, violet on the right
hue = TEAL + (VIOLET - TEAL) * np.clip(u * 1.1 - 0.05, 0, 1)[..., None]


def add(intensity, colour, amount):
    global img
    a = np.clip(intensity * amount, 0, 1)[..., None]
    if SCHEME == "dark":
        img = img + (colour - img * 0.0) * a * 0.9
    else:
        img = img * (1 - a) + colour * a


d = v - horizon(u)
# the broad glow: soft above the line, a little deeper below it
glow = np.exp(-(d / np.where(d < 0, 0.10, 0.16)) ** 2)
add(glow, hue, GLOW)

# a sun-like brightening where the horizon swells, right of the middle
sun = np.exp(-(((u - 0.68) / 0.20) ** 2 + ((d + 0.02) / 0.12) ** 2))
add(sun, hue * 0.6 + 100, SUN)

# thin bands of light: offsets from the horizon, sweeping in from the left and fading to the right
for k, (offset, width, strength) in enumerate([(0.0, 0.0022, 1.0), (-0.035, 0.0016, 0.75), (-0.075, 0.0013, 0.55),
                                              (0.040, 0.0016, 0.6), (0.090, 0.0013, 0.4), (-0.13, 0.0011, 0.35)]):
    curve = horizon(u) + offset * (1 + 0.6 * np.sin(u * np.pi * (1.1 + 0.15 * k) + k))
    line = np.exp(-(((v - curve) / width) ** 2))
    fade = np.clip(1.25 - u * (0.9 + 0.1 * k), 0, 1) ** 1.3
    halo = np.exp(-(((v - curve) / (width * 9)) ** 2)) * 0.25
    # bright lines on the dark sky; on the light one white lines over the coloured glow
    colour = np.minimum(hue + 70, 255) if SCHEME == "dark" else np.full(3, 255.0)
    add((line + halo) * fade * strength, colour, LINES)

# a faint grain against banding in the gradients
rng = np.random.default_rng(7)
img = img + rng.normal(0, 1.2, img.shape)
Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB").save(OUT, optimize=True)
print(OUT)
