#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从《夕汀前端规范》palettes.json **生成** ThemeEngine.kt 的调色板段（唯一数值源）。

为什么要有它：规范 C1 要求"只留一份机器可读数值文件"，代码从它生成 / 校验，
避免"规范一套、代码一套"（闪译正是这么漂的：20 套传统暗色还停在旧的 OLED 档）。

用法：
  python3 tools/gen_palettes.py            # 打印生成的 Kotlin 段（写到 stdout）
  python3 tools/gen_palettes.py --write    # 就地改写 ThemeEngine.kt 的调色板段
  python3 tools/gen_palettes.py --check    # 只比对，不写；有差异则退出码 1

派生色（⑭/⑮）来源：玲珑调音 `Palettes.kt` 的 derive 表（=规范 14.3/14.4 的落地），
sel（选取色）按 15.2 公式现算：色相不动、彩度夹 0.55~0.92、明度 0.44。
"""
import json
import pathlib
import re
import sys

SPEC_PATH = pathlib.Path("/workspace/夕汀前端规范/palettes.json")
ENGINE = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/java/com/speedtrans/app/theme/ThemeEngine.kt"
BALL = pathlib.Path("/workspace/PrimaTune/app/src/main/java/com/sideroca/voicetuner/Palettes.kt")

START = "    val modernLight = listOf("
GEN_HEAD = "    // \u26a0\ufe0f \u672c\u6bb5\u7531"   # 生成段起点标记（写入后仍能定位）
END = "    val palettes: List<Palette> = modernLight + modernDark + chineseLight + chineseNew + chineseDark"

SECTIONS = [("modernLight", "val modernLight"), ("modernDark", "val modernDark"),
            ("chineseLight", "val chineseLight"), ("chineseNew", "val chineseNew"),
            ("chineseDark", "val chineseDark")]


def parse_derive():
    """从玲珑调音 Palettes.kt 抓 derive 表：id -> (gen, ball, genText, spark, sparkBg)"""
    s = BALL.read_text(encoding="utf-8")
    blk = s[s.index("val derive"):]
    blk = blk[:blk.index("\n    )")]
    out = {}
    for m in re.finditer(r'"([^"]+)"\s*to\s*Deriv\((.*)\)\s*,?\s*$', blk, re.M):
        vals = [v.strip() for v in m.group(2).split(",") if v.strip()]
        ints = []
        for v in vals:
            mm = re.search(r"0x([0-9A-Fa-f]+)", v)
            ints.append(int(mm.group(1), 16) if mm else 0)
        while len(ints) < 5:
            ints.append(0)
        out[m.group(1)] = tuple(ints)
    return out


def hsl_of(rgb):
    r, g, b = [(v / 255.0) for v in rgb]
    mx, mn = max(r, g, b), min(r, g, b)
    l = (mx + mn) / 2
    if mx == mn:
        return 0.0, 0.0, l
    d = mx - mn
    s = d / (2 - mx - mn) if l > 0.5 else d / (mx + mn)
    if mx == r:
        h = ((g - b) / d) % 6
    elif mx == g:
        h = (b - r) / d + 2
    else:
        h = (r - g) / d + 4
    return h * 60, s, l


def rgb_of(h, s, l):
    c = (1 - abs(2 * l - 1)) * s
    x = c * (1 - abs((h / 60) % 2 - 1))
    m = l - c / 2
    if h < 60:   r, g, b = c, x, 0
    elif h < 120: r, g, b = x, c, 0
    elif h < 180: r, g, b = 0, c, x
    elif h < 240: r, g, b = 0, x, c
    elif h < 300: r, g, b = x, 0, c
    else:        r, g, b = c, 0, x
    return tuple(int(round((v + m) * 255)) for v in (r, g, b))


def sel_of(accent):
    """⑮.2 「选取色」= 中间调：色相不动、彩度夹 0.55~0.92、明度 0.44"""
    h, s, _ = hsl_of(((accent >> 16) & 0xFF, (accent >> 8) & 0xFF, accent & 0xFF))
    return rgb_of(h, min(max(s, 0.55), 0.92), 0.44)


def hexs(v):
    # 注意：Kotlin 里 0xFFxxxxxx 超过 Int 范围会被当成 Long → 必须套 c(...) 转 Int（沿用原文件的写法）
    return "c(0xFF%06X)" % v


def gen_kotlin():
    spec = json.loads(SPEC_PATH.read_text(encoding="utf-8"))["palettes"]
    deriv = parse_derive()
    lines = [
        "    // ⚠️ 本段由 `tools/gen_palettes.py` 从《夕汀前端规范》palettes.json **生成**（唯一数值源），",
        "    //    不要手改；改了跑 `python3 tools/gen_palettes.py --check` 会报差异。",
        "    //    派生色（生成/球/字色 → ⑭；选取色 → ⑮.2）：只有 26 套浅色传统色有处方，其余回落 accent。",
    ]
    for sec, decl in SECTIONS:
        items = [p for p in spec if p["section"] == sec]
        lines.append(f"    {decl} = listOf(")
        for p in items:
            c = p["colors"]
            sh, ef = p["shape"], p["effects"]
            head = (f'        Palette("{p["id"]}", "{p["name"]}", "{p["group"]}", '
                    f'{str(p["isDark"]).lower()},\n'
                    f'            {hexs(int(c["bg"][1:], 16))}, {hexs(int(c["card"][1:], 16))}, '
                    f'{hexs(int(c["accent"][1:], 16))}, {hexs(int(c["text"][1:], 16))}, '
                    f'{hexs(int(c["subText"][1:], 16))},\n'
                    f'            {hexs(int(c["panelBg"][1:], 16))}, {hexs(int(c["panelText"][1:], 16))}, '
                    f'{hexs(int(c["panelSub"][1:], 16))},\n'
                    f'            barBg = {hexs(int(c["barBg"][1:], 16))}, barText = {hexs(int(c["barText"][1:], 16))},')
            tail = (f'\n            cardStroke = {hexs(int(c["cardStroke"][1:], 16))}, elev = {ef["elev"]}, '
                    f'cardRadius = {sh["cardRadius"]}, btnRadius = {sh["btnRadius"]}, '
                    f'solidBtn = {str(ef["solidBtn"]).lower()}')
            ids = []
            d = deriv.get(p["id"])
            if d:
                g, b, gt, sp, spb = d
                ids.append(f'gen = {hexs(g & 0xFFFFFF)}')
                ids.append(f'ball = {hexs(b & 0xFFFFFF)}')
                ids.append(f'genText = {hexs((gt & 0xFFFFFF) if gt else 0xFFFFFF)}')   # ⑮.1 统一白字
                sel = sel_of(int(c["accent"][1:], 16))
                ids.append(f'sel = {hexs((sel[0] << 16) | (sel[1] << 8) | sel[2])}')
                if sp:
                    ids.append(f'spark = {hexs(sp & 0xFFFFFF)}')
                if spb:
                    ids.append(f'sparkBg = {hexs(spb & 0xFFFFFF)}')
            body = head + tail
            if ids:
                body += ",\n            " + ", ".join(ids)
            lines.append(body + "),")
        lines.append("    )")
        lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def main():
    kotlin = gen_kotlin()
    def region(s):
        a = s.find(GEN_HEAD)
        if a < 0:
            a = s.index(START)
        return a, s.index(END)

    if "--write" in sys.argv:
        s = ENGINE.read_text(encoding="utf-8")
        a, b = region(s)
        ENGINE.write_text(s[:a] + kotlin + "\n" + s[b:], encoding="utf-8")
        print("已写入", ENGINE)
        return 0
    if "--check" in sys.argv:
        s = ENGINE.read_text(encoding="utf-8")
        a, b = region(s)
        cur = s[a:b]
        same = cur.strip() == kotlin.strip()
        print("一致 ✓" if same else "**有差异**：代码与 palettes.json 不同步")
        return 0 if same else 1
    sys.stdout.write(kotlin)
    return 0


if __name__ == "__main__":
    sys.exit(main())
