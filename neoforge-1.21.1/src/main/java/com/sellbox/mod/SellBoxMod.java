package com.sellbox.mod;

import com.mojang.logging.LogUtils;
import com.sellbox.mod.block.SellBoxBlockEntity;
import com.sellbox.mod.command.ModCommands;
import com.sellbox.mod.config.SellBoxConfig;
import com.sellbox.mod.network.ModPayloads;
import com.sellbox.mod.playershop.PlayerShopConfig;
import com.sellbox.mod.playershop.PlayerShopRegistry;
import com.sellbox.mod.registry.ModBlockEntities;
import com.sellbox.mod.registry.ModBlocks;
import com.sellbox.mod.registry.ModCreativeTabs;
import com.sellbox.mod.registry.ModItems;
import com.sellbox.mod.integration.CuriosIntegration;
import com.sellbox.mod.integration.TabIntegration;
import com.sellbox.mod.registry.ModMenuTypes;
import com.sellbox.mod.registry.ModSoundEvents;
import com.sellbox.mod.api.IEconomy;
import com.sellbox.mod.api.SellBoxEconomyAPI;
import com.sellbox.mod.shop.SellBoxEconomy;
import com.sellbox.mod.shop.TradeLimitTracker;
import net.minecraft.server.level.ServerPlayer;
import com.sellbox.mod.shop.ShopConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

@Mod(SellBoxMod.MODID)
public class SellBoxMod {
    public static final String MODID = "sellbox";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SellBoxMod(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenuTypes.MENUS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        PlayerShopRegistry.init(modEventBus);
        ModSoundEvents.register(modEventBus);

        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::onRegisterCapabilities);
        modEventBus.addListener(this::onRegisterPayloads);

        // 配置已改为自定义 JSON，不再使用 NeoForge ModConfig

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.register(SellBoxEvents.class);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CuriosIntegration.registerMoneyCurio();
            com.sellbox.mod.gui.GuiTheme.load();
            LOGGER.info("SellBox mod common setup complete");
        });
    }

    private void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.SELL_BOX.get(),
                (be, side) -> {
                    if (be instanceof SellBoxBlockEntity sellBox) {
                        return sellBox.getItemHandler();
                    }
                    return null;
                }
        );
    }

    private void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        ModPayloads.register(event);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }

    private void onServerStarting(ServerStartingEvent event) {
        var configDir = FMLPaths.CONFIGDIR.get();
        var worldDir = event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath();

        // 全局配置（不区分存档）
        SellBoxConfig.init(configDir);
        SellBoxConfig.loadPricing();
        ShopConfig.init(configDir);

        // 存档相关数据（按存档保存）
        SellBoxConfig.initPerWorld(worldDir);
        SellBoxConfig.loadPending();

        // 根据配置决定是否按存档加载商店配置
        if (!SellBoxConfig.isGlobalShopConfig()) {
            ShopConfig.initPerWorld(worldDir);
        }

        // 根据配置决定是否按存档加载物品收购价
        if (!SellBoxConfig.isGlobalPrices()) {
            SellBoxConfig.loadPricingPerWorld(worldDir);
        }

PlayerShopConfig.initPerWorld(worldDir);
            SellBoxEconomy.initPerWorld(worldDir);
            TradeLimitTracker.initPerWorld(worldDir);
        SellBoxEconomyAPI.set(new IEconomy() {
            @Override public int getBalance(ServerPlayer player) { return SellBoxEconomy.getBalance(player); }
            @Override public boolean hasBalance(ServerPlayer player, int amount) { return SellBoxEconomy.hasBalance(player, amount); }
            @Override public void addBalance(ServerPlayer player, int amount) { SellBoxEconomy.addBalance(player, amount); ModPayloads.sendBalanceToPlayer(player); }
            @Override public boolean deductBalance(ServerPlayer player, int amount) { boolean r = SellBoxEconomy.deductBalance(player, amount); ModPayloads.sendBalanceToPlayer(player); return r; }
            @Override public boolean tryDeductBalance(ServerPlayer player, int amount) { boolean r = SellBoxEconomy.tryDeductBalance(player, amount); ModPayloads.sendBalanceToPlayer(player); return r; }
            @Override public void setBalance(ServerPlayer player, int amount) { SellBoxEconomy.setBalance(player, amount); ModPayloads.sendBalanceToPlayer(player); }
            @Override public String formatMoney(int amount) { return SellBoxEconomy.formatMoney(amount); }
        });
    }

    private void onServerStarted(ServerStartedEvent event) {
        TabIntegration.onServerStarted(event.getServer());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        TabIntegration.onServerStopped();
        SellBoxBlockEntity.clearLoadedRegistry();
        SellBoxConfig.flushSavePending();
        SellBoxConfig.flushSavePricing();
        SellBoxEconomy.flushSave();
        PlayerShopConfig.flushSave();
        TradeLimitTracker.flushSave();
        ShopConfig.flushSave();
    }
}
