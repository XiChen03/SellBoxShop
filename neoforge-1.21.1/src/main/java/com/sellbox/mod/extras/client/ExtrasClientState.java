package com.sellbox.mod.extras.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sellbox.mod.extras.ExtrasConfigFile;
import com.sellbox.mod.extras.ExtrasItems;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端持有的扩展模块状态（从服务端状态包解析而来）。
 * <p>
 * 只做「读」：界面从这里取数据渲染，操作全部发请求包给服务端。
 */
public final class ExtrasClientState {
    private ExtrasClientState() {}

    public static int balance = 0;

    // ── 签到 ──
    public static int streak = 0;
    public static int bestStreak = 0;
    public static boolean signedToday = false;
    public static long lastSignDay = 0;
    /** 已签到日期（epochDay），升序 */
    public static final List<Long> signedDays = new ArrayList<>();
    /** 本轮已领取的里程奖励 */
    public static final List<Integer> claimedMilestones = new ArrayList<>();

    // ── 邮箱 ──
    public static final List<MailView> mails = new ArrayList<>();

    // ── 兑换 ──
    public static final List<ExchangeView> exchange = new ArrayList<>();
    /** 管理员是否开启「余额换出货币物品」 */
    public static boolean exchangeOut = false;
    /** 管理员是否开启「拾取/获得时自动换入」 */
    public static boolean autoExchange = true;

    // ── 扫地女仆 ──
    public static boolean sweeperEnabled = true;
    public static int sweeperInterval = 600;
    public static int sweeperOverload = 640;
    public static int sweeperDustbins = 8;
    public static final List<String> sweeperBlacklist = new ArrayList<>();
    public static final List<String> sweeperWhitelist = new ArrayList<>();
    public static final List<String> sweeperExtraEntities = new ArrayList<>();
    public static String sweeperChatMessage = "[扫地女仆]：已清理，若有遗失请自行打开垃圾桶查找。";
    public static String sweeperCountdownMessage = "[扫地女仆]：我将在 %s 秒后打扫地板！";
    public static String sweeperAfterMessage = "[扫地女仆]：清理了 %s 个掉落物品和 %s 个不必要的实体。";

    // ── 好友 ──
    public static final List<FriendView> friends = new ArrayList<>();
    public static final List<FriendView> friendRequests = new ArrayList<>();

    /** 最近一次响应（用于错误提示） */
    // ── 管理员 ──
    /** 当前玩家是否有管理员权限（服务端下发的标记） */
    public static boolean admin = false;
    /** 兑换码列表（仅 admin 时下发） */
    public static final List<CodeView> codes = new ArrayList<>();
    /** 在线玩家名（仅 admin 时下发，发邮件选人用） */
    public static final List<String> onlinePlayers = new ArrayList<>();

    // ── 签到规则（所有人可见） ──
    public static String signinMode = "linear";
    public static long signinBase = 100L;
    public static long signinStep = 20L;
    public static int signinCap = 10;
    /** table 模式：连续天数 → 金币 */
    public static final List<DayReward> signinExact = new ArrayList<>();
    /** 固定某天的额外奖励 */
    public static final List<MileView> signinMiles = new ArrayList<>();

    // ── 开箱 ──
    public static final List<CrateView> crates = new ArrayList<>();
    /** 最近一次开箱结果（动画播放中非 null） */
    public static CrateResult lastCrateResult;
    /** 最近一次连开结果（汇总面板显示中非 null） */
    public static CrateBatchResult lastCrateBatch;

    public static String lastError = "";
    public static String lastMessage = "";
    /** 最近一次响应的动作 / 是否成功，供界面做「成功后再清空/关闭表单」 */
    public static String lastAction = "";
    public static boolean lastOk = true;

    public static void apply(String json) {
        if (json == null || json.isEmpty()) return;
        // 快照当前状态：状态包解析失败时整体回滚，避免留下半套空列表
        int oldBalance = balance, oldStreak = streak, oldBest = bestStreak, oldCap = signinCap;
        boolean oldSignedToday = signedToday, oldAdmin = admin;
        long oldLastSign = lastSignDay, oldBase = signinBase, oldStep = signinStep;
        String oldMode = signinMode;
        var oldSignedDays = new ArrayList<>(signedDays);
        var oldClaimed = new ArrayList<>(claimedMilestones);
        var oldMails = new ArrayList<>(mails);
        var oldExchange = new ArrayList<>(exchange);
        var oldFriends = new ArrayList<>(friends);
        var oldRequests = new ArrayList<>(friendRequests);
        var oldCodes = new ArrayList<>(codes);
        var oldOnline = new ArrayList<>(onlinePlayers);
        var oldExact = new ArrayList<>(signinExact);
        var oldMiles = new ArrayList<>(signinMiles);
        var oldCrates = new ArrayList<>(crates);
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (o.has("balance")) balance = o.get("balance").getAsInt();

            if (o.has("sign")) {
                JsonObject s = o.getAsJsonObject("sign");
                streak = s.has("streak") ? s.get("streak").getAsInt() : 0;
                bestStreak = s.has("best") ? s.get("best").getAsInt() : 0;
                signedToday = s.has("today") && s.get("today").getAsBoolean();
                lastSignDay = s.has("lastDay") ? s.get("lastDay").getAsLong() : 0L;
                signedDays.clear();
                if (s.has("days")) for (var e : s.getAsJsonArray("days")) signedDays.add(e.getAsLong());
                claimedMilestones.clear();
                if (s.has("milestones")) {
                    for (var e : s.getAsJsonArray("milestones")) claimedMilestones.add(e.getAsInt());
                }
            }

            mails.clear();
            if (o.has("mails")) {
                for (var e : o.getAsJsonArray("mails")) {
                    JsonObject m = e.getAsJsonObject();
                    MailView v = new MailView();
                    v.id = m.has("id") ? m.get("id").getAsString() : "";
                    v.title = m.has("title") ? m.get("title").getAsString() : "";
                    v.content = m.has("content") ? m.get("content").getAsString() : "";
                    v.kind = m.has("kind") ? m.get("kind").getAsString() : "system";
                    v.sender = m.has("sender") ? m.get("sender").getAsString() : "";
                    v.money = m.has("money") ? m.get("money").getAsLong() : 0L;
                    v.claimed = m.has("claimed") && m.get("claimed").getAsBoolean();
                    v.sentAt = m.has("sentAt") ? m.get("sentAt").getAsLong() : 0L;
                    v.transferAmount = m.has("transferAmount") ? m.get("transferAmount").getAsLong() : 0L;
                    v.record = m.has("record") && m.get("record").getAsBoolean();
                    v.payload = m.has("payload") ? m.get("payload").getAsString() : "";
                    v.fromServer = m.has("fromServer") && m.get("fromServer").getAsBoolean();
                    int itemTotal = 0;
                    if (m.has("items")) {
                        for (var it : m.getAsJsonArray("items")) {
                            JsonObject io = it.getAsJsonObject();
                            v.itemNames.add(io.has("name") ? io.get("name").getAsString() : "?");
                            itemTotal += io.has("count") ? Math.max(0, io.get("count").getAsInt()) : 1;
                        }
                    }
                    // 服务端下发的 count 是「邮件里的物品条数」，界面要显示的是总件数
                    v.itemCount = itemTotal > 0 ? itemTotal
                            : (m.has("count") ? m.get("count").getAsInt() : 0);
                    mails.add(v);
                }
            }

            exchangeOut = o.has("exchangeOut") && o.get("exchangeOut").getAsBoolean();
            autoExchange = !o.has("autoExchange") || o.get("autoExchange").getAsBoolean();
            if (o.has("sweeper") && o.get("sweeper").isJsonObject()) {
                var sw = o.getAsJsonObject("sweeper");
                sweeperEnabled = !sw.has("enabled") || sw.get("enabled").getAsBoolean();
                sweeperInterval = sw.has("interval") ? sw.get("interval").getAsInt() : 600;
                sweeperOverload = sw.has("overload") ? sw.get("overload").getAsInt() : 640;
                sweeperDustbins = sw.has("dustbins") ? sw.get("dustbins").getAsInt() : 8;
                readIds(sw, "blacklist", sweeperBlacklist);
                readIds(sw, "whitelist", sweeperWhitelist);
                readIds(sw, "extraEntities", sweeperExtraEntities);
                sweeperChatMessage = sw.has("chatMessage") ? sw.get("chatMessage").getAsString() : "[扫地女仆]：已清理，若有遗失请自行打开垃圾桶查找。";
                sweeperCountdownMessage = sw.has("countdownMessage") ? sw.get("countdownMessage").getAsString() : "[扫地女仆]：我将在 %s 秒后打扫地板！";
                sweeperAfterMessage = sw.has("afterMessage") ? sw.get("afterMessage").getAsString() : "[扫地女仆]：清理了 %s 个掉落物品和 %s 个不必要的实体。";
            }
            exchange.clear();
            if (o.has("exchange")) {
                for (var e : o.getAsJsonArray("exchange")) {
                    JsonObject x = e.getAsJsonObject();
                    ExchangeView v = new ExchangeView();
                    v.itemId = x.has("id") ? x.get("id").getAsString() : "";
                    v.name = x.has("name") ? x.get("name").getAsString() : "";
                    // 服务端名字可能包含自定义名/NBT 名，只有缺失时才用注册表名兜底
                    if (v.name.isEmpty() && !v.itemId.isEmpty()) v.name = itemName(v.itemId);
                    v.value = x.has("value") ? x.get("value").getAsLong() : 0L;
                    v.owned = x.has("owned") ? x.get("owned").getAsInt() : 0;
                    if (!v.itemId.isEmpty()) exchange.add(v);
                }
            }

            friends.clear();
            if (o.has("friends")) {
                for (var e : o.getAsJsonArray("friends")) {
                    JsonObject f = e.getAsJsonObject();
                    FriendView v = new FriendView();
                    v.uuid = f.has("uuid") ? f.get("uuid").getAsString() : "";
                    v.name = f.has("name") ? f.get("name").getAsString() : "";
                    v.online = f.has("online") && f.get("online").getAsBoolean();
                    friends.add(v);
                }
            }
            admin = o.has("admin") && o.get("admin").getAsBoolean();

            onlinePlayers.clear();
            if (o.has("online")) {
                for (var e : o.getAsJsonArray("online")) onlinePlayers.add(e.getAsString());
            }

            if (o.has("signinCfg")) {
                JsonObject s = o.getAsJsonObject("signinCfg");
                signinMode = s.has("mode") ? s.get("mode").getAsString() : "linear";
                signinBase = s.has("base") ? s.get("base").getAsLong() : 100L;
                signinStep = s.has("step") ? s.get("step").getAsLong() : 20L;
                signinCap = s.has("cap") ? s.get("cap").getAsInt() : 10;
                signinExact.clear();
                if (s.has("exact")) {
                    for (var e : s.getAsJsonArray("exact")) {
                        JsonObject d = e.getAsJsonObject();
                        DayReward v = new DayReward();
                        v.day = d.has("day") ? d.get("day").getAsInt() : 0;
                        v.money = d.has("money") ? d.get("money").getAsLong() : 0L;
                        if (v.day > 0) signinExact.add(v);
                    }
                }
                signinMiles.clear();
                if (s.has("milestones")) {
                    for (var e : s.getAsJsonArray("milestones")) {
                        JsonObject d = e.getAsJsonObject();
                        MileView v = new MileView();
                        v.day = d.has("day") ? d.get("day").getAsInt() : 0;
                        v.money = d.has("money") ? d.get("money").getAsLong() : 0L;
                        if (d.has("items")) {
                            for (var it : d.getAsJsonArray("items")) v.items.add(it.getAsString());
                        }
                        if (v.day > 0) signinMiles.add(v);
                    }
                }
            }

            crates.clear();
            if (o.has("crates")) {
                for (var e : o.getAsJsonArray("crates")) {
                    JsonObject c = e.getAsJsonObject();
                    CrateView v = new CrateView();
                    v.id = c.has("id") ? c.get("id").getAsString() : "";
                    v.name = c.has("name") ? c.get("name").getAsString() : "";
                    v.price = c.has("price") ? c.get("price").getAsLong() : 0L;
                    v.pityRarity = c.has("pityRarity") ? c.get("pityRarity").getAsString() : "common";
                    v.pityCount = c.has("pityCount") ? c.get("pityCount").getAsInt() : 0;
                    v.pityNow = c.has("pityNow") ? c.get("pityNow").getAsInt() : 0;
                    if (c.has("icon")) v.icon = ExtrasConfigFile.readItem(c.getAsJsonObject("icon"));
                    if (c.has("items")) {
                        for (var it : c.getAsJsonArray("items")) {
                            JsonObject io = it.getAsJsonObject();
                            CrateItemView w = new CrateItemView();
                            w.id = io.has("id") ? io.get("id").getAsString() : "";
                            w.name = io.has("name") ? io.get("name").getAsString() : "";
                            if (w.name.isEmpty() && !w.id.isEmpty()) w.name = itemName(w.id);
                            w.count = io.has("count") ? io.get("count").getAsInt() : 1;
                            w.chance = io.has("chance") ? io.get("chance").getAsDouble() : 0;
                            w.rarity = io.has("rarity") ? io.get("rarity").getAsString() : "common";
                            if (!w.name.isEmpty()) v.items.add(w);
                        }
                    }
                    if (!v.id.isEmpty()) crates.add(v);
                }
            }

            codes.clear();
            if (o.has("codes")) {
                for (var e : o.getAsJsonArray("codes")) {
                    JsonObject c = e.getAsJsonObject();
                    CodeView v = new CodeView();
                    v.code = c.has("code") ? c.get("code").getAsString() : "";
                    v.title = c.has("title") ? c.get("title").getAsString() : "";
                    v.money = c.has("money") ? c.get("money").getAsLong() : 0L;
                    v.uses = c.has("uses") ? c.get("uses").getAsInt() : 0;
                    v.maxUses = c.has("maxUses") ? c.get("maxUses").getAsInt() : -1;
                    v.claimed = c.has("claimed") ? c.get("claimed").getAsInt() : 0;
                    v.expireAt = c.has("expireAt") ? c.get("expireAt").getAsLong() : 0L;
                    v.itemCount = c.has("itemCount") ? c.get("itemCount").getAsInt() : 0;
                    if (!v.code.isEmpty()) codes.add(v);
                }
            }

            friendRequests.clear();
        sweeperBlacklist.clear();
        sweeperWhitelist.clear();
        sweeperExtraEntities.clear();
            if (o.has("requests")) {
                for (var e : o.getAsJsonArray("requests")) {
                    JsonObject f = e.getAsJsonObject();
                    FriendView v = new FriendView();
                    v.uuid = f.has("uuid") ? f.get("uuid").getAsString() : "";
                    v.name = f.has("name") ? f.get("name").getAsString() : "";
                    friendRequests.add(v);
                }
            }
        } catch (Throwable ignored) {
            // 解析失败：把快照整体放回去
            balance = oldBalance; streak = oldStreak; bestStreak = oldBest; signinCap = oldCap;
            signedToday = oldSignedToday; admin = oldAdmin;
            lastSignDay = oldLastSign; signinBase = oldBase; signinStep = oldStep;
            signinMode = oldMode;
            signedDays.clear(); signedDays.addAll(oldSignedDays);
            claimedMilestones.clear(); claimedMilestones.addAll(oldClaimed);
            mails.clear(); mails.addAll(oldMails);
            exchange.clear(); exchange.addAll(oldExchange);
            friends.clear(); friends.addAll(oldFriends);
            friendRequests.clear(); friendRequests.addAll(oldRequests);
            codes.clear(); codes.addAll(oldCodes);
            onlinePlayers.clear(); onlinePlayers.addAll(oldOnline);
            signinExact.clear(); signinExact.addAll(oldExact);
            signinMiles.clear(); signinMiles.addAll(oldMiles);
            crates.clear(); crates.addAll(oldCrates);
        }
    }

    /**
     * 解析服务端响应。服务端只发翻译键 + 参数，本地化在客户端做，
     * 所以中英文客户端看到的提示都是各自的语言。
     */
    public static void applyResponse(String json) {
        lastAction = "";
        lastOk = false;
        if (json == null || json.isEmpty()) return;
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            boolean ok = !o.has("ok") || o.get("ok").getAsBoolean();
            lastAction = o.has("action") ? o.get("action").getAsString() : "";
            lastOk = ok;

            String key = o.has("msgKey") ? o.get("msgKey").getAsString() : "";
            Object[] args = new Object[0];
            if (o.has("msgArgs")) {
                var arr = o.getAsJsonArray("msgArgs");
                args = new Object[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    var el = arr.get(i);
                    args[i] = el.getAsJsonPrimitive().isNumber()
                            ? (Object) el.getAsLong()
                            : (Object) el.getAsString();
                }
            }

            String text = key.isEmpty() ? "" : net.minecraft.network.chat.Component
                    .translatable(key, args).getString();

            if (ok) {
                lastMessage = text;
                lastError = "";
                if (o.has("reel") && o.has("stopAt")) {
                    CrateResult r = new CrateResult();
                    r.crateId = o.has("crate") ? o.get("crate").getAsString() : "";
                    r.stopAt = o.get("stopAt").getAsInt();
                    r.startedAt = System.currentTimeMillis();
                    r.reel.clear();
                    for (var e : o.getAsJsonArray("reel")) {
                        JsonObject ro = e.getAsJsonObject();
                        ReelView v = new ReelView();
                        v.id = ro.has("id") ? ro.get("id").getAsString() : "";
                        v.name = ro.has("name") ? ro.get("name").getAsString() : "";
                        if (v.name.isEmpty() && !v.id.isEmpty()) v.name = itemName(v.id);
                        v.rarity = ro.has("rarity") ? ro.get("rarity").getAsString() : "common";
                        r.reel.add(v);
                    }
                    if (o.has("won")) {
                        JsonObject w = o.getAsJsonObject("won");
                        r.wonName = w.has("name") ? w.get("name").getAsString() : "";
                        r.wonId = w.has("id") ? w.get("id").getAsString() : "";
                        if (r.wonName.isEmpty() && !r.wonId.isEmpty()) r.wonName = itemName(r.wonId);
                        r.wonCount = w.has("count") ? w.get("count").getAsInt() : 1;
                        r.wonRarity = w.has("rarity") ? w.get("rarity").getAsString() : "common";
                    }
                    lastCrateResult = r;
                }
                if (o.has("wons")) {
                    CrateBatchResult b = new CrateBatchResult();
                    b.crateId = o.has("crate") ? o.get("crate").getAsString() : "";
                    b.count = o.has("count") ? o.get("count").getAsInt() : 0;
                    b.startedAt = System.currentTimeMillis();
                    for (var e : o.getAsJsonArray("wons")) {
                        JsonObject ro = e.getAsJsonObject();
                        ReelView v = new ReelView();
                        v.name = ro.has("name") ? ro.get("name").getAsString() : "";
                        v.id = ro.has("id") ? ro.get("id").getAsString() : "";
                        if (v.name.isEmpty() && !v.id.isEmpty()) v.name = itemName(v.id);
                        v.count = ro.has("count") ? ro.get("count").getAsInt() : 1;
                        v.rarity = ro.has("rarity") ? ro.get("rarity").getAsString() : "common";
                        b.wons.add(v);
                    }
                    lastCrateBatch = b;
                }
                // 保底进度回填到当前查看的箱体
                if (o.has("pity") && o.has("crate")) {
                    String cid = o.get("crate").getAsString();
                    int pity = o.get("pity").getAsInt();
                    for (CrateView cv : crates) if (cv.id.equals(cid)) cv.pityNow = pity;
                }
            } else {
                lastError = text.isEmpty()
                        ? (o.has("error") ? o.get("error").getAsString() : "")
                        : text;
                lastMessage = "";
            }
        } catch (Throwable ignored) {
        }
    }

    /** 邮件正文：转账类邮件由 kind + 参数在客户端拼，其它直接用服务端存的 content */
    public static String mailBody(MailView m) {
        if (m.record && m.payload != null && !m.payload.isEmpty()) {
            String s = recordBody(m.payload);
            if (!s.isEmpty()) return s;
        }
        if ("transfer".equals(m.kind) && m.transferAmount > 0) {
            return net.minecraft.network.chat.Component.translatable(
                    "extras.mail.transfer_body", m.sender, m.transferAmount).getString();
        }
        if ("player".equals(m.kind) && m.transferAmount > 0) {
            return net.minecraft.network.chat.Component.translatable(
                    "extras.mail.player_from", m.sender == null ? "" : m.sender, m.transferAmount).getString();
        }
        return m.content == null ? "" : m.content;
    }

    /** 把流水 payload 渲染成一行文字（物品名按客户端语言本地化） */
    public static String recordBody(String payload) {
        try {
            com.google.gson.JsonObject p =
                    com.google.gson.JsonParser.parseString(payload).getAsJsonObject();
            String type = p.has("type") ? p.get("type").getAsString() : "";
            String itemId = p.has("item") ? p.get("item").getAsString() : "";
            int count = p.has("count") ? p.get("count").getAsInt() : 0;
            long money = p.has("money") ? p.get("money").getAsLong() : 0L;
            String name = itemName(itemId);
            String key = switch (type) {
                case "sale" -> "extras.record.sale";
                case "buy" -> "extras.record.buy";
                case "barter" -> "extras.record.barter";
                case "exchange" -> "extras.record.exchange";
                default -> "extras.record.other";
            };
            return net.minecraft.network.chat.Component.translatable(key, name, count, money).getString();
        } catch (Throwable t) {
            return "";
        }
    }

    /** 物品注册 ID → 客户端本地化名字 */
    /** itemStack(id) 的缓存：奖池每行每帧都会问一次，不能每帧 new */
    private static final java.util.Map<String, net.minecraft.world.item.ItemStack> STACK_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 由物品 ID 造一个单件 ItemStack（画图标/出 tooltip 用），失败返回 EMPTY。
     * <p>
     * 返回的是**共享实例**，调用方只能读不能改（数量、NBT 都不许动），
     * 因为同一个 id 的所有调用方拿到的是同一个对象。
     */
    public static net.minecraft.world.item.ItemStack itemStack(String itemId) {
        if (itemId == null || itemId.isEmpty()) return net.minecraft.world.item.ItemStack.EMPTY;
        var cached = STACK_CACHE.get(itemId);
        if (cached != null) return cached;
        net.minecraft.world.item.ItemStack made = net.minecraft.world.item.ItemStack.EMPTY;
        try {
            var id = net.minecraft.resources.ResourceLocation.tryParse(itemId);
            if (id != null) {
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
                if (item != null) made = new net.minecraft.world.item.ItemStack(item);
            }
        } catch (Throwable ignored) {
        }
        STACK_CACHE.put(itemId, made);
        return made;
    }

    /** 重连/换服时清空与注册表相关的缓存 */
    public static void clearCaches() {
        STACK_CACHE.clear();
    }

    /**
     * 断开连接 / 切换服务器时整体复位。
     * <p>
     * 这些都是静态字段，不清的话换服后会残留上一个服务器的数据：
     * 轻则显示别人的余额和邮件，重则 admin 标记还在（服务端会拒绝，但界面会显示管理页签）。
     */
    /** 独立解析扫地女仆配置，避免其它状态字段解析失败时一起丢掉 */
    public static void applySweeper(String json) {
        try {
            var root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("sweeper") || !root.get("sweeper").isJsonObject()) return;
            var sw = root.getAsJsonObject("sweeper");
            sweeperEnabled = !sw.has("enabled") || sw.get("enabled").getAsBoolean();
            sweeperInterval = sw.has("interval") ? sw.get("interval").getAsInt() : 600;
            sweeperOverload = sw.has("overload") ? sw.get("overload").getAsInt() : 640;
            sweeperDustbins = sw.has("dustbins") ? sw.get("dustbins").getAsInt() : 8;
            readIds(sw, "blacklist", sweeperBlacklist);
            readIds(sw, "whitelist", sweeperWhitelist);
            readIds(sw, "extraEntities", sweeperExtraEntities);
            sweeperChatMessage = sw.has("chatMessage") ? sw.get("chatMessage").getAsString() : sweeperChatMessage;
            sweeperCountdownMessage = sw.has("countdownMessage") ? sw.get("countdownMessage").getAsString() : sweeperCountdownMessage;
            sweeperAfterMessage = sw.has("afterMessage") ? sw.get("afterMessage").getAsString() : sweeperAfterMessage;
        } catch (Throwable ignored) {
        }
    }

    private static void readIds(com.google.gson.JsonObject o, String key, List<String> out) {
        out.clear();
        if (o.has(key) && o.get(key).isJsonArray()) {
            for (var e : o.getAsJsonArray(key)) out.add(e.getAsString());
        }
    }

    public static void reset() {
        balance = 0;
        streak = 0;
        bestStreak = 0;
        signedToday = false;
        lastSignDay = 0;
        signedDays.clear();
        claimedMilestones.clear();
        mails.clear();
        exchange.clear();
        friends.clear();
        friendRequests.clear();
        admin = false;
        codes.clear();
        onlinePlayers.clear();
        signinMode = "linear";
        signinBase = 100L;
        signinStep = 20L;
        signinCap = 10;
        signinExact.clear();
        signinMiles.clear();
        crates.clear();
        lastCrateResult = null;
        lastCrateBatch = null;
        lastError = "";
        lastMessage = "";
        lastAction = "";
        lastOk = true;
        clearCaches();
    }

    public static String itemName(String itemId) {
        if (itemId == null || itemId.isEmpty()) return "";
        try {
            var stack = itemStack(itemId);
            return stack.isEmpty() ? itemId : stack.getHoverName().getString();
        } catch (Throwable t) {
            return itemId;
        }
    }

    /** 是否有未领取邮件（纯记录邮件不可领取，不计入） */
    /** 物理货币物品：获取即入账，不允许兑换出 */
    public static boolean isCurrencyItem(String itemId) {
        return "sellbox:gold_coin".equals(itemId) || "sellbox:money".equals(itemId);
    }

    public static int unreadCount() {
        int n = 0;
        for (MailView m : mails) if (!m.claimed && !m.record) n++;
        return n;
    }

    // ════════════════════════════════════════════
    //  视图模型
    // ════════════════════════════════════════════

    public static final class MailView {
        public String id = "";
        public String title = "";
        public String content = "";
        public String kind = "system";
        public String sender = "";
        public long money = 0;
        public boolean claimed = false;
        public long sentAt = 0;
        public int itemCount = 0;
        public long transferAmount = 0;
        /** 纯记录邮件（交易流水）：不可领取 */
        public boolean record = false;
        /** 结构化附加数据 JSON（流水邮件） */
        public String payload = "";
        /** 以服务器名义发送（管理员邮件/欢迎邮件） */
        public boolean fromServer = false;
        public final List<String> itemNames = new ArrayList<>();
    }

    public static final class ExchangeView {
        public String itemId = "";
        public String name = "";
        public long value = 0;
        public int owned = 0;
    }

    public static final class CodeView {
        public String code = "";
        public String title = "";
        public long money = 0;
        public int uses = 0;
        public int maxUses = -1;
        public int claimed = 0;
        public long expireAt = 0;
        public int itemCount = 0;
    }

    public static final class FriendView {
        public String uuid = "";
        public String name = "";
        public boolean online = false;
    }

    public static final class DayReward {
        public int day = 0;
        public long money = 0;
    }

    public static final class MileView {
        public int day = 0;
        public long money = 0;
        public final List<String> items = new ArrayList<>();
    }

    public static final class CrateView {
        public String id = "";
        public String name = "";
        public long price = 0;
        /** 保底品质（common = 未启用） */
        public String pityRarity = "common";
        /** 保底次数，0 = 未启用 */
        public int pityCount = 0;
        /** 当前保底进度（还差几次触发），由 crate_open 响应回填 */
        public int pityNow = 0;
        public ItemStack icon = ItemStack.EMPTY;
        public final List<CrateItemView> items = new ArrayList<>();

        public boolean pityEnabled() {
            return pityCount > 0 && !"common".equals(pityRarity);
        }
    }

    public static final class CrateItemView {
        public String id = "";
        public String name = "";
        public int count = 1;
        public double chance = 0;
        public String rarity = "common";
    }

    /** 连开结果（一次开多个时，不播转盘，出汇总面板） */
    public static final class CrateBatchResult {
        public String crateId = "";
        public int count = 0;
        public long startedAt = 0;
        public final List<ReelView> wons = new ArrayList<>();
    }

    /** 开箱动画数据（由 crate_open 响应构造） */
    public static final class CrateResult {
        public String crateId = "";
        public int stopAt = 0;
        public long startedAt = 0;
        public String wonName = "";
        public String wonId = "";
        public int wonCount = 1;
        public String wonRarity = "common";
        public final List<ReelView> reel = new ArrayList<>();
    }

    public static final class ReelView {
        public String name = "";
        public String id = "";
        public int count = 1;
        public String rarity = "common";
    }
}
