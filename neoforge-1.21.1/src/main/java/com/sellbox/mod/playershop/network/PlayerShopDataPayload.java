package com.sellbox.mod.playershop.network;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.shop.ShopEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public record PlayerShopDataPayload(
        Map<UUID, String> shopNames,
        Map<UUID, List<ShopEntry>> shopItems,
        Map<UUID, Map<String, Integer>> stockCounts,
        Set<UUID> unloadedOwners,
        Map<UUID, String> ownerNames
) implements CustomPacketPayload {
    public static final Type<PlayerShopDataPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "player_shop_data"));

    /** null 安全：任何 String 字段透传前都过一遍，避免 EncoderException 把玩家踢下线 */
    private static String safe(String s) {
        return s == null ? "" : s;
    }

    public static final StreamCodec<FriendlyByteBuf, PlayerShopDataPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.shopNames.size());
                for (var entry : payload.shopNames.entrySet()) {
                    buf.writeUUID(entry.getKey());
                    buf.writeUtf(safe(entry.getValue()));
                }

                buf.writeVarInt(payload.shopItems.size());
                for (var entry : payload.shopItems.entrySet()) {
                    buf.writeUUID(entry.getKey());
                    List<ShopEntry> items = entry.getValue();
                    buf.writeVarInt(items.size());
                    for (ShopEntry item : items) {
                        buf.writeUtf(safe(item.itemId().toString()));
                        buf.writeUtf(safe(item.tradeType().name()));
                        buf.writeVarInt(item.price());
                        buf.writeUtf(safe(item.entryType()));
                        buf.writeUtf(safe(item.extraData()));
                        buf.writeBoolean(item.requireOp());
                        buf.writeVarInt(item.quantity());
                        buf.writeUtf(safe(item.customName()));
                        if (item.barterItemId() != null) {
                            buf.writeBoolean(true);
                            buf.writeUtf(safe(item.barterItemId().toString()));
                        } else {
                            buf.writeBoolean(false);
                        }
                        buf.writeVarInt(item.barterItemQty());
                        buf.writeBoolean(item.strictMatch());
                        buf.writeUtf(safe(item.nbt() != null ? item.nbt() : ""));
                        buf.writeBoolean(item.barterOfferStrict());
                        buf.writeUtf(safe(item.barterOfferNbt() != null ? item.barterOfferNbt() : ""));
                        buf.writeBoolean(item.barterGoodsStrict());
                        if (item.requiredQuestId() != null) {
                            buf.writeBoolean(true);
                            buf.writeLong(item.requiredQuestId());
                            buf.writeUtf(safe(item.requiredQuestName() != null ? item.requiredQuestName() : ""));
                        } else {
                            buf.writeBoolean(false);
                        }
                    }
                }

                buf.writeVarInt(payload.stockCounts.size());
                for (var entry : payload.stockCounts.entrySet()) {
                    buf.writeUUID(entry.getKey());
                    Map<String, Integer> stocks = entry.getValue();
                    buf.writeVarInt(stocks.size());
                    for (var stock : stocks.entrySet()) {
                        buf.writeUtf(safe(stock.getKey()));
                        buf.writeVarInt(stock.getValue());
                    }
                }

                buf.writeVarInt(payload.unloadedOwners.size());
                for (UUID uid : payload.unloadedOwners) {
                    buf.writeUUID(uid);
                }

                buf.writeVarInt(payload.ownerNames.size());
                for (var entry : payload.ownerNames.entrySet()) {
                    buf.writeUUID(entry.getKey());
                    buf.writeUtf(safe(entry.getValue()));
                }
            },
            buf -> {
                int shopNamesSize = buf.readVarInt();
                Map<UUID, String> shopNames = new LinkedHashMap<>();
                for (int i = 0; i < shopNamesSize; i++) {
                    shopNames.put(buf.readUUID(), buf.readUtf());
                }

                int shopItemsSize = buf.readVarInt();
                Map<UUID, List<ShopEntry>> shopItems = new LinkedHashMap<>();
                for (int i = 0; i < shopItemsSize; i++) {
                    UUID uuid = buf.readUUID();
                    int itemsSize = buf.readVarInt();
                    List<ShopEntry> items = new ArrayList<>();
                    for (int j = 0; j < itemsSize; j++) {
                        ResourceLocation itemId = ResourceLocation.tryParse(buf.readUtf());
                        ShopEntry.TradeType tradeType = ShopEntry.TradeType.valueOf(buf.readUtf());
                        int price = buf.readVarInt();
                        String entryType = buf.readUtf();
                        String extraData = buf.readUtf();
                        boolean requireOp = buf.readBoolean();
                        int quantity = buf.readVarInt();
                        String customName = buf.readUtf();
                        boolean hasBarter = buf.readBoolean();
                        ResourceLocation barterItemId = hasBarter ? ResourceLocation.tryParse(buf.readUtf()) : null;
                        int barterItemQty = buf.readVarInt();
                        boolean strictMatch = buf.readBoolean();
                        String nbtStr = buf.readUtf();
                        String realNbt = nbtStr.isEmpty() ? null : nbtStr;
                        boolean barterOfferStrict = buf.readBoolean();
                        String barterOfferNbtStr = buf.readUtf();
                        String realOfferNbt = barterOfferNbtStr.isEmpty() ? null : barterOfferNbtStr;
                        boolean barterGoodsStrict = buf.readBoolean();
                        boolean hasQuest = buf.readBoolean();
                        Long requiredQuestId = hasQuest ? buf.readLong() : null;
                        String requiredQuestName = hasQuest ? buf.readUtf() : null;
                        if (requiredQuestName != null && requiredQuestName.isEmpty()) requiredQuestName = null;
                        if (itemId != null) {
                            items.add(new ShopEntry(itemId, tradeType, price, entryType, extraData, requireOp, quantity, customName, barterItemId, barterItemQty, requiredQuestId, requiredQuestName, strictMatch, realNbt, barterOfferStrict, realOfferNbt, barterGoodsStrict));
                        }
                    }
                    shopItems.put(uuid, items);
                }

                int stockCountsSize = buf.readVarInt();
                Map<UUID, Map<String, Integer>> stockCounts = new LinkedHashMap<>();
                for (int i = 0; i < stockCountsSize; i++) {
                    UUID uuid = buf.readUUID();
                    int stocksSize = buf.readVarInt();
                    Map<String, Integer> stocks = new LinkedHashMap<>();
                    for (int j = 0; j < stocksSize; j++) {
                        stocks.put(buf.readUtf(), buf.readVarInt());
                    }
                    stockCounts.put(uuid, stocks);
                }

                int unloadedSize = buf.readVarInt();
                Set<UUID> unloadedOwners = new HashSet<>();
                for (int i = 0; i < unloadedSize; i++) {
                    unloadedOwners.add(buf.readUUID());
                }

                int ownerNamesSize = buf.readVarInt();
                Map<UUID, String> ownerNames = new LinkedHashMap<>();
                for (int i = 0; i < ownerNamesSize; i++) {
                    ownerNames.put(buf.readUUID(), buf.readUtf());
                }

                return new PlayerShopDataPayload(shopNames, shopItems, stockCounts, unloadedOwners, ownerNames);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
