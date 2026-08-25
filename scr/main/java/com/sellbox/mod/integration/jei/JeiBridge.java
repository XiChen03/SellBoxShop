package com.sellbox.mod.integration.jei;

import com.sellbox.mod.shop.ShopEntry;
import net.minecraft.world.item.ItemStack;
import java.util.List;

public class JeiBridge {
    private static boolean checked = false;
    private static boolean available = false;

    public static boolean isAvailable() {
        if (!checked) {
            try {
                Class.forName("mezz.jei.api.IModPlugin");
                available = true;
            } catch (ClassNotFoundException e) {
                available = false;
            }
            checked = true;
        }
        return available;
    }

    public static void setSellBoxRecipes(String category, List<ShopEntry> entries) {
        if (!isAvailable()) return;
        try {
            Class.forName("com.sellbox.mod.integration.jei.ShopJeiPlugin")
                    .getMethod("setSellBoxRecipes", String.class, List.class)
                    .invoke(null, category, entries);
        } catch (Exception e) {
            // ignore
        }
    }

    public static void showRecipes(ItemStack stack) {
        if (!isAvailable()) return;
        try {
            Class.forName("com.sellbox.mod.integration.jei.ShopJeiPlugin")
                    .getMethod("showRecipes", ItemStack.class)
                    .invoke(null, stack);
        } catch (Exception e) {
            // ignore
        }
    }

    public static void showUses(ItemStack stack) {
        if (!isAvailable()) return;
        try {
            Class.forName("com.sellbox.mod.integration.jei.ShopJeiPlugin")
                    .getMethod("showUses", ItemStack.class)
                    .invoke(null, stack);
        } catch (Exception e) {
            // ignore
        }
    }
}
