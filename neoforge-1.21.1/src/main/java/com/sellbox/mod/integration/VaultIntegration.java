package com.sellbox.mod.integration;

import com.sellbox.mod.SellBoxMod;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;

/**
 * Vault 经济集成（纯反射，无编译期依赖）。
 * <p>
 * <b>只可能用在于混合端</b>（Arclight / Mohist / Cardboard 这类同时加载 Bukkit 插件与模组的服务端）。
 * 纯 Forge / NeoForge 服务端上没有 Bukkit，本类初始化会直接失败并静默禁用。
 * <p>
 * 探测链路：
 * <pre>
 * Bukkit.getServer()
 *   → getServicesManager()
 *   → getRegistration(net.milkbowl.vault.economy.Economy.class)
 *   → getProvider()  →  Economy
 * </pre>
 * Vault 侧的 {@code Economy} 接口主要方法（用反射调用，签名按 Vault 1.7.x）：
 * <ul>
 *   <li>{@code double getBalance(OfflinePlayer)} / {@code getBalance(String)} / {@code getBalance(String, String)}</li>
 *   <li>{@code EconomyResponse withdrawPlayer(OfflinePlayer, double)}</li>
 *   <li>{@code EconomyResponse depositPlayer(OfflinePlayer, double)}</li>
 *   <li>{@code String format(double)} / {@code String getName()}</li>
 * </ul>
 * {@code EconomyResponse} 里用 {@code transactionSuccess()} 或 {@code errorMessage == null} 判定成功。
 * <p>
 * <b>精度说明</b>：Vault 是 {@code double}，SellBox 全链路是 {@code int}。
 * 本类在边界处做四舍五入（{@link Math#round}），所以小数金额会被截到整数。
 * 若服主需要小数，应改用「以分为单位」的整数记账，那是另一套改动。
 */
public class VaultIntegration {
    private static volatile boolean loaded = false;
    private static volatile boolean warnedPrecision = false;

    // Bukkit 侧
    private static Method getServerMethod;          // Bukkit.getServer()
    private static Object serverInstance;           // 缓存 Server 实例
    private static Method getServicesManagerMethod; // Server#getServicesManager()
    private static Method getRegistrationMethod;    // ServicesManager#getRegistration(Class)

    // Economy 提供者
    private static Class<?> economyClass;           // net.milkbowl.vault.economy.Economy
    private static Object economy;                  // 提供者实例
    private static String providerName = "(unknown)";

    // Economy 方法
    private static Method getBalanceOfflineMethod;  // double getBalance(OfflinePlayer)
    private static Method getBalanceNameMethod;     // double getBalance(String)
    private static Method withdrawOfflineMethod;    // EconomyResponse withdrawPlayer(OfflinePlayer, double)
    private static Method depositOfflineMethod;     // EconomyResponse depositPlayer(OfflinePlayer, double)
    private static Method hasOfflineMethod;         // boolean has(OfflinePlayer, double)
    private static Method formatMethod;             // String format(double)
    private static Method getNameMethod;            // String getName()

    // Bukkit 侧
    private static Method getPlayerMethod;          // Bukkit.getPlayer(UUID)
    private static Class<?> offlinePlayerClass;     // org.bukkit.OfflinePlayer

    static {
        probe();
    }

    /**
     * 手动重新探测。
     * <p>混合端上插件加载顺序不固定，Vault 的 Economy 提供者可能比本模组晚注册，
     * 所以除了 {@code <clinit>} 里探一次，服务端启动完成后还会再调一次。
     *
     * @return 探测后是否可用
     */
    public static synchronized boolean probe() {
        if (loaded) return true;
        try {
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            getServerMethod = bukkitClass.getMethod("getServer");

            getPlayerMethod = null;
            for (Method m : bukkitClass.getMethods()) {
                if (m.getName().equals("getPlayer") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == UUID.class) {
                    getPlayerMethod = m;
                    break;
                }
            }

            Class<?> serverClass = Class.forName("org.bukkit.Server");
            getServicesManagerMethod = serverClass.getMethod("getServicesManager");
            Class<?> servicesManagerClass = Class.forName("org.bukkit.plugin.ServicesManager");
            getRegistrationMethod = servicesManagerClass.getMethod("getRegistration", Class.class);

            economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            offlinePlayerClass = Class.forName("org.bukkit.OfflinePlayer");

            // economy 各方法按需探测，缺哪个少哪个功能
            getBalanceOfflineMethod = tryMethod(economyClass, "getBalance", offlinePlayerClass);
            if (getBalanceOfflineMethod == null) {
                getBalanceNameMethod = tryMethod(economyClass, "getBalance", String.class);
            }
            withdrawOfflineMethod = tryMethod(economyClass, "withdrawPlayer", offlinePlayerClass, double.class);
            depositOfflineMethod = tryMethod(economyClass, "depositPlayer", offlinePlayerClass, double.class);
            hasOfflineMethod = tryMethod(economyClass, "has", offlinePlayerClass, double.class);
            formatMethod = tryMethod(economyClass, "format", double.class);
            getNameMethod = tryMethod(economyClass, "getName");

            if (getBalanceOfflineMethod == null && getBalanceNameMethod == null) {
                SellBoxMod.LOGGER.warn("Vault: Economy class found but no getBalance overload; disabled");
                return false;
            }

            loaded = true;
            SellBoxMod.LOGGER.info("Vault integration initialized (provider will be resolved on first use)");
            return true;
        } catch (Throwable t) {
            // 纯 Forge/NeoForge 服务端走到这里是正常的，不做 warning
            SellBoxMod.LOGGER.debug("Vault/Bukkit not present: {}", t.toString());
            loaded = false;
            return false;
        }
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

    /** 是否探测到了 Vault（不代表 Economy 提供者已就绪） */
    public static boolean isLoaded() {
        return loaded;
    }

    /** Vault 已装载且 Economy 提供者可用 */
    public static boolean isEconomyReady() {
        return loaded && resolveEconomy() != null;
    }

    /** Economy 提供者的名字，用于日志/指令显示 */
    public static String getProviderName() {
        if (!isEconomyReady()) return "(none)";
        return providerName;
    }

    /**
     * 延迟解析 Economy 提供者。
     * <p>不放在 {@code <clinit>} 里是因为插件可能在模组之后才注册服务。
     */
    private static Object resolveEconomy() {
        if (economy != null) return economy;
        if (!loaded) return null;
        try {
            if (serverInstance == null) {
                serverInstance = getServerMethod.invoke(null);
            }
            if (serverInstance == null) return null;
            Object servicesManager = getServicesManagerMethod.invoke(serverInstance);
            if (servicesManager == null) return null;
            Object registration = getRegistrationMethod.invoke(servicesManager, economyClass);
            if (registration == null) return null;

            Method getProvider = registration.getClass().getMethod("getProvider");
            Object provider = getProvider.invoke(registration);
            if (provider == null) return null;

            economy = provider;
            if (getNameMethod != null) {
                try {
                    Object n = getNameMethod.invoke(economy);
                    if (n instanceof String s && !s.isEmpty()) providerName = s;
                } catch (Throwable ignored) {
                }
            }
            SellBoxMod.LOGGER.info("Vault economy provider resolved: {}", providerName);
            return economy;
        } catch (Throwable t) {
            return null;
        }
    }

    // ════════════════════════════════════════════
    //  Bukkit 对象转换
    // ════════════════════════════════════════════

    /**
     * 把服务端玩家转成 Bukkit 的 {@code OfflinePlayer}。
     * <p>用 UUID 走 {@code Bukkit.getPlayer(UUID)}；玩家在线时它返回 {@code Player}
     * （本身就是 {@code OfflinePlayer} 的子接口），离线时返回 null —— 此时
     * 会退回 {@code Bukkit.getOfflinePlayer(UUID)}。
     */
    private static Object toOfflinePlayer(ServerPlayer player) {
        try {
            if (getPlayerMethod != null) {
                Object p = getPlayerMethod.invoke(null, player.getUUID());
                if (p != null) return p;
            }
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            for (Method m : bukkitClass.getMethods()) {
                if (m.getName().equals("getOfflinePlayer") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == UUID.class) {
                    return m.invoke(null, player.getUUID());
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** {@code EconomyResponse} 是否成功：优先 transactionSuccess()，退化到 errorMessage == null */
    private static boolean isSuccess(Object response) {
        if (response == null) return false;
        try {
            Method ts = response.getClass().getMethod("transactionSuccess");
            Object v = ts.invoke(response);
            if (v instanceof Boolean b) return b;
        } catch (Throwable ignored) {
        }
        try {
            Method em = response.getClass().getMethod("errorMessage");
            Object v = em.invoke(response);
            return v == null;
        } catch (Throwable ignored) {
        }
        return false;
    }

    // ════════════════════════════════════════════
    //  对外的余额操作（金额单位：整数）
    // ════════════════════════════════════════════

    public static int getBalance(ServerPlayer player) {
        if (!isEconomyReady()) return 0;
        Object op = toOfflinePlayer(player);
        if (op == null) return 0;
        try {
            double v;
            if (getBalanceOfflineMethod != null) {
                v = ((Number) getBalanceOfflineMethod.invoke(economy, op)).doubleValue();
            } else {
                v = ((Number) getBalanceNameMethod.invoke(economy, player.getName().getString())).doubleValue();
            }
            int rounded = (int) Math.round(v);
            warnPrecisionOnce(v, rounded);
            return rounded;
        } catch (Throwable t) {
            SellBoxMod.LOGGER.error("Vault getBalance failed for {}", player.getName().getString(), t);
            return 0;
        }
    }

    public static boolean addBalance(ServerPlayer player, int amount) {
        if (!isEconomyReady() || amount <= 0 || depositOfflineMethod == null) return false;
        Object op = toOfflinePlayer(player);
        if (op == null) return false;
        try {
            Object resp = depositOfflineMethod.invoke(economy, op, (double) amount);
            return isSuccess(resp);
        } catch (Throwable t) {
            SellBoxMod.LOGGER.error("Vault depositPlayer failed for {}", player.getName().getString(), t);
            return false;
        }
    }

    public static boolean deductBalance(ServerPlayer player, int amount) {
        if (!isEconomyReady() || amount <= 0 || withdrawOfflineMethod == null) return false;
        Object op = toOfflinePlayer(player);
        if (op == null) return false;
        try {
            Object resp = withdrawOfflineMethod.invoke(economy, op, (double) amount);
            return isSuccess(resp);
        } catch (Throwable t) {
            SellBoxMod.LOGGER.error("Vault withdrawPlayer failed for {}", player.getName().getString(), t);
            return false;
        }
    }

    public static boolean hasBalance(ServerPlayer player, int amount) {
        if (!isEconomyReady()) return false;
        Object op = toOfflinePlayer(player);
        if (op == null) return false;
        if (hasOfflineMethod != null) {
            try {
                Object v = hasOfflineMethod.invoke(economy, op, (double) amount);
                if (v instanceof Boolean b) return b;
            } catch (Throwable ignored) {
            }
        }
        // 兜底：自己比较
        return getBalance(player) >= amount;
    }

    /**
     * 设置余额：Vault 没有 setBalance，用「差额存/取」实现。
     *
     * @return 是否成功
     */
    public static boolean setBalance(ServerPlayer player, int amount) {
        if (!isEconomyReady()) return false;
        int current = getBalance(player);
        if (current == amount) return true;
        if (amount > current) return addBalance(player, amount - current);
        return deductBalance(player, current - amount);
    }

    /** 格式化金额（走 Vault 的 format，拿不到就自己格式化） */
    public static String formatMoney(int amount) {
        if (isEconomyReady() && formatMethod != null) {
            try {
                Object v = formatMethod.invoke(economy, (double) amount);
                if (v instanceof String s && !s.isEmpty()) return s;
            } catch (Throwable ignored) {
            }
        }
        return String.format(Locale.ROOT, "%,d", amount);
    }

    /**
     * 触发一次精度提醒（只在第一次真正发生截断时打日志）。
     *
     * @param rawAmount Vault 返回的原始 double
     * @param rounded   截断后的 int
     */
    private static void warnPrecisionOnce(double rawAmount, int rounded) {
        if (warnedPrecision) return;
        if (Math.abs(rawAmount - rounded) > 1.0e-6) {
            warnedPrecision = true;
            SellBoxMod.LOGGER.warn(
                    "Vault: balance {} is not an integer, SellBox rounded it to {}. "
                            + "Money in this economy has decimals; SellBox only supports whole numbers.",
                    rawAmount, rounded);
        }
    }
}
