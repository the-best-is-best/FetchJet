import os
import sys

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "..", "res")
OUT = sys.argv[1] if len(sys.argv) > 1 else HERE

NAVY_TOP = (7, 12, 25)
NAVY_BOTTOM = (16, 32, 62)
CYAN = (40, 233, 255)
WHITE = (255, 255, 255)
MUTED = (160, 178, 205)


def font(weight, size):
    return ImageFont.truetype(os.path.join(RES, "fonts", "FetchJetSans-%s.ttf" % weight), size)


def logo(size):
    ico = Image.open(os.path.join(RES, "fetchjet.ico"))
    ico.size = (256, 256)
    return ico.convert("RGBA").resize((size, size), Image.LANCZOS)


def gradient(width, height, top, bottom, horizontal=False):
    image = Image.new("RGB", (width, height))
    draw = ImageDraw.Draw(image)
    steps = width if horizontal else height
    for i in range(steps):
        t = i / max(1, steps - 1)
        color = tuple(round(top[c] + (bottom[c] - top[c]) * t) for c in range(3))
        if horizontal:
            draw.line([(i, 0), (i, height)], fill=color)
        else:
            draw.line([(0, i), (width, i)], fill=color)
    return image


def centered(draw, text, y, width, fnt, fill, x0=0):
    box = draw.textbbox((0, 0), text, font=fnt)
    draw.text((x0 + (width - (box[2] - box[0])) / 2 - box[0], y), text, font=fnt, fill=fill)


def dialog_bitmap():
    image = Image.new("RGB", (493, 312), WHITE)
    panel = gradient(164, 312, NAVY_TOP, NAVY_BOTTOM)
    image.paste(panel, (0, 0))
    mark = logo(92)
    image.paste(mark, (36, 58), mark)
    draw = ImageDraw.Draw(image)
    centered(draw, "FetchJet", 162, 164, font("Bold", 24), WHITE)
    centered(draw, "Download manager", 194, 164, font("Medium", 12), MUTED)
    draw.line([(52, 226), (112, 226)], fill=CYAN, width=2)
    image.save(os.path.join(OUT, "WixUIDialog.bmp"))


def banner_bitmap():
    image = Image.new("RGB", (493, 58), WHITE)
    draw = ImageDraw.Draw(image)
    draw.rectangle([(420, 0), (493, 58)], fill=NAVY_TOP)
    mark = logo(42)
    image.paste(mark, (436, 8), mark)
    draw.line([(0, 57), (493, 57)], fill=(225, 230, 238), width=1)
    image.save(os.path.join(OUT, "WixUIBanner.bmp"))


def bundle_header():
    image = gradient(560, 96, NAVY_TOP, NAVY_BOTTOM, horizontal=True)
    mark = logo(64)
    image.paste(mark, (20, 16), mark)
    draw = ImageDraw.Draw(image)
    draw.text((100, 18), "FetchJet", font=font("Bold", 28), fill=WHITE)
    draw.text((101, 56), "Fast downloads, videos and torrents", font=font("Medium", 13), fill=MUTED)
    draw.line([(0, 94), (560, 94)], fill=CYAN, width=2)
    image.save(os.path.join(OUT, "BundleHeader.png"))
    logo(64).save(os.path.join(OUT, "BundleLogo.png"))


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    dialog_bitmap()
    banner_bitmap()
    bundle_header()
    print("art written to", OUT)
