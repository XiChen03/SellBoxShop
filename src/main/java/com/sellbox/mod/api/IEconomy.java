package com.sellbox.mod.api;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public interface IEconomy {
    int getBalance(UUID playerId);
    boolean hasBalance(UUID playerId, int amount);
    boolean addBalance(UUID playerId, int amount);
    boolean deductBalance(UUID playerId, int amount);
    boolean setBalance(UUID playerId, int amount);
    String formatMoney(int amount);
    void sendBalanceSync(ServerPlayer player);
    boolean isAvailable();
}
