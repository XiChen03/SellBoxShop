package com.sellbox.mod.playershop.network;

import com.sellbox.mod.SellBoxEvents;
import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.playershop.PlayerShopConfig;
import com.sellbox.mod.shop.ShopEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.mojang.authlib.GameProfile;
import java.util.*;

public record PlayerShopOpenPayload(
        BlockPos marketPos
) implements CustomPacketPayload {
    public static final Type<PlayerShopOpenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "player_shop_open"));

    public static final StreamCodec<FriendlyByteBuf, PlayerShopOpenPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PlayerShopOpenPayload::marketPos,
            PlayerShopOpenPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlayerShopOpenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                var allShops = PlayerShopConfig.getAllShops();

                Map<UUID, String> shopNames = new LinkedHashMap<>();
                Map<UUID, List<ShopEntry>> shopItems = new LinkedHashMap<>();
                Map<UUID, Map<String, Integer>> stockCounts = new LinkedHashMap<>();

                UUID requestUuid = sp.getUUID();
                Map<UUID, String> ownerNames = new LinkedHashMap<>();
                if (!allShops.containsKey(requestUuid)) {
                    shopNames.put(requestUuid, "");
                    shopItems.put(requestUuid, new ArrayList<>());
                    stockCounts.put(requestUuid, new LinkedHashMap<>());
                }
                ownerNames.put(requestUuid, sp.getName().getString());

                if (sp.level() instanceof ServerLevel sl) {
                    SellBoxEvents.refreshStockCache(sl);
                }

                Map<UUID, Map<String, Integer>> cached = SellBoxEvents.getCachedStocks();
                for (var entry : allShops.entrySet()) {
                    UUID ownerUuid = entry.getKey();
                    var info = entry.getValue();
                    shopNames.put(ownerUuid, info.shopName());
                    shopItems.put(ownerUuid, new ArrayList<>(info.items()));
                    stockCounts.put(ownerUuid, cached.getOrDefault(ownerUuid, new LinkedHashMap<>()));
                    // 解析玩家名（离线玩家也通过 profile cache 获取）
                    String ownerName = null;
                    ServerPlayer online = sp.getServer().getPlayerList().getPlayer(ownerUuid);
                    if (online != null) {
                        ownerName = online.getName().getString();
                    } else {
                        Optional<GameProfile> profile = sp.getServer().getProfileCache().get(ownerUuid);
                        if (profile.isPresent()) {
                            ownerName = profile.get().getName();
                        }
                    }
                    ownerNames.put(ownerUuid, ownerName != null ? ownerName : ownerUuid.toString().substring(0, 8));
                }

                Set<UUID> unloadedOwners = SellBoxEvents.getCachedUnloadedOwners();
                PacketDistributor.sendToPlayer(sp, new PlayerShopDataPayload(shopNames, shopItems, stockCounts, unloadedOwners, ownerNames));
            }
        });
    }
}
