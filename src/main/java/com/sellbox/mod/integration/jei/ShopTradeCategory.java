package com.sellbox.mod.integration.jei;

import com.sellbox.mod.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class ShopTradeCategory extends AbstractRecipeCategory<ShopTradeRecipe> {
    private final IDrawable arrow;

    public ShopTradeCategory(RecipeType<ShopTradeRecipe> type, String label, IGuiHelper guiHelper) {
        super(
                type,
                Component.literal(label),
                guiHelper.createDrawableItemStack(new ItemStack(ModItems.GOLD_COIN.get())),
                116, 72
        );
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ShopTradeRecipe recipe, IFocusGroup focuses) {
        int inputX = 4;
        int outputX = 70;
        int slotY = 16;

        if (!recipe.inputs().isEmpty()) {
            builder.addSlot(RecipeIngredientRole.INPUT, inputX, slotY)
                    .addItemStacks(recipe.inputs());
        }
        if (!recipe.outputs().isEmpty()) {
            var slot = builder.addSlot(RecipeIngredientRole.OUTPUT, outputX, slotY)
                    .addItemStacks(recipe.outputs());
            if (recipe.hideTooltip()) {
                slot.addTooltipCallback((view, tooltip) -> {
                    while (tooltip.size() > 1) {
                        tooltip.remove(tooltip.size() - 1);
                    }
                });
            }
        }
    }

    @Override
    public void draw(ShopTradeRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;

        boolean hasInput = !recipe.inputs().isEmpty();
        boolean hasOutput = !recipe.outputs().isEmpty();
        int price = recipe.priceAmount();

        int leftLabelY = 34;
        int rightLabelY = 34;

        if (price > 0) {
            ItemStack coin = new ItemStack(ModItems.GOLD_COIN.get());
            coin.setCount(Math.min(price, 99));
            if (hasOutput && !hasInput) {
                guiGraphics.renderFakeItem(coin, 4, 16);
                guiGraphics.renderItemDecorations(font, coin, 4, 16);
                int qty = recipe.outputs().get(0).getCount();
                guiGraphics.drawString(font, "\u6240\u9700\uFF1A\u00A5" + price, 4, leftLabelY, 0xFFAA0000, false);
                guiGraphics.drawString(font, "\u6240\u5F97\uFF1Ax" + qty, 70, rightLabelY, 0xFF008800, false);
            } else if (hasInput && !hasOutput) {
                guiGraphics.renderFakeItem(coin, 70, 16);
                guiGraphics.renderItemDecorations(font, coin, 70, 16);
                int qty = recipe.inputs().get(0).getCount();
                guiGraphics.drawString(font, "\u6240\u9700\uFF1Ax" + qty, 4, leftLabelY, 0xFFAA0000, false);
                guiGraphics.drawString(font, "\u6240\u5F97\uFF1A\u00A5" + price, 70, rightLabelY, 0xFF008800, false);
            }
        } else if (hasInput && hasOutput) {
            int inputQty = recipe.inputs().get(0).getCount();
            int outputQty = recipe.outputs().get(0).getCount();
            guiGraphics.drawString(font, "\u6240\u9700\uFF1Ax" + inputQty, 4, leftLabelY, 0xFFAA0000, false);
            guiGraphics.drawString(font, "\u6240\u5F97\uFF1Ax" + outputQty, 70, rightLabelY, 0xFF008800, false);
        } else if (hasOutput && !hasInput) {
            int qty = recipe.outputs().get(0).getCount();
            guiGraphics.drawString(font, "\u6240\u9700\uFF1A0", 4, leftLabelY, 0xFFAA0000, false);
            guiGraphics.drawString(font, "\u6240\u5F97\uFF1Ax" + qty, 70, rightLabelY, 0xFF008800, false);
        } else if (hasInput && !hasOutput) {
            int qty = recipe.inputs().get(0).getCount();
            guiGraphics.drawString(font, "\u6240\u9700\uFF1Ax" + qty, 4, leftLabelY, 0xFFAA0000, false);
            guiGraphics.drawString(font, "\u6240\u5F97\uFF1A0", 70, rightLabelY, 0xFF008800, false);
        }

        arrow.draw(guiGraphics, 46, 16);

        String tradeType = recipe.tradeTypeLabel();
        if (tradeType != null && !tradeType.isEmpty()) {
            var text = Component.literal(tradeType);
            int textWidth = font.width(text);
            int x = (getWidth() - textWidth) / 2;
            guiGraphics.drawString(font, text, x, 4, 0xFF404040, false);
        }

        String extraInfo = recipe.extraInfo();
        if (extraInfo != null && !extraInfo.isEmpty()) {
            var text = Component.literal(extraInfo);
            int textWidth = font.width(text);
            int x = (getWidth() - textWidth) / 2;
            guiGraphics.drawString(font, text, x, 48, 0xFF606060, false);
        }

        String categoryName = recipe.categoryName();
        if (categoryName != null && !categoryName.isEmpty()) {
            var text = Component.literal("\u4F4D\u7F6E\uFF1A" + categoryName);
            int textWidth = font.width(text);
            int x = (getWidth() - textWidth) / 2;
            guiGraphics.drawString(font, text, x, 60, 0xFF808080, false);
        }
    }
}
