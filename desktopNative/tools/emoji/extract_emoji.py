import argparse
import os
import re
import struct
import sys
import zlib

SIZES = (20, 32, 48, 64)
EXTRA = [0x1F4BB, 0x1F4F1, 0x1F5A5, 0x1F34E, 0x1F427, 0x1F4BD]


def tables(data):
    count = struct.unpack(">H", data[4:6])[0]
    found = {}
    for i in range(count):
        tag, _, offset, length = struct.unpack(">4sIII", data[12 + 16 * i:28 + 16 * i])
        found[tag.decode("latin-1")] = (offset, length)
    return found


def read_cmap(data, offset):
    count = struct.unpack(">H", data[offset + 2:offset + 4])[0]
    best = None
    for i in range(count):
        platform, encoding, sub = struct.unpack(">HHI", data[offset + 4 + 8 * i:offset + 12 + 8 * i])
        fmt = struct.unpack(">H", data[offset + sub:offset + sub + 2])[0]
        if fmt == 12:
            best = offset + sub
    mapping = {}
    if best is None:
        return mapping
    groups = struct.unpack(">I", data[best + 12:best + 16])[0]
    for i in range(groups):
        start, end, glyph = struct.unpack(">III", data[best + 16 + 12 * i:best + 28 + 12 * i])
        for code in range(start, end + 1):
            mapping[code] = glyph + code - start
    return mapping


def read_bitmaps(data, cblc, cbdt):
    count = struct.unpack(">I", data[cblc + 4:cblc + 8])[0]
    best = None
    for i in range(count):
        base = cblc + 8 + 48 * i
        array_offset, _, subtables = struct.unpack(">III", data[base:base + 12])
        ppem = data[base + 45]
        if best is None or ppem > best[0]:
            best = (ppem, array_offset, subtables)
    _, array_offset, subtables = best
    images = {}
    for i in range(subtables):
        entry = cblc + array_offset + 8 * i
        first, last, extra = struct.unpack(">HHI", data[entry:entry + 8])
        head = cblc + array_offset + extra
        index_format, image_format, data_offset = struct.unpack(">HHI", data[head:head + 8])
        if image_format != 17:
            continue
        if index_format == 1:
            for glyph in range(first, last + 1):
                k = head + 8 + 4 * (glyph - first)
                a, b = struct.unpack(">II", data[k:k + 8])
                if b > a:
                    images[glyph] = cbdt + data_offset + a
        elif index_format == 3:
            for glyph in range(first, last + 1):
                k = head + 8 + 2 * (glyph - first)
                a, b = struct.unpack(">HH", data[k:k + 4])
                if b > a:
                    images[glyph] = cbdt + data_offset + a
    return images


def png_bytes(data, at):
    length = struct.unpack(">I", data[at + 5:at + 9])[0]
    return data[at + 9:at + 9 + length]


def decode_png(blob):
    i = 8
    idat = b""
    palette = None
    alpha = None
    width = height = color = depth = 0
    while i < len(blob):
        n = struct.unpack(">I", blob[i:i + 4])[0]
        kind = blob[i + 4:i + 8]
        chunk = blob[i + 8:i + 8 + n]
        if kind == b"IHDR":
            width, height, depth, color = struct.unpack(">IIBB", chunk[:10])
            if depth != 8 and color not in (0, 3):
                raise ValueError("bit depth")
        elif kind == b"PLTE":
            palette = chunk
        elif kind == b"tRNS":
            alpha = chunk
        elif kind == b"IDAT":
            idat += chunk
        i += 12 + n
    raw = zlib.decompress(idat)
    bpp = {2: 3, 6: 4, 3: 1, 0: 1, 4: 2}[color]
    stride = width * bpp if depth == 8 else (width * depth + 7) // 8
    rows = []
    previous = bytearray(stride)
    p = 0
    for _ in range(height):
        f = raw[p]
        line = bytearray(raw[p + 1:p + 1 + stride])
        p += 1 + stride
        for x in range(stride):
            left = line[x - bpp] if x >= bpp else 0
            up = previous[x]
            corner = previous[x - bpp] if x >= bpp else 0
            if f == 1:
                line[x] = (line[x] + left) & 255
            elif f == 2:
                line[x] = (line[x] + up) & 255
            elif f == 3:
                line[x] = (line[x] + (left + up) // 2) & 255
            elif f == 4:
                pa = abs(up - corner)
                pb = abs(left - corner)
                pc = abs(left + up - 2 * corner)
                pred = left if pa <= pb and pa <= pc else (up if pb <= pc else corner)
                line[x] = (line[x] + pred) & 255
        previous = line
        if depth < 8:
            mask = (1 << depth) - 1
            unpacked = bytearray()
            for x in range(width):
                bit = x * depth
                value = (line[bit // 8] >> (8 - depth - bit % 8)) & mask
                unpacked.append(value if color == 3 else value * 255 // mask)
            line = unpacked
        rows.append(line)
    pixels = []
    for line in rows:
        out = []
        for x in range(width):
            if color == 6:
                out.append(tuple(line[4 * x:4 * x + 4]))
            elif color == 2:
                out.append(tuple(line[3 * x:3 * x + 3]) + (255,))
            elif color == 3:
                index = line[x]
                a = alpha[index] if alpha is not None and index < len(alpha) else 255
                out.append(tuple(palette[3 * index:3 * index + 3]) + (a,))
            elif color == 0:
                out.append((line[x], line[x], line[x], 255))
            else:
                out.append((line[2 * x], line[2 * x], line[2 * x], line[2 * x + 1]))
        pixels.append(out)
    return width, height, pixels


def scale(width, height, pixels, size):
    side = max(width, height)
    ox = (side - width) / 2.0
    oy = (side - height) / 2.0
    step = side / float(size)
    out = []
    for ty in range(size):
        row = []
        y0 = ty * step - oy
        y1 = y0 + step
        for tx in range(size):
            x0 = tx * step - ox
            x1 = x0 + step
            acc = [0.0, 0.0, 0.0, 0.0]
            total = step * step
            for sy in range(max(0, int(y0)), min(height, int(y1) + 1)):
                wy = min(y1, sy + 1) - max(y0, sy)
                if wy <= 0:
                    continue
                line = pixels[sy]
                for sx in range(max(0, int(x0)), min(width, int(x1) + 1)):
                    wx = min(x1, sx + 1) - max(x0, sx)
                    if wx <= 0:
                        continue
                    r, g, b, a = line[sx]
                    w = wx * wy * a / 255.0
                    acc[0] += r * w
                    acc[1] += g * w
                    acc[2] += b * w
                    acc[3] += a * wx * wy
            alpha_sum = acc[3] / 255.0
            if alpha_sum > 0:
                row.append((int(acc[0] / alpha_sum + 0.5), int(acc[1] / alpha_sum + 0.5), int(acc[2] / alpha_sum + 0.5), min(255, int(acc[3] / total + 0.5))))
            else:
                row.append((0, 0, 0, 0))
        out.append(row)
    return out


def encode_png(size, pixels):
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for r, g, b, a in row:
            raw += bytes((r, g, b, a))

    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def wanted_codepoints(sources):
    found = set(EXTRA)
    for folder in sources:
        for root, _, files in os.walk(folder):
            for name in files:
                if not name.endswith((".slint", ".kt")):
                    continue
                with open(os.path.join(root, name), encoding="utf-8") as handle:
                    for ch in handle.read():
                        code = ord(ch)
                        if code >= 0x2190 and not 0x2500 <= code <= 0x25FF and code != 0xFE0F and code != 0xFEFF:
                            found.add(code)
    return found


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--font", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--source", action="append", required=True)
    args = parser.parse_args()
    with open(args.font, "rb") as handle:
        data = handle.read()
    found = tables(data)
    cmap = read_cmap(data, found["cmap"][0])
    images = read_bitmaps(data, found["CBLC"][0], found["CBDT"][0])
    os.makedirs(args.out, exist_ok=True)
    written = 0
    for code in sorted(wanted_codepoints(args.source)):
        glyph = cmap.get(code)
        if glyph is None or glyph not in images:
            continue
        width, height, pixels = decode_png(png_bytes(data, images[glyph]))
        for size in SIZES:
            with open(os.path.join(args.out, "%x-%d.png" % (code, size)), "wb") as handle:
                handle.write(encode_png(size, scale(width, height, pixels, size)))
            written += 1
    print("wrote", written, "files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
