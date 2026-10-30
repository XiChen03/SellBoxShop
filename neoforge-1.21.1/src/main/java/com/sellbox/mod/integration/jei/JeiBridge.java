package com.sellbox.mod.integration.jei;

import com.sellbox.mod.shop.ShopEntry;
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

    public static void updateShopRecipes(String category, List<ShopEntry> entries) {
        if (!isAvailable()) return;
        try {
            Class.forName("com.sellbox.mod.integration.jei.ShopJeiPlugin")
                    .getMethod("setCategoryRecipes", String.class, List.class)
                    .invoke(null, category, entries);
        } catch (Exception e) {
            // ignore
        }
    }
}
