package com.sellbox.mod.item;

import com.sellbox.mod.network.ModNetwork;
import com.sellbox.mod.shop.SellBoxEconomy;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GoldCoinItem extends Item {
    public GoldCoinItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        if (player.isShiftKeyDown()) {
            int count = stack.getCount();
            stack.shrink(count);
            SellBoxEconomy.addBalance((ServerPlayer) player, count);
            ModNetwork.sendBalanceToPlayer((ServerPlayer) player);
            player.displayClientMessage(
                    Component.translatable("message.sellbox.coin_used", count), true
            );
            return InteractionResultHolder.success(stack);
        }

        SellBoxEconomy.sendLeaderboard((ServerPlayer) player, 1);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.sellbox.gold_coin").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.sellbox.gold_coin_shift").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.sellbox.gold_coin_right").withStyle(ChatFormatting.GRAY));
    }
}
