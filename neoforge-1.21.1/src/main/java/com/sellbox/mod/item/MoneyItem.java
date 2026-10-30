package com.sellbox.mod.item;

import com.sellbox.mod.integration.CuriosIntegration;
import com.sellbox.mod.shop.SellBoxEconomy;import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class MoneyItem extends Item {
    public MoneyItem(Properties properties) {
        super(properties);
    }

    // 允许钞票放在头盔栏位（无 Curios 时可在此显示余额 HUD）
    @Override
    public EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return EquipmentSlot.HEAD;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 潜行+右键 → 查看余额
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                int balance = SellBoxEconomy.getBalance(serverPlayer);
                player.displayClientMessage(
                        Component.translatable("message.sellbox.balance", balance), false
                );
                player.getCooldowns().addCooldown(this, 200);
            }
            return InteractionResultHolder.consume(stack);
        }

        // 非潜行 → 尝试装备到 Curios 饰品栏（优先）或头盔栏
        if (CuriosIntegration.isLoaded()) {
            if (!level.isClientSide) {
                if (CuriosIntegration.tryEquipToCurios(player, stack)) {
                    return InteractionResultHolder.success(stack);
                }
            }
            return InteractionResultHolder.pass(stack);
        }

        // 无 Curios → 头盔栏为空 → 装备到头盔栏
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet.isEmpty()) {
            if (!level.isClientSide) {
                player.setItemSlot(EquipmentSlot.HEAD, stack.copy());
                stack.setCount(0);
            }
            return InteractionResultHolder.success(stack);
        }

        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.sellbox.money").withStyle(ChatFormatting.GREEN));
        if (CuriosIntegration.isLoaded()) {
            tooltip.add(Component.translatable("tooltip.sellbox.money_curios").withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("tooltip.sellbox.money_head").withStyle(ChatFormatting.AQUA));
        }
    }
}
