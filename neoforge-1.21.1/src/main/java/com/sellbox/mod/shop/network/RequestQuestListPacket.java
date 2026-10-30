package com.sellbox.mod.shop.network;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.integration.FTBQuestsIntegration;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestQuestListPacket() implements CustomPacketPayload {
    public static final Type<RequestQuestListPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "request_quest_list"));

    public static final StreamCodec<FriendlyByteBuf, RequestQuestListPacket> CODEC = StreamCodec.of(
            (buf, payload) -> {},
            buf -> new RequestQuestListPacket()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RequestQuestListPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp && sp.hasPermissions(2)) {
                PacketDistributor.sendToPlayer(sp, new SyncQuestListPacket(FTBQuestsIntegration.getAllQuests()));
            }
        });
    }
}
