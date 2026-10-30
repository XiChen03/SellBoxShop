package com.sellbox.mod.util;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 物品"代表性栈"查询。
 *
 * <p>裸 {@code new ItemStack(item)} 不带任何 NBT / 组件，而部分模组的物品图标是靠这些数据
 * 现场合成的（例如森罗物语：烟火的秘制烤串，会从串上食材的贴图取色合成 16x16 图标）。
 * 没有真实数据的栈，这类物品就只剩一块黑框。
 *
 * <p>采样优先级：<b>玩家背包 &gt; 创造模式物品栏</b>。
 * 背包优先是因为像秘制烤串这种物品只能由玩家自己串出来，创造栏里那份只是模组给的预览桩，
 * 数据未必完整；玩家手上那一串才是真正的展示形态。
 *
 * <p>返回的栈是缓存里的同一份实例，调用方需要修改时必须自己 {@link ItemStack#copy()}。
 */
public class ItemSampleStacks {

    /** 创造模式物品栏的采样表，按存档/维度缓存（内容不会随时间变） */
    private static Map<Item, ItemStack> creativeCache = Map.of();
    private static HolderLookup.Provider cachedHolders;

    private ItemSampleStacks() {}

    /**
     * 取一份全物品的代表性栈快照。背包是活的，所以每次调用都重新扫，
     * 调用方应当只在重建列表时取一次，不要在每帧渲染里调。
     */
    public static Map<Item, ItemStack> snapshot() {
        Map<Item, ItemStack> map = new HashMap<>(creativeSamples());
        collectPlayerInventory(map);
        return map;
    }

    /** 取代表性栈；物品不在背包也不在任何创造栏里时返回 {@link ItemStack#EMPTY} */
    public static ItemStack of(Item item) {
        ItemStack s = snapshot().get(item);
        return s == null ? ItemStack.EMPTY : s;
    }

    private static void collectPlayerInventory(Map<Item, ItemStack> out) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) out.put(stack.getItem(), stack);   // 背包里的覆盖创造栏的
        }
    }

    private static Map<Item, ItemStack> creativeSamples() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return Map.of();
        HolderLookup.Provider holders = mc.level.registryAccess();
        if (creativeCache.isEmpty() || cachedHolders != holders) {
            creativeCache = buildCreative(mc.level.enabledFeatures(), holders);
            cachedHolders = holders;
        }
        return creativeCache;
    }

    private static Map<Item, ItemStack> buildCreative(FeatureFlagSet features, HolderLookup.Provider holders) {
        try {
            // 玩家还没开过创造模式物品栏时，各 tab 的 displayItems 是空的，这里补一次构建；
            // tryRebuildTabContents 内部按参数缓存，重复调用不会重复构建
            CreativeModeTabs.tryRebuildTabContents(features, true, holders);
        } catch (Throwable ignored) {
        }
        Map<Item, ItemStack> map = new HashMap<>();
        try {
            for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
                for (ItemStack stack : tab.getDisplayItems()) {
                    if (!stack.isEmpty()) map.putIfAbsent(stack.getItem(), stack.copy());
                }
            }
        } catch (Throwable ignored) {
        }
        return map;
    }
}