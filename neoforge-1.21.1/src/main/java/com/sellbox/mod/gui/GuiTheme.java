package com.sellbox.mod.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sellbox.mod.SellBoxMod;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一 GUI 主题配置 - 所有商店/市场 GUI 颜色集中管理。
 * <p>
 * 关键设计：{@link #current} 的<b>对象身份永远不变</b>，切换主题时只把新配色的字段值
 * 抄进这个实例（{@link #applyFieldsFrom}）。界面在构造时缓存 `GuiTheme.get()`，
 * 因此切换后已打开的界面会立即变色，无需重开。
 */
public class GuiTheme {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 全局唯一实例，身份稳定（切勿整体替换，否则各处缓存的引用会变旧） */
    private static final GuiTheme current = new GuiTheme();

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

    /** 该主题的人工可读名（用于按钮/提示） */
    public static String displayName(String id) {
        return switch (id) {
            case "default" -> "默认深色";
            case "modern" -> "现代";
            case "light" -> "浅色";
            case "green" -> "护眼绿";
            case "purple" -> "暗紫";
            case "orange" -> "暖橙";
            default -> id;
        };
    }

    public static String getCurrentDisplayName() { return displayName(currentName); }

    /**
     * 按名字切换主题。切换成功后立即写盘，并就地刷新 {@link #current}。
     *
     * @return 名字不存在时返回 false
     */
    public static boolean setByName(String name) {
        if (name == null) return false;
        String key = name.toLowerCase();
        ThemeFactory factory = registry.get(key);
        if (factory == null) return false;
        applyFieldsFrom(factory.create());
        currentName = key;
        save();
        return true;
    }

    /** 就地覆盖：只复制实例字段，保持对象身份 */
    private static void applyFieldsFrom(GuiTheme source) {
        if (source == null || source == current) return;
        for (Field f : GuiTheme.class.getFields()) {
            int mod = f.getModifiers();
            if (Modifier.isStatic(mod) || Modifier.isFinal(mod)) continue;
            Class<?> type = f.getType();
            if (type != int.class && type != float.class && type != boolean.class) continue;
            try {
                f.set(current, f.get(source));
            } catch (IllegalAccessException e) {
                SellBoxMod.LOGGER.error("Failed to copy GUI theme field {}", f.getName(), e);
            }
        }
    }

    // ════════════════════════════════════════════
    //  持久化
    // ════════════════════════════════════════════
    public static void init(Path configDir) {
        configPath = configDir.resolve("SellBox").resolve("gui_theme.json");
        load();
    }

    private static Path getConfigPath() {
        return configPath;
    }

    public static void load() {
        Path path = getConfigPath();
        if (path == null) return;
        if (!Files.exists(path)) {
            save();
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Map<?, ?> data = GSON.fromJson(reader, Map.class);
            if (data != null && data.get("theme") instanceof String name) {
                ThemeFactory factory = registry.get(name.toLowerCase());
                if (factory != null) {
                    applyFieldsFrom(factory.create());
                    currentName = name.toLowerCase();
                } else {
                    SellBoxMod.LOGGER.warn("Unknown GUI theme '{}' in {}, keeping default", name, path);
                }
            }
        } catch (Exception e) {
            SellBoxMod.LOGGER.error("Failed to load GUI theme from {}", path, e);
        }
    }

    public static void save() {
        Path path = getConfigPath();
        if (path == null) return;
        try {
            Files.createDirectories(path.getParent());
            Map<String, String> data = new LinkedHashMap<>();
            data.put("theme", currentName);
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
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
    /** 面板外框（0 = 不画边框） */
    public int panelBorder        = 0x00000000;
    /** 左侧栏顶部标题条底色（0 = 不画，与左栏同色） */
    public int headerBandBg       = 0x00000000;
    /** 弹窗/子界面外框（0 = 不画） */
    public int popupBorder        = 0x00000000;
    /** 弹窗全屏遮罩底色（0 = 不压暗背景） */
    public int scrim              = 0xCC000000;
    /** 列表行底色（0 = 不填充） */
    public int listRowBg          = 0x00000000;
    /** 列表行悬浮底色（0 = 不填充） */
    public int listRowHover       = 0x22FFFFFF;
    /** 列表行选中底色（0 = 不填充） */
    public int listRowSelected    = 0x444488FF;

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
    /** 分类条目常态填充（0 = 不填充，只画边框） */
    public int catPillBg          = 0x00000000;
    /** 分类条目选中填充（0 = 退化为 catSelectedFill） */
    public int catPillSelectedBg  = 0x00000000;
    public int catText            = 0x88CCFF;
    public int catTitleText       = 0xFFAA00;
    public int myShopLabel        = 0xFFFFAA00;
    /** 是否绘制分类条目边框（false = 纯色块风格） */
    public boolean drawCategoryBorder = true;

    // ════════════════════════════════════════════
    //  物品网格
    // ════════════════════════════════════════════
    public int cellBorder         = 0xFF444444;
    public int cellSelectedFill   = 0x404488FF;
    public int cellHoveredFill    = 0x30FFFFFF;
    /** 格子常态底色（0 = 透明，只画边框） */
    public int cellBg             = 0x00000000;
    public int cellInInventory    = 0x88FFAA00;
    public int addSlotBorder      = 0x66444444;
    public int addSlotText        = 0x88FF88;
    public int addSlotBgNormal    = 0x88333333;
    public int addSlotBgHovered   = 0xAA44AA44;
    /** 是否绘制格子边框（false = 纯色块风格） */
    public boolean drawCellBorder = true;

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
    /** 搜索框底色（0 = 不填充，只画边框） */
    public int searchBg              = 0x00000000;
    /** 普通输入框底色；0 表示不铺底色（仅靠边框区分） */
    public int inputBg               = 0x00000000;

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
    /** 余额区底色；0 表示不铺底色（经典主题沿用纯边框） */
    public int balanceBg          = 0x00000000;
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
    /** 文字是否带投影。浅色主题必须关掉，否则深色字 + 白色糊影会糊成一团 */
    public boolean textShadow     = true;

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
