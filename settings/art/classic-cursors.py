#!/usr/bin/env python3
"""Writes the classic X11 pointer as an Xcursor theme (settings/packaging/root/usr/share/icons/amadeos-classic).

X's own cursors come from the cursor font and carry no name, so a pointer change in Settings > Accessibility
could not reach the programs that are already open. As a theme every cursor is named (left_ptr, xterm, ...),
and the window manager swaps all named cursors server-wide with XFixesChangeCursorByName.

Run under an X server without a cursor theme, e.g.
    Xvfb :9 -noreset & DISPLAY=:9 XCURSOR_THEME=none python3 settings/art/classic-cursors.py
Each shape is read back with XFixesGetCursorImage and written at three nominal sizes: 24 (as X draws it),
32 (doubled) and 48 (tripled), pixel for pixel, so the classic pointer can be made larger too.
"""
import ctypes
import os
import struct
import sys
import time

NAMES = """X_cursor arrow based_arrow_down based_arrow_up boat bogosity bottom_left_corner bottom_right_corner
bottom_side bottom_tee box_spiral center_ptr circle clock coffee_mug cross cross_reverse crosshair diamond_cross
dot dotbox double_arrow draft_large draft_small draped_box exchange fleur gobbler gumby hand1 hand2 heart icon
iron_cross left_ptr left_side left_tee leftbutton ll_angle lr_angle man middlebutton mouse pencil pirate plus
question_arrow right_ptr right_side right_tee rightbutton rtl_logo sailboat sb_down_arrow sb_h_double_arrow
sb_left_arrow sb_right_arrow sb_up_arrow sb_v_double_arrow shuttle sizing spider spraycan star target tcross
top_left_arrow top_left_corner top_right_corner top_side top_tee trek ul_angle umbrella ur_angle watch
xterm""".split()

# Names newer programs ask for (CSS and freedesktop), each with the classic shape that stands in for it.
ALIASES = {
    "default": "left_ptr", "pointer": "hand2", "text": "xterm", "wait": "watch", "progress": "watch",
    "help": "question_arrow", "crosshair": "crosshair", "move": "fleur", "all-scroll": "fleur",
    "grab": "hand1", "grabbing": "fleur", "not-allowed": "X_cursor", "no-drop": "X_cursor",
    "col-resize": "sb_h_double_arrow", "row-resize": "sb_v_double_arrow",
    "ew-resize": "sb_h_double_arrow", "ns-resize": "sb_v_double_arrow",
    "n-resize": "top_side", "s-resize": "bottom_side", "e-resize": "right_side", "w-resize": "left_side",
    "ne-resize": "top_right_corner", "nw-resize": "top_left_corner",
    "se-resize": "bottom_right_corner", "sw-resize": "bottom_left_corner",
    "context-menu": "left_ptr", "cell": "plus", "copy": "left_ptr", "alias": "left_ptr",
    "vertical-text": "xterm", "zoom-in": "left_ptr", "zoom-out": "left_ptr",
}

SIZES = ((24, 1), (32, 2), (48, 3))


class CursorImage(ctypes.Structure):
    _fields_ = [("x", ctypes.c_short), ("y", ctypes.c_short), ("width", ctypes.c_ushort),
                ("height", ctypes.c_ushort), ("xhot", ctypes.c_ushort), ("yhot", ctypes.c_ushort),
                ("serial", ctypes.c_ulong), ("pixels", ctypes.POINTER(ctypes.c_ulong)),
                ("atom", ctypes.c_ulong), ("name", ctypes.c_char_p)]


def capture():
    x11 = ctypes.cdll.LoadLibrary("libX11.so.6")
    xfixes = ctypes.cdll.LoadLibrary("libXfixes.so.3")
    x11.XOpenDisplay.restype = ctypes.c_void_p
    x11.XDefaultRootWindow.restype = ctypes.c_ulong
    x11.XDefaultRootWindow.argtypes = [ctypes.c_void_p]
    x11.XCreateFontCursor.restype = ctypes.c_ulong
    x11.XCreateFontCursor.argtypes = [ctypes.c_void_p, ctypes.c_uint]
    x11.XDefineCursor.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.c_ulong]

    x11.XSync.argtypes = [ctypes.c_void_p, ctypes.c_int]
    xfixes.XFixesGetCursorImage.restype = ctypes.POINTER(CursorImage)
    xfixes.XFixesGetCursorImage.argtypes = [ctypes.c_void_p]
    display = x11.XOpenDisplay(None)
    if not display:
        sys.exit("no X display")
    root = x11.XDefaultRootWindow(display)
    images = {}
    for index, name in enumerate(NAMES):
        cursor = x11.XCreateFontCursor(display, index * 2)
        x11.XDefineCursor(display, root, cursor)
        x11.XSync(display, 0)
        time.sleep(0.05)
        img = xfixes.XFixesGetCursorImage(display).contents
        if img.name:
            sys.exit("the X server has a cursor theme; run without one (XCURSOR_THEME=none)")
        pixels = [img.pixels[i] & 0xFFFFFFFF for i in range(img.width * img.height)]
        images[name] = (img.width, img.height, img.xhot, img.yhot, pixels)
    return images


def scaled(image, factor):
    width, height, xhot, yhot, pixels = image
    out = []
    for y in range(height * factor):
        row = pixels[(y // factor) * width:(y // factor + 1) * width]
        for x in range(width * factor):
            out.append(row[x // factor])
    return width * factor, height * factor, xhot * factor + factor // 2, yhot * factor + factor // 2, out


def xcursor_file(image):
    chunks = []
    for nominal, factor in SIZES:
        width, height, xhot, yhot, pixels = scaled(image, factor)
        chunks.append((nominal, struct.pack("<9I", 36, 0xFFFD0002, nominal, 1, width, height,
                                            min(xhot, width - 1), min(yhot, height - 1), 0)
                       + struct.pack("<%dI" % len(pixels), *pixels)))
    header = struct.pack("<4sIII", b"Xcur", 16, 0x10000, len(chunks))
    position = 16 + 12 * len(chunks)
    toc = b""
    for nominal, data in chunks:
        toc += struct.pack("<III", 0xFFFD0002, nominal, position)
        position += len(data)
    return header + toc + b"".join(data for _, data in chunks)


def main():
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                       "../packaging/root/usr/share/icons/amadeos-classic/cursors")
    os.makedirs(out, exist_ok=True)
    for name, image in capture().items():
        with open(os.path.join(out, name), "wb") as f:
            f.write(xcursor_file(image))
    for alias, target in ALIASES.items():
        path = os.path.join(out, alias)
        if alias in NAMES:
            continue
        if os.path.lexists(path):
            os.remove(path)
        os.symlink(target, path)
    print("wrote", len(NAMES), "cursors and", len(ALIASES), "aliases to", os.path.normpath(out))


if __name__ == "__main__":
    main()
