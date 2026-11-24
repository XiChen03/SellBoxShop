package com.sellbox.mod.extras;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sellbox.mod.SellBoxMod;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扩展模块的服务端配置：转换表 + 兑换码 + 新玩家邮件 + 签到规则 + 开箱箱体。
 * <p>
 * 单独一个文件 {@code config/SellBox/sellbox_extras.json}，和玩家数据（{@code sellbox_extras_players.json}）
 * 分开，方便管理员直接编辑/备份。
 */
public final class ExtrasConfigFile {
    private ExtrasConfigFile() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path configPath;
    private static volatile boolean dirty = false;

    /** 转换表：物品 ID → 单件价值（金币） */
    private static final Map<String, Long> exchangeRates = new LinkedHashMap<>();
    /** 是否允许从余额换出货币物品（默认关闭） */
    private static boolean exchangeOut = false;
    /** 拾取/获得转换表物品时自动换入（默认开启） */
    private static boolean autoExchange = true;

    // ── 扫地女仆 ──
    private static boolean sweeperEnabled = true;
    private static int sweeperInterval = 600;
    private static int sweeperOverload = 640;
    private static int sweeperDustbins = 8;
    private static final List<String> sweeperBlacklist = new ArrayList<>();
    private static final List<String> sweeperWhitelist = new ArrayList<>();
    private static final List<String> sweeperExtraEntities = new ArrayList<>();
    private static String sweeperChatMessage = "[扫地女仆]：已清理，若有遗失请自行打开垃圾桶查找。";
    private static String sweeperCountdownMessage = "[扫地女仆]：我将在 %s 秒后打扫地板！";
    private static String sweeperAfterMessage = "[扫地女仆]：清理了 %s 个掉落物品和 %s 个不必要的实体。";
    /** 兑换码：code → 兑换码定义 */
    private static final Map<String, RedeemCode> redeemCodes = new ConcurrentHashMap<>();

    /** 新玩家首次上线时自动推送的邮件（空 = 不推送） */
    private static final List<DefaultMail> welcomeMails = new ArrayList<>();

    /** 签到奖励规则 */
    private static final SigninConfig signin = new SigninConfig();

    /** 开箱箱体（有序，客户端按顺序分页） */
    private static final List<CrateSet> crates = new ArrayList<>();

    // ════════════════════════════════════════════
    //  物品序列化适配（两端唯一差异集中在这两个方法里）
    // ════════════════════════════════════════════

    public static JsonObject writeItem(ItemStack stack) {
        return ExtrasItems.write(stack);
    }

    public static ItemStack readItem(JsonObject o) {
        return ExtrasItems.read(o);
    }

    // ════════════════════════════════════════════
    //  数据结构
    // ════════════════════════════════════════════

    /** 一条「默认邮件」定义（新玩家欢迎邮件等） */
    public static final class DefaultMail {
        public String title = "";
        public String content = "";
        public long money = 0L;
        public final List<ItemStack> items = new ArrayList<>();

        public boolean isEmpty() {
            return (title == null || title.isEmpty())
                    && (content == null || content.isEmpty())
                    && money <= 0 && items.isEmpty();
        }
    }

    /** 一个兑换码 */
    public static final class RedeemCode {
        public String code = "";
        public String title = "";
        public long money = 0L;
        public final List<ItemStack> items = new ArrayList<>();
        public int maxUses = -1;
        public int uses = 0;
        public long expireAt = 0L;
        public boolean oncePerPlayer = true;
        public final Set<String> claimedBy = new LinkedHashSet<>();

        public boolean expired() {
            return expireAt > 0 && System.currentTimeMillis() > expireAt;
        }

        public boolean usable() {
            if (expired()) return false;
            return maxUses < 0 || uses < maxUses;
        }
    }

    /**
     * 签到奖励规则。
     * <ul>
     *   <li>{@code mode="linear"}：第 n 天 = base + step × (min(n, cap) - 1)</li>
     *   <li>{@code mode="table"}：查 {@link #exact}，没配置的天数退回 base</li>
     * </ul>
     * {@link #milestones} 是「固定某天额外给钱/给物品」，两种模式都会叠加。
     */
    public static final class SigninConfig {
        public String mode = "linear";
        public long base = 100L;
        public long step = 20L;
        public int cap = 10;
        /** table 模式：连续天数 → 金币 */
        public final Map<Integer, Long> exact = new LinkedHashMap<>();
        /** 固定某天的额外奖励（可带物品） */
        public final List<Milestone> milestones = new ArrayList<>();

        /** 第 streak 天的基础金币 */
        public long rewardFor(int streak) {
            if (streak <= 0) return 0L;
            if ("table".equals(mode)) {
                Long v = exact.get(streak);
                return v == null ? base : v;
            }
            int capped = Math.max(1, Math.min(streak, Math.max(1, cap)));
            return base + step * (capped - 1);
        }

        public Milestone milestoneFor(int streak) {
            for (Milestone m : milestones) if (m.day == streak) return m;
            return null;
        }
    }

    /** 一条里程碑：连续第 day 天额外给 money + items */
    public static final class Milestone {
        public int day = 0;
        public long money = 0L;
        public final List<ItemStack> items = new ArrayList<>();
    }

    /** 箱子里的一个奖品 */
    public static final class CrateItem {
        public ItemStack stack = ItemStack.EMPTY;
        /** 权重百分比（0–100，同箱内按总和归一化） */
        public double chance = 1.0;
        /** 品质：common / rare / epic / legendary */
        public String rarity = "common";
    }

    /** 一个箱体 */
    public static final class CrateSet {
        public String id = "";
        public String name = "";
        public long price = 100L;
        public final List<CrateItem> items = new ArrayList<>();
        /** 保底品质：累计多少次没出到该品质及以上时强制出一件（common = 不启用） */
        public String pityRarity = "common";
        /** 保底次数，0 = 关闭 */
        public int pityCount = 0;
        /** 箱子图标（空 = 显示“开箱”文字） */
        public ItemStack icon = ItemStack.EMPTY;

        public double totalWeight() {
            double sum = 0;
            for (CrateItem it : items) if (!it.stack.isEmpty()) sum += Math.max(0, it.chance);
            return sum;
        }

        /** 保底是否启用 */
        public boolean pityEnabled() {
            return pityCount > 0 && rarityRank(pityRarity) > 0;
        }
    }

    // ════════════════════════════════════════════
    //  生命周期
    // ════════════════════════════════════════════

    public static void init(Path configDir) {
        configPath = configDir.resolve("SellBox").resolve("sellbox_extras.json");
        load();
    }

    public static void initPerWorld(Path worldDir) {
        configPath = worldDir.resolve("config").resolve("SellBox").resolve("sellbox_extras.json");
        load();
    }

    private static void load() {
        exchangeRates.clear();
        redeemCodes.clear();
        welcomeMails.clear();
        crates.clear();
        resetSignin();
        resetSweeperDefaults();
        if (configPath == null || !Files.exists(configPath)) return;
        try {
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            exchangeOut = root.has("exchangeOut") && root.get("exchangeOut").getAsBoolean();
            autoExchange = !root.has("autoExchange") || root.get("autoExchange").getAsBoolean();
            loadSweeper(root);
            if (root.has("exchange")) {
                for (var e : root.getAsJsonObject("exchange").entrySet()) {
                    exchangeRates.put(e.getKey(), e.getValue().getAsLong());
                }
            }
            if (root.has("welcomeMails")) {
                for (var e : root.getAsJsonArray("welcomeMails")) {
                    JsonObject o = e.getAsJsonObject();
                    DefaultMail dm = new DefaultMail();
                    dm.title = o.has("title") ? o.get("title").getAsString() : "";
                    dm.content = o.has("content") ? o.get("content").getAsString() : "";
                    dm.money = o.has("money") ? o.get("money").getAsLong() : 0L;
                    if (o.has("items")) {
                        for (var it : o.getAsJsonArray("items")) {
                            ItemStack st = readItem(it.getAsJsonObject());
                            if (!st.isEmpty()) dm.items.add(st);
                        }
                    }
                    if (!dm.isEmpty()) welcomeMails.add(dm);
                }
            }
            if (root.has("redeemCodes")) {
                for (var e : root.getAsJsonArray("redeemCodes")) {
                    JsonObject o = e.getAsJsonObject();
                    RedeemCode c = new RedeemCode();
                    c.code = o.has("code") ? o.get("code").getAsString().trim().toUpperCase(Locale.ROOT) : "";
                    c.title = o.has("title") ? o.get("title").getAsString() : "";
                    c.money = o.has("money") ? o.get("money").getAsLong() : 0L;
                    c.maxUses = o.has("maxUses") ? o.get("maxUses").getAsInt() : -1;
                    c.uses = o.has("uses") ? o.get("uses").getAsInt() : 0;
                    c.expireAt = o.has("expireAt") ? o.get("expireAt").getAsLong() : 0L;
                    c.oncePerPlayer = !o.has("oncePerPlayer") || o.get("oncePerPlayer").getAsBoolean();
                    if (o.has("claimedBy")) {
                        for (var p : o.getAsJsonArray("claimedBy")) {
                            String raw = p.getAsString().trim();
                            try {
                                // 统一成标准小写 UUID，兼容手工配置的大写形式
                                c.claimedBy.add(UUID.fromString(raw).toString());
                            } catch (Throwable ignored) {
                                c.claimedBy.add(raw.toLowerCase(Locale.ROOT));
                            }
                        }
                    }
                    if (o.has("items")) {
                        for (var it : o.getAsJsonArray("items")) {
                            ItemStack s = readItem(it.getAsJsonObject());
                            if (!s.isEmpty()) c.items.add(s);
                        }
                    }
                    if (!c.code.isEmpty()) redeemCodes.put(c.code, c);
                }
            }
            if (root.has("signin")) loadSignin(root.getAsJsonObject("signin"));
            if (root.has("crates")) {
                for (var e : root.getAsJsonArray("crates")) {
                    JsonObject o = e.getAsJsonObject();
                    CrateSet cs = new CrateSet();
                    cs.id = o.has("id") ? o.get("id").getAsString() : "";
                    cs.name = o.has("name") ? o.get("name").getAsString() : cs.id;
                    cs.price = o.has("price") ? o.get("price").getAsLong() : 100L;
                    cs.price = Math.max(0L, Math.min(ExtrasConfig.CRATE_MAX_PRICE, cs.price));
                    cs.pityRarity = normalizeRarity(o.has("pityRarity") ? o.get("pityRarity").getAsString() : "common");
                    cs.pityCount = Math.max(0, Math.min(10_000, o.has("pityCount") ? o.get("pityCount").getAsInt() : 0));
                    if (o.has("icon")) cs.icon = readItem(o.getAsJsonObject("icon"));
                    if (o.has("items")) {
                        for (var it : o.getAsJsonArray("items")) {
                            JsonObject io = it.getAsJsonObject();
                            CrateItem ci = new CrateItem();
                            ci.chance = io.has("chance") ? io.get("chance").getAsDouble() : 1.0;
                            if (!Double.isFinite(ci.chance)) ci.chance = 1.0;
                            ci.chance = Math.max(0.01, Math.min(100.0, ci.chance));
                            ci.rarity = normalizeRarity(io.has("rarity") ? io.get("rarity").getAsString() : "common");
                            if (io.has("stack")) ci.stack = readItem(io.getAsJsonObject("stack"));
                            if (!ci.stack.isEmpty()) cs.items.add(ci);
                        }
                    }
                    sortCrateItems(cs);
                    if (!cs.id.isEmpty()) crates.add(cs);
                }
            }
            SellBoxMod.LOGGER.info("Extras: loaded {} exchange rates, {} redeem codes, {} crates",
                    exchangeRates.size(), redeemCodes.size(), crates.size());
        } catch (Throwable t) {
            SellBoxMod.LOGGER.error("Failed to load sellbox_extras.json", t);
            // 保留损坏文件副本，避免随后的 save() 用默认值把它覆盖掉
            try {
                Path backup = configPath.resolveSibling(configPath.getFileName() + ".corrupt-" + System.currentTimeMillis());
                Files.copy(configPath, backup);
                SellBoxMod.LOGGER.warn("Extras: backed up corrupted config to {}", backup.getFileName());
            } catch (Throwable ignored) {
            }
        }
    }

    private static void resetSignin() {
        signin.mode = "linear";
        signin.base = ExtrasConfig.SIGNIN_BASE_REWARD;
        signin.step = ExtrasConfig.SIGNIN_STREAK_STEP;
        signin.cap = ExtrasConfig.SIGNIN_STREAK_CAP;
        signin.exact.clear();
        signin.milestones.clear();
        // 未配置时用内置默认里程碑
        for (var e : ExtrasConfig.SIGNIN_MILESTONES.entrySet()) {
            Milestone m = new Milestone();
            m.day = e.getKey();
            m.money = e.getValue();
            signin.milestones.add(m);
        }
        signin.milestones.sort(Comparator.comparingInt(m -> m.day));
    }

    private static void loadSignin(JsonObject o) {
        signin.mode = o.has("mode") && "table".equals(o.get("mode").getAsString()) ? "table" : "linear";
        signin.base = Math.max(0L, o.has("base") ? o.get("base").getAsLong() : ExtrasConfig.SIGNIN_BASE_REWARD);
        signin.step = Math.max(0L, o.has("step") ? o.get("step").getAsLong() : ExtrasConfig.SIGNIN_STREAK_STEP);
        signin.cap = Math.max(1, o.has("cap") ? o.get("cap").getAsInt() : ExtrasConfig.SIGNIN_STREAK_CAP);
        signin.exact.clear();
        if (o.has("exact")) {
            for (var e : o.getAsJsonObject("exact").entrySet()) {
                try {
                    long money = Math.max(0L, e.getValue().getAsLong());
                    if (money > 0) signin.exact.put(Integer.parseInt(e.getKey()), money);
                } catch (Throwable ignored) {}
            }
        }
        signin.milestones.clear();
        if (o.has("milestones")) {
            for (var e : o.getAsJsonArray("milestones")) {
                JsonObject mo = e.getAsJsonObject();
                Milestone m = new Milestone();
                m.day = mo.has("day") ? mo.get("day").getAsInt() : 0;
                m.money = Math.max(0L, mo.has("money") ? mo.get("money").getAsLong() : 0L);
                if (mo.has("items")) {
                    for (var it : mo.getAsJsonArray("items")) {
                        ItemStack s = readItem(it.getAsJsonObject());
                        if (!s.isEmpty()) m.items.add(s);
                    }
                }
                if (m.day > 0) signin.milestones.add(m);
            }
            signin.milestones.sort(Comparator.comparingInt(m -> m.day));
        }
    }

    public static void save() {
        if (configPath == null) return;
        try {
            Files.createDirectories(configPath.getParent());
            JsonObject root = new JsonObject();
            for (CrateSet c : crates) sortCrateItems(c);

            JsonObject ex = new JsonObject();
            for (var e : exchangeRates.entrySet()) ex.addProperty(e.getKey(), e.getValue());
            root.add("exchange", ex);
            root.addProperty("exchangeOut", exchangeOut);
            root.addProperty("autoExchange", autoExchange);
            saveSweeper(root);

            JsonArray codes = new JsonArray();
            for (RedeemCode c : redeemCodes.values()) {
                JsonObject o = new JsonObject();
                o.addProperty("code", c.code);
                o.addProperty("title", c.title);
                o.addProperty("money", c.money);
                o.addProperty("maxUses", c.maxUses);
                o.addProperty("uses", c.uses);
                o.addProperty("expireAt", c.expireAt);
                o.addProperty("oncePerPlayer", c.oncePerPlayer);
                JsonArray claimed = new JsonArray();
                for (String u : c.claimedBy) claimed.add(u);
                o.add("claimedBy", claimed);
                JsonArray items = new JsonArray();
                for (ItemStack s : c.items) items.add(writeItem(s));
                o.add("items", items);
                codes.add(o);
            }
            root.add("redeemCodes", codes);

            JsonArray welcomes = new JsonArray();
            for (DefaultMail dm : welcomeMails) {
                JsonObject o = new JsonObject();
                o.addProperty("title", dm.title);
                o.addProperty("content", dm.content);
                o.addProperty("money", dm.money);
                JsonArray items = new JsonArray();
                for (ItemStack s : dm.items) items.add(writeItem(s));
                o.add("items", items);
                welcomes.add(o);
            }
            root.add("welcomeMails", welcomes);

            root.add("signin", saveSignin());

            JsonArray cs = new JsonArray();
            for (CrateSet c : crates) {
                JsonObject o = new JsonObject();
                o.addProperty("id", c.id);
                o.addProperty("name", c.name);
                o.addProperty("price", c.price);
                o.addProperty("pityRarity", c.pityRarity);
                o.addProperty("pityCount", c.pityCount);
                if (c.icon != null && !c.icon.isEmpty()) o.add("icon", writeItem(c.icon));
                JsonArray its = new JsonArray();
                for (CrateItem ci : c.items) {
                    JsonObject io = new JsonObject();
                    io.addProperty("chance", ci.chance);
                    io.addProperty("rarity", ci.rarity);
                    io.add("stack", writeItem(ci.stack));
                    its.add(io);
                }
                o.add("items", its);
                cs.add(o);
            }
            root.add("crates", cs);

            Files.writeString(configPath, GSON.toJson(root), StandardCharsets.UTF_8);
            dirty = false;
        } catch (Throwable t) {
            SellBoxMod.LOGGER.error("Failed to save sellbox_extras.json", t);
        }
    }

    private static JsonObject saveSignin() {
        JsonObject o = new JsonObject();
        o.addProperty("mode", signin.mode);
        o.addProperty("base", signin.base);
        o.addProperty("step", signin.step);
        o.addProperty("cap", signin.cap);
        JsonObject ex = new JsonObject();
        for (var e : signin.exact.entrySet()) ex.addProperty(String.valueOf(e.getKey()), e.getValue());
        o.add("exact", ex);
        JsonArray ms = new JsonArray();
        for (Milestone m : signin.milestones) {
            JsonObject mo = new JsonObject();
            mo.addProperty("day", m.day);
            mo.addProperty("money", m.money);
            JsonArray items = new JsonArray();
            for (ItemStack s : m.items) items.add(writeItem(s));
            mo.add("items", items);
            ms.add(mo);
        }
        o.add("milestones", ms);
        return o;
    }

    // ════════════════════════════════════════════
    //  新玩家欢迎邮件
    // ════════════════════════════════════════════

    public static List<DefaultMail> getWelcomeMails() {
        return Collections.unmodifiableList(welcomeMails);
    }

    public static void addWelcomeMail(DefaultMail dm) {
        if (dm == null || dm.isEmpty()) return;
        welcomeMails.add(dm);
        markDirty();
    }

    public static void clearWelcomeMails() {
        welcomeMails.clear();
        markDirty();
    }

    public static void markDirty() { dirty = true; }

    public static boolean isDirty() { return dirty; }

    // ════════════════════════════════════════════
    //  转换表
    // ════════════════════════════════════════════

    public static Map<String, Long> getExchangeRates() {
        return Collections.unmodifiableMap(exchangeRates);
    }

    public static boolean isExchangeOut() {
        return exchangeOut;
    }

    public static void setExchangeOut(boolean enabled) {
        exchangeOut = enabled;
        markDirty();
    }

    public static boolean isAutoExchange() {
        return autoExchange;
    }

    public static void setAutoExchange(boolean enabled) {
        autoExchange = enabled;
        markDirty();
    }

    // ── 扫地女仆配置 ──

    private static void resetSweeperDefaults() {
        sweeperEnabled = true;
        sweeperInterval = 600;
        sweeperOverload = 640;
        sweeperDustbins = 8;
        sweeperBlacklist.clear();
        sweeperWhitelist.clear();
        sweeperExtraEntities.clear();
        sweeperBlacklist.add("minecraft:cobblestone");
        sweeperBlacklist.add("minecraft:sand");
        sweeperWhitelist.add("minecraft:nether_star");
        sweeperExtraEntities.add("minecraft:arrow");
        sweeperExtraEntities.add("minecraft:spectral_arrow");
        sweeperChatMessage = "[扫地女仆]：已清理，若有遗失请自行打开垃圾桶查找。";
        sweeperCountdownMessage = "[扫地女仆]：我将在 %s 秒后打扫地板！";
        sweeperAfterMessage = "[扫地女仆]：清理了 %s 个掉落物品和 %s 个不必要的实体。";
    }

    private static void loadSweeper(JsonObject root) {
        resetSweeperDefaults();
        if (!root.has("sweeper") || !root.get("sweeper").isJsonObject()) return;
        JsonObject s = root.getAsJsonObject("sweeper");
        sweeperEnabled = !s.has("enabled") || s.get("enabled").getAsBoolean();
        sweeperInterval = Math.max(0, Math.min(360000, s.has("interval") ? s.get("interval").getAsInt() : 600));
        sweeperOverload = Math.max(1, s.has("overload") ? s.get("overload").getAsInt() : 640);
        sweeperDustbins = Math.max(1, Math.min(64, s.has("dustbins") ? s.get("dustbins").getAsInt() : 8));
        readStringList(s, "blacklist", sweeperBlacklist);
        readStringList(s, "whitelist", sweeperWhitelist);
        readStringList(s, "extraEntities", sweeperExtraEntities);
        sweeperWhitelist.remove("minecraft:heavy_core");
        if (sweeperBlacklist.isEmpty() && sweeperWhitelist.isEmpty() && sweeperExtraEntities.isEmpty()) {
            resetSweeperDefaults();
        }
        SellBoxMod.LOGGER.info("Sweeper config: black={} white={} entities={}",
                sweeperBlacklist.size(), sweeperWhitelist.size(), sweeperExtraEntities.size());
        sweeperChatMessage = s.has("chatMessage") ? s.get("chatMessage").getAsString() : "[扫地女仆]：已清理，若有遗失请自行打开垃圾桶查找。";
        if ("[扫地女仆]：垃圾箱已更新 ".equals(sweeperChatMessage)) {
            sweeperChatMessage = "[扫地女仆]：已清理，若有遗失请自行打开垃圾桶查找。";
        }
        sweeperCountdownMessage = s.has("countdownMessage") ? s.get("countdownMessage").getAsString() : "[扫地女仆]：我将在 %s 秒后打扫地板！";
        sweeperAfterMessage = s.has("afterMessage") ? s.get("afterMessage").getAsString() : "[扫地女仆]：清理了 %s 个掉落物品和 %s 个不必要的实体。";
    }

    private static void readStringList(JsonObject o, String key, List<String> out) {
        if (!o.has(key) || !o.get(key).isJsonArray()) return;
        out.clear();
        for (var e : o.getAsJsonArray(key)) {
            String v = e.getAsString();
            if (v != null && !v.isEmpty()) out.add(v);
        }
    }

    private static void saveSweeper(JsonObject root) {
        JsonObject s = new JsonObject();
        s.addProperty("enabled", sweeperEnabled);
        s.addProperty("interval", sweeperInterval);
        s.addProperty("overload", sweeperOverload);
        s.addProperty("dustbins", sweeperDustbins);
        s.add("blacklist", stringArray(sweeperBlacklist));
        s.add("whitelist", stringArray(sweeperWhitelist));
        s.add("extraEntities", stringArray(sweeperExtraEntities));
        s.addProperty("chatMessage", sweeperChatMessage);
        s.addProperty("countdownMessage", sweeperCountdownMessage);
        s.addProperty("afterMessage", sweeperAfterMessage);
        root.add("sweeper", s);
    }

    private static JsonArray stringArray(List<String> list) {
        JsonArray a = new JsonArray();
        for (String v : list) a.add(v);
        return a;
    }

    public static boolean isSweeperEnabled() { return sweeperEnabled; }
    public static int getSweeperInterval() { return sweeperInterval; }
    public static int getSweeperOverload() { return sweeperOverload; }
    public static int getSweeperDustbins() { return sweeperDustbins; }
    public static List<String> getSweeperBlacklist() { return sweeperBlacklist; }
    public static List<String> getSweeperWhitelist() { return sweeperWhitelist; }
    public static List<String> getSweeperExtraEntities() { return sweeperExtraEntities; }
    public static void setSweeperEnabled(boolean v) { sweeperEnabled = v; markDirty(); }
    public static void setSweeperInterval(int v) { sweeperInterval = Math.max(0, Math.min(360000, v)); markDirty(); }
    public static void setSweeperOverload(int v) { sweeperOverload = Math.max(1, v); markDirty(); }
    public static void setSweeperDustbins(int v) { sweeperDustbins = Math.max(1, Math.min(64, v)); markDirty(); }
    public static String getSweeperChatMessage() { return sweeperChatMessage; }
    public static void setSweeperChatMessage(String v) { sweeperChatMessage = v == null || v.isBlank() ? "" : v.trim(); markDirty(); }
    public static String getSweeperCountdownMessage() { return sweeperCountdownMessage; }
    public static void setSweeperCountdownMessage(String v) { sweeperCountdownMessage = v == null || v.isBlank() ? "[扫地女仆]：我将在 %s 秒后打扫地板！" : v.trim(); markDirty(); }
    public static String getSweeperAfterMessage() { return sweeperAfterMessage; }
    public static void setSweeperAfterMessage(String v) { sweeperAfterMessage = v == null || v.isBlank() ? "[扫地女仆]：清理了 %s 个掉落物品和 %s 个不必要的实体。" : v.trim(); markDirty(); }
    public static void sweepListAdd(List<String> list, String id) { if (id != null && !id.isEmpty() && !list.contains(id)) { list.add(id); markDirty(); } }
    public static void sweepListRemove(List<String> list, String id) { if (list.remove(id)) markDirty(); }

    public static long rateOf(String itemId) {
        Long v = exchangeRates.get(itemId);
        return v == null ? 0L : v;
    }

    public static void setRate(String itemId, long value) {
        if (itemId == null || itemId.isEmpty()) return;
        if (value <= 0) exchangeRates.remove(itemId);
        else exchangeRates.put(itemId, value);
        markDirty();
    }

    public static void removeRate(String itemId) {
        exchangeRates.remove(itemId);
        markDirty();
    }

    // ════════════════════════════════════════════
    //  签到规则
    // ════════════════════════════════════════════

    public static SigninConfig signin() {
        return signin;
    }

    public static void setSigninMode(String mode) {
        signin.mode = "table".equals(mode) ? "table" : "linear";
        markDirty();
    }

    public static void setSigninNumbers(long base, long step, int cap) {
        signin.base = Math.max(0L, base);
        signin.step = Math.max(0L, step);
        signin.cap = Math.max(1, cap);
        markDirty();
    }

    public static void setSigninExact(int day, long money) {
        if (day <= 0) return;
        if (money <= 0) signin.exact.remove(day);
        else signin.exact.put(day, money);
        markDirty();
    }

    /** 加/更新一条里程碑（同一天只留一条，物品会追加） */
    public static Milestone putMilestone(int day, long money, ItemStack extra) {
        if (day <= 0) return null;
        Milestone m = signin.milestoneFor(day);
        if (m == null) {
            m = new Milestone();
            m.day = day;
            signin.milestones.add(m);
            signin.milestones.sort(Comparator.comparingInt(x -> x.day));
        }
        if (money > 0) m.money = money;
        if (extra != null && !extra.isEmpty() && m.items.size() < 9) m.items.add(extra.copy());
        markDirty();
        return m;
    }

    public static void removeMilestone(int day) {
        signin.milestones.removeIf(m -> m.day == day);
        markDirty();
    }

    // ════════════════════════════════════════════
    //  兑换码
    // ════════════════════════════════════════════

    public static Map<String, RedeemCode> getRedeemCodes() {
        return Collections.unmodifiableMap(redeemCodes);
    }

    public static RedeemCode codeOf(String code) {
        return code == null ? null : redeemCodes.get(code.trim().toUpperCase(Locale.ROOT));
    }

    public static void putCode(RedeemCode code) {
        if (code == null || code.code == null || code.code.isEmpty()) return;
        code.code = code.code.trim().toUpperCase(Locale.ROOT);
        redeemCodes.put(code.code, code);
        markDirty();
    }

    public static void removeCode(String code) {
        if (code == null) return;
        redeemCodes.remove(code.trim().toUpperCase(Locale.ROOT));
        markDirty();
    }

    // ════════════════════════════════════════════
    //  开箱
    // ════════════════════════════════════════════

    public static List<CrateSet> getCrates() {
        return Collections.unmodifiableList(crates);
    }

    public static CrateSet crateOf(String id) {
        if (id == null) return null;
        for (CrateSet c : crates) if (c.id.equals(id)) return c;
        return null;
    }

    public static CrateSet createCrate(String name) {
        String base = (name == null || name.trim().isEmpty()) ? "crate" : name.trim();
        String id = base;
        int n = 1;
        while (crateOf(id) != null) id = base + (++n);
        CrateSet c = new CrateSet();
        c.id = id;
        c.name = base;
        c.price = 100L;
        crates.add(c);
        markDirty();
        return c;
    }

    public static void removeCrate(String id) {
        crates.removeIf(c -> c.id.equals(id));
        markDirty();
    }

    public static void setCratePrice(String id, long price) {
        CrateSet c = crateOf(id);
        if (c == null) return;
        c.price = Math.max(0L, Math.min(ExtrasConfig.CRATE_MAX_PRICE, price));
        markDirty();
    }

    /** 改箱体显示名（id 保持不变，保底进度/存档键不会被重置） */
    public static void setCrateName(String id, String name) {
        CrateSet c = crateOf(id);
        if (c == null) return;
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return;
        c.name = n.length() > 32 ? n.substring(0, 32) : n;
        markDirty();
    }

    public static boolean addCrateItem(String id, ItemStack stack, double chance, String rarity) {
        CrateSet c = crateOf(id);
        if (c == null || stack == null || stack.isEmpty() || c.items.size() >= ExtrasConfig.CRATE_MAX_ITEMS) return false;
        CrateItem ci = new CrateItem();
        ci.stack = stack.copy();
        ci.chance = Double.isFinite(chance) ? Math.max(0.01, Math.min(100.0, chance)) : 1.0;
        ci.rarity = normalizeRarity(rarity);
        c.items.add(ci);
        sortCrateItems(c);
        markDirty();
        return true;
    }

    /**
     * 就地修改奖池某一项的权重 / 品质。
     *
     * @param setChance 是否改权重
     * @param setRarity 是否改品质
     * @return false = 箱体或下标无效
     */
    public static boolean setCrateItem(String id, int index,
                                       boolean setChance, double chance,
                                       boolean setRarity, String rarity) {
        CrateSet c = crateOf(id);
        if (c == null || index < 0 || index >= c.items.size()) return false;
        CrateItem ci = c.items.get(index);
        if (setChance) ci.chance = Double.isFinite(chance) ? Math.max(0.01, Math.min(100.0, chance)) : 1.0;
        if (setRarity) {
            ci.rarity = normalizeRarity(rarity);
            sortCrateItems(c);
        }
        markDirty();
        return true;
    }

    public static void removeCrateItem(String id, int index) {
        CrateSet c = crateOf(id);
        if (c == null || index < 0 || index >= c.items.size()) return;
        c.items.remove(index);
        markDirty();
    }

    /** 奖池始终按品质从高到低（红金紫蓝绿白），同品质保持添加顺序 */
    private static void sortCrateItems(CrateSet c) {
        if (c == null) return;
        c.items.sort((a, b) -> Integer.compare(rarityRank(b.rarity), rarityRank(a.rarity)));
    }

    public static String normalizeRarity(String r) {
        if (r == null) return "common";
        return switch (r.toLowerCase(Locale.ROOT)) {
            case "uncommon", "rare", "epic", "legendary", "mythic" -> r.toLowerCase(Locale.ROOT);
            default -> "common";
        };
    }

    /** 品质档次（越大越稀有），不在表里的当 0 */
    public static int rarityRank(String r) {
        if (r == null) return 0;
        int i = ExtrasConfig.CRATE_RARITY_ORDER.indexOf(r.toLowerCase(Locale.ROOT));
        return Math.max(0, i);
    }

    /** 设置箱体保底规则（rarity 为 common 或 count<=0 即关闭） */
    public static void setCratePity(String id, String rarity, int count) {
        CrateSet c = crateOf(id);
        if (c == null) return;
        c.pityRarity = normalizeRarity(rarity);
        c.pityCount = Math.max(0, Math.min(10_000, count));
        markDirty();
    }

    /** 设置箱子图标 */
    public static boolean setCrateIcon(String id, ItemStack icon) {
        CrateSet c = crateOf(id);
        if (c == null || icon == null || icon.isEmpty()) return false;
        c.icon = icon.copy();
        markDirty();
        return true;
    }

    /** 奖池里所有满足「品质 ≥ r」的奖品 */
    public static List<CrateItem> itemsAtLeast(CrateSet c, String rarity) {
        List<CrateItem> out = new ArrayList<>();
        if (c == null) return out;
        int need = rarityRank(rarity);
        for (CrateItem it : c.items) {
            if (it.stack == null || it.stack.isEmpty()) continue;
            if (rarityRank(it.rarity) >= need) out.add(it);
        }
        return out;
    }

    /** 修改某个里程碑的金币（0 = 只留物品） */
    /** 改里程碑天数 + 金币；改天数时保留原本的物品 */
    public static boolean updateMilestone(int oldDay, int newDay, long money) {
        if (oldDay <= 0 || newDay <= 0) return false;
        Milestone m = signin.milestoneFor(oldDay);
        if (m == null) return false;
        Milestone target = signin.milestoneFor(newDay);
        if (target != null && target != m) {
            // 目标天数已存在：合并物品后删掉旧条目
            for (ItemStack it : m.items) {
                if (target.items.size() < 9) target.items.add(it.copy());
            }
            signin.milestones.remove(m);
            m = target;
        } else {
            m.day = newDay;
        }
        m.money = Math.max(0L, money);
        signin.milestones.sort(Comparator.comparingInt(x -> x.day));
        markDirty();
        return true;
    }

    public static void setMilestoneMoney(int day, long money) {
        Milestone m = signin.milestoneFor(day);
        if (m == null) return;
        m.money = Math.max(0L, money);
        markDirty();
    }

    /** 删掉某个里程碑里的第 index 件物品 */
    public static void removeMilestoneItem(int day, int index) {
        Milestone m = signin.milestoneFor(day);
        if (m == null || index < 0 || index >= m.items.size()) return;
        m.items.remove(index);
        markDirty();
    }
}
