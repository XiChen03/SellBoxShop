package com.sellbox.mod.extras;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sellbox.mod.SellBoxMod;
import net.minecraft.server.MinecraftServer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扩展模块的玩家数据管理器：加载、保存、查询。
 * <p>
 * 数据文件 {@code config/SellBox/sellbox_extras_players.json}，结构是
 * {@code {"<uuid>": { ...单个玩家数据... }}}。
 * <p>
 * 保存策略：标记 dirty + 定时（10 秒）落盘，`onServerStopped` 时强制写一次，
 * 避免每次签到/领取都做一次同步 IO。
 */
public final class ExtrasManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<UUID, ExtrasData> PLAYERS = new ConcurrentHashMap<>();
    private static Path dataPath;
    private static volatile boolean dirty = false;
    /** 上次自动清理邮件的时间 */
    private static volatile long lastPurgeMs = 0L;
    private static volatile long lastSaveMs = 0L;

    private ExtrasManager() {}

    public static void init(Path configDir) {
        dataPath = configDir.resolve("SellBox").resolve("sellbox_extras_players.json");
        load();
    }

    public static void initPerWorld(Path worldDir) {
        dataPath = worldDir.resolve("config").resolve("SellBox").resolve("sellbox_extras_players.json");
        load();
    }

    public static ExtrasData get(UUID uuid) {
        return PLAYERS.computeIfAbsent(uuid, k -> new ExtrasData());
    }

    /** 只查询，不创建；用于「可能不存在」的离线玩家，避免伪造 UUID 撑大存档 */
    public static ExtrasData find(UUID uuid) {
        return uuid == null ? null : PLAYERS.get(uuid);
    }

    /** 删除箱体时清掉所有玩家的保底进度，避免同名新箱子继承旧计数 */
    public static void clearCratePity(String crateId) {
        if (crateId == null || crateId.isEmpty()) return;
        for (ExtrasData data : PLAYERS.values()) data.cratePity.remove(crateId);
        markDirty();
    }

    /** 停服时复位自动清理/保存节流计时，换存档后立即重新计时 */
    public static void clearRuntimeState() {
        lastPurgeMs = 0L;
        lastSaveMs = 0L;
    }

    public static void markDirty() {
        dirty = true;
    }

    // ────────────────────────────────────────────
    //  IO
    // ────────────────────────────────────────────

    private static void load() {
        if (dataPath == null || !Files.exists(dataPath)) {
            PLAYERS.clear();
            return;
        }
        try {
            String json = Files.readString(dataPath, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            // 先解析到临时 map，全部完成后再替换；解析失败不破坏内存里的旧状态
            Map<UUID, ExtrasData> loaded = new java.util.LinkedHashMap<>();
            boolean anyBad = false;
            for (var e : root.entrySet()) {
                try {
                    UUID id = UUID.fromString(e.getKey());
                    loaded.put(id, ExtrasData.fromJson(e.getValue().getAsJsonObject()));
                } catch (Throwable t) {
                    anyBad = true;
                    SellBoxMod.LOGGER.warn("Extras: skipped invalid player entry {}", e.getKey());
                }
            }
            if (anyBad) backupCorrupt(dataPath, false);
            PLAYERS.clear();
            PLAYERS.putAll(loaded);
            SellBoxMod.LOGGER.info("Extras: loaded data for {} players", loaded.size());
        } catch (Throwable t) {
            SellBoxMod.LOGGER.error("Failed to load sellbox_extras_players.json", t);
            // 整份文件坏掉时先挪走，至少给管理员留下可恢复的副本；再清空内存避免串档
            backupCorrupt(dataPath, true);
            PLAYERS.clear();
        }
    }

    /** 数据文件损坏时备份；move=true 时同时把原文件挪走，避免下次保存覆盖它 */
    private static void backupCorrupt(Path path, boolean move) {
        try {
            Path backup = path.resolveSibling(path.getFileName() + ".corrupt-" + System.currentTimeMillis());
            if (move) Files.move(path, backup);
            else Files.copy(path, backup);
            SellBoxMod.LOGGER.warn("Extras: backed up corrupted data file to {}", backup.getFileName());
        } catch (Throwable t) {
            SellBoxMod.LOGGER.error("Extras: failed to back up corrupted data file", t);
        }
    }

    public static void save() {
        if (dataPath == null) return;
        try {
            Files.createDirectories(dataPath.getParent());
            JsonObject root = new JsonObject();
            for (var e : PLAYERS.entrySet()) {
                root.add(e.getKey().toString(), e.getValue().toJson());
            }
            Files.writeString(dataPath, GSON.toJson(root), StandardCharsets.UTF_8);
            dirty = false;
            lastSaveMs = System.currentTimeMillis();
            if (ExtrasConfigFile.isDirty()) ExtrasConfigFile.save();
        } catch (Throwable t) {
            SellBoxMod.LOGGER.error("Failed to save sellbox_extras_players.json", t);
        }
    }

    /** 每 10 秒最多落盘一次（由服务端 tick 调用） */
    public static void tickSave(MinecraftServer server) {
        purgeOldMailsIfDue();
        if (!dirty) return;
        long now = System.currentTimeMillis();
        if (now - lastSaveMs < 10_000L) return;
        save();
    }

    /**
     * 每小时检查一次：把所有玩家的过期邮件清掉。
     * <p>有未领取附件的邮件不会被删（见 {@link ExtrasData#purgeOldMails(long)}）。
     */
    public static void purgeOldMailsIfDue() {
        long now = System.currentTimeMillis();
        if (now - lastPurgeMs < ExtrasConfig.MAIL_PURGE_INTERVAL_MS) return;
        lastPurgeMs = now;
        int total = 0;
        for (ExtrasData d : PLAYERS.values()) {
            if (d.purgeOldMails(now) > 0) {
                total++;
                dirty = true;
            }
        }
        if (total > 0) {
            SellBoxMod.LOGGER.info("[SellBox] 自动清理了 {} 位玩家的过期邮件", total);
        }
    }

    /** 全服立即清理一次（重启/手动指令用） */
    public static int purgeAllNow() {
        long now = System.currentTimeMillis();
        int n = 0;
        for (ExtrasData d : PLAYERS.values()) n += d.purgeOldMails(now);
        if (n > 0) dirty = true;
        return n;
    }

    /** 玩家退出时把该玩家的数据落盘 */
    public static void flushOnLogout() {
        if (dirty) save();
    }

    public static int loadedCount() {
        return PLAYERS.size();
    }

    /** 存档里所有已知玩家 UUID（群发用，含离线） */
    public static java.util.Set<UUID> allIds() {
        return new java.util.LinkedHashSet<>(PLAYERS.keySet());
    }
}
