package com.sellbox.mod.playershop;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class StockBlockItem extends BlockItem {
    public StockBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.sellbox.stock_1").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.sellbox.stock_2").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.sellbox.stock_3").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.sellbox.stock_4").withStyle(ChatFormatting.GRAY));
    }
}
