package com.sellbox.mod.extras.client;

import com.sellbox.mod.SellBoxMod;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** 垃圾箱界面滚轮切换；不注册原版 generic_9x6，避免和原版重复注册冲突。 */
@EventBusSubscriber(modid = SellBoxMod.MODID, value = Dist.CLIENT)
public final class SweeperDustbinScrollHandler {
    private SweeperDustbinScrollHandler() {
    }

    @SubscribeEvent
    public static void onScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!(event.getScreen() instanceof ContainerScreen screen)) return;
        String t = screen.getTitle().getString();
        if (!t.startsWith("垃圾箱 ")) return;
        int idx;
        try {
            idx = Integer.parseInt(t.substring(4).trim());
        } catch (Throwable e) {
            return;
        }
        int next = idx + (event.getScrollDeltaY() > 0 ? -1 : 1); // 1-based display
        if (next < 1 || next > ExtrasClientState.sweeperDustbins) {
            event.setCanceled(true);
            return;
        }
        ExtrasActions.sweeperDustbin(next - 1);
        // 不要在这里 onClose：事件返回后 vanilla 还会调用 screen.afterMouseAction()，
        // 提前把 screen 置空会 NPE。服务端收到请求后会直接打开新的垃圾箱界面。
        event.setCanceled(true);
    }
}
