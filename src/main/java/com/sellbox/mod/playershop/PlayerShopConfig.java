package com.sellbox.mod.playershop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.shop.ShopEntry;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class PlayerShopConfig {
    public record PlayerShopInfo(String shopName, List<ShopEntry> items) {}
    public record ShopStats(long totalEarnings, long totalItemsSold) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path configPath;
    private static Path statsPath;
    private static final Map<UUID, PlayerShopInfo> playerShops = new LinkedHashMap<>();
    private static final Map<UUID, ShopStats> shopStats = new LinkedHashMap<>();
    private static volatile boolean dirtyShops = false;
    private static volatile boolean dirtyStats = false;

    public static void init(Path configDir) {
        configPath = configDir.resolve("SellBox").resolve("sellbox_playershops.json");
        statsPath = configDir.resolve("SellBox").resolve("sellbox_shopstats.json");
        load();
        loadStats();
    }

    public static ShopStats getStats(UUID uuid) {
        return shopStats.getOrDefault(uuid, new ShopStats(0, 0));
    }

    public static Map<UUID, ShopStats> getAllStats() {
        return Collections.unmodifiableMap(shopStats);
    }

    public static void addEarnings(UUID uuid, int earnings, int itemsSold) {
        shopStats.merge(uuid, new ShopStats(earnings, itemsSold),
                (old, val) -> new ShopStats(old.totalEarnings() + val.totalEarnings(), old.totalItemsSold() + val.totalItemsSold()));
        dirtyStats = true;
    }

    public static void loadStats() {
        shopStats.clear();
        if (statsPath == null || !Files.exists(statsPath)) return;
        try (BufferedReader reader = Files.newBufferedReader(statsPath, StandardCharsets.UTF_8)) {
            Map<String, Map<String, Number>> raw = GSON.fromJson(
                    reader, new TypeToken<Map<String, Map<String, Number>>>() {}.getType());
            if (raw == null) return;
            for (var e : raw.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(e.getKey());
                    long earnings = e.getValue().getOrDefault("earnings", 0).longValue();
                    long items = e.getValue().getOrDefault("items", 0).longValue();
                    shopStats.put(uuid, new ShopStats(earnings, items));
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("Failed to load shop stats", e);
        }
    }

    private static void saveStats() {
        if (statsPath == null) return;
        try {
            Map<String, Map<String, Number>> data = new LinkedHashMap<>();
            for (var e : shopStats.entrySet()) {
                Map<String, Number> s = new LinkedHashMap<>();
                s.put("earnings", e.getValue().totalEarnings());
                s.put("items", e.getValue().totalItemsSold());
                data.put(e.getKey().toString(), s);
            }
            Files.createDirectories(statsPath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(statsPath, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("Failed to save shop stats", e);
        }
    }

    public static void load() {
        playerShops.clear();
        if (configPath == null || !Files.exists(configPath)) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            Map<String, Map<String, Object>> rawData = GSON.fromJson(
                    reader, new TypeToken<Map<String, Map<String, Object>>>() {}.getType());
            if (rawData == null) return;
            for (var rawEntry : rawData.entrySet()) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(rawEntry.getKey());
                } catch (IllegalArgumentException e) {
                    SellBoxMod.LOGGER.warn("Invalid UUID in player shop config: {}", rawEntry.getKey());
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> shopData = rawEntry.getValue();
                String shopName = shopData.containsKey("shopName") ? (String) shopData.get("shopName") : "";
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> itemsRaw = shopData.containsKey("items")
                        ? (List<Map<String, Object>>) shopData.get("items")
                        : List.of();
                List<ShopEntry> items = new ArrayList<>();
                for (Map<String, Object> item : itemsRaw) {
                    var itemId = net.minecraft.resources.ResourceLocation.tryParse((String) item.get("type"));
                    if (itemId == null) continue;
                    var tradeType = ShopEntry.TradeType.valueOf(
                            ((String) item.getOrDefault("trade_type", "sell")).toUpperCase());
                    int price = ((Number) item.get("price")).intValue();
                    String entryType = item.containsKey("entry_type") ? (String) item.get("entry_type") : "item";
                    String extraData = item.containsKey("extra_data") ? (String) item.get("extra_data") : "";
                    boolean requireOp = item.containsKey("require_op") && Boolean.parseBoolean(item.get("require_op").toString());
                    int qty = item.containsKey("quantity") ? ((Number) item.get("quantity")).intValue() : 1;
                    String cName = item.containsKey("custom_name") ? (String) item.get("custom_name") : "";
                    net.minecraft.resources.ResourceLocation barterItemId = null;
                    if (item.containsKey("barter_item_id") && item.get("barter_item_id") != null) {
                        barterItemId = net.minecraft.resources.ResourceLocation.tryParse(item.get("barter_item_id").toString());
                    }
                    int barterItemQty = item.containsKey("barter_item_qty") ? ((Number) item.get("barter_item_qty")).intValue() : 1;
                    String realExtra = !"barter".equals(entryType) ? extraData : "";
                    Long questId = item.containsKey("required_quest_id") ? ((Number) item.get("required_quest_id")).longValue() : null;
                    String questName = item.containsKey("required_quest_name") ? (String) item.get("required_quest_name") : null;
                    net.minecraft.nbt.CompoundTag nbt = null;
                    if (item.containsKey("nbt") && item.get("nbt") != null) {
                        try { nbt = net.minecraft.nbt.TagParser.parseTag(item.get("nbt").toString()); } catch (Exception ignored) { }
                    }
                    boolean strictMatch = item.containsKey("strict_match") && Boolean.parseBoolean(item.get("strict_match").toString());
                    net.minecraft.nbt.CompoundTag barterNbt = null;
                    if (item.containsKey("barter_nbt") && item.get("barter_nbt") != null) {
                        try { barterNbt = net.minecraft.nbt.TagParser.parseTag(item.get("barter_nbt").toString()); } catch (Exception ignored) { }
                    }
                    boolean barterStrictMatch = item.containsKey("barter_strict_match") && Boolean.parseBoolean(item.get("barter_strict_match").toString());
                    items.add(new ShopEntry(itemId, tradeType, price, entryType, realExtra, requireOp, qty, cName, barterItemId, barterItemQty, questId, questName, nbt, strictMatch, barterNbt, barterStrictMatch));
                }
                playerShops.put(uuid, new PlayerShopInfo(shopName, items));
            }
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("Failed to load player shop config", e);
        }
    }

    public static void save() {
        if (configPath == null) return;
        try {
            Map<String, Map<String, Object>> data = new LinkedHashMap<>();
            for (var entry : playerShops.entrySet()) {
                Map<String, Object> shopData = new LinkedHashMap<>();
                shopData.put("shopName", entry.getValue().shopName());
                List<Map<String, Object>> items = new ArrayList<>();
                for (ShopEntry shopEntry : entry.getValue().items()) {
                    Map<String, Object> itemMap = new LinkedHashMap<>();
                    itemMap.put("type", shopEntry.itemId().toString());
                    itemMap.put("trade_type", shopEntry.tradeType().name().toLowerCase());
                    itemMap.put("price", shopEntry.price());
                    int qty = shopEntry.quantity();
                    if (qty > 1) itemMap.put("quantity", qty);
                    if (!shopEntry.customName().isEmpty()) itemMap.put("custom_name", shopEntry.customName());
                    if (!"item".equals(shopEntry.entryType())) {
                        itemMap.put("entry_type", shopEntry.entryType());
                        itemMap.put("extra_data", shopEntry.extraData());
                        if (shopEntry.requireOp()) {
                            itemMap.put("require_op", true);
                        }
                    }
                    if (shopEntry.barterItemId() != null) {
                        itemMap.put("barter_item_id", shopEntry.barterItemId().toString());
                        itemMap.put("barter_item_qty", shopEntry.barterItemQty());
                    }
                    if (shopEntry.requiredQuestId() != null) {
                        itemMap.put("required_quest_id", shopEntry.requiredQuestId());
                    }
                    if (shopEntry.requiredQuestName() != null) {
                        itemMap.put("required_quest_name", shopEntry.requiredQuestName());
                    }
                    if (shopEntry.nbt() != null) {
                        itemMap.put("nbt", shopEntry.nbt().toString());
                    }
                    if (shopEntry.strictMatch()) {
                        itemMap.put("strict_match", true);
                    }
                    if (shopEntry.barterNbt() != null) {
                        itemMap.put("barter_nbt", shopEntry.barterNbt().toString());
                    }
                    if (shopEntry.barterStrictMatch()) {
                        itemMap.put("barter_strict_match", true);
                    }
                    items.add(itemMap);
                }
                shopData.put("items", items);
                data.put(entry.getKey().toString(), shopData);
            }
            Files.createDirectories(configPath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("Failed to save player shop config", e);
        }
    }

    public static PlayerShopInfo getShop(UUID uuid) {
        return playerShops.computeIfAbsent(uuid, k -> new PlayerShopInfo("", new ArrayList<>()));
    }

    public static void setShopName(UUID uuid, String name) {
        var info = getShop(uuid);
        playerShops.put(uuid, new PlayerShopInfo(name, info.items()));
        dirtyShops = true;
    }

    public static void addItem(UUID uuid, ShopEntry entry) {
        var info = getShop(uuid);
        info.items().add(entry);
        dirtyShops = true;
    }

    public static void removeItem(UUID uuid, int index) {
        var info = getShop(uuid);
        if (index >= 0 && index < info.items().size()) {
            info.items().remove(index);
            dirtyShops = true;
        }
    }

    public static void updateItem(UUID uuid, int index, ShopEntry entry) {
        var info = getShop(uuid);
        if (index >= 0 && index < info.items().size()) {
            info.items().set(index, entry);
            dirtyShops = true;
        }
    }

    public static void moveItemUp(UUID uuid, int index) {
        var info = getShop(uuid);
        List<ShopEntry> items = info.items();
        if (index > 0 && index < items.size()) {
            var tmp = items.get(index);
            items.set(index, items.get(index - 1));
            items.set(index - 1, tmp);
            dirtyShops = true;
        }
    }

    public static void moveItemDown(UUID uuid, int index) {
        var info = getShop(uuid);
        List<ShopEntry> items = info.items();
        if (index >= 0 && index < items.size() - 1) {
            var tmp = items.get(index);
            items.set(index, items.get(index + 1));
            items.set(index + 1, tmp);
            dirtyShops = true;
        }
    }

    public static void flushSave() {
        if (dirtyShops) {
            dirtyShops = false;
            save();
        }
        if (dirtyStats) {
            dirtyStats = false;
            saveStats();
        }
    }

    public static Map<UUID, PlayerShopInfo> getAllShops() {
        return Collections.unmodifiableMap(playerShops);
    }
}
