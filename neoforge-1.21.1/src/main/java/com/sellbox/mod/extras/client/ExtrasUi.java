package com.sellbox.mod.extras.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扩展面板的纯工具方法：翻译缓存、颜色、解析。
 * <p>
 * 这些方法不碰界面状态，抽出来是为了让 {@link ExtrasScreen} 和各个页面类都能用，
 * 也避免主界面类继续膨胀。
 */
public final class ExtrasUi {
    private ExtrasUi() {}

    /** 翻译缓存（语言 + 键 + 参数 → 文本） */
    private static final Map<String, String> TR_CACHE = new ConcurrentHashMap<>();
    private static String TR_LANG = null;

    /**
     * 翻译键 → 显示文本。
     * <p>
     * 界面每帧要取几十上百个标签，直接走 {@code Component.translatable(...).getString()}
     * 每次都做一遍语言查找 + 参数替换，开销不小，所以按「语言 + 键 + 参数」缓存，
     * 语言切换时整体作废。
     */
    public static String tr(String key, Object... args) {
        String lang = null;
        try {
            lang = Minecraft.getInstance().getLanguageManager().getSelected();
        } catch (Throwable ignored) {
        }
        if (lang == null || !lang.equals(TR_LANG)) {
            TR_LANG = lang;
            TR_CACHE.clear();
        }
        String ck = args.length == 0 ? key : key + ((char) 0) + Arrays.deepToString(args);
        String hit = TR_CACHE.get(ck);
        if (hit != null) return hit;
        String text = args.length == 0
                ? Component.translatable(key).getString()
                : Component.translatable(key, args).getString();
        // 参数里可能带玩家名/物品名，缓存不能无限增长
        if (TR_CACHE.size() > 512) TR_CACHE.clear();
        TR_CACHE.put(ck, text);
        return text;
    }

    /** 品质 → 颜色 */
    public static int rarityColor(String rarity) {
        return switch (rarity == null ? "" : rarity) {
            case "uncommon" -> 0xFF55FF55;  // 绿
            case "rare" -> 0xFF55AAFF;      // 蓝
            case "epic" -> 0xFFAA55FF;      // 紫
            case "legendary" -> 0xFFFFAA00; // 金
            case "mythic" -> 0xFFFF5555;    // 红
            default -> 0xFFFFFFFF;          // 白
        };
    }

    /** 品质 → 本地化名称（平凡/普通/精致/史诗/传说/绝世） */
    public static String rarityName(String rarity) {
        return switch (rarity == null ? "" : rarity) {
            case "uncommon" -> tr("extras.rarity.uncommon");
            case "rare" -> tr("extras.rarity.rare");
            case "epic" -> tr("extras.rarity.epic");
            case "legendary" -> tr("extras.rarity.legendary");
            case "mythic" -> tr("extras.rarity.mythic");
            default -> tr("extras.rarity.common");
        };
    }

    /** ARGB 线性插值 */
    public static int mix(int a, int b, float t) {
        int aa = (a >>> 24) & 255, ab = (b >>> 24) & 255;
        int A = Math.round(aa * (1 - t) + ab * t);
        int R = Math.round(((a >> 16) & 255) * (1f - t) + ((b >> 16) & 255) * t);
        int G = Math.round(((a >> 8) & 255) * (1f - t) + ((b >> 8) & 255) * t);
        int B = Math.round((a & 255) * (1f - t) + (b & 255) * t);
        return (A << 24) | (R << 16) | (G << 8) | B;
    }

    /** 输入框 → 非负 long（解析失败当 0） */
    public static long parseLong(EditBox box) {
        if (box == null) return 0L;
        try {
            return Math.max(0L, Long.parseLong(box.getValue().trim()));
        } catch (Throwable t) {
            return 0L;
        }
    }

    /** 输入框 → 非负 long；空输入返回 null，非法输入也返回 null（用于区分空和填错） */
    public static Long parseLongOrNull(EditBox box) {
        if (box == null) return null;
        String v = box.getValue().trim();
        if (v.isEmpty()) return null;
        try {
            return Math.max(0L, Long.parseLong(v));
        } catch (Throwable t) {
            return null;
        }
    }

    /** 输入框 → 非负 int；空输入返回 null，非法输入也返回 null */
    public static Integer parseIntOrNull(EditBox box) {
        if (box == null) return null;
        String v = box.getValue().trim();
        if (v.isEmpty()) return null;
        try {
            return Math.max(0, Integer.parseInt(v));
        } catch (Throwable t) {
            return null;
        }
    }

    /** 输入框 → 非负 int（解析失败当 0） */
    public static int parseInt(EditBox box) {
        if (box == null) return 0;
        try {
            return Math.max(0, Integer.parseInt(box.getValue().trim()));
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 输入框 → double；空或非法返回 def */
    public static double parseDouble(EditBox box, double def) {
        if (box == null) return def;
        String v = box.getValue().trim();
        if (v.isEmpty()) return def;
        try {
            double d = Double.parseDouble(v);
            return Double.isFinite(d) ? d : def;
        } catch (Throwable t) {
            return def;
        }
    }

    /** 邮件类型 → 默认标题键 */
    public static String defaultTitleKey(String kind) {
        return switch (kind == null ? "" : kind) {
            case "signin" -> "extras.mail.kind.signin";
            case "redeem" -> "extras.mail.kind.redeem";
            case "transfer" -> "extras.mail.kind.transfer";
            case "admin" -> "extras.mail.kind.admin";
            case "inventory_full" -> "extras.mail.kind.inventory_full";
            case "player" -> "extras.mail.kind.player";
            case "welcome" -> "extras.mail.kind.welcome";
            case "record" -> "extras.mail.kind.record";
            default -> "extras.mail.kind.system";
        };
    }

    /** 百分比格式化：权重 / 总和 */
    public static String pct(double part, double total) {
        return total > 0 ? String.format(Locale.ROOT, "%.1f%%", part * 100.0 / total) : "-";
    }
}
