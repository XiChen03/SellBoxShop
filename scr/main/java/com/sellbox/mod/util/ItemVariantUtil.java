package com.sellbox.mod.util;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 支持"酿造等级品质变体"的物品工具类（兼容 Kaleidoscope Tavern 等具有
 * getBrewLevel(ItemStack) 静态方法的酒类模组物品）。
 * 品质以物品根 NBT 的 int 键 "BrewLevel"（0~6）区分。
 */
public final class ItemVariantUtil {

    public static final String BREW_LEVEL_KEY = "BrewLevel";
    public static final int BREW_MAX_LEVEL = 6;

    /** BrewLevel 1~6 对应的中文品质名（与酒馆模组 lang 一致） */
    public static final String[] BREW_QUALITY_NAMES = {"难以下咽", "劣质", "普通", "优质", "精酿", "典藏"};

    private ItemVariantUtil() {
    }

    /**
     * 判断物品是否支持 BrewLevel 品质变体：通过反射检测是否存在
     * 公开静态方法 getBrewLevel(ItemStack)（酒馆的 BottleBlockItem 及其子类均有）。
     */
    public static boolean hasBrewLevel(Item item) {
        try {
            item.getClass().getMethod("getBrewLevel", ItemStack.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /** 构造指定 BrewLevel 等级的物品栈（携带 BrewLevel NBT） */
    public static ItemStack withBrewLevel(Item item, int level) {
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putInt(BREW_LEVEL_KEY, Math.max(0, Math.min(BREW_MAX_LEVEL, level)));
        return stack;
    }

    /** 品质名（1~6），越界返回空串 */
    public static String brewQualityName(int level) {
        if (level < 1 || level > BREW_MAX_LEVEL) return "";
        return BREW_QUALITY_NAMES[level - 1];
    }
}
