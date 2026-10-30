package com.sellbox.mod.config;

import com.sellbox.mod.SellBoxMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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
    // 物品收购价缓存（物品 -> 价格）
    private static final Map<Item, Integer> PRICES = new ConcurrentHashMap<>();
    // ID+NBT 精确级价格：键 = itemId|SNBT（如 kaleidoscope_tavern:wine|{BrewLevel:6}），匹配时优先于 ID 级
    private static final Map<String, Integer> PRICES_NBT = new ConcurrentHashMap<>();
    // 离线待发放余额缓存（玩家UUID -> 金额）
    private static final Map<UUID, Integer> PENDING = new ConcurrentHashMap<>();
    // 根配置目录（由 FMLPaths.CONFIGDIR 传入）
    private static Path configDir;
    // SellBox 子文件夹的引用
    private static Path sellBoxDir;
    // 物品收购价文件路径
    private static Path priceFile;
    // 当前活跃的价格文件路径（可能是全局或存档）
    private static Path activePriceFile;
    // 离线待发放余额文件路径
    private static Path pendingFile;
    // 运行时商店覆盖开关（VSS 经济下可临时启用商店，不持久化）
    private static boolean shopOverrideEnabled = false;
    private static volatile boolean dirtyPending = false;
    private static volatile boolean dirtyPricing = false;

    // 配置值（从 sellbox-common.properties 读取）
    private static int sellTime = 5;
    private static boolean useVssCurrency = false;
    private static boolean globalPrices = true;
    private static boolean globalShopConfig = true;

    // 配置文件路径
    private static Path commonConfigPath;

    public static int getSellTime() { return sellTime; }
    public static void setSellTime(int value) { sellTime = value; saveCommonConfig(); }
    public static boolean isVssCurrencyEnabled() { return useVssCurrency; }
    public static boolean isGlobalPrices() { return globalPrices; }
    public static boolean isGlobalShopConfig() { return globalShopConfig; }

    public static void setVssCurrencyEnabled(boolean value) { useVssCurrency = value; saveCommonConfig(); }
    public static void setGlobalPrices(boolean value) { globalPrices = value; saveCommonConfig(); }
    public static void setGlobalShopConfig(boolean value) { globalShopConfig = value; saveCommonConfig(); }

    public static void init(Path configDirPath) {
        configDir = configDirPath;
        sellBoxDir = configDirPath.resolve("SellBox");
        priceFile = sellBoxDir.resolve("sellbox_prices.txt");
        activePriceFile = priceFile;
        pendingFile = sellBoxDir.resolve("sellbox_pending.dat");
        commonConfigPath = sellBoxDir.resolve("sellbox-common.properties");
        try {
            Files.createDirectories(sellBoxDir);
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("无法创建 SellBox 配置目录", e);
        }
        loadCommonConfig();
    }

    // 按存档初始化（仅 pending 文件改为存档内）
    public static void initPerWorld(Path worldDir) {
        Path worldSellBoxDir = worldDir.resolve("config").resolve("SellBox");
        pendingFile = worldSellBoxDir.resolve("sellbox_pending.dat");
        try {
            Files.createDirectories(worldSellBoxDir);
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("无法创建存档 SellBox 目录", e);
        }
    }

    // 从 sellbox-common.properties 读取配置
    private static void loadCommonConfig() {
        if (commonConfigPath == null || !Files.exists(commonConfigPath)) {
            saveCommonConfig();
            return;
        }
        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(commonConfigPath)) {
            props.load(is);
            sellTime = Integer.parseInt(props.getProperty("sellTime", "5"));
            useVssCurrency = Boolean.parseBoolean(props.getProperty("useVssCurrency", "false"));
            globalPrices = Boolean.parseBoolean(props.getProperty("globalPrices", "true"));
            globalShopConfig = Boolean.parseBoolean(props.getProperty("globalShopConfig", "true"));
        } catch (Exception e) {
            SellBoxMod.LOGGER.error("Failed to load sellbox-common.properties", e);
        }
    }

    // 保存配置到 sellbox-common.properties
    public static void saveCommonConfig() {
        if (commonConfigPath == null) return;
        try {
            Files.createDirectories(commonConfigPath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(commonConfigPath, StandardCharsets.UTF_8)) {
                writer.write("# SellBox 模组配置文件");
                writer.newLine();
                writer.newLine();
                writer.write("# 自动售货机出货时间（游戏刻，0-24000，默认 5）");
                writer.newLine();
                writer.write("# Range: 0 ~ 24000");
                writer.newLine();
                writer.write("sellTime=" + sellTime);
                writer.newLine();
                writer.newLine();
                writer.write("# 是否使用 VSS 货币系统");
                writer.newLine();
                writer.write("# 默认 false，使用本模组内置经济");
                writer.write("# 当 Vaulted-Shop 加载时设为 true 使用 VSS 经济");
                writer.newLine();
                writer.write("useVssCurrency=" + useVssCurrency);
                writer.newLine();
                writer.newLine();
                writer.write("# 物品收购价是否全局共享");
                writer.newLine();
                writer.write("# 默认 true，所有存档共享同一份收购价");
                writer.write("# 设为 false 则按存档分别保存");
                writer.newLine();
                writer.write("globalPrices=" + globalPrices);
                writer.newLine();
                writer.newLine();
                writer.write("# 商店配置是否全局共享");
                writer.newLine();
                writer.write("# 默认 true，所有存档共享同一份商店配置");
                writer.write("# 设为 false 则按存档分别保存");
                writer.newLine();
                writer.write("globalShopConfig=" + globalShopConfig);
                writer.newLine();
            }
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("Failed to save sellbox-common.properties", e);
        }
    }

    // 获取商店覆盖开关（VSS 经济下是否允许使用商店）
    public static boolean isShopOverrideEnabled() {
        return shopOverrideEnabled;
    }

    // 设置商店覆盖开关（VSS 经济下临时启用/禁用商店）
    public static void setShopOverrideEnabled(boolean value) {
        shopOverrideEnabled = value;
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

    // 与 SellBoxEconomy.tryDeductBalance 对称：检查余额充足后扣款，返回是否成功
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

    // 从 sellbox_prices.txt 加载物品收购价
    public static void loadPricing() {
        PRICES.clear();
        PRICES_NBT.clear();
        Path readPath = activePriceFile != null ? activePriceFile : priceFile;
        if (readPath == null || !Files.exists(readPath)) {
            createDefaultPriceFile();
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(readPath, StandardCharsets.UTF_8)) {
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
                            // 解析并归一化后作为键，与运行时 stack 的 custom_data SNBT 保持一致
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

    // 按存档加载物品收购价（用于 globalPrices=false 时）
    public static void loadPricingPerWorld(Path worldDir) {
        Path worldPriceFile = worldDir.resolve("config").resolve("SellBox").resolve("sellbox_prices.txt");
        try {
            Files.createDirectories(worldPriceFile.getParent());
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("Failed to create world pricing directory", e);
        }
        // 切换到存档路径
        activePriceFile = worldPriceFile;
        if (!Files.exists(worldPriceFile)) {
            createDefaultPriceFile();
        } else {
            loadPricing();
        }
    }

    // 保存物品收购价到 sellbox_prices.txt
    public static void savePricing() {
        Path savePath = activePriceFile != null ? activePriceFile : priceFile;
        if (savePath == null) return;

        try (BufferedWriter writer = Files.newBufferedWriter(savePath, StandardCharsets.UTF_8)) {
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

            // NBT 精确级价格单独追加，键即 itemId|SNBT
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

    // 当价格文件不存在时创建默认文件
    private static void createDefaultPriceFile() {
        Path savePath = activePriceFile != null ? activePriceFile : priceFile;
        if (savePath == null) return;
        try {
            Files.createDirectories(savePath.getParent());
            savePricing();
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("Failed to create default pricing file", e);
        }
    }

    public static int getPrice(Item item) {
        return PRICES.getOrDefault(item, 0);
    }

    /** 按 ID+完整组件 NBT 全匹配查价：优先精确 NBT 级（itemId|SNBT），无则回退 ID 级 */
    public static int getPrice(ItemStack stack, net.minecraft.core.HolderLookup.Provider registries) {
        if (stack != null && !stack.isEmpty()) {
            String key = nbtPriceKey(stack, registries);
            if (key != null) {
                Integer exact = PRICES_NBT.get(key);
                if (exact != null) return exact;
            }
        }
        return getPrice(stack == null ? null : stack.getItem());
    }

    public static void setPriceNbt(ItemStack stack, int price, net.minecraft.core.HolderLookup.Provider registries) {
        String key = nbtPriceKey(stack, registries);
        if (key == null) return;
        if (price <= 0) {
            PRICES_NBT.remove(key);
        } else {
            PRICES_NBT.put(key, price);
        }
        dirtyPricing = true;
    }

    public static void removePriceNbt(ItemStack stack, net.minecraft.core.HolderLookup.Provider registries) {
        String key = nbtPriceKey(stack, registries);
        if (key == null) return;
        PRICES_NBT.remove(key);
        dirtyPricing = true;
    }

    /** 直接按 itemId|SNBT 键设置 NBT 价格（客户端 GUI 用），空键防御 */
    public static void setPriceNbtKey(String key, int price) {
        if (key == null || key.isEmpty()) return;
        if (price <= 0) {
            PRICES_NBT.remove(key);
        } else {
            PRICES_NBT.put(key, price);
        }
        dirtyPricing = true;
    }

    /** 直接按 itemId|SNBT 键删除 NBT 价格，返回是否存在 */
    public static boolean removePriceNbtKey(String key) {
        if (key == null || key.isEmpty()) return false;
        boolean existed = PRICES_NBT.remove(key) != null;
        if (existed) dirtyPricing = true;
        return existed;
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

    /** 生成 ID+完整组件 NBT 键；无任何组件时生成 itemId|{} 键（保证无 NBT 物品可被 NBT 级定价覆盖） */
    private static String nbtPriceKey(ItemStack stack, net.minecraft.core.HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty()) return null;
        String snbt = com.sellbox.mod.util.ItemVariantUtil.brewNbtSnbt(stack, registries);
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.toString() + "|" + (snbt != null ? snbt : "{}");
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

    // 从 sellbox_pending.dat 加载离线待发放余额
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

    // 保存离线待发放余额到 sellbox_pending.dat
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
