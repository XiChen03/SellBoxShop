package com.sellbox.mod.shop.network;

import com.sellbox.mod.SellBoxMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncShopConfigPayload(
        boolean vssCurrencyEnabled,
        boolean shopOverrideEnabled,
        boolean editMode,
        boolean adminEditMode
) implements CustomPacketPayload {
    public static final Type<SyncShopConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "sync_shop_config"));

    public static final StreamCodec<FriendlyByteBuf, SyncShopConfigPayload> CODEC = StreamCodec.composite(
            StreamCodec.of(FriendlyByteBuf::writeBoolean, FriendlyByteBuf::readBoolean), SyncShopConfigPayload::vssCurrencyEnabled,
            StreamCodec.of(FriendlyByteBuf::writeBoolean, FriendlyByteBuf::readBoolean), SyncShopConfigPayload::shopOverrideEnabled,
            StreamCodec.of(FriendlyByteBuf::writeBoolean, FriendlyByteBuf::readBoolean), SyncShopConfigPayload::editMode,
            StreamCodec.of(FriendlyByteBuf::writeBoolean, FriendlyByteBuf::readBoolean), SyncShopConfigPayload::adminEditMode,
            SyncShopConfigPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}