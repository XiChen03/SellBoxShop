package com.sellbox.mod;

import com.sellbox.mod.gui.SellBoxScreen;
import com.sellbox.mod.network.ModNetwork;
import com.sellbox.mod.playershop.MarketScreen;
import com.sellbox.mod.playershop.StockScreen;
import com.sellbox.mod.registry.ModMenuTypes;
import com.sellbox.mod.playershop.PlayerShopRegistry;
import com.sellbox.mod.shop.ShopKeyHandler;
import com.sellbox.mod.shop.ShopScreen;
import com.sellbox.mod.client.BalanceOverlay;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import net.minecraft.ChatFormatting;

public class SellBoxModClient {
    public static KeyMapping shopKey;

    public SellBoxModClient(IEventBus modEventBus) {
        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(this::onRegisterKeyMappings);
        MinecraftForge.EVENT_BUS.register(Tooltips.class);
        MinecraftForge.EVENT_BUS.register(BalanceOverlay.class);
        com.sellbox.mod.integration.JEIIntegration.init();
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        shopKey = new KeyMapping("key.sellbox.shop", 75, "key.categories.sellbox");
        event.register(shopKey);
        ShopKeyHandler.register(shopKey);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenuTypes.SELL_BOX.get(), SellBoxScreen::new);
            MenuScreens.register(PlayerShopRegistry.MARKET_MENU.get(), MarketScreen::new);
            MenuScreens.register(PlayerShopRegistry.STOCK_MENU.get(), StockScreen::new);
        });
    }

    public static class Tooltips {
        @SubscribeEvent
        public static void onItemTooltip(ItemTooltipEvent event) {
            ItemStack stack = event.getItemStack();
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            Integer price = ModNetwork.clientPrices.get(id);
            // NBT 精确级价格优先（如酒品质 BrewLevel）
            if (stack.getTag() != null) {
                Integer exact = ModNetwork.clientPrices.get(id + "|" + stack.getTag().toString());
                if (exact != null) price = exact;
            }
            if (price != null && price > 0) {
                if (Screen.hasShiftDown()) {
                    event.getToolTip().add(Component.translatable("tooltip.sellbox.total_price", String.valueOf(price * stack.getCount())).withStyle(ChatFormatting.YELLOW));
                } else {
                    event.getToolTip().add(Component.translatable("tooltip.sellbox.unit_price", String.valueOf(price)).withStyle(ChatFormatting.GOLD));
                    event.getToolTip().add(Component.translatable("tooltip.sellbox.shift_hint").withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC));
                }
            }
        }
    }
}