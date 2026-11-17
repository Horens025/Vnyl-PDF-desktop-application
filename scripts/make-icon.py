#!/usr/bin/env python3
"""
Generate the Vnyl app icon (1024x1024 PNG) used as the source for
`npm run tauri icon`. Needs Pillow and a bold sans font.

  python3 scripts/make-icon.py [out.png] [font.ttf]
  npm run tauri icon src-tauri/icons/source/vnyl-icon.png

The art: a Liquid-Glass tile (deep blue-violet, specular sheen, rim light), a
vinyl record's grooves, and an Apple-blue label carrying a white "V".
"""
import math
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = sys.argv[1] if len(sys.argv) > 1 else "vnyl-icon.png"
FONT = sys.argv[2] if len(sys.argv) > 2 else "/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf"
S = 2048  # draw at 2x, then downsample for clean edges
C = S // 2
TILE = int(S * 0.805)  # macOS-style margin around the tile
RADIUS = int(TILE * 0.225)


def vertical_gradient(size, top, bottom):
    img = Image.new("RGBA", (size, size))
    px = img.load()
    for y in range(size):
        t = y / (size - 1)
        row = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,)
        for x in range(size):
            px[x, y] = row
    return img


def squircle_mask(size, box, radius):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).rounded_rectangle(box, radius=radius, fill=255)
    return m


box = (C - TILE // 2, C - TILE // 2, C + TILE // 2, C + TILE // 2)
mask = squircle_mask(S, box, RADIUS)

# Tile body: deep indigo to violet-blue.
tile = vertical_gradient(S, (18, 22, 52), (52, 40, 120))

# Soft coloured glows so the glass has something to refract.
glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
g = ImageDraw.Draw(glow)
g.ellipse((C - 900, C - 1100, C + 300, C + 100), fill=(40, 120, 255, 150))
g.ellipse((C - 200, C + 100, C + 1000, C + 1100), fill=(190, 70, 200, 120))
tile = Image.alpha_composite(tile, glow.filter(ImageFilter.GaussianBlur(220)))

# Vinyl grooves: concentric rings, brighter toward the rim.
rings = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(rings)
r_out, r_in = int(TILE * 0.47), int(TILE * 0.2)
d.ellipse((C - r_out, C - r_out, C + r_out, C + r_out), fill=(8, 10, 26, 235))
r = r_in + 14
i = 0
while r < r_out - 6:
    alpha = 26 + (i % 3) * 14
    d.ellipse((C - r, C - r, C + r, C + r), outline=(255, 255, 255, alpha), width=3)
    r += 17
    i += 1
# A broad specular sweep across the record, like light on vinyl.
sweep = Image.new("RGBA", (S, S), (0, 0, 0, 0))
sd = ImageDraw.Draw(sweep)
for a0 in (-28, 152):
    sd.pieslice((C - r_out, C - r_out, C + r_out, C + r_out), a0 - 16, a0 + 16, fill=(255, 255, 255, 46))
rings = Image.alpha_composite(rings, sweep.filter(ImageFilter.GaussianBlur(26)))
tile = Image.alpha_composite(tile, rings)

# Label: Apple-blue disc with a rim, white V, and a spindle hole.
lab = int(TILE * 0.2)
label = vertical_gradient(S, (110, 190, 255), (10, 110, 255))
lm = Image.new("L", (S, S), 0)
ImageDraw.Draw(lm).ellipse((C - lab, C - lab, C + lab, C + lab), fill=255)
tile.paste(label, (0, 0), lm)
ImageDraw.Draw(tile).ellipse((C - lab, C - lab, C + lab, C + lab), outline=(255, 255, 255, 120), width=5)

font = ImageFont.truetype(FONT, int(lab * 1.28))
txt = Image.new("RGBA", (S, S), (0, 0, 0, 0))
td = ImageDraw.Draw(txt)
td.text((C, C + 6), "V", font=font, fill=(255, 255, 255, 255), anchor="mm")
shadow = txt.filter(ImageFilter.GaussianBlur(10))
sh_px = shadow.load()
for y in range(S):
    for x in range(S):
        a = sh_px[x, y][3]
        sh_px[x, y] = (10, 50, 140, int(a * 0.55))
tile = Image.alpha_composite(tile, shadow.transform(shadow.size, Image.AFFINE, (1, 0, 0, 0, 1, -8)))
tile = Image.alpha_composite(tile, txt)

# Glass finish: top sheen, rim light, and bottom shade, all clipped to the tile.
sheen = Image.new("RGBA", (S, S), (0, 0, 0, 0))
shd = ImageDraw.Draw(sheen)
shd.ellipse((box[0] - 200, box[1] - TILE // 2 - 60, box[2] + 200, box[1] + TILE // 3), fill=(255, 255, 255, 62))
tile = Image.alpha_composite(tile, sheen.filter(ImageFilter.GaussianBlur(16)))
rim = Image.new("RGBA", (S, S), (0, 0, 0, 0))
rd = ImageDraw.Draw(rim)
rd.rounded_rectangle(box, radius=RADIUS, outline=(255, 255, 255, 120), width=7)
rd.rounded_rectangle((box[0] + 9, box[1] + 9, box[2] - 9, box[3] - 9), radius=RADIUS - 9, outline=(255, 255, 255, 36), width=4)
tile = Image.alpha_composite(tile, rim)

# Transparent outside the tile, plus a soft drop shadow beneath it.
final = Image.new("RGBA", (S, S), (0, 0, 0, 0))
drop = Image.new("RGBA", (S, S), (0, 0, 0, 0))
drop.paste((0, 0, 0, 120), (0, 26), mask)
final = Image.alpha_composite(final, drop.filter(ImageFilter.GaussianBlur(34)))
tile.putalpha(mask)
final = Image.alpha_composite(final, tile)
final.resize((1024, 1024), Image.LANCZOS).save(OUT)
print("wrote", OUT)
