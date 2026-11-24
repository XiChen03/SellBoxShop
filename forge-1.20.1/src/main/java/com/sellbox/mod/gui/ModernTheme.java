package com.sellbox.mod.gui;

/**
 * "现代"主题 —— 参照 SDMShop 的样式。
 * <p>
 * 配色不是拍脑袋定的，而是从参考截图<b>反解</b>出来的：
 * <pre>
 *   参考图纯天空        #85B0FF
 *   面板覆盖天空处      #455980   →  按 A=0.75 反解得面板原色 #2E3C56
 *   左栏覆盖天空处      #5B6980   →  在面板之上再提白约 6~8%
 *   格子覆盖天空处      #2D3B55   →  在面板之上再叠 50% 的 #161D2A
 *   搜索框              #212C40
 *   分类药丸 未选/选中  #313E55 / #7E8CA6
 * </pre>
 * 视觉要点：<b>唯一一层半透明蓝灰底板</b>，左栏在它上面提亮，格子/搜索框/药丸在它上面压暗，
 * 大圆角、无描边、文字以灰白为主。
 */
public class ModernTheme extends GuiTheme {

    public ModernTheme() {
        // ════════════════════════════════════════════
        //  背景 / 面板
        // ════════════════════════════════════════════
        screenBackground     = 0x40000000;   // 淡蒙版：把世界压暗一档，面板边界才立得住
        mainPanelBg          = 0xE6425479;   // 唯一底板 - 90% 不透明（叠在淡蒙版上≈#455980，即参考图色）
        leftPanelBg          = 0x1FFFFFFF;   // 左栏 - 在底板之上提白 12%（合成 #5B6D8F ≈ 参考图 #5B6980）
        headerBandBg         = 0x00000000;   // 顶部与主体同色（参考图实测如此）
        popupBg              = 0xFA37456A;   // 弹窗 - 比面板略深一档，层次分明
        accentStripe         = 0xFF8FB7FF;
        panelDivider         = 0x33A8C4F0;   // 左右分栏竖线 - 浅蓝，不抢视线
        separatorLine        = 0x33FFFFFF;
        panelBorder          = 0x00000000;
        popupBorder          = 0x33FFFFFF;
        scrim                = 0xB3000000;

        // ════════════════════════════════════════════
        //  按钮 - 圆角、无描边
        // ════════════════════════════════════════════
        btnBorder            = 0x00000000;
        btnBgNormal          = 0xE62F3B50;
        btnBgHovered         = 0xE64E6288;
        btnText              = 0xFFE6ECF7;
        btnBgGreenNormal     = 0xE62E4A44;
        btnBgGreenHovered    = 0xE63E6E60;

        // ════════════════════════════════════════════
        //  分类 - 圆角药丸
        // ════════════════════════════════════════════
        catBorder            = 0x00000000;
        catSelectedFill      = 0xFF7E8CA6;
        catHoveredFill       = 0xCC455878;
        catPillBg            = 0xE62F3B50;
        catPillSelectedBg    = 0xFF7E8CA6;
        drawCategoryBorder   = false;
        catText              = 0xFFE2E8F2;
        catTitleText         = 0xFFF2F5FA;
        myShopLabel          = 0xFF8FB7FF;

        // ════════════════════════════════════════════
        //  物品网格 - 独立圆角块（在底板之上压暗）
        // ════════════════════════════════════════════
        cellBg               = 0x80182334;   // 格子 - 在底板上压暗（合成≈#2D3B55，即参考图格底色）
        drawCellBorder       = false;
        cellBorder           = 0x00000000;
        cellSelectedFill     = 0x668FB7FF;
        cellHoveredFill      = 0x33E6ECF7;
        cellInInventory      = 0xFF8FB7FF;
        addSlotBorder        = 0x00000000;
        addSlotText          = 0xFF8FE3B0;
        addSlotBgNormal      = 0x80161D2A;
        addSlotBgHovered     = 0x80323F5C;

        // ════════════════════════════════════════════
        //  文字
        // ════════════════════════════════════════════
        textPrimary          = 0xFFF2F5FA;
        textSecondary        = 0xFFDCE4F0;
        textMuted            = 0xFFB4C0D4;
        textHint             = 0xFF9AA8C0;
        textDisabled         = 0xFF8896AE;
        textTitle            = 0xFFF2F5FA;
        textPrice            = 0xFFDCE4F0;
        textTotal            = 0xFFE8C878;
        textTradeType        = 0xFFDCE4F0;
        textCategoryName     = 0xFFE2E8F2;
        textModeLabel        = 0xFF8FB7FF;
        textMatchMode        = 0xFFC3A6E6;
        textLocked           = 0xFF8FE3B0;
        textInInventory      = 0xFF8FB7FF;
        textError            = 0xFFFF8A8A;

        // ════════════════════════════════════════════
        //  特殊槽位
        // ════════════════════════════════════════════
        slotBagBorder        = 0xFFE8C878;
        slotReceiptBorder    = 0xFFC3A6E6;
        slotInnerFill        = 0xFF161D2A;
        slotLabelText        = 0xFFDCE4F0;

        // ════════════════════════════════════════════
        //  输入框 - 深色胶囊
        // ════════════════════════════════════════════
        inputBorderFocused   = 0x00000000;
        inputBorderUnfocused = 0x00000000;
        searchBg             = 0xE6182130;
        inputBg              = 0xE6182130;   // 与搜索框同一底色，输入框在任何背景上都可见
        searchText           = 0xFFF2F5FA;
        searchCursor         = 0xFFFFFFFF;

        // ════════════════════════════════════════════
        //  滚动条
        // ════════════════════════════════════════════
        scrollbarTrack       = 0x33202C42;
        scrollbarThumb       = 0xCC7E8CA6;

        // ════════════════════════════════════════════
        //  右键菜单 / 列表行
        // ════════════════════════════════════════════
        ctxMenuBg            = 0xFA2E3C56;
        ctxMenuBorder        = 0x33FFFFFF;
        ctxMenuItemHover     = 0x448FB7FF;
        listRowBg            = 0x00000000;
        listRowHover         = 0x33E6ECF7;
        listRowSelected      = 0x668FB7FF;

        // ════════════════════════════════════════════
        //  余额 / 库存
        // ════════════════════════════════════════════
        balanceBorder        = 0x00000000;
        balanceBg            = 0x80182334;   // 余额卡底色，与物品格子同一压暗档
        balanceText          = 0xFFF2F5FA;
        stockInColor         = 0x508FE3B0;
        stockOutColor        = 0x50FF8A8A;

        // ════════════════════════════════════════════
        //  NBT / 信息面板
        // ════════════════════════════════════════════
        nbtBoxOuter          = 0xFF101A2A;
        nbtBoxInner          = 0xFF161D2A;
        infoPanelBg          = 0x662E3C56;
        itemListBg           = 0xE82E3C56;

        // ════════════════════════════════════════════
        //  严格匹配 / 以物易物
        // ════════════════════════════════════════════
        strictMatchOn        = 0x668FE3B0;
        strictMatchOff       = 0x33FFFFFF;
        barterLockedBorder   = 0xFF8FE3B0;
        barterDisabledBorder = 0xFF8896AE;

        // ════════════════════════════════════════════
        //  任务选择器
        // ════════════════════════════════════════════
        questGroupBg         = 0xE62E3C56;
        questGroupHover      = 0x448FB7FF;
        questChapterBg       = 0xE62A3450;
        questChapterHover    = 0x448FB7FF;
        questItemHover       = 0x448FB7FF;

        // ════════════════════════════════════════════
        //  字体缩放
        // ════════════════════════════════════════════
        titleFontScale       = 1.15f;
        tradeTypeScale       = 0.8f;
        qtyTextScale         = 0.6f;

        // ════════════════════════════════════════════
        //  布局基准
        // ════════════════════════════════════════════
        basePanelWidth       = 440;
        basePanelHeight      = 280;
        baseLeftPanelWidth   = 104;
        baseCategoryHeight   = 18;
        gridCols             = 8;
        gridRows             = 4;
        baseGridSpacingX     = 46;
        baseGridSpacingY     = 52;
        scrollbarWidth       = 4;
        cellGap              = 3;
        cornerRadius         = 8;
    }
}
