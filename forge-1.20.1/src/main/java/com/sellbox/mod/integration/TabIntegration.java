package com.sellbox.mod.integration;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.shop.SellBoxEconomy;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Function;

/**
 * NEZNAMY TAB 桥接：把 %sellboxmoney% / %sellbox_money%（玩家余额）注册进 TAB 的占位符系统。
 * TAB 不会自动识别 SellBoxPlaceholderEvent，必须经此注册。
 */
public class TabIntegration {
    private static boolean tabLoaded = false;
    private static boolean registered = false;
    private static volatile MinecraftServer server;

    private static Method tabApiGetInstance;
    private static Method tabApiGetPlaceholderManager;
    private static Method registerPlayerPlaceholder;
    private static Method tabPlayerGetUniqueId;

    static {
        try {
            Class<?> tabApiClass = Class.forName("me.neznamy.tab.api.TabAPI");
            Class<?> placeholderManagerClass = Class.forName("me.neznamy.tab.api.placeholder.PlaceholderManager");
            Class<?> tabPlayerClass = Class.forName("me.neznamy.tab.api.TabPlayer");
            tabApiGetInstance = tabApiClass.getMethod("getInstance");
            tabApiGetPlaceholderManager = tabApiClass.getMethod("getPlaceholderManager");
            registerPlayerPlaceholder = placeholderManagerClass.getMethod(
                    "registerPlayerPlaceholder", String.class, int.class, Function.class);
            tabPlayerGetUniqueId = tabPlayerClass.getMethod("getUniqueId");
            tabLoaded = true;
            SellBoxMod.LOGGER.info("TAB integration loaded successfully");
        } catch (Throwable e) {
            SellBoxMod.LOGGER.info("TAB mod not found, integration disabled");
            tabLoaded = false;
        }
    }

    public static boolean isLoaded() {
        return tabLoaded;
    }

    public static void onServerStarted(MinecraftServer server) {
        TabIntegration.server = server;
        registerPlaceholders();
    }

    public static void onServerStopped() {
        server = null;
    }

    private static void registerPlaceholders() {
        if (!tabLoaded || registered) return;
        try {
            Object tabApi = tabApiGetInstance.invoke(null);
            if (tabApi == null) {
                SellBoxMod.LOGGER.warn("TAB API not initialized yet, %sellboxmoney% not registered");
                return;
            }
            Object placeholderManager = tabApiGetPlaceholderManager.invoke(tabApi);
            Function<Object, Object> balanceFunction = player -> {
                try {
                    UUID uuid = (UUID) tabPlayerGetUniqueId.invoke(player);
                    MinecraftServer s = server;
                    if (s == null) return 0;
                    ServerPlayer sp = s.getPlayerList().getPlayer(uuid);
                    if (sp == null) return 0;
                    return SellBoxEconomy.getBalance(sp);
                } catch (Exception e) {
                    return 0;
                }
            };
            registerPlayerPlaceholder.invoke(placeholderManager, "%sellboxmoney%", 1000, balanceFunction);
            registerPlayerPlaceholder.invoke(placeholderManager, "%sellbox_money%", 1000, balanceFunction);
            registered = true;
            SellBoxMod.LOGGER.info("Registered %sellboxmoney% placeholder with TAB");
        } catch (Exception e) {
            SellBoxMod.LOGGER.error("Failed to register TAB placeholders", e);
        }
    }
}
