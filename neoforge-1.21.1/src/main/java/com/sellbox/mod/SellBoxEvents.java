package com.sellbox.mod;

import com.sellbox.mod.block.SellBoxBlockEntity;
import com.sellbox.mod.config.SellBoxConfig;
import com.sellbox.mod.playershop.MarketBlockEntity;
import com.sellbox.mod.playershop.PlayerShopConfig;
import com.sellbox.mod.playershop.StockBlockEntity;
import com.sellbox.mod.shop.SellBoxEconomy;import com.sellbox.mod.shop.ShopConfig;
import com.sellbox.mod.shop.network.SyncShopConfigPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import com.sellbox.mod.network.ModPayloads;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;
import java.util.UUID;

@EventBusSubscriber(modid = SellBoxMod.MODID)
public class SellBoxEvents {
    private static final Map<String, Long> lastSellDayByLevel = new HashMap<>();
    private static int flushTick = 0;
    private static int stockCacheTick = 0;
    private static int balanceSyncTick = 0;
    private static Map<UUID, Map<String, Integer>> cachedStocks = new HashMap<>();
    private static Set<UUID> cachedUnloadedOwners = new HashSet<>();
    private static final Set<UUID> pendingOfflineNotify = new HashSet<>();
    private static final Map<UUID, BlockPos> playerLoginPos = new HashMap<>();

    public static Map<UUID, Map<String, Integer>> getCachedStocks() {
        return cachedStocks;
    }

    public static Set<UUID> getCachedUnloadedOwners() {
        return cachedUnloadedOwners;
    }

    @SubscribeEvent
    static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide) return;
        if (!event.getEntity().isShiftKeyDown()) return;

        var level = event.getLevel();
        var pos = event.getPos();
        var be = level.getBlockEntity(pos);
        if (be instanceof SellBoxBlockEntity sellBox) {
            var player = event.getEntity();
            if (sellBox.getOwner() == null) {
                sellBox.setOwner(player.getUUID());
                sellBox.setOwnerName(player.getName().getString());
                sellBox.setChanged();
            }
            if (sellBox.getOwner().equals(player.getUUID())) {
                BlockState state = level.getBlockState(pos);
                Block block = state.getBlock();
                level.destroyBlock(pos, false);
                Block.popResource(level, pos, new ItemStack(block));
                event.setCanceled(true);
            }
            return;
        }
        if (be instanceof MarketBlockEntity market) {
            var player = event.getEntity();
            if (market.getOwner() == null) {
                market.setOwner(player.getUUID());
                market.setOwnerName(player.getName().getString());
                market.setChanged();
            }
            if (market.getOwner().equals(player.getUUID())) {
                BlockState state = level.getBlockState(pos);
                Block block = state.getBlock();
                level.destroyBlock(pos, false);
                Block.popResource(level, pos, new ItemStack(block));
                event.setCanceled(true);
            }
            return;
        }
        if (be instanceof StockBlockEntity stock) {
            var player = event.getEntity();
            if (stock.getOwner() == null) {
                stock.setOwner(player.getUUID());
                stock.setOwnerName(player.getName().getString());
                stock.setChanged();
            }
            if (stock.getOwner().equals(player.getUUID())) {
                BlockState state = level.getBlockState(pos);
                Block block = state.getBlock();
                level.destroyBlock(pos, false);
                Block.popResource(level, pos, new ItemStack(block));
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Pre event) {
        if (event.getLevel().isClientSide) return;

        ServerLevel level = (ServerLevel) event.getLevel();

        // 先把上一 tick 攒下的出货收据发出去（同一 tick 多个出货箱出货会合并成一份）
        SellBoxBlockEntity.flushPendingReceipts(level);

        String dimId = level.dimension().location().toString();
        long day = level.getDayTime() / 24000;
        long timeOfDay = level.getDayTime() % 24000;
        long lastSellDay = lastSellDayByLevel.getOrDefault(dimId, -1L);

        if (day > lastSellDay && timeOfDay >= SellBoxConfig.getSellTime()) {
            lastSellDayByLevel.put(dimId, day);
            SellBoxMod.LOGGER.info("Auto-sell triggered at time {} in {}", timeOfDay, dimId);
            processSellBoxes(level);
        }

        // 所有经济模式下都周期同步余额，保证左上角实时更新
        if (dimId.equals("minecraft:overworld")) {
            balanceSyncTick++;
            if (balanceSyncTick >= 20) { // 每秒同步一次 (20 tick = 1秒)
                balanceSyncTick = 0;
                for (ServerPlayer player : level.players()) {
                    ModPayloads.sendBalanceToPlayer(player);
                }
            }
        }

        if (dimId.equals("minecraft:overworld")) {
            flushTick++;
            if (flushTick >= 600) {
                flushTick = 0;
                SellBoxEconomy.flushSave();
                SellBoxConfig.flushSavePending();
                SellBoxConfig.flushSavePricing();
                PlayerShopConfig.flushSave();
                ShopConfig.flushSave();
            }
            stockCacheTick++;
            if (stockCacheTick >= 100) {
                stockCacheTick = 0;
                refreshStockCache(level);
            }
        }

        checkPendingOfflineNotifications(level);
    }

    private static void checkPendingOfflineNotifications(ServerLevel level) {
        if (pendingOfflineNotify.isEmpty()) return;
        for (ServerPlayer player : level.players()) {
            UUID uuid = player.getUUID();
            if (!pendingOfflineNotify.contains(uuid)) continue;

            BlockPos loginPos = playerLoginPos.get(uuid);
            if (loginPos == null) {
                pendingOfflineNotify.remove(uuid);
                playerLoginPos.remove(uuid);
                continue;
            }

            if (!player.blockPosition().equals(loginPos)) {
                pendingOfflineNotify.remove(uuid);
                playerLoginPos.remove(uuid);

                int pending = SellBoxConfig.getPendingBalance(uuid);
                int amount = 0;
                if (pending > 0) {
                    // 先扣减并落盘，扣成功再入账：崩溃窗口内不会重复发放
                    int collected = SellBoxConfig.deductPendingBalance(uuid, pending);
                    if (collected > 0) {
                        SellBoxEconomy.addBalance(player, collected);
                        ModPayloads.sendBalanceToPlayer(player);
                        amount = collected;
                    }
                }
                player.displayClientMessage(
                        Component.translatable("message.sellbox.offline_earnings", amount),
                        true
                );
            }
        }
    }

    private static void processSellBoxes(ServerLevel level) {
        Set<LevelChunk> seen = new HashSet<>();
        // 1) 在线玩家 ±10 区块内已加载的出货箱
        for (ServerPlayer player : level.players()) {
            int cx = player.chunkPosition().x;
            int cz = player.chunkPosition().z;
            for (int dx = -10; dx <= 10; dx++) {
                for (int dz = -10; dz <= 10; dz++) {
                    if (level.hasChunk(cx + dx, cz + dz)) {
                        LevelChunk chunk = level.getChunk(cx + dx, cz + dz);
                        if (seen.add(chunk)) {
                            for (BlockEntity be : chunk.getBlockEntities().values()) {
                                if (be instanceof SellBoxBlockEntity sellBox) {
                                    sellBox.sellItems();
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2) 所有已加载区块里的出货箱：主人下线也照常出售，钱进离线收益。
        //    该表由出货箱自己在 setLevel/setRemoved 时维护，因此涵盖任意区块加载方式：
        //    玩家附近、/forceload、出生点区块、FTB Chunks 等模组加载器，无需逐家适配。
        SellBoxBlockEntity.forEachLoadedSellBox(level, SellBoxBlockEntity::sellItems);
    }

    @SubscribeEvent
    static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ModPayloads.sendAllPricesToPlayer(player);
        ModPayloads.sendBalanceToPlayer(player);
        PacketDistributor.sendToPlayer(player, new SyncShopConfigPayload(
                SellBoxConfig.isVssCurrencyEnabled(),
                SellBoxConfig.isShopOverrideEnabled(),
                ShopConfig.isEditMode() && player.hasPermissions(2),
                PlayerShopConfig.isAdminEditMode() && player.hasPermissions(2)
        ));

        UUID playerUuid = player.getUUID();

        // 登录后首次移动时提示离线收益（无收益也提示 0）
        pendingOfflineNotify.add(playerUuid);
        playerLoginPos.put(playerUuid, player.blockPosition());
    }

    @SubscribeEvent
    static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        pendingOfflineNotify.remove(player.getUUID());
        playerLoginPos.remove(player.getUUID());
    }

    public static void refreshStockCache(ServerLevel level) {
        var allShops = PlayerShopConfig.getAllShops();
        Map<UUID, Map<String, Integer>> stocks = new LinkedHashMap<>();
        Set<UUID> unloadedOwners = new HashSet<>();
        for (UUID uid : allShops.keySet()) {
            stocks.put(uid, new LinkedHashMap<>());
        }
        Set<BlockPos> scannedMarkets = new HashSet<>();
        Set<LevelChunk> scannedChunks = new HashSet<>();
        int playerRange = 16;
        for (ServerLevel sl : level.getServer().getAllLevels()) {
            for (ServerPlayer p : sl.players()) {
                int pcx = p.getBlockX() >> 4;
                int pcz = p.getBlockZ() >> 4;
                for (int dx = -playerRange; dx <= playerRange; dx++) {
                    for (int dz = -playerRange; dz <= playerRange; dz++) {
                        int tx = pcx + dx;
                        int tz = pcz + dz;
                        if (!sl.hasChunk(tx, tz)) continue;
                        LevelChunk chunk = sl.getChunk(tx, tz);
                        if (chunk == null) continue;
                        for (BlockEntity be : chunk.getBlockEntities().values()) {
                            if (be instanceof MarketBlockEntity market) {
                                BlockPos mp = market.getBlockPos();
                                if (!scannedMarkets.add(mp)) continue;
                                int mcx = mp.getX() >> 4;
                                int mcz = mp.getZ() >> 4;
                                UUID marketOwner = market.getOwner();
                                int sx = mcx;
                                int sz = mcz;
                                // 与交易路径 collectOwnerStocks 保持一致：只扫市场所在的 1 个 chunk
                                if (!sl.hasChunk(sx, sz)) {
                                    if (marketOwner != null) unloadedOwners.add(marketOwner);
                                } else {
                                    LevelChunk sc = sl.getChunk(sx, sz);
                                    if (sc != null && scannedChunks.add(sc)) {
                                        for (BlockEntity sbe : sc.getBlockEntities().values()) {
                                            if (sbe instanceof StockBlockEntity stock) {
                                                UUID stockOwner = stock.getOwner();
                                                Map<String, Integer> ownerStocks = stocks.get(stockOwner);
                                                if (ownerStocks == null) continue;
                                                for (int i = 0; i < stock.getContainerSize(); i++) {
                                                    ItemStack stack = stock.getItem(i);
                                                    if (!stack.isEmpty()) {
                                                        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                                                        ownerStocks.merge(id, stack.getCount(), Integer::sum);
                                                        String snbt = com.sellbox.mod.util.ItemVariantUtil.brewNbtSnbt(stack, level.registryAccess());
                                                        ownerStocks.merge(id + "|" + (snbt != null ? snbt : "{}"), stack.getCount(), Integer::sum);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (marketOwner != null) {
                                    Map<String, Integer> ownerStocks = stocks.get(marketOwner);
                                    if (ownerStocks != null) {
                                        for (int i = 0; i < com.sellbox.mod.playershop.MarketBlockEntity.RECEIPT_BOOK_SLOT; i++) {
                                            ItemStack stack = market.getItem(i);
                                            if (!stack.isEmpty()) {
                                                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                                                ownerStocks.merge(id, stack.getCount(), Integer::sum);
                                                String snbt = com.sellbox.mod.util.ItemVariantUtil.brewNbtSnbt(stack, level.registryAccess());
                                                ownerStocks.merge(id + "|" + (snbt != null ? snbt : "{}"), stack.getCount(), Integer::sum);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        cachedStocks = stocks;
        cachedUnloadedOwners = unloadedOwners;
    }
}
