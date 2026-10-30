package com.sellbox.mod;

import com.mojang.logging.LogUtils;
import com.sellbox.mod.command.ModCommands;
import com.sellbox.mod.config.SellBoxConfig;
import com.sellbox.mod.integration.CuriosIntegration;
import com.sellbox.mod.network.ModNetwork;
import com.sellbox.mod.playershop.PlayerShopConfig;
import com.sellbox.mod.playershop.PlayerShopRegistry;
import com.sellbox.mod.registry.ModBlockEntities;
import com.sellbox.mod.registry.ModBlocks;
import com.sellbox.mod.registry.ModCreativeTabs;
import com.sellbox.mod.registry.ModItems;
import com.sellbox.mod.registry.ModMenuTypes;
import com.sellbox.mod.registry.ModSounds;
import com.sellbox.mod.shop.SellBoxEconomy;
import com.sellbox.mod.shop.ShopConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.api.distmarker.Dist;
import org.slf4j.Logger;

@Mod(SellBoxMod.MODID)
public class SellBoxMod {
    public static final String MODID = "sellbox";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SellBoxMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenuTypes.MENUS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        PlayerShopRegistry.init(modEventBus);

        modEventBus.addListener(this::onCommonSetup);
        ModNetwork.register();

        MinecraftForge.EVENT_BUS.register(SellBoxEvents.class);

        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopped);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SellBoxConfig.SPEC);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            new SellBoxModClient(modEventBus);
        }
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            com.sellbox.mod.integration.CuriosIntegration.registerMoneyCurio();
            LOGGER.info("SellBox mod common setup complete");
        });
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }

    private void onServerStarting(ServerStartingEvent event) {
        SellBoxConfig.init(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().toFile().toPath());
        SellBoxConfig.loadPricing();
        SellBoxConfig.loadPending();
        ShopConfig.init(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().toFile().toPath());
        PlayerShopConfig.init(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().toFile().toPath());
        SellBoxEconomy.init(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().toFile().toPath());
        com.sellbox.mod.playershop.PlayerShopBlacklist.init(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().toFile().toPath());

        if (com.sellbox.mod.integration.SDMShopIntegration.isLoaded() && !SellBoxConfig.isVssFlagFileExists()) {
            SellBoxConfig.setVssCurrencyEnabled(true);
            SellBoxConfig.setShopOverrideEnabled(false);
            LOGGER.info("SDMShopRework detected on fresh start — automatically enabled SDMShop economy, disabled built-in shop");
        }
    }

    private void onServerStopped(ServerStoppedEvent event) {
        SellBoxConfig.flushSavePending();
        SellBoxConfig.flushSavePricing();
        SellBoxEconomy.flushSave();
        PlayerShopConfig.flushSave();
        ShopConfig.flushSave();
        com.sellbox.mod.playershop.PlayerShopBlacklist.flushSave();
    }
}