package com.sellbox.mod.extras;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sellbox.mod.shop.SellBoxEconomy;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单个玩家在扩展模块里的全部数据：签到、邮箱、好友。
 * <p>
 * 只用 JSON 持久化，字段全部带默认值，老存档缺字段时按默认行为处理。
 */
public final class ExtrasData {
    /** 最长连续签到天数 */
    public int bestStreak = 0;
    /** 当前连续天数 */
    public int streak = 0;
    /** 最后一次签到日（epochDay，0 = 从未） */
    public long lastSignDay = 0L;
    /** 已签到日期集合（epochDay） */
    public final Set<Long> signedDays = new LinkedHashSet<>();
    /** 本轮已领取的里程奖励天数 */
    public final Set<Integer> claimedMilestones = new LinkedHashSet<>();

    /** 邮箱（最新的在前） */
    public final List<Mail> mails = new ArrayList<>();
    /** 好友 UUID → 备注名 */
    public final Map<UUID, String> friends = new LinkedHashMap<>();
    /** 收到的待处理好友申请 UUID 集合 */
    public final Set<UUID> friendRequests = new LinkedHashSet<>();
    /** 是否已经发过新玩家欢迎邮件（只发一次） */
    public boolean welcomeSent = false;
    /**
     * 保底计数：箱体 id → 累计多少次没开出「保底品质及以上」。
     * <p>
     * 只在服务端用，跟着玩家数据一起存，换箱体互不影响。
     */
    public final Map<String, Integer> cratePity = new LinkedHashMap<>();


    // ════════════════════════════════════════════
    //  签到
    // ════════════════════════════════════════════

    /** 今天（服务器本地时区）的 epochDay */
    public static long today() {
        return LocalDate.now().toEpochDay();
    }

    public boolean signedToday() {
        return signedDays.contains(today());
    }

    /**
     * 当前仍然有效的连续天数：昨天签过（或今天已签）才保留，
     * 断签超过一天就显示 0，而不是把上次的 streak 一直挂在界面上。
     */
    public int effectiveStreak() {
        long t = today();
        return signedDays.contains(t) || signedDays.contains(t - 1) ? streak : 0;
    }

    /**
     * 执行签到。
     *
     * @return 本次签到后的连续天数；-1 表示今天已签到
     */
    public int signIn() {
        long t = today();
        if (signedDays.contains(t)) return -1;

        // 昨天签过 → 连续；否则重开一轮。断签后里程奖励也随「本轮」重置，
        // 否则玩家第二轮再连续到第 3/5/7 天时永远领不到奖励。
        boolean continued = signedDays.contains(t - 1);
        if (!continued) claimedMilestones.clear();
        streak = continued ? streak + 1 : 1;
        lastSignDay = t;
        signedDays.add(t);
        bestStreak = Math.max(bestStreak, streak);
        trimDays();
        return streak;
    }

    /** 只保留最近 400 天，避免存档无限增长 */
    private void trimDays() {
        if (signedDays.size() <= 400) return;
        List<Long> sorted = new ArrayList<>(signedDays);
        sorted.sort(Long::compareTo);
        for (int i = 0; i < sorted.size() - 400; i++) {
            signedDays.remove(sorted.get(i));
        }
    }

    // ════════════════════════════════════════════
    //  邮箱
    // ════════════════════════════════════════════

    /**
     * 清掉超过保留期的邮件。<b>有未领取附件的邮件永不删除</b>，
     * 只删「已领取」或「本来就没东西可领」的旧邮件（含流水记录）。
     *
     * @return 实际删除的封数
     */
    public int purgeOldMails(long now) {
        int removed = 0;
        for (int i = mails.size() - 1; i >= 0; i--) {
            Mail m = mails.get(i);
            if (m.claimable()) continue;                       // 还有东西没领 → 留着
            if (now - m.sentAt < ExtrasConfig.MAIL_KEEP_MS) continue;  // 没到保留期
            mails.remove(i);
            removed++;
        }
        return removed;
    }

    /**
     * 手动清空：删掉所有「没有未领取附件」的邮件，规则同 {@link #purgeOldMails}。
     *
     * @return 实际删除的封数
     */
    public int clearMails() {
        int before = mails.size();
        mails.removeIf(m -> !m.claimable());
        return before - mails.size();
    }

    /** 当前是否有未领取附件的邮件 */
    public int pendingMailCount() {
        int n = 0;
        for (Mail m : mails) if (m.claimable()) n++;
        return n;
    }

    /**
     * 添加一封邮件。同类型同发送者的未领取邮件会合并附件。
     *
     * @return 实际加入的那封邮件
     */
    public Mail addMail(Mail mail) {
        if (mail == null) return null;
        // 只合并「纯附件」邮件：任何带文字/金额/转账信息的邮件都必须独立保留，
        // 否则后来那封的标题、正文或金币会被静默丢掉（玩家发信/附件奖励会凭空消失）。
        if (mail.sender != null && !mail.sender.isEmpty()
                && !mail.record && !mail.fromServer && !mail.hasMoney()
                && mail.transferAmount <= 0 && mail.title.isEmpty() && mail.content.isEmpty()
                && !mail.items.isEmpty()) {
            for (Mail m : mails) {
                if (!m.claimed && !m.record && !m.fromServer && !m.hasMoney()
                        && m.transferAmount <= 0 && m.title.isEmpty() && m.content.isEmpty()
                        && mail.sender.equals(m.sender) && mail.kind.equals(m.kind)
                        && m.items.size() + mail.items.size() <= 27) {
                    m.items.addAll(mail.items);
                    return m;
                }
            }
        }
        mails.add(0, mail);
        // 上限 100 封：只删除「已经没有未领取奖励」的邮件。
        // 如果信箱里全是未领取邮件，宁可暂时超限，也不能把玩家的奖励删掉。
        while (mails.size() > ExtrasConfig.MAX_MAILS) {
            int removeIdx = -1;
            for (int i = mails.size() - 1; i >= 0; i--) {
                if (!mails.get(i).claimable()) { removeIdx = i; break; }
            }
            if (removeIdx < 0) break;
            mails.remove(removeIdx);
        }
        return mail;
    }

    /**
     * 状态包下发的邮件窗口：**所有未领取的**（这些可能还有附件，客户端要能点领取）
     * 加上最近 {@link ExtrasConfig#MAIL_STATE_RECENT} 封已领取的，避免 100 封全量下发。
     * <p>
     * 顺序与 {@link #mails} 一致（新的在前），保证客户端下标语义不变。
     */
    public static List<Mail> windowForState(List<Mail> all) {
        if (all == null || all.isEmpty()) return List.of();
        int recent = 0;
        List<Mail> out = new ArrayList<>(Math.min(all.size(), ExtrasConfig.MAIL_STATE_RECENT + 8));
        for (Mail m : all) {
            if (!m.claimable()) {
                if (recent >= ExtrasConfig.MAIL_STATE_RECENT) continue;
                recent++;
            }
            out.add(m);
        }
        return out;
    }

    /** 未领取/未读邮件数（用于红点；纯记录邮件不可领取，不计入） */
    public int unreadCount() {
        int n = 0;
        for (Mail m : mails) if (!m.claimed && !m.record) n++;
        return n;
    }

    // ════════════════════════════════════════════
    //  序列化
    // ════════════════════════════════════════════

    public JsonObject toJson(RegistryAccess access) {
        JsonObject o = new JsonObject();
        o.addProperty("bestStreak", bestStreak);
        o.addProperty("streak", streak);
        o.addProperty("lastSignDay", lastSignDay);
        o.addProperty("welcomeSent", welcomeSent);

        if (!cratePity.isEmpty()) {
            JsonObject pity = new JsonObject();
            for (Map.Entry<String, Integer> e : cratePity.entrySet()) pity.addProperty(e.getKey(), e.getValue());
            o.add("cratePity", pity);
        }

        JsonArray days = new JsonArray();
        for (long d : signedDays) days.add(d);
        o.add("signedDays", days);

        JsonArray miles = new JsonArray();
        for (int d : claimedMilestones) miles.add(d);
        o.add("claimedMilestones", miles);

        JsonArray mailArr = new JsonArray();
        for (Mail m : mails) mailArr.add(m.toJson(access));
        o.add("mails", mailArr);

        JsonObject fri = new JsonObject();
        for (Map.Entry<UUID, String> e : friends.entrySet()) {
            fri.addProperty(e.getKey().toString(), e.getValue() == null ? "" : e.getValue());
        }
        o.add("friends", fri);

        JsonArray req = new JsonArray();
        for (UUID u : friendRequests) req.add(u.toString());
        o.add("friendRequests", req);

        return o;
    }

    public static ExtrasData fromJson(JsonObject o, RegistryAccess access) {
        ExtrasData d = new ExtrasData();
        if (o == null) return d;
        d.bestStreak = getInt(o, "bestStreak");
        d.streak = getInt(o, "streak");
        d.lastSignDay = getLong(o, "lastSignDay");
        d.welcomeSent = o.has("welcomeSent") && o.get("welcomeSent").getAsBoolean();

        if (o.has("cratePity")) {
            for (var e : o.getAsJsonObject("cratePity").entrySet()) {
                try {
                    d.cratePity.put(e.getKey(), e.getValue().getAsInt());
                } catch (Throwable ignored) {
                }
            }
        }

        if (o.has("signedDays")) {
            for (var e : o.getAsJsonArray("signedDays")) d.signedDays.add(e.getAsLong());
        }
        if (o.has("claimedMilestones")) {
            for (var e : o.getAsJsonArray("claimedMilestones")) d.claimedMilestones.add(e.getAsInt());
        }
        if (o.has("mails")) {
            for (var e : o.getAsJsonArray("mails")) {
                Mail m = Mail.fromJson(e.getAsJsonObject(), access);
                if (m != null) d.mails.add(m);
            }
        }
        if (o.has("friends")) {
            for (var e : o.getAsJsonObject("friends").entrySet()) {
                try {
                    d.friends.put(UUID.fromString(e.getKey()), e.getValue().getAsString());
                } catch (Throwable ignored) {
                }
            }
        }
        if (o.has("friendRequests")) {
            for (var e : o.getAsJsonArray("friendRequests")) {
                try {
                    d.friendRequests.add(UUID.fromString(e.getAsString()));
                } catch (Throwable ignored) {
                }
            }
        }
        return d;
    }

    private static int getInt(JsonObject o, String k) {
        try { return o.has(k) ? o.get(k).getAsInt() : 0; } catch (Throwable t) { return 0; }
    }

    private static long getLong(JsonObject o, String k) {
        try { return o.has(k) ? o.get(k).getAsLong() : 0L; } catch (Throwable t) { return 0L; }
    }

    // ════════════════════════════════════════════
    //  邮件
    // ════════════════════════════════════════════

    /** 一封邮件 */
    public static final class Mail {
        public String id = UUID.randomUUID().toString();
        public String title = "";
        public String content = "";
        /** 业务类型：system / redeem / signin / transfer / admin */
        public String kind = "system";
        public String sender = "";
        public UUID senderUUID;
        /** 附件物品 */
        public final List<ItemStack> items = new ArrayList<>();
        /** 附带的金额（星币） */
        public long money = 0L;
        /** 是否已领取 */
        public boolean claimed = false;
        /** 发送时间（epoch 毫秒） */
        public long sentAt = System.currentTimeMillis();
        /** 转账类邮件的金额（客户端据 sender + 本字段本地化正文） */
        public long transferAmount = 0L;
        /** 纯记录邮件（交易流水/系统通知）：不可领取，只能删除 */
        public boolean record = false;
        /** 结构化附加数据（流水邮件的类型/物品/数量/金额），客户端渲染用 */
        public String payload = "";
        /** 以服务器名义发送（管理员邮件）：没有 player 实体，领取奖励时要特殊处理 */
        public boolean fromServer = false;

        public boolean hasMoney() { return money > 0; }

        /** 是否还有东西可领（纯记录邮件永远不可领） */
        public boolean claimable() {
            return !record && !claimed && (money > 0 || !items.isEmpty());
        }

        public JsonObject toJson(RegistryAccess access) {
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("title", title);
            o.addProperty("content", content);
            o.addProperty("kind", kind);
            o.addProperty("sender", sender);
            if (senderUUID != null) o.addProperty("senderUUID", senderUUID.toString());
            o.addProperty("money", money);
            o.addProperty("claimed", claimed);
            o.addProperty("sentAt", sentAt);
            if (transferAmount != 0) o.addProperty("transferAmount", transferAmount);
            if (record) o.addProperty("record", true);
            if (payload != null && !payload.isEmpty()) o.addProperty("payload", payload);
            if (fromServer) o.addProperty("fromServer", true);

            JsonArray arr = new JsonArray();
            for (ItemStack s : items) {
                if (s != null && !s.isEmpty()) arr.add(ExtrasItems.write(s, access));
            }
            o.add("items", arr);
            return o;
        }

        public static Mail fromJson(JsonObject o, RegistryAccess access) {
            if (o == null) return null;
            Mail m = new Mail();
            m.id = o.has("id") ? o.get("id").getAsString() : UUID.randomUUID().toString();
            m.title = o.has("title") ? o.get("title").getAsString() : "";
            m.content = o.has("content") ? o.get("content").getAsString() : "";
            m.kind = o.has("kind") ? o.get("kind").getAsString() : "system";
            m.sender = o.has("sender") ? o.get("sender").getAsString() : "";
            if (o.has("senderUUID")) {
                try { m.senderUUID = UUID.fromString(o.get("senderUUID").getAsString()); }
                catch (Throwable ignored) {}
            }
            m.money = o.has("money") ? o.get("money").getAsLong() : 0L;
            m.claimed = o.has("claimed") && o.get("claimed").getAsBoolean();
            m.sentAt = o.has("sentAt") ? o.get("sentAt").getAsLong() : System.currentTimeMillis();
            m.transferAmount = o.has("transferAmount") ? o.get("transferAmount").getAsLong() : 0L;
            m.record = o.has("record") && o.get("record").getAsBoolean();
            m.payload = o.has("payload") ? o.get("payload").getAsString() : "";
            m.fromServer = o.has("fromServer") && o.get("fromServer").getAsBoolean();
            if (o.has("items")) {
                for (var e : o.getAsJsonArray("items")) {
                    ItemStack s = ExtrasItems.read(e.getAsJsonObject(), access);
                    if (!s.isEmpty()) m.items.add(s);
                }
            }
            return m;
        }
    }

    /**
     * 便捷构造：发放物品邮件，背包放不下就进邮箱。
     */
    public static void deliver(ServerPlayer player, ExtrasData data, String title, String kind,
                               List<ItemStack> items, long money, RegistryAccess access) {
        // 先尝试塞背包
        List<ItemStack> leftover = new ArrayList<>();
        if (items != null) {
            for (ItemStack s : items) {
                if (s == null || s.isEmpty()) continue;
                if (!player.getInventory().add(s.copy())) {
                    leftover.add(s.copy());
                }
            }
        }
        if (money > 0) {
            SellBoxEconomy.addBalance(player, (int) money);
        }
        if (!leftover.isEmpty()) {
            Mail m = new Mail();
            m.title = title;
            m.kind = kind;
            m.sender = "";
            m.items.addAll(leftover);
            data.addMail(m);
        }
    }

    /** 全局缓存（由 ExtrasManager 维护） */
    public static final Map<UUID, ExtrasData> CACHE = new ConcurrentHashMap<>();
}
