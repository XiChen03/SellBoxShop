package com.sellbox.mod.extras.client;

import com.sellbox.mod.extras.client.ExtrasActions;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;

/** 垃圾箱界面：滚轮上下切换垃圾箱。 */
public class SweeperDustbinScreen extends ContainerScreen {
    public SweeperDustbinScreen(ChestMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    private int nextBin(double scrollY) {
        String t = this.title.getString();
        if (!t.startsWith("垃圾箱 ")) return -1;
        int idx;
        try { idx = Integer.parseInt(t.substring(4).trim()); }
        catch (Throwable e) { return -1; }
        int dir = scrollY > 0 ? -1 : 1;
        int next = idx + dir;
        if (next < 0 || next >= com.sellbox.mod.extras.client.ExtrasClientState.sweeperDustbins) return -2;
        ExtrasActions.sweeperDustbin(next);
        this.onClose();
        return next;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollAmount) {
        if (nextBin(scrollAmount) != -1) return true;
        return super.mouseScrolled(mx, my, scrollAmount);
    }
}
