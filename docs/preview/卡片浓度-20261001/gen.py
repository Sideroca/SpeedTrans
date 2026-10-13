# -*- coding: utf-8 -*-
"""闪译 · 设置页「卡片浓度」对照 —— HTML 生成器（分段输出友好）"""
import json, os
BASE = os.path.dirname(os.path.abspath(__file__))
P = json.load(open("/workspace/夕汀前端规范/palettes.json"))["palettes"]
def pal(i): return next(p for p in P if p["id"] == i)
def hx(h): return tuple(int(h[i:i+2], 16) for i in (1, 3, 5))
def rgba(h, a): r, g, b = hx(h); return "rgba(%d,%d,%d,%.4f)" % (r, g, b, a)

T = pal("tea"); C = T["colors"]
CARD, ACCENT, TEXT, STROKE = C["card"], C["accent"], C["text"], C["cardStroke"]
D = 3.5; S = 3.77

def chip(label, sel, a):
    fill = rgba(ACCENT if sel else CARD, a); line = rgba(STROKE, a)
    return ('<div class="chip" style="background:%s;box-shadow:inset 0 0 0 1.5px %s;color:%s;'
            'font-weight:%s">%s</div>' % (fill, line, "#FFFFFF" if sel else TEXT,
                                          "700" if sel else "400", label))

def chips(a, sa=None):
    sa = a if sa is None else sa
    return chip("现代经典", 0, a) + chip("🏮 中国传统色", 0, a) + chip("属性设置", 1, sa)

def chip_final(label, sel, a, ta):
    """定稿口径：填充=浓度（选中保底30%）· 描边=浓度 · 文字=半跟"""
    base = (255, 255, 255) if sel else hx(TEXT)
    col = "rgba(%d,%d,%d,%.4f)" % (base[0], base[1], base[2], ta)
    return ('<div class="chip" style="background:%s;box-shadow:inset 0 0 0 1.5px %s;color:%s;'
            'font-weight:%s">%s</div>'
            % (rgba(ACCENT if sel else CARD, a), rgba(STROKE, a), col,
               "700" if sel else "400", label))

def chips_final(c):
    ta = (1 + c) / 2
    return (chip_final("现代经典", 0, c, ta) + chip_final("🏮 中国传统色", 0, c, ta)
            + chip_final("属性设置", 1, max(c, .30), ta))

def rowof(pid, sel, a):
    p = pal(pid); c = p["colors"]
    segs = "".join('<i style="flex:%s;background:%s"></i>' % (w, col)
                   for col, w in ((c["accent"], .5), (c["card"], .25), (c["bg"], .25)))
    nm = ('color:%s;font-weight:700' % c["accent"]) if sel else ('color:%s' % c["text"])
    return ('<div class="trow" style="background:%s;box-shadow:inset 0 0 0 1.5px %s">'
            '<div class="tstrip">%s</div><div class="tname" style="%s">%s</div></div>'
            % (rgba(c["barBg"] if sel else c["card"], a),
               rgba(c["accent"] if sel else c["cardStroke"], a), segs, nm, p["name"]))

PICK = [("tea", 1), ("song_yingqing", 0), ("jianzhan_wujin", 0)]
LEVELS = (1.0, .5, .1)

def striph(html, tag, h=300):
    return ('<div class="sbox"><div class="strip" style="background-image:url(wp.png);height:%dpx">'
            '%s<div class="tag">%s</div></div></div>' % (h, html, tag))

S = []
def sec(sid, title, note, body):
    S.append('<div class="sec" id="%s"><div class="st">%s</div><div class="sn">%s</div>%s</div>'
             % (sid, title, note, body))

sec("s1", "现状（真机截图裁切 · 设置页浓度 51%）",
    "三个分类按钮完全不受浓度影响 —— 这就是问题本身",
    '<div class="sbox"><img class="real" src="now.png"></div>')

sec("s2", "方案 A｜完全跟随浓度", "填充与描边同乘浓度，选中态一并变透",
    "".join(striph(chips(c), "%d%%" % (c*100)) for c in LEVELS))

sec("s3", "方案 B-1｜选中态「保底 30%」",
    "选中 alpha = max(浓度, 30%)　　浓度 10% 时选中仍留 30%",
    "".join(striph(chips(c, max(c, .30)), "%d%%" % (c*100)) for c in LEVELS))

sec("s4", "方案 B-2｜选中态「只掉 30% 降幅」",
    "选中 alpha = 1 − 0.30×(1−浓度)　　浓度 10% 时选中留 73%",
    "".join(striph(chips(c, 1 - .30*(1-c)), "%d%%" % (c*100)) for c in LEVELS))

sec("s7", "★ 定稿方案｜B-1 保底 30% + 文字半跟",
    "填充=浓度（选中 max(浓度,30%)）· 描边=浓度 · 文字=(1+浓度)/2 → 100% / 75% / 55%",
    "".join(striph(chips_final(c), "%d%%" % (c*100)) for c in LEVELS))

sec("s5", "附｜主题色卡行（本次不改）",
    "色卡行当时同样没吃浓度；此图是「若修」的观感，供日后定夺",
    "".join(striph("".join(rowof(p, s, c) for p, s in PICK), "%d%%" % (c*100), h=575)
            for c in LEVELS))

sec("s6", "结论与数值证据", "同一次截屏（设置页浓度 = 51%）内的真机实测像素",
    '<div class="ev">'
    '<div class="row"><b>现代经典</b><span>填充实测 <code>#FEFEFE</code>（通道波动 0.1）</span>'
    '<i class="bad">＝源码 card #FFFFFF，100% 不透明</i></div>'
    '<div class="row"><b>属性设置</b><span>填充实测 <code>#789261</code>（通道波动 0.2）</span>'
    '<i class="bad">＝源码 accent #789262，100% 不透明</i></div>'
    '<div class="row"><b>同页大卡片</b><span>空隙实测 <code>#AFAFAE</code> / <code>#CFCFC9</code></span>'
    '<i class="ok">被壁纸稀释 → 51% 已生效</i></div>'
    '<div class="fn">病根：<code>SettingsActivity.kt</code> → <code>selectThemeCategory()</code> 内局部'
    ' <code>style()</code> 未过 <code>fadedCard()</code>；且浓度滑条回调从不重刷分类按钮。</div></div>')

HTML = """<!DOCTYPE html><html><head><meta charset="utf-8"><style>
*{box-sizing:border-box;margin:0;padding:0}
body{background:#14161a;font-family:"MiSans","Noto Sans CJK SC",sans-serif;padding:26px;color:#dfe4ea;width:1520px}
h1{font-size:29px;color:#fff}
.sub{font-size:15px;color:#8b93a1;line-height:1.9;margin:8px 0 24px}
.sec{margin-bottom:28px}
.st{font-size:20px;color:#fff;margin-bottom:3px}
.sn{font-size:14px;color:#8b93a1;margin-bottom:12px}
.sbox{margin-bottom:12px}
.strip{position:relative;width:1440px;background-size:cover;background-position:center;
       border-radius:10px;overflow:hidden;display:flex;align-items:center;gap:35px;padding-left:90px}
.tag{position:absolute;right:14px;top:12px;background:rgba(10,12,16,.66);color:#fff;
     font-size:19px;padding:5px 13px;border-radius:8px}
.chip{height:112px;border-radius:56px;padding:0 49px;display:flex;align-items:center;
      font-size:49px;line-height:1;white-space:nowrap}
.trow{width:1300px;height:175px;border-radius:42px;display:flex;align-items:center;padding:0 35px}
.tstrip{width:336px;height:105px;border-radius:21px;overflow:hidden;display:flex}
.tname{font-size:49px;margin-left:35px}
img.real{display:block;border-radius:10px;width:1440px}
.strip:has(.trow){flex-direction:column;align-items:stretch;justify-content:center;gap:14px;padding:0 70px}
.ev{width:1440px;background:#1b1e24;border:1px solid #2a2f38;border-radius:12px;padding:20px 24px}
.ev .row{display:flex;align-items:baseline;font-size:19px;color:#cfd6e0;padding:7px 0}
.ev .row b{width:150px;color:#fff}
.ev .row span{width:520px;color:#9aa3b2}
.ev .row i{font-style:normal;font-weight:700}
.ev .bad{color:#ff8a6b}
.ev .ok{color:#7fd18a}
.ev .fn{margin-top:12px;padding-top:12px;border-top:1px solid #2a2f38;font-size:17px;line-height:1.9;color:#9aa3b2}
.ev code{background:#242a33;color:#dfe4ea;padding:1px 6px;border-radius:5px;font-size:16px}
</style></head><body>
<h1>闪译 · 设置页「卡片浓度」对照</h1>
<div class="sub">渲染图（合成预览，非真机截图）｜真实底色＝真机截图裁切的壁纸｜配色＝竹青 tea 实测值｜几何＝真机像素实测</div>
%s
</body></html>""" % "".join(S)

open(os.path.join(BASE, "index.html"), "w").write(HTML)
print("html ok")
