package com.sellbox.mod.registry;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.block.SellBoxBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SellBoxMod.MODID);

    public static final Supplier<BlockEntityType<SellBoxBlockEntity>> SELL_BOX = BLOCK_ENTITIES.register("sell_box",
            () -> BlockEntityType.Builder.of(SellBoxBlockEntity::new, ModBlocks.SELL_BOX.get()).build(null));
}
