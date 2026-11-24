package com.sellbox.mod.integration;

import com.sellbox.mod.SellBoxMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * SDMShop 经济集成（纯反射，无编译期依赖）。
 * <p>
 * SDMShop 在 7.x 之后换了一套完全不同的经济系统，两代的类名、货币类型、调用方式都不一样，
 * 本类用「新 API 优先、旧 API 兜底」的多路探测同时兼容：
 *
 * <table border="1">
 *   <caption>两代 API 对比</caption>
 *   <tr><th></th><th>6.x（sdmshoprework）</th><th>7.x+（SDM Economy）</th></tr>
 *   <tr><td>入口类</td><td>{@code net.sixik.sdmshoprework.economy.EconomyManager}</td>
 *       <td>{@code dev.sixik.sdmeconomy.api.EconomyApi}</td></tr>
 *   <tr><td>取余额</td><td>{@code Function<ServerPlayer, Long>} 存在 public 字段 {@code economy}</td>
 *       <td>{@code EconomyApi.getAccount(player).getBalance(currency)} → {@link BigDecimal}</td></tr>
 *   <tr><td>改余额</td><td>{@code BiConsumer<ServerPlayer, Long>}</td>
 *       <td>{@code account.setBalance(currency, BigDecimal)} / {@code modify(...)}</td></tr>
 *   <tr><td>货币</td><td>单一内置货币</td><td>多货币注册表，默认 {@code sdm:coin}</td></tr>
 * </table>
 *
 * <p><b>精度</b>：SellBox 内部是 {@code int}，新版是 {@link BigDecimal}/{@code double}。
 * 读余额时四舍五入，第一次发生小数截断会打一条 warn。
 */
public class SDMShopIntegration {

    /** 探测到的 API 世代 */
    private enum ApiKind {
        NONE,
        /** 6.x：net.sixik.sdmshoprework */
        LEGACY,
        /** 7.x+：dev.sixik.sdmeconomy */
        SDM_ECONOMY
    }

    private static volatile ApiKind api = ApiKind.NONE;
    /** 用于日志/指令展示 */
    private static String apiVersion = "(none)";
    private static boolean warnedPrecision = false;

    // ── 6.x ──
    private static Field economyField;
    private static Method getMethod;
    private static Method setMethod;
    private static Method syncMethod;

    // ── 7.x+ ──
    private static Class<?> economyApiClass;
    private static Class<?> bankAccountClass;
    private static Class<?> storedCurrencyClass;
    private static Method apiGetAccount;         // BankAccount getAccount(Player)
    private static Method apiGetStoredCurrency;  // IStoredCurrency getStoredCurrency(ResourceLocation)
    private static Method apiGetStoredCurrencies;// Map getStoredCurrencies(boolean)
    private static Method accGetBalance;         // BigDecimal getBalance(IStoredCurrency)
    private static Method accSetBalance;         // void setBalance(IStoredCurrency, BigDecimal)
    private static Method accModify;             // void modify(IStoredCurrency, BigDecimal)
    private static Method currencyFormat;        // String format(BigDecimal)  —— 可选
    /** 解析出来的默认货币实例（sdm:coin），延迟到首次使用时解析 */
    private static Object defaultCurrency;
    private static boolean currencyResolveFailed = false;

    static {
        probe();
    }

    /**
     * 探测可用 API。新 API 优先（7.x 是当前主线），失败再退回 6.x。
     *
     * @return 是否探测到任一可用 API
     */
    public static synchronized boolean probe() {
        if (api != ApiKind.NONE) return true;

        if (probeSdmEconomy()) return true;
        if (probeLegacy()) return true;

        SellBoxMod.LOGGER.info("SDMShop mod not found, integration disabled");
        api = ApiKind.NONE;
        return false;
    }

    // ────────────────────────────────────────────
    //  7.x+：SDM Economy
    // ────────────────────────────────────────────
    private static boolean probeSdmEconomy() {
        try {
            economyApiClass = Class.forName("dev.sixik.sdmeconomy.api.EconomyApi");
            bankAccountClass = Class.forName("dev.sixik.sdmshop2.libs.sdmeconomy.BankAccount");
            storedCurrencyClass = Class.forName("dev.sixik.sdmshop2.libs.sdmeconomy.IStoredCurrency");

            apiGetAccount = economyApiClass.getMethod("getAccount", Player.class);
            apiGetStoredCurrency = economyApiClass.getMethod("getStoredCurrency", ResourceLocation.class);
            apiGetStoredCurrencies = economyApiClass.getMethod("getStoredCurrencies", boolean.class);

            accGetBalance = bankAccountClass.getMethod("getBalance", storedCurrencyClass);
            accSetBalance = bankAccountClass.getMethod("setBalance", storedCurrencyClass, BigDecimal.class);
            accModify = bankAccountClass.getMethod("modify", storedCurrencyClass, BigDecimal.class);

            // 格式化（可选）
            try {
                currencyFormat = storedCurrencyClass.getMethod("format", BigDecimal.class);
            } catch (Throwable ignored) {
                currencyFormat = null;
            }

            api = ApiKind.SDM_ECONOMY;
            apiVersion = "7.x+ (SDM Economy)";
            SellBoxMod.LOGGER.info("SDMShop integration loaded, API = {}", apiVersion);
            return true;
        } catch (Throwable t) {
            // 类不存在是正常情况（用户装的是 6.x 或没装），只打 debug
            SellBoxMod.LOGGER.debug("SDMShop 7.x API not present: {}", t.toString());
            resetSdmEconomy();
            return false;
        }
    }

    private static void resetSdmEconomy() {
        economyApiClass = null;
        bankAccountClass = null;
        storedCurrencyClass = null;
        apiGetAccount = null;
        apiGetStoredCurrency = null;
        apiGetStoredCurrencies = null;
        accGetBalance = null;
        accSetBalance = null;
        accModify = null;
        currencyFormat = null;
        defaultCurrency = null;
        currencyResolveFailed = false;
    }

    // ────────────────────────────────────────────
    //  6.x：sdmshoprework
    // ────────────────────────────────────────────
    private static boolean probeLegacy() {
        String cp = "net.sixik.sdmshoprework.economy.EconomyManager";
        try {
            Class<?> mgrClass = Class.forName(cp);
            economyField = mgrClass.getField("economy");
            Class<?> moduleClass = Class.forName(cp + "$EconomyModule");
            getMethod = moduleClass.getMethod("get");
            setMethod = moduleClass.getMethod("set");
            try {
                syncMethod = moduleClass.getMethod("sync");
            } catch (Throwable ignored) {
                syncMethod = null;
            }

            api = ApiKind.LEGACY;
            apiVersion = "6.x (sdmshoprework)";
            SellBoxMod.LOGGER.info("SDMShop integration loaded, API = {}", apiVersion);
            return true;
        } catch (Throwable t) {
            SellBoxMod.LOGGER.debug("SDMShop 6.x API not present: {}", t.toString());
            economyField = null;
            getMethod = null;
            setMethod = null;
            syncMethod = null;
            return false;
        }
    }

    // ════════════════════════════════════════════
    //  对外接口
    // ════════════════════════════════════════════

    public static boolean isLoaded() {
        return api != ApiKind.NONE;
    }

    /** 探测到的 API 版本，例如 {@code "7.x+ (SDM Economy)"} */
    public static String getApiVersion() {
        return isLoaded() ? apiVersion : "(none)";
    }

    private static boolean isEconomyReady() {
        return switch (api) {
            case LEGACY -> {
                try {
                    yield economyField != null && economyField.get(null) != null;
                } catch (Throwable t) {
                    yield false;
                }
            }
            case SDM_ECONOMY -> resolveCurrency() != null;
            case NONE -> false;
        };
    }

    /**
     * 解析默认货币 {@code sdm:coin}。
     * <p>不能放在 {@code <clinit>}：货币注册表要等服务端数据包加载完才填好。
     */
    private static Object resolveCurrency() {
        if (defaultCurrency != null) return defaultCurrency;
        if (currencyResolveFailed) return null;
        if (api != ApiKind.SDM_ECONOMY) return null;

        try {
            ResourceLocation coinId = ResourceLocation.tryParse("sdm:coin");
            Object c = coinId != null ? apiGetStoredCurrency.invoke(null, coinId) : null;

            if (c == null) {
                // 兜底：注册表里第一个已存储货币
                Object map = apiGetStoredCurrencies.invoke(null, false);
                if (map instanceof Map<?, ?> m && !m.isEmpty()) {
                    c = m.values().iterator().next();
                }
            }

            if (c == null) {
                currencyResolveFailed = true;
                SellBoxMod.LOGGER.warn("SDMShop: no stored currency found (expected sdm:coin); integration idle");
                return null;
            }

            defaultCurrency = c;
            SellBoxMod.LOGGER.info("SDMShop: using currency sdm:coin");
            return defaultCurrency;
        } catch (Throwable t) {
            currencyResolveFailed = true;
            SellBoxMod.LOGGER.error("SDMShop: failed to resolve currency", t);
            return null;
        }
    }

    public static int getMoney(ServerPlayer player) {
        if (!isEconomyReady()) return 0;
        try {
            if (api == ApiKind.LEGACY) {
                Object economy = economyField.get(null);
                Object getFunc = getMethod.invoke(economy);
                @SuppressWarnings("unchecked")
                long result = ((Function<ServerPlayer, Long>) getFunc).apply(player);
                return (int) result;
            }

            Object account = apiGetAccount.invoke(null, player);
            if (account == null) return 0;
            Object raw = accGetBalance.invoke(account, resolveCurrency());
            if (!(raw instanceof BigDecimal bd)) return 0;
            int rounded = bd.setScale(0, java.math.RoundingMode.HALF_UP).intValue();
            warnPrecisionOnce(bd, rounded);
            return rounded;
        } catch (Throwable t) {
            SellBoxMod.LOGGER.error("SDMShop getMoney failed for {}", player.getName().getString(), t);
            return 0;
        }
    }

    public static void addMoney(ServerPlayer player, int amount) {
        if (!isEconomyReady() || amount <= 0) return;
        if (api == ApiKind.LEGACY) {
            setMoney(player, getMoney(player) + amount);
            return;
        }
        modify(player, BigDecimal.valueOf(amount));
    }

    public static void removeMoney(ServerPlayer player, int amount) {
        if (!isEconomyReady() || amount <= 0) return;
        if (api == ApiKind.LEGACY) {
            setMoney(player, Math.max(0, getMoney(player) - amount));
            return;
        }
        // 新版允许负余额，但 SellBox 的语义是「最多扣到 0」，这里自己夹一下
        int current = getMoney(player);
        int delta = Math.min(amount, Math.max(0, current));
        if (delta <= 0) return;
        modify(player, BigDecimal.valueOf(-delta));
    }

    public static void setMoney(ServerPlayer player, int amount) {
        if (!isEconomyReady()) return;
        try {
            if (api == ApiKind.LEGACY) {
                Object economy = economyField.get(null);
                Object setFunc = setMethod.invoke(economy);
                @SuppressWarnings("unchecked")
                BiConsumer<ServerPlayer, Long> consumer = (BiConsumer<ServerPlayer, Long>) setFunc;
                consumer.accept(player, (long) amount);
                // 旧版需要显式 sync 才能让客户端余额刷新
                if (syncMethod != null) {
                    try {
                        syncMethod.invoke(economy, player);
                    } catch (Throwable ignored) {
                    }
                }
                return;
            }

            Object account = apiGetAccount.invoke(null, player);
            if (account == null) return;
            accSetBalance.invoke(account, resolveCurrency(), BigDecimal.valueOf(amount));
        } catch (Throwable t) {
            SellBoxMod.LOGGER.error("SDMShop setMoney failed for {}", player.getName().getString(), t);
        }
    }

    /** 新版：走 {@code BankAccount.modify}，由它自己累加并触发同步 */
    private static void modify(ServerPlayer player, BigDecimal delta) {
        try {
            Object account = apiGetAccount.invoke(null, player);
            if (account == null) return;
            accModify.invoke(account, resolveCurrency(), delta);
        } catch (Throwable t) {
            SellBoxMod.LOGGER.error("SDMShop modify failed for {}", player.getName().getString(), t);
        }
    }

    /** 第一次发生小数截断时打一条 warn（只打一次） */
    private static void warnPrecisionOnce(BigDecimal raw, int rounded) {
        if (warnedPrecision) return;
        if (raw.compareTo(BigDecimal.valueOf(rounded)) != 0) {
            warnedPrecision = true;
            SellBoxMod.LOGGER.warn(
                    "SDMShop: balance {} is not an integer, SellBox rounded it to {}. "
                            + "This economy uses decimals; SellBox only supports whole numbers.",
                    raw.toPlainString(), rounded);
        }
    }

    // ════════════════════════════════════════════
    //  排行榜
    // ════════════════════════════════════════════

    public static void sendLeaderboard(ServerPlayer requester, int page) {
        if (!isLoaded()) {
            requester.sendSystemMessage(Component.translatable("message.sellbox.sdm_not_found"));
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
     * <p>新版走 {@code IStoredCurrency.format(BigDecimal)}（与 SDMShop 界面一致），
     * 旧版与兜底用本地千分位格式。
     */
    public static String formatMoney(int amount) {
        if (api == ApiKind.SDM_ECONOMY && currencyFormat != null) {
            Object currency = resolveCurrency();
            if (currency != null) {
                try {
                    Object v = currencyFormat.invoke(currency, BigDecimal.valueOf(amount));
                    if (v instanceof String s && !s.isEmpty()) return s;
                } catch (Throwable ignored) {
                }
            }
        }
        return String.format(Locale.ROOT, "%,d", amount);
    }
}
