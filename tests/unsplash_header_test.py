#!/usr/bin/env python3
"""The Unsplash helper sends its Client-ID header only to api.unsplash.com, never to the image URLs the API hands back.
Run: python3 tests/unsplash_header_test.py"""
import importlib.util
import os
import sys
import urllib.request
from pathlib import Path

os.environ["UNSPLASH_ACCESS_KEY"] = "TESTKEY"
spec = importlib.util.spec_from_file_location("fu", Path(__file__).resolve().parents[1] / ".github/tools/fetch_unsplash.py")
fu = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fu)
fu.time.sleep = lambda s: None
failed = 0


def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok


sent = []


class R:
    def __enter__(self): return self
    def __exit__(self, *a): return False
    def read(self): return b"{}"


urllib.request.urlopen = lambda req, timeout=0: (sent.append((req.full_url, {k.lower(): v for k, v in req.header_items()})), R())[1]
for url, auth in [("https://api.unsplash.com/search/photos?query=x", True),
                  ("https://images.unsplash.com/photo-1?w=400", False),
                  ("https://plus.unsplash.com/premium_photo-1", False),
                  ("http://api.unsplash.com/search/photos", False),
                  ("https://api.unsplash.com.evil.example/x", False),
                  ("https://evil.example/api.unsplash.com", False)]:
    sent.clear()
    fu.get(url, raw=True)
    h = sent[0][1]
    check(f"{url}: the key is {'sent' if auth else 'not sent'}", ("authorization" in h) == auth and (not auth or h["authorization"] == "Client-ID TESTKEY"), h)
    check(f"{url}: the other headers still go", h.get("accept-version") == "v1" and "user-agent" in h, h)
sys.exit(1 if failed else 0)
