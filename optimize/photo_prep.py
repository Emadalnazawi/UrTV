#!/usr/bin/env python3
"""A photo made ready for the TV: turned upright by its EXIF orientation, in sRGB, shrunk to the TV's screen
(never enlarged, at most 1920 px wide), saved as a JPEG of quality 85 with no metadata at all (no location, camera or
date). Used by optimize/photos.sh before anything is uploaded.

    photo_prep.py IN OUT [--screen WxH]      (default screen 1920x1080)
"""
import io
import sys

from PIL import Image, ImageCms, ImageOps

try:   # iPhone / Samsung HEIC and HEIF: only if pillow-heif is installed (optimize/setup.sh tries; it is optional)
    import pillow_heif
    pillow_heif.register_heif_opener()
    HEIC = True
except Exception:  # noqa: BLE001  (not installed, or no wheel for this computer)
    HEIC = False

FORMATS = "JPEG, PNG, WebP, GIF, BMP" + (", HEIC/HEIF" if HEIC else "")

MAX_WIDTH = 1920
MAX_PIXELS = 4_200_000   # the TV decodes it at full size: a tall photo is kept below this, not just below the screen width
QUALITY = 85


def target(screen):
    """The size the TV shows at: its screen, but at most MAX_WIDTH wide."""
    sw, sh = screen
    if sw <= MAX_WIDTH:
        return sw, sh
    return MAX_WIDTH, max(1, round(sh * MAX_WIDTH / sw))


def size_for(w, h, screen):
    """(w, h) shrunk just enough to still cover the screen (the TV crops to fill it), never enlarged."""
    tw, th = target(screen)
    scale = min(1.0, max(tw / w, th / h))
    if w * h * scale * scale > MAX_PIXELS:
        scale = (MAX_PIXELS / (w * h)) ** 0.5
    return max(1, round(w * scale)), max(1, round(h * scale))


def to_srgb(im):
    """Colours kept right when the photo has its own profile (phones save Display P3); the profile itself is then dropped."""
    icc = im.info.get("icc_profile")
    if not icc:
        return im
    try:
        src = ImageCms.ImageCmsProfile(io.BytesIO(icc))
        return ImageCms.profileToProfile(im, src, ImageCms.createProfile("sRGB"), outputMode="RGB")
    except Exception:  # noqa: BLE001  (a broken profile: keep the pixels as they are)
        return im


def prepare(src, dst, screen=(MAX_WIDTH, 1080)):
    """Writes dst; returns its (width, height). Raises ValueError with a reason when src is not a photo we can use."""
    try:
        im = Image.open(src)
        if im.format == "JPEG":
            side = max(target(screen))   # whichever way EXIF turns it, this is enough on both sides
            im.draft("RGB", (side, side))   # the decoder skips most of a big file's pixels
        im.load()
    except Exception as e:  # noqa: BLE001
        raw = src.lower().endswith((".dng", ".cr2", ".cr3", ".nef", ".arw", ".raf", ".orf", ".rw2"))
        heic = src.lower().endswith((".heic", ".heif")) and not HEIC
        why = ("a RAW file (DNG and the like) is not supported: export it as JPEG first" if raw else
               "HEIC is not supported on this computer (pillow-heif is not installed): export it as JPEG first, or run optimize/setup.sh again" if heic else
               f"supported: {FORMATS}")
        raise ValueError(f"{src}: not a photo I can read ({type(e).__name__}); {why}") from e
    im = ImageOps.exif_transpose(im)   # also drops the orientation tag
    im = to_srgb(im)
    if im.mode in ("RGBA", "LA", "P"):
        im = im.convert("RGBA")
        flat = Image.new("RGB", im.size, (255, 255, 255))
        flat.paste(im, mask=im.getchannel("A"))
        im = flat
    elif im.mode != "RGB":
        im = im.convert("RGB")
    size = size_for(*im.size, screen)
    if size != im.size:
        im = im.resize(size, Image.LANCZOS)
    # a new file with only the pixels: nothing from the original's info (EXIF, GPS, XMP, comments) is passed on
    im.save(dst, "JPEG", quality=QUALITY, optimize=True, progressive=False, exif=b"", icc_profile=None)
    return im.size


def main():
    a = sys.argv[1:]
    screen = (MAX_WIDTH, 1080)
    if "--screen" in a:
        k = a.index("--screen")
        try:
            screen = tuple(int(x) for x in a[k + 1].lower().split("x"))
            assert len(screen) == 2 and min(screen) > 0
        except (IndexError, ValueError, AssertionError):
            sys.exit("--screen needs WxH, like 1920x1080")
        del a[k:k + 2]
    if len(a) != 2:
        sys.exit(__doc__)
    try:
        w, h = prepare(a[0], a[1], screen)
    except ValueError as e:
        sys.exit(f"!! {e}")
    print(f"{w}x{h}")


if __name__ == "__main__":
    main()
