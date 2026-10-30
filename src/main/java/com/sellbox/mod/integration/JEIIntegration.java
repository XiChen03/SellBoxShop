package com.sellbox.mod.integration;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.shop.ShopEntry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class JEIIntegration {
    private static boolean jeiPresent = false;
    private static boolean initialized = false;

    private static final Map<String, List<ShopEntry>> clientShopCategories = new ConcurrentHashMap<>();
    private static List<ShopEntry> flatShopEntries = Collections.emptyList();

    public static void onShopEntriesUpdated(Map<String, List<ShopEntry>> categories) {
        clientShopCategories.clear();
        if (categories != null) {
            clientShopCategories.putAll(categories);
        }
        List<ShopEntry> all = new ArrayList<>();
        for (List<ShopEntry> list : clientShopCategories.values()) {
            if (list != null) all.addAll(list);
        }
        flatShopEntries = Collections.unmodifiableList(all);
    }

    public static void init() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> modListClass = Class.forName("net.minecraftforge.fml.ModList");
            Method isLoaded = modListClass.getMethod("isLoaded", String.class);
            Object result = isLoaded.invoke(null, "jei");
            jeiPresent = result instanceof Boolean && (Boolean) result;
        } catch (Throwable t) {
            jeiPresent = false;
        }
        if (jeiPresent) {
            SellBoxMod.LOGGER.info("JEI detected, admin shop recipe hints enabled");
        }
    }

    public static boolean isJeiPresent() {
        return jeiPresent;
    }

    public static List<ShopEntry> findRelatedEntries(ShopEntry entry) {
        if (entry == null) return Collections.emptyList();
        String itemId = entry.itemId().toString();
        List<ShopEntry> related = new ArrayList<>();
        for (ShopEntry e : flatShopEntries) {
            if (e == entry) continue;
            if (itemId.equals(e.itemId().toString())) {
                if (itemId.equals(e.itemId().toString()) && e.tradeType() == entry.tradeType()
                        && e.price() == entry.price() && e.quantity() == entry.quantity()
                        && Objects.equals(e.entryType(), entry.entryType())) continue;
                related.add(e);
            }
        }
        return related;
    }

    public static ItemStack getCoinStack() {
        try {
            Class<?> cls = Class.forName("com.sellbox.mod.registry.ModItems");
            Object goldCoin = cls.getField("GOLD_COIN").get(null);
            if (goldCoin instanceof net.minecraftforge.registries.RegistryObject<?> ro) {
                Object item = ro.get();
                if (item instanceof net.minecraft.world.item.Item itemCast) {
                    return new ItemStack(itemCast);
                }
            }
        } catch (Throwable ignored) {}
        return new ItemStack(Items.GOLD_NUGGET);
    }
}
