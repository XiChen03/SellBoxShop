package com.sellbox.mod.client;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.integration.CuriosIntegration;
import com.sellbox.mod.network.ModPayloads;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@EventBusSubscriber(modid = SellBoxMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class BalanceOverlay {
    private static final ResourceLocation COIN_ICON = ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "textures/gui/coin.png");
    private static boolean cachedHasMoney = false;
    private static long lastEquippedCheck = 0;

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "balance"),
                BalanceOverlay::render);
    }

    private static void render(GuiGraphics gui, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        long now = System.currentTimeMillis();
        if (now - lastEquippedCheck > 500) {
            lastEquippedCheck = now;
            cachedHasMoney = CuriosIntegration.hasMoneyEquipped(mc.player);
        }
        if (!cachedHasMoney) return;
        if (mc.getDebugOverlay().showDebugScreen()) return;

        int balance = ModPayloads.clientBalance;
        if (balance < 0) return;

        int iconSize = 16;
        int padding = 4;
        String text = String.valueOf(balance);
        int x = padding;
        int y = padding;

        gui.blit(COIN_ICON, x, y, 0, 0, iconSize, iconSize, iconSize, iconSize);
        gui.drawString(mc.font, text, x + iconSize + padding, y + (iconSize - 8) / 2 + 1, 0xFFFFFF, true);
    }
}
