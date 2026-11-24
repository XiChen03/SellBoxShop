package com.sellbox.mod.extras.client;

import com.sellbox.mod.SellBoxMod;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 垃圾箱界面两侧的上一页/下一页按钮。 */
public final class SweeperDustbinButtons {
    private static double pendingMouseX = -1, pendingMouseY = -1;

    private SweeperDustbinButtons() {
    }

    private static void captureMouse() {
        long win = Minecraft.getInstance().getWindow().getWindow();
        double[] x = new double[1], y = new double[1];
        GLFW.glfwGetCursorPos(win, x, y);
        pendingMouseX = x[0];
        pendingMouseY = y[0];
    }

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        String title = screen.getTitle().getString();
        if (!title.startsWith("垃圾箱 ")) return;
        if (pendingMouseX >= 0 && pendingMouseY >= 0) {
            GLFW.glfwSetCursorPos(Minecraft.getInstance().getWindow().getWindow(), pendingMouseX, pendingMouseY);
            pendingMouseX = -1;
            pendingMouseY = -1;
        }
        int idx;
        try {
            idx = Integer.parseInt(title.substring(4).trim());
        } catch (Throwable e) {
            return;
        }
        int count = ExtrasClientState.sweeperDustbins;
        // 标题里的编号从 1 开始，内部索引从 0 开始
        int display = idx;
        int left = screen.width / 2 - 88;
        int top = screen.height / 2 - 111;
        Button prev = Button.builder(Component.literal("<"), b -> {
            if (display > 1) {
                captureMouse();
                ExtrasActions.sweeperDustbin(display - 2);
            }
        }).bounds(left + 48, top + 5, 16, 11).build();
        prev.active = display > 1;
        Button next = Button.builder(Component.literal(">"), b -> {
            if (display < count) {
                captureMouse();
                ExtrasActions.sweeperDustbin(display);
            }
        }).bounds(left + 70, top + 5, 16, 11).build();
        next.active = display < count;
        event.addListener(prev);
        event.addListener(next);
    }
}
