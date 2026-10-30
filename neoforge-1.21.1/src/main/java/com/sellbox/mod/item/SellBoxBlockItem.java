package com.sellbox.mod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class SellBoxBlockItem extends BlockItem {
    public SellBoxBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.sellbox.sell_box_1").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.sellbox.sell_box_2").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.sellbox.sell_box_3").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.sellbox.sell_box_4").withStyle(ChatFormatting.GRAY));
    }
}
