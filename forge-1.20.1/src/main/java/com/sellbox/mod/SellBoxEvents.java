package com.sellbox.mod;

import com.sellbox.mod.block.SellBoxBlockEntity;
import com.sellbox.mod.config.SellBoxConfig;
import com.sellbox.mod.integration.CuriosIntegration;
import com.sellbox.mod.integration.SDMShopIntegration;
import com.sellbox.mod.network.ModNetwork;
import com.sellbox.mod.playershop.MarketBlockEntity;
import com.sellbox.mod.playershop.PlayerShopConfig;
import com.sellbox.mod.playershop.StockBlockEntity;
import com.sellbox.mod.shop.SellBoxEconomy;
import com.sellbox.mod.shop.ShopConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = SellBoxMod.MODID)
public class SellBoxEvents {
    private static final Map<String, Long> lastSellDayByLevel = new HashMap<>();
    private static int vssSyncTick = 0;
    private static int flushTick = 0;
    private static int stockCacheTick = 0;
    private static int moveCheckTick = 0;
    private static final Set<UUID> pendingNotifyPlayers = new HashSet<>();
    private static final Set<UUID> pendingCuriosRestore = new HashSet<>();
    private static final Map<UUID, Integer> curiosRestoreAttempts = new HashMap<>();
    private static final int MAX_CURIOS_RESTORE_ATTEMPTS = 20;
    private static final Map<UUID, BlockPos> lastPlayerPos = new HashMap<>();
    private static int curiosRestoreTick = 0;
    private static Map<UUID, Map<String, Integer>> cachedStocks = new HashMap<>();
    private static Set<UUID> cachedUnloadedOwners = new HashSet<>();

    public static Map<UUID, Map<String, Integer>> getCachedStocks() {
        return cachedStocks;
    }

    public static Set<UUID> getCachedUnloadedOwners() {
        return cachedUnloadedOwners;
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
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
                UUID ownerUuid = sellBox.getOwner();
                String ownerNam = sellBox.getOwnerName();
                level.destroyBlock(pos, false);
                // 破坏时把归属写进掉落物，否则重放后箱子会变成无主、谁都能抢
                ItemStack dropStack = new ItemStack(block);
                if (ownerUuid != null) {
                    com.sellbox.mod.item.SellBoxBlockItem.saveOwnerNBT(dropStack, ownerUuid, ownerNam);
                }
                Block.popResource(level, pos, dropStack);
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
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            if (!event.level.isClientSide()) {
                // 先把上一 tick 攒下的出货收据发出去（同一 tick 多个出货箱出货会合并成一份）
                SellBoxBlockEntity.flushPendingReceipts((ServerLevel) event.level);
            }
            return;
        }
        if (event.phase != TickEvent.Phase.END) return;
        if (event.level.isClientSide()) return;

        ServerLevel level = (ServerLevel) event.level;
        String dimId = level.dimension().location().toString();
        long day = level.getDayTime() / 24000;
        long timeOfDay = level.getDayTime() % 24000;
        long lastSellDay = lastSellDayByLevel.getOrDefault(dimId, -1L);

        if (day > lastSellDay && timeOfDay >= SellBoxConfig.SELL_TIME.get()) {
            lastSellDayByLevel.put(dimId, day);
            SellBoxMod.LOGGER.info("Auto-sell triggered at time {} in {}", timeOfDay, dimId);
            processSellBoxes(level);
        }

        if (dimId.equals("minecraft:overworld") && SDMShopIntegration.isLoaded() && SellBoxConfig.isSdmCurrencyEnabled()) {
            vssSyncTick++;
            if (vssSyncTick >= 20) {
                vssSyncTick = 0;
                for (ServerPlayer player : level.players()) {
                    ModNetwork.sendBalanceToPlayer(player);
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
                com.sellbox.mod.playershop.PlayerShopBlacklist.flushSave();
            }
            stockCacheTick++;
            if (stockCacheTick >= 100) {
                stockCacheTick = 0;
                refreshStockCache(level);
            }
            moveCheckTick++;
            if (moveCheckTick >= 10 && !pendingNotifyPlayers.isEmpty()) {
                moveCheckTick = 0;
                Iterator<UUID> it = pendingNotifyPlayers.iterator();
                while (it.hasNext()) {
                    UUID uid = it.next();
                    ServerPlayer p = level.getServer().getPlayerList().getPlayer(uid);
                    if (p == null) { it.remove(); continue; }
                    BlockPos curPos = p.blockPosition();
                    BlockPos lastPos = lastPlayerPos.get(uid);
                    if (lastPos != null && !curPos.equals(lastPos)) {
                        int bal = SellBoxConfig.getPendingBalance(uid);
                        int amount = 0;
                        if (bal > 0) {
                            // 先扣减并落盘，扣成功再入账：崩溃窗口内不会重复发放
                            int collected = SellBoxConfig.deductPendingBalance(uid, bal);
                            if (collected > 0) {
                                SellBoxEconomy.addBalance(p, collected);
                                ModNetwork.sendBalanceToPlayer(p);
                                amount = collected;
                            }
                        }
                        p.displayClientMessage(Component.translatable("message.sellbox.offline_earnings", amount), false);
                        it.remove();
                        lastPlayerPos.remove(uid);
                    } else {
                        lastPlayerPos.put(uid, curPos);
                    }
                }
            }

            // 延迟恢复 Curios 钞票：每 20 tick 执行一次，等待 Curios 处理完 invalidStacks 后再从背包恢复
            // 仅在确认恢复成功后才移除等待队列；否则持续重试（最多 MAX_CURIOS_RESTORE_ATTEMPTS 次）
            curiosRestoreTick++;
            if (curiosRestoreTick >= 20 && !pendingCuriosRestore.isEmpty()) {
                curiosRestoreTick = 0;
                Iterator<UUID> it = pendingCuriosRestore.iterator();
                while (it.hasNext()) {
                    UUID uid = it.next();
                    ServerPlayer p = level.getServer().getPlayerList().getPlayer(uid);
                    if (p == null) { it.remove(); curiosRestoreAttempts.remove(uid); continue; }
                    int attempts = curiosRestoreAttempts.getOrDefault(uid, 0);
                    if (attempts >= MAX_CURIOS_RESTORE_ATTEMPTS) {
                        SellBoxMod.LOGGER.warn("Gave up restoring MoneyItem to curios after {} attempts for player {}",
                                MAX_CURIOS_RESTORE_ATTEMPTS, p.getName().getString());
                        it.remove();
                        curiosRestoreAttempts.remove(uid);
                        continue;
                    }
                    curiosRestoreAttempts.put(uid, attempts + 1);
                    if (CuriosIntegration.restoreMoneyOnLogin(p)) {
                        it.remove();
                        curiosRestoreAttempts.remove(uid);
                    }
                }
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
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // 延迟恢复 Curios 中的钞票（等待 Curios 处理完 invalidStacks 后再恢复）
        pendingCuriosRestore.add(player.getUUID());

        ModNetwork.sendAllPricesToPlayer(player);
        ModNetwork.sendBalanceToPlayer(player);
        ModNetwork.sendShopConfigToPlayer(player);
        ModNetwork.sendBlacklistToPlayer(player);
        ModNetwork.sendAdminEditToPlayer(player);
        ModNetwork.sendQuestListToPlayer(player);
        ModNetwork.sendAllShopEntriesToPlayer(player);

        UUID playerUuid = player.getUUID();

        // 登录后首次移动时提示离线收益（无收益也提示 0）
        pendingNotifyPlayers.add(playerUuid);
        lastPlayerPos.put(playerUuid, player.blockPosition());
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
                                                        String key = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                                                        if (stack.getTag() != null) key = key + "|" + stack.getTag().toString();
                                                        ownerStocks.merge(key, stack.getCount(), Integer::sum);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (marketOwner != null) {
                                    Map<String, Integer> ownerStocks = stocks.get(marketOwner);
                                    if (ownerStocks != null) {
                                        for (int i = 0; i < market.getContainerSize() - 2; i++) {
                                            ItemStack stack = market.getItem(i);
                                            if (!stack.isEmpty()) {
                                                String key = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                                                if (stack.getTag() != null) key = key + "|" + stack.getTag().toString();
                                                ownerStocks.merge(key, stack.getCount(), Integer::sum);
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