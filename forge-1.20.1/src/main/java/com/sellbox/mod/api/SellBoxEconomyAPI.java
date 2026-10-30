package com.sellbox.mod.api;

public class SellBoxEconomyAPI {
    private static volatile IEconomy economy;

    public static IEconomy get() {
        return economy;
    }

    public static void set(IEconomy impl) {
        economy = impl;
    }
}
