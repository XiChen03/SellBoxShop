package com.sellbox.mod.integration;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.shop.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class SDMShopIntegration {
    private static Class<?> economyModuleClass;
    private static Field economyField;
    private static Method getMethod;
    private static Method setMethod;
    private static Method syncMethod;
    private static boolean classFound = false;

    static {
        String[] classPaths = {
                "net.sixik.sdmshoprework.economy.EconomyManager",
        };
        for (String cp : classPaths) {
            try {
                Class<?> mgrClass = Class.forName(cp);
                economyField = mgrClass.getField("economy");
                economyModuleClass = Class.forName(cp + "$EconomyModule");
                getMethod = economyModuleClass.getMethod("get");
                setMethod = economyModuleClass.getMethod("set");
                syncMethod = economyModuleClass.getMethod("sync");
                classFound = true;
                SellBoxMod.LOGGER.info("SDMShop integration class found from {}", cp);
                break;
            } catch (Exception e) {
                SellBoxMod.LOGGER.debug("SDMShop class {} not found: {}", cp, e.getMessage());
            }
        }
        if (!classFound) {
            SellBoxMod.LOGGER.info("SDMShop mod not found, integration disabled");
        }
    }

    private static boolean isEconomyReady() {
        if (!classFound) return false;
        try {
            return economyField.get(null) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isLoaded() {
        return classFound;
    }

    public static int getMoney(ServerPlayer player) {
        if (!isEconomyReady()) return 0;
        try {
            Object economy = economyField.get(null);
            Object getFunc = getMethod.invoke(economy);
            long result = ((Function<ServerPlayer, Long>) getFunc).apply(player);
            return (int) result;
        } catch (Exception e) {
            return 0;
        }
    }

    public static void addMoney(ServerPlayer player, int amount) {
        if (!isEconomyReady() || amount <= 0) return;
        try {
            int current = getMoney(player);
            setMoney(player, current + amount);
        } catch (Exception e) { }
    }

    public static void removeMoney(ServerPlayer player, int amount) {
        if (!isEconomyReady()) return;
        try {
            int current = getMoney(player);
            setMoney(player, Math.max(0, current - amount));
        } catch (Exception e) { }
    }

    public static void setMoney(ServerPlayer player, int amount) {
        if (!isEconomyReady()) return;
        try {
            Object economy = economyField.get(null);
            Object setFunc = setMethod.invoke(economy);
            ((BiConsumer<ServerPlayer, Long>) setFunc).accept(player, (long) amount);
        } catch (Exception e) { }
    }

    public static void sendLeaderboard(ServerPlayer requester, int page) {
        if (!isLoaded()) {
            requester.sendSystemMessage(Component.translatable("message.sellbox.sdm_not_found"));
            return;
        }

        try {
            List<ServerPlayer> allPlayers = requester.server.getPlayerList().getPlayers();
            List<Map.Entry<String, Integer>> rankings = new ArrayList<>();

            for (ServerPlayer player : allPlayers) {
                int money = getMoney(player);
                rankings.add(new AbstractMap.SimpleEntry<>(player.getName().getString(), money));
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
                    String balance = String.valueOf(entry.getValue());

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

    public static String formatMoney(int amount) {
        return String.format("%,d", amount);
    }

    @SuppressWarnings("unchecked")
    public static List<ShopEntry> getShopEntries() {
        List<ShopEntry> result = new ArrayList<>();
        if (!classFound) return result;

        Class<?> shopEntryClass = null;
        Method getItemMethod = null;
        Method getPriceMethod = null;
        Method getQuantityMethod = null;
        try {
            shopEntryClass = Class.forName("net.sixik.sdmshoprework.shop.ShopEntry");
            getItemMethod = shopEntryClass.getMethod("getItem");
            getPriceMethod = shopEntryClass.getMethod("getPrice");
            getQuantityMethod = shopEntryClass.getMethod("getQuantity");
        } catch (Exception e) {
            SellBoxMod.LOGGER.warn("SDMShop ShopEntry class not found: {}", e.getMessage());
            return result;
        }

        try {
            String[] sourceClasses = {
                "net.sixik.sdmshoprework.SDMShopRework",
                "net.sixik.sdmshoprework.client.SDMShopClient",
                "net.sixik.sdmshoprework.shop.ShopManager",
                "net.sixik.sdmshoprework.shop.client.ShopClient",
                "net.sixik.sdmshoprework.SDMShopClient"
            };

            for (String clsName : sourceClasses) {
                try {
                    Class<?> cls = Class.forName(clsName);
                    for (Field f : cls.getDeclaredFields()) {
                        if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                        f.setAccessible(true);
                        Object val = f.get(null);
                        if (val == null) continue;

                        // Check if this field is directly a List<ShopEntry>
                        if (val instanceof List) {
                            List<?> list = (List<?>) val;
                            if (!list.isEmpty() && shopEntryClass.isInstance(list.get(0))) {
                                for (Object entry : list) {
                                    ItemStack item = (ItemStack) getItemMethod.invoke(entry);
                                    if (item == null || item.isEmpty()) continue;
                                    int price = (int) getPriceMethod.invoke(entry);
                                    int qty = (int) getQuantityMethod.invoke(entry);
                                    ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item.getItem());
                                    result.add(ShopEntry.item(itemId, ShopEntry.TradeType.BUY, price, Math.max(1, qty)));
                                }
                                continue;
                            }
                        }

                        // Otherwise try as tabs/categories
                        List<?> rawTabs = null;
                        if (val instanceof Map) {
                            rawTabs = new ArrayList<>(((Map<?, ?>) val).values());
                        } else if (val instanceof List) {
                            rawTabs = (List<?>) val;
                        } else if (val.getClass().isArray()) {
                            rawTabs = Arrays.asList((Object[]) val);
                        }
                        if (rawTabs == null || rawTabs.isEmpty()) continue;

                        for (Object tab : rawTabs) {
                            List<?> entries = tryExtractEntries(tab, shopEntryClass);
                            if (entries == null || entries.isEmpty()) continue;
                            for (Object entry : entries) {
                                if (!shopEntryClass.isInstance(entry)) continue;
                                ItemStack item = (ItemStack) getItemMethod.invoke(entry);
                                if (item == null || item.isEmpty()) continue;
                                int price = (int) getPriceMethod.invoke(entry);
                                int qty = (int) getQuantityMethod.invoke(entry);
                                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item.getItem());
                                result.add(ShopEntry.item(itemId, ShopEntry.TradeType.BUY, price, Math.max(1, qty)));
                            }
                        }
                    }
                    if (!result.isEmpty()) {
                        SellBoxMod.LOGGER.info("Found SDMShop entries in class: {}", clsName);
                        break;
                    }
                } catch (ClassNotFoundException ignored) {}
            }

            if (result.isEmpty()) {
                SellBoxMod.LOGGER.warn("SDMShop entries not found - no data synced yet or structure mismatch");
            } else {
                SellBoxMod.LOGGER.info("Loaded {} SDMShop entries via reflection", result.size());
            }
        } catch (Exception e) {
            SellBoxMod.LOGGER.warn("Could not read SDMShop entries: {}", e.getMessage());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<?> tryExtractEntries(Object tab, Class<?> entryClass) {
        Class<?> tabClass = tab.getClass();
        String[] entryFieldNames = {"entries", "shopEntries", "ENTRIES", "shopEntries", "entryList", "ITEM_ENTRIES"};

        for (String name : entryFieldNames) {
            try {
                Field f = tabClass.getDeclaredField(name);
                f.setAccessible(true);
                Object val = f.get(tab);
                if (val instanceof List) {
                    List<?> list = (List<?>) val;
                    if (!list.isEmpty() && entryClass.isInstance(list.get(0))) return list;
                }
            } catch (Exception ignored) {}
        }

        String[] getterNames = {"getEntries", "getShopEntries", "entries", "getEntryList"};
        for (String name : getterNames) {
            try {
                Method m = tabClass.getMethod(name);
                Object val = m.invoke(tab);
                if (val instanceof List) {
                    List<?> list = (List<?>) val;
                    if (!list.isEmpty() && entryClass.isInstance(list.get(0))) return list;
                }
            } catch (Exception ignored) {}
        }

        return null;
    }
}