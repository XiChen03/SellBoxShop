package com.sellbox.mod.extras.net;

import com.sellbox.mod.SellBoxMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 扩展模块的通用请求包（客户端 → 服务端）。
 * <p>
 * 用一个 JSON 字符串承载所有动作参数，避免为签到/邮箱/转账/兑换/兑换码/好友
 * 各写一个 payload。服务端按 {@link #action()} 分发。
 * <p>
 * 结构：
 * <pre>
 * {"action":"sign_in"}
 * {"action":"mail_claim","id":"&lt;mailId&gt;"}
 * {"action":"transfer","target":"&lt;玩家名&gt;","amount":100}
 * {"action":"exchange_in","item":"minecraft:iron_ingot","count":8}
 * {"action":"exchange_out","item":"minecraft:iron_ingot","count":8}
 * {"action":"redeem","code":"ABC123"}
 * {"action":"friend_add","name":"Steve"}
 * {"action":"friend_accept","uuid":"..."}
 * {"action":"friend_remove","uuid":"..."}
 * </pre>
 */
public record ExtrasRequestPayload(String json) implements CustomPacketPayload {

    public static final Type<ExtrasRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "extras_request"));

    public static final StreamCodec<FriendlyByteBuf, ExtrasRequestPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(1_048_576), ExtrasRequestPayload::json,
                    ExtrasRequestPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** null 安全的字符串写入（防止 EncoderException 踢人） */
    public static String safe(String s) {
        return s == null ? "" : s;
    }
}
