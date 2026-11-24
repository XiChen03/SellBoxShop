package com.sellbox.mod.client;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.integration.SDMShopIntegration;
import com.sellbox.mod.integration.VSSIntegration;
import com.sellbox.mod.network.ModNetwork;
import com.sellbox.mod.shop.ShopMenu;
import com.sellbox.mod.shop.ShopScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 物品栏界面里的商店按钮，生存背包与创造背包各有一个位置。
 * <p>
 * <b>实现方式：纯事件绘制，不挂成 widget。</b>
 * 原因：{@code CreativeModeInventoryScreen} 切页签时会调 {@code rebuildWidgets()}，
 * 把 {@code ScreenEvent.Init.Post} 里 addListener 进去的 widget 全部清掉，
 * 而 rebuildWidgets 不会再触发一次 Init.Post —— 表现就是「第一次打开有按钮，切个页签就没了」。
 * 改成在 {@code ScreenEvent.Render.Post} 里直接画、在 {@code MouseButtonPressed.Pre} 里判定点击，
 * 就不受任何 UI 重建影响。
 */
@Mod.EventBusSubscriber(modid = SellBoxMod.MODID, value = Dist.CLIENT)
public final class InventoryShopButton {
    private static final ResourceLocation SELLBOX_ICON =
            new ResourceLocation(SellBoxMod.MODID, "textures/item/sellbox1.png");

    private static final int ICON_SIZE = 16;

    // ── 生存背包：副手槽（InventoryMenu 偏移 77,62）上方 5px ──
    private static final int SURVIVAL_X = 77;
    private static final int SURVIVAL_Y = 62 - ICON_SIZE - 5;

    private static final Component TITLE = Component.literal("打开商店");

    private InventoryShopButton() {
    }

    /** 只有生存背包显示；创造背包不显示 */
    private static boolean applies(Screen screen) {
        return screen instanceof InventoryScreen i && i.getMenu() != null;
    }

    private static int iconX(AbstractContainerScreen<?> screen) {
        return screen.getGuiLeft() + SURVIVAL_X;
    }

    private static int iconY(AbstractContainerScreen<?> screen) {
        return screen.getGuiTop() + SURVIVAL_Y;
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!applies(screen) || !(screen instanceof AbstractContainerScreen<?> container)) return;

        int x = iconX(container);
        int y = iconY(container);
        int mx = event.getMouseX();
        int my = event.getMouseY();
        boolean hovered = mx >= x && mx < x + ICON_SIZE && my >= y && my < y + ICON_SIZE;

        GuiGraphics g = event.getGuiGraphics();
        if (hovered) {
            g.fill(x - 1, y - 1, x + ICON_SIZE + 1, y + ICON_SIZE + 1, 0x33FFFFFF);
        }
        g.blit(SELLBOX_ICON, x, y, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        if (hovered) {
            g.renderTooltip(Minecraft.getInstance().font, java.util.List.of(TITLE),
                    java.util.Optional.empty(), mx, my);
        }
    }

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0) return;
        Screen screen = event.getScreen();
        if (!applies(screen) || !(screen instanceof AbstractContainerScreen<?> container)) return;

        int x = iconX(container);
        int y = iconY(container);
        double mx = event.getMouseX();
        double my = event.getMouseY();
        if (mx < x || mx >= x + ICON_SIZE || my < y || my >= y + ICON_SIZE) return;

        event.setCanceled(true);
        openShop();
    }

    private static void openShop() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen != null) mc.setScreen(null);

        if (!ModNetwork.clientConfigSynced) return;
        boolean externalEco = (SDMShopIntegration.isLoaded() && ModNetwork.clientSdmEnabled)
                || (VSSIntegration.isLoaded() && ModNetwork.clientVssEnabled);
        if (externalEco && !ModNetwork.clientShopOverride) {
            mc.player.displayClientMessage(Component.literal("外部经济模式下商店已禁用"), true);
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
