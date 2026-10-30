package com.sellbox.mod.playershop.network;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.playershop.PlayerShopConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PlayerShopNamePayload(
        String shopName,
        BlockPos marketPos
) implements CustomPacketPayload {
    public static final Type<PlayerShopNamePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "player_shop_name"));

    public static final StreamCodec<FriendlyByteBuf, PlayerShopNamePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PlayerShopNamePayload::shopName,
            BlockPos.STREAM_CODEC, PlayerShopNamePayload::marketPos,
            PlayerShopNamePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlayerShopNamePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                PlayerShopConfig.setShopName(sp.getUUID(), payload.shopName());
            }
        });
    }
}
