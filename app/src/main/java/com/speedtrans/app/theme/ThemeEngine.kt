package com.speedtrans.app.theme

import android.content.Context
import android.graphics.drawable.GradientDrawable

/**
 * 主题引擎 v3.2：64 套配色图书馆（现代经典 18 + 中国传统色 46）。
 * 两大分类：现代经典（各知名软件设计语言）/ 中国传统色（Chinese Color Atlas 官方调色板）。
 * 每套 = 语义色 + 形状语言（圆角/实心描边）+ 质感（描边/投影）+ 撞色条（barBg/barText，传统色对撞）。
 */
data class Palette(
    val id: String,
    val name: String,
    val group: String,    // "modern" = 现代经典 | "chinese" = 中国传统色
    val isDark: Boolean,
    val bg: Int,
    val card: Int,
    val accent: Int,
    val text: Int,
    val subText: Int,
    val panelBg: Int,
    val panelText: Int,
    val panelSub: Int,
    val barBg: Int,       // 撞色条背景（传统色对撞）
    val barText: Int,     // 撞色条文字
    val cardStroke: Int = 0,
    val elev: Int = 0,
    val cardRadius: Int = 12,
    val btnRadius: Int = 12,
    val solidBtn: Boolean = true,
    // ---- 派生色（《夕汀前端规范》⑭/⑮）：只有 26 套浅色传统色有处方，其余 = 0 → 回落 accent ----
    val gen: Int = 0,       // ⑭「生成／主行动」色（accent 提亮提纯）
    val genText: Int = 0,   // ⑮.1 其字色（统一白字）
    val ball: Int = 0,      // ⑭「球」色（同色相明度 +12）→ 滑条滑块等小圆点
    val sel: Int = 0,       // ⑮.2「选取」色（中间调：彩度夹 0.55~0.92、明度 0.44）→ 选中态
    val spark: Int = 0,     // 竹青专属（提示类文字色）
    val sparkBg: Int = 0    // 竹青专属（提示类底色）
) {
    /** ⑭ 是否给了派生处方（决定"主行动按钮 / 选中态"走不走派生色；未列入的配色保持原样） */
    val hasDeriv: Boolean get() = gen != 0
    val genColor: Int get() = if (gen != 0) gen else accent
    val genInk: Int get() = if (genText != 0) genText else 0xFFFFFFFF.toInt()
    val ballColor: Int get() = if (ball != 0) ball else accent
    val selColor: Int get() = if (sel != 0) sel else accent
}

object ThemeEngine {

    private fun c(v: Long) = v.toInt()

    // ============ 现代经典 · 浅色（9） ============
    // ⚠️ 本段由 `tools/gen_palettes.py` 从《夕汀前端规范》palettes.json **生成**（唯一数值源），
    //    不要手改；改了跑 `python3 tools/gen_palettes.py --check` 会报差异。
    //    派生色（生成/球/字色 → ⑭；选取色 → ⑮.2）：只有 26 套浅色传统色有处方，其余回落 accent。
    val modernLight = listOf(
        Palette("deepseek", "深海蓝", "modern", false,
            c(0xFFF7F8FA), c(0xFFFFFFFF), c(0xFF4D6BFE), c(0xFF1F2329), c(0xFF828A9B),
            c(0xFFFFFFFF), c(0xFF1F2329), c(0xFF828A9B),
            barBg = c(0xFFF0C239), barText = c(0xFF1F2329),
            cardStroke = c(0xFFE3E7EF), elev = 4, cardRadius = 12, btnRadius = 12, solidBtn = true),
        Palette("ios", "iOS 经典", "modern", false,
            c(0xFFF2F2F7), c(0xFFFFFFFF), c(0xFF007AFF), c(0xFF000000), c(0xFF8E8E93),
            c(0xFFFFFFFF), c(0xFF000000), c(0xFF8E8E93),
            barBg = c(0xFFED5736), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFC6C6C8), elev = 0, cardRadius = 10, btnRadius = 10, solidBtn = true),
        Palette("kimi", "月白紫", "modern", false,
            c(0xFFFFFFFF), c(0xFFF7F9FC), c(0xFF007CFF), c(0xFF002F5B), c(0xFF8A99AC),
            c(0xFFFFFFFF), c(0xFF002F5B), c(0xFF8A99AC),
            barBg = c(0xFF9D2933), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFE3E9F4), elev = 4, cardRadius = 12, btnRadius = 12, solidBtn = true),
        Palette("qq", "QQ 经典蓝", "modern", false,
            c(0xFFF0F4F8), c(0xFFFFFFFF), c(0xFF12B7F5), c(0xFF1A1A1A), c(0xFF86909C),
            c(0xFFFFFFFF), c(0xFF1A1A1A), c(0xFF86909C),
            barBg = c(0xFFF0C239), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFE1E8F0), elev = 4, cardRadius = 12, btnRadius = 12, solidBtn = true),
        Palette("notion", "Notion 极简", "modern", false,
            c(0xFFFFFFFF), c(0xFFF7F6F3), c(0xFF2EAADC), c(0xFF37352F), c(0xFF9B9A97),
            c(0xFFFFFFFF), c(0xFF37352F), c(0xFF9B9A97),
            barBg = c(0xFF789262), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFE9E7E1), elev = 0, cardRadius = 6, btnRadius = 6, solidBtn = true),
        Palette("chatgpt", "ChatGPT 极简", "modern", false,
            c(0xFFFFFFFF), c(0xFFF7F7F8), c(0xFF10A37F), c(0xFF0D0D0D), c(0xFF8E8EA0),
            c(0xFFFFFFFF), c(0xFF0D0D0D), c(0xFF8E8EA0),
            barBg = c(0xFFD6ECF0), barText = c(0xFF0D0D0D),
            cardStroke = c(0xFFECECF1), elev = 0, cardRadius = 16, btnRadius = 24, solidBtn = true),
        Palette("claude", "Claude 米橙", "modern", false,
            c(0xFFFAF9F5), c(0xFFF0EEE6), c(0xFFD97757), c(0xFF3D3929), c(0xFF919086),
            c(0xFFFAF9F5), c(0xFF3D3929), c(0xFF919086),
            barBg = c(0xFF1A2847), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFE5E1D6), elev = 0, cardRadius = 12, btnRadius = 12, solidBtn = false),
        Palette("perplexity", "Perplexity 青", "modern", false,
            c(0xFFF3F3EE), c(0xFFFCFCF9), c(0xFF20808D), c(0xFF13343B), c(0xFF7A8B8F),
            c(0xFFFCFCF9), c(0xFF13343B), c(0xFF7A8B8F),
            barBg = c(0xFFD6ECF0), barText = c(0xFF13343B),
            cardStroke = c(0xFFE4E4DC), elev = 4, cardRadius = 12, btnRadius = 12, solidBtn = true),
        Palette("cat_latte", "Catppuccin 拿铁", "modern", false,
            c(0xFFEFF1F5), c(0xFFE6E9EF), c(0xFF1E66F5), c(0xFF4C4F69), c(0xFF7C7F93),
            c(0xFFFFFFFF), c(0xFF4C4F69), c(0xFF8C8FA3),
            barBg = c(0xFF789262), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFBCC0CC), elev = 0, cardRadius = 12, btnRadius = 12, solidBtn = true),
    )

    val modernDark = listOf(
        Palette("github", "GitHub 夜", "modern", true,
            c(0xFF0D1117), c(0xFF161B22), c(0xFF2F81F7), c(0xFFE6EDF3), c(0xFF8B949E),
            c(0xFF161B22), c(0xFFE6EDF3), c(0xFF8B949E),
            barBg = c(0xFFD6ECF0), barText = c(0xFF0D1117),
            cardStroke = c(0xFF30363D), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true),
        Palette("discord", "Discord 夜", "modern", true,
            c(0xFF313338), c(0xFF2B2D31), c(0xFF5865F2), c(0xFFDBDEE1), c(0xFF949BA4),
            c(0xFF2B2D31), c(0xFFDBDEE1), c(0xFF949BA4),
            barBg = c(0xFFF0C239), barText = c(0xFF1A1B26),
            cardStroke = c(0xFF3F4147), elev = 0, cardRadius = 12, btnRadius = 12, solidBtn = true),
        Palette("cyber", "赛博 2077", "modern", true,
            c(0xFF0D0D10), c(0xFF16161A), c(0xFFFCEE0A), c(0xFFF2F2EE), c(0xFF9A9A8F),
            c(0xFF101014), c(0xFFF2F2EE), c(0xFF8F8F86),
            barBg = c(0xFF1A2847), barText = c(0xFFF0C239),
            cardStroke = c(0xFF33321E), elev = 0, cardRadius = 6, btnRadius = 4, solidBtn = true),
        Palette("violet", "紫电夜", "modern", true,
            c(0xFF170F2B), c(0xFF221742), c(0xFF9C5CFF), c(0xFFF1EAFE), c(0xFF9E90C4),
            c(0xFF221742), c(0xFFF1EAFE), c(0xFF9E90C4),
            barBg = c(0xFFF0C239), barText = c(0xFF221742),
            cardStroke = c(0xFF33265A), elev = 0, cardRadius = 12, btnRadius = 12, solidBtn = true),
        Palette("cat_mocha", "Catppuccin 摩卡", "modern", true,
            c(0xFF1E1E2E), c(0xFF313244), c(0xFF89B4FA), c(0xFFCDD6F4), c(0xFFA6ADC8),
            c(0xFF181825), c(0xFFCDD6F4), c(0xFFA6ADC8),
            barBg = c(0xFF789262), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF45475A), elev = 0, cardRadius = 12, btnRadius = 12, solidBtn = true),
        Palette("nord", "Nord 北欧", "modern", true,
            c(0xFF2E3440), c(0xFF3B4252), c(0xFF88C0D0), c(0xFFECEFF4), c(0xFFD8DEE9),
            c(0xFF2E3440), c(0xFFECEFF4), c(0xFF93A1B5),
            barBg = c(0xFFD6ECF0), barText = c(0xFF2E3440),
            cardStroke = c(0xFF4C566A), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true),
        Palette("dracula", "Dracula 霓虹", "modern", true,
            c(0xFF282A36), c(0xFF44475A), c(0xFFBD93F9), c(0xFFF8F8F2), c(0xFF9AA0C0),
            c(0xFF21222C), c(0xFFF8F8F2), c(0xFF9AA0C0),
            barBg = c(0xFFF0C239), barText = c(0xFF282A36),
            cardStroke = c(0xFF6272A4), elev = 0, cardRadius = 12, btnRadius = 12, solidBtn = true),
        Palette("tokyo", "Tokyo Night", "modern", true,
            c(0xFF1A1B26), c(0xFF24283B), c(0xFF7AA2F7), c(0xFFC0CAF5), c(0xFF7982A9),
            c(0xFF16161E), c(0xFFC0CAF5), c(0xFF7982A9),
            barBg = c(0xFFF0C239), barText = c(0xFF1A1B26),
            cardStroke = c(0xFF292E42), elev = 0, cardRadius = 12, btnRadius = 12, solidBtn = true),
        Palette("ds", "死亡搁浅 · 冷蓝", "modern", true,
            c(0xFF1E262E), c(0xFF262F38), c(0xFF7FB4D9), c(0xFFC8D4DC), c(0xFF7A8A96),
            c(0xFF222B33), c(0xFFC8D4DC), c(0xFF7A8A96),
            barBg = c(0xFF9D2933), barText = c(0xFFF2F3F7),
            cardStroke = c(0xFF3A465E), elev = 0, cardRadius = 6, btnRadius = 6, solidBtn = false),
    )

    val chineseLight = listOf(
        Palette("song", "宋代美学 · 天水碧", "chinese", false,
            c(0xFFD6ECF0), c(0xFFFFFFFF), c(0xFF5AA4AE), c(0xFF33454F), c(0xFF758A99),
            c(0xFFFFFFFF), c(0xFF33454F), c(0xFF758A99),
            barBg = c(0xFFF0C239), barText = c(0xFF33454F),
            cardStroke = c(0xFF9FBFBF), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true,
            gen = c(0xFF86CBD5), ball = c(0xFFB4DFE4), genText = c(0xFFFFFFFF), sel = c(0xFF329FAE)),
        Palette("tea", "茶文化 · 竹青", "chinese", false,
            c(0xFFEFF3EA), c(0xFFFFFFFF), c(0xFF789262), c(0xFF3B4634), c(0xFF9E8368),
            c(0xFFFFFFFF), c(0xFF3B4634), c(0xFF9E8368),
            barBg = c(0xFFF0C239), barText = c(0xFF3B4634),
            cardStroke = c(0xFFC5D1BC), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true,
            gen = c(0xFF8FC464), ball = c(0xFFB5CEA1), genText = c(0xFFFFFFFF), sel = c(0xFF6BAE32), spark = c(0xFF778D61), sparkBg = c(0xFFDCE2D4)),
    )

    val chineseNew = listOf(
        Palette("ruyao_tianqing", "天青釉 · 汝窑", "chinese", false,
            c(0xFFC7F2F9), c(0xFFF4FDFE), c(0xFF438894), c(0xFF1A2526), c(0xFF4B676B),
            c(0xFFF4FDFE), c(0xFF1A2526), c(0xFF4B676B),
            barBg = c(0xFF7FA9B0), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFA4D3DB), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true,
            gen = c(0xFF86C9D5), ball = c(0xFFB4DDE4), genText = c(0xFFFFFFFF), sel = c(0xFF329CAE)),
        Palette("longquan_fenqing", "粉青釉 · 龙泉", "chinese", false,
            c(0xFFDBF9E9), c(0xFFF5FEF9), c(0xFF439467), c(0xFF1A261F), c(0xFF4B6B59),
            c(0xFFF5FEF9), c(0xFF1A261F), c(0xFF4B6B59),
            barBg = c(0xFFA8C3B4), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFC0DBCC), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true,
            gen = c(0xFF83D8A9), ball = c(0xFFB1E7C9), genText = c(0xFFFFFFFF), sel = c(0xFF32AE69)),
        Palette("longquan_meiziqing", "梅子青 · 龙泉", "chinese", false,
            c(0xFFC7F9D8), c(0xFFF4FEF8), c(0xFF43945E), c(0xFF1A261E), c(0xFF4B6B56),
            c(0xFFF4FEF8), c(0xFF1A261E), c(0xFF4B6B56),
            barBg = c(0xFF6F9E7F), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFA2DBB6), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true,
            gen = c(0xFF83D89F), ball = c(0xFFB1E7C3), genText = c(0xFFFFFFFF), sel = c(0xFF32AE5C)),
        Palette("song_yingqing", "影青 · 宋", "chinese", false,
            c(0xFFF9EDED), c(0xFFFEFAFA), c(0xFF023291), c(0xFF241F1F), c(0xFF6B5C5C),
            c(0xFFFEFAFA), c(0xFF241F1F), c(0xFF6B5C5C),
            barBg = c(0xFFE0F0E8), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFD9C5C5), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true,
            gen = c(0xFF6E8DCC), ball = c(0xFF9BB1DC), genText = c(0xFFFFFFFF), sel = c(0xFF094ED7)),
        Palette("dingyao_yabai", "牙白 · 定窑", "chinese", false,
            c(0xFFF9EDED), c(0xFFFEFAFA), c(0xFF916D37), c(0xFF241F1F), c(0xFF6B5C5C),
            c(0xFFFEFAFA), c(0xFF241F1F), c(0xFF6B5C5C),
            barBg = c(0xFFF2EDDE), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFD9C5C5), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true,
            gen = c(0xFFDDB77E), ball = c(0xFFEAD2AE), genText = c(0xFFFFFFFF), sel = c(0xFFAE7D32)),
        Palette("jianzhan_wujin", "乌金釉 · 建盏", "chinese", true,
            c(0xFF291F10), c(0xFF3A3022), c(0xFFB1A28E), c(0xFFF7F3EC), c(0xFFAB9C87),
            c(0xFF3A3022), c(0xFFF7F3EC), c(0xFFAB9C87),
            barBg = c(0xFFA3BCC7), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFF50432F), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true),
        Palette("jun_tianlan", "钧窑天蓝", "chinese", false,
            c(0xFFC7DEF9), c(0xFFF4F9FE), c(0xFF436894), c(0xFF1A2026), c(0xFF4B5A6B),
            c(0xFFF4F9FE), c(0xFF1A2026), c(0xFF4B5A6B),
            barBg = c(0xFF6E8FB5), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFA2BDDB), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true,
            gen = c(0xFF6291C8), ball = c(0xFF8FB0D8), genText = c(0xFFFFFFFF), sel = c(0xFF326BAE)),
        Palette("jun_zihong", "钧窑紫红", "chinese", true,
            c(0xFF2D1A1F), c(0xFF3E2C30), c(0xFF96719E), c(0xFFFAF1F2), c(0xFFB2969C),
            c(0xFF3E2C30), c(0xFFFAF1F2), c(0xFFB2969C),
            barBg = c(0xFF89D1A0), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFF563D43), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true),
        Palette("langyao_hong", "郎窑红 · 清", "chinese", true,
            c(0xFF2D1B1A), c(0xFF3F2C2B), c(0xFFA97C46), c(0xFFFAF1F0), c(0xFFB39794),
            c(0xFF3F2C2B), c(0xFFFAF1F0), c(0xFFB39794),
            barBg = c(0xFF89D1A0), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFF573E3C), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true),
        Palette("jiangdou_hong", "豇豆红 · 清", "chinese", false,
            c(0xFFF9C7CC), c(0xFFFEF4F5), c(0xFF942430), c(0xFF261A1B), c(0xFF6B4B4E),
            c(0xFFFEF4F5), c(0xFF261A1B), c(0xFF6B4B4E),
            barBg = c(0xFFC45A65), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFDBA2A8), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true,
            gen = c(0xFFD86D78), ball = c(0xFFE59DA5), genText = c(0xFFFFFFFF), sel = c(0xFFB42C3B)),
        Palette("yanzhi_shui", "胭脂水 · 清", "chinese", false,
            c(0xFFF9C7C7), c(0xFFFEF4F4), c(0xFF944343), c(0xFF261A1A), c(0xFF6B4B4B),
            c(0xFFFEF4F4), c(0xFF261A1A), c(0xFF6B4B4B),
            barBg = c(0xFFE7A6A6), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFDBA4A4), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true,
            gen = c(0xFFD86E6E), ball = c(0xFFE59E9E), genText = c(0xFFFFFFFF), sel = c(0xFFAE3232)),
        Palette("ming_tianbai", "甜白 · 明", "chinese", false,
            c(0xFFF9EDED), c(0xFFFEFAFA), c(0xFF013F91), c(0xFF241F1F), c(0xFF6B5C5C),
            c(0xFFFEFAFA), c(0xFF241F1F), c(0xFF6B5C5C),
            barBg = c(0xFFF6F3EC), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFD9C5C5), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true,
            gen = c(0xFF6490C9), ball = c(0xFF92B0D9), genText = c(0xFFFFFFFF), sel = c(0xFF0962D7)),
        Palette("ming_jilan", "霁蓝 · 明", "chinese", true,
            c(0xFF17212F), c(0xFF283240), c(0xFF60A7FF), c(0xFFEFF4FA), c(0xFF91A0B4),
            c(0xFF283240), c(0xFFEFF4FA), c(0xFF91A0B4),
            barBg = c(0xFFE0B027), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFF384658), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true),
        Palette("qing_chayemo", "茶叶末 · 清", "chinese", true,
            c(0xFF271F0F), c(0xFF383121), c(0xFFB4A37E), c(0xFFF6F3EC), c(0xFFA89D87),
            c(0xFF383121), c(0xFFF6F3EC), c(0xFFA89D87),
            barBg = c(0xFF7FC2DE), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFF4E442F), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true),
        Palette("ming_kongque", "孔雀绿 · 明", "chinese", true,
            c(0xFF10251F), c(0xFF233630), c(0xFF00C09B), c(0xFFEDF6F3), c(0xFF8AA69C),
            c(0xFF233630), c(0xFFEDF6F3), c(0xFF8AA69C),
            barBg = c(0xFFFF9687), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFF314B43), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true),
        Palette("ming_fanhong", "矾红 · 明", "chinese", true,
            c(0xFF2D1B19), c(0xFF3F2C2A), c(0xFF997B4B), c(0xFFFAF1F0), c(0xFFB39794),
            c(0xFF3F2C2A), c(0xFFFAF1F0), c(0xFFB39794),
            barBg = c(0xFFDABA70), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFF573E3B), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true),
        Palette("qinghua_lan", "青花钴蓝", "chinese", true,
            c(0xFF19202F), c(0xFF2B3140), c(0xFF78A1FF), c(0xFFF0F3FA), c(0xFF949FB4),
            c(0xFF2B3140), c(0xFFF0F3FA), c(0xFF949FB4),
            barBg = c(0xFFF3A437), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFF3C4559), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true),
        Palette("qing_shanyuhuang", "鳝鱼黄 · 清", "chinese", false,
            c(0xFFF9E6C7), c(0xFFFEFBF4), c(0xFF94723C), c(0xFF26221A), c(0xFF6B5F4B),
            c(0xFFFEFBF4), c(0xFF26221A), c(0xFF6B5F4B),
            barBg = c(0xFFB89A6A), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFDBC5A2), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true,
            gen = c(0xFFDDB87E), ball = c(0xFFEAD3AE), genText = c(0xFFFFFFFF), sel = c(0xFFAE7E32)),
        Palette("qin_xuansè", "玄色 · 秦", "chinese", true,
            c(0xFF1F1E2E), c(0xFF30303F), c(0xFFA3A3B4), c(0xFFF2F3FA), c(0xFF9C9CB3),
            c(0xFF30303F), c(0xFFF2F3FA), c(0xFF9C9CB3),
            barBg = c(0xFFC1B5A8), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFF434258), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true),
        Palette("tang_shiliu", "石榴红 · 唐", "chinese", true,
            c(0xFF2D1B19), c(0xFF3F2C2A), c(0xFFA87C4C), c(0xFFFAF1F0), c(0xFFB39793),
            c(0xFF3F2C2A), c(0xFFFAF1F0), c(0xFFB39793),
            barBg = c(0xFF8AD1A0), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFF573E3B), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true),
        Palette("tang_feihong", "绯红 · 唐", "chinese", true,
            c(0xFF2D1B18), c(0xFF3F2C2A), c(0xFFA47E4E), c(0xFFFAF1F0), c(0xFFB39793),
            c(0xFF3F2C2A), c(0xFFFAF1F0), c(0xFFB39793),
            barBg = c(0xFFE8B275), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFF573E3A), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true),
        Palette("tang_zhehuang", "柘黄 · 唐", "chinese", false,
            c(0xFFF9E9C7), c(0xFFFEFBF4), c(0xFF946503), c(0xFF26221A), c(0xFF6B614B),
            c(0xFFFEFBF4), c(0xFF26221A), c(0xFF6B614B),
            barBg = c(0xFFC89B3C), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFDBC9A2), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true,
            gen = c(0xFFDDBE7E), ball = c(0xFFEAD6AE), genText = c(0xFFFFFFFF), sel = c(0xFFD79509)),
        Palette("gugong_hongqiang", "故宫红墙", "chinese", true,
            c(0xFF2D1B1A), c(0xFF3F2C2B), c(0xFF9E7943), c(0xFFFAF1F0), c(0xFFB39795),
            c(0xFF3F2C2B), c(0xFFFAF1F0), c(0xFFB39795),
            barBg = c(0xFF8AD1A0), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFF573E3C), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true),
        Palette("yuebai", "月白", "chinese", false,
            c(0xFFF9EDED), c(0xFFFEFAFA), c(0xFF416091), c(0xFF241F1F), c(0xFF6B5C5C),
            c(0xFFFEFAFA), c(0xFF241F1F), c(0xFF6B5C5C),
            barBg = c(0xFFD6ECF0), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFD9C5C5), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true,
            gen = c(0xFF698FCA), ball = c(0xFF96B0DA), genText = c(0xFFFFFFFF), sel = c(0xFF3262AE)),
        Palette("shilv", "石绿", "chinese", false,
            c(0xFFC7F9F9), c(0xFFF4FEFE), c(0xFF219493), c(0xFF1A2626), c(0xFF4B6B6B),
            c(0xFFF4FEFE), c(0xFF1A2626), c(0xFF4B6B6B),
            barBg = c(0xFF57C3C2), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFA2DBDB), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true,
            gen = c(0xFF86D5D4), ball = c(0xFFB4E4E4), genText = c(0xFFFFFFFF), sel = c(0xFF29B7B6)),
        Palette("shiqing", "石青", "chinese", true,
            c(0xFF10232B), c(0xFF22353C), c(0xFF00B5E7), c(0xFFEDF5F9), c(0xFF89A3AF),
            c(0xFF22353C), c(0xFFEDF5F9), c(0xFF89A3AF),
            barBg = c(0xFFE0B027), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFF304954), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true),
        Palette("dailan", "黛蓝", "chinese", true,
            c(0xFF18212F), c(0xFF293240), c(0xFF8BA6D1), c(0xFFEFF4FA), c(0xFF929FB4),
            c(0xFF293240), c(0xFFEFF4FA), c(0xFF929FB4),
            barBg = c(0xFFD3B18A), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFF3A4659), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true),
        Palette("yaqing", "鸦青", "chinese", true,
            c(0xFF0F242B), c(0xFF22353C), c(0xFF95A8B0), c(0xFFEDF5F8), c(0xFF88A3AE),
            c(0xFF22353C), c(0xFFEDF5F8), c(0xFF88A3AE),
            barBg = c(0xFFBEB7A7), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFF2F4953), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true),
        Palette("xuanqing", "玄青", "chinese", true,
            c(0xFF201E2E), c(0xFF312F3F), c(0xFFA39FC7), c(0xFFF3F2FA), c(0xFF9D9BB3),
            c(0xFF312F3F), c(0xFFF3F2FA), c(0xFF9D9BB3),
            barBg = c(0xFFC7B690), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFF444257), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true),
        Palette("jiangzi", "绛紫", "chinese", true,
            c(0xFF2D1A1E), c(0xFF3E2C30), c(0xFF9675A0), c(0xFFFAF1F2), c(0xFFB2969B),
            c(0xFF3E2C30), c(0xFFFAF1F2), c(0xFFB2969B),
            barBg = c(0xFF89D1A1), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFF563D42), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true),
        Palette("ouhe", "藕荷", "chinese", false,
            c(0xFFF9EDED), c(0xFFFEFAFA), c(0xFF912340), c(0xFF241F1F), c(0xFF6B5C5C),
            c(0xFFFEFAFA), c(0xFF241F1F), c(0xFF6B5C5C),
            barBg = c(0xFFE4C6D0), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFD9C5C5), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true,
            gen = c(0xFFD86A87), ball = c(0xFFE49BAE), genText = c(0xFFFFFFFF), sel = c(0xFFB52C50)),
        Palette("taohong", "桃红", "chinese", false,
            c(0xFFF9C7D3), c(0xFFFEF4F7), c(0xFF944356), c(0xFF261A1D), c(0xFF6B4B52),
            c(0xFFFEF4F7), c(0xFF261A1D), c(0xFF6B4B52),
            barBg = c(0xFFF4A7B9), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFDBA2B0), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true,
            gen = c(0xFFD86B85), ball = c(0xFFE59CAD), genText = c(0xFFFFFFFF), sel = c(0xFFAE324F)),
        Palette("haitang_hong", "海棠红", "chinese", false,
            c(0xFFF9C7CE), c(0xFFFEF4F6), c(0xFF941A2A), c(0xFF261A1C), c(0xFF6B4B4F),
            c(0xFFFEF4F6), c(0xFF261A1C), c(0xFF6B4B4F),
            barBg = c(0xFFDB5A6B), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFDBA2AA), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true,
            gen = c(0xFFD86D7B), ball = c(0xFFE59DA6), genText = c(0xFFFFFFFF), sel = c(0xFFBF2236)),
        Palette("zhusha", "朱砂", "chinese", false,
            c(0xFFF9D6C7), c(0xFFFEF7F4), c(0xFF942C00), c(0xFF261E1A), c(0xFF6B554B),
            c(0xFFFEF7F4), c(0xFF261E1A), c(0xFF6B554B),
            barBg = c(0xFFFF4C00), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFDBB3A2), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true,
            gen = c(0xFFD0754F), ball = c(0xFFDD9B7F), genText = c(0xFFFFFFFF), sel = c(0xFFD74609)),
        Palette("xiangse", "缃色", "chinese", false,
            c(0xFFF9EDC7), c(0xFFFEFCF4), c(0xFF946F00), c(0xFF26231A), c(0xFF6B634B),
            c(0xFFFEFCF4), c(0xFF26231A), c(0xFF6B634B),
            barBg = c(0xFFF0C239), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFDBCDA2), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true,
            gen = c(0xFFDDC57E), ball = c(0xFFEADBAE), genText = c(0xFFFFFFFF), sel = c(0xFFD7A409)),
        Palette("chijin", "赤金", "chinese", false,
            c(0xFFF9EAC7), c(0xFFFEFBF4), c(0xFF946700), c(0xFF26231A), c(0xFF6B614B),
            c(0xFFFEFBF4), c(0xFF26231A), c(0xFF6B614B),
            barBg = c(0xFFF2BE45), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFDBCAA2), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true,
            gen = c(0xFFDDC07E), ball = c(0xFFEAD8AE), genText = c(0xFFFFFFFF), sel = c(0xFFD79909)),
        Palette("zheshi", "赭石", "chinese", true,
            c(0xFF2D1C15), c(0xFF3E2D26), c(0xFFD1947A), c(0xFFFAF1EE), c(0xFFB2988E),
            c(0xFF3E2D26), c(0xFFFAF1EE), c(0xFFB2988E),
            barBg = c(0xFF5DD38A), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFF563F35), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true),
        Palette("tanse", "檀", "chinese", false,
            c(0xFFF9CFC7), c(0xFFFEF6F4), c(0xFF944335), c(0xFF261C1A), c(0xFF6B504B),
            c(0xFFFEF6F4), c(0xFF261C1A), c(0xFF6B504B),
            barBg = c(0xFFB36D61), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFDBABA2), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true,
            gen = c(0xFFD57260), ball = c(0xFFE29D91), genText = c(0xFFFFFFFF), sel = c(0xFFAE4532)),
        Palette("wanse", "绾", "chinese", false,
            c(0xFFF9D3C7), c(0xFFFEF7F4), c(0xFF945543), c(0xFF261D1A), c(0xFF6B524B),
            c(0xFFFEF7F4), c(0xFF261D1A), c(0xFF6B524B),
            barBg = c(0xFFA98175), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFDBAFA2), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true,
            gen = c(0xFFD37458), ball = c(0xFFE09C89), genText = c(0xFFFFFFFF), sel = c(0xFFAE4E32)),
        Palette("hupo", "琥珀", "chinese", false,
            c(0xFFF9DCC7), c(0xFFFEF9F4), c(0xFF943D00), c(0xFF261F1A), c(0xFF6B584B),
            c(0xFFFEF9F4), c(0xFF261F1A), c(0xFF6B584B),
            barBg = c(0xFFCA6924), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFDBBAA2), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true,
            gen = c(0xFFCC793E), ball = c(0xFFD99A6E), genText = c(0xFFFFFFFF), sel = c(0xFFD75E09)),
        Palette("yanxia", "烟霞", "chinese", false,
            c(0xFFF9CFD7), c(0xFFFEF4F6), c(0xFF944353), c(0xFF261A1D), c(0xFF6B4B52),
            c(0xFFFEF4F6), c(0xFF261A1D), c(0xFF6B4B52),
            barBg = c(0xFFD8A7B1), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFDBAFB8), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true,
            gen = c(0xFFD86B81), ball = c(0xFFE59CAA), genText = c(0xFFFFFFFF), sel = c(0xFFAE324B)),
        Palette("ailv", "艾绿", "chinese", false,
            c(0xFFE3F9DB), c(0xFFF7FEF4), c(0xFF589443), c(0xFF1D261A), c(0xFF536B4B),
            c(0xFFF7FEF4), c(0xFF1D261A), c(0xFF536B4B),
            barBg = c(0xFFA8BFA0), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFC4DBBB), elev = 0, cardRadius = 8, btnRadius = 8, solidBtn = true,
            gen = c(0xFF99D883), ball = c(0xFFBFE7B1), genText = c(0xFFFFFFFF), sel = c(0xFF52AE32)),
    )

    val chineseDark = listOf(
        Palette("ink", "水墨 · 朱砂", "chinese", true,
            c(0xFF2D1C15), c(0xFF3E2D26), c(0xFFD1947B), c(0xFFFAF1EE), c(0xFFB2988E),
            c(0xFF3E2D26), c(0xFFFAF1EE), c(0xFFB2988E),
            barBg = c(0xFF18C9FF), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFF563F36), elev = 0, cardRadius = 12, btnRadius = 12, solidBtn = true),
        Palette("guochao", "国潮 · 描金", "chinese", true,
            c(0xFF2D1C14), c(0xFF3E2D26), c(0xFFD09579), c(0xFFFAF2EE), c(0xFFB1988D),
            c(0xFF3E2D26), c(0xFFFAF2EE), c(0xFFB1988D),
            barBg = c(0xFFE0B027), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFF563F35), elev = 0, cardRadius = 12, btnRadius = 12, solidBtn = true),
    )

    val palettes: List<Palette> = modernLight + modernDark + chineseLight + chineseNew + chineseDark

    private const val KEY = "theme_id"

    fun current(context: Context): Palette {
        val id = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString(KEY, "tea")!!
        return palettes.firstOrNull { it.id == id } ?: palettes[0]
    }

    /** 跟随主题的页面底色：在 bg 上轻染 accent（让不同主题的页面氛围一眼可辨） */
    fun backdrop(pal: Palette): Int {
        val t = if (pal.isDark) 0.15f else 0.10f
        val b = pal.bg
        val f = pal.accent
        val r = ((b shr 16 and 0xFF) * (1 - t) + (f shr 16 and 0xFF) * t).toInt()
        val g = ((b shr 8 and 0xFF) * (1 - t) + (f shr 8 and 0xFF) * t).toInt()
        val bl = ((b and 0xFF) * (1 - t) + (f and 0xFF) * t).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    fun save(context: Context, id: String) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putString(KEY, id).apply()
    }

    fun cardDrawable(
        color: Int,
        radiusDp: Float,
        density: Float,
        strokeColor: Int = 0
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radiusDp * density
        if (strokeColor != 0) setStroke((1 * density).toInt(), strokeColor)
    }

    /** 撞色条：纯平色（定稿自"预览-撞色条中国色-平色"文件；44 色通用、左右上下全均匀） */
    fun barGradient(base: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Float = 1f): GradientDrawable =
        GradientDrawable().apply {
            setColor(base)
            cornerRadius = radiusPx
            if (strokeColor != 0 && strokePx > 0) setStroke(strokePx.toInt().coerceAtLeast(1), strokeColor)
        }

}
