package com.sellbox.mod.registry;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.gui.SellBoxMenu;
import com.sellbox.mod.shop.ShopMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, SellBoxMod.MODID);

    public static final Supplier<MenuType<SellBoxMenu>> SELL_BOX = MENUS.register("sell_box",
            () -> IMenuTypeExtension.create(SellBoxMenu::new));
            
    public static final Supplier<MenuType<ShopMenu>> SHOP = MENUS.register("shop",
            () -> IMenuTypeExtension.create(ShopMenu::new));
}
