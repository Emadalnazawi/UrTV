#!/usr/bin/env python3
"""optimize/photo_prep.py: a photo is made ready for the TV (upright, screen size, JPEG 85, nothing else kept).
Generated pictures only.   Run: python3 tests/photo_prep_test.py   (needs Pillow)"""
import io
import os
import sys
import tempfile

try:
    from PIL import Image, ImageCms
except ModuleNotFoundError:
    print("SKIP photo prep: Pillow is not installed (pip install pillow)")
    sys.exit(0)

here = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(here, "..", "optimize"))
import photo_prep  # noqa: E402

tmp = tempfile.mkdtemp()
failed = 0


def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + detail))
    failed += not ok


def make(name, size, **save):
    path = os.path.join(tmp, name)
    im = Image.new("RGB", size, (200, 40, 40))
    im.paste((20, 20, 220), (0, 0, size[0] // 4, size[1] // 4))   # a blue corner: where it ends up shows the rotation
    im.save(path, **save)
    return path


def prep(path, screen=(1920, 1080)):
    out = path + ".out.jpg"
    return out, photo_prep.prepare(path, out, screen)


def exif_with(**tags):
    im = Image.new("RGB", (1, 1))
    ex = im.getexif()
    for k, v in tags.items():
        if k == "orientation":
            ex[0x0112] = v
        elif k == "make":
            ex[0x010F] = v
        elif k == "model":
            ex[0x0110] = v
    ex.get_ifd(0x8825)[1] = "N"   # GPS latitude reference: a location
    ex.get_ifd(0x8825)[2] = (29.0, 22.0, 36.0)
    return ex


# size: shrunk to the screen, never enlarged, at most 1920 wide
out, size = prep(make("big.jpg", (4000, 2250)))
check("a big photo is shrunk to 1920 wide", size == (1920, 1080), size)
out, size = prep(make("small.jpg", (800, 450)))
check("a small photo is not enlarged", size == (800, 450), size)
out, size = prep(make("big.jpg", (4000, 2250)), screen=(1280, 720))
check("a 720p screen: shrunk to cover it", size == (1280, 720), size)
out, size = prep(make("big.jpg", (6000, 3375)), screen=(3840, 2160))
check("a 4K screen: still at most 1920 wide", size == (1920, 1080), size)
out, size = prep(make("tall.jpg", (3000, 4000)))
check("a tall photo stays under the pixel cap and covers the width", size[0] * size[1] <= photo_prep.MAX_PIXELS and 1700 <= size[0] <= 1920
      and abs(size[0] / size[1] - 0.75) < 0.01, size)
im = Image.open(out)
check("the result is a JPEG in RGB", im.format == "JPEG" and im.mode == "RGB", f"{im.format} {im.mode}")
check("the result fits what the TV accepts (3 MB)", os.path.getsize(out) < 3 << 20, os.path.getsize(out))

# orientation: EXIF 6 = turn 90 degrees clockwise to be upright; the blue corner (top left of the file) moves to the top right
path = make("rot.jpg", (400, 200), exif=exif_with(orientation=6))
out, size = prep(path)
im = Image.open(out)
check("EXIF orientation is applied (landscape file shown upright = portrait)", size == (200, 400), size)
check("...the pixels were turned, not only the tag", im.getpixel((190, 10))[2] > 150 and im.getpixel((10, 10))[2] < 100, im.getpixel((190, 10)))
check("...and no orientation tag is left to turn it twice", 0x0112 not in im.getexif(), dict(im.getexif()))

# metadata: none of it survives
path = make("meta.jpg", (2400, 1600), exif=exif_with(orientation=1, make="SecretCam", model="X1"),
            icc_profile=ImageCms.ImageCmsProfile(ImageCms.createProfile("sRGB")).tobytes(), comment=b"private note")
raw = open(path, "rb").read()
check("(the test photo really carries EXIF, GPS, a make, a comment and a profile)",
      b"Exif" in raw and b"SecretCam" in raw and b"private note" in raw and b"ICC_PROFILE" in raw)
out, size = prep(path)
raw = open(out, "rb").read()
im = Image.open(out)
check("no EXIF, camera name or location in the file", b"Exif" not in raw and b"SecretCam" not in raw and not im.getexif()
      and 0x8825 not in im.getexif(), list(im.getexif().keys()))
check("no comment, XMP or profile in the file", b"private note" not in raw and b"ICC_PROFILE" not in raw and b"xpacket" not in raw
      and not {"comment", "icc_profile", "exif", "xmp"} & set(im.info), list(im.info))
out, size = prep(make("plain.png", (300, 200)))
check("a PNG becomes a JPEG", Image.open(out).format == "JPEG")

# colours: a wide-gamut profile is converted, not just dropped
p3 = ImageCms.createProfile("sRGB")
path = os.path.join(tmp, "prof.jpg")
Image.new("RGB", (64, 64), (10, 200, 30)).save(path, icc_profile=ImageCms.ImageCmsProfile(p3).tobytes())
out, size = prep(path)
check("a photo with a profile keeps its colours", max(abs(a - b) for a, b in zip(Image.open(out).getpixel((5, 5)), (10, 200, 30))) < 8)

# transparency and odd modes
for name, mode, fill in (("alpha.png", "RGBA", (255, 0, 0, 0)), ("pal.png", "P", 3), ("grey.png", "L", 90), ("cmyk.jpg", "CMYK", (0, 0, 0, 0))):
    path = os.path.join(tmp, name)
    Image.new(mode, (200, 100), fill).save(path)
    out, size = prep(path)
    check(f"{mode} is accepted and comes out RGB", Image.open(out).mode == "RGB", Image.open(out).mode)
path = os.path.join(tmp, "alpha.png")
out, size = prep(path)
check("transparent areas turn white, not black", min(Image.open(out).getpixel((5, 5))) > 240, Image.open(out).getpixel((5, 5)))

# things that are not photos
bad = os.path.join(tmp, "x.jpg")
open(bad, "wb").write(b"not a picture")
try:
    prep(bad)
    check("a file that is not a photo is refused", False, "no error")
except ValueError as e:
    check("a file that is not a photo is refused, with the file's name", "x.jpg" in str(e), str(e))
try:
    photo_prep.prepare(os.path.join(tmp, "nope.jpg"), os.path.join(tmp, "o.jpg"))
    check("a missing file is refused", False, "no error")
except ValueError:
    check("a missing file is refused", True)

# HEIC (when pillow-heif is there) opens and comes out as a JPEG; RAW and HEIC without support say what to do
if photo_prep.HEIC:
    import pillow_heif
    hp = os.path.join(tmp, "p.heic")
    pillow_heif.from_pillow(Image.new("RGB", (400, 300), (10, 120, 60))).save(hp)
    out, size = prep(hp)
    check("HEIC: opened and saved as a JPEG", size == (400, 300) and Image.open(out).format == "JPEG", str(size))
else:
    print("SKIP HEIC: pillow-heif is not installed here")
for name, word in (("shot.dng", "RAW"), ("shot.heic", "export it as JPEG")):
    p = os.path.join(tmp, name); open(p, "wb").write(b"junk")
    try:
        prep(p); check(f"{name} that cannot be read: refused", False, "no error")
    except ValueError as e:
        check(f"{name} that cannot be read: says what to do", word in str(e) or (name.endswith("heic") and "not a photo" in str(e)), str(e))
check("the message names the supported formats", "JPEG, PNG, WebP" in photo_prep.FORMATS)

# the command line (no -I here: it would hide a Pillow that pip put in the user's site-packages, as on CI;
# photos.sh runs it with the venv's python, whose packages -I keeps)
import subprocess
r = subprocess.run([sys.executable, os.path.join(here, "..", "optimize", "photo_prep.py"), make("cli.jpg", (3000, 2000)),
                    os.path.join(tmp, "cli-out.jpg"), "--screen", "1280x720"], capture_output=True, text=True)
check("command line: prints the size", r.returncode == 0 and r.stdout.strip() == "1280x853", r.stdout + r.stderr)
r = subprocess.run([sys.executable, os.path.join(here, "..", "optimize", "photo_prep.py"), bad, os.path.join(tmp, "o.jpg")],
                   capture_output=True, text=True)
check("command line: a bad file exits with a message", r.returncode != 0 and "x.jpg" in r.stderr, r.stderr)
sys.exit(1 if failed else 0)
