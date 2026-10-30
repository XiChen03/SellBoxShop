package com.sellbox.mod.shop;

import com.sellbox.mod.config.SellBoxConfig;
import com.sellbox.mod.integration.SDMShopIntegration;
import com.sellbox.mod.network.ModNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;

public class ShopKeyHandler {
    private static final String KEY_NAME = "key.sellbox.shop";
    private static KeyMapping shopKey;

    public static void register(KeyMapping keyMapping) {
        shopKey = keyMapping;
        MinecraftForge.EVENT_BUS.addListener(ShopKeyHandler::onKeyInput);
    }

    private static void onKeyInput(InputEvent.Key event) {
        if (shopKey != null && shopKey.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.screen == null) {
                if (!ModNetwork.clientConfigSynced) return;
                if (SDMShopIntegration.isLoaded() && ModNetwork.clientSdmEnabled && !ModNetwork.clientShopOverride) {
                    mc.player.displayClientMessage(Component.literal("SDMShop经济模式下商店已禁用"), true);
                    return;
                }
                mc.setScreen(new ShopScreen(
                        new ShopMenu(0, mc.player.getInventory()),
                        mc.player.getInventory(),
                        Component.translatable("gui.shop.title")
                ));
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.ShopOpenPacket(""));
            }
        }
    }
}