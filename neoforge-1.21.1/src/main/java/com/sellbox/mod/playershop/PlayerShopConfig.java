package com.sellbox.mod.playershop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.shop.ShopEntry;
import net.minecraft.resources.ResourceLocation;

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
    private static Path blacklistPath;
    private static final Map<UUID, PlayerShopInfo> playerShops = new LinkedHashMap<>();
    private static final Map<UUID, ShopStats> shopStats = new LinkedHashMap<>();
    private static final Set<String> blacklistedItems = new HashSet<>();
    private static volatile boolean dirtyShops = false;
    private static volatile boolean dirtyStats = false;
    private static volatile boolean dirtyBlacklist = false;
    private static boolean adminEditMode = false;

    public static void init(Path configDir) {
        configPath = configDir.resolve("SellBox").resolve("sellbox_playershops.json");
        statsPath = configDir.resolve("SellBox").resolve("sellbox_shopstats.json");
        blacklistPath = configDir.resolve("SellBox").resolve("sellbox_blacklist.txt");
        load();
        loadStats();
        loadBlacklist();
    }

    public static void initPerWorld(Path worldDir) {
        configPath = worldDir.resolve("config").resolve("SellBox").resolve("sellbox_playershops.json");
        statsPath = worldDir.resolve("config").resolve("SellBox").resolve("sellbox_shopstats.json");
        blacklistPath = worldDir.resolve("config").resolve("SellBox").resolve("sellbox_blacklist.txt");
        load();
        loadStats();
        loadBlacklist();
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
                    Long requiredQuestId = item.containsKey("required_quest_id") && item.get("required_quest_id") != null
                            ? ((Number) item.get("required_quest_id")).longValue() : null;
                    String requiredQuestName = item.containsKey("required_quest_name") ? (String) item.get("required_quest_name") : null;
                    boolean strictMatch = item.containsKey("strict_match") && Boolean.parseBoolean(item.get("strict_match").toString());
                    String nbt = item.containsKey("nbt") && item.get("nbt") != null ? (String) item.get("nbt") : null;
                    boolean barterOfferStrict = item.containsKey("barter_offer_strict") && Boolean.parseBoolean(item.get("barter_offer_strict").toString());
                    String barterOfferNbt = item.containsKey("barter_offer_nbt") && item.get("barter_offer_nbt") != null ? (String) item.get("barter_offer_nbt") : null;
                    boolean barterGoodsStrict = item.containsKey("barter_goods_strict") && Boolean.parseBoolean(item.get("barter_goods_strict").toString());
                    String realExtra = !"barter".equals(entryType) ? extraData : "";
                    items.add(new ShopEntry(itemId, tradeType, price, entryType, realExtra, requireOp, qty, cName, barterItemId, barterItemQty, requiredQuestId, requiredQuestName, strictMatch, nbt, barterOfferStrict, barterOfferNbt, barterGoodsStrict));
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
                    // 以物易物字段
                    if (shopEntry.barterItemId() != null) {
                        itemMap.put("barter_item_id", shopEntry.barterItemId().toString());
                        itemMap.put("barter_item_qty", shopEntry.barterItemQty());
                    }
                    if (shopEntry.strictMatch()) {
                        itemMap.put("strict_match", true);
                        if (shopEntry.nbt() != null && !shopEntry.nbt().isEmpty()) {
                            itemMap.put("nbt", shopEntry.nbt());
                        }
                    }
                    // 以物易物：商品物 NBT 总是保留；出价物严格匹配字段单独存
                    if ("barter".equals(shopEntry.entryType())) {
                        if (shopEntry.nbt() != null && !shopEntry.nbt().isEmpty()) {
                            itemMap.put("nbt", shopEntry.nbt());
                        }
                        if (shopEntry.barterOfferStrict()) {
                            itemMap.put("barter_offer_strict", true);
                            if (shopEntry.barterOfferNbt() != null && !shopEntry.barterOfferNbt().isEmpty()) {
                                itemMap.put("barter_offer_nbt", shopEntry.barterOfferNbt());
                            }
                        }
                        if (shopEntry.barterGoodsStrict()) {
                            itemMap.put("barter_goods_strict", true);
                        }
                    }
                    if (shopEntry.requiredQuestId() != null) {
                        itemMap.put("required_quest_id", shopEntry.requiredQuestId());
                        if (shopEntry.requiredQuestName() != null) {
                            itemMap.put("required_quest_name", shopEntry.requiredQuestName());
                        }
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

    // ========== 黑名单 ==========

    public static boolean isItemBlacklisted(ResourceLocation itemId) {
        return blacklistedItems.contains(itemId.toString());
    }

    public static boolean addToBlacklist(ResourceLocation itemId) {
        boolean added = blacklistedItems.add(itemId.toString());
        if (added) dirtyBlacklist = true;
        return added;
    }

    public static boolean removeFromBlacklist(ResourceLocation itemId) {
        boolean removed = blacklistedItems.remove(itemId.toString());
        if (removed) dirtyBlacklist = true;
        return removed;
    }

    public static Set<String> getBlacklistedItems() {
        return Collections.unmodifiableSet(blacklistedItems);
    }

    public static void loadBlacklist() {
        blacklistedItems.clear();
        if (blacklistPath == null || !Files.exists(blacklistPath)) return;
        try (BufferedReader reader = Files.newBufferedReader(blacklistPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                blacklistedItems.add(line);
            }
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("Failed to load blacklist", e);
        }
        SellBoxMod.LOGGER.info("Loaded {} blacklisted items", blacklistedItems.size());
    }

    private static void saveBlacklist() {
        if (blacklistPath == null) return;
        try {
            Files.createDirectories(blacklistPath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(blacklistPath, StandardCharsets.UTF_8)) {
                writer.write("# SellBox 物品黑名单 - 每行一个物品ID");
                writer.newLine();
                for (String id : blacklistedItems) {
                    writer.write(id);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("Failed to save blacklist", e);
        }
    }

    // ========== 管理员编辑 ==========

    public static boolean isAdminEditMode() {
        return adminEditMode;
    }

    public static void toggleAdminEditMode() {
        adminEditMode = !adminEditMode;
    }

    // ========== 保存 ==========

    public static void flushSave() {
        if (dirtyShops) {
            dirtyShops = false;
            save();
        }
        if (dirtyStats) {
            dirtyStats = false;
            saveStats();
        }
        if (dirtyBlacklist) {
            dirtyBlacklist = false;
            saveBlacklist();
        }
    }

    public static Map<UUID, PlayerShopInfo> getAllShops() {
        return Collections.unmodifiableMap(playerShops);
    }
}
