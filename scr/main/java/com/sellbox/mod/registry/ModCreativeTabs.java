package com.sellbox.mod.registry;

import com.sellbox.mod.SellBoxMod;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ModCreativeTabs {
    private static final ResourceKey<Registry<CreativeModeTab>> CREATIVE_TAB_KEY = ResourceKey.createRegistryKey(
            new ResourceLocation("minecraft", "creative_mode_tab")
    );

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(
            CREATIVE_TAB_KEY, SellBoxMod.MODID
    );

    private static final List<Supplier<ItemStack>> extraItems = new ArrayList<>();

    public static void addToSellBoxTab(Supplier<ItemStack> itemSupplier) {
        extraItems.add(itemSupplier);
    }

    public static final RegistryObject<CreativeModeTab> SELLBOX_TAB = TABS.register("sellbox_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.SELL_BOX.get()))
                    .title(Component.translatable("itemGroup.sellbox"))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.SELL_BOX.get());
                        output.accept(ModItems.GOLD_COIN.get());
                        output.accept(ModItems.COIN_BAG.get());
                        output.accept(ModItems.MONEY.get());
                        for (Supplier<ItemStack> sup : extraItems) {
                            output.accept(sup.get());
                        }
                    })
                    .build()
    );
}
