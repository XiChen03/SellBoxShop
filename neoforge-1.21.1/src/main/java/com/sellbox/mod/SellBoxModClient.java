package com.sellbox.mod;

import com.sellbox.mod.gui.SellBoxScreen;
import com.sellbox.mod.network.ModPayloads;
import com.sellbox.mod.playershop.MarketScreen;
import com.sellbox.mod.playershop.PlayerShopRegistry;
import com.sellbox.mod.playershop.StockScreen;
import com.sellbox.mod.registry.ModMenuTypes;
import com.sellbox.mod.shop.ShopKeyHandler;
import com.sellbox.mod.shop.ShopScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@Mod(value = SellBoxMod.MODID, dist = Dist.CLIENT)
public class SellBoxModClient {
    public static KeyMapping shopKey;
    public static KeyMapping themeKey;
    public static KeyMapping extrasKey;

    public SellBoxModClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(this::onRegisterMenuScreens);
        modEventBus.addListener(this::onRegisterKeyMappings);
        modEventBus.addListener(this::onRegisterGuiLayers);
        NeoForge.EVENT_BUS.register(Tooltips.class);
        NeoForge.EVENT_BUS.addListener(SellBoxModClient::onThemeKey);
        NeoForge.EVENT_BUS.addListener(SellBoxModClient::onExtrasKey);
        NeoForge.EVENT_BUS.addListener(SellBoxModClient::onLoggingOut);
        com.sellbox.mod.shop.InventoryShopButton.register();
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        shopKey = new KeyMapping("打开管理员商店", 75, "SellBox");
        event.register(shopKey);
        ShopKeyHandler.register(shopKey);
        themeKey = new KeyMapping("切换GUI主题", 76, "SellBox"); // L
        event.register(themeKey);
        extrasKey = new KeyMapping("key.sellbox.extras", 79, "SellBox"); // O
        event.register(extrasKey);
    }

    /** L 键打开主题切换界面 */
    private static void onThemeKey(net.neoforged.neoforge.client.event.InputEvent.Key event) {
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
    private static void onExtrasKey(net.neoforged.neoforge.client.event.InputEvent.Key event) {
        if (extrasKey == null || !extrasKey.consumeClick()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null) {
            if (com.sellbox.mod.network.ModPayloads.clientConfigSynced
                    && !com.sellbox.mod.network.ModPayloads.clientExtrasEnabled) {
                mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("extras.msg.disabled"), true);
                return;
            }
            com.sellbox.mod.extras.client.ExtrasActions.open();
            mc.setScreen(new com.sellbox.mod.extras.client.ExtrasScreen());
        }
    }

    /**
     * 断开（退出世界 / 被踢 / 换服）时清掉所有客户端静态状态。
     * <p>
     * 这些状态挂在静态字段上，不会随实例销毁；不清就会串服。
     */
    private static void onLoggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        com.sellbox.mod.extras.client.ExtrasClientState.reset();
        com.sellbox.mod.extras.client.ExtrasNoticeQueue.clear();
        com.sellbox.mod.network.ModPayloads.clientPrices.clear();
        com.sellbox.mod.network.ModPayloads.clientExtrasState = "";
        com.sellbox.mod.network.ModPayloads.clientExtrasResponse = "";
    }

    /** 注册右下角成就式提醒层（HUD 最上层，GUI 打开时也能看到） */
    private void onRegisterGuiLayers(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "extras_notice"),
                com.sellbox.mod.extras.client.ExtrasNoticeOverlay::render);
    }

    private void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.SELL_BOX.get(), SellBoxScreen::new);
        event.register(PlayerShopRegistry.MARKET_MENU.get(), MarketScreen::new);
        event.register(PlayerShopRegistry.STOCK_MENU.get(), StockScreen::new);
    }

    public static class Tooltips {
        @SubscribeEvent
        public static void onItemTooltip(ItemTooltipEvent event) {
            ItemStack stack = event.getItemStack();
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            Integer price = ModPayloads.clientPrices.get(id);
            // NBT 精确级价格优先（完整组件 SNBT，等价于 1.20.1 根 NBT）；无组件时按 id|{} 查询，与 nbtPriceKey 语义一致
            var level = Minecraft.getInstance().level;
            var registries = level != null ? level.registryAccess() : null;
            String nbtStr = registries != null ? com.sellbox.mod.util.ItemVariantUtil.brewNbtSnbt(stack, registries) : null;
            Integer exact = (nbtStr != null) ? ModPayloads.clientPrices.get(id + "|" + nbtStr) : null;
            if (exact != null) price = exact;
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
