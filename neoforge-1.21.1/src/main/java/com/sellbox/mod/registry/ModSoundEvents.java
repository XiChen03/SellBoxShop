package com.sellbox.mod.registry;

import com.sellbox.mod.SellBoxMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSoundEvents {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, SellBoxMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> GOOD =
            SOUND_EVENTS.register("good", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "good")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DIE =
            SOUND_EVENTS.register("die", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "die")));

    public static void register(IEventBus bus) {
        SOUND_EVENTS.register(bus);
    }
}
