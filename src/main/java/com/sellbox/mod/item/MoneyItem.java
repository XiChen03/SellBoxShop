package com.sellbox.mod.item;

import com.sellbox.mod.shop.SellBoxEconomy;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MoneyItem extends Item {
    public MoneyItem(Properties properties) {
        super(properties);
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, CompoundTag unused) {
        ICapabilityProvider provider = com.sellbox.mod.integration.CuriosIntegration.createCurioProvider(stack);
        return provider != null ? provider : super.initCapabilities(stack, unused);
    }

    @Override
    public EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return EquipmentSlot.HEAD;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

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

        if (com.sellbox.mod.integration.CuriosIntegration.isLoaded()) {
            if (!level.isClientSide) {
                if (com.sellbox.mod.integration.CuriosIntegration.tryEquipToCurios(player, stack)) {
                    return InteractionResultHolder.success(stack);
                }
            }
            return InteractionResultHolder.pass(stack);
        }

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
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.sellbox.money").withStyle(ChatFormatting.GREEN));
        if (com.sellbox.mod.integration.CuriosIntegration.isLoaded()) {
            tooltip.add(Component.translatable("tooltip.sellbox.money_curios").withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("tooltip.sellbox.money_head").withStyle(ChatFormatting.AQUA));
        }
    }
}