package com.sellbox.mod.api;

import javax.annotation.Nullable;

public class SellBoxEconomyAPI {
    private static volatile IEconomy economy;

    @Nullable
    public static IEconomy get() { return economy; }

    /** 供 SellBox 内部初始化时调用 */
    public static void set(IEconomy impl) { economy = impl; }
}
