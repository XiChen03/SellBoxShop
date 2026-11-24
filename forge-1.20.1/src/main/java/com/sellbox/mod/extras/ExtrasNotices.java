package com.sellbox.mod.extras;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

/**
 * 右下角「成就式」提醒的服务端发送入口。
 * <p>
 * 协议（JSON）：
 * <pre>
 * { "titleKey":"extras.notice.sold.title", "titleArgs":["铁锭", 64],
 *   "textKey":"extras.notice.sold.text",  "textArgs":[128],
 *   "icon":"money", "kind":"sale" }
 * </pre>
 * 正文用翻译键而不是成品字符串，客户端按自己的语言渲染。
 */
public final class ExtrasNotices {
    private ExtrasNotices() {}

    /** 交易/出售类（金色） */
    public static void sale(ServerPlayer player, String titleKey, Object[] titleArgs,
                            String textKey, Object[] textArgs) {
        send(player, titleKey, titleArgs, textKey, textArgs, "money", "sale");
    }

    /** 购买类（蓝色） */
    public static void buy(ServerPlayer player, String titleKey, Object[] titleArgs,
                           String textKey, Object[] textArgs) {
        send(player, titleKey, titleArgs, textKey, textArgs, "item", "buy");
    }

    /** 邮件类（绿色） */
    public static void mail(ServerPlayer player, String titleKey, Object[] titleArgs,
                            String textKey, Object[] textArgs) {
        send(player, titleKey, titleArgs, textKey, textArgs, "mail", "mail");
    }

    /** 通用信息 / 错误 */
    public static void info(ServerPlayer player, String titleKey, Object[] titleArgs,
                            String textKey, Object[] textArgs) {
        send(player, titleKey, titleArgs, textKey, textArgs, "info", "info");
    }

    public static void error(ServerPlayer player, String titleKey, Object[] titleArgs,
                             String textKey, Object[] textArgs) {
        send(player, titleKey, titleArgs, textKey, textArgs, "error", "error");
    }

    /** 开箱类（金橙色） */
    public static void crate(ServerPlayer player, String titleKey, Object[] titleArgs,
                             String textKey, Object[] textArgs) {
        // 等开箱动画/中奖卡片出来后再弹右下角提示
        send(player, titleKey, titleArgs, textKey, textArgs, "crate", "crate",
                com.sellbox.mod.extras.ExtrasConfig.CRATE_ANIM_MS);
    }

    public static void send(ServerPlayer player, String titleKey, Object[] titleArgs,
                            String textKey, Object[] textArgs, String icon, String kind) {
        send(player, titleKey, titleArgs, textKey, textArgs, icon, kind, 0L);
    }

    public static void send(ServerPlayer player, String titleKey, Object[] titleArgs,
                            String textKey, Object[] textArgs, String icon, String kind, long delayMs) {
        if (player == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("titleKey", titleKey);
        o.add("titleArgs", args(titleArgs));
        o.addProperty("textKey", textKey);
        o.add("textArgs", args(textArgs));
        o.addProperty("icon", icon == null ? "info" : icon);
        o.addProperty("kind", kind == null ? "info" : kind);
        if (delayMs > 0) o.addProperty("delayMs", delayMs);
        com.sellbox.mod.network.ModNetwork.sendExtrasNotice(player, o.toString());
    }

    private static JsonArray args(Object[] src) {
        JsonArray arr = new JsonArray();
        if (src != null) {
            for (Object a : src) {
                if (a instanceof Number n) arr.add(n.longValue());
                else arr.add(String.valueOf(a));
            }
        }
        return arr;
    }
}
