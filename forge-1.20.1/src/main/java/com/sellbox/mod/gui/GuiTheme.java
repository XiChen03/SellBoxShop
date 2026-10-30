package com.sellbox.mod.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sellbox.mod.SellBoxMod;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一 GUI 主题配置 - 所有商店/市场 GUI 颜色集中管理
 * 通过 {@link #get()} 获取当前主题，运行时可切换
 */
public class GuiTheme {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static GuiTheme current = new GuiTheme();
    private static String currentName = "default";
    private static final Map<String, ThemeFactory> registry = new LinkedHashMap<>();
    private static Path configPath;

    static {
        registry.put("default", ThemePresets::dark);
        registry.put("modern", ModernTheme::new);
        registry.put("light", ThemePresets::light);
        registry.put("green", ThemePresets::green);
        registry.put("purple", ThemePresets::purple);
        registry.put("orange", ThemePresets::orange);
    }

    public static GuiTheme get() { return current; }
    public static String getCurrentName() { return currentName; }
    public static Map<String, ThemeFactory> getRegistry() { return registry; }

    public static void set(GuiTheme theme) { current = theme; }

    public static void setByName(String name) {
        ThemeFactory factory = registry.get(name.toLowerCase());
        if (factory != null) {
            current = factory.create();
            currentName = name.toLowerCase();
            save();
        }
    }

    // ════════════════════════════════════════════
    //  持久化
    // ════════════════════════════════════════════
    public static void init(Path configDir) {
        configPath = configDir.resolve("SellBox").resolve("gui_theme.json");
        load();
    }

    public static void load() {
        if (configPath == null || !Files.exists(configPath)) {
            save();
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            Map<String, String> data = GSON.fromJson(reader, Map.class);
            if (data != null && data.containsKey("theme")) {
                String name = data.get("theme");
                ThemeFactory factory = registry.get(name);
                if (factory != null) {
                    current = factory.create();
                    currentName = name;
                }
            }
        } catch (Exception e) {
            SellBoxMod.LOGGER.error("Failed to load GUI theme", e);
        }
    }

    public static void save() {
        if (configPath == null) return;
        try {
            Files.createDirectories(configPath.getParent());
            Map<String, String> data = new LinkedHashMap<>();
            data.put("theme", currentName);
            try (BufferedWriter writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("Failed to save GUI theme", e);
        }
    }

    @FunctionalInterface
    public interface ThemeFactory {
        GuiTheme create();
    }

    // ════════════════════════════════════════════
    //  面板 / 背景
    // ════════════════════════════════════════════
    public int screenBackground   = 0x00000000;
    public int mainPanelBg        = 0xFF222222;
    public int leftPanelBg        = 0xFF2A2A2A;
    public int popupBg            = 0xFF333333;
    public int accentStripe       = 0xFFFFAA00;
    public int panelDivider       = 0xFF444444;
    public int separatorLine      = 0xFF555555;

    // ════════════════════════════════════════════
    //  按钮 (ShopButton)
    // ════════════════════════════════════════════
    public int btnBorder          = 0xFF4488FF;
    public int btnBgNormal        = 0x88333333;
    public int btnBgHovered       = 0x44FFFFFF;
    public int btnText            = 0xFFFFFF;
    public int btnBgGreenNormal   = 0x88333333;
    public int btnBgGreenHovered  = 0xAA44AA44;

    // ════════════════════════════════════════════
    //  分类 / 商店列表
    // ════════════════════════════════════════════
    public int catBorder          = 0xFF4488FF;
    public int catSelectedFill    = 0x444488FF;
    public int catHoveredFill     = 0x22FFFFFF;
    public int catText            = 0x88CCFF;
    public int catTitleText       = 0xFFAA00;
    public int myShopLabel        = 0xFFFFAA00;

    // ════════════════════════════════════════════
    //  物品网格
    // ════════════════════════════════════════════
    public int cellBorder         = 0xFF444444;
    public int cellSelectedFill   = 0x404488FF;
    public int cellHoveredFill    = 0x30FFFFFF;
    public int cellInInventory    = 0x88FFAA00;
    public int addSlotBorder      = 0x66444444;
    public int addSlotText        = 0x88FF88;
    public int addSlotBgNormal    = 0x88333333;
    public int addSlotBgHovered   = 0xAA44AA44;

    // ════════════════════════════════════════════
    //  文字颜色
    // ════════════════════════════════════════════
    public int textPrimary        = 0xFFFFFF;
    public int textSecondary      = 0xDDDDDD;
    public int textMuted          = 0xAAAAAA;
    public int textHint           = 0x777777;
    public int textDisabled       = 0x888888;
    public int textTitle          = 0xFFFFAA;
    public int textPrice          = 0x88FF88;
    public int textTotal          = 0xFFD700;
    public int textTradeType      = 0xFFFFAA;
    public int textCategoryName   = 0x88CCFF;
    public int textModeLabel      = 0xFFAA00;
    public int textMatchMode      = 0xFF88AA;
    public int textLocked         = 0x88FF88;
    public int textInInventory    = 0x88FFAA;
    public int textError          = 0xFFFF4444;

    // ════════════════════════════════════════════
    //  特殊槽位边框
    // ════════════════════════════════════════════
    public int slotBagBorder      = 0xFFFFD700;
    public int slotReceiptBorder  = 0xFFBB00FF;
    public int slotInnerFill      = 0xFF2D2D2D;
    public int slotLabelText      = 0xFFFFFFFF;

    // ════════════════════════════════════════════
    //  输入框
    // ════════════════════════════════════════════
    public int inputBorderFocused    = 0x55FFFFFF;
    public int inputBorderUnfocused  = 0x55AAAAAA;

    // ════════════════════════════════════════════
    //  滚动条
    // ════════════════════════════════════════════
    public int scrollbarTrack     = 0x80333333;
    public int scrollbarThumb     = 0xCCAAAAAA;

    // ════════════════════════════════════════════
    //  右键菜单
    // ════════════════════════════════════════════
    public int ctxMenuBg          = 0xCC222222;
    public int ctxMenuBorder      = 0xFF888888;
    public int ctxMenuItemHover   = 0x444488FF;

    // ════════════════════════════════════════════
    //  库存 / 余额
    // ════════════════════════════════════════════
    public int balanceBorder      = 0x55FFD700;
    public int balanceText        = 0xFFD700;
    public int stockInColor       = 0x5044FF44;
    public int stockOutColor      = 0x50FF4444;
    public int searchText         = 0xFFFFFF;
    public int searchCursor       = 0xFFFFFFFF;

    // ════════════════════════════════════════════
    //  NBT / 信息面板
    // ════════════════════════════════════════════
    public int nbtBoxOuter        = 0xFF101010;
    public int nbtBoxInner        = 0xFF2C2C2C;
    public int infoPanelBg        = 0x44222222;
    public int itemListBg         = 0xFF222222;

    // ════════════════════════════════════════════
    //  严格匹配按钮
    // ════════════════════════════════════════════
    public int strictMatchOn      = 0x44FF8844;
    public int strictMatchOff     = 0x44444444;

    // ════════════════════════════════════════════
    //  以物易物边框
    // ════════════════════════════════════════════
    public int barterLockedBorder = 0xFF44FF44;
    public int barterDisabledBorder = 0xFF888888;

    // ════════════════════════════════════════════
    //  任务选择器
    // ════════════════════════════════════════════
    public int questGroupBg       = 0xFF2D2D2D;
    public int questGroupHover   = 0xFF444466;
    public int questChapterBg    = 0xFF262626;
    public int questChapterHover = 0xFF3A3A5A;
    public int questItemHover    = 0xFF444488;

    // ════════════════════════════════════════════
    //  字体缩放
    // ════════════════════════════════════════════
    public float titleFontScale   = 1.3f;
    public float tradeTypeScale  = 0.85f;
    public float qtyTextScale    = 0.6f;

    // ════════════════════════════════════════════
    //  布局基准
    // ════════════════════════════════════════════
    public int basePanelWidth     = 420;
    public int basePanelHeight    = 260;
    public int baseLeftPanelWidth = 100;
    public int baseCategoryHeight = 14;
    public int gridCols           = 8;
    public int gridRows           = 4;
    public int baseGridSpacingX   = 44;
    public int baseGridSpacingY   = 48;
    public int scrollbarWidth     = 3;
    public int cellGap            = 2;
    public int cornerRadius       = 2;
}
