package com.sellbox.mod.registry;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.block.SellBoxBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SellBoxMod.MODID);

    public static final DeferredBlock<Block> SELL_BOX = BLOCKS.register("sell_box",
            () -> new SellBoxBlock(BlockBehaviour.Properties.of()
                    .strength(-1.0F, 3600000.0F)
                    .noOcclusion()));
}
