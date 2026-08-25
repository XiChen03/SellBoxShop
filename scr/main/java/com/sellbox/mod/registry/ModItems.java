package com.sellbox.mod.registry;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.item.CoinBagItem;
import com.sellbox.mod.item.GoldCoinItem;
import com.sellbox.mod.item.MoneyItem;
import com.sellbox.mod.item.SellBoxBlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.lang.reflect.Constructor;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SellBoxMod.MODID);

    public static final RegistryObject<SellBoxBlockItem> SELL_BOX = ITEMS.register("sell_box",
            () -> new SellBoxBlockItem(ModBlocks.SELL_BOX.get(), new Item.Properties().stacksTo(1)));

    public static final RegistryObject<GoldCoinItem> GOLD_COIN = ITEMS.register("gold_coin",
            () -> new GoldCoinItem(new Item.Properties()));

    public static final RegistryObject<Item> MONEY = ITEMS.register("money",
            () -> createMoneyItem());

    public static final RegistryObject<CoinBagItem> COIN_BAG = ITEMS.register("coin_bag",
            () -> new CoinBagItem(new Item.Properties().stacksTo(1)));

    private static Item createMoneyItem() {
        Item.Properties props = new Item.Properties().stacksTo(1);
        if (ModList.get().isLoaded("curios")) {
            try {
                // 尝试加载实现了 ICurioItem 的 MoneyItem 子类（通过反射创建）
                Class<?> clazz = Class.forName("com.sellbox.mod.item.CuriosMoneyItem");
                Constructor<?> ctor = clazz.getConstructor(Item.Properties.class);
                return (Item) ctor.newInstance(props);
            } catch (Exception e) {
                SellBoxMod.LOGGER.error("Failed to create CuriosMoneyItem, falling back", e);
            }
        }
        return new MoneyItem(props);
    }
}