package com.sellbox.mod.client;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.gui.PricingScreen;
import com.sellbox.mod.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 客户端专属的方块交互快捷键。
 *
 * <p>刻意与 {@code SellBoxBlock}（双端公共类）分开：定价界面继承自 {@code Screen}，
 * 是纯客户端类型，公共类一旦引用它，专用服务器在加载该类时就会因为
 * {@code net.minecraft.client} 不存在而失败，进而导致方块注册中断、连锁空指针。
 */
@EventBusSubscriber(modid = SellBoxMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientBlockShortcuts {

    /** 创造 + OP2 时 Shift+右键出货箱 = 快捷打开收购价定价界面 */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide) return;
        if (!event.getEntity().isShiftKeyDown()) return;

        BlockPos pos = event.getPos();
        var state = event.getLevel().getBlockState(pos);
        if (!state.is(ModBlocks.SELL_BOX.get())) return;

        var player = event.getEntity();
        if (!player.isCreative() || !player.hasPermissions(2)) return;

        Minecraft.getInstance().setScreen(new PricingScreen());
        event.setCanceled(true);
    }
}