package com.sellbox.mod.registry;

import com.sellbox.mod.SellBoxMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SellBoxMod.MODID);

    private static final List<Supplier<ItemStack>> EXTRA_ITEMS = new ArrayList<>();

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SELLBOX_TAB = TABS.register("sellbox_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.sellbox"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> ModItems.SELL_BOX.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.SELL_BOX.get());
                        output.accept(ModItems.GOLD_COIN.get());
                        output.accept(ModItems.MONEY.get());
                        output.accept(ModItems.COIN_BAG.get());
                        for (Supplier<ItemStack> supplier : EXTRA_ITEMS) {
                            output.accept(supplier.get());
                        }
                    })
                    .build());

    public static void addToSellBoxTab(Supplier<ItemStack> itemSupplier) {
        EXTRA_ITEMS.add(itemSupplier);
    }
}
