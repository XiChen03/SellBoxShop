package com.sellbox.mod.shop;

import com.sellbox.mod.integration.VSSIntegration;
import com.sellbox.mod.network.ModPayloads;
import com.sellbox.mod.shop.network.ShopOpenPayload;
import com.sellbox.mod.registry.ModMenuTypes;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

public class ShopKeyHandler {
    private static KeyMapping shopKey;

    public static void register(KeyMapping keyMapping) {
        shopKey = keyMapping;
        NeoForge.EVENT_BUS.addListener(ShopKeyHandler::onKeyInput);
    }

    public static KeyMapping getShopKey() {
        return shopKey;
    }

    private static void onKeyInput(InputEvent.Key event) {
        if (shopKey != null && shopKey.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.screen == null) {
                if (!ModPayloads.clientConfigSynced) return;
                if (VSSIntegration.isLoaded() && ModPayloads.clientVssEnabled && !ModPayloads.clientShopOverride) {
                    mc.player.displayClientMessage(Component.literal("VSS经济模式下商店已禁用"), true);
                    return;
                }
                mc.setScreen(new ShopScreen(
                        new ShopMenu(0, mc.player.getInventory()),
                        mc.player.getInventory(),
                        Component.translatable("gui.shop.title")
                ));
                PacketDistributor.sendToServer(new ShopOpenPayload(""));
            }
        }
    }
}
