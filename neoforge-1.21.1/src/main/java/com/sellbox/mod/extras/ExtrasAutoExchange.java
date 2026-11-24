package com.sellbox.mod.extras;

import com.sellbox.mod.item.GoldCoinItem;
import com.sellbox.mod.shop.SellBoxEconomy;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 自动换入：拾取/其它模组直接发放的转换表物品，不进入背包，直接按转换表价值入余额。
 * 每秒扫描一次在线玩家背包，覆盖 FTB 任务奖励、下单模组发币等不经过拾取事件的情况。
 */
public final class ExtrasAutoExchange {
    private static int tick;
    private static final java.util.function.Consumer<ServerPlayer> BALANCE_SENDER = com.sellbox.mod.network.ModPayloads::sendBalanceToPlayer;

    private ExtrasAutoExchange() {
    }

    public static void tick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return;
        if (++tick < 20) return;
        tick = 0;
        if (!com.sellbox.mod.config.SellBoxConfig.isExtrasEnabled()) return;
        if (!ExtrasConfigFile.isAutoExchange()) return;
        if (level.getServer() == null) return;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            convert(player);
        }
    }

    /** @return 本次转换的数量 */
    public static int convert(ServerPlayer player) {
        if (!com.sellbox.mod.config.SellBoxConfig.isExtrasEnabled()) return 0;
        if (!ExtrasConfigFile.isAutoExchange()) return 0;
        var inv = player.getInventory();
        int total = 0;
        long gain = 0L;
        // 只扫描主背包 + 副手，绝不转换正在穿的盔甲
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack stack = inv.items.get(i);
            if (stack == null || stack.isEmpty()) continue;
            long rate = stack.getItem() instanceof GoldCoinItem
                    ? 1L : ExtrasConfigFile.rateOf(ExtrasItems.keyOf(stack));
            if (rate <= 0) continue;
            int n = stack.getCount();
            inv.items.set(i, ItemStack.EMPTY);
            total += n;
            gain += rate * n;
        }
        for (int i = 0; i < inv.offhand.size(); i++) {
            ItemStack stack = inv.offhand.get(i);
            if (stack == null || stack.isEmpty()) continue;
            long rate = stack.getItem() instanceof GoldCoinItem
                    ? 1L : ExtrasConfigFile.rateOf(ExtrasItems.keyOf(stack));
            if (rate <= 0) continue;
            int n = stack.getCount();
            inv.offhand.set(i, ItemStack.EMPTY);
            total += n;
            gain += rate * n;
        }
        if (total <= 0) return 0;
        long capped = Math.min(Integer.MAX_VALUE, gain);
        SellBoxEconomy.addBalance(player, (int) capped);
        BALANCE_SENDER.accept(player);
        player.displayClientMessage(Component.translatable(
                "message.sellbox.exchange_auto", total, capped), true);
        return total;
    }
}
