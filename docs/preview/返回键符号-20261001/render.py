import os
from playwright.sync_api import sync_playwright
from PIL import Image
B = os.path.dirname(os.path.abspath(__file__))
with sync_playwright() as p:
    b = p.chromium.launch()
    pg = b.new_page(viewport={"width": 380, "height": 300}, device_scale_factor=2.5)
    pg.goto("file://" + os.path.join(B, "preview.html"))
    pg.wait_for_timeout(700)
    pg.screenshot(path=os.path.join(B, "_raw.png"), full_page=True)
    b.close()
im = Image.open(os.path.join(B, "_raw.png")).convert("RGB")
im = im.resize((460, int(im.height * 460 / im.width)), Image.LANCZOS)
im.save(os.path.join(B, "arrow-before-after.png"), optimize=True)
os.remove(os.path.join(B, "_raw.png"))
print("ok", im.size, os.path.getsize(os.path.join(B, "arrow-before-after.png"))//1024, "KB")
