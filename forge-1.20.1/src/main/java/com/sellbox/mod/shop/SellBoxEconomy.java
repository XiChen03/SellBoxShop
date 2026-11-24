package com.sellbox.mod.shop;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.api.IEconomy;
import com.sellbox.mod.api.SellBoxEconomyAPI;
import com.sellbox.mod.config.SellBoxConfig;
import com.sellbox.mod.integration.SDMShopIntegration;
import com.sellbox.mod.integration.VSSIntegration;
import com.sellbox.mod.integration.VaultIntegration;
import com.sellbox.mod.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SellBoxEconomy implements IEconomy {
    private static final ConcurrentHashMap<UUID, Integer> balances = new ConcurrentHashMap<>();
    private static Path balanceFile;
    private static volatile boolean dirty = false;

    /** SDMShop 经济（6.x sdmshoprework / 7.x+ SDM Economy） */
    private static boolean isSdmEnabled() {
        return SDMShopIntegration.isLoaded() && SellBoxConfig.isSdmCurrencyEnabled();
    }

    /** ViScriptShop 经济（1.20.1 / 1.21.1 皆可，API 双签名自动识别） */
    private static boolean isVssEnabled() {
        return VSSIntegration.isLoaded() && SellBoxConfig.isVssCurrencyEnabled();
    }

    /** Vault 经济：只有在混合端探测到 Economy 提供者、且配置开关打开时才接管 */
    private static boolean isVaultEnabled() {
        return SellBoxConfig.isVaultCurrencyEnabled() && VaultIntegration.isEconomyReady();
    }

    public static void init(Path configDir, Path worldDir) {
        if (worldDir != null) {
            // 按存档保存模式：保存在世界存档/config/SellBox/
            balanceFile = worldDir.resolve("config").resolve("SellBox").resolve("sellbox_economy.dat");
        } else {
            // 回退到配置目录
            balanceFile = configDir.resolve("SellBox").resolve("sellbox_economy.dat");
        }
        load();
        SellBoxEconomyAPI.set(new SellBoxEconomy());
    }

    @Override
    public int getBalance(UUID playerId) {
        if (isVaultEnabled() || isVssEnabled() || isSdmEnabled()) return 0;
        return balances.getOrDefault(playerId, 0);
    }

    public static int getBalance(ServerPlayer player) {
        if (isVaultEnabled()) {
            return VaultIntegration.getBalance(player);
        }
        if (isVssEnabled()) {
            return VSSIntegration.getMoney(player);
        }
        if (isSdmEnabled()) {
            return SDMShopIntegration.getMoney(player);
        }
        return balances.getOrDefault(player.getUUID(), 0);
    }

    @Override
    public boolean hasBalance(UUID playerId, int amount) {
        return getBalance(playerId) >= amount;
    }

    public static boolean hasBalance(ServerPlayer player, int amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean addBalance(UUID playerId, int amount) {
        if (amount <= 0) return true;
        if (isVaultEnabled() || isVssEnabled() || isSdmEnabled()) return false;
        balances.merge(playerId, amount, Integer::sum);
        dirty = true;
        return true;
    }

    public static void addBalance(ServerPlayer player, int amount) {
        if (amount <= 0) return;
        if (isVaultEnabled()) {
            VaultIntegration.addBalance(player, amount);
            ModNetwork.sendBalanceToPlayer(player);
            return;
        }
        if (isVssEnabled()) {
            VSSIntegration.addMoney(player, amount);
            ModNetwork.sendBalanceToPlayer(player);
            return;
        }
        if (isSdmEnabled()) {
            SDMShopIntegration.addMoney(player, amount);
            ModNetwork.sendBalanceToPlayer(player);
            return;
        }
        UUID uuid = player.getUUID();
        balances.merge(uuid, amount, Integer::sum);
        dirty = true;
        ModNetwork.sendBalanceToPlayer(player);
    }

    @Override
    public boolean deductBalance(UUID playerId, int amount) {
        if (amount <= 0) return true;
        if (isVaultEnabled() || isVssEnabled() || isSdmEnabled()) return false;
        int current = balances.getOrDefault(playerId, 0);
        if (current < amount) return false;
        if (current == amount) {
            balances.remove(playerId);
        } else {
            balances.put(playerId, current - amount);
        }
        dirty = true;
        return true;
    }

    public static boolean deductBalance(ServerPlayer player, int amount) {
        if (amount <= 0) return true;
        if (isVaultEnabled()) {
            boolean ok = VaultIntegration.deductBalance(player, amount);
            if (ok) ModNetwork.sendBalanceToPlayer(player);
            return ok;
        }
        if (isVssEnabled()) {
            VSSIntegration.removeMoney(player, amount);
            ModNetwork.sendBalanceToPlayer(player);
            return true;
        }
        if (isSdmEnabled()) {
            SDMShopIntegration.removeMoney(player, amount);
            ModNetwork.sendBalanceToPlayer(player);
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
        ModNetwork.sendBalanceToPlayer(player);
        return true;
    }

    public static boolean tryDeductBalance(ServerPlayer player, int amount) {
        if (amount <= 0) return true;
        if (!hasBalance(player, amount)) return false;
        return deductBalance(player, amount);
    }

    @Override
    public boolean setBalance(UUID playerId, int amount) {
        if (amount < 0) amount = 0;
        if (isVaultEnabled() || isVssEnabled() || isSdmEnabled()) return false;
        if (amount == 0) {
            balances.remove(playerId);
        } else {
            balances.put(playerId, amount);
        }
        dirty = true;
        return true;
    }

    public static void setBalance(ServerPlayer player, int amount) {
        if (amount < 0) amount = 0;
        if (isVaultEnabled()) {
            VaultIntegration.setBalance(player, amount);
            ModNetwork.sendBalanceToPlayer(player);
            return;
        }
        if (isVssEnabled()) {
            VSSIntegration.setMoney(player, amount);
            ModNetwork.sendBalanceToPlayer(player);
            return;
        }
        if (isSdmEnabled()) {
            SDMShopIntegration.setMoney(player, amount);
            ModNetwork.sendBalanceToPlayer(player);
            return;
        }
        if (amount == 0) {
            balances.remove(player.getUUID());
        } else {
            balances.put(player.getUUID(), amount);
        }
        dirty = true;
        ModNetwork.sendBalanceToPlayer(player);
    }

    @Override
    public void sendBalanceSync(ServerPlayer player) {
        ModNetwork.sendBalanceToPlayer(player);
    }

    @Override
    public String formatMoney(int amount) {
        return formatMoneyStatic(amount);
    }

    public static String formatMoneyStatic(int amount) {
        if (isVaultEnabled()) {
            return VaultIntegration.formatMoney(amount);
        }
        if (isVssEnabled()) {
            return VSSIntegration.formatMoney(amount);
        }
        if (isSdmEnabled()) {
            return SDMShopIntegration.formatMoney(amount);
        }
        return String.format("%,d", amount);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    public static void sendLeaderboard(ServerPlayer requester, int page) {
        if (isVssEnabled()) {
            VSSIntegration.sendLeaderboard(requester, page);
            return;
        }
        if (isSdmEnabled()) {
            SDMShopIntegration.sendLeaderboard(requester, page);
            return;
        }
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
