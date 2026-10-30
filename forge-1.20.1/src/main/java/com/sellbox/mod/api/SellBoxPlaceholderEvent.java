package com.sellbox.mod.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

import java.util.Locale;

/**
 * SellBox 占位符事件（发布到 {@code MinecraftForge.EVENT_BUS}，请在服务端线程触发）。
 * <p>
 * 内置占位符：{@code %sellboxmoney%}（玩家余额，纯数字）。
 * <p>
 * 计分板 / TAB / 悬浮文字等模组读取余额的两种方式：
 * <ol>
 *   <li>直接查询：{@code
 *       SellBoxPlaceholderEvent ev = new SellBoxPlaceholderEvent(player, "sellboxmoney");
 *       MinecraftForge.EVENT_BUS.post(ev);
 *       if (ev.hasValue()) String money = ev.getValue();
 *   }</li>
 *   <li>整段文本替换：{@code SellBoxPlaceholders.resolve(text, player)}，
 *       会自动解析文本中所有 {@code %key%}（含 {@code %sellboxmoney%}）。</li>
 * </ol>
 * 其他模组也可监听本事件，为 SellBox 解析的文本贡献自有占位符。
 */
public class SellBoxPlaceholderEvent extends Event {
    private final ServerPlayer player;
    private final String placeholder;
    private String value;

    /**
     * @param player      占位符对应的在线玩家（非空）
     * @param placeholder 占位符键名，不带 {@code %}，大小写不敏感（如 {@code sellboxmoney}）
     */
    public SellBoxPlaceholderEvent(ServerPlayer player, String placeholder) {
        this.player = player;
        this.placeholder = placeholder == null ? "" : placeholder.toLowerCase(Locale.ROOT);
    }

    /** {@return 占位符对应的在线玩家} */
    public ServerPlayer getPlayer() {
        return player;
    }

    /** {@return 小写化、不含 % 的占位符键名} */
    public String getPlaceholder() {
        return placeholder;
    }

    /** {@return 已解析出的值；未解析时为 null} */
    public String getValue() {
        return value;
    }

    /** {@return 是否已有模组为该占位符提供了值} */
    public boolean hasValue() {
        return value != null;
    }

    /** 设置占位符的值；首个提供值的监听器生效（后到的请先检查 {@link #hasValue()}） */
    public void setValue(String value) {
        this.value = value;
    }
}
