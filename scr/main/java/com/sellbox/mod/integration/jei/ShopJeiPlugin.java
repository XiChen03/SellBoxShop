package com.sellbox.mod.integration.jei;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.shop.ShopEntry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@JeiPlugin
public class ShopJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = new ResourceLocation(SellBoxMod.MODID, "shop_jei_plugin");

    public static final RecipeType<ShopTradeRecipe> SELLBOX_TYPE = RecipeType.create(
            SellBoxMod.MODID, "sellbox_trade", ShopTradeRecipe.class
    );

    private static IRecipeManager recipeManager;
    private static IJeiRuntime jeiRuntime;
    private static final Map<RecipeType<ShopTradeRecipe>, List<ShopTradeRecipe>> registeredRecipes = new ConcurrentHashMap<>();

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new ShopTradeCategory(SELLBOX_TYPE, "SellBox\u5546\u54C1", guiHelper)
        );
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        jeiRuntime = runtime;
        recipeManager = runtime.getRecipeManager();
    }

    @Override
    public void onRuntimeUnavailable() {
        jeiRuntime = null;
        recipeManager = null;
    }

    public static synchronized void setSellBoxRecipes(String category, List<ShopEntry> entries) {
        setRecipesForType(SELLBOX_TYPE, category, entries);
    }

    private static void setRecipesForType(RecipeType<ShopTradeRecipe> type, String categoryName, List<ShopEntry> entries) {
        if (recipeManager == null) return;

        List<ShopTradeRecipe> old = registeredRecipes.remove(type);
        if (old != null && !old.isEmpty()) {
            recipeManager.hideRecipes(type, old);
        }

        List<ShopTradeRecipe> newRecipes = buildRecipes(categoryName, entries);
        if (!newRecipes.isEmpty()) {
            recipeManager.addRecipes(type, newRecipes);
            registeredRecipes.put(type, newRecipes);
        }
    }

    private static List<ShopTradeRecipe> buildRecipes(String categoryName, List<ShopEntry> entries) {
        if (entries == null || entries.isEmpty()) return List.of();
        List<ShopTradeRecipe> recipes = new ArrayList<>();
        for (ShopEntry entry : entries) {
            ShopTradeRecipe recipe = convertToRecipe(entry, categoryName);
            if (recipe != null) {
                recipes.add(recipe);
            }
        }
        return recipes;
    }

    private static ShopTradeRecipe convertToRecipe(ShopEntry entry, String categoryName) {
        String entryType = entry.entryType();
        ItemStack entryStack = applyCustomName(entry.createStack(), entry.customName());
        int price = entry.price();

        if ("barter".equals(entryType) && entry.barterItemId() != null) {
            ItemStack offerStack = applyCustomName(entry.createBarterOfferStack(), "");
            return new ShopTradeRecipe(offerStack, entryStack, "\u4EE5\u7269\u6613\u7269", null, categoryName, 0, false);
        }

        if ("command".equals(entryType)) {
            String xpInfo = "\u2714 " + entryStack.getHoverName().getString();
            return new ShopTradeRecipe(List.of(), List.of(entryStack), "\u8D2D\u4E70", xpInfo, categoryName, Math.max(1, price), true);
        }

        if ("xp".equals(entryType)) {
            String xpInfo = entry.extraData() + " \u7ECF\u9A8C";
            return new ShopTradeRecipe(List.of(), List.of(entryStack), "\u8D2D\u4E70", xpInfo, categoryName, Math.max(1, price), true);
        }

        if (entry.tradeType() == ShopEntry.TradeType.BUY) {
            return new ShopTradeRecipe(List.of(), List.of(entryStack), "\u8D2D\u4E70", null, categoryName, Math.max(1, price), false);
        }

        if (entry.tradeType() == ShopEntry.TradeType.SELL) {
            return new ShopTradeRecipe(List.of(entryStack), List.of(), "\u51FA\u552E", null, categoryName, Math.max(1, price), false);
        }

        return null;
    }

    private static ItemStack applyCustomName(ItemStack stack, String customName) {
        if (customName != null && !customName.isEmpty()) {
            stack.setHoverName(Component.literal(customName));
        }
        return stack;
    }

    public static void showRecipes(ItemStack stack) {
        if (jeiRuntime == null) return;
        var focus = jeiRuntime.getJeiHelpers().getFocusFactory().createFocus(
                RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack
        );
        jeiRuntime.getRecipesGui().show(List.of(focus));
    }

    public static void showUses(ItemStack stack) {
        if (jeiRuntime == null) return;
        var focus = jeiRuntime.getJeiHelpers().getFocusFactory().createFocus(
                RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, stack
        );
        jeiRuntime.getRecipesGui().show(List.of(focus));
    }
}
