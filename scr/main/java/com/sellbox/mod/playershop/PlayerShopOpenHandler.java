package com.sellbox.mod.playershop;

import com.sellbox.mod.SellBoxEvents;
import com.sellbox.mod.network.ModNetwork;
import com.sellbox.mod.shop.ShopEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

public class PlayerShopOpenHandler {

    public static void handle(ServerPlayer sp, BlockPos marketPos) {
        var allShops = PlayerShopConfig.getAllShops();

        Map<UUID, String> shopNames = new LinkedHashMap<>();
        Map<UUID, List<ShopEntry>> shopItems = new LinkedHashMap<>();
        Map<UUID, Map<String, Integer>> stockCounts = new LinkedHashMap<>();
        Map<UUID, String> ownerNames = new LinkedHashMap<>();

        UUID requestUuid = sp.getUUID();
        if (!allShops.containsKey(requestUuid)) {
            shopNames.put(requestUuid, "");
            shopItems.put(requestUuid, new ArrayList<>());
            stockCounts.put(requestUuid, new LinkedHashMap<>());
        }

        if (sp.level() instanceof ServerLevel sl) {
            SellBoxEvents.refreshStockCache(sl);
        }

        Map<UUID, Map<String, Integer>> cached = SellBoxEvents.getCachedStocks();
        var playerList = sp.getServer().getPlayerList();
        for (var entry : allShops.entrySet()) {
            UUID ownerUuid = entry.getKey();
            var info = entry.getValue();
            shopNames.put(ownerUuid, info.shopName());
            shopItems.put(ownerUuid, new ArrayList<>(info.items()));
            stockCounts.put(ownerUuid, cached.getOrDefault(ownerUuid, new LinkedHashMap<>()));
            ServerPlayer player = playerList.getPlayer(ownerUuid);
            if (player != null) {
                ownerNames.put(ownerUuid, player.getName().getString());
            } else {
                var cache = sp.getServer().getProfileCache();
                if (cache != null) {
                    var profile = cache.get(ownerUuid);
                    if (profile.isPresent()) {
                        ownerNames.put(ownerUuid, profile.get().getName());
                        continue;
                    }
                }
                ownerNames.put(ownerUuid, ownerUuid.toString().substring(0, 8));
            }
        }

        Set<UUID> unloadedOwners = SellBoxEvents.getCachedUnloadedOwners();
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                new ModNetwork.PlayerShopDataPacket(shopNames, shopItems, stockCounts, unloadedOwners, ownerNames));
    }
}
