package com.sellbox.mod.extras.net;

import com.sellbox.mod.SellBoxMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 扩展模块的服务端响应包（服务端 → 客户端）。
 * <p>
 * 同样是 JSON 承载：
 * <pre>
 * {"ok":true,"action":"sign_in","streak":5,"balance":1200}
 * {"ok":false,"action":"redeem","error":"兑换码不存在"}
 * </pre>
 * 客户端收到后刷新界面并把 {@code message} 显示成 actionbar。
 */
public record ExtrasResponsePayload(String json) implements CustomPacketPayload {

    public static final Type<ExtrasResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "extras_response"));

    public static final StreamCodec<FriendlyByteBuf, ExtrasResponsePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(1_048_576), ExtrasResponsePayload::json,
                    ExtrasResponsePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
