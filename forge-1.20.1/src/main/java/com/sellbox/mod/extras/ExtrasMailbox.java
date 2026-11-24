package com.sellbox.mod.extras;

import com.google.gson.JsonObject;
import com.sellbox.mod.shop.SellBoxEconomy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 邮箱互联：把出货箱 / 管理员商店 / 玩家市场的交易流水写进信箱，
 * 并提供群发、新玩家自动推送、玩家互发邮件。
 * <p>
 * 流水邮件用 {@code kind="record"} + {@code payload} 存结构化数据，
 * 客户端按自己的语言渲染（物品名也从 itemId 本地化，不依赖服务端语言）。
 */
public final class ExtrasMailbox {
    private ExtrasMailbox() {}

    /** 最近一次玩家互发邮件时间（防刷屏） */
    private static final java.util.Map<UUID, Long> lastMail = new java.util.concurrent.ConcurrentHashMap<>();

    /** 停服/换档时清掉玩家发信冷却 */
    public static void clearRuntimeState() {
        lastMail.clear();
    }

    // ════════════════════════════════════════════
    //  交易流水
    // ════════════════════════════════════════════

    /** 流水来源：sellbox（出货箱）/ shop（管理员商店）/ market（玩家市场） */
    public static final String SRC_SELLBOX = "sellbox";
    public static final String SRC_SHOP = "shop";
    public static final String SRC_MARKET = "market";

    /**
     * 记一条流水邮件。
     *
     * @param type   sale / buy / barter / exchange / signin
     * @param itemId 物品注册 ID（客户端据此本地化名字），可为 null
     * @param count  物品数量
     * @param money  涉及金额（正数）
     */
    public static void record(ServerPlayer player, String type, String source,
                             String itemId, int count, long money) {
        if (player == null) return;
        JsonObject p = new JsonObject();
        p.addProperty("type", type);
        if (source != null) p.addProperty("source", source);
        if (itemId != null && !itemId.isEmpty()) p.addProperty("item", itemId);
        p.addProperty("count", count);
        p.addProperty("money", money);

        ExtrasData.Mail m = new ExtrasData.Mail();
        m.id = UUID.randomUUID().toString();
        m.title = "";
        m.content = "";
        m.kind = "record";
        m.record = true;
        m.money = 0L;
        m.payload = p.toString();
        m.sender = "";
        m.sentAt = System.currentTimeMillis();

        ExtrasData data = ExtrasManager.get(player.getUUID());
        data.addMail(m);
        pruneRecords(data);
        ExtrasManager.markDirty();
    }

    /** 控制流水邮件数量，避免 100 封上限把正经邮件挤掉 */
    private static void pruneRecords(ExtrasData data) {
        int n = 0;
        List<ExtrasData.Mail> toRemove = new ArrayList<>();
        for (ExtrasData.Mail m : data.mails) {
            if (m.record) {
                n++;
                if (n > ExtrasConfig.MAX_RECEIPTS) toRemove.add(m);
            }
        }
        data.mails.removeAll(toRemove);
    }

    // ════════════════════════════════════════════
    //  群发 / 新玩家推送
    // ════════════════════════════════════════════

    /**
     * 群发给所有已知玩家（含离线）。
     *
     * @param includeOnlineOnly true = 只发在线的
     * @return 实际投递的人数
     */
    public static int broadcast(MinecraftServer server, ExtrasData.Mail proto, boolean includeOnlineOnly) {
        if (server == null || proto == null) return 0;

        List<UUID> targets = new ArrayList<>();
        if (includeOnlineOnly) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) targets.add(p.getUUID());
        } else {
            // 在线 + 存档里所有已知玩家（离线也能收到，下次上线可见）
            targets.addAll(ExtrasManager.allIds());
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!targets.contains(p.getUUID())) targets.add(p.getUUID());
            }
        }

        int n = 0;
        for (UUID id : targets) {
            ExtrasData data = ExtrasManager.get(id);
            ExtrasData.Mail copy = copyOf(proto);
            data.addMail(copy);
            n++;
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            if (online != null) {
                ExtrasServer.sendState(online);
                ExtrasNotices.mail(online, "extras.notice.mail.title", new Object[0],
                        "extras.notice.mail.broadcast", new Object[0]);
            }
        }
        ExtrasManager.markDirty();
        return n;
    }

    /** 深拷贝一封邮件（多人群发要各自独立，否则领取状态串味） */
    public static ExtrasData.Mail copyOf(ExtrasData.Mail src) {
        ExtrasData.Mail m = new ExtrasData.Mail();
        m.id = UUID.randomUUID().toString();
        m.title = src.title;
        m.content = src.content;
        m.kind = src.kind;
        m.record = src.record;
        m.sender = src.sender;
        m.senderUUID = src.senderUUID;
        m.fromServer = src.fromServer;
        m.money = src.money;
        m.transferAmount = src.transferAmount;
        m.payload = src.payload;
        m.claimed = false;
        m.sentAt = System.currentTimeMillis();
        for (ItemStack s : src.items) m.items.add(s.copy());
        return m;
    }

    /**
     * 新玩家首次上线：把配置里的欢迎邮件全部投递一次。
     */
    public static void sendWelcomeIfNeeded(ServerPlayer player) {
        ExtrasData data = ExtrasManager.get(player.getUUID());
        if (data.welcomeSent) return;
        data.welcomeSent = true;

        List<ExtrasConfigFile.DefaultMail> list = ExtrasConfigFile.getWelcomeMails();
        for (ExtrasConfigFile.DefaultMail dm : list) {
            ExtrasData.Mail m = new ExtrasData.Mail();
            m.title = dm.title;
            m.content = dm.content;
            m.kind = "welcome";
            m.money = dm.money;
            m.sender = "";
            m.fromServer = true;
            for (ItemStack s : dm.items) m.items.add(s.copy());
            data.addMail(m);
        }
        ExtrasManager.markDirty();
        if (!list.isEmpty()) {
            ExtrasServer.sendState(player);
        }
    }

    // ════════════════════════════════════════════
    //  玩家互发
    // ════════════════════════════════════════════

    /**
     * 玩家给玩家写信（纯文字，可选手持物附件，可选附带金币）。
     *
     * @return null = 成功；否则是错误翻译键
     */
    public static String sendTo(ServerPlayer from, ServerPlayer to, String title, String content,
                                long money, boolean attachHeld) {
        long now = System.currentTimeMillis();
        Long last = lastMail.get(from.getUUID());
        if (last != null && now - last < ExtrasConfig.MAIL_COOLDOWN_MS) {
            return "extras.msg.too_fast";
        }

        if (money > 0) {
            if (SellBoxEconomy.getBalance(from) < money) return "extras.msg.balance_low";
            if (!SellBoxEconomy.deductBalance(from, (int) money)) return "extras.msg.deduct_failed";
            SellBoxEconomy.addBalance(to, (int) money);
        }

        ExtrasData.Mail m = new ExtrasData.Mail();
        m.kind = "player";
        m.title = title == null ? "" : title;
        m.content = content == null ? "" : content;
        m.money = 0L;
        m.sender = from.getName().getString();
        m.senderUUID = from.getUUID();
        m.transferAmount = money;
        if (attachHeld) {
            // 邮件附件必须是「转移」，不能复制：先拷贝进邮件，再从手上扣 1 个。
            // 否则玩家可以把任意物品寄给自己/小号，实现无限复制。
            ItemStack held = from.getMainHandItem();
            if (!held.isEmpty()) {
                m.items.add(held.copy());
                held.shrink(1);
            }
        }
        ExtrasManager.get(to.getUUID()).addMail(m);
        lastMail.put(from.getUUID(), now);
        ExtrasManager.markDirty();

        ExtrasServer.sendState(to);
        ExtrasNotices.mail(to, "extras.notice.mail.title", new Object[0],
                "extras.notice.mail.incoming", new Object[]{from.getName().getString()});
        return null;
    }

    /** 物品 ID → 客户端可本地化的名字（只是给指令/日志用） */
    public static String itemNameOf(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId == null ? "" : itemId);
        if (id == null) return itemId == null ? "" : itemId;
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
        return stack.isEmpty() ? itemId : stack.getHoverName().getString();
    }
}
