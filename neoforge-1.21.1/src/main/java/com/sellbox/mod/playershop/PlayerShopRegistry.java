package com.sellbox.mod.playershop;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.registry.ModCreativeTabs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class PlayerShopRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SellBoxMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SellBoxMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SellBoxMod.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, SellBoxMod.MODID);

    public static final DeferredBlock<MarketBlock> MARKET_BLOCK = BLOCKS.register("market",
            () -> new MarketBlock(BlockBehaviour.Properties.of()
                    .strength(-1.0F, 3600000.0F)
                    .noOcclusion()));

    public static final DeferredBlock<StockBlock> STOCK_BLOCK = BLOCKS.register("stock",
            () -> new StockBlock(BlockBehaviour.Properties.of()
                    .strength(-1.0F, 3600000.0F)
                    .noOcclusion()));

    public static final Supplier<BlockEntityType<MarketBlockEntity>> MARKET_BLOCK_ENTITY = BLOCK_ENTITIES.register("market",
            () -> BlockEntityType.Builder.of(MarketBlockEntity::new, MARKET_BLOCK.get()).build(null));

    public static final Supplier<BlockEntityType<StockBlockEntity>> STOCK_BLOCK_ENTITY = BLOCK_ENTITIES.register("stock",
            () -> BlockEntityType.Builder.of(StockBlockEntity::new, STOCK_BLOCK.get()).build(null));

    public static final DeferredItem<MarketBlockItem> MARKET_ITEM = ITEMS.registerItem("market",
            props -> new MarketBlockItem(MARKET_BLOCK.get(), props));

    public static final DeferredItem<StockBlockItem> STOCK_ITEM = ITEMS.registerItem("stock",
            props -> new StockBlockItem(STOCK_BLOCK.get(), props));

    public static final Supplier<MenuType<MarketMenu>> MARKET_MENU = MENUS.register("market",
            () -> IMenuTypeExtension.create(MarketMenu::new));

    public static final Supplier<MenuType<StockMenu>> STOCK_MENU = MENUS.register("stock",
            () -> IMenuTypeExtension.create(StockMenu::new));

    public static final Supplier<MenuType<PlayerShopMenu>> PLAYER_SHOP_MENU = MENUS.register("player_shop",
            () -> IMenuTypeExtension.create(PlayerShopMenu::new));

    public static void init(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        MENUS.register(modEventBus);
        modEventBus.addListener(PlayerShopRegistry::onRegisterCapabilities);
        ModCreativeTabs.addToSellBoxTab(() -> new net.minecraft.world.item.ItemStack(MARKET_ITEM.get()));
        ModCreativeTabs.addToSellBoxTab(() -> new net.minecraft.world.item.ItemStack(STOCK_ITEM.get()));
    }

    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                MARKET_BLOCK_ENTITY.get(),
                (be, side) -> {
                    if (be instanceof MarketBlockEntity market) {
                        return market.getItemHandler();
                    }
                    return null;
                }
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                STOCK_BLOCK_ENTITY.get(),
                (be, side) -> {
                    if (be instanceof StockBlockEntity stock) {
                        return stock.getItemHandler();
                    }
                    return null;
                }
        );
    }
}
