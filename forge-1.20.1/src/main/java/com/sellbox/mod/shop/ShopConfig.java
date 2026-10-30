package com.sellbox.mod.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.sellbox.mod.SellBoxMod;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ShopConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path configPath;
    private static Map<String, List<ShopEntry>> categories = new LinkedHashMap<>();
    private static boolean editMode = false;
    private static boolean adminEditEnabled = false;
    private static volatile boolean dirty = false;

    public static boolean isEditMode() { return editMode; }
    public static void setEditMode(boolean mode) { editMode = mode; }
    public static void toggleEditMode() { editMode = !editMode; }

    public static boolean isAdminEditEnabled() { return adminEditEnabled; }
    public static void toggleAdminEdit() { adminEditEnabled = !adminEditEnabled; }

    public static void addCategory(String name) {
        if (!categories.containsKey(name)) {
            categories.put(name, new ArrayList<>());
            dirty = true;
        }
    }

    public static void removeCategory(String name) {
        categories.remove(name);
        dirty = true;
    }

    public static void renameCategory(String oldName, String newName) {
        if (oldName.equals(newName) || categories.containsKey(newName)) return;
        List<ShopEntry> entries = categories.remove(oldName);
        if (entries != null) {
            categories.put(newName, entries);
            dirty = true;
        }
    }

    public static void moveCategoryUp(String name) {
        List<String> keys = new ArrayList<>(categories.keySet());
        int idx = keys.indexOf(name);
        if (idx <= 0) return;
        String prev = keys.get(idx - 1);
        List<ShopEntry> prevEntries = categories.get(prev);
        List<ShopEntry> curEntries = categories.get(name);
        LinkedHashMap<String, List<ShopEntry>> reordered = new LinkedHashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            if (i == idx - 1) {
                reordered.put(name, curEntries);
            } else if (i == idx) {
                reordered.put(prev, prevEntries);
            } else {
                reordered.put(keys.get(i), categories.get(keys.get(i)));
            }
        }
        categories = reordered;
        dirty = true;
    }

    public static void moveCategoryDown(String name) {
        List<String> keys = new ArrayList<>(categories.keySet());
        int idx = keys.indexOf(name);
        if (idx < 0 || idx >= keys.size() - 1) return;
        String next = keys.get(idx + 1);
        List<ShopEntry> nextEntries = categories.get(next);
        List<ShopEntry> curEntries = categories.get(name);
        LinkedHashMap<String, List<ShopEntry>> reordered = new LinkedHashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            if (i == idx) {
                reordered.put(next, nextEntries);
            } else if (i == idx + 1) {
                reordered.put(name, curEntries);
            } else {
                reordered.put(keys.get(i), categories.get(keys.get(i)));
            }
        }
        categories = reordered;
        dirty = true;
    }

    public static void addItem(String categoryName, ShopEntry entry) {
        categories.computeIfAbsent(categoryName, k -> new ArrayList<>()).add(entry);
        dirty = true;
    }

    public static void removeItem(String categoryName, int index) {
        List<ShopEntry> entries = categories.get(categoryName);
        if (entries != null && index >= 0 && index < entries.size()) {
            entries.remove(index);
            dirty = true;
        }
    }

    public static void updateItem(String categoryName, int index, ShopEntry newEntry) {
        List<ShopEntry> entries = categories.get(categoryName);
        if (entries != null && index >= 0 && index < entries.size()) {
            entries.set(index, newEntry);
            dirty = true;
        }
    }

    public static void moveItemUp(String categoryName, int index) {
        List<ShopEntry> entries = categories.get(categoryName);
        if (entries != null && index > 0 && index < entries.size()) {
            ShopEntry temp = entries.get(index - 1);
            entries.set(index - 1, entries.get(index));
            entries.set(index, temp);
            dirty = true;
        }
    }

    public static void moveItemDown(String categoryName, int index) {
        List<ShopEntry> entries = categories.get(categoryName);
        if (entries != null && index >= 0 && index < entries.size() - 1) {
            ShopEntry temp = entries.get(index + 1);
            entries.set(index + 1, entries.get(index));
            entries.set(index, temp);
            dirty = true;
        }
    }

    public static void init(Path configDir) {
        configPath = configDir.resolve("SellBox").resolve("sellbox_shop.json");
        load();
    }

    /**
     * 设置世界目录路径（用于按存档保存数据）
     */
    public static void setWorldDir(Path worldDir) {
        if (worldDir != null) {
            configPath = worldDir.resolve("config").resolve("SellBox").resolve("sellbox_shop.json");
            load();
        }
    }

    public static void flushSave() {
        if (dirty) {
            dirty = false;
            save();
        }
    }

    public static void load() {
        categories.clear();
        if (configPath == null || !Files.exists(configPath)) {
            createDefault();
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            Map<String, List<Map<String, Object>>> rawData = GSON.fromJson(
                    reader, new TypeToken<Map<String, List<Map<String, Object>>>>() {}.getType());
            if (rawData == null) {
                createDefault();
                return;
            }
            for (Map.Entry<String, List<Map<String, Object>>> entry : rawData.entrySet()) {
                String categoryName = entry.getKey();
                List<ShopEntry> entries = new ArrayList<>();
                for (Map<String, Object> item : entry.getValue()) {
                    String typeStr = (String) item.get("type");
                    ResourceLocation itemId = ResourceLocation.tryParse(typeStr);
                    if (itemId == null) continue;
                    ShopEntry.TradeType tradeType = ShopEntry.TradeType.valueOf(
                            ((String) item.get("trade_type")).toUpperCase());
                    int price = ((Number) item.get("price")).intValue();
                    String entryType = item.containsKey("entry_type") ? (String) item.get("entry_type") : "item";
                    String extraData = item.containsKey("extra_data") ? (String) item.get("extra_data") : "";
                    boolean requireOp = item.containsKey("require_op") && Boolean.parseBoolean(item.get("require_op").toString());
                    int qty = item.containsKey("quantity") ? ((Number) item.get("quantity")).intValue() : 1;
                    String cName = item.containsKey("custom_name") ? (String) item.get("custom_name") : "";
                    ResourceLocation barterItemId = null;
                    if (item.containsKey("barter_item_id") && item.get("barter_item_id") != null) {
                        barterItemId = ResourceLocation.tryParse(item.get("barter_item_id").toString());
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
                    int tradeLimit = item.containsKey("trade_limit") ? ((Number) item.get("trade_limit")).intValue() : 0;
                    boolean limitPerPlayer = item.containsKey("limit_per_player") && Boolean.parseBoolean(item.get("limit_per_player").toString());
                    entries.add(new ShopEntry(itemId, tradeType, price, entryType, realExtra, requireOp, qty, cName, barterItemId, barterItemQty, questId, questName, nbt, strictMatch, barterNbt, barterStrictMatch, tradeLimit, limitPerPlayer));
                }
                categories.put(categoryName, entries);
            }
            if (categories.isEmpty()) {
                categories.put("默认商店", new ArrayList<>());
            }
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("Failed to load shop config, creating default", e);
            createDefault();
        }
    }

    public static void save() {
        if (configPath == null) return;
        try {
            Map<String, List<Map<String, Object>>> data = new LinkedHashMap<>();
            for (Map.Entry<String, List<ShopEntry>> entry : categories.entrySet()) {
                List<Map<String, Object>> items = new ArrayList<>();
                for (ShopEntry shopEntry : entry.getValue()) {
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
                    // 交易限量：trade_limit<=0 表示无限；limit_per_player=true 表示每个玩家各自计算
                    if (shopEntry.hasTradeLimit()) {
                        itemMap.put("trade_limit", shopEntry.tradeLimit());
                        if (shopEntry.limitPerPlayer()) {
                            itemMap.put("limit_per_player", true);
                        }
                    }
                    items.add(itemMap);
                }
                data.put(entry.getKey(), items);
            }
            Files.createDirectories(configPath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("Failed to save shop config", e);
        }
    }

    private static void createDefault() {
        categories = new LinkedHashMap<>();
        save();
    }

    public static Map<String, List<ShopEntry>> getCategories() {
        return categories;
    }

    @Nullable
    public static List<ShopEntry> getCategory(String name) {
        return categories.get(name);
    }

    public static List<String> getCategoryNames() {
        return new ArrayList<>(categories.keySet());
    }
}
