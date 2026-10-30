package com.sellbox.mod.shop;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.config.SellBoxConfig;
import com.sellbox.mod.integration.VSSIntegration;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SellBoxEconomy {
    private static final ConcurrentHashMap<UUID, Integer> balances = new ConcurrentHashMap<>();
    private static Path balanceFile;
    private static volatile boolean dirty = false;

    private static boolean isVssEnabled() {
        return VSSIntegration.isLoaded() && SellBoxConfig.isVssCurrencyEnabled();
    }

    public static void init(Path configDir) {
        balanceFile = configDir.resolve("SellBox").resolve("sellbox_economy.dat");
        load();
    }

    public static void initPerWorld(Path worldDir) {
        balanceFile = worldDir.resolve("config").resolve("SellBox").resolve("sellbox_economy.dat");
        load();
    }

    public static int getBalance(ServerPlayer player) {
        if (isVssEnabled()) {
            return VSSIntegration.getMoney(player);
        }
        return balances.getOrDefault(player.getUUID(), 0);
    }

    public static boolean hasBalance(ServerPlayer player, int amount) {
        return getBalance(player) >= amount;
    }

    public static void addBalance(ServerPlayer player, int amount) {
        if (amount <= 0) return;
        if (isVssEnabled()) {
            VSSIntegration.addMoney(player, amount);
            return;
        }
        UUID uuid = player.getUUID();
        balances.merge(uuid, amount, Integer::sum);
        dirty = true;
    }

    public static boolean deductBalance(ServerPlayer player, int amount) {
        if (amount <= 0) return true;
        if (isVssEnabled()) {
            VSSIntegration.removeMoney(player, amount);
            return true;
        }
        UUID uuid = player.getUUID();
        int current = balances.getOrDefault(uuid, 0);
        if (current < amount) return false;
        if (current == amount) {
            balances.remove(uuid);
        } else {
            balances.put(uuid, current - amount);
        }
        dirty = true;
        return true;
    }

    public static boolean tryDeductBalance(ServerPlayer player, int amount) {
        if (amount <= 0) return true;
        if (!hasBalance(player, amount)) return false;
        return deductBalance(player, amount);
    }

    public static void setBalance(ServerPlayer player, int amount) {
        if (amount < 0) amount = 0;
        if (isVssEnabled()) {
            VSSIntegration.setMoney(player, amount);
            return;
        }
        if (amount == 0) {
            balances.remove(player.getUUID());
        } else {
            balances.put(player.getUUID(), amount);
        }
        dirty = true;
    }

    public static void sendLeaderboard(ServerPlayer requester, int page) {
        if (isVssEnabled()) {
            VSSIntegration.sendLeaderboard(requester, page);
            return;
        }
        // Local fallback leaderboard
        var allPlayers = requester.server.getPlayerList().getPlayers();
        List<Map.Entry<String, Integer>> rankings = new ArrayList<>();
        for (ServerPlayer player : allPlayers) {
            int money = getBalance(player);
            rankings.add(new AbstractMap.SimpleEntry<>(player.getName().getString(), money));
        }
        rankings.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        int pageSize = 8;
        int totalPages = Math.max(1, (int) Math.ceil((double) rankings.size() / pageSize));
        int rawPage = Math.min(page, totalPages);
        if (rawPage < 1) rawPage = 1;
        int currentPage = rawPage;
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
            for (int i = start; i < end; i++) maxNameWidth = Math.max(maxNameWidth, rankings.get(i).getKey().length());
            maxNameWidth = Math.min(maxNameWidth + 2, 20);
            for (int i = start; i < end; i++) {
                var entry = rankings.get(i);
                String sb = " " + String.format("%-4s", (i + 1) + ".") +
                        String.format("%-" + maxNameWidth + "s", entry.getKey()) +
                        " " + entry.getValue();
                ChatFormatting color = (i == 0) ? ChatFormatting.GOLD :
                        (i == 1) ? ChatFormatting.WHITE :
                                (i == 2) ? ChatFormatting.RED : ChatFormatting.GRAY;
                requester.sendSystemMessage(Component.literal(sb).withStyle(color));
            }
        }
        requester.sendSystemMessage(Component.literal(" ====*====*====*====").withStyle(ChatFormatting.DARK_GRAY));
        if (totalPages > 1) {
            MutableComponent nav = Component.literal("");
            if (currentPage > 1) {
                nav = nav.append(Component.literal(" << ").withStyle(ChatFormatting.GREEN)
                        .withStyle(style -> style.withClickEvent(
                                new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sellbox leaderboard " + (currentPage - 1)))));
            }
            nav = nav.append(Component.literal(" " + currentPage + "/" + totalPages + " ").withStyle(ChatFormatting.GRAY));
            if (currentPage < totalPages) {
                nav = nav.append(Component.literal(" >> ").withStyle(ChatFormatting.GREEN)
                        .withStyle(style -> style.withClickEvent(
                                new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sellbox leaderboard " + (currentPage + 1)))));
            }
            requester.sendSystemMessage(nav);
        }
        requester.sendSystemMessage(Component.literal("").withStyle(ChatFormatting.GOLD));
    }

    public static String formatMoney(int amount) {
        if (isVssEnabled()) {
            return VSSIntegration.formatMoney(amount);
        }
        return String.format("%,d", amount);
    }

    // 从 sellbox_economy.dat 加载本地经济数据
    private static void load() {
        balances.clear();
        if (balanceFile == null || !Files.exists(balanceFile)) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(balanceFile)))) {
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                long mostSig = in.readLong();
                long leastSig = in.readLong();
                int balance = in.readInt();
                if (balance > 0) {
                    balances.put(new UUID(mostSig, leastSig), balance);
                }
            }
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("Failed to load economy data", e);
        }
    }

    public static void flushSave() {
        if (dirty) {
            dirty = false;
            save();
        }
    }

    // 保存本地经济数据到 sellbox_economy.dat
    private static void save() {
        if (balanceFile == null) return;
        try {
            Files.createDirectories(balanceFile.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(balanceFile)))) {
                out.writeInt(balances.size());
                for (var entry : balances.entrySet()) {
                    out.writeLong(entry.getKey().getMostSignificantBits());
                    out.writeLong(entry.getKey().getLeastSignificantBits());
                    out.writeInt(entry.getValue());
                }
            }
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("Failed to save economy data", e);
        }
    }
}
