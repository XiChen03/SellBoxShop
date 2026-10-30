package com.sellbox.mod.integration.jei;

import net.minecraft.world.item.ItemStack;
import java.util.List;

public record ShopTradeRecipe(
        List<ItemStack> inputs,
        List<ItemStack> outputs,
        String tradeLabel,
        String detail,
        String category,
        boolean iconOnly
) {
    public ShopTradeRecipe(ItemStack input, ItemStack output, String tradeLabel, String detail, String category) {
        this(List.of(input), List.of(output), tradeLabel, detail, category, false);
    }
}
