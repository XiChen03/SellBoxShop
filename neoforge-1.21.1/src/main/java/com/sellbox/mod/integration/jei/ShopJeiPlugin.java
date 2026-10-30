package com.sellbox.mod.integration.jei;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.registry.ModItems;
import com.sellbox.mod.shop.ShopEntry;
import com.sellbox.mod.shop.ShopScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@JeiPlugin
public class ShopJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "shop_jei_plugin");

    private static IRecipeManager recipeManager;
    private static final Map<String, List<ShopTradeRecipe>> categoryRecipes = new ConcurrentHashMap<>();

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new ShopTradeCategory(guiHelper));
    }

    @Override
    public void registerGuiHandlers(mezz.jei.api.registration.IGuiHandlerRegistration registration) {
        registration.addGlobalGuiHandler(new IGlobalGuiHandler() {
            @Override
            public Optional<mezz.jei.api.runtime.IClickableIngredient<?>> getClickableIngredientUnderMouse(
                    mezz.jei.api.gui.builder.IClickableIngredientFactory factory, double mouseX, double mouseY) {
                Screen screen = Minecraft.getInstance().screen;
                if (!(screen instanceof ShopScreen shopScreen)) return Optional.empty();
                ItemStack stack = shopScreen.getItemAt((int) mouseX, (int) mouseY);
                if (stack == null || stack.isEmpty()) return Optional.empty();
                var result = factory.createBuilder(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, stack)
                        .buildWithArea((int) mouseX, (int) mouseY, 1, 1);
                return result.map(x -> (mezz.jei.api.runtime.IClickableIngredient<?>) x);
            }
        });
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        recipeManager = jeiRuntime.getRecipeManager();
    }

    @Override
    public void onRuntimeUnavailable() {
        recipeManager = null;
    }

    public static synchronized void setCategoryRecipes(String category, List<ShopEntry> entries) {
        if (recipeManager == null) return;

        List<ShopTradeRecipe> old = categoryRecipes.remove(category);
        if (old != null && !old.isEmpty()) {
            recipeManager.hideRecipes(ShopTradeCategory.TYPE, old);
        }

        List<ShopTradeRecipe> newRecipes = buildRecipes(category, entries);
        if (!newRecipes.isEmpty()) {
            recipeManager.addRecipes(ShopTradeCategory.TYPE, newRecipes);
            categoryRecipes.put(category, newRecipes);
        }
    }

    private static List<ShopTradeRecipe> buildRecipes(String category, List<ShopEntry> entries) {
        if (entries == null || entries.isEmpty()) return List.of();
        List<ShopTradeRecipe> recipes = new ArrayList<>();
        ItemStack goldCoin = new ItemStack(ModItems.GOLD_COIN.get());
        for (ShopEntry entry : entries) {
            ShopTradeRecipe recipe = convertToRecipe(entry, category, goldCoin);
            if (recipe != null) {
                recipes.add(recipe);
            }
        }
        return recipes;
    }

    private static ShopTradeRecipe convertToRecipe(ShopEntry entry, String category, ItemStack goldCoin) {
        String entryType = entry.entryType();
        ItemStack entryStack = applyCustomName(entry.createStack(), entry.customName());

        if ("barter".equals(entryType) && entry.barterItemId() != null) {
            ItemStack offerStack = applyCustomName(entry.createBarterOfferStack(), entry.customName());
            return new ShopTradeRecipe(offerStack, entryStack, "以物易物", null, category);
        }

        if ("command".equals(entryType)) {
            ItemStack priceStack = goldCoin.copyWithCount(Math.max(1, entry.price()));
            return new ShopTradeRecipe(List.of(priceStack), List.of(entryStack), "购买", null, category, true);
        }

        if ("xp".equals(entryType)) {
            ItemStack priceStack = goldCoin.copyWithCount(Math.max(1, entry.price()));
            return new ShopTradeRecipe(List.of(priceStack), List.of(entryStack), "购买", entry.extraData() + " 经验", category, true);
        }

        if (entry.tradeType() == ShopEntry.TradeType.BUY) {
            ItemStack priceStack = goldCoin.copyWithCount(Math.max(1, entry.price()));
            return new ShopTradeRecipe(priceStack, entryStack, "购买", null, category);
        }

        if (entry.tradeType() == ShopEntry.TradeType.SELL) {
            ItemStack priceStack = goldCoin.copyWithCount(Math.max(1, entry.price()));
            return new ShopTradeRecipe(entryStack, priceStack, "出售", null, category);
        }

        return null;
    }

    private static ItemStack applyCustomName(ItemStack stack, String customName) {
        if (customName != null && !customName.isEmpty()) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(customName));
        }
        return stack;
    }
}
