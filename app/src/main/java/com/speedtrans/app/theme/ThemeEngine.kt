package com.speedtrans.app.theme

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

/**
 * 主题引擎 v3.2：24 套配色图书馆。
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
    val solidBtn: Boolean = true
)

object ThemeEngine {

    private fun c(v: Long) = v.toInt()

    // ============ 现代经典 · 浅色（9） ============
    val modernLight = listOf(
        Palette("deepseek", "深海蓝", "modern", false,
            c(0xFFF7F8FA), c(0xFFFFFFFF), c(0xFF4D6BFE), c(0xFF1F2329), c(0xFF828A9B),
            c(0xFFFFFFFF), c(0xFF1F2329), c(0xFF828A9B),
            barBg = c(0xFFF0C239), barText = c(0xFF1F2329),
            cardStroke = c(0xFFE3E7EF), elev = 4),
        Palette("ios", "iOS 经典", "modern", false,
            c(0xFFF2F2F7), c(0xFFFFFFFF), c(0xFF007AFF), c(0xFF000000), c(0xFF8E8E93),
            c(0xFFFFFFFF), c(0xFF000000), c(0xFF8E8E93),
            barBg = c(0xFFED5736), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFC6C6C8), elev = 0, cardRadius = 10, btnRadius = 10),
        Palette("kimi", "月白紫", "modern", false,
            c(0xFFFFFFFF), c(0xFFF7F9FC), c(0xFF007CFF), c(0xFF002F5B), c(0xFF8A99AC),
            c(0xFFFFFFFF), c(0xFF002F5B), c(0xFF8A99AC),
            barBg = c(0xFF9D2933), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFE3E9F4), elev = 4),
        Palette("qq", "QQ 经典蓝", "modern", false,
            c(0xFFF0F4F8), c(0xFFFFFFFF), c(0xFF12B7F5), c(0xFF1A1A1A), c(0xFF86909C),
            c(0xFFFFFFFF), c(0xFF1A1A1A), c(0xFF86909C),
            barBg = c(0xFFF0C239), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFE1E8F0), elev = 4),
        Palette("notion", "Notion 极简", "modern", false,
            c(0xFFFFFFFF), c(0xFFF7F6F3), c(0xFF2EAADC), c(0xFF37352F), c(0xFF9B9A97),
            c(0xFFFFFFFF), c(0xFF37352F), c(0xFF9B9A97),
            barBg = c(0xFF789262), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFE9E7E1), elev = 0, cardRadius = 6, btnRadius = 6),
        Palette("chatgpt", "ChatGPT 极简", "modern", false,
            c(0xFFFFFFFF), c(0xFFF7F7F8), c(0xFF10A37F), c(0xFF0D0D0D), c(0xFF8E8EA0),
            c(0xFFFFFFFF), c(0xFF0D0D0D), c(0xFF8E8EA0),
            barBg = c(0xFFD6ECF0), barText = c(0xFF0D0D0D),
            cardStroke = c(0xFFECECF1), elev = 0, cardRadius = 16, btnRadius = 24),
        Palette("claude", "Claude 米橙", "modern", false,
            c(0xFFFAF9F5), c(0xFFF0EEE6), c(0xFFD97757), c(0xFF3D3929), c(0xFF919086),
            c(0xFFFAF9F5), c(0xFF3D3929), c(0xFF919086),
            barBg = c(0xFF1A2847), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFE5E1D6), elev = 0, solidBtn = false),
        Palette("perplexity", "Perplexity 青", "modern", false,
            c(0xFFF3F3EE), c(0xFFFCFCF9), c(0xFF20808D), c(0xFF13343B), c(0xFF7A8B8F),
            c(0xFFFCFCF9), c(0xFF13343B), c(0xFF7A8B8F),
            barBg = c(0xFFD6ECF0), barText = c(0xFF13343B),
            cardStroke = c(0xFFE4E4DC), elev = 4),
        Palette("cat_latte", "Catppuccin 拿铁", "modern", false,
            c(0xFFEFF1F5), c(0xFFE6E9EF), c(0xFF1E66F5), c(0xFF4C4F69), c(0xFF7C7F93),
            c(0xFFFFFFFF), c(0xFF4C4F69), c(0xFF8C8FA3),
            barBg = c(0xFF789262), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFBCC0CC), elev = 0)
    )

    // ============ 现代经典 · 暗色（9） ============
    val modernDark = listOf(
        Palette("github", "GitHub 夜", "modern", true,
            c(0xFF0D1117), c(0xFF161B22), c(0xFF2F81F7), c(0xFFE6EDF3), c(0xFF8B949E),
            c(0xFF161B22), c(0xFFE6EDF3), c(0xFF8B949E),
            barBg = c(0xFFD6ECF0), barText = c(0xFF0D1117),
            cardStroke = c(0xFF30363D), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("discord", "Discord 夜", "modern", true,
            c(0xFF313338), c(0xFF2B2D31), c(0xFF5865F2), c(0xFFDBDEE1), c(0xFF949BA4),
            c(0xFF2B2D31), c(0xFFDBDEE1), c(0xFF949BA4),
            barBg = c(0xFFF0C239), barText = c(0xFF1A1B26),
            cardStroke = c(0xFF3F4147), elev = 0),
        Palette("cyber", "赛博 2077", "modern", true,
            c(0xFF0D0D10), c(0xFF16161A), c(0xFFFCEE0A), c(0xFFF2F2EE), c(0xFF9A9A8F),
            c(0xFF101014), c(0xFFF2F2EE), c(0xFF8F8F86),
            barBg = c(0xFF1A2847), barText = c(0xFFF0C239),
            cardStroke = c(0xFF33321E), elev = 0, cardRadius = 6, btnRadius = 4),
        Palette("violet", "紫电夜", "modern", true,
            c(0xFF170F2B), c(0xFF221742), c(0xFF9C5CFF), c(0xFFF1EAFE), c(0xFF9E90C4),
            c(0xFF221742), c(0xFFF1EAFE), c(0xFF9E90C4),
            barBg = c(0xFFF0C239), barText = c(0xFF221742),
            cardStroke = c(0xFF33265A), elev = 0),
        Palette("cat_mocha", "Catppuccin 摩卡", "modern", true,
            c(0xFF1E1E2E), c(0xFF313244), c(0xFF89B4FA), c(0xFFCDD6F4), c(0xFFA6ADC8),
            c(0xFF181825), c(0xFFCDD6F4), c(0xFFA6ADC8),
            barBg = c(0xFF789262), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF45475A), elev = 0),
        Palette("nord", "Nord 北欧", "modern", true,
            c(0xFF2E3440), c(0xFF3B4252), c(0xFF88C0D0), c(0xFFECEFF4), c(0xFFD8DEE9),
            c(0xFF2E3440), c(0xFFECEFF4), c(0xFF93A1B5),
            barBg = c(0xFFD6ECF0), barText = c(0xFF2E3440),
            cardStroke = c(0xFF4C566A), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("dracula", "Dracula 霓虹", "modern", true,
            c(0xFF282A36), c(0xFF44475A), c(0xFFBD93F9), c(0xFFF8F8F2), c(0xFF9AA0C0),
            c(0xFF21222C), c(0xFFF8F8F2), c(0xFF9AA0C0),
            barBg = c(0xFFF0C239), barText = c(0xFF282A36),
            cardStroke = c(0xFF6272A4), elev = 0),
        Palette("tokyo", "Tokyo Night", "modern", true,
            c(0xFF1A1B26), c(0xFF24283B), c(0xFF7AA2F7), c(0xFFC0CAF5), c(0xFF7982A9),
            c(0xFF16161E), c(0xFFC0CAF5), c(0xFF7982A9),
            barBg = c(0xFFF0C239), barText = c(0xFF1A1B26),
            cardStroke = c(0xFF292E42), elev = 0),
        Palette("ds", "死亡搁浅 · 冷蓝", "modern", true,
            c(0xFF1E262E), c(0xFF262F38), c(0xFF7FB4D9), c(0xFFC8D4DC), c(0xFF7A8A96),
            c(0xFF222B33), c(0xFFC8D4DC), c(0xFF7A8A96),
            barBg = c(0xFF9D2933), barText = c(0xFFF2F3F7),
            cardStroke = c(0xFF3A465E), elev = 0,
            cardRadius = 6, btnRadius = 6, solidBtn = false)
    )

    // ============ 中国传统色（4，Chinese Color Atlas 官方调色板） ============
    val chineseLight = listOf(
        // 宋代美学：月白留白，天水碧为魂，缃色点缀
        Palette("song", "宋代美学 · 天水碧", "chinese", false,
            c(0xFFD6ECF0), c(0xFFFFFFFF), c(0xFF5AA4AE), c(0xFF33454F), c(0xFF758A99),
            c(0xFFFFFFFF), c(0xFF33454F), c(0xFF758A99),
            barBg = c(0xFFF0C239), barText = c(0xFF33454F),
            cardStroke = c(0xFF9FBFBF), elev = 0, cardRadius = 8, btnRadius = 8),
        // 茶文化：竹青为底意，月白留白，茶褐点缀，禅意
        Palette("tea", "茶文化 · 竹青", "chinese", false,
            c(0xFFEFF3EA), c(0xFFFFFFFF), c(0xFF789262), c(0xFF3B4634), c(0xFF9E8368),
            c(0xFFFFFFFF), c(0xFF3B4634), c(0xFF9E8368),
            barBg = c(0xFFF0C239), barText = c(0xFF3B4634),
            cardStroke = c(0xFFC5D1BC), elev = 0, cardRadius = 8, btnRadius = 8)
    )

    /**
     * 中国传统色 42 套（2026-09-10 新增；天水碧/竹青复用「宋代美学」「茶文化」两套）。
     * 色值：核心色沿用撞色条 44 色库（与中国色/《中国传统色》色名体系对齐）；
     * 搭配：浅色版 = 同色淡染底 + 近白卡 + 核心色按钮；深色版 = 深染底 + 提亮核心色；
     * 极浅核心（甜白/月白/牙白/影青/藕荷/缃色）配古典深色主色（霁蓝/黛蓝/茶叶末/青花/绛紫/赭石）。
     * 排序：浅色在前、深色在后；水墨·朱砂 与 国潮·描金 仍压在最后。
     */
    val chineseNew = listOf(
        Palette("ruyao_tianqing", "天青釉 · 汝窑", "chinese", false,
            c(0xFFF2F6F7), c(0xFFFAFBFC), c(0xFF6F949A), c(0xFF2D363D), c(0xFF54676E),
            c(0xFFFAFBFC), c(0xFF2D363D), c(0xFF54676E),
            barBg = c(0xFF7FA9B0), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFDBE6E8), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("longquan_fenqing", "粉青釉 · 龙泉", "chinese", false,
            c(0xFFF6F9F7), c(0xFFFBFCFC), c(0xFF84998E), c(0xFF333A3E), c(0xFF63716F),
            c(0xFFFBFCFC), c(0xFF333A3E), c(0xFF63716F),
            barBg = c(0xFFA8C3B4), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFE6EEEA), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("longquan_meiziqing", "梅子青 · 龙泉", "chinese", false,
            c(0xFFF0F5F2), c(0xFFF9FBFA), c(0xFF618B6F), c(0xFF2B3536), c(0xFF4E635B),
            c(0xFFF9FBFA), c(0xFF2B3536), c(0xFF4E635B),
            barBg = c(0xFF6F9E7F), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFD6E3DB), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("song_yingqing", "影青 · 宋", "chinese", false,
            c(0xFFFBFDFC), c(0xFFFDFEFE), c(0xFF2E4E8F), c(0xFF3A4045), c(0xFF798283),
            c(0xFFFDFEFE), c(0xFF3A4045), c(0xFF798283),
            barBg = c(0xFFE0F0E8), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFF6FAF8), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("dingyao_yabai", "牙白 · 定窑", "chinese", false,
            c(0xFFFDFDFB), c(0xFFFEFEFD), c(0xFF6E5B3F), c(0xFF3D4044), c(0xFF7F817F),
            c(0xFFFEFEFD), c(0xFF3D4044), c(0xFF7F817F),
            barBg = c(0xFFF2EDDE), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFFBF9F5), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("jianzhan_wujin", "乌金釉 · 建盏", "chinese", true,
            c(0xFF121316), c(0xFF171719), c(0xFF8D8A86), c(0xFFDCDDDE), c(0xFF6F7174),
            c(0xFF171719), c(0xFFDCDDDE), c(0xFF6F7174),
            barBg = c(0xFF3B3630), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF5E5A55), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("jun_tianlan", "钧窑天蓝", "chinese", false,
            c(0xFFF0F3F7), c(0xFFF9FBFC), c(0xFF607D9F), c(0xFF2A323E), c(0xFF4D5D70),
            c(0xFFF9FBFC), c(0xFF2A323E), c(0xFF4D5D70),
            barBg = c(0xFF6E8FB5), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFD6DFEA), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("jun_zihong", "钧窑紫红", "chinese", true,
            c(0xFF1F161D), c(0xFF2D1C24), c(0xFFAF808C), c(0xFFE6DFE3), c(0xFF947A87),
            c(0xFF2D1C24), c(0xFFE6DFE3), c(0xFF947A87),
            barBg = c(0xFF8E4A5B), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFA26A78), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("langyao_hong", "郎窑红 · 清", "chinese", true,
            c(0xFF231015), c(0xFF331217), c(0xFFC16367), c(0xFFE9DADD), c(0xFF9F686F),
            c(0xFF331217), c(0xFFE9DADD), c(0xFF9F686F),
            barBg = c(0xFFA72126), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFB6484D), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("jiangdou_hong", "豇豆红 · 清", "chinese", false,
            c(0xFFF9EEEF), c(0xFFFCF9F9), c(0xFFAC4F58), c(0xFF362B33), c(0xFF6E4951),
            c(0xFFFCF9F9), c(0xFF362B33), c(0xFF6E4951),
            barBg = c(0xFFC45A65), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFEED0D3), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("yanzhi_shui", "胭脂水 · 清", "chinese", false,
            c(0xFFFCF6F6), c(0xFFFEFBFB), c(0xFFB68383), c(0xFF3B363C), c(0xFF7B666A),
            c(0xFFFEFBFB), c(0xFF3B363C), c(0xFF7B666A),
            barBg = c(0xFFE7A6A6), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFF8E6E6), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("ming_tianbai", "甜白 · 明", "chinese", false,
            c(0xFFFEFDFD), c(0xFFFEFEFE), c(0xFF1E3A5F), c(0xFF3D4046), c(0xFF818385),
            c(0xFFFEFEFE), c(0xFF3D4046), c(0xFF818385),
            barBg = c(0xFFF6F3EC), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFFCFBF9), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("ming_jilan", "霁蓝 · 明", "chinese", true,
            c(0xFF0E141E), c(0xFF0F1826), c(0xFF7C8CA2), c(0xFFD8DDE3), c(0xFF627389),
            c(0xFF0F1826), c(0xFFD8DDE3), c(0xFF627389),
            barBg = c(0xFF1E3A5F), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF465D7B), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("qing_chayemo", "茶叶末 · 清", "chinese", true,
            c(0xFF1A1919), c(0xFF24211D), c(0xFF998C78), c(0xFFE2E1E0), c(0xFF86827A),
            c(0xFF24211D), c(0xFFE2E1E0), c(0xFF86827A),
            barBg = c(0xFF6E5B3F), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF887861), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("ming_kongque", "孔雀绿 · 明", "chinese", true,
            c(0xFF0E2121), c(0xFF102D2A), c(0xFF62AD9A), c(0xFFD8E7E5), c(0xFF629791),
            c(0xFF102D2A), c(0xFFD8E7E5), c(0xFF629791),
            barBg = c(0xFF1F8A70), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF479F89), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("ming_fanhong", "矾红 · 明", "chinese", true,
            c(0xFF281116), c(0xFF3A1318), c(0xFFD5676A), c(0xFFECDBDD), c(0xFFAC6B71),
            c(0xFF3A1318), c(0xFFECDBDD), c(0xFFAC6B71),
            barBg = c(0xFFC3272B), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFCD4D51), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("qinghua_lan", "青花钴蓝", "chinese", true,
            c(0xFF101726), c(0xFF141D32), c(0xFF6C83B0), c(0xFFDAE0E9), c(0xFF697C9E),
            c(0xFF141D32), c(0xFFDAE0E9), c(0xFF697C9E),
            barBg = c(0xFF2E4E8F), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF536DA3), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("qing_shanyuhuang", "鳝鱼黄 · 清", "chinese", false,
            c(0xFFF7F4F0), c(0xFFFCFBF9), c(0xFFA1875D), c(0xFF353433), c(0xFF696153),
            c(0xFFFCFBF9), c(0xFF353433), c(0xFF696153),
            barBg = c(0xFFB89A6A), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFEBE2D5), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("qin_xuansè", "玄色 · 秦尚黑", "chinese", true,
            c(0xFF101117), c(0xFF13141A), c(0xFF848488), c(0xFFDADBDE), c(0xFF686D75),
            c(0xFF13141A), c(0xFFDADBDE), c(0xFF686D75),
            barBg = c(0xFF2B2B33), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF515157), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("tang_shiliu", "石榴红 · 唐", "chinese", true,
            c(0xFF2F0C0F), c(0xFF470C0D), c(0xFFF5544C), c(0xFFF2D8D8), c(0xFFC15F5E),
            c(0xFF470C0D), c(0xFFF2D8D8), c(0xFFC15F5E),
            barBg = c(0xFFF20C00), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFF4372D), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("tang_feihong", "绯红 · 唐", "chinese", true,
            c(0xFF271519), c(0xFF3A1A1E), c(0xFFD27A7B), c(0xFFECDEE0), c(0xFFAB777C),
            c(0xFF3A1A1E), c(0xFFECDEE0), c(0xFFAB777C),
            barBg = c(0xFFC04243), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFCB6464), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("tang_zhehuang", "柘黄 · 唐", "chinese", false,
            c(0xFFF9F5EB), c(0xFFFDFBF8), c(0xFFB08834), c(0xFF37342D), c(0xFF6F6142),
            c(0xFFFDFBF8), c(0xFF37342D), c(0xFF6F6142),
            barBg = c(0xFFC89B3C), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFEFE3C8), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("gugong_hongqiang", "故宫红墙", "chinese", true,
            c(0xFF1F0F15), c(0xFF2C1117), c(0xFFBC7D82), c(0xFFE5DADD), c(0xFF936770),
            c(0xFF2C1117), c(0xFFE5DADD), c(0xFF936770),
            barBg = c(0xFF8C1F28), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFA0474E), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("yuebai", "月白", "chinese", false,
            c(0xFFFAFDFD), c(0xFFFDFEFE), c(0xFF425066), c(0xFF394046), c(0xFF758086),
            c(0xFFFDFEFE), c(0xFF394046), c(0xFF758086),
            barBg = c(0xFFD6ECF0), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFF3F9FA), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("shilv", "石绿", "chinese", false,
            c(0xFFEEF9F8), c(0xFFF9FCFC), c(0xFF4CABAA), c(0xFF273A40), c(0xFF457175),
            c(0xFFF9FCFC), c(0xFF273A40), c(0xFF457175),
            barBg = c(0xFF57C3C2), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFCFEEED), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("shiqing", "石青", "chinese", true,
            c(0xFF101929), c(0xFF142038), c(0xFF6C8AC1), c(0xFFDAE1EC), c(0xFF6981A9),
            c(0xFF142038), c(0xFFDAE1EC), c(0xFF6981A9),
            barBg = c(0xFF2E59A7), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF5376B6), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("dailan", "黛蓝", "chinese", true,
            c(0xFF13171F), c(0xFF191E27), c(0xFF7A8493), c(0xFFDCE0E4), c(0xFF727D8C),
            c(0xFF191E27), c(0xFFDCE0E4), c(0xFF727D8C),
            barBg = c(0xFF425066), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF646F81), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("yaqing", "鸦青", "chinese", true,
            c(0xFF13171B), c(0xFF191D22), c(0xFF7A8184), c(0xFFDCDFE2), c(0xFF727B82),
            c(0xFF191D22), c(0xFFDCDFE2), c(0xFF727B82),
            barBg = c(0xFF424C50), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF646C6F), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("xuanqing", "玄青", "chinese", true,
            c(0xFF13141B), c(0xFF181821), c(0xFF8E8D98), c(0xFFDCDDE1), c(0xFF707482),
            c(0xFF181821), c(0xFFDCDDE1), c(0xFF707482),
            barBg = c(0xFF3D3B4F), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF5F5E6E), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("jiangzi", "绛紫", "chinese", true,
            c(0xFF1F151C), c(0xFF2C1B23), c(0xFFAE7B88), c(0xFFE5DEE2), c(0xFF937785),
            c(0xFF2C1B23), c(0xFFE5DEE2), c(0xFF937785),
            barBg = c(0xFF8C4356), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFA06474), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("ouhe", "藕荷", "chinese", false,
            c(0xFFFCF9FA), c(0xFFFEFDFD), c(0xFFA08B92), c(0xFF3B3A42), c(0xFF7A727A),
            c(0xFFFEFDFD), c(0xFF3B3A42), c(0xFF7A727A),
            barBg = c(0xFFE4C6D0), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFF7EFF1), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("taohong", "桃红", "chinese", false,
            c(0xFFFDF6F8), c(0xFFFEFBFC), c(0xFFC08391), c(0xFF3D363E), c(0xFF806671),
            c(0xFFFEFBFC), c(0xFF3D363E), c(0xFF806671),
            barBg = c(0xFFF4A7B9), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFFBE6EB), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("haitang_hong", "海棠红", "chinese", false,
            c(0xFFFBEEF0), c(0xFFFDF9F9), c(0xFFC04F5E), c(0xFF3A2B33), c(0xFF774954),
            c(0xFFFDF9F9), c(0xFF3A2B33), c(0xFF774954),
            barBg = c(0xFFDB5A6B), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFF4D0D5), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("zhusha", "朱砂", "chinese", false,
            c(0xFFFFEDE5), c(0xFFFFF8F6), c(0xFFE04200), c(0xFF3F2924), c(0xFF84432B),
            c(0xFFFFF8F6), c(0xFF3F2924), c(0xFF84432B),
            barBg = c(0xFFFF4C00), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFFFCCB7), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("xiangse", "缃色", "chinese", false,
            c(0xFFFDF8EB), c(0xFFFEFCF8), c(0xFFBD992D), c(0xFF3D3A2C), c(0xFF7F7041),
            c(0xFFFEFCF8), c(0xFF3D3A2C), c(0xFF7F7041),
            barBg = c(0xFFF0C239), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFFAEDC7), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("chijin", "赤金", "chinese", false,
            c(0xFFFDF8EC), c(0xFFFEFCF8), c(0xFFBE9636), c(0xFF3D392E), c(0xFF7F6F45),
            c(0xFFFEFCF8), c(0xFF3D392E), c(0xFF7F6F45),
            barBg = c(0xFFF2BE45), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFFBECCA), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("zheshi", "赭石", "chinese", true,
            c(0xFF211818), c(0xFF2E1F1C), c(0xFFB48874), c(0xFFE6E0DF), c(0xFF977F78),
            c(0xFF2E1F1C), c(0xFFE6E0DF), c(0xFF977F78),
            barBg = c(0xFF955539), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFA8735C), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("tanse", "檀", "chinese", false,
            c(0xFFF7F0EF), c(0xFFFCF9F9), c(0xFF9D5F55), c(0xFF342E32), c(0xFF675050),
            c(0xFFFCF9F9), c(0xFF342E32), c(0xFF675050),
            barBg = c(0xFFB36D61), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFE9D6D2), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("wanse", "绾", "chinese", false,
            c(0xFFF6F2F1), c(0xFFFBFAFA), c(0xFF947166), c(0xFF333135), c(0xFF645857),
            c(0xFFFBFAFA), c(0xFF333135), c(0xFF645857),
            barBg = c(0xFFA98175), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFE6DBD8), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("hupo", "琥珀", "chinese", false,
            c(0xFFF9F0E9), c(0xFFFDF9F7), c(0xFFB15C1F), c(0xFF372D2A), c(0xFF704E39),
            c(0xFFFDF9F7), c(0xFF372D2A), c(0xFF704E39),
            barBg = c(0xFFCA6924), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFF0D5C1), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("yanxia", "烟霞", "chinese", false,
            c(0xFFFBF6F7), c(0xFFFDFBFC), c(0xFFB48A93), c(0xFF39363D), c(0xFF76666E),
            c(0xFFFDFBFC), c(0xFF39363D), c(0xFF76666E),
            barBg = c(0xFFD8A7B1), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFF4E6E9), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("ailv", "艾绿", "chinese", false,
            c(0xFFF6F8F5), c(0xFFFBFCFB), c(0xFF8B9F85), c(0xFF33393B), c(0xFF636F68),
            c(0xFFFBFCFB), c(0xFF33393B), c(0xFF636F68),
            barBg = c(0xFFA8BFA0), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFE6EDE4), elev = 0, cardRadius = 8, btnRadius = 8),
    )

    val chineseDark = listOf(
        // 水墨网页：墨色打底，月白为字，朱砂为 CTA
        Palette("ink", "水墨 · 朱砂", "chinese", true,
            c(0xFF1A1210), c(0xFF2E211B), c(0xFFFF4C00), c(0xFFD6ECF0), c(0xFF758A99),
            c(0xFF2A1E17), c(0xFFD6ECF0), c(0xFF758A99),
            barBg = c(0xFFD6ECF0), barText = c(0xFF1A1210),
            cardStroke = c(0xFF4A352A), elev = 0),
        // 国潮品牌：玄红为底，描金为点缀
        Palette("guochao", "国潮 · 描金", "chinese", true,
            c(0xFF26120C), c(0xFF331913), c(0xFFFF4C00), c(0xFFD6ECF0), c(0xFF758A99),
            c(0xFF331913), c(0xFFD6ECF0), c(0xFF758A99),
            barBg = c(0xFFEACD76), barText = c(0xFF26120C),
            cardStroke = c(0xFF5A2C1F), elev = 0)
    )

    val palettes: List<Palette> = modernLight + modernDark + chineseLight + chineseNew + chineseDark

    private const val KEY = "theme_id"

    fun current(context: Context): Palette {
        val id = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString(KEY, "tea")!!
        return palettes.firstOrNull { it.id == id } ?: palettes[0]
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

    /**
     * 递归应用主题到视图树（语义色 + 形状语言 + 撞色条）。
     */
    fun applyTo(view: View, pal: Palette, cardIds: Set<Int> = emptySet(), subIds: Set<Int> = emptySet()) {
        val density = view.resources.displayMetrics.density
        when (view) {
            is ViewGroup -> for (i in 0 until view.childCount) applyTo(view.getChildAt(i), pal, cardIds, subIds)
            is Button -> {
                if (view is android.widget.CompoundButton) {
                    view.buttonTintList = ColorStateList.valueOf(pal.accent)
                    view.setTextColor(pal.text)
                } else if (pal.solidBtn) {
                    view.backgroundTintList = ColorStateList.valueOf(pal.accent)
                    view.setTextColor(if (Color.luminance(pal.accent) > 0.5f) 0xFF111111.toInt() else 0xFFFFFFFF.toInt())
                } else {
                    view.background = cardDrawable(pal.card, pal.btnRadius.toFloat(), density, pal.accent)
                    view.setTextColor(pal.accent)
                }
            }
            is EditText -> {
                view.background = cardDrawable(pal.card, 10f, density, pal.cardStroke)
                view.setTextColor(pal.text)
                view.setHintTextColor(pal.subText)
            }
            is TextView -> {
                // 白名单制：只有明确标记的才上卡片背景，说明文字保持纯文字
                if (view.id in cardIds) {
                    view.background = cardDrawable(pal.card, pal.cardRadius.toFloat(), density, pal.cardStroke)
                }
                view.setTextColor(when (view.id) {
                    in cardIds -> pal.text
                    in subIds -> pal.subText
                    else -> pal.text
                })
            }
        }
    }
}
