package com.sellbox.mod.api;

import net.minecraft.server.level.ServerPlayer;

public interface IEconomy {
    int getBalance(ServerPlayer player);
    boolean hasBalance(ServerPlayer player, int amount);
    void addBalance(ServerPlayer player, int amount);
    boolean deductBalance(ServerPlayer player, int amount);
    boolean tryDeductBalance(ServerPlayer player, int amount);
    void setBalance(ServerPlayer player, int amount);
    String formatMoney(int amount);
}
