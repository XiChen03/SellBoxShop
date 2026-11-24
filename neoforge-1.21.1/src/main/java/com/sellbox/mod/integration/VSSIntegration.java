package com.sellbox.mod.integration;

import com.sellbox.mod.SellBoxMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.*;

/**
 * ViScriptShop（VSS）经济集成（纯反射，无编译期依赖）。
 * <p>
 * VSS 在大版本之间换过货币数据类型，本类用「double 优先、int 兜底」的双签名探测同时兼容：
 * <table border="1">
 *   <caption>两套 API 对比</caption>
 *   <tr><th>方法</th><th>1.1.x（int）</th><th>1.2.x+（double）</th></tr>
 *   <tr><td>getMoney</td><td>int getMoney(ServerPlayer)</td><td>double getMoney(ServerPlayer)</td></tr>
 *   <tr><td>addMoney</td><td>int addMoney(ServerPlayer, int)</td><td>void addMoney(ServerPlayer, double)</td></tr>
 *   <tr><td>removeMoney</td><td>void removeMoney(ServerPlayer, int)</td><td>double removeMoney(ServerPlayer, double[, boolean])</td></tr>
 *   <tr><td>setMoney</td><td>void setMoney(ServerPlayer, int)</td><td>void setMoney(ServerPlayer, double)</td></tr>
 * </table>
 * <p>
 * <b>为什么必须双探测</b>：旧实现直接 {@code getMethod("addMoney", ServerPlayer.class, int.class)}，
 * 在 1.2.x 上抛 {@code NoSuchMethodException} 被 catch 吞掉，整个集成判定为「未安装」，
 * 只留下一条 {@code VSS mod not found} 日志——看起来像没装模组，极难排查。
 * <p>
 * <b>精度</summary>：SellBox 内部金额是 {@code int}，VSS 1.2.x 是 {@code double}。
 * 读余额时四舍五入取整；第一次发生截断会打一条 warn。
 */
public class VSSIntegration {
    private static volatile boolean vssLoaded = false;
    private static boolean warnedPrecision = false;

    /** 新版（1.2.x+）货币是 double；旧版是 int */
    private static boolean moneyIsDouble = false;
    /** 探测到的 API 版本描述，用于日志/指令显示 */
    private static String apiVersion = "(none)";

    private static Class<?> serverUtilClass;

    private static Method getMoneyMethod;      // int|double getMoney(ServerPlayer)
    private static Method addMoneyMethod;      // void addMoney(ServerPlayer, int|double)
    private static Method removeMoneyMethod;   // void removeMoney(ServerPlayer, int) | double removeMoney(ServerPlayer, double[, boolean])
    private static Method setMoneyMethod;      // void setMoney(ServerPlayer, int|double)

    /** 新版才有的格式化工具（MoneyUtil.formatGrouped(double)），拿不到就用本地实现 */
    private static Method formatGroupedMethod;

    static {
        probe();
    }

    /**
     * 探测并绑定 VSS 的货币 API。
     *
     * @return 探测成功后是否可用
     */
    public static synchronized boolean probe() {
        if (vssLoaded) return true;
        try {
            serverUtilClass = Class.forName("com.viscriptshop.util.ViScriptShopServerUtil");
        } catch (Throwable t) {
            SellBoxMod.LOGGER.info("VSS mod not found, integration disabled");
            vssLoaded = false;
            return false;
        }

        // getMoney 的参数类型两版一致，只有返回类型不同
        getMoneyMethod = tryMethod(serverUtilClass, "getMoney", ServerPlayer.class);
        if (getMoneyMethod == null) {
            SellBoxMod.LOGGER.warn("VSS: ViScriptShopServerUtil found but getMoney(ServerPlayer) missing; disabled");
            vssLoaded = false;
            return false;
        }
        moneyIsDouble = getMoneyMethod.getReturnType() == double.class
                || getMoneyMethod.getReturnType() == Double.class;

        // ── 写方法：先试 double（1.2.x），再退回 int（1.1.x） ──
        Class<?> moneyType = moneyIsDouble ? double.class : int.class;
        addMoneyMethod = tryMethod(serverUtilClass, "addMoney", ServerPlayer.class, moneyType);
        setMoneyMethod = tryMethod(serverUtilClass, "setMoney", ServerPlayer.class, moneyType);
        removeMoneyMethod = tryMethod(serverUtilClass, "removeMoney", ServerPlayer.class, moneyType);

        if (addMoneyMethod == null || setMoneyMethod == null || removeMoneyMethod == null) {
            // 返回类型与写方法签名不一致时兜底：四种组合都试一遍
            Class<?> alt = moneyIsDouble ? int.class : double.class;
            if (addMoneyMethod == null) addMoneyMethod = tryMethod(serverUtilClass, "addMoney", ServerPlayer.class, alt);
            if (setMoneyMethod == null) setMoneyMethod = tryMethod(serverUtilClass, "setMoney", ServerPlayer.class, alt);
            if (removeMoneyMethod == null) removeMoneyMethod = tryMethod(serverUtilClass, "removeMoney", ServerPlayer.class, alt);

            if (addMoneyMethod == null || setMoneyMethod == null || removeMoneyMethod == null) {
                SellBoxMod.LOGGER.warn("VSS: money write methods missing (tried int and double overloads); disabled");
                vssLoaded = false;
                return false;
            }
            // 写方法用的是另一套类型，以写方法为准
            moneyIsDouble = alt == double.class;
            SellBoxMod.LOGGER.warn("VSS: getMoney/setMoney type mismatch, following write methods ({})",
                    moneyIsDouble ? "double" : "int");
        }

        // 新版格式化工具（可选）
        try {
            Class<?> moneyUtil = Class.forName("com.viscriptshop.util.MoneyUtil");
            formatGroupedMethod = tryMethod(moneyUtil, "formatGrouped", double.class);
            if (formatGroupedMethod == null) {
                formatGroupedMethod = tryMethod(moneyUtil, "format", double.class);
            }
        } catch (Throwable ignored) {
            formatGroupedMethod = null;
        }

        apiVersion = moneyIsDouble ? "1.2.x (double)" : "1.1.x (int)";
        vssLoaded = true;
        SellBoxMod.LOGGER.info("VSS integration loaded, API = {}, format = {}",
                apiVersion, formatGroupedMethod != null ? "MoneyUtil" : "local");
        return true;
    }

    private static Method tryMethod(Class<?> owner, String name, Class<?>... params) {
        try {
            Method m = owner.getMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean isLoaded() {
        return vssLoaded;
    }

    /** 探测到的 API 版本，例如 {@code "1.2.x (double)"} */
    public static String getApiVersion() {
        return apiVersion;
    }

    // ════════════════════════════════════════════
    //  余额读写
    // ════════════════════════════════════════════

    public static int getMoney(ServerPlayer player) {
        if (!vssLoaded) return 0;
        try {
            Object ret = getMoneyMethod.invoke(null, player);
            double raw = ret instanceof Number n ? n.doubleValue() : 0D;
            int rounded = (int) Math.round(raw);
            warnPrecisionOnce(raw, rounded);
            return rounded;
        } catch (Throwable t) {
            SellBoxMod.LOGGER.error("VSS getMoney failed for {}", player.getName().getString(), t);
            return 0;
        }
    }

    /**
     * 加钱。
     *
     * @return 旧版返回新的余额（int）；新版返回 void，这里回读一次 getMoney
     */
    public static int addMoney(ServerPlayer player, int amount) {
        if (!vssLoaded || amount <= 0 || addMoneyMethod == null) return 0;
        try {
            Object ret = addMoneyMethod.invoke(null, player, toApiAmount(amount));
            if (!moneyIsDouble && ret instanceof Number n) {
                return (int) n.longValue();
            }
            return getMoney(player);
        } catch (Throwable t) {
            SellBoxMod.LOGGER.error("VSS addMoney failed for {}", player.getName().getString(), t);
            return 0;
        }
    }

    public static void removeMoney(ServerPlayer player, int amount) {
        if (!vssLoaded || amount <= 0 || removeMoneyMethod == null) return;
        try {
            removeMoneyMethod.invoke(null, player, toApiAmount(amount));
        } catch (Throwable t) {
            SellBoxMod.LOGGER.error("VSS removeMoney failed for {}", player.getName().getString(), t);
        }
    }

    public static void setMoney(ServerPlayer player, int amount) {
        if (!vssLoaded || setMoneyMethod == null) return;
        try {
            setMoneyMethod.invoke(null, player, toApiAmount(amount));
        } catch (Throwable t) {
            SellBoxMod.LOGGER.error("VSS setMoney failed for {}", player.getName().getString(), t);
        }
    }

    /** 把 SellBox 的 int 金额转成当前 API 需要的参数类型 */
    private static Object toApiAmount(int amount) {
        return moneyIsDouble ? (Object) (double) amount : (Object) amount;
    }

    /** 第一次发生小数截断时打一条 warn（只打一次） */
    private static void warnPrecisionOnce(double raw, int rounded) {
        if (warnedPrecision) return;
        if (Math.abs(raw - rounded) > 1.0e-6) {
            warnedPrecision = true;
            SellBoxMod.LOGGER.warn(
                    "VSS: balance {} is not an integer, SellBox rounded it to {}. "
                            + "This economy uses decimals; SellBox only supports whole numbers.",
                    raw, rounded);
        }
    }

    // ════════════════════════════════════════════
    //  排行榜
    // ════════════════════════════════════════════

    public static void sendLeaderboard(ServerPlayer requester, int page) {
        if (!vssLoaded) {
            requester.sendSystemMessage(Component.translatable("message.sellbox.vss_not_found"));
            return;
        }

        try {
            List<ServerPlayer> allPlayers = requester.server.getPlayerList().getPlayers();
            List<Map.Entry<String, Integer>> rankings = new ArrayList<>();

            for (ServerPlayer player : allPlayers) {
                rankings.add(new AbstractMap.SimpleEntry<>(player.getName().getString(), getMoney(player)));
            }
            rankings.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

            int pageSize = 8;
            int totalPages = Math.max(1, (int) Math.ceil((double) rankings.size() / pageSize));
            int rawPage = Math.min(page, totalPages);
            if (rawPage < 1) rawPage = 1;
            final int currentPage = rawPage;

            int start = (currentPage - 1) * pageSize;
            int end = Math.min(start + pageSize, rankings.size());

            requester.sendSystemMessage(Component.literal("").withStyle(ChatFormatting.GOLD));

            MutableComponent header = Component.literal(" ====== ").withStyle(ChatFormatting.GOLD)
                    .append(Component.translatable("message.sellbox.leaderboard_title").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" ======").withStyle(ChatFormatting.GOLD));
            requester.sendSystemMessage(header);

            if (rankings.isEmpty()) {
                requester.sendSystemMessage(Component.translatable("message.sellbox.leaderboard_empty").withStyle(ChatFormatting.GRAY));
            } else {
                int maxNameWidth = 0;
                for (int i = start; i < end; i++) {
                    maxNameWidth = Math.max(maxNameWidth, rankings.get(i).getKey().length());
                }
                maxNameWidth = Math.min(maxNameWidth + 2, 20);

                for (int i = start; i < end; i++) {
                    var entry = rankings.get(i);
                    String num = (i + 1) + ".";
                    String name = entry.getKey();
                    String balance = formatMoney(entry.getValue());

                    String sb = " " + String.format("%-4s", num) +
                            String.format("%-" + maxNameWidth + "s", name) +
                            " " + balance;

                    ChatFormatting color = (i == 0) ? ChatFormatting.GOLD :
                            (i == 1) ? ChatFormatting.WHITE :
                                    (i == 2) ? ChatFormatting.RED : ChatFormatting.GRAY;

                    requester.sendSystemMessage(Component.literal(sb).withStyle(color));
                }
            }

            requester.sendSystemMessage(
                    Component.literal(" ====*====*====*====").withStyle(ChatFormatting.DARK_GRAY)
            );

            if (totalPages > 1) {
                MutableComponent nav = Component.literal("");
                if (currentPage > 1) {
                    MutableComponent prev = Component.literal(" << ")
                            .withStyle(ChatFormatting.GREEN)
                            .withStyle(style -> style.withClickEvent(
                                    new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                            "/sellbox leaderboard " + (currentPage - 1))
                            ));
                    nav = nav.append(prev);
                }
                nav = nav.append(Component.literal(" " + currentPage + "/" + totalPages + " ").withStyle(ChatFormatting.GRAY));
                if (currentPage < totalPages) {
                    MutableComponent next = Component.literal(" >> ")
                            .withStyle(ChatFormatting.GREEN)
                            .withStyle(style -> style.withClickEvent(
                                    new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                            "/sellbox leaderboard " + (currentPage + 1))
                            ));
                    nav = nav.append(next);
                }
                requester.sendSystemMessage(nav);
            }

            requester.sendSystemMessage(Component.literal("").withStyle(ChatFormatting.GOLD));

        } catch (Exception e) {
            SellBoxMod.LOGGER.error("Failed to send leaderboard", e);
        }
    }

    /**
     * 格式化金额。
     * <p>VSS 1.2.x 自带 {@code MoneyUtil.formatGrouped(double)}，有就用它（风格与 VSS 界面一致），
     * 否则退回本地 {@code String.format("%,d")}。
     */
    public static String formatMoney(int amount) {
        if (vssLoaded && formatGroupedMethod != null) {
            try {
                Object v = formatGroupedMethod.invoke(null, (double) amount);
                if (v instanceof String s && !s.isEmpty()) return s;
            } catch (Throwable ignored) {
            }
        }
        return String.format(Locale.ROOT, "%,d", amount);
    }
}
