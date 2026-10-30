package com.sellbox.mod.gui;

/**
 * "现代" 主题 - 全新像素化半透明风格
 */
public class ModernTheme extends GuiTheme {

    public ModernTheme() {
        // ════════════════════════════════════════════
        //  背景遮罩
        // ════════════════════════════════════════════
        screenBackground     = 0x80000000;   // 半透明黑色蒙版

        // ════════════════════════════════════════════
        //  面板背景 (深蓝灰色)
        // ════════════════════════════════════════════
        mainPanelBg          = 0xE61A1F2E;   // 主面板 - 深蓝灰 90%不透明
        leftPanelBg          = 0xE6141828;   // 左侧栏 - 更深的蓝灰
        popupBg              = 0xF01E2236;   // 弹窗 - 深蓝灰 94%不透明
        accentStripe         = 0xFF4A90D9;   // 强调条 - 亮蓝色
        panelDivider         = 0xFF2A3050;   // 面板分割线
        separatorLine        = 0xFF3A4060;   // 普通分割线

        // ════════════════════════════════════════════
        //  按钮 (现代风格)
        // ════════════════════════════════════════════
        btnBorder            = 0xFF4A90D9;   // 蓝色边框
        btnBgNormal          = 0xCC2A3050;   // 深蓝灰背景 80%不透明
        btnBgHovered         = 0x664A90D9;   // 蓝色半透明悬浮
        btnText              = 0xFFFFFF;     // 白色文字
        btnBgGreenNormal     = 0xCC1E3A2A;   // 绿色按钮底色
        btnBgGreenHovered    = 0x664CAF50;   // 绿色悬浮

        // ════════════════════════════════════════════
        //  分类导航栏
        // ════════════════════════════════════════════
        catBorder            = 0xFF4A90D9;   // 蓝色边框
        catSelectedFill      = 0x444A90D9;   // 选中 - 蓝色半透明
        catHoveredFill       = 0x22FFFFFF;   // 悬浮 - 白色微透
        catText              = 0xFFB0B8C8;   // 未选中文字 - 浅蓝灰
        catTitleText         = 0xFFFFFFFF;   // 标题 - 纯白
        myShopLabel          = 0xFF4CAF50;   // "我的商店" - 绿色

        // ════════════════════════════════════════════
        //  物品网格
        // ════════════════════════════════════════════
        cellBorder           = 0xFF3A4060;   // 格子边框 - 深蓝灰
        cellSelectedFill     = 0x444A90D9;   // 选中 - 蓝色
        cellHoveredFill      = 0x33FFFFFF;   // 悬浮 - 白色微透
        cellInInventory      = 0xFF4CAF50;   // 库存标记 - 绿色
        addSlotBorder        = 0xFF3A4060;   // 添加槽边框
        addSlotText          = 0xFF4CAF50;   // "+" 号 - 绿色
        addSlotBgNormal      = 0xCC1E2236;   // 添加槽背景
        addSlotBgHovered     = 0x664CAF50;   // 添加槽悬浮 - 绿色

        // ════════════════════════════════════════════
        //  文字颜色
        // ════════════════════════════════════════════
        textPrimary          = 0xFFFFFFFF;   // 主要文字 - 纯白
        textSecondary        = 0xFFB0B8C8;   // 次要文字 - 浅蓝灰
        textMuted            = 0xFF7080A0;   // 弱化文字 - 暗蓝灰
        textHint             = 0xFF5A6A8A;   // 提示文字 - 更暗
        textDisabled         = 0xFF4A5A7A;   // 禁用文字
        textTitle            = 0xFFFFFFFF;   // 标题 - 纯白
        textPrice            = 0xFF4CAF50;   // 价格 - 绿色
        textTotal            = 0xFFFFC107;   // 总价 - 橙黄
        textTradeType        = 0xFF90CAF9;   // 交易类型 - 浅蓝
        textCategoryName     = 0xFFB0B8C8;   // 分类名 - 浅蓝灰
        textModeLabel        = 0xFF90CAF9;   // 模式标签 - 浅蓝
        textMatchMode        = 0xFFCE93D8;   // 匹配模式 - 淡紫
        textLocked           = 0xFF4CAF50;   // 锁定 - 绿色
        textInInventory      = 0xFF81C784;   // 库存中 - 浅绿
        textError            = 0xFFEF5350;   // 错误 - 红色

        // ════════════════════════════════════════════
        //  特殊槽位边框
        // ════════════════════════════════════════════
        slotBagBorder        = 0xFFFFC107;   // 钱袋 - 橙黄
        slotReceiptBorder    = 0xFFCE93D8;   // 收据 - 淡紫
        slotInnerFill        = 0xFF1E2236;   // 槽位内部 - 深蓝灰
        slotLabelText        = 0xFFB0B8C8;   // 槽位标签 - 浅蓝灰

        // ════════════════════════════════════════════
        //  输入框
        // ════════════════════════════════════════════
        inputBorderFocused   = 0xFF4A90D9;   // 聚焦 - 蓝色
        inputBorderUnfocused = 0xFF3A4060;   // 未聚焦 - 深蓝灰

        // ════════════════════════════════════════════
        //  滚动条
        // ════════════════════════════════════════════
        scrollbarTrack       = 0x801E2236;   // 轨道 - 深蓝灰半透明
        scrollbarThumb       = 0xCC4A90D9;   // 滑块 - 蓝色

        // ════════════════════════════════════════════
        //  右键菜单
        // ════════════════════════════════════════════
        ctxMenuBg            = 0xF01E2236;   // 菜单背景 - 深蓝灰
        ctxMenuBorder        = 0xFF4A90D9;   // 菜单边框 - 蓝色
        ctxMenuItemHover     = 0x444A90D9;   // 菜单项悬浮 - 蓝色

        // ════════════════════════════════════════════
        //  余额 / 库存
        // ════════════════════════════════════════════
        balanceBorder        = 0xFF4A90D9;   // 余额边框 - 蓝色
        balanceText          = 0xFFFFC107;   // 余额文字 - 橙黄
        stockInColor         = 0x504CAF50;   // 有库存 - 绿色半透
        stockOutColor        = 0x50EF5350;   // 无库存 - 红色半透
        searchText           = 0xFFFFFFFF;   // 搜索文字 - 白色
        searchCursor         = 0xFFFFFFFF;   // 光标 - 白色

        // ════════════════════════════════════════════
        //  NBT / 信息面板
        // ════════════════════════════════════════════
        nbtBoxOuter          = 0xFF0E1018;   // NBT框外层
        nbtBoxInner          = 0xFF1E2236;   // NBT框内层
        infoPanelBg          = 0x661E2236;   // 信息面板
        itemListBg           = 0xE6141828;   // 物品列表背景

        // ════════════════════════════════════════════
        //  严格匹配按钮
        // ════════════════════════════════════════════
        strictMatchOn        = 0x664CAF50;   // 开启 - 绿色
        strictMatchOff       = 0x443A4060;   // 关闭 - 深蓝灰

        // ════════════════════════════════════════════
        //  以物易物
        // ════════════════════════════════════════════
        barterLockedBorder   = 0xFF4CAF50;   // 锁定边框 - 绿色
        barterDisabledBorder = 0xFF4A5A7A;   // 禁用边框

        // ════════════════════════════════════════════
        //  任务选择器
        // ════════════════════════════════════════════
        questGroupBg         = 0xE61E2236;   // 任务组背景
        questGroupHover      = 0x444A90D9;   // 任务组悬浮 - 蓝色
        questChapterBg       = 0xE61A1E30;   // 章节背景
        questChapterHover    = 0x444A90D9;   // 章节悬浮
        questItemHover       = 0x444A90D9;   // 任务项悬浮

        // ════════════════════════════════════════════
        //  字体缩放
        // ════════════════════════════════════════════
        titleFontScale       = 1.2f;
        tradeTypeScale       = 0.8f;
        qtyTextScale         = 0.7f;

        // ════════════════════════════════════════════
        //  布局基准
        // ════════════════════════════════════════════
        basePanelWidth       = 440;
        basePanelHeight      = 280;
        baseLeftPanelWidth   = 110;
        baseCategoryHeight   = 28;
        gridCols             = 6;
        gridRows             = 4;
        baseGridSpacingX     = 50;
        baseGridSpacingY     = 56;
        scrollbarWidth       = 4;
        cellGap              = 3;
        cornerRadius         = 4;
    }
}
