package com.sellbox.mod.playershop;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.registry.ModCreativeTabs;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class PlayerShopRegistry {
    public static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SellBoxMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SellBoxMod.MODID);
    public static final DeferredRegister<net.minecraft.world.item.Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SellBoxMod.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, SellBoxMod.MODID);

    public static final RegistryObject<MarketBlock> MARKET_BLOCK = BLOCKS.register("market",
            () -> new MarketBlock(BlockBehaviour.Properties.of()
                    .strength(-1.0F, 3600000.0F)
                    .noOcclusion()));

    public static final RegistryObject<StockBlock> STOCK_BLOCK = BLOCKS.register("stock",
            () -> new StockBlock(BlockBehaviour.Properties.of()
                    .strength(-1.0F, 3600000.0F)
                    .noOcclusion()));

    public static final Supplier<BlockEntityType<MarketBlockEntity>> MARKET_BLOCK_ENTITY = BLOCK_ENTITIES.register("market",
            () -> BlockEntityType.Builder.of(MarketBlockEntity::new, MARKET_BLOCK.get()).build(null));

    public static final Supplier<BlockEntityType<StockBlockEntity>> STOCK_BLOCK_ENTITY = BLOCK_ENTITIES.register("stock",
            () -> BlockEntityType.Builder.of(StockBlockEntity::new, STOCK_BLOCK.get()).build(null));

    public static final RegistryObject<MarketBlockItem> MARKET_ITEM = ITEMS.register("market",
            () -> new MarketBlockItem(MARKET_BLOCK.get(), new net.minecraft.world.item.Item.Properties()));

    public static final RegistryObject<StockBlockItem> STOCK_ITEM = ITEMS.register("stock",
            () -> new StockBlockItem(STOCK_BLOCK.get(), new net.minecraft.world.item.Item.Properties()));

    public static final Supplier<MenuType<MarketMenu>> MARKET_MENU = MENUS.register("market",
            () -> IForgeMenuType.create(MarketMenu::new));

    public static final Supplier<MenuType<StockMenu>> STOCK_MENU = MENUS.register("stock",
            () -> IForgeMenuType.create(StockMenu::new));

    public static final Supplier<MenuType<PlayerShopMenu>> PLAYER_SHOP_MENU = MENUS.register("player_shop",
            () -> IForgeMenuType.create(PlayerShopMenu::new));

    public static void init(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        MENUS.register(modEventBus);
        ModCreativeTabs.addToSellBoxTab(() -> new net.minecraft.world.item.ItemStack(MARKET_ITEM.get()));
        ModCreativeTabs.addToSellBoxTab(() -> new net.minecraft.world.item.ItemStack(STOCK_ITEM.get()));
    }
}
