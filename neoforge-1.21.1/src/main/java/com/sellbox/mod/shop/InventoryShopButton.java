package com.sellbox.mod.shop;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.integration.VSSIntegration;
import com.sellbox.mod.network.ModPayloads;
import com.sellbox.mod.shop.network.ShopOpenPayload;
import com.sellbox.mod.registry.ModMenuTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 物品栏界面（按 E 打开）中副手框上方的商店按钮。
 * 点击后打开 K 键对应的商店页面。
 */
public class InventoryShopButton {
    private static final ResourceLocation SELLBOX_ICON = ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "textures/item/sellbox1.png");

    // 副手槽在 InventoryMenu 中的偏移（77, 62），相对于界面左上角
    private static final int OFFHAND_X = 77;
    private static final int OFFHAND_Y = 62;
    private static final int SLOT_SIZE = 16;
    private static final int GAP = 5; // 副手框上方 5px

    private static int getButtonX(InventoryScreen screen) {
        return screen.getGuiLeft() + OFFHAND_X;
    }

    private static int getButtonY(InventoryScreen screen) {
        return screen.getGuiTop() + OFFHAND_Y - SLOT_SIZE - GAP;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(InventoryShopButton::onRender);
        NeoForge.EVENT_BUS.addListener(InventoryShopButton::onMouseClick);
    }

    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        if (screen.getMenu() == null) return;

        int x = getButtonX(screen);
        int y = getButtonY(screen);

        GuiGraphics gui = event.getGuiGraphics();
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        boolean hovered = mouseX >= x && mouseX <= x + SLOT_SIZE && mouseY >= y && mouseY <= y + SLOT_SIZE;

        // 仅显示出货箱图标（无背景、无边框）
        gui.blit(SELLBOX_ICON, x, y, 0, 0, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);

        // 悬浮提示
        if (hovered) {
            gui.renderTooltip(Minecraft.getInstance().font, Component.literal("打开商店"), mouseX, mouseY);
        }
    }

    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0) return;
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        if (screen.getMenu() == null) return;

        int x = getButtonX(screen);
        int y = getButtonY(screen);
        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();

        if (mouseX >= x && mouseX <= x + SLOT_SIZE && mouseY >= y && mouseY <= y + SLOT_SIZE) {
            openShop();
            event.setCanceled(true);
        }
    }

    private static void openShop() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen != null) mc.setScreen(null);

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
