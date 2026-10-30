package com.sellbox.mod.integration.jei;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
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
    public static final RecipeType<ShopTradeRecipe> TYPE = RecipeType.create(
            SellBoxMod.MODID, "shop_trade", ShopTradeRecipe.class
    );

    private final IDrawable arrow;

    public ShopTradeCategory(IGuiHelper guiHelper) {
        super(
                TYPE,
                Component.literal("商店"),
                guiHelper.createDrawableItemStack(new ItemStack(ModItems.GOLD_COIN.get())),
                116, 68
        );
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ShopTradeRecipe recipe, IFocusGroup focuses) {
        var inputs = recipe.inputs();
        var outputs = recipe.outputs();

        int inputX = 4;
        int outputX = 70;
        int slotY = 18;

        if (!inputs.isEmpty()) {
            builder.addSlot(RecipeIngredientRole.INPUT, inputX, slotY)
                    .addItemStacks(inputs);
        }
        if (!outputs.isEmpty()) {
            var outputSlot = builder.addSlot(RecipeIngredientRole.OUTPUT, outputX, slotY)
                    .addItemStacks(outputs);
            if (recipe.iconOnly()) {
                outputSlot.setSlotName("icon")
                        .addRichTooltipCallback((IRecipeSlotView view, mezz.jei.api.gui.builder.ITooltipBuilder tooltip) -> {
                            var stack = view.getDisplayedItemStack();
                            if (stack.isPresent()) {
                                tooltip.clear();
                                tooltip.add(stack.get().getHoverName());
                            }
                        });
            }
        }
    }

    @Override
    public void draw(ShopTradeRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;

        arrow.draw(guiGraphics, 46, 18);

        if (recipe.tradeLabel() != null) {
            drawCentered(guiGraphics, font, recipe.tradeLabel(), 4, 0xFF808080);
        }

        int totalInput = recipe.inputs().stream().mapToInt(ItemStack::getCount).sum();
        drawLeft(guiGraphics, font, "所需：" + totalInput, 4, 37, 0xFFAAAAAA);

        int totalOutput = recipe.outputs().stream().mapToInt(ItemStack::getCount).sum();
        drawLeft(guiGraphics, font, "所得：" + totalOutput, 70, 37, 0xFFAAAAAA);

        if (recipe.detail() != null) {
            drawCentered(guiGraphics, font, recipe.detail(), 48, 0xFFAAAAAA);
            drawCentered(guiGraphics, font, "位置：" + recipe.category(), 58, 0xFF888888);
        } else {
            drawCentered(guiGraphics, font, "位置：" + recipe.category(), 48, 0xFF888888);
        }
    }

    private void drawCentered(GuiGraphics guiGraphics, net.minecraft.client.gui.Font font, String text, int y, int color) {
        int textWidth = font.width(Component.literal(text));
        int x = (getWidth() - textWidth) / 2;
        guiGraphics.drawString(font, text, x, y, color, false);
    }

    private void drawLeft(GuiGraphics guiGraphics, net.minecraft.client.gui.Font font, String text, int x, int y, int color) {
        guiGraphics.drawString(font, text, x, y, color, false);
    }
}
