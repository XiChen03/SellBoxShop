package com.sellbox.mod.shop.network;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.shop.ShopConfig;
import com.sellbox.mod.shop.ShopEntry;
import com.sellbox.mod.shop.TradeLimitTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 设置某条商品的交易限量。
 *
 * @param limit        交易次数上限，<=0 表示无限
 * @param perPlayer    true = 每个玩家各自计算，false = 全体共用一个额度
 */
public record SetEntryLimitPacket(String category, int index, int limit, boolean perPlayer) implements CustomPacketPayload {
    public static final Type<SetEntryLimitPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "set_entry_limit"));

    public static final StreamCodec<FriendlyByteBuf, SetEntryLimitPacket> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.category);
                buf.writeVarInt(payload.index);
                buf.writeVarInt(payload.limit);
                buf.writeBoolean(payload.perPlayer);
            },
            buf -> new SetEntryLimitPacket(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetEntryLimitPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null || !player.hasPermissions(2)) return;

            var entries = ShopConfig.getCategory(payload.category);
            if (entries == null || payload.index < 0 || payload.index >= entries.size()) return;

            ShopEntry old = entries.get(payload.index);
            int limit = Math.max(0, payload.limit);
            ShopEntry updated = old.withTradeLimit(limit, payload.perPlayer);
            ShopConfig.updateItem(payload.category, payload.index, updated);
            // 额度变了就清空历史计数，否则新额度要等旧计数用完才生效
            TradeLimitTracker.reset(updated, payload.category);
        });
    }
}