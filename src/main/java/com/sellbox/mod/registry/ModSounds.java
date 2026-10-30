package com.sellbox.mod.registry;

import com.sellbox.mod.SellBoxMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, SellBoxMod.MODID);

    public static final RegistryObject<SoundEvent> GOOD = SOUNDS.register("good",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(SellBoxMod.MODID, "good")));

    public static final RegistryObject<SoundEvent> DIE = SOUNDS.register("die",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(SellBoxMod.MODID, "die")));
}
