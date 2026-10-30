package com.sellbox.mod.registry;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.item.CoinBagItem;
import com.sellbox.mod.item.GoldCoinItem;
import com.sellbox.mod.item.MoneyItem;
import com.sellbox.mod.item.SellBoxBlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SellBoxMod.MODID);

    public static final DeferredItem<SellBoxBlockItem> SELL_BOX = ITEMS.registerItem("sell_box",
            props -> new SellBoxBlockItem(ModBlocks.SELL_BOX.get(), props));

    public static final DeferredItem<GoldCoinItem> GOLD_COIN = ITEMS.registerItem("gold_coin",
            GoldCoinItem::new, new Item.Properties());

    public static final DeferredItem<MoneyItem> MONEY = ITEMS.registerItem("money",
            MoneyItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredItem<CoinBagItem> COIN_BAG = ITEMS.registerItem("coin_bag",
            CoinBagItem::new, new Item.Properties().stacksTo(1));
}
