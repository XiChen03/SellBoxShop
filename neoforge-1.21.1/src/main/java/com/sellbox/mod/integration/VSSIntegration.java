package com.sellbox.mod.integration;

import com.sellbox.mod.SellBoxMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.*;

public class VSSIntegration {
    private static boolean vssLoaded = false;
    private static Class<?> serverUtilClass;
    private static Method getMoneyMethod;
    private static Method addMoneyMethod;
    private static Method removeMoneyMethod;
    private static Method setMoneyMethod;

    static {
        try {
            serverUtilClass = Class.forName("com.viscriptshop.util.ViScriptShopServerUtil");
            getMoneyMethod = serverUtilClass.getMethod("getMoney", ServerPlayer.class);
            addMoneyMethod = serverUtilClass.getMethod("addMoney", ServerPlayer.class, int.class);
            removeMoneyMethod = serverUtilClass.getMethod("removeMoney", ServerPlayer.class, int.class);
            setMoneyMethod = serverUtilClass.getMethod("setMoney", ServerPlayer.class, int.class);
            vssLoaded = true;
            SellBoxMod.LOGGER.info("VSS integration loaded successfully");
        } catch (Exception e) {
            SellBoxMod.LOGGER.info("VSS mod not found, integration disabled");
            vssLoaded = false;
        }
    }

    public static boolean isLoaded() {
        return vssLoaded;
    }

    public static int getMoney(ServerPlayer player) {
        if (!vssLoaded) return 0;
        try {
            return (int) getMoneyMethod.invoke(null, player);
        } catch (Exception e) {
            SellBoxMod.LOGGER.error("Failed to get money", e);
            return 0;
        }
    }

    public static int addMoney(ServerPlayer player, int amount) {
        if (!vssLoaded || amount <= 0) return 0;
        try {
            Object ret = addMoneyMethod.invoke(null, player, amount);
            int result = (ret instanceof Integer i) ? i : 0;
            SellBoxMod.LOGGER.info("Added {} VSS money to player {}", amount, player.getName().getString());
            return result;
        } catch (Exception e) {
            SellBoxMod.LOGGER.error("Failed to add money", e);
            return 0;
        }
    }

    public static void removeMoney(ServerPlayer player, int amount) {
        if (!vssLoaded) return;
        try {
            removeMoneyMethod.invoke(null, player, amount);
        } catch (Exception e) {
            SellBoxMod.LOGGER.error("Failed to remove money", e);
        }
    }

    public static void setMoney(ServerPlayer player, int amount) {
        if (!vssLoaded) return;
        try {
            setMoneyMethod.invoke(null, player, amount);
        } catch (Exception e) {
            SellBoxMod.LOGGER.error("Failed to set money", e);
        }
    }

    public static void sendLeaderboard(ServerPlayer requester, int page) {
        if (!vssLoaded) {
            requester.sendSystemMessage(Component.translatable("message.sellbox.vss_not_found"));
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
}
