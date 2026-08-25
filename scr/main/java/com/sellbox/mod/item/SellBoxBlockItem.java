package com.sellbox.mod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class SellBoxBlockItem extends BlockItem {
    public SellBoxBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static void saveOwnerNBT(ItemStack stack, UUID owner, String ownerName) {
        CompoundTag blockEntityTag = stack.getOrCreateTagElement("BlockEntityTag");
        blockEntityTag.putString("OwnerUUID", owner.toString());
        blockEntityTag.putString("OwnerName", ownerName);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.sellbox.sell_box_1").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.sellbox.sell_box_2").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.sellbox.sell_box_3").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.sellbox.sell_box_4").withStyle(ChatFormatting.GRAY));
    }
}
