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
     * 中国传统色 42 套（2026-09-10；v2 浓郁版）。
     * 取色法（HSV 提升饱和度）：浅色版 = 玉色底(低饱和高明度) + 近白卡 + 宝石主色；
     * 深色版 = 宝石深底 + 提亮纯色主色；近白核心配古典深色（霁蓝/黛蓝/茶叶末/青花/绛紫/赭石）。
     * 排序：浅色在前、深色在后；水墨·朱砂 与 国潮·描金 压轴。
     */
    val chineseNew = listOf(
        Palette("ruyao_tianqing", "天青釉 · 汝窑", "chinese", false,
            c(0xFFC2F1F9), c(0xFFF2FDFE), c(0xFF57949E), c(0xFF1D2729), c(0xFF4C6B70),
            c(0xFFF2FDFE), c(0xFF1D2729), c(0xFF4C6B70),
            barBg = c(0xFF7FA9B0), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFA4D3DB), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("longquan_fenqing", "粉青釉 · 龙泉", "chinese", false,
            c(0xFFDBF9E8), c(0xFFF2FEF8), c(0xFF579E77), c(0xFF1D2922), c(0xFF4C705C),
            c(0xFFF2FEF8), c(0xFF1D2922), c(0xFF4C705C),
            barBg = c(0xFFA8C3B4), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFC0DBCC), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("longquan_meiziqing", "梅子青 · 龙泉", "chinese", false,
            c(0xFFC2F9D5), c(0xFFF2FEF6), c(0xFF579E6F), c(0xFF1D2921), c(0xFF4C7059),
            c(0xFFF2FEF6), c(0xFF1D2921), c(0xFF4C7059),
            barBg = c(0xFF6F9E7F), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFA1DBB5), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("song_yingqing", "影青 · 宋", "chinese", false,
            c(0xFFF9EBEB), c(0xFFFEF9F9), c(0xFF174299), c(0xFF292525), c(0xFF706363),
            c(0xFFFEF9F9), c(0xFF292525), c(0xFF706363),
            barBg = c(0xFFE0F0E8), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFDBCACA), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("dingyao_yabai", "牙白 · 定窑", "chinese", false,
            c(0xFFF9EBEB), c(0xFFFEF9F9), c(0xFF997847), c(0xFF292525), c(0xFF706363),
            c(0xFFFEF9F9), c(0xFF292525), c(0xFF706363),
            barBg = c(0xFFF2EDDE), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFDBCACA), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("jianzhan_wujin", "乌金釉 · 建盏", "chinese", true,
            c(0xFF332B21), c(0xFF453B30), c(0xFFD1AB7D), c(0xFFF2E7DA), c(0xFFA3937F),
            c(0xFF453B30), c(0xFFF2E7DA), c(0xFFA3937F),
            barBg = c(0xFF3B3630), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF5C4D3C), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("jun_tianlan", "钧窑天蓝", "chinese", false,
            c(0xFFC2DBF9), c(0xFFF2F8FE), c(0xFF4D739E), c(0xFF1D2229), c(0xFF4C5D70),
            c(0xFFF2F8FE), c(0xFF1D2229), c(0xFF4C5D70),
            barBg = c(0xFF6E8FB5), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF9EBADB), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("jun_zihong", "钧窑紫红", "chinese", true,
            c(0xFF33141C), c(0xFF451E28), c(0xFFD15E7B), c(0xFFF2DAE0), c(0xFFA37F88),
            c(0xFF451E28), c(0xFFF2DAE0), c(0xFFA37F88),
            barBg = c(0xFF8E4A5B), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF5C3C44), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("langyao_hong", "郎窑红 · 清", "chinese", true,
            c(0xFF331315), c(0xFF451D1E), c(0xFFD1262C), c(0xFFF2DADB), c(0xFFA37F81),
            c(0xFF451D1E), c(0xFFF2DADB), c(0xFFA37F81),
            barBg = c(0xFFA72126), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF5C3C3D), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("jiangdou_hong", "豇豆红 · 清", "chinese", false,
            c(0xFFF9C2C8), c(0xFFFEF2F3), c(0xFF9E2F3A), c(0xFF291D1E), c(0xFF704C50),
            c(0xFFFEF2F3), c(0xFF291D1E), c(0xFF704C50),
            barBg = c(0xFFC45A65), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFDB9EA4), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("yanzhi_shui", "胭脂水 · 清", "chinese", false,
            c(0xFFF9C2C2), c(0xFFFEF2F2), c(0xFF9E5757), c(0xFF291D1D), c(0xFF704C4C),
            c(0xFFFEF2F2), c(0xFF291D1D), c(0xFF704C4C),
            barBg = c(0xFFE7A6A6), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFDBA4A4), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("ming_tianbai", "甜白 · 明", "chinese", false,
            c(0xFFF9EBEB), c(0xFFFEF9F9), c(0xFF164F99), c(0xFF292525), c(0xFF706363),
            c(0xFFFEF9F9), c(0xFF292525), c(0xFF706363),
            barBg = c(0xFFF6F3EC), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFDBCACA), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("ming_jilan", "霁蓝 · 明", "chinese", true,
            c(0xFF132133), c(0xFF1D2E45), c(0xFF2D73D1), c(0xFFDAE4F2), c(0xFF7F8FA3),
            c(0xFF1D2E45), c(0xFFDAE4F2), c(0xFF7F8FA3),
            barBg = c(0xFF1E3A5F), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF3C4A5C), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("qing_chayemo", "茶叶末 · 清", "chinese", true,
            c(0xFF332818), c(0xFF453722), c(0xFFD1A86A), c(0xFFF2E8DA), c(0xFFA3957F),
            c(0xFF453722), c(0xFFF2E8DA), c(0xFFA3957F),
            barBg = c(0xFF6E5B3F), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF5C4F3C), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("ming_kongque", "孔雀绿 · 明", "chinese", true,
            c(0xFF13332B), c(0xFF1D453B), c(0xFF26D1A7), c(0xFFDAF2EC), c(0xFF7FA39A),
            c(0xFF1D453B), c(0xFFDAF2EC), c(0xFF7FA39A),
            barBg = c(0xFF1F8A70), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF3C5C54), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("ming_fanhong", "矾红 · 明", "chinese", true,
            c(0xFF331314), c(0xFF451D1E), c(0xFFD1262A), c(0xFFF2DADB), c(0xFFA37F80),
            c(0xFF451D1E), c(0xFFF2DADB), c(0xFFA37F80),
            barBg = c(0xFFC3272B), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF5C3C3C), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("qinghua_lan", "青花钴蓝", "chinese", true,
            c(0xFF131E33), c(0xFF1D2A45), c(0xFF2E64D1), c(0xFFDAE2F2), c(0xFF7F8BA3),
            c(0xFF1D2A45), c(0xFFDAE2F2), c(0xFF7F8BA3),
            barBg = c(0xFF2E4E8F), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF3C465C), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("qing_shanyuhuang", "鳝鱼黄 · 清", "chinese", false,
            c(0xFFF9E4C2), c(0xFFFEFAF2), c(0xFF9E7D47), c(0xFF29241D), c(0xFF70624C),
            c(0xFFFEFAF2), c(0xFF29241D), c(0xFF70624C),
            barBg = c(0xFFB89A6A), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFDBC49E), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("qin_xuansè", "玄色 · 秦", "chinese", true,
            c(0xFF212133), c(0xFF303045), c(0xFF7D7DD1), c(0xFFDADAF2), c(0xFF7F7FA3),
            c(0xFF303045), c(0xFFDADAF2), c(0xFF7F7FA3),
            barBg = c(0xFF2B2B33), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF3C3C5C), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("tang_shiliu", "石榴红 · 唐", "chinese", true,
            c(0xFF331513), c(0xFF451F1D), c(0xFFD12E26), c(0xFFF2DBDA), c(0xFFA3817F),
            c(0xFF451F1D), c(0xFFF2DBDA), c(0xFFA3817F),
            barBg = c(0xFFF20C00), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF5C3D3C), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("tang_feihong", "绯红 · 唐", "chinese", true,
            c(0xFF331314), c(0xFF451D1D), c(0xFFD13335), c(0xFFF2DADA), c(0xFFA37F80),
            c(0xFF451D1D), c(0xFFF2DADA), c(0xFFA37F80),
            barBg = c(0xFFC04243), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF5C3C3C), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("tang_zhehuang", "柘黄 · 唐", "chinese", false,
            c(0xFFF9E7C2), c(0xFFFEFAF2), c(0xFF9E700E), c(0xFF29251D), c(0xFF70654C),
            c(0xFFFEFAF2), c(0xFF29251D), c(0xFF70654C),
            barBg = c(0xFFC89B3C), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFDBC89E), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("gugong_hongqiang", "故宫红墙", "chinese", true,
            c(0xFF331316), c(0xFF451D20), c(0xFFD12634), c(0xFFF2DADC), c(0xFFA37F82),
            c(0xFF451D20), c(0xFFF2DADC), c(0xFFA37F82),
            barBg = c(0xFF8C1F28), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF5C3C3E), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("yuebai", "月白", "chinese", false,
            c(0xFFF9EBEB), c(0xFFFEF9F9), c(0xFF567099), c(0xFF292525), c(0xFF706363),
            c(0xFFFEF9F9), c(0xFF292525), c(0xFF706363),
            barBg = c(0xFFD6ECF0), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFDBCACA), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("shilv", "石绿", "chinese", false,
            c(0xFFC2F9F8), c(0xFFF2FEFE), c(0xFF2C9E9D), c(0xFF1D2929), c(0xFF4C7070),
            c(0xFFF2FEFE), c(0xFF1D2929), c(0xFF4C7070),
            barBg = c(0xFF57C3C2), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFF9EDBDB), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("shiqing", "石青", "chinese", true,
            c(0xFF131F33), c(0xFF1D2B45), c(0xFF2663D1), c(0xFFDAE3F2), c(0xFF7F8CA3),
            c(0xFF1D2B45), c(0xFFDAE3F2), c(0xFF7F8CA3),
            barBg = c(0xFF2E59A7), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF3C475C), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("dailan", "黛蓝", "chinese", true,
            c(0xFF1C2533), c(0xFF283345), c(0xFF7C9DD1), c(0xFFDAE3F2), c(0xFF7F8DA3),
            c(0xFF283345), c(0xFFDAE3F2), c(0xFF7F8DA3),
            barBg = c(0xFF425066), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF3C485C), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("yaqing", "鸦青", "chinese", true,
            c(0xFF212E33), c(0xFF303F45), c(0xFF7DB9D1), c(0xFFDAEBF2), c(0xFF7F99A3),
            c(0xFF303F45), c(0xFFDAEBF2), c(0xFF7F99A3),
            barBg = c(0xFF424C50), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF3C535C), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("xuanqing", "玄青", "chinese", true,
            c(0xFF232133), c(0xFF323045), c(0xFF867DD1), c(0xFFDCDAF2), c(0xFF837FA3),
            c(0xFF323045), c(0xFFDCDAF2), c(0xFF837FA3),
            barBg = c(0xFF3D3B4F), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF3F3C5C), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("jiangzi", "绛紫", "chinese", true,
            c(0xFF33131C), c(0xFF451D27), c(0xFFD15474), c(0xFFF2DAE0), c(0xFFA37F89),
            c(0xFF451D27), c(0xFFF2DAE0), c(0xFFA37F89),
            barBg = c(0xFF8C4356), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF5C3C44), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("ouhe", "藕荷", "chinese", false,
            c(0xFFF9EBEB), c(0xFFFEF9F9), c(0xFF99354F), c(0xFF292525), c(0xFF706363),
            c(0xFFFEF9F9), c(0xFF292525), c(0xFF706363),
            barBg = c(0xFFE4C6D0), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFDBCACA), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("taohong", "桃红", "chinese", false,
            c(0xFFF9C2CF), c(0xFFFEF2F5), c(0xFF9E5768), c(0xFF291D1F), c(0xFF704C55),
            c(0xFFFEF2F5), c(0xFF291D1F), c(0xFF704C55),
            barBg = c(0xFFF4A7B9), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFDB9EAC), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("haitang_hong", "海棠红", "chinese", false,
            c(0xFFF9C2C9), c(0xFFFEF2F3), c(0xFF9E2535), c(0xFF291D1E), c(0xFF704C51),
            c(0xFFFEF2F3), c(0xFF291D1E), c(0xFF704C51),
            barBg = c(0xFFDB5A6B), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFDB9EA6), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("zhusha", "朱砂", "chinese", false,
            c(0xFFF9D2C2), c(0xFFFEF6F2), c(0xFF9E3508), c(0xFF29201D), c(0xFF70574C),
            c(0xFFFEF6F2), c(0xFF29201D), c(0xFF70574C),
            barBg = c(0xFFFF4C00), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFDBB09E), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("xiangse", "缃色", "chinese", false,
            c(0xFFF9EBC2), c(0xFFFEFBF2), c(0xFF9E7808), c(0xFF29261D), c(0xFF70674C),
            c(0xFFFEFBF2), c(0xFF29261D), c(0xFF70674C),
            barBg = c(0xFFF0C239), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFDBCC9E), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("chijin", "赤金", "chinese", false,
            c(0xFFF9E8C2), c(0xFFFEFBF2), c(0xFF9E720B), c(0xFF29251D), c(0xFF70654C),
            c(0xFFFEFBF2), c(0xFF29251D), c(0xFF70654C),
            barBg = c(0xFFF2BE45), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFDBC99E), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("zheshi", "赭石", "chinese", true,
            c(0xFF331D13), c(0xFF45291D), c(0xFFD16A3D), c(0xFFF2E1DA), c(0xFFA38A7F),
            c(0xFF45291D), c(0xFFF2E1DA), c(0xFFA38A7F),
            barBg = c(0xFF955539), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF5C453C), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("tanse", "檀", "chinese", false,
            c(0xFFF9CAC2), c(0xFFFEF4F2), c(0xFF9E4E40), c(0xFF291E1D), c(0xFF70524C),
            c(0xFFFEF4F2), c(0xFF291E1D), c(0xFF70524C),
            barBg = c(0xFFB36D61), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFDBA79E), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("wanse", "绾", "chinese", false,
            c(0xFFF9CFC2), c(0xFFFEF5F2), c(0xFF9E6757), c(0xFF291F1D), c(0xFF70554C),
            c(0xFFFEF5F2), c(0xFF291F1D), c(0xFF70554C),
            barBg = c(0xFFA98175), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFDBAD9F), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("hupo", "琥珀", "chinese", false,
            c(0xFFF9D9C2), c(0xFFFEF7F2), c(0xFF9E4608), c(0xFF29221D), c(0xFF705B4C),
            c(0xFFFEF7F2), c(0xFF29221D), c(0xFF705B4C),
            barBg = c(0xFFCA6924), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFDBB79E), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("yanxia", "烟霞", "chinese", false,
            c(0xFFF9C9D2), c(0xFFFEF2F4), c(0xFF9E5765), c(0xFF291D1F), c(0xFF704C54),
            c(0xFFFEF2F4), c(0xFF291D1F), c(0xFF704C54),
            barBg = c(0xFFD8A7B1), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFDBAFB8), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("ailv", "艾绿", "chinese", false,
            c(0xFFDFF9D6), c(0xFFF5FEF2), c(0xFF699E57), c(0xFF20291D), c(0xFF56704C),
            c(0xFFF5FEF2), c(0xFF20291D), c(0xFF56704C),
            barBg = c(0xFFA8BFA0), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFC4DBBB), elev = 0, cardRadius = 8, btnRadius = 8),
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
