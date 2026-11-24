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
    public static KeyMapping themeKey;
    public static KeyMapping extrasKey;

    public SellBoxModClient(IEventBus modEventBus) {
        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(this::onRegisterKeyMappings);
        MinecraftForge.EVENT_BUS.register(Tooltips.class);
        MinecraftForge.EVENT_BUS.register(BalanceOverlay.class);
        MinecraftForge.EVENT_BUS.addListener(SellBoxModClient::onRenderNotices);
        MinecraftForge.EVENT_BUS.addListener(SellBoxModClient::onThemeKey);
        MinecraftForge.EVENT_BUS.addListener(SellBoxModClient::onExtrasKey);
        MinecraftForge.EVENT_BUS.addListener(SellBoxModClient::onLoggingOut);
        MinecraftForge.EVENT_BUS.register(com.sellbox.mod.extras.client.SweeperDustbinButtons.class);
        com.sellbox.mod.integration.JEIIntegration.init();
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        shopKey = new KeyMapping("key.sellbox.shop", 75, "key.categories.sellbox");
        event.register(shopKey);
        ShopKeyHandler.register(shopKey);
        themeKey = new KeyMapping("key.sellbox.theme", 76, "key.categories.sellbox"); // L
        event.register(themeKey);
        extrasKey = new KeyMapping("key.sellbox.extras", 79, "key.categories.sellbox"); // O
        event.register(extrasKey);
    }

    /**
     * 断开（退出世界 / 被踢 / 换服）时清掉所有客户端静态状态。
     */
    private static void onLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        com.sellbox.mod.extras.client.ExtrasClientState.reset();
        com.sellbox.mod.extras.client.ExtrasNoticeQueue.clear();
        com.sellbox.mod.network.ModNetwork.clientPrices.clear();
        com.sellbox.mod.network.ModNetwork.clientExtrasState = "";
        com.sellbox.mod.network.ModNetwork.clientExtrasResponse = "";
    }

    /** 右下角成就式提醒（HUD 最上层，GUI 打开时也能看到） */
    private static void onRenderNotices(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
        com.sellbox.mod.extras.client.ExtrasNoticeOverlay.render(event.getGuiGraphics());
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenuTypes.SELL_BOX.get(), SellBoxScreen::new);
            MenuScreens.register(PlayerShopRegistry.MARKET_MENU.get(), MarketScreen::new);
            MenuScreens.register(PlayerShopRegistry.STOCK_MENU.get(), StockScreen::new);
        });
    }

    /** L 键打开主题切换界面 */
    private static void onThemeKey(net.minecraftforge.client.event.InputEvent.Key event) {
        if (themeKey == null || !themeKey.consumeClick()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof com.sellbox.mod.gui.ThemeSwitchScreen) {
            mc.setScreen(null);
            return;
        }
        if (mc.player != null && mc.screen == null) {
            com.sellbox.mod.gui.ThemeSwitchScreen.open();
        }
    }

    /** O 键打开扩展功能面板（签到/邮箱/兑换/转账/兑换码/好友） */
    private static void onExtrasKey(net.minecraftforge.client.event.InputEvent.Key event) {
        if (extrasKey == null || !extrasKey.consumeClick()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null) {
            if (ModNetwork.clientConfigSynced && !ModNetwork.clientExtrasEnabled) {
                mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("extras.msg.disabled"), true);
                return;
            }
            com.sellbox.mod.extras.client.ExtrasActions.open();
            mc.setScreen(new com.sellbox.mod.extras.client.ExtrasScreen());
        }
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