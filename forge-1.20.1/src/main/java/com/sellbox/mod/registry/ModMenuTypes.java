package com.sellbox.mod.registry;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.gui.SellBoxMenu;
import com.sellbox.mod.shop.ShopMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, SellBoxMod.MODID);

    public static final Supplier<MenuType<SellBoxMenu>> SELL_BOX = MENUS.register("sell_box",
            () -> IForgeMenuType.create(SellBoxMenu::new));

    public static final Supplier<MenuType<ShopMenu>> SHOP = MENUS.register("shop",
            () -> IForgeMenuType.create(ShopMenu::new));
}
