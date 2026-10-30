package com.sellbox.mod.shop.network;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.integration.FTBQuestsIntegration;
import com.sellbox.mod.shop.ShopConfig;
import com.sellbox.mod.shop.ShopEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetEntryQuestPacket(String category, int index, Long requiredQuestId) implements CustomPacketPayload {
    public static final Type<SetEntryQuestPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "set_entry_quest"));

    public static final StreamCodec<FriendlyByteBuf, SetEntryQuestPacket> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.category);
                buf.writeVarInt(payload.index);
                buf.writeBoolean(payload.requiredQuestId != null);
                if (payload.requiredQuestId != null) {
                    buf.writeLong(payload.requiredQuestId);
                }
            },
            buf -> {
                String cat = buf.readUtf();
                int idx = buf.readVarInt();
                Long qId = buf.readBoolean() ? buf.readLong() : null;
                return new SetEntryQuestPacket(cat, idx, qId);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetEntryQuestPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null || !player.hasPermissions(2)) return;

            var entries = ShopConfig.getCategory(payload.category);
            if (entries != null && payload.index >= 0 && payload.index < entries.size()) {
                ShopEntry old = entries.get(payload.index);
                String qName = payload.requiredQuestId != null ? FTBQuestsIntegration.getQuestNameById(payload.requiredQuestId) : null;
                ShopConfig.updateItem(payload.category, payload.index,
                        old.withPrerequisite(payload.requiredQuestId, qName));
            }
        });
    }
}
