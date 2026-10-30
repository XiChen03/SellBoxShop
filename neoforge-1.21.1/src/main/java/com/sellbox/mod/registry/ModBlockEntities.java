package com.sellbox.mod.registry;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.block.SellBoxBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SellBoxMod.MODID);

    public static final Supplier<BlockEntityType<SellBoxBlockEntity>> SELL_BOX = BLOCK_ENTITIES.register("sell_box",
            () -> BlockEntityType.Builder.of(SellBoxBlockEntity::new, ModBlocks.SELL_BOX.get()).build(null));
}
