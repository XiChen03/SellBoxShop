package com.sellbox.mod.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * 支持"酿造等级品质变体"的物品工具类（兼容 Kaleidoscope Tavern 等具有
 * getBrewLevel(ItemStack) 静态方法的酒类模组物品，或物品ID含酒类前缀的模组物品）。
 *
 * 品质以物品 minecraft:custom_data 子标签的 int 键 "BrewLevel"（0~6）区分。
 */
public final class ItemVariantUtil {

    public static final String BREW_LEVEL_KEY = "BrewLevel";
    public static final int BREW_MAX_LEVEL = 6;

    /** BrewLevel 1~6 对应的中文品质名（与酒馆模组 lang 一致） */
    public static final String[] BREW_QUALITY_NAMES = {"难以下咽", "劣质", "普通", "优质", "精酿", "典藏"};

    /** 按物品ID前缀识别酒类时匹配的namespace/以词边界的path片段（可按需扩展；kaleidoscope_tavern 为常见酒馆模组） */
    private static final String[] KNOWN_BREW_NAMESPACES = {"kaleidoscope_tavern", "winecraft", "brewery"};
    /** path 词边界匹配的酿酒词根（避免 "win" 误伤含 win 的物品） */
    private static final String[] KNOWN_BREW_PATH_TOKENS = {"wine", "brew", "beer", "tavern", "spirits", "alcohol"};

    private ItemVariantUtil() {
    }

    /** 判断物品是否支持 BrewLevel 品质变体：反射检测 getBrewLevel(ItemStack) 静态方法，或物品ID命中酒类命名空间/词根 */
    public static boolean hasBrewLevel(Item item) {
        try {
            item.getClass().getMethod("getBrewLevel", ItemStack.class);
            return true;
        } catch (NoSuchMethodException e) {
            // 反射未命中时退回 ID 匹配
        }
        var rl = BuiltInRegistries.ITEM.getKey(item);
        String ns = rl.getNamespace().toLowerCase();
        String path = rl.getPath().toLowerCase();
        for (String n : KNOWN_BREW_NAMESPACES) {
            if (ns.equals(n)) return true;
        }
        for (String tok : KNOWN_BREW_PATH_TOKENS) {
            if (path.startsWith(tok) || path.endsWith(tok) || path.equals(tok)) return true;
        }
        return false;
    }

    /** 构造指定 BrewLevel 等级的物品栈（携带 BrewLevel NBT 于 custom_data） */
    public static ItemStack withBrewLevel(Item item, int level) {
        ItemStack stack = new ItemStack(item);
        int lvl = Math.max(0, Math.min(BREW_MAX_LEVEL, level));
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        tag.putInt(BREW_LEVEL_KEY, lvl);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /** 读取物品 custom_data 中的 BrewLevel（无则 0） */
    public static int getBrewLevel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 0;
        try {
            return data.getUnsafe().getInt(BREW_LEVEL_KEY);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 生成用于 NBT 定价的完整组件 SNBT 键（形如 {"minecraft:enchantments":{...}}，等价于 1.20.1 的根 NBT）；
     *  无任何非默认组件返回 "{}"；stack/reg 为空或异常返回 null */
    public static String brewNbtSnbt(ItemStack stack, net.minecraft.core.HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty() || registries == null) return null;
        try {
            CompoundTag save = (CompoundTag) stack.save(registries);
            CompoundTag comps = save.contains("components") ? save.getCompound("components") : new CompoundTag();
            if (comps.isEmpty()) return "{}";
            return comps.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析 SNBT 为 CompoundTag；解析失败返回 null */
    public static CompoundTag parseSnbt(String snbt) {
        if (snbt == null || snbt.trim().isEmpty()) return null;
        try {
            return TagParser.parseTag(snbt);
        } catch (Exception e) {
            return null;
        }
    }

    /** 从"完整组件 NBT"（components 子标签）重建物品栈：组件正确应用在 components 层（而非塞进 custom_data）；
     *  空/解析失败返回仅含 item 的基础栈 */
    public static ItemStack stackFromComponentsSnbt(Item item, CompoundTag componentsTag, net.minecraft.core.HolderLookup.Provider registries) {
        ItemStack stack = new ItemStack(item);
        if (componentsTag == null || componentsTag.isEmpty() || registries == null) return stack;
        try {
            CompoundTag stTag = new CompoundTag();
            stTag.putString("id", BuiltInRegistries.ITEM.getKey(item).toString());
            stTag.put("components", componentsTag);
            var parsed = ItemStack.parse(registries, stTag);
            if (parsed.isPresent()) return parsed.get();
        } catch (Exception ignored) {
        }
        return stack;
    }

    /** 品质名（1~6），越界返回空串 */
    public static String brewQualityName(int level) {
        if (level < 1 || level > BREW_MAX_LEVEL) return "";
        return BREW_QUALITY_NAMES[level - 1];
    }
}
