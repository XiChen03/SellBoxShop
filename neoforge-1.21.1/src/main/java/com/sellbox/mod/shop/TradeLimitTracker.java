package com.sellbox.mod.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sellbox.mod.SellBoxMod;
import net.minecraft.world.entity.player.Player;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 交易限量计数器（仅服务端）。
 *
 * <p>按「交易次数」统计，和 {@code TradePayload.executeTrade} 里的 quantity 同一口径：
 * 交易确认界面里填 10 就记 10 次，不管这条商品一次给几个物品。
 *
 * <p>两种模式：
 * <ul>
 *   <li>全体（limitPerPlayer = false）：一个条目一个计数器，所有玩家共用</li>
 *   <li>单人（limitPerPlayer = true）：条目 + 玩家 UUID 各一个计数器</li>
 * </ul>
 *
 * <p>条目 key 由「分类 + 物品 + 买卖方向 + 单价 + 条目类型」拼成。改价或改条目类型会被视为新条目、
 * 计数归零 —— 这是刻意为之，调价通常意味着重开一批货。同一分类里若存在完全相同的两条商品
 * （用「复制」造出来的），它们会共用一个额度。
 *
 * <p>存档位置：{@code <世界>/config/SellBox/sellbox_trade_limits.json}
 */
public class TradeLimitTracker {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** 分隔符用控制字符，避免和用户自拟的分类名/物品 ID 冲突 */
    private static final char SEP = '\u0001';

    private static final Map<String, Integer> GLOBAL_USED = new ConcurrentHashMap<>();
    private static final Map<String, Integer> PLAYER_USED = new ConcurrentHashMap<>();

    private static Path dataPath;
    private static boolean dirty = false;

    private TradeLimitTracker() {}

    /** 条目计数器 key */
    public static String keyOf(String category, ShopEntry entry) {
        return category + SEP + entry.itemId() + SEP + entry.tradeType() + SEP + entry.price() + SEP + entry.entryType();
    }

    /** 该条目对指定玩家还剩多少次可交易；未设限量时返回 {@link Integer#MAX_VALUE} */
    public static int remaining(ShopEntry entry, String category, UUID playerId) {
        if (entry == null || !entry.hasTradeLimit()) return Integer.MAX_VALUE;
        String key = keyOf(category, entry);
        int used = entry.limitPerPlayer()
                ? PLAYER_USED.getOrDefault(key + SEP + playerId, 0)
                : GLOBAL_USED.getOrDefault(key, 0);
        return Math.max(0, entry.tradeLimit() - used);
    }

    /** 已交易次数 */
    public static int used(ShopEntry entry, String category, UUID playerId) {
        if (entry == null) return 0;
        String key = keyOf(category, entry);
        return entry.limitPerPlayer()
                ? PLAYER_USED.getOrDefault(key + SEP + playerId, 0)
                : GLOBAL_USED.getOrDefault(key, 0);
    }

    /** 记一笔已成交的交易 */
    public static void record(ShopEntry entry, String category, Player player, int times) {
        if (entry == null || !entry.hasTradeLimit() || times <= 0) return;
        String key = keyOf(category, entry);
        String mapKey = entry.limitPerPlayer() ? key + SEP + player.getUUID() : key;
        (entry.limitPerPlayer() ? PLAYER_USED : GLOBAL_USED).merge(mapKey, times, Integer::sum);
        dirty = true;
    }

    /** 额度是否已被用尽（客户端据此把提示转红、禁用点击） */
    public static boolean isExhausted(ShopEntry entry, String category, UUID playerId) {
        if (entry == null || !entry.hasTradeLimit()) return false;
        return used(entry, category, playerId) >= entry.tradeLimit();
    }

    /**
     * 打包一批条目的已用次数，随商店数据一起下发给客户端。
     * 单人限量的已用次数因人而异，所以必须按接收者分别计算。
     *
     * <p>有限量的条目一律写入（包括 0），这样客户端能用这份数据整体替换该分类的旧计数。
     */
    public static Map<String, Integer> snapshotUsed(java.util.List<ShopEntry> entries, String category, UUID playerId) {
        Map<String, Integer> out = new ConcurrentHashMap<>();
        if (entries == null) return out;
        for (ShopEntry e : entries) {
            if (e == null || !e.hasTradeLimit()) continue;
            out.put(keyOf(category, e), used(e, category, playerId));
        }
        return out;
    }

    /** 某分类所有条目 key 的前缀，用于客户端整体替换该分类的计数 */
    public static String keyPrefix(String category) {
        return category + SEP;
    }

    /** 改限量时清掉历史计数，让新额度立刻生效 */
    public static void reset(ShopEntry entry, String category) {
        String key = keyOf(category, entry);
        String prefix = key + SEP;
        GLOBAL_USED.remove(key);
        PLAYER_USED.keySet().removeIf(k -> k.startsWith(prefix));
        dirty = true;
    }

    public static void initPerWorld(Path worldDir) {
        dataPath = worldDir.resolve("config").resolve("SellBox").resolve("sellbox_trade_limits.json");
        load();
    }

    public static void load() {
        GLOBAL_USED.clear();
        PLAYER_USED.clear();
        dirty = false;
        if (dataPath == null || !Files.exists(dataPath)) return;
        try (BufferedReader reader = Files.newBufferedReader(dataPath, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || !root.isJsonObject()) return;
            JsonObject obj = root.getAsJsonObject();
            readInto(obj.get("global"), GLOBAL_USED);
            readInto(obj.get("player"), PLAYER_USED);
        } catch (IOException | RuntimeException e) {
            SellBoxMod.LOGGER.error("Failed to load trade limit counters", e);
        }
    }

    private static void readInto(JsonElement el, Map<String, Integer> into) {
        if (el == null || !el.isJsonObject()) return;
        for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
            if (e.getValue().isJsonPrimitive()) {
                try {
                    into.put(e.getKey(), e.getValue().getAsInt());
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    public static void flushSave() {
        if (dataPath == null || !dirty) return;
        dirty = false;
        try {
            JsonObject root = new JsonObject();
            root.add("global", writeMap(GLOBAL_USED));
            root.add("player", writeMap(PLAYER_USED));
            Files.createDirectories(dataPath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(dataPath, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("Failed to save trade limit counters", e);
        }
    }

    private static JsonObject writeMap(Map<String, Integer> map) {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            obj.addProperty(e.getKey(), e.getValue());
        }
        return obj;
    }
}