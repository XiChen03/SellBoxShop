package com.sellbox.mod.gui;

/**
 * 预设主题集合
 */
public class ThemePresets {

    /** 默认深色主题（当前样式） */
    public static GuiTheme dark() {
        return new GuiTheme();
    }

    /** 浅色主题：白粉 */
    public static GuiTheme light() {
        GuiTheme t = new GuiTheme();
        // 面板：奶白底 + 粉色层次
        t.screenBackground = 0x00000000;
        t.mainPanelBg      = 0xFFFFF7FA;
        t.leftPanelBg      = 0xFFFFEAF2;
        t.popupBg          = 0xFFFFFBFD;
        t.accentStripe     = 0xFFE86A9A;
        t.panelDivider     = 0xFFF0C6D6;
        t.separatorLine    = 0xFFE6A8BF;
        // 按钮
        t.btnBorder        = 0xFFE86A9A;
        t.btnBgNormal      = 0x55F3B6C9;
        t.btnBgHovered     = 0x88F19BB8;
        t.btnBgGreenNormal = 0x55F3B6C9;
        t.btnBgGreenHovered= 0x88F19BB8;
        t.btnText          = 0xFF5A2A3A;
        // 分类
        t.catBorder        = 0xFFE86A9A;
        t.catSelectedFill  = 0x44E86A9A;
        t.catHoveredFill   = 0x22E86A9A;
        t.catText          = 0xFFB23A68;
        t.catTitleText     = 0xFF8A3B5A;
        // 网格
        t.cellBorder       = 0xFFF2C9D8;
        t.cellSelectedFill = 0x44E86A9A;
        t.cellHoveredFill  = 0x30F19BB8;
        // 文字：深梅色，白粉底上保持可读
        t.textPrimary      = 0xFF3A2730;
        t.textSecondary    = 0xFF5C404B;
        t.textMuted        = 0xFF8A6572;
        t.textHint         = 0xFFA98693;
        t.textTitle        = 0xFF7A2F4F;
        t.textPrice        = 0xFFB03A63;
        t.textTotal        = 0xFFA05A00;
        t.textTradeType    = 0xFF4A3540;
        t.textCategoryName = 0xFFB23A68;
        // 槽位
        t.slotBagBorder    = 0xFFD99A3A;
        t.slotReceiptBorder= 0xFFB06ACB;
        t.slotInnerFill    = 0xFFFFEFF5;
        t.slotLabelText    = 0xFF5A3A47;
        // 输入框
        t.inputBorderFocused   = 0xCCE86A9A;
        t.inputBorderUnfocused = 0x99D9A8BA;
        // 滚动条
        t.scrollbarTrack  = 0x55F0C6D6;
        t.scrollbarThumb  = 0xCCE86A9A;
        // 右键菜单
        t.ctxMenuBg       = 0xEEFFF7FA;
        t.ctxMenuBorder   = 0xFFE6A8BF;
        t.ctxMenuItemHover= 0x33E86A9A;
        // 余额
        t.balanceBorder   = 0x66E86A9A;
        t.balanceText     = 0xFFC0426F;
        t.textShadow      = false;
        return t;
    }

    /** 绿色护眼主题 */
    public static GuiTheme green() {
        GuiTheme t = new GuiTheme();
        t.mainPanelBg      = 0xFF1A2E1A;
        t.leftPanelBg      = 0xFF1E331E;
        t.popupBg          = 0xFF223322;
        t.accentStripe     = 0xFF44AA44;
        t.panelDivider     = 0xFF336633;
        t.separatorLine    = 0xFF447744;
        t.btnBorder        = 0xFF44AA44;
        t.btnBgNormal      = 0x88224422;
        t.btnBgHovered     = 0x4444AA44;
        t.btnBgGreenNormal = 0x88224422;
        t.btnBgGreenHovered= 0xAA44AA44;
        t.catBorder        = 0xFF44AA44;
        t.catSelectedFill  = 0x4444AA44;
        t.catHoveredFill   = 0x2244AA44;
        t.catText          = 0xFF88DD88;
        t.catTitleText     = 0xFF88CC44;
        t.cellBorder       = 0xFF336633;
        t.cellSelectedFill = 0x4444AA44;
        t.cellHoveredFill  = 0x3044AA44;
        t.textTitle        = 0xFF88DD88;
        t.textPrice        = 0xFF88FF88;
        t.textTotal        = 0xFFCCDD44;
        t.textCategoryName = 0xFF88DD88;
        t.slotBagBorder    = 0xFFCCDD44;
        t.slotReceiptBorder= 0xFFCC44CC;
        t.slotInnerFill    = 0xFF1E331E;
        t.scrollbarTrack   = 0x80224422;
        t.scrollbarThumb   = 0xCC44AA44;
        t.ctxMenuBg        = 0xCC1A2E1A;
        t.ctxMenuBorder    = 0xFF44AA44;
        t.ctxMenuItemHover = 0x4444AA44;
        t.balanceBorder    = 0x55CCDD44;
        t.balanceText      = 0xFFCCDD44;
        return t;
    }

    /** 紫色主题 */
    public static GuiTheme purple() {
        GuiTheme t = new GuiTheme();
        t.mainPanelBg      = 0xFF1E1A2E;
        t.leftPanelBg      = 0xFF231E33;
        t.popupBg          = 0xFF282238;
        t.accentStripe     = 0xFFAA44CC;
        t.panelDivider     = 0xFF443366;
        t.separatorLine    = 0xFF554477;
        t.btnBorder        = 0xFFAA44CC;
        t.btnBgNormal      = 0x88332244;
        t.btnBgHovered     = 0x44AA44CC;
        t.btnBgGreenNormal = 0x88332244;
        t.btnBgGreenHovered= 0xAA8844CC;
        t.catBorder        = 0xFFAA44CC;
        t.catSelectedFill  = 0x44AA44CC;
        t.catHoveredFill   = 0x22AA44CC;
        t.catText          = 0xFFCC88FF;
        t.catTitleText     = 0xFFCC8844;
        t.myShopLabel      = 0xFFCCAA44;
        t.cellBorder       = 0xFF443366;
        t.cellSelectedFill = 0x44AA44CC;
        t.cellHoveredFill  = 0x30AA44CC;
        t.textTitle        = 0xFFCC88FF;
        t.textPrice        = 0xFF88CC88;
        t.textTotal        = 0xFFDDAA44;
        t.textCategoryName = 0xFFCC88FF;
        t.textModeLabel    = 0xFFCCAA44;
        t.slotBagBorder    = 0xFFDDAA44;
        t.slotReceiptBorder= 0xFFCC44AA;
        t.slotInnerFill    = 0xFF231E33;
        t.scrollbarTrack   = 0x80332244;
        t.scrollbarThumb   = 0xCCAA44CC;
        t.ctxMenuBg        = 0xCC1E1A2E;
        t.ctxMenuBorder    = 0xFFAA44CC;
        t.ctxMenuItemHover = 0x44AA44CC;
        t.balanceBorder    = 0x55DDAA44;
        t.balanceText      = 0xFFDDAA44;
        return t;
    }

    /** 橙色暖色主题 */
    public static GuiTheme orange() {
        GuiTheme t = new GuiTheme();
        t.mainPanelBg      = 0xFF2E2218;
        t.leftPanelBg      = 0xFF332818;
        t.popupBg          = 0xFF382C20;
        t.accentStripe     = 0xFFCC6622;
        t.panelDivider     = 0xFF664433;
        t.separatorLine    = 0xFF775544;
        t.btnBorder        = 0xFFCC6622;
        t.btnBgNormal      = 0x88442211;
        t.btnBgHovered     = 0x44CC6622;
        t.btnBgGreenNormal = 0x88442211;
        t.btnBgGreenHovered= 0xAA886633;
        t.catBorder        = 0xFFCC6622;
        t.catSelectedFill  = 0x44CC6622;
        t.catHoveredFill   = 0x22CC6622;
        t.catText          = 0xFFEEAA66;
        t.catTitleText     = 0xFFCCAA44;
        t.cellBorder       = 0xFF664433;
        t.cellSelectedFill = 0x44CC6622;
        t.cellHoveredFill  = 0x30CC6622;
        t.textTitle        = 0xFFEEAA66;
        t.textPrice        = 0xFF88CC88;
        t.textTotal        = 0xFFDDCC44;
        t.textCategoryName = 0xFFEEAA66;
        t.slotBagBorder    = 0xFFDDCC44;
        t.slotReceiptBorder= 0xFFCC4488;
        t.slotInnerFill    = 0xFF332818;
        t.scrollbarTrack   = 0x80442211;
        t.scrollbarThumb   = 0xCCAA8866;
        t.ctxMenuBg        = 0xCC2E2218;
        t.ctxMenuBorder    = 0xFFCC6622;
        t.ctxMenuItemHover = 0x44CC6622;
        t.balanceBorder    = 0x55DDCC44;
        t.balanceText      = 0xFFDDCC44;
        return t;
    }
}
