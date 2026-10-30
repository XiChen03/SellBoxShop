package com.sellbox.mod.shop.network;

import com.sellbox.mod.SellBoxMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenShopScreenPayload() implements CustomPacketPayload {
    public static final Type<OpenShopScreenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "open_shop_screen"));
    public static final StreamCodec<FriendlyByteBuf, OpenShopScreenPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {},
            buf -> new OpenShopScreenPayload()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
