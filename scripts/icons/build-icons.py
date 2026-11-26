import argparse
import concurrent.futures
import os
import re
import shutil
import struct
import subprocess
import sys
import xml.etree.ElementTree as ET
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MASTERS = ROOT / "design" / "icon"
WORK = ROOT / "build" / "icon-work"
EDGE = os.environ.get("FETCHJET_EDGE", r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe")
FG_SCALE = 66.0 / 108.0
SVG_NS = "{http://www.w3.org/2000/svg}"


def master(name):
    return (MASTERS / name).as_uri()


def png_chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)


def png_encode(width, height, pixels, channels):
    stride = width * channels
    raw = b"".join(b"\0" + bytes(pixels[y * stride:(y + 1) * stride]) for y in range(height))
    color = {4: 6, 3: 2}[channels]
    return (b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, color, 0, 0, 0))
            + png_chunk(b"IDAT", zlib.compress(raw, 9)) + png_chunk(b"IEND", b""))


def png_decode(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")
    pos, idat = 8, b""
    width = height = color = 0
    while pos < len(data):
        length, kind = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            width, height, depth, color, _, _, interlace = struct.unpack(">IIBBBBB", body)
            if depth != 8 or interlace:
                raise ValueError("unsupported PNG")
        elif kind == b"IDAT":
            idat += body
    channels = {6: 4, 2: 3}[color]
    raw = zlib.decompress(idat)
    stride = width * channels
    out = bytearray()
    prev = bytearray(stride)
    i = 0
    for _ in range(height):
        kind = raw[i]
        line = bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        if kind == 2:
            line = bytearray((a + b) & 255 for a, b in zip(line, prev))
        elif kind:
            for x in range(stride):
                left = line[x - channels] if x >= channels else 0
                up = prev[x]
                corner = prev[x - channels] if x >= channels else 0
                if kind == 1:
                    line[x] = (line[x] + left) & 255
                elif kind == 3:
                    line[x] = (line[x] + (left + up) // 2) & 255
                else:
                    p = left + up - corner
                    pa, pb, pc = abs(p - left), abs(p - up), abs(p - corner)
                    line[x] = (line[x] + (left if pa <= pb and pa <= pc else up if pb <= pc else corner)) & 255
        out += line
        prev = line
    return width, height, channels, out


def png_size(path):
    data = Path(path).read_bytes()
    return struct.unpack(">II", data[16:24])


def bmp_read(path):
    data = Path(path).read_bytes()
    offset = struct.unpack("<I", data[10:14])[0]
    width, height = struct.unpack("<ii", data[18:26])
    bits = struct.unpack("<H", data[28:30])[0]
    if bits != 24:
        raise ValueError("only 24-bit BMP")
    stride = (width * 3 + 3) & ~3
    pixels = bytearray(width * height * 3)
    for row in range(abs(height)):
        y = abs(height) - 1 - row if height > 0 else row
        src = data[offset + row * stride:offset + row * stride + width * 3]
        for x in range(width):
            b, g, r = src[x * 3:x * 3 + 3]
            pixels[(y * width + x) * 3:(y * width + x) * 3 + 3] = bytes((r, g, b))
    return width, abs(height), pixels


def bmp_write(path, width, height, pixels):
    stride = (width * 3 + 3) & ~3
    body = bytearray()
    for row in range(height):
        y = height - 1 - row
        line = bytearray()
        for x in range(width):
            r, g, b = pixels[(y * width + x) * 3:(y * width + x) * 3 + 3]
            line += bytes((b, g, r))
        body += line + b"\0" * (stride - width * 3)
    header = b"BM" + struct.pack("<IHHI", 54 + len(body), 0, 0, 54)
    info = struct.pack("<IiiHHIIiiII", 40, width, height, 1, 24, 0, len(body), 2835, 2835, 0, 0)
    Path(path).write_bytes(header + info + body)


def ico_write(path, pngs):
    entries, blobs = b"", b""
    offset = 6 + 16 * len(pngs)
    for size, data in pngs:
        entries += struct.pack("<BBBBHHII", 0 if size >= 256 else size, 0 if size >= 256 else size, 0, 0, 1, 32, len(data), offset)
        blobs += data
        offset += len(data)
    Path(path).write_bytes(struct.pack("<HHH", 0, 1, len(pngs)) + entries + blobs)


def icns_write(path, pngs):
    chunks = [(kind, data) for kind, data in pngs]
    toc = b"TOC " + struct.pack(">I", 8 + 8 * len(chunks)) + b"".join(
        kind.encode() + struct.pack(">I", 8 + len(data)) for kind, data in chunks)
    body = toc + b"".join(kind.encode() + struct.pack(">I", 8 + len(data)) + data for kind, data in chunks)
    Path(path).write_bytes(b"icns" + struct.pack(">I", 8 + len(body)) + body)


def num(value):
    text = "%.3f" % value
    text = text.rstrip("0").rstrip(".")
    return "0" if text in ("-0", "") else text


def img(src, x, y, w, h, style=""):
    return ('<img src="%s" style="position:absolute;left:%spx;top:%spx;width:%spx;height:%spx;%s">'
            % (src, num(x), num(y), num(w), num(h), style))


def layer_icon(size, x=0, y=0):
    return img(master("fetchjet-icon.svg"), x, y, size, size)


def layer_art(canvas_w, canvas_h, art_size, style=""):
    full = art_size / FG_SCALE
    return img(master("fetchjet-foreground.svg"), (canvas_w - full) / 2, (canvas_h - full) / 2, full, full, style)


def page_html(width, height, layers, background="transparent"):
    return ('<!doctype html><html><head><meta charset="utf-8"><style>html,body{margin:0;padding:0;background:%s;overflow:hidden}'
            '#c{position:relative;width:%dpx;height:%dpx;overflow:hidden}</style></head><body><div id="c">%s</div></body></html>'
            % (background, width, height, "".join(layers)))


def comp_full(size):
    return page_html(size, size, [layer_icon(size)])


def comp_opaque(size):
    return page_html(size, size, [img(master("fetchjet-background.svg"), 0, 0, size, size), layer_art(size, size, size)])


def comp_round(size):
    return page_html(size, size, [
        img(master("fetchjet-background.svg"), 0, 0, size, size, "border-radius:50%"),
        layer_art(size, size, size * 0.85)])


def comp_art(size, style=""):
    return page_html(size, size, [layer_art(size, size, size, style)])


def comp_tray(size):
    return page_html(size, size, [img(master("fetchjet-tray.svg"), 0, 0, size, size)])


def comp_mac(size):
    body = size * 824 / 1024
    off = size * 100 / 1024
    shadow = "filter:drop-shadow(0 %spx %spx rgba(0,0,0,0.3))" % (num(size * 10 / 1024), num(size * 10 / 1024))
    return page_html(size, size, [img(master("fetchjet-icon.svg"), off, off, body, body, shadow)])


def comp_placed(width, height, x, y, size):
    return page_html(width, height, [layer_icon(size, x, y)])


def comp_patch(width, height, old_png, box):
    x, y, size = box
    pad = 3
    script = """
<script>
const img = new Image();
img.onload = () => {
  const c = document.getElementById('cv');
  const ctx = c.getContext('2d');
  ctx.drawImage(img, 0, 0);
  const W = %d, H = %d;
  const x0 = Math.max(0, %d - %d), y0 = Math.max(0, %d - %d);
  const x1 = Math.min(W - 1, %d + %d + %d - 1), y1 = Math.min(H - 1, %d + %d + %d - 1);
  const d = ctx.getImageData(0, 0, W, H);
  const p = d.data;
  const at = (xx, yy) => { xx = Math.min(W - 1, Math.max(0, xx)); yy = Math.min(H - 1, Math.max(0, yy)); return (yy * W + xx) * 4; };
  const tl = at(x0 - 1, y0 - 1), tr = at(x1 + 1, y0 - 1), bl = at(x0 - 1, y1 + 1), br = at(x1 + 1, y1 + 1);
  const src = new Uint8ClampedArray(p);
  for (let yy = y0; yy <= y1; yy++) {
    const v = (yy - y0 + 1) / (y1 - y0 + 2);
    for (let xx = x0; xx <= x1; xx++) {
      const u = (xx - x0 + 1) / (x1 - x0 + 2);
      const T = at(xx, y0 - 1), B = at(xx, y1 + 1), L = at(x0 - 1, yy), R = at(x1 + 1, yy);
      const o = (yy * W + xx) * 4;
      for (let k = 0; k < 3; k++) {
        const val = (1 - v) * src[T + k] + v * src[B + k] + (1 - u) * src[L + k] + u * src[R + k]
          - ((1 - u) * (1 - v) * src[tl + k] + u * (1 - v) * src[tr + k] + (1 - u) * v * src[bl + k] + u * v * src[br + k]);
        p[o + k] = val;
      }
      p[o + 3] = 255;
    }
  }
  ctx.putImageData(d, 0, 0);
  document.title = 'ready';
};
img.src = '%s';
</script>""" % (width, height, x, pad, y, pad, x, size, pad, y, size, pad, old_png)
    canvas = '<canvas id="cv" width="%d" height="%d" style="position:absolute;left:0;top:0"></canvas>' % (width, height)
    return page_html(width, height, [canvas, layer_icon(size, x, y), script])


class Renderer:
    def __init__(self, jobs):
        self.jobs = jobs
        self.count = 0

    def shoot(self, name, html, width, height, extra_wait=False):
        WORK.mkdir(parents=True, exist_ok=True)
        name = re.sub(r"[^A-Za-z0-9_.-]", "_", name)
        page = WORK / (name + ".html")
        out = WORK / (name + ".png")
        page.write_text(html, encoding="utf-8")
        if out.exists():
            out.unlink()
        args = [EDGE, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run",
                "--force-device-scale-factor=1", "--default-background-color=00000000",
                "--allow-file-access-from-files", "--user-data-dir=" + str(WORK / "profiles" / name),
                "--window-size=%d,%d" % (width, height), "--screenshot=" + str(out)]
        if extra_wait:
            args.append("--virtual-time-budget=5000")
        args.append(page.as_uri())
        subprocess.run(args, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=180)
        if not out.exists():
            raise RuntimeError("Edge did not write " + str(out))
        got = png_size(out)
        if got != (width, height):
            raise RuntimeError("%s: got %s, wanted %dx%d" % (name, got, width, height))
        return out.read_bytes()

    def run(self, tasks):
        results = {}
        with concurrent.futures.ThreadPoolExecutor(max_workers=self.jobs) as pool:
            futures = {pool.submit(self.shoot, *task): task[0] for task in tasks}
            for future in concurrent.futures.as_completed(futures):
                results[futures[future]] = future.result()
        return results


def rel(path):
    return ROOT / path


IOS = "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/"
MSIX = "desktopNative/packaging/msix/Assets/"
ANDROID_RES = "androidApp/src/main/res/"
LINUX_ICONS = "desktopNative/packaging/linux/icons/"

STORE_PATCHES = {
    "store-shots/tile-71.png": (3, 3, 65),
    "store-shots/tile-150.png": (6, 6, 138),
    "store-shots/tile-300.png": (12, 12, 276),
    "store-shots/box-1080.png": (130, 130, 820),
    "store-shots/box-2160.png": (260, 260, 1640),
    "store-shots/poster-720x1080.png": (144, 194, 432),
    "store-shots/poster-1440x2160.png": (288, 388, 864),
    "store-shots/hero-1920x1080.png": (170, 350, 380),
    "store-shots/hero-3840x2160.png": (340, 700, 760),
    "extension-xpi/edge-tile-440x280.png": (30, 65, 150),
    "extension-xpi/edge-tile-1400x560.png": (130, 130, 300),
}


def png_targets():
    t = {}
    for density, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        t[ANDROID_RES + "mipmap-%s/ic_launcher.png" % density] = (size, size, comp_full(size))
        t[ANDROID_RES + "mipmap-%s/ic_launcher_round.png" % density] = (size, size, comp_round(size))
    t[IOS + "AppIcon.png"] = (1024, 1024, comp_opaque(1024))
    t[IOS + "AppIcon-Dark.png"] = (1024, 1024, comp_art(1024))
    t[IOS + "AppIcon-Tinted.png"] = (1024, 1024, comp_art(1024, "filter:grayscale(1)"))
    for name, size in (("icon_20", 20), ("icon_20@2x", 40), ("icon_20@3x", 60), ("icon_29", 29), ("icon_29@2x", 58),
                       ("icon_29@3x", 87), ("icon_40", 40), ("icon_40@2x", 80), ("icon_40@3x", 120), ("icon_60@2x", 120),
                       ("icon_60@3x", 180), ("icon_76", 76), ("icon_76@2x", 152), ("icon_83.5@2x", 167)):
        t[IOS + name + ".png"] = (size, size, comp_opaque(size))
    t[MSIX + "Square150x150Logo.png"] = (150, 150, comp_placed(150, 150, 18, 18, 114))
    t[MSIX + "Square150x150Logo.scale-200.png"] = (300, 300, comp_placed(300, 300, 36, 36, 228))
    t[MSIX + "Wide310x150Logo.png"] = (310, 150, comp_placed(310, 150, 98, 18, 114))
    t[MSIX + "Wide310x150Logo.scale-200.png"] = (620, 300, comp_placed(620, 300, 196, 36, 228))
    t[MSIX + "StoreLogo.png"] = (50, 50, comp_full(50))
    t[MSIX + "Square44x44Logo.png"] = (44, 44, comp_full(44))
    for size in (16, 24, 32, 48, 256):
        t[MSIX + "Square44x44Logo.targetsize-%d.png" % size] = (size, size, comp_full(size))
        t[MSIX + "Square44x44Logo.targetsize-%d_altform-unplated.png" % size] = (size, size, comp_full(size))
    t["desktopNative/res/fetchjet-tray.png"] = (64, 64, comp_tray(64))
    t["desktopNative/ui/logo.png"] = (512, 512, comp_full(512))
    t["docs/media/logo.png"] = (512, 512, comp_full(512))
    t["desktopNative/packaging/installer/BundleLogo.png"] = (64, 64, comp_full(64))
    t["extension/icon16.png"] = (16, 16, comp_full(16))
    t["extension/icon48.png"] = (48, 48, comp_full(48))
    t["extension/icon128.png"] = (128, 128, comp_full(128))
    for size in (16, 19, 32, 38, 48, 64):
        t["extension/toolbar%d.png" % size] = (size, size, comp_tray(size))
    t["extension-xpi/edge-logo-300.png"] = (300, 300, comp_full(300))
    for size in (16, 24, 32, 48, 64, 128, 256, 512):
        t[LINUX_ICONS + "hicolor/%dx%d/apps/fetchjet.png" % (size, size)] = (size, size, comp_full(size))
    return t


def internal_targets():
    t = {}
    for size in (16, 24, 32, 48, 64, 128, 256):
        t["ico-%d" % size] = (size, size, comp_full(size))
    for size in (32, 64, 128, 256, 512, 1024):
        t["mac-%d" % size] = (size, size, comp_mac(size))
    t["logo-92"] = (92, 92, comp_full(92))
    t["logo-42"] = (42, 42, comp_full(42))
    t["logo-64"] = (64, 64, comp_full(64))
    return t


def to_task(name, spec):
    width, height, html = spec
    return (name, html, width, height)


def gradient(width, height, top, bottom, horizontal=False):
    steps = width if horizontal else height
    colors = []
    for i in range(steps):
        t = i / max(1, steps - 1)
        colors.append(tuple(round(top[c] + (bottom[c] - top[c]) * t) for c in range(3)))
    return colors


def paste(dst, dst_w, logo_png, x0, y0):
    w, h, ch, px = png_decode(logo_png)
    for y in range(h):
        for x in range(w):
            k = (y * w + x) * ch
            a = px[k + 3] / 255.0 if ch == 4 else 1.0
            o = ((y0 + y) * dst_w + x0 + x) * 3
            for c in range(3):
                dst[o + c] = round(px[k + c] * a + dst[o + c] * (1 - a))


def installer_art(shots):
    navy_top, navy_bottom = (7, 12, 25), (16, 32, 62)
    path = rel("desktopNative/packaging/installer/WixUIDialog.bmp")
    w, h, px = bmp_read(path)
    column = gradient(164, 312, navy_top, navy_bottom)
    for y in range(58, 58 + 92):
        for x in range(36, 36 + 92):
            px[(y * w + x) * 3:(y * w + x) * 3 + 3] = bytes(column[y])
    paste(px, w, shots["logo-92"], 36, 58)
    bmp_write(path, w, h, px)

    path = rel("desktopNative/packaging/installer/WixUIBanner.bmp")
    w, h, px = bmp_read(path)
    for y in range(8, 8 + 42):
        for x in range(436, 436 + 42):
            px[(y * w + x) * 3:(y * w + x) * 3 + 3] = bytes(navy_top)
    paste(px, w, shots["logo-42"], 436, 8)
    bmp_write(path, w, h, px)

    path = rel("desktopNative/packaging/installer/BundleHeader.png")
    w, h, ch, px = png_decode(path.read_bytes())
    if ch != 3:
        raise RuntimeError("BundleHeader.png is expected to be RGB")
    row = gradient(560, 96, navy_top, navy_bottom, horizontal=True)
    for y in range(16, 16 + 64):
        for x in range(20, 20 + 64):
            px[(y * w + x) * 3:(y * w + x) * 3 + 3] = bytes(row[x])
    paste(px, w, shots["logo-64"], 20, 16)
    path.write_bytes(png_encode(w, h, px, 3))


def to_rgb(png):
    w, h, ch, px = png_decode(png)
    if ch == 3:
        return png
    rgb = bytearray(w * h * 3)
    rgb[0::3], rgb[1::3], rgb[2::3] = px[0::4], px[1::4], px[2::4]
    return png_encode(w, h, rgb, 3)


def store_tasks():
    tasks = []
    for path, box in STORE_PATCHES.items():
        source = rel(path)
        if not source.exists():
            continue
        WORK.mkdir(parents=True, exist_ok=True)
        old = WORK / ("old-" + source.name)
        shutil.copyfile(source, old)
        w, h = png_size(source)
        tasks.append((path, comp_patch(w, h, old.as_uri(), box), w, h))
    return tasks


class Shape:
    def __init__(self, cmds, bbox, fill, stroke, width, matrix, glow):
        self.cmds, self.bbox, self.fill, self.stroke, self.width, self.matrix, self.glow = cmds, bbox, fill, stroke, width, matrix, glow


def compose(outer, inner):
    so, xo, yo = outer
    si, xi, yi = inner
    return so * si, so * xi + xo, so * yi + yo


def parse_transform(text):
    m = (1.0, 0.0, 0.0)
    for kind, args in re.findall(r"(translate|scale)\(([^)]*)\)", text or ""):
        values = [float(v) for v in re.split(r"[\s,]+", args.strip())]
        if kind == "translate":
            step = (1.0, values[0], values[1] if len(values) > 1 else 0.0)
        else:
            step = (values[0], 0.0, 0.0)
        m = compose(m, step)
    return m


ARGS = {"M": 2, "L": 2, "Q": 4, "C": 6, "A": 7, "Z": 0}


def parse_path(d):
    tokens = re.findall(r"[A-Za-z]|-?\d*\.?\d+(?:[eE]-?\d+)?", d)
    cmds, i, cmd = [], 0, None
    while i < len(tokens):
        if re.match(r"[A-Za-z]", tokens[i]):
            cmd = tokens[i]
            i += 1
            if cmd not in ARGS:
                raise ValueError("unsupported path command " + cmd)
            if cmd == "Z":
                cmds.append(("Z", []))
                continue
        n = ARGS[cmd]
        cmds.append((cmd, [float(v) for v in tokens[i:i + n]]))
        i += n
        if cmd == "M":
            cmd = "L"
    return cmds


def path_bbox(cmds):
    xs, ys = [], []
    cur = (0.0, 0.0)
    for cmd, a in cmds:
        if cmd in ("M", "L"):
            cur = (a[0], a[1])
            xs.append(a[0]); ys.append(a[1])
        elif cmd == "Q":
            p0, p1, p2 = cur, (a[0], a[1]), (a[2], a[3])
            for k in range(2):
                den = p0[k] - 2 * p1[k] + p2[k]
                pts = [0.0, 1.0]
                if den:
                    t = (p0[k] - p1[k]) / den
                    if 0 < t < 1:
                        pts.append(t)
                for t in pts:
                    v = (1 - t) ** 2 * p0[k] + 2 * t * (1 - t) * p1[k] + t * t * p2[k]
                    (xs if k == 0 else ys).append(v)
            cur = p2
        elif cmd == "C":
            p0, p1, p2, p3 = cur, (a[0], a[1]), (a[2], a[3]), (a[4], a[5])
            for s in range(101):
                t = s / 100
                for k in range(2):
                    v = ((1 - t) ** 3 * p0[k] + 3 * (1 - t) ** 2 * t * p1[k] + 3 * (1 - t) * t * t * p2[k] + t ** 3 * p3[k])
                    (xs if k == 0 else ys).append(v)
            cur = p3
    return min(xs), min(ys), max(xs), max(ys)


def rect_path(x, y, w, h, r):
    r = min(r, w / 2, h / 2)
    if r <= 0:
        return [("M", [x, y]), ("L", [x + w, y]), ("L", [x + w, y + h]), ("L", [x, y + h]), ("Z", [])]
    return [("M", [x + r, y]), ("L", [x + w - r, y]), ("A", [r, r, 0, 0, 1, x + w, y + r]), ("L", [x + w, y + h - r]),
            ("A", [r, r, 0, 0, 1, x + w - r, y + h]), ("L", [x + r, y + h]), ("A", [r, r, 0, 0, 1, x, y + h - r]),
            ("L", [x, y + r]), ("A", [r, r, 0, 0, 1, x + r, y]), ("Z", [])]


def parse_svg(path):
    root = ET.parse(path).getroot()
    gradients = {}
    for node in root.iter():
        tag = node.tag.replace(SVG_NS, "")
        if tag in ("linearGradient", "radialGradient"):
            stops = [(float(s.get("offset")), s.get("stop-color"), float(s.get("stop-opacity", "1")))
                     for s in node if s.tag.replace(SVG_NS, "") == "stop"]
            attrs = {k: float(v) for k, v in node.attrib.items() if k in ("x1", "y1", "x2", "y2", "cx", "cy", "r")}
            gradients[node.get("id")] = (tag, attrs, stops)
    shapes = []

    def walk(node, matrix, glow):
        tag = node.tag.replace(SVG_NS, "")
        matrix = compose(matrix, parse_transform(node.get("transform")))
        glow = glow or bool(node.get("filter"))
        if tag in ("defs", "linearGradient", "radialGradient", "filter"):
            return
        if tag == "rect":
            x, y = float(node.get("x", 0)), float(node.get("y", 0))
            w, h = float(node.get("width")), float(node.get("height"))
            cmds = rect_path(x, y, w, h, float(node.get("rx", 0)))
            shapes.append(Shape(cmds, (x, y, x + w, y + h), node.get("fill", "#000000"), node.get("stroke"),
                                float(node.get("stroke-width", 1)), matrix, glow))
        elif tag == "path":
            cmds = parse_path(node.get("d"))
            shapes.append(Shape(cmds, path_bbox(cmds), node.get("fill", "#000000"), node.get("stroke"),
                                float(node.get("stroke-width", 1)), matrix, glow))
        for child in node:
            walk(child, matrix, glow)

    walk(root, (1.0, 0.0, 0.0), False)
    return shapes, gradients


def apply(matrix, x, y):
    s, tx, ty = matrix
    return s * x + tx, s * y + ty


def path_data(cmds, matrix):
    s = matrix[0]
    out = []
    for cmd, a in cmds:
        if cmd == "Z":
            out.append("Z")
        elif cmd == "A":
            x, y = apply(matrix, a[5], a[6])
            out.append("A%s,%s %s %d,%d %s,%s" % (num(a[0] * s), num(a[1] * s), num(a[2]), int(a[3]), int(a[4]), num(x), num(y)))
        else:
            pts = []
            for i in range(0, len(a), 2):
                x, y = apply(matrix, a[i], a[i + 1])
                pts.append("%s,%s" % (num(x), num(y)))
            out.append(cmd + " ".join(pts))
    return " ".join(out)


def android_color(hex_color, opacity=1.0):
    hex_color = hex_color.lstrip("#")
    if len(hex_color) == 3:
        hex_color = "".join(c * 2 for c in hex_color)
    return "#%02X%s" % (round(opacity * 255), hex_color.upper())


def user_gradient(grad, bbox, matrix):
    kind, a, stops = grad
    bx, by, bx1, by1 = bbox
    w, h = bx1 - bx, by1 - by
    if kind == "linearGradient":
        x1, y1, x2, y2 = a.get("x1", 0), a.get("y1", 0), a.get("x2", 1), a.get("y2", 0)
        dx, dy = x2 - x1, y2 - y1
        dd = dx * dx + dy * dy
        gx, gy = dx / (w * dd), dy / (h * dd)
        gg = gx * gx + gy * gy
        sx, sy = bx + x1 * w, by + y1 * h
        ex, ey = sx + gx / gg, sy + gy / gg
        sx, sy = apply(matrix, sx, sy)
        ex, ey = apply(matrix, ex, ey)
        return {"type": "linear", "start": (sx, sy), "end": (ex, ey), "stops": stops}
    cx, cy = apply(matrix, bx + a.get("cx", 0.5) * w, by + a.get("cy", 0.5) * h)
    r = a.get("r", 0.5) * max(w, h) * matrix[0]
    return {"type": "radial", "center": (cx, cy), "radius": r, "stops": stops}


def resolve(paint, gradients, bbox, matrix):
    if not paint or paint == "none":
        return None
    m = re.match(r"url\(#([^)]+)\)", paint)
    if m:
        return user_gradient(gradients[m.group(1)], bbox, matrix)
    return {"type": "solid", "color": paint}


def vector_items(path, outer=(1.0, 0.0, 0.0), solid=None, halo=True):
    shapes, gradients = parse_svg(path)
    items = []
    glow_boxes = [s for s in shapes if s.glow]
    halo_items = []
    if halo and glow_boxes:
        xs, ys = [], []
        for s in glow_boxes:
            x0, y0 = apply(s.matrix, s.bbox[0], s.bbox[1])
            x1, y1 = apply(s.matrix, s.bbox[2], s.bbox[3])
            xs += [x0, x1]; ys += [y0, y1]
        cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
        grow = (1.1, cx - 1.1 * cx, cy - 1.1 * cy)
        for s in glow_boxes:
            m = compose(outer, compose(grow, s.matrix))
            halo_items.append({"d": path_data(s.cmds, m), "fill": resolve(s.fill, gradients, s.bbox, m), "alpha": 0.35})
    halo_done = False
    for s in shapes:
        m = compose(outer, s.matrix)
        if s.glow and not halo_done:
            items += halo_items
            halo_done = True
        fill = resolve(s.fill, gradients, s.bbox, m)
        stroke = resolve(s.stroke, gradients, s.bbox, m)
        if solid:
            fill = {"type": "solid", "color": solid} if fill else None
            stroke = {"type": "solid", "color": solid} if stroke else None
        items.append({"d": path_data(s.cmds, m), "fill": fill, "stroke": stroke, "width": s.width * m[0]})
    return items


def android_gradient(paint, attr, indent):
    pad = " " * indent
    lines = [pad + '<aapt:attr name="android:%s">' % attr]
    if paint["type"] == "linear":
        lines.append(pad + '    <gradient android:type="linear" android:startX="%s" android:startY="%s" android:endX="%s" android:endY="%s">'
                     % (num(paint["start"][0]), num(paint["start"][1]), num(paint["end"][0]), num(paint["end"][1])))
    else:
        lines.append(pad + '    <gradient android:type="radial" android:centerX="%s" android:centerY="%s" android:gradientRadius="%s">'
                     % (num(paint["center"][0]), num(paint["center"][1]), num(paint["radius"])))
    for offset, color, opacity in paint["stops"]:
        lines.append(pad + '        <item android:offset="%s" android:color="%s" />' % (num(offset), android_color(color, opacity)))
    lines.append(pad + "    </gradient>")
    lines.append(pad + "</aapt:attr>")
    return lines


def vector_xml(items, size_dp, viewport):
    uses_aapt = any((it.get("fill") or {}).get("type") in ("linear", "radial") or
                    (it.get("stroke") or {}).get("type") in ("linear", "radial") for it in items)
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"']
    if uses_aapt:
        out.append('    xmlns:aapt="http://schemas.android.com/aapt"')
    out += ['    android:width="%sdp"' % num(size_dp), '    android:height="%sdp"' % num(size_dp),
            '    android:viewportWidth="%s"' % num(viewport), '    android:viewportHeight="%s">' % num(viewport)]
    for it in items:
        attrs = ['android:pathData="%s"' % it["d"]]
        children = []
        fill, stroke = it.get("fill"), it.get("stroke")
        if fill and fill["type"] == "solid":
            attrs.append('android:fillColor="%s"' % android_color(fill["color"]))
        elif fill:
            children += android_gradient(fill, "fillColor", 8)
        if "alpha" in it:
            attrs.append('android:fillAlpha="%s"' % num(it["alpha"]))
        if stroke:
            attrs.append('android:strokeWidth="%s"' % num(it["width"]))
            if stroke["type"] == "solid":
                attrs.append('android:strokeColor="%s"' % android_color(stroke["color"]))
            else:
                children += android_gradient(stroke, "strokeColor", 8)
        if children:
            out.append("    <path " + "\n        ".join(attrs) + ">")
            out += children
            out.append("    </path>")
        else:
            out.append("    <path " + "\n        ".join(attrs) + " />")
    out.append("</vector>")
    return "\n".join(out) + "\n"


def preview_svg(layers, viewport):
    defs, body, n = [], [], 0
    for items in layers:
        for it in items:
            def paint(p):
                nonlocal n
                if not p:
                    return "none"
                if p["type"] == "solid":
                    return p["color"]
                n += 1
                stops = "".join('<stop offset="%s" stop-color="%s" stop-opacity="%s"/>' % (num(o), c, num(a)) for o, c, a in p["stops"])
                if p["type"] == "linear":
                    defs.append('<linearGradient id="g%d" gradientUnits="userSpaceOnUse" x1="%s" y1="%s" x2="%s" y2="%s">%s</linearGradient>'
                                % (n, num(p["start"][0]), num(p["start"][1]), num(p["end"][0]), num(p["end"][1]), stops))
                else:
                    defs.append('<radialGradient id="g%d" gradientUnits="userSpaceOnUse" cx="%s" cy="%s" r="%s">%s</radialGradient>'
                                % (n, num(p["center"][0]), num(p["center"][1]), num(p["radius"]), stops))
                return "url(#g%d)" % n
            fill = paint(it.get("fill"))
            stroke = paint(it.get("stroke"))
            extra = ' fill-opacity="%s"' % num(it["alpha"]) if "alpha" in it else ""
            if it.get("stroke"):
                extra += ' stroke="%s" stroke-width="%s"' % (stroke, num(it["width"]))
            body.append('<path d="%s" fill="%s"%s/>' % (it["d"], fill, extra))
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %s %s"><defs>%s</defs>%s</svg>'
            % (num(viewport), num(viewport), "".join(defs), "".join(body)))


def write_text(path, text):
    p = rel(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8", newline="\n")


def android_vectors():
    safe = (FG_SCALE, 54 - 54 * FG_SCALE, 54 - 54 * FG_SCALE)
    fg = vector_items(MASTERS / "fetchjet-foreground.svg")
    bg = vector_items(MASTERS / "fetchjet-background.svg")
    mono = vector_items(MASTERS / "fetchjet-mono.svg", outer=safe, solid="#FFFFFF", halo=False)
    logo = vector_items(MASTERS / "fetchjet-icon.svg")
    write_text(ANDROID_RES + "drawable/ic_launcher_foreground.xml", vector_xml(fg, 108, 108))
    write_text(ANDROID_RES + "drawable-v24/ic_launcher_foreground.xml", vector_xml(fg, 108, 108))
    write_text(ANDROID_RES + "drawable/ic_launcher_background.xml", vector_xml(bg, 108, 108))
    write_text(ANDROID_RES + "drawable/ic_launcher_monochrome.xml", vector_xml(mono, 108, 108))
    write_text("androidUi/src/androidMain/res/drawable/ic_fetchjet_logo.xml", vector_xml(logo, 1024, 108))
    WORK.mkdir(parents=True, exist_ok=True)
    (WORK / "preview-android-adaptive.svg").write_text(preview_svg([bg, fg], 108), encoding="utf-8")
    (WORK / "preview-android-mono.svg").write_text(preview_svg([mono], 108), encoding="utf-8")
    (WORK / "preview-android-logo.svg").write_text(preview_svg([logo], 108), encoding="utf-8")


def copy_svgs():
    icon = (MASTERS / "fetchjet-icon.svg").read_text(encoding="utf-8")
    write_text("desktopNative/ui/logo.svg", icon)
    write_text(LINUX_ICONS + "hicolor/scalable/apps/fetchjet.svg", icon)


def contact_sheet(out_path, files):
    cells = []
    for f in files:
        p = rel(f)
        w, h = (png_size(p) if p.suffix == ".png" else (128, 128))
        scale = min(1.0, 220.0 / max(w, h))
        cells.append('<div class="c"><div class="i"><img src="%s" width="%d" height="%d"></div><div class="t">%s<br>%dx%d</div></div>'
                     % (p.as_uri(), max(1, round(w * scale)), max(1, round(h * scale)), f.split("/")[-1], w, h))
    for svg in ("preview-android-adaptive.svg", "preview-android-mono.svg", "preview-android-logo.svg"):
        style = "border-radius:50%" if "adaptive" in svg else ""
        cells.append('<div class="c"><div class="i" style="background:#333"><img src="%s" width="160" height="160" style="%s"></div><div class="t">%s</div></div>'
                     % ((WORK / svg).as_uri(), style, svg))
    for f in ("desktopNative/res/fetchjet.ico", "desktopNative/res/launcher.icns"):
        cells.append('<div class="c"><div class="t">%s<br>%d bytes</div></div>' % (f, rel(f).stat().st_size))
    html = ('<!doctype html><html><head><meta charset="utf-8"><style>body{margin:0;padding:8px;background:#9aa0ab;font:10px Segoe UI,sans-serif}'
            '.c{display:inline-block;vertical-align:top;margin:4px;width:232px}.i{background:repeating-conic-gradient(#ccc 0 25%%,#fff 0 50%%) 0 0/12px 12px;'
            'min-height:40px;display:flex;align-items:center;justify-content:center;padding:4px}.t{word-break:break-all}</style></head><body>%s</body></html>'
            % "".join(cells))
    page = WORK / "contact-sheet.html"
    page.write_text(html, encoding="utf-8")
    rows = (len(cells) + 7) // 8
    height = min(16000, 60 + rows * 270)
    Renderer(1).shoot("contact-sheet-shot", html, 1920, height)
    shutil.copyfile(WORK / "contact-sheet-shot.png", out_path)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jobs", type=int, default=6)
    parser.add_argument("--sheet", default="")
    parser.add_argument("--skip-store", action="store_true")
    args = parser.parse_args()
    if not Path(EDGE).exists():
        sys.exit("Edge not found: set FETCHJET_EDGE")
    renderer = Renderer(args.jobs)

    android_vectors()
    copy_svgs()

    targets = png_targets()
    internal = internal_targets()
    tasks = [to_task(name, spec) for name, spec in list(targets.items()) + list(internal.items())]
    files = renderer.run(tasks)

    for name in targets:
        out = rel(name)
        out.parent.mkdir(parents=True, exist_ok=True)
        data = files[name]
        if name == IOS + "AppIcon.png" or (name.startswith(IOS + "icon_")):
            data = to_rgb(data)
        out.write_bytes(data)

    ico_write(rel("desktopNative/res/fetchjet.ico"), [(s, files["ico-%d" % s]) for s in (16, 24, 32, 48, 64, 128, 256)])
    icns_write(rel("desktopNative/res/launcher.icns"), [
        ("ic07", files["mac-128"]), ("ic08", files["mac-256"]), ("ic09", files["mac-512"]), ("ic10", files["mac-1024"]),
        ("ic11", files["mac-32"]), ("ic12", files["mac-64"]), ("ic13", files["mac-256"]), ("ic14", files["mac-512"])])
    installer_art(files)

    store = []
    if not args.skip_store:
        store_shots = Renderer(min(args.jobs, 3)).run([t + (True,) for t in store_tasks()])
        for path, data in store_shots.items():
            target = rel(path)
            target.write_bytes(data)
            store.append(path)

    if args.sheet:
        listed = list(targets) + ["desktopNative/packaging/installer/BundleHeader.png"] + sorted(store)
        contact_sheet(args.sheet, listed)
    print("icons written:", len(targets) + 2 + 3 + len(store))


if __name__ == "__main__":
    main()
