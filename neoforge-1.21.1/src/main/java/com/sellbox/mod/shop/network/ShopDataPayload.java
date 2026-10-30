package com.sellbox.mod.shop.network;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.shop.ShopEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public record ShopDataPayload(
        String category,
        List<ShopEntry> entries,
        int balance,
        List<String> categories,
        /** 条目 key -> 已交易次数，仅限量条目才有；单人限量按接收者分别计算 */
        Map<String, Integer> usedCounts
) implements CustomPacketPayload {
    public static final Type<ShopDataPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "shop_data"));

    public static final StreamCodec<FriendlyByteBuf, ShopDataPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.category);
                buf.writeVarInt(p.entries.size());
                for (ShopEntry e : p.entries) {
                    buf.writeUtf(e.itemId().toString());
                    buf.writeUtf(e.tradeType().name());
                    buf.writeVarInt(e.price());
                    buf.writeUtf(e.entryType());
                    buf.writeUtf(e.extraData());
                    buf.writeBoolean(e.requireOp());
                    buf.writeVarInt(e.quantity());
                    buf.writeUtf(e.customName());
                    if (e.barterItemId() != null) {
                        buf.writeBoolean(true);
                        buf.writeUtf(e.barterItemId().toString());
                    } else {
                        buf.writeBoolean(false);
                    }
                    buf.writeVarInt(e.barterItemQty());
                    if (e.requiredQuestId() != null) {
                        buf.writeBoolean(true);
                        buf.writeLong(e.requiredQuestId());
                        buf.writeUtf(e.requiredQuestName() != null ? e.requiredQuestName() : "");
                    } else {
                        buf.writeBoolean(false);
                    }
                    buf.writeBoolean(e.strictMatch());
                    buf.writeUtf(e.nbt() != null ? e.nbt() : "");
                    buf.writeBoolean(e.barterOfferStrict());
                    buf.writeUtf(e.barterOfferNbt() != null ? e.barterOfferNbt() : "");
                    buf.writeBoolean(e.barterGoodsStrict());
                    buf.writeVarInt(e.tradeLimit());
                    buf.writeBoolean(e.limitPerPlayer());
                }
                buf.writeVarInt(p.balance);
                buf.writeVarInt(p.categories.size());
                for (String cat : p.categories) {
                    buf.writeUtf(cat);
                }
                Map<String, Integer> used = p.usedCounts();
                buf.writeVarInt(used.size());
                for (Map.Entry<String, Integer> e : used.entrySet()) {
                    buf.writeUtf(e.getKey());
                    buf.writeVarInt(e.getValue());
                }
            },
            buf -> {
                String cat = buf.readUtf();
                int count = buf.readVarInt();
                List<ShopEntry> es = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    ResourceLocation itemId = ResourceLocation.tryParse(buf.readUtf());
                    ShopEntry.TradeType tt = ShopEntry.TradeType.valueOf(buf.readUtf());
                    int pr = buf.readVarInt();
                    String et = buf.readUtf();
                    String ed = buf.readUtf();
                    boolean ro = buf.readBoolean();
                    int qty = buf.readVarInt();
                    String cn = buf.readUtf();
                    boolean hasB = buf.readBoolean();
                    ResourceLocation bid = hasB ? ResourceLocation.tryParse(buf.readUtf()) : null;
                    int bq = buf.readVarInt();
                    boolean hasQuest = buf.readBoolean();
                    Long rqId = hasQuest ? buf.readLong() : null;
                    String rqName = hasQuest ? buf.readUtf() : null;
                    if (rqName != null && rqName.isEmpty()) rqName = null;
                    boolean strictMatch = buf.readBoolean();
                    String nbtStr = buf.readUtf();
                    String realNbt = nbtStr.isEmpty() ? null : nbtStr;
                    boolean barterOfferStrict = buf.readBoolean();
                    String barterOfferNbtStr = buf.readUtf();
                    String realOfferNbt = barterOfferNbtStr.isEmpty() ? null : barterOfferNbtStr;
                    boolean barterGoodsStrict = buf.readBoolean();
                    int tradeLimit = buf.readVarInt();
                    boolean limitPerPlayer = buf.readBoolean();
                    if (itemId != null) {
                        es.add(new ShopEntry(itemId, tt, pr, et, ed, ro, qty, cn, bid, bq, rqId, rqName, strictMatch, realNbt, barterOfferStrict, realOfferNbt, barterGoodsStrict, tradeLimit, limitPerPlayer));
                    }
                }
                int bal = buf.readVarInt();
                int catCount = buf.readVarInt();
                List<String> cats = new ArrayList<>();
                for (int i = 0; i < catCount; i++) {
                    cats.add(buf.readUtf());
                }
                int usedCount = buf.readVarInt();
                Map<String, Integer> used = new HashMap<>();
                for (int i = 0; i < usedCount; i++) {
                    String uk = buf.readUtf();
                    used.put(uk, buf.readVarInt());
                }
                return new ShopDataPayload(cat, es, bal, cats, used);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
