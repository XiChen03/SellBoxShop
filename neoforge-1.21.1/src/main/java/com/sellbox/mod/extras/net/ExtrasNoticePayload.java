package com.sellbox.mod.extras.net;

import com.sellbox.mod.SellBoxMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 右下角成就式提醒（服务端 → 客户端）。
 * <p>
 * JSON 结构：
 * <pre>
 * { "title": "交易完成", "text": "卖出 铁锭 x64 +128 金币", "icon": "coin", "kind": "trade" }
 * </pre>
 * 客户端收到后塞进队列，由 {@code ExtrasNoticeOverlay} 在右下角滑入显示。
 */
public record ExtrasNoticePayload(String json) implements CustomPacketPayload {

    public static final Type<ExtrasNoticePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "extras_notice"));

    public static final StreamCodec<FriendlyByteBuf, ExtrasNoticePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(1_048_576), ExtrasNoticePayload::json,
                    ExtrasNoticePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
