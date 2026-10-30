package com.sellbox.mod.playershop.network;

import com.sellbox.mod.SellBoxMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenPlayerShopScreenPayload(BlockPos marketPos) implements CustomPacketPayload {
    public static final Type<OpenPlayerShopScreenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "open_player_shop_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenPlayerShopScreenPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenPlayerShopScreenPayload::marketPos,
            OpenPlayerShopScreenPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
