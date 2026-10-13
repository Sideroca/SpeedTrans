import os
from playwright.sync_api import sync_playwright
BASE = os.path.dirname(os.path.abspath(__file__))

SECS = [("s1", "p1-now.png"), ("s2", "p2-planA.png"), ("s3", "p3-planB1-floor30.png"),
        ("s4", "p4-planB2-drop30.png"), ("s7", "p7-FINAL.png"),
        ("s5", "p5-themerows.png"), ("s6", "p6-evidence.png")]

with sync_playwright() as p:
    b = p.chromium.launch()
    pg = b.new_page(viewport={"width": 1520, "height": 1000}, device_scale_factor=1)
    pg.goto("file://" + os.path.join(BASE, "index.html"))
    pg.wait_for_timeout(900)
    pg.screenshot(path=os.path.join(BASE, "preview-full.png"), full_page=True)
    for sid, out in SECS:
        el = pg.query_selector("#" + sid)
        el.screenshot(path=os.path.join(BASE, out))
        print("shot", out)
    b.close()
print("done")
