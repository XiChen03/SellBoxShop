package com.sellbox.mod.api;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.shop.SellBoxEconomy;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SellBox 占位符工具。内置 {@code %sellboxmoney%}（玩家余额，纯数字）。
 * <p>
 * 文本示例：{@code "余额: %sellboxmoney%"} → {@code "余额: 1234"}
 */
public final class SellBoxPlaceholders {
    /** 匹配 %key%（key 不含 % 与空白） */
    private static final Pattern TOKEN = Pattern.compile("%([^%\\s]+)%");

    private SellBoxPlaceholders() {}

    /**
     * 解析文本中所有 {@code %key%} 占位符（含内置 {@code %sellboxmoney%}）。
     * 未识别的占位符保持原样。请在服务端线程调用。
     *
     * @param text   原始文本（可为 null）
     * @param player 占位符对应的在线玩家
     * @return 替换后的文本
     */
    public static String resolve(String text, ServerPlayer player) {
        if (text == null || player == null || text.indexOf('%') < 0) return text;
        Matcher m = TOKEN.matcher(text);
        if (!m.find()) return text;
        StringBuilder sb = new StringBuilder(text.length() + 16);
        int last = 0;
        do {
            sb.append(text, last, m.start());
            SellBoxPlaceholderEvent ev = new SellBoxPlaceholderEvent(player, m.group(1));
            NeoForge.EVENT_BUS.post(ev);
            sb.append(ev.hasValue() ? ev.getValue() : m.group(0));
            last = m.end();
        } while (m.find());
        sb.append(text, last, text.length());
        return sb.toString();
    }

    /** 内置占位符：sellboxmoney / sellbox_money → 玩家余额（纯数字） */
    @EventBusSubscriber(modid = SellBoxMod.MODID)
    public static class Builtin {
        @SubscribeEvent
        public static void onPlaceholder(SellBoxPlaceholderEvent ev) {
            if (ev.hasValue()) return;
            String key = ev.getPlaceholder();
            if ("sellboxmoney".equals(key) || "sellbox_money".equals(key)) {
                ev.setValue(String.valueOf(SellBoxEconomy.getBalance(ev.getPlayer())));
            }
        }
    }
}
