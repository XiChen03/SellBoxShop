package com.sellbox.mod.client;

import com.sellbox.mod.gui.GuiDraw;
import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.integration.CuriosIntegration;
import com.sellbox.mod.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class BalanceOverlay {
    private static final ResourceLocation COIN_ICON = new ResourceLocation("sellbox:textures/gui/coin.png");
    private static boolean cachedHasMoney = false;
    private static long lastEquippedCheck = 0;

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 每帧反射查 Curios 开销不小；缓存 500ms，且只属于本地玩家，不会串数据
        long now = System.currentTimeMillis();
        if (now - lastEquippedCheck > 500) {
            lastEquippedCheck = now;
            cachedHasMoney = CuriosIntegration.hasMoneyEquipped(mc.player);
        }
        if (!cachedHasMoney) return;
        if (mc.options.renderDebug) return;

        int balance = ModNetwork.clientBalance;
        if (balance < 0) return;

        GuiGraphics gui = event.getGuiGraphics();
        int iconSize = 16;
        int padding = 4;
        String text = String.valueOf(balance);
        int x = padding;
        int y = padding;

        gui.blit(COIN_ICON, x, y, 0, 0, iconSize, iconSize, iconSize, iconSize);
        gui.drawString(mc.font, text, x + iconSize + padding, y + (iconSize - 8) / 2 + 1, 0xFFFFFF,GuiDraw.shadow());
    }
}