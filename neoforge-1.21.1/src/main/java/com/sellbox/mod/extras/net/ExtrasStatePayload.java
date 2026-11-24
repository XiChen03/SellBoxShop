package com.sellbox.mod.extras.net;

import com.sellbox.mod.SellBoxMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 扩展模块的全量状态同步（服务端 → 客户端，打开界面时发一次）。
 * <p>
 * JSON 结构：
 * <pre>
 * {
 *   "balance": 1200,
 *   "sign": {"streak":5,"best":12,"today":true,"lastDay":20345,"days":[...],"milestones":[3,5]},
 *   "mails": [{"id":"...","title":"...","kind":"system","money":0,"claimed":false,"count":2,"sentAt":...}],
 *   "exchange": [{"id":"minecraft:iron_ingot","name":"铁锭","value":8}],
 *   "friends": [{"uuid":"...","name":"Steve"}],
 *   "requests": [{"uuid":"...","name":"Alex"}]
 * }
 * </pre>
 */
public record ExtrasStatePayload(String json) implements CustomPacketPayload {

    public static final Type<ExtrasStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "extras_state"));

    public static final StreamCodec<FriendlyByteBuf, ExtrasStatePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(1_048_576), ExtrasStatePayload::json,
                    ExtrasStatePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
