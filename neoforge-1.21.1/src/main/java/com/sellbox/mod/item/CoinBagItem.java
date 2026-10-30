package com.sellbox.mod.item;

import com.sellbox.mod.shop.SellBoxEconomy;import com.sellbox.mod.network.ModPayloads;
import com.sellbox.mod.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

public class CoinBagItem extends Item {
    public static final int MAX_CAPACITY = 10000000;

    public CoinBagItem(Properties properties) {
        super(properties);
    }

    public static int getCoins(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 0;
        return data.getUnsafe().getInt("coins");
    }

    public static void setCoins(ItemStack stack, int amount) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        if (amount <= 0) {
            tag.remove("coins");
        } else {
            tag.putInt("coins", amount);
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static int getFreeSpace(ItemStack stack) {
        return MAX_CAPACITY - getCoins(stack);
    }

    public static int addCoins(ItemStack stack, int amount) {
        int coins = getCoins(stack);
        int canAdd = Math.min(amount, MAX_CAPACITY - coins);
        if (canAdd > 0) {
            setCoins(stack, coins + canAdd);
        }
        return canAdd;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack bagStack = player.getItemInHand(hand);
        int coins = getCoins(bagStack);

        if (level.isClientSide) {
            return InteractionResultHolder.success(bagStack);
        }

        if (player.isShiftKeyDown()) {
            if (coins > 0 && player instanceof ServerPlayer serverPlayer) {
                SellBoxEconomy.addBalance(serverPlayer, coins);
                ModPayloads.sendBalanceToPlayer(serverPlayer);
                setCoins(bagStack, 0);
                player.displayClientMessage(
                        Component.translatable("message.sellbox.bag_used", coins), true
                );
            }
            return InteractionResultHolder.success(bagStack);
        }

        int toTake = Math.min(coins, 64);
        if (toTake > 0) {
            setCoins(bagStack, coins - toTake);
            ItemStack goldCoins = new ItemStack(ModItems.GOLD_COIN.get(), toTake);
            if (!player.addItem(goldCoins)) {
                player.drop(goldCoins, false);
            }
            player.displayClientMessage(
                    Component.translatable("message.sellbox.bag_take", toTake), true
            );
        }

        return InteractionResultHolder.success(bagStack);
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack bagStack, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) return false;

        ItemStack slotStack = slot.getItem();

        if (!slotStack.isEmpty() && slotStack.is(ModItems.GOLD_COIN.get())) {
            int coinsInBag = getCoins(bagStack);
            int free = MAX_CAPACITY - coinsInBag;
            if (free <= 0) return true;

            int toAbsorb = Math.min(slotStack.getCount(), free);
            if (toAbsorb <= 0) return true;

            slotStack.shrink(toAbsorb);
            setCoins(bagStack, coinsInBag + toAbsorb);
            slot.setChanged();
            if (!player.level().isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.sellbox.bag_absorbed", toAbsorb), true
                );
            }
            return true;
        }

        if (slotStack.isEmpty()) {
            int coinsInBag = getCoins(bagStack);
            if (coinsInBag <= 0) return true;

            int toRelease = Math.min(coinsInBag, 64);
            setCoins(bagStack, coinsInBag - toRelease);
            slot.set(new ItemStack(ModItems.GOLD_COIN.get(), toRelease));
            slot.setChanged();
            if (!player.level().isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.sellbox.bag_take", toRelease), true
                );
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack bagStack, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (!other.is(ModItems.GOLD_COIN.get())) return false;
        if (action != ClickAction.SECONDARY) return false;

        int coinsInBag = getCoins(bagStack);
        int free = MAX_CAPACITY - coinsInBag;
        if (free <= 0) return true;

        int toAbsorb = Math.min(other.getCount(), free);
        if (toAbsorb <= 0) return true;

        other.shrink(toAbsorb);
        setCoins(bagStack, coinsInBag + toAbsorb);
        slot.setChanged();
        if (!player.level().isClientSide) {
            player.displayClientMessage(
                    Component.translatable("message.sellbox.bag_absorbed", toAbsorb), true
            );
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int coins = getCoins(stack);
        tooltip.add(Component.translatable("tooltip.sellbox.coin_bag", coins, MAX_CAPACITY)
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.sellbox.coin_bag_right").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.sellbox.coin_bag_shift").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.sellbox.coin_bag_slot").withStyle(ChatFormatting.GRAY));
    }
}
