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
import com.sellbox.mod.shop.TradeLimitTracker;
import com.sellbox.mod.shop.ShopConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
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
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopped);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SellBoxConfig.SPEC, "SellBox/sellbox-common.toml");

        if (FMLEnvironment.dist == Dist.CLIENT) {
            new SellBoxModClient(modEventBus);
        }
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            com.sellbox.mod.integration.CuriosIntegration.registerMoneyCurio();
            // 载入 GUI 主题（此前从未调用，导致主题重启后丢失）
            com.sellbox.mod.gui.GuiTheme.init(
                    net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get());
            LOGGER.info("SellBox mod common setup complete");
        });
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }

    private void onServerStarting(ServerStartingEvent event) {
        // 初始化配置目录
        java.nio.file.Path configDir = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().toFile().toPath();
        SellBoxConfig.init(configDir);
        ShopConfig.init(configDir);
        com.sellbox.mod.playershop.PlayerShopBlacklist.init(configDir);

        // 获取世界存档路径
        net.minecraft.server.MinecraftServer server = event.getServer();
        java.nio.file.Path worldDir = null;
        
        if (server != null) {
            worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath();
        }

        // 物品收购价：根据配置决定存储位置
        if (SellBoxConfig.isGlobalPricing()) {
            SellBoxConfig.loadPricing(); // 全局共享模式
        } else if (worldDir != null) {
            SellBoxConfig.setWorldDir(worldDir); // 按存档保存模式
        } else {
            SellBoxConfig.loadPricing(); // 回退到全局
        }

        // 商店配置：根据配置决定存储位置
        if (SellBoxConfig.isGlobalShop()) {
            // 全局共享模式 - 已在 ShopConfig.init() 中初始化
        } else if (worldDir != null) {
            ShopConfig.setWorldDir(worldDir); // 按存档保存模式
        }

        // 离线待发放：始终按存档保存
        if (worldDir != null) {
            SellBoxConfig.setWorldDir(worldDir);
        } else {
            SellBoxConfig.loadPending();
        }

        // 玩家商店和统计：始终按存档保存
        if (worldDir != null) {
            PlayerShopConfig.setWorldDir(worldDir);
        } else {
            PlayerShopConfig.init(configDir);
        }

        // 玩家余额：始终按存档保存
        SellBoxEconomy.init(configDir, worldDir);

        // 交易限量计数：始终按存档保存
        if (worldDir != null) {
            TradeLimitTracker.initPerWorld(worldDir);
            // 扩展模块（签到/邮箱/兑换/兑换码/好友）
            com.sellbox.mod.extras.ExtrasConfigFile.initPerWorld(worldDir);
            com.sellbox.mod.extras.ExtrasManager.initPerWorld(worldDir);
        }

        if (com.sellbox.mod.integration.SDMShopIntegration.isLoaded()) {
            LOGGER.info("SDMShop detected, API = {}", com.sellbox.mod.integration.SDMShopIntegration.getApiVersion());
            if (!SellBoxConfig.isVssFlagFileExists()) {
                SellBoxConfig.setSdmCurrencyEnabled(true);
                SellBoxConfig.setShopOverrideEnabled(false);
                LOGGER.info("SDMShop detected on fresh start — automatically enabled SDMShop economy, disabled built-in shop");
            }
        }

    }

    private void onServerStarted(ServerStartedEvent event) {
        // NEZNAMY TAB 模组：服务器完全启动后注册 %sellboxmoney%（与 NeoForge 端一致）
        com.sellbox.mod.integration.TabIntegration.onServerStarted(event.getServer());
        // 扩展数据每 10 秒最多落盘一次
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ServerTickEvent e) -> {
            if (e.phase == net.minecraftforge.event.TickEvent.Phase.END) {
                com.sellbox.mod.extras.ExtrasManager.tickSave(e.getServer());
            }
        });
        // VSS 探测结果（1.20.1 与 1.21.1 的 API 都是用 double，双签名探测自动识别）
        if (com.sellbox.mod.integration.VSSIntegration.isLoaded()) {
            LOGGER.info("VSS: loaded, API = {}, currency switch = {}",
                    com.sellbox.mod.integration.VSSIntegration.getApiVersion(),
                    SellBoxConfig.isVssCurrencyEnabled() ? "on" : "off");
        }
        // 混合端上 Vault 的 Economy 提供者可能比本模组晚注册，服务端启动完成后再探一次
        // 新版 SDM Economy 的货币注册表要等服务端数据加载完才就绪，这里再探一次并触发货币解析
        if (com.sellbox.mod.integration.SDMShopIntegration.probe()) {
            if (SellBoxConfig.isSdmCurrencyEnabled()) {
                // 触发一次取余额，把 sdm:coin 解析出来并验证可用
                var first = event.getServer().getPlayerList().getPlayers();
                if (!first.isEmpty()) {
                    com.sellbox.mod.integration.SDMShopIntegration.getMoney(first.get(0));
                }
                LOGGER.info("SDMShop: economy switch = on, API = {}",
                        com.sellbox.mod.integration.SDMShopIntegration.getApiVersion());
            }
        }
        if (com.sellbox.mod.integration.VaultIntegration.probe()) {
            boolean ready = com.sellbox.mod.integration.VaultIntegration.isEconomyReady();
            LOGGER.info("Vault: loaded=true, economy ready={}, provider={}",
                    ready, com.sellbox.mod.integration.VaultIntegration.getProviderName());
            if (SellBoxConfig.isVaultCurrencyEnabled() && !ready) {
                LOGGER.warn("Vault 经济已开启，但没找到 Economy 提供者（服务端可能没装经济插件），暂时回落到内置经济");
            }
        } else if (SellBoxConfig.isVaultCurrencyEnabled()) {
            LOGGER.warn("Vault 经济已开启，但当前服务端没有 Bukkit/Vault（纯 Forge 端不支持），回落到内置经济");
        }
    }

    private void onServerStopped(ServerStoppedEvent event) {
        com.sellbox.mod.integration.TabIntegration.onServerStopped();
        com.sellbox.mod.extras.ExtrasManager.save();
        com.sellbox.mod.extras.ExtrasConfigFile.save();
        com.sellbox.mod.extras.ExtrasServer.clearRuntimeState();
        com.sellbox.mod.block.SellBoxBlockEntity.clearLoadedRegistry();
        SellBoxConfig.flushSavePending();
        SellBoxConfig.flushSavePricing();
        SellBoxEconomy.flushSave();
        PlayerShopConfig.flushSave();
        ShopConfig.flushSave();
        com.sellbox.mod.playershop.PlayerShopBlacklist.flushSave();
        TradeLimitTracker.flushSave();
    }
}