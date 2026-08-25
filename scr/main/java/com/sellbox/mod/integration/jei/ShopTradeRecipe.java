package com.sellbox.mod.integration.jei;

import net.minecraft.world.item.ItemStack;
import java.util.List;

public record ShopTradeRecipe(
    List<ItemStack> inputs,
    List<ItemStack> outputs,
    String tradeTypeLabel,
    String extraInfo,
    String categoryName,
    int priceAmount,
    boolean hideTooltip
) {
    public ShopTradeRecipe(ItemStack input, ItemStack output, String tradeTypeLabel, String extraInfo, String categoryName, int priceAmount, boolean hideTooltip) {
        this(List.of(input), List.of(output), tradeTypeLabel, extraInfo, categoryName, priceAmount, hideTooltip);
    }
}
