package com.sellbox.mod.config;

import com.sellbox.mod.SellBoxMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SellBoxConfig {
    private static final Map<Item, Integer> PRICES = new ConcurrentHashMap<>();
    /** ID+NBT 精确级价格：键 = itemId|SNBT（如 kaleidoscope_tavern:wine|{BrewLevel:6}），匹配时优先于 ID 级 */
    private static final Map<String, Integer> PRICES_NBT = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> PENDING = new ConcurrentHashMap<>();
    private static boolean sdmCurrencyEnabled = false;
    private static Path configDir;
    private static Path sellBoxDir;
    private static Path priceFile;
    private static Path pendingFile;
    private static Path vssFlagFile;
    private static Path shopOverrideFlagFile;
    private static boolean shopOverrideEnabled = false;
    private static volatile boolean dirtyPending = false;
    private static volatile boolean dirtyPricing = false;

    // 世界目录路径（用于按存档保存数据）
    private static Path worldDir;
    // 当前生效的价格文件路径（全局或按存档，由 loadPricing/savePricing 实际读写）
    private static Path activePriceFile;

    public static final ForgeConfigSpec SPEC;
    public static ForgeConfigSpec.IntValue SELL_TIME;
    public static ForgeConfigSpec.BooleanValue USE_SDM_CURRENCY;
    public static ForgeConfigSpec.BooleanValue GLOBAL_PRICING;
    public static ForgeConfigSpec.BooleanValue GLOBAL_SHOP;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        SELL_TIME = builder
                .comment("自动售货机出货时间（游戏刻，0-24000，默认 5）")
                .defineInRange("sellTime", 5, 0, 24000);

        USE_SDM_CURRENCY = builder
                .comment("是否使用 SDM 经济系统（默认 false，使用本模组内置经济；当 SDMShop 加载时设为 true 使用 SDM 经济）")
                .define("useSdmCurrency", false);

        GLOBAL_PRICING = builder
                .comment("物品收购价是否全局共享（默认 true，所有存档共享同一份收购价；设为 false 则按存档分别保存）")
                .define("globalPricing", true);

        GLOBAL_SHOP = builder
                .comment("商店配置是否全局共享（默认 true，所有存档共享同一份商店配置；设为 false 则按存档分别保存）")
                .define("globalShop", true);

        SPEC = builder.build();
    }

    public static void init(Path configDirPath) {
        configDir = configDirPath;
        sellBoxDir = configDirPath.resolve("SellBox");
        priceFile = sellBoxDir.resolve("sellbox_prices.txt");
        activePriceFile = priceFile;
        pendingFile = sellBoxDir.resolve("sellbox_pending.dat");
        vssFlagFile = sellBoxDir.resolve("sellbox_vss.flag");
        shopOverrideFlagFile = sellBoxDir.resolve("sellbox_shopoverride.flag");
        try {
            Files.createDirectories(sellBoxDir);
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("无法创建 SellBox 配置目录", e);
        }
        loadSdmFlag();
        loadShopOverrideFlag();
        loadPricing();
    }

    /**
     * 设置世界目录路径（用于按存档保存数据）
     */
    public static void setWorldDir(Path worldDirPath) {
        worldDir = worldDirPath;
        // 根据配置决定数据保存路径
        if (worldDirPath != null) {
            Path worldSellBoxDir = worldDirPath.resolve("config").resolve("SellBox");
            
            // 物品收购价：根据配置决定保存位置
            if (!isGlobalPricing()) {
                activePriceFile = worldSellBoxDir.resolve("sellbox_prices.txt");
                loadPricing();
            }
            
            // 离线待发放：始终保存在世界存档
            pendingFile = worldSellBoxDir.resolve("sellbox_pending.dat");
            loadPending();
        }
    }

    /**
     * 获取世界目录路径
     */
    public static Path getWorldDir() {
        return worldDir;
    }

    /**
     * 物品收购价是否全局共享
     */
    public static boolean isGlobalPricing() {
        return GLOBAL_PRICING.get();
    }

    /**
     * 商店配置是否全局共享
     */
    public static boolean isGlobalShop() {
        return GLOBAL_SHOP.get();
    }

    public static boolean isSdmCurrencyEnabled() {
        return sdmCurrencyEnabled;
    }

    public static boolean isVssFlagFileExists() {
        return vssFlagFile != null && Files.exists(vssFlagFile);
    }

    public static void setSdmCurrencyEnabled(boolean value) {
        sdmCurrencyEnabled = value;
        USE_SDM_CURRENCY.set(value);
        saveSdmFlag();
    }

    private static void loadSdmFlag() {
        if (vssFlagFile == null || !Files.exists(vssFlagFile)) return;
        try {
            String content = Files.readString(vssFlagFile, StandardCharsets.UTF_8).trim();
            sdmCurrencyEnabled = Boolean.parseBoolean(content);
            USE_SDM_CURRENCY.set(sdmCurrencyEnabled);
            SellBoxMod.LOGGER.info("Loaded SDM flag: {}", sdmCurrencyEnabled);
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("Failed to load SDM flag", e);
        }
    }

    private static void saveSdmFlag() {
        if (vssFlagFile == null) return;
        try {
            Files.createDirectories(vssFlagFile.getParent());
            Files.writeString(vssFlagFile, Boolean.toString(sdmCurrencyEnabled), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("Failed to save SDM flag", e);
        }
    }

    public static boolean isShopOverrideEnabled() {
        return shopOverrideEnabled;
    }

    public static void setShopOverrideEnabled(boolean value) {
        shopOverrideEnabled = value;
        saveShopOverrideFlag();
    }

    private static void loadShopOverrideFlag() {
        if (shopOverrideFlagFile == null || !Files.exists(shopOverrideFlagFile)) return;
        try {
            String content = Files.readString(shopOverrideFlagFile, StandardCharsets.UTF_8).trim();
            shopOverrideEnabled = Boolean.parseBoolean(content);
            SellBoxMod.LOGGER.info("Loaded shop override flag: {}", shopOverrideEnabled);
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("Failed to load shop override flag", e);
        }
    }

    private static void saveShopOverrideFlag() {
        if (shopOverrideFlagFile == null) return;
        try {
            Files.createDirectories(shopOverrideFlagFile.getParent());
            Files.writeString(shopOverrideFlagFile, Boolean.toString(shopOverrideEnabled), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("Failed to save shop override flag", e);
        }
    }

    public static int getPendingBalance(UUID playerUuid) {
        return PENDING.getOrDefault(playerUuid, 0);
    }

    public static boolean addPendingBalance(UUID playerUuid, int amount) {
        if (amount <= 0) return true;
        int previous = PENDING.getOrDefault(playerUuid, 0);
        PENDING.merge(playerUuid, amount, Integer::sum);
        // 离线收益涉及真实金钱，一改立刻落盘，避免崩溃窗口导致丢钱/重复发放
        if (!savePending()) {
            // 落盘失败：回滚内存，保持与磁盘一致；调用方据此取消本次结算
            if (previous <= 0) PENDING.remove(playerUuid);
            else PENDING.put(playerUuid, previous);
            return false;
        }
        return true;
    }

    public static int deductPendingBalance(UUID playerUuid, int maxAmount) {
        int current = PENDING.getOrDefault(playerUuid, 0);
        if (current <= 0 || maxAmount <= 0) return 0;
        int deducted = Math.min(current, maxAmount);
        if (deducted >= current) {
            PENDING.remove(playerUuid);
        } else {
            PENDING.put(playerUuid, current - deducted);
        }
        // 离线收益涉及真实金钱，一改立刻落盘，避免崩溃窗口导致丢钱/重复发放
        if (!savePending()) {
            // 落盘失败：回滚内存，返回 0 表示本次未扣，调用方也不会发放
            PENDING.put(playerUuid, current);
            return 0;
        }
        return deducted;
    }

    public static boolean tryDeductPendingBalance(UUID playerUuid, int amount) {
        if (amount <= 0) return true;
        int current = PENDING.getOrDefault(playerUuid, 0);
        if (current < amount) return false;
        if (current == amount) {
            PENDING.remove(playerUuid);
        } else {
            PENDING.put(playerUuid, current - amount);
        }
        // 离线收益涉及真实金钱，一改立刻落盘，避免崩溃窗口导致丢钱/重复发放
        if (!savePending()) {
            PENDING.put(playerUuid, current);
            return false;
        }
        return true;
    }

    public static void loadPricing() {
        PRICES.clear();
        // 注意：reload 不清空 NBT 精确价格，NBT 价格统一用 /sellbox delnbt 清空
        if (activePriceFile == null || !Files.exists(activePriceFile)) {
            createDefaultPriceFile();
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(activePriceFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;

                String[] parts = line.split("=", 2);
                if (parts.length != 2) continue;

                String itemId = parts[0].trim();
                int price;
                try {
                    price = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException e) {
                    continue;
                }

                // NBT 级价格行：itemId|SNBT=price（如 kaleidoscope_tavern:wine|{BrewLevel:6}=500）
                int pipeIdx = itemId.indexOf('|');
                if (pipeIdx >= 0) {
                    String baseId = itemId.substring(0, pipeIdx).trim();
                    String snbt = itemId.substring(pipeIdx + 1).trim();
                    ResourceLocation nid = ResourceLocation.tryParse(baseId);
                    if (nid != null && BuiltInRegistries.ITEM.containsKey(nid) && !snbt.isEmpty()) {
                        try {
                            // 解析并归一化后作为键，与运行时 stack.getTag().toString() 保持一致
                            net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.TagParser.parseTag(snbt);
                            if (price > 0) PRICES_NBT.put(nid + "|" + tag.toString(), price);
                        } catch (Exception ignored) {
                        }
                    }
                    continue;
                }

                ResourceLocation id = ResourceLocation.tryParse(itemId);
                if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                    Item item = BuiltInRegistries.ITEM.get(id);
                    if (price > 0) {
                        PRICES.put(item, price);
                    }
                }
            }
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("Failed to load pricing file", e);
        }

        SellBoxMod.LOGGER.info("Loaded {} priced items", PRICES.size());
    }

    public static void savePricing() {
        if (activePriceFile == null) return;

        try (BufferedWriter writer = Files.newBufferedWriter(activePriceFile, StandardCharsets.UTF_8)) {
            writer.write("# SellBox 物品收购价配置文件");
            writer.newLine();
            writer.write("# 格式：物品ID=价格（整数）");
            writer.newLine();
            writer.write("# NBT 精确级：物品ID|NBT=价格，例如 kaleidoscope_tavern:wine|{BrewLevel:6}=500");
            writer.newLine();
            writer.write("# 示例：minecraft:iron_ingot=10");
            writer.newLine();
            writer.write("# 支持 # 和 // 开头的注释行，空行自动跳过");
            writer.newLine();
            writer.newLine();

            List<Map.Entry<Item, Integer>> sorted = new ArrayList<>(PRICES.entrySet());
            sorted.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

            for (Map.Entry<Item, Integer> entry : sorted) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(entry.getKey());
                writer.write(id.toString() + "=" + entry.getValue());
                writer.newLine();
            }

            List<Map.Entry<String, Integer>> sortedNbt = new ArrayList<>(PRICES_NBT.entrySet());
            sortedNbt.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
            for (Map.Entry<String, Integer> entry : sortedNbt) {
                writer.write(entry.getKey() + "=" + entry.getValue());
                writer.newLine();
            }
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("Failed to save pricing file", e);
        }
    }

    private static void createDefaultPriceFile() {
        if (activePriceFile == null) return;
        try {
            Files.createDirectories(activePriceFile.getParent());
            savePricing();
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("Failed to create default pricing file", e);
        }
    }

    public static int getPrice(Item item) {
        return PRICES.getOrDefault(item, 0);
    }

    /** 按 ID+NBT 全匹配查价：优先精确 NBT 级（itemId|SNBT），无则回退 ID 级 */
    public static int getPrice(ItemStack stack) {
        if (!stack.isEmpty()) {
            String key = nbtPriceKey(stack);
            if (key != null) {
                Integer exact = PRICES_NBT.get(key);
                if (exact != null) return exact;
            }
        }
        return getPrice(stack.getItem());
    }

    public static void setPriceNbt(ItemStack stack, int price) {
        String key = nbtPriceKey(stack);
        if (key == null) return;
        if (price <= 0) {
            PRICES_NBT.remove(key);
        } else {
            PRICES_NBT.put(key, price);
        }
        dirtyPricing = true;
    }

    public static void removePriceNbt(ItemStack stack) {
        String key = nbtPriceKey(stack);
        if (key == null) return;
        PRICES_NBT.remove(key);
        dirtyPricing = true;
    }

    public static Map<String, Integer> getAllPricesNbt() {
        return Collections.unmodifiableMap(PRICES_NBT);
    }

    /** 清空全部 NBT 精确价格，返回清除条数 */
    public static int clearPricesNbt() {
        int n = PRICES_NBT.size();
        if (n > 0) {
            PRICES_NBT.clear();
            dirtyPricing = true;
        }
        return n;
    }

    /** 生成 ID+NBT 键；物品无 NBT 时返回 null（此时不参与 NBT 精确匹配） */
    private static String nbtPriceKey(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getTag() == null) return null;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.toString() + "|" + stack.getTag().toString();
    }

    public static void setPrice(Item item, int price) {
        if (price <= 0) {
            PRICES.remove(item);
        } else {
            PRICES.put(item, price);
        }
        dirtyPricing = true;
    }

    public static Map<Item, Integer> getAllPrices() {
        return Collections.unmodifiableMap(PRICES);
    }

    public static boolean isPriced(Item item) {
        return PRICES.containsKey(item);
    }

    public static void flushSavePending() {
        if (dirtyPending) {
            dirtyPending = false;
            savePending();
        }
    }

    public static void flushSavePricing() {
        if (dirtyPricing) {
            dirtyPricing = false;
            savePricing();
        }
    }

    public static void loadPending() {
        PENDING.clear();
        if (pendingFile == null || !Files.exists(pendingFile)) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(pendingFile)))) {
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                long mostSig = in.readLong();
                long leastSig = in.readLong();
                int balance = in.readInt();
                if (balance > 0) {
                    PENDING.put(new UUID(mostSig, leastSig), balance);
                }
            }
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("Failed to load pending balances", e);
        }
    }

    // 先写临时文件并 fsync，再原子替换，避免写到一半损坏整个账本；返回是否成功落盘
    public static boolean savePending() {
        if (pendingFile == null) return false;
        Path tmp = pendingFile.resolveSibling(pendingFile.getFileName().toString() + ".tmp");
        try {
            Files.createDirectories(pendingFile.getParent());
            try (FileChannel channel = FileChannel.open(tmp,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                 DataOutputStream out = new DataOutputStream(Channels.newOutputStream(channel))) {
                out.writeInt(PENDING.size());
                for (Map.Entry<UUID, Integer> entry : PENDING.entrySet()) {
                    out.writeLong(entry.getKey().getMostSignificantBits());
                    out.writeLong(entry.getKey().getLeastSignificantBits());
                    out.writeInt(entry.getValue());
                }
                out.flush();
                channel.force(true);
            }
            try {
                Files.move(tmp, pendingFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, pendingFile, StandardCopyOption.REPLACE_EXISTING);
            }
            dirtyPending = false;
            return true;
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("Failed to save pending balances", e);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
            return false;
        }
    }

    public static void setConfigDir(Path configDir) {
        init(configDir);
    }
}
