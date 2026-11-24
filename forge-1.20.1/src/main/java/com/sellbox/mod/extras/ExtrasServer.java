package com.sellbox.mod.extras;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.shop.SellBoxEconomy;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扩展模块的服务端逻辑（1.20.1）。
 * <p>
 * 所有金额变动都走 {@link SellBoxEconomy}，自动跟随当前启用的经济系统
 * （内置 / SDMShop / ViScriptShop / Vault）。
 */
public final class ExtrasServer {
    private ExtrasServer() {}

    /** 玩家上次转账时间（防连点） */
    private static final Map<UUID, Long> lastTransfer = new ConcurrentHashMap<>();

    /** 已开启管理模式的玩家（由 /sellbox extras admin 打开，重启后失效） */
    private static final Set<UUID> adminMode = ConcurrentHashMap.newKeySet();

    public static boolean isAdminMode(ServerPlayer player) {
        return adminMode.contains(player.getUUID());
    }

    /** 停服/换档时清掉运行期状态，避免下个存档继承管理员模式与冷却 */
    public static void clearRuntimeState() {
        adminMode.clear();
        lastTransfer.clear();
        lastCrateOpen.clear();
        ExtrasMailbox.clearRuntimeState();
        ExtrasManager.clearRuntimeState();
    }

    /** 由指令调用：开关某个玩家的管理模式，并立即重发状态 */
    public static void setAdminMode(ServerPlayer player, boolean on) {
        if (on) adminMode.add(player.getUUID());
        else adminMode.remove(player.getUUID());
        sendState(player);
    }

    // ════════════════════════════════════════════
    //  入口：请求分发
    // ════════════════════════════════════════════

    public static void handleRequest(ServerPlayer player, String json) {
        JsonObject resp = new JsonObject();
        String action = "";
        try {
            JsonObject req = JsonParser.parseString(json).getAsJsonObject();
            action = req.has("action") ? req.get("action").getAsString() : "";
            resp.addProperty("action", action);
            if (!com.sellbox.mod.config.SellBoxConfig.isExtrasEnabled()) {
                fail(resp, "extras.msg.disabled");
                sendResponse(player, resp);
                return;
            }

            switch (action) {
                case "open"           -> { sendState(player); return; }
                case "sign_in"        -> handleSignIn(player, resp);
                case "mail_claim"     -> handleMailClaim(player, req, resp);
                case "mail_claim_all" -> handleMailClaimAll(player, resp);
                case "mail_delete"    -> handleMailDelete(player, req, resp);
                case "mail_clear"    -> handleMailClear(player, resp);
                case "transfer"       -> handleTransfer(player, req, resp);
                case "exchange_in"    -> handleExchangeIn(player, req, resp);
                case "exchange_out"   -> handleExchangeOut(player, req, resp);
                case "redeem"         -> handleRedeem(player, req, resp);
                case "friend_add"     -> handleFriendAdd(player, req, resp);
                case "friend_accept"  -> handleFriendAccept(player, req, resp);
                case "friend_remove"  -> handleFriendRemove(player, req, resp);
                case "admin_rate_set"    -> handleAdminRateSet(player, req, resp);
                case "admin_rate_hold"   -> handleAdminRateHold(player, req, resp);
                case "admin_rate_remove" -> handleAdminRateRemove(player, req, resp);
                case "admin_code_create" -> handleAdminCodeCreate(player, req, resp);
                case "admin_code_remove" -> handleAdminCodeRemove(player, req, resp);
                case "admin_mail_send"   -> handleAdminMailSend(player, req, resp);
                case "mail_send"         -> handlePlayerMailSend(player, req, resp);
                case "admin_mail_broadcast" -> handleAdminMailBroadcast(player, req, resp);
                case "admin_welcome_add"    -> handleAdminWelcomeAdd(player, req, resp);
                case "admin_welcome_clear"  -> handleAdminWelcomeClear(player, resp);
                case "crate_open"           -> handleCrateOpen(player, req, resp);
                case "admin_signin_set"     -> handleAdminSigninSet(player, req, resp);
                case "admin_signin_mode"    -> handleAdminSigninMode(player, req, resp);
                case "admin_signin_mile"    -> handleAdminSigninMile(player, req, resp);
                case "admin_signin_mile_del"-> handleAdminSigninMileDel(player, req, resp);
                case "admin_crate_create"   -> handleAdminCrateCreate(player, req, resp);
                case "admin_crate_remove"   -> handleAdminCrateRemove(player, req, resp);
                case "admin_crate_price"    -> handleAdminCratePrice(player, req, resp);
                case "admin_crate_update"   -> handleAdminCrateUpdate(player, req, resp);
                case "admin_crate_item_add" -> handleAdminCrateItemAdd(player, req, resp);
                case "admin_crate_item_del" -> handleAdminCrateItemDel(player, req, resp);
                case "admin_crate_pity"     -> handleAdminCratePity(player, req, resp);
                case "admin_crate_icon"     -> handleAdminCrateIcon(player, req, resp);
                case "admin_crate_item_set" -> handleAdminCrateItemSet(player, req, resp);
                case "exchange_in_all"      -> handleExchangeInAll(player, req, resp);
                case "admin_exchange_out"   -> handleAdminExchangeOut(player, req, resp);
                case "admin_auto_exchange"  -> handleAdminAutoExchange(player, req, resp);
                case "admin_sweeper_toggle" -> handleSweeperToggle(player, req, resp);
                case "admin_sweeper_save"   -> handleSweeperSave(player, req, resp);
                case "admin_sweeper_list_add" -> handleSweeperList(player, req, resp, true);
                case "admin_sweeper_list_remove" -> handleSweeperList(player, req, resp, false);
                case "admin_sweeper_clean"  -> handleSweeperClean(player, req, resp);
                case "sweeper_dustbin"      -> handleSweeperDustbin(player, req, resp);
                case "admin_mile_money"     -> handleAdminMileMoney(player, req, resp);
                case "admin_mile_update"    -> handleAdminMileUpdate(player, req, resp);
                case "admin_mile_item_del"  -> handleAdminMileItemDel(player, req, resp);
                default -> {
                    resp.addProperty("ok", false);
                    resp.addProperty("error", action);
                    resp.addProperty("msgKey", "extras.msg.unknown_action");
                }
            }
        } catch (Throwable t) {
            SellBoxMod.LOGGER.error("Extras request failed", t);
            resp.addProperty("ok", false);
            resp.addProperty("error", String.valueOf(t.getMessage()));
            resp.addProperty("msgKey", "extras.msg.internal_error");
        }

        sendResponse(player, resp);
        ExtrasManager.markDirty();
        sendState(player);
        // 管理动作会改全局配置（箱子/兑换表/签到规则），把新状态同步给其他在线玩家，
        // 否则他们开着的面板会一直显示旧价格/旧奖池，直到下一次操作。
        boolean adminOk = resp.has("ok") && resp.get("ok").getAsBoolean();
        if (action.startsWith("admin_") && adminOk && !"admin_mail_broadcast".equals(action)) {
            for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
                if (p != player) sendState(p);
            }
        }
    }

    private static void sendResponse(ServerPlayer player, JsonObject resp) {
        com.sellbox.mod.network.ModNetwork.sendExtrasResponse(player, resp.toString());
    }

    /** 签到规则 → JSON（签到页展示 + 管理页回填） */
    private static JsonObject sendSigninConfig() {
        ExtrasConfigFile.SigninConfig cfg = ExtrasConfigFile.signin();
        JsonObject s = new JsonObject();
        s.addProperty("mode", cfg.mode);
        s.addProperty("base", cfg.base);
        s.addProperty("step", cfg.step);
        s.addProperty("cap", cfg.cap);
        JsonArray exact = new JsonArray();
        for (var e : cfg.exact.entrySet()) {
            JsonObject eo = new JsonObject();
            eo.addProperty("day", e.getKey());
            eo.addProperty("money", e.getValue());
            exact.add(eo);
        }
        s.add("exact", exact);
        JsonArray miles = new JsonArray();
        for (ExtrasConfigFile.Milestone m : cfg.milestones) {
            JsonObject mo = new JsonObject();
            mo.addProperty("day", m.day);
            mo.addProperty("money", m.money);
            JsonArray items = new JsonArray();
            for (ItemStack st : m.items) items.add(ExtrasItems.nameOf(st));
            mo.add("items", items);
            miles.add(mo);
        }
        s.add("milestones", miles);
        return s;
    }

    private static void ok(JsonObject resp, String msgKey, Object... args) {
        resp.addProperty("ok", true);
        resp.addProperty("msgKey", msgKey);
        if (args.length > 0) {
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            for (Object a : args) {
                if (a instanceof Number num) {
                    arr.add(num.longValue());
                } else {
                    arr.add(String.valueOf(a));
                }
            }
            resp.add("msgArgs", arr);
        }
    }

    private static void fail(JsonObject resp, String msgKey, Object... args) {
        resp.addProperty("ok", false);
        resp.addProperty("msgKey", msgKey);
        if (args.length > 0) {
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            for (Object a : args) {
                if (a instanceof Number num) {
                    arr.add(num.longValue());
                } else {
                    arr.add(String.valueOf(a));
                }
            }
            resp.add("msgArgs", arr);
        }
    }

    // ════════════════════════════════════════════
    //  签到
    // ════════════════════════════════════════════

    private static void handleSignIn(ServerPlayer player, JsonObject resp) {
        ExtrasData data = ExtrasManager.get(player.getUUID());
        if (data.signedToday()) {
            fail(resp, "extras.msg.signin_already");
            return;
        }

        int streak = data.signIn();

        // 奖励由管理配置决定：linear（基础+连续加成）/ table（逐日精确值），再叠加里程碑
        ExtrasConfigFile.SigninConfig cfg = ExtrasConfigFile.signin();
        long reward = cfg.rewardFor(streak);
        List<ItemStack> bonusItems = new ArrayList<>();
        ExtrasConfigFile.Milestone mile = cfg.milestoneFor(streak);
        if (mile != null && data.claimedMilestones.add(streak)) {
            reward += mile.money;
            for (ItemStack s : mile.items) bonusItems.add(s.copy());
        }

        claimRewards(player, reward, bonusItems);

        ok(resp, "extras.msg.signin_ok", streak, reward);
        resp.addProperty("streak", streak);
        resp.addProperty("reward", reward);
    }

    // ════════════════════════════════════════════
    //  邮箱
    // ════════════════════════════════════════════

    private static void handleMailClaim(ServerPlayer player, JsonObject req, JsonObject resp) {
        ExtrasData data = ExtrasManager.get(player.getUUID());
        String id = req.has("id") ? req.get("id").getAsString() : "";
        ExtrasData.Mail mail = null;
        for (ExtrasData.Mail m : data.mails) {
            if (m.id.equals(id)) { mail = m; break; }
        }
        if (mail == null) { fail(resp, "extras.msg.mail_missing"); return; }
        // 记录邮件/纯文字邮件没有奖励，不能通过构造包把 claimed 置为 true
        if (!mail.claimable()) { fail(resp, "extras.msg.mail_claimed"); return; }

        long money = mail.money;
        List<ItemStack> items = new ArrayList<>(mail.items);
        mail.claimed = true;
        mail.items.clear();
        mail.money = 0;

        claimRewards(player, money, items);
        ok(resp, "extras.msg.mail_claim_ok", money, countItems(items));
    }

    /** 手动清空邮箱：只删没有未领取附件的邮件 */
    private static void handleMailClear(ServerPlayer player, JsonObject resp) {
        ExtrasData data = ExtrasManager.get(player.getUUID());
        int kept = data.pendingMailCount();
        int n = data.clearMails();
        ExtrasManager.markDirty();
        ok(resp, "extras.msg.mail_cleared", n, kept);
        sendState(player);
    }

    private static void handleMailClaimAll(ServerPlayer player, JsonObject resp) {
        ExtrasData data = ExtrasManager.get(player.getUUID());
        long totalMoney = 0;
        List<ItemStack> all = new ArrayList<>();
        int n = 0;
        for (ExtrasData.Mail m : data.mails) {
            if (!m.claimable()) continue;
            totalMoney += m.money;
            all.addAll(m.items);
            m.items.clear();
            m.money = 0;
            m.claimed = true;
            n++;
        }
        if (n == 0) { fail(resp, "extras.msg.mail_none"); return; }
        claimRewards(player, totalMoney, all);
        ok(resp, "extras.msg.mail_claim_all_ok", n, totalMoney, countItems(all));
    }

    private static void handleMailDelete(ServerPlayer player, JsonObject req, JsonObject resp) {
        ExtrasData data = ExtrasManager.get(player.getUUID());
        String id = req.has("id") ? req.get("id").getAsString() : "";
        ExtrasData.Mail target = null;
        for (ExtrasData.Mail m : data.mails) {
            if (m.id.equals(id)) { target = m; break; }
        }
        if (target == null) { fail(resp, "extras.msg.mail_missing"); return; }
        // 未领取的奖励邮件不允许直接删除（避免误删/恶意客户端删掉自己的奖励）
        if (target.claimable()) { fail(resp, "extras.msg.mail_has_rewards"); return; }
        data.mails.remove(target);
        ok(resp, "extras.msg.mail_deleted");
    }

    /** 发奖：金额直接进余额，物品塞背包，塞不下的进邮箱 */
    private static void claimRewards(ServerPlayer player, long money, List<ItemStack> items) {
        // 经济余额是 int；先夹到安全范围，避免配置里的超大值截断成负数
        money = Math.max(0L, Math.min(Integer.MAX_VALUE, money));
        if (money > 0) {
            SellBoxEconomy.addBalance(player, (int) money);
        }
        List<ItemStack> leftover = new ArrayList<>();
        for (ItemStack s : items) {
            if (s == null || s.isEmpty()) continue;
            ItemStack copy = s.copy();
            if (!player.getInventory().add(copy)) {
                leftover.add(copy);
            }
        }
        if (!leftover.isEmpty()) {
            ExtrasData.Mail m = new ExtrasData.Mail();
            m.title = "";
            m.kind = "inventory_full";
            m.items.addAll(leftover);
            ExtrasManager.get(player.getUUID()).addMail(m);
            // 背包满时给一个明确反馈，避免玩家以为奖励丢了
            ExtrasNotices.send(player,
                    "extras.notice.inventory_full.title", new Object[0],
                    "extras.notice.inventory_full.text", new Object[0],
                    "mail", "warn");
        }
    }



    // ════════════════════════════════════════════
    //  转账
    // ════════════════════════════════════════════

    private static void handleTransfer(ServerPlayer player, JsonObject req, JsonObject resp) {
        String targetName = req.has("target") ? req.get("target").getAsString().trim() : "";
        int amount = req.has("amount") ? req.get("amount").getAsInt() : 0;

        if (targetName.isEmpty()) { fail(resp, "extras.msg.transfer_no_target"); return; }
        if (amount < ExtrasConfig.TRANSFER_MIN) { fail(resp, "extras.msg.transfer_too_small", ExtrasConfig.TRANSFER_MIN); return; }
        if (amount > ExtrasConfig.TRANSFER_MAX) { fail(resp, "extras.msg.transfer_too_large", ExtrasConfig.TRANSFER_MAX); return; }

        ServerPlayer target = player.server.getPlayerList().getPlayerByName(targetName);
        if (target == null) { fail(resp, "extras.msg.player_offline", targetName); return; }
        if (target.getUUID().equals(player.getUUID())) { fail(resp, "extras.msg.transfer_self"); return; }

        long now = System.currentTimeMillis();
        Long last = lastTransfer.get(player.getUUID());
        if (last != null && now - last < ExtrasConfig.TRANSFER_COOLDOWN_MS) {
            fail(resp, "extras.msg.too_fast");
            return;
        }

        int balance = SellBoxEconomy.getBalance(player);
        if (balance < amount) { fail(resp, "extras.msg.balance_low", balance); return; }

        if (!SellBoxEconomy.deductBalance(player, amount)) {
            fail(resp, "extras.msg.deduct_failed");
            return;
        }
        SellBoxEconomy.addBalance(target, amount);
        lastTransfer.put(player.getUUID(), now);

        ExtrasData targetData = ExtrasManager.get(target.getUUID());
        ExtrasData.Mail mail = new ExtrasData.Mail();
        mail.title = "";
        mail.content = "";
        mail.kind = "transfer";
        mail.money = 0;
        mail.sender = player.getName().getString();
        mail.transferAmount = amount;
        mail.senderUUID = player.getUUID();
        targetData.addMail(mail);
        ExtrasManager.markDirty();
        // 立刻刷新收款人的余额/邮箱，否则对方开着面板时看不到这笔转账
        sendState(target);
        ExtrasNotices.mail(target, "extras.notice.mail.title", new Object[0],
                "extras.notice.mail.incoming", new Object[]{player.getName().getString()});

        ok(resp, "extras.msg.transfer_ok", target.getName().getString(), amount);
        resp.addProperty("balance", SellBoxEconomy.getBalance(player));
    }

    // ════════════════════════════════════════════
    //  货币兑换
    // ════════════════════════════════════════════

    /** 管理：开启/关闭拾取/获得时自动换入 */
    private static void handleAdminAutoExchange(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        boolean enabled = req.has("enabled") && req.get("enabled").getAsBoolean();
        ExtrasConfigFile.setAutoExchange(enabled);
        ExtrasConfigFile.save();
        ok(resp, enabled ? "extras.msg.admin_auto_exchange_on" : "extras.msg.admin_auto_exchange_off");
    }

    /** 管理：开启/关闭余额换出 */
    private static void handleAdminExchangeOut(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        boolean enabled = req.has("enabled") && req.get("enabled").getAsBoolean();
        ExtrasConfigFile.setExchangeOut(enabled);
        ExtrasConfigFile.save();
        ok(resp, enabled ? "extras.msg.admin_exchange_out_on" : "extras.msg.admin_exchange_out_off");
    }

    /** 一键换入背包内所有在转换表里的物品 */
    private static void handleExchangeInAll(ServerPlayer player, JsonObject req, JsonObject resp) {
        long total = 0L;
        long gain = 0L;
        List<ItemStack> stacks = new ArrayList<>(player.getInventory().items);
        stacks.addAll(player.getInventory().offhand);
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            String id = ExtrasItems.keyOf(stack);
            long rate = ExtrasConfigFile.rateOf(id);
            if (rate <= 0) continue;
            int n = stack.getCount();
            total += n;
            gain += rate * n;
            stack.setCount(0);
        }
        if (total <= 0) { fail(resp, "extras.msg.exchange_no_item"); return; }
        long capped = Math.min(Integer.MAX_VALUE, gain);
        SellBoxEconomy.addBalance(player, (int) capped);
        ok(resp, "extras.msg.exchange_in_ok", total, capped);
        resp.addProperty("balance", SellBoxEconomy.getBalance(player));
    }

    private static void handleSweeperToggle(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        ExtrasConfigFile.setSweeperEnabled(req.has("enabled") && req.get("enabled").getAsBoolean());
        ExtrasConfigFile.save();
        ok(resp, "extras.msg.sweeper_saved");
    }

    private static void handleSweeperSave(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        int interval = req.has("interval") ? req.get("interval").getAsInt() : 600;
        int overload = req.has("overload") ? req.get("overload").getAsInt() : 640;
        int dustbins = req.has("dustbins") ? req.get("dustbins").getAsInt() : 8;
        if (interval < 0 || interval > 360000 || overload < 1 || dustbins < 1 || dustbins > 64) {
            fail(resp, "extras.msg.admin_bad_value"); return;
        }
        ExtrasConfigFile.setSweeperInterval(interval);
        ExtrasConfigFile.setSweeperOverload(overload);
        ExtrasConfigFile.setSweeperDustbins(dustbins);
        if (req.has("chat")) ExtrasConfigFile.setSweeperChatMessage(req.get("chat").getAsString());
        if (req.has("countdown")) ExtrasConfigFile.setSweeperCountdownMessage(req.get("countdown").getAsString());
        if (req.has("after")) ExtrasConfigFile.setSweeperAfterMessage(req.get("after").getAsString());
        ExtrasConfigFile.save();
        ok(resp, "extras.msg.sweeper_saved");
    }

    private static void handleSweeperList(ServerPlayer player, JsonObject req, JsonObject resp, boolean add) {
        if (!requireAdmin(player, resp)) return;
        String kind = req.has("kind") ? req.get("kind").getAsString() : "";
        String id = req.has("id") ? req.get("id").getAsString() : "";
        java.util.List<String> list = switch (kind) {
            case "blacklist" -> ExtrasConfigFile.getSweeperBlacklist();
            case "whitelist" -> ExtrasConfigFile.getSweeperWhitelist();
            case "entities" -> ExtrasConfigFile.getSweeperExtraEntities();
            default -> null;
        };
        if (list == null || id.isEmpty()) { fail(resp, "extras.msg.admin_bad_value"); return; }
        if (add) ExtrasConfigFile.sweepListAdd(list, id);
        else ExtrasConfigFile.sweepListRemove(list, id);
        ExtrasConfigFile.save();
        SellBoxMod.LOGGER.info("Sweeper list {} {} -> size {}", add ? "add" : "remove", id, list.size());
        ok(resp, "extras.msg.sweeper_saved");
    }

    private static void handleSweeperClean(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        ExtrasSweeper.cleanNow(player.server);
        ok(resp, "extras.msg.sweeper_cleaned");
    }

    private static void handleSweeperDustbin(ServerPlayer player, JsonObject req, JsonObject resp) {
        int index = req.has("index") ? req.get("index").getAsInt() : 0;
        ExtrasSweeper.openDustbin(player, index);
        ok(resp, "extras.msg.sweeper_saved");
    }

    private static void handleExchangeIn(ServerPlayer player, JsonObject req, JsonObject resp) {
        String itemId = req.has("item") ? req.get("item").getAsString() : "";
        int count = req.has("count") ? Math.max(1, req.get("count").getAsInt()) : 1;
        count = Math.min(count, ExtrasConfig.EXCHANGE_MAX_STACK);

        long rate = ExtrasConfigFile.rateOf(itemId);
        if (rate <= 0) { fail(resp, "extras.msg.exchange_unknown"); return; }

        int removed = removeFromInventory(player, itemId, count);
        if (removed <= 0) { fail(resp, "extras.msg.exchange_no_item"); return; }

        long gain;
        if (removed <= 0) gain = 0;
        else if (rate > Integer.MAX_VALUE / removed) gain = Integer.MAX_VALUE;
        else gain = rate * removed;
        SellBoxEconomy.addBalance(player, (int) gain);
        ok(resp, "extras.msg.exchange_in_ok", removed, gain);
        resp.addProperty("balance", SellBoxEconomy.getBalance(player));
    }

    private static void handleExchangeOut(ServerPlayer player, JsonObject req, JsonObject resp) {
        String itemId = req.has("item") ? req.get("item").getAsString() : "";
        int count = req.has("count") ? Math.max(1, req.get("count").getAsInt()) : 1;
        count = Math.min(count, ExtrasConfig.EXCHANGE_MAX_STACK);
        if (!ExtrasConfigFile.isExchangeOut()) { fail(resp, "extras.msg.exchange_out_disabled"); return; }

        long rate = ExtrasConfigFile.rateOf(itemId);
        if (rate <= 0) { fail(resp, "extras.msg.exchange_unknown"); return; }

        count = Math.min(count, 32);
        int balance = SellBoxEconomy.getBalance(player);
        if (balance < rate) { fail(resp, "extras.msg.balance_need", rate); return; }
        count = (int) Math.min(count, balance / rate);
        if (count <= 0) { fail(resp, "extras.msg.balance_need", rate); return; }
        long cost = rate * count;

        // 先校验物品 ID，再扣钱：否则无效 ID 会把金币扣掉却发不出物品
        var id = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        if (id == null) { fail(resp, "extras.msg.bad_item_id"); return; }
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            fail(resp, "extras.msg.bad_item_id");
            return;
        }
        // 金币是「获取即入账」的货币物品，不允许再从兑换表反向换出
        if (item == com.sellbox.mod.registry.ModItems.GOLD_COIN.get()
                || item == com.sellbox.mod.registry.ModItems.MONEY.get()) {
            fail(resp, "extras.msg.currency_not_withdrawable");
            return;
        }
        // 一次最多一组，且单次不超过 32 个
        count = Math.min(count, Math.min(32, Math.max(1, new ItemStack(item).getMaxStackSize())));
        cost = rate * count;

        if (!SellBoxEconomy.deductBalance(player, (int) cost)) {
            fail(resp, "extras.msg.deduct_failed");
            return;
        }

        List<ItemStack> items = new ArrayList<>();
        int remaining = count;
        while (remaining > 0) {
            int batch = Math.min(remaining, 64);
            items.add(new ItemStack(item, batch));
            remaining -= batch;
        }
        claimRewards(player, 0, items);

        ok(resp, "extras.msg.exchange_out_ok", cost, count);
        resp.addProperty("balance", SellBoxEconomy.getBalance(player));
    }

    private static int removeFromInventory(ServerPlayer player, String itemId, int wanted) {
        var id = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        if (id == null) return 0;
        var targetItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);

        int remaining = wanted;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || s.getItem() != targetItem) continue;
            int take = Math.min(remaining, s.getCount());
            s.shrink(take);
            remaining -= take;
            if (s.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
        }
        return wanted - remaining;
    }

    // ════════════════════════════════════════════
    //  兑换码
    // ════════════════════════════════════════════

    private static void handleRedeem(ServerPlayer player, JsonObject req, JsonObject resp) {
        String codeStr = req.has("code") ? req.get("code").getAsString().trim() : "";
        if (codeStr.isEmpty()) { fail(resp, "extras.msg.redeem_empty"); return; }

        ExtrasConfigFile.RedeemCode code = ExtrasConfigFile.codeOf(codeStr);
        if (code == null) { fail(resp, "extras.msg.redeem_missing"); return; }
        if (code.expired()) { fail(resp, "extras.msg.redeem_expired"); return; }
        if (!code.usable()) { fail(resp, "extras.msg.redeem_limit"); return; }

        String uuid = player.getUUID().toString();
        if (code.oncePerPlayer && code.claimedBy.contains(uuid)) {
            fail(resp, "extras.msg.redeem_used");
            return;
        }

        code.claimedBy.add(uuid);
        if (code.maxUses >= 0) code.uses++;
        ExtrasConfigFile.markDirty();

        claimRewards(player, code.money, new ArrayList<>(code.items));

        String title = code.title == null || code.title.isEmpty()
                ? net.minecraft.network.chat.Component.translatable("extras.redeem.default_title").getString()
                : code.title;
        ok(resp, "extras.msg.redeem_ok", title, code.money, countItems(code.items));
        resp.addProperty("balance", SellBoxEconomy.getBalance(player));
    }

    // ════════════════════════════════════════════
    //  好友
    // ════════════════════════════════════════════

    private static void handleFriendAdd(ServerPlayer player, JsonObject req, JsonObject resp) {
        String name = req.has("name") ? req.get("name").getAsString().trim() : "";
        if (name.isEmpty()) { fail(resp, "extras.msg.friend_no_name"); return; }

        ServerPlayer target = player.server.getPlayerList().getPlayerByName(name);
        if (target == null) { fail(resp, "extras.msg.player_offline", name); return; }
        if (target.getUUID().equals(player.getUUID())) { fail(resp, "extras.msg.friend_self"); return; }

        ExtrasData mine = ExtrasManager.get(player.getUUID());
        if (mine.friends.containsKey(target.getUUID())) { fail(resp, "extras.msg.friend_exists"); return; }
        if (mine.friends.size() >= ExtrasConfig.MAX_FRIENDS) { fail(resp, "extras.msg.friend_full"); return; }

        ExtrasData theirs = ExtrasManager.get(target.getUUID());
        if (theirs.friendRequests.size() >= ExtrasConfig.MAX_FRIEND_REQUESTS) {
            fail(resp, "extras.msg.friend_req_full");
            return;
        }
        if (theirs.friends.containsKey(player.getUUID())) {
            mine.friends.put(target.getUUID(), target.getName().getString());
            ok(resp, "extras.msg.friend_exists_ok");
            return;
        }

        theirs.friendRequests.add(player.getUUID());
        ExtrasManager.markDirty();
        // 对方开着扩展面板时也要立刻看到新的好友申请
        sendState(target);

        target.displayClientMessage(
                Component.translatable("extras.msg.friend_request_notice",
                        player.getName().getString()).withStyle(net.minecraft.ChatFormatting.YELLOW), false);
        ok(resp, "extras.msg.friend_sent", target.getName().getString());
    }

    private static void handleFriendAccept(ServerPlayer player, JsonObject req, JsonObject resp) {
        String uuidStr = req.has("uuid") ? req.get("uuid").getAsString() : "";
        UUID from;
        try { from = UUID.fromString(uuidStr); } catch (Throwable t) { fail(resp, "extras.msg.bad_uuid"); return; }

        ExtrasData mine = ExtrasManager.get(player.getUUID());
        if (mine.friends.size() >= ExtrasConfig.MAX_FRIENDS) { fail(resp, "extras.msg.friend_full"); return; }
        if (!mine.friendRequests.remove(from)) { fail(resp, "extras.msg.friend_req_missing"); return; }

        String name = nameOf(player.server, from);
        mine.friends.put(from, name);
        ExtrasData theirs = ExtrasManager.find(from);
        if (theirs != null) {
            if (theirs.friends.size() >= ExtrasConfig.MAX_FRIENDS) {
                // 对方好友已满：把申请放回去，别静默失败
                mine.friends.remove(from);
                mine.friendRequests.add(from);
                fail(resp, "extras.msg.friend_full");
                return;
            }
            theirs.friends.put(player.getUUID(), player.getName().getString());
        }
        ExtrasManager.markDirty();
        // 对方的面板（如果开着）也要立刻看到好友列表变化
        ServerPlayer other = player.server.getPlayerList().getPlayer(from);
        if (other != null) sendState(other);

        ok(resp, "extras.msg.friend_accepted", name);
    }

    private static void handleFriendRemove(ServerPlayer player, JsonObject req, JsonObject resp) {
        String uuidStr = req.has("uuid") ? req.get("uuid").getAsString() : "";
        UUID target;
        try { target = UUID.fromString(uuidStr); } catch (Throwable t) { fail(resp, "extras.msg.bad_uuid"); return; }

        ExtrasData mine = ExtrasManager.get(player.getUUID());
        mine.friends.remove(target);
        mine.friendRequests.remove(target);
        // 用 find 而不是 get：任意 UUID 的删除请求不应该创建新的玩家数据文件
        ExtrasData theirs = ExtrasManager.find(target);
        if (theirs != null) theirs.friends.remove(player.getUUID());
        ExtrasManager.markDirty();
        ServerPlayer other = player.server.getPlayerList().getPlayer(target);
        if (other != null) sendState(other);

        ok(resp, "extras.msg.friend_removed");
    }

    /** 玩家名 → UUID：先查在线，再查 profile 缓存（离线也能找到） */
    public static UUID resolveUuid(MinecraftServer server, String name) {
        if (server == null || name == null || name.isEmpty()) return null;
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return online.getUUID();
        var cache = server.getProfileCache();
        if (cache != null) {
            var opt = cache.get(name);
            if (opt.isPresent()) return opt.get().getId();
        }
        return null;
    }

    private static String nameOf(MinecraftServer server, UUID uuid) {
        ServerPlayer p = server.getPlayerList().getPlayer(uuid);
        if (p != null) return p.getName().getString();
        var cache = server.getProfileCache();
        if (cache != null) {
            var opt = cache.get(uuid);
            if (opt.isPresent()) return opt.get().getName();
        }
        return uuid.toString().substring(0, 8);
    }


    // ════════════════════════════════════════════
    //  管理员操作（需要权限等级 2）
    // ════════════════════════════════════════════

    /** 所有 admin_* 动作的统一权限闸门 */
    private static boolean requireAdmin(ServerPlayer player, JsonObject resp) {
        if (adminMode.contains(player.getUUID())) return true;
        fail(resp, "extras.msg.no_permission");
        return false;
    }

    /** 设置兑换单价（物品 ID 由客户端从手持物/注册表带入） */
    private static void handleAdminRateSet(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        String itemId = req.has("item") ? req.get("item").getAsString().trim() : "";
        long value = req.has("value") ? req.get("value").getAsLong() : 0L;
        if (itemId.isEmpty()) { fail(resp, "extras.msg.bad_item_id"); return; }
        if (value <= 0) { fail(resp, "extras.msg.admin_bad_value"); return; }

        ExtrasConfigFile.setRate(itemId, value);
        ExtrasConfigFile.save();
        ok(resp, "extras.msg.admin_rate_ok", itemId, value);
    }

    /** 把手里拿着的物品加进兑换表 */
    private static void handleAdminRateHold(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        ItemStack held = selectedItem(player, req);
        if (held.isEmpty()) { fail(resp, "extras.msg.admin_empty_hand"); return; }
        long value = req.has("value") ? req.get("value").getAsLong() : 0L;
        if (value <= 0) { fail(resp, "extras.msg.admin_bad_value"); return; }

        String itemId = ExtrasItems.keyOf(held);
        ExtrasConfigFile.setRate(itemId, value);
        ExtrasConfigFile.save();
        ok(resp, "extras.msg.admin_rate_ok", itemId, value);
    }

    private static void handleAdminRateRemove(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        String itemId = req.has("item") ? req.get("item").getAsString().trim() : "";
        if (itemId.isEmpty()) { fail(resp, "extras.msg.bad_item_id"); return; }
        ExtrasConfigFile.removeRate(itemId);
        ExtrasConfigFile.save();
        ok(resp, "extras.msg.admin_rate_removed", itemId);
    }

    private static void handleAdminCodeCreate(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        String code = req.has("code") ? req.get("code").getAsString().trim() : "";
        long money = req.has("money") ? req.get("money").getAsLong() : 0L;
        int maxUses = req.has("maxUses") ? req.get("maxUses").getAsInt() : -1;
        if (maxUses < -1) maxUses = -1;
        if (maxUses > 1_000_000) maxUses = 1_000_000;
        if (code.isEmpty()) { fail(resp, "extras.msg.redeem_empty"); return; }
        if (code.length() > 32) { fail(resp, "extras.msg.admin_bad_value"); return; }

        code = code.toUpperCase(Locale.ROOT);
        if (ExtrasConfigFile.codeOf(code) != null) {
            fail(resp, "extras.msg.admin_code_exists", code);
            return;
        }
        ExtrasConfigFile.RedeemCode c = new ExtrasConfigFile.RedeemCode();
        c.code = code;
        c.title = "";
        c.money = Math.max(0L, money);
        c.maxUses = maxUses;
        c.oncePerPlayer = true;
        // 创建时把手持物当奖励（可选）
        if (!player.getMainHandItem().isEmpty()) {
            c.items.add(player.getMainHandItem().copy());
        }
        ExtrasConfigFile.putCode(c);
        ExtrasConfigFile.save();
        ok(resp, "extras.msg.admin_code_ok", code, c.money);
    }

    private static void handleAdminCodeRemove(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        String code = req.has("code") ? req.get("code").getAsString().trim() : "";
        if (code.isEmpty()) { fail(resp, "extras.msg.redeem_empty"); return; }
        ExtrasConfigFile.removeCode(code);
        ExtrasConfigFile.save();
        ok(resp, "extras.msg.admin_code_removed", code.toUpperCase(Locale.ROOT));
    }

    // ════════════════════════════════════════════
    //  邮箱互联
    // ════════════════════════════════════════════

    /** 玩家 → 玩家发邮件（纯文字 + 可选手持物 + 可选金币） */
    private static void handlePlayerMailSend(ServerPlayer player, JsonObject req, JsonObject resp) {
        String targetName = req.has("target") ? req.get("target").getAsString().trim() : "";
        String title = req.has("title") ? req.get("title").getAsString().trim() : "";
        String content = req.has("content") ? req.get("content").getAsString() : "";
        long money = req.has("money") ? req.get("money").getAsLong() : 0L;
        boolean attach = req.has("attach") && req.get("attach").getAsBoolean();
        boolean hasAttachment = attach && !player.getMainHandItem().isEmpty();

        if (targetName.isEmpty()) { fail(resp, "extras.msg.mail_target_empty"); return; }
        if (title.length() > ExtrasConfig.MAIL_TITLE_MAX || content.length() > ExtrasConfig.MAIL_CONTENT_MAX) {
            fail(resp, "extras.msg.mail_too_long", ExtrasConfig.MAIL_TITLE_MAX, ExtrasConfig.MAIL_CONTENT_MAX);
            return;
        }
        if (title.isEmpty() && content.isEmpty() && money <= 0 && !hasAttachment) {
            fail(resp, "extras.msg.mail_empty");
            return;
        }
        if (money < 0 || money > ExtrasConfig.TRANSFER_MAX) {
            fail(resp, "extras.msg.transfer_too_large", ExtrasConfig.TRANSFER_MAX);
            return;
        }

        ServerPlayer target = player.server.getPlayerList().getPlayerByName(targetName);
        if (target == null) { fail(resp, "extras.msg.player_offline", targetName); return; }
        if (target.getUUID().equals(player.getUUID())) { fail(resp, "extras.msg.transfer_self"); return; }

        String err = ExtrasMailbox.sendTo(player, target, title, content, money, attach);
        if (err != null) { fail(resp, err); return; }

        ok(resp, "extras.msg.mail_sent", target.getName().getString());
        resp.addProperty("balance", SellBoxEconomy.getBalance(player));
    }

    /**
     * 管理员群发：{@code toAll = true} 时发给所有已知玩家（含离线），
     * 否则等同单发。{@code welcome = true} 时同时存成新玩家欢迎邮件。
     */
    private static void handleAdminMailBroadcast(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        String title = req.has("title") ? req.get("title").getAsString() : "";
        String content = req.has("content") ? req.get("content").getAsString() : "";
        long money = req.has("money") ? req.get("money").getAsLong() : 0L;
        boolean welcome = req.has("welcome") && req.get("welcome").getAsBoolean();
        if (title.length() > ExtrasConfig.MAIL_TITLE_MAX || content.length() > ExtrasConfig.MAIL_CONTENT_MAX) {
            fail(resp, "extras.msg.mail_too_long", ExtrasConfig.MAIL_TITLE_MAX, ExtrasConfig.MAIL_CONTENT_MAX);
            return;
        }

        if (title.isEmpty() && content.isEmpty() && player.getMainHandItem().isEmpty() && money <= 0) {
            fail(resp, "extras.msg.mail_empty");
            return;
        }

        ExtrasData.Mail proto = new ExtrasData.Mail();
        proto.title = title;
        proto.content = content;
        proto.kind = "admin";
        // 以服务器名义：不写管理员名字，正文由客户端本地化成「服务器」
        proto.sender = "";
        proto.fromServer = true;
        proto.money = Math.max(0L, money);
        if (!player.getMainHandItem().isEmpty()) {
            proto.items.add(player.getMainHandItem().copy());
        }

        int n = ExtrasMailbox.broadcast(player.server, proto, false);

        if (welcome) {
            ExtrasConfigFile.DefaultMail dm = new ExtrasConfigFile.DefaultMail();
            dm.title = title;
            dm.content = content;
            dm.money = proto.money;
            dm.items.addAll(proto.items);
            ExtrasConfigFile.addWelcomeMail(dm);
            ExtrasConfigFile.save();
        }

        ok(resp, "extras.msg.admin_broadcast_ok", n);
    }

    /** 把当前手持物 + 表单内容存成「新玩家欢迎邮件」 */
    private static void handleAdminWelcomeAdd(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        String title = req.has("title") ? req.get("title").getAsString() : "";
        String content = req.has("content") ? req.get("content").getAsString() : "";
        long money = req.has("money") ? req.get("money").getAsLong() : 0L;
        if (title.length() > ExtrasConfig.MAIL_TITLE_MAX || content.length() > ExtrasConfig.MAIL_CONTENT_MAX) {
            fail(resp, "extras.msg.mail_too_long", ExtrasConfig.MAIL_TITLE_MAX, ExtrasConfig.MAIL_CONTENT_MAX);
            return;
        }

        ExtrasConfigFile.DefaultMail dm = new ExtrasConfigFile.DefaultMail();
        dm.title = title;
        dm.content = content;
        dm.money = Math.max(0L, money);
        if (!player.getMainHandItem().isEmpty()) {
            dm.items.add(player.getMainHandItem().copy());
        }
        if (dm.isEmpty()) { fail(resp, "extras.msg.mail_empty"); return; }

        ExtrasConfigFile.addWelcomeMail(dm);
        ExtrasConfigFile.save();
        ok(resp, "extras.msg.admin_welcome_added", ExtrasConfigFile.getWelcomeMails().size());
    }

    private static void handleAdminWelcomeClear(ServerPlayer player, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        ExtrasConfigFile.clearWelcomeMails();
        ExtrasConfigFile.save();
        ok(resp, "extras.msg.admin_welcome_cleared");
    }

    /** 设置箱体保底：品质 + 次数（count<=0 或品质 common 即关闭） */
    private static void handleAdminCratePity(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        String id = req.has("crate") ? req.get("crate").getAsString() : "";
        String rarity = req.has("rarity") ? req.get("rarity").getAsString() : "common";
        int count = req.has("count") ? req.get("count").getAsInt() : 0;
        if (ExtrasConfigFile.crateOf(id) == null) { fail(resp, "extras.msg.crate_not_found"); return; }
        ExtrasConfigFile.setCratePity(id, rarity, count);
        ExtrasConfigFile.save();
        ok(resp, "extras.msg.admin_pity_ok", count);
    }

    /** 就地修改奖池某一项的权重 / 品质 */
    private static void handleAdminCrateItemSet(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        String id = req.has("crate") ? req.get("crate").getAsString() : "";
        int index = req.has("index") ? req.get("index").getAsInt() : -1;
        boolean hasChance = req.has("chance");
        boolean hasRarity = req.has("rarity");
        double chance = hasChance ? req.get("chance").getAsDouble() : 0;
        String rarity = hasRarity ? req.get("rarity").getAsString() : "common";
        if (!ExtrasConfigFile.setCrateItem(id, index, hasChance, chance, hasRarity, rarity)) {
            fail(resp, "extras.msg.admin_bad_value");
            return;
        }
        ExtrasConfigFile.save();
        ok(resp, "extras.msg.admin_item_set_ok");
    }

    /** 改里程碑金币 */
    private static void handleAdminMileMoney(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        int day = req.has("day") ? req.get("day").getAsInt() : 0;
        long money = req.has("money") ? req.get("money").getAsLong() : 0L;
        ExtrasConfigFile.setMilestoneMoney(day, money);
        ExtrasConfigFile.save();
        ok(resp, "extras.msg.admin_mile_money_ok", day, money);
    }

    /** 修改里程碑天数/金币（保留物品） */
    private static void handleAdminMileUpdate(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        int oldDay = req.has("oldDay") ? req.get("oldDay").getAsInt() : 0;
        int newDay = req.has("day") ? req.get("day").getAsInt() : 0;
        long money = req.has("money") ? req.get("money").getAsLong() : 0L;
        if (oldDay <= 0 || newDay <= 0 || newDay > 3650) { fail(resp, "extras.msg.admin_bad_value"); return; }
        if (!ExtrasConfigFile.updateMilestone(oldDay, newDay, money)) {
            fail(resp, "extras.msg.admin_bad_value"); return;
        }
        ExtrasConfigFile.save();
        ok(resp, "extras.msg.admin_mile_money_ok", newDay, money);
    }

    /** 删里程碑里的第 index 件物品 */
    private static void handleAdminMileItemDel(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        int day = req.has("day") ? req.get("day").getAsInt() : 0;
        int index = req.has("index") ? req.get("index").getAsInt() : -1;
        ExtrasConfigFile.removeMilestoneItem(day, index);
        ExtrasConfigFile.save();
        ok(resp, "extras.msg.admin_mile_item_removed");
    }

    private static void handleAdminMailSend(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        String targetName = req.has("target") ? req.get("target").getAsString().trim() : "";
        String title = req.has("title") ? req.get("title").getAsString() : "";
        String content = req.has("content") ? req.get("content").getAsString() : "";
        long money = req.has("money") ? req.get("money").getAsLong() : 0L;
        if (title.length() > ExtrasConfig.MAIL_TITLE_MAX || content.length() > ExtrasConfig.MAIL_CONTENT_MAX) {
            fail(resp, "extras.msg.mail_too_long", ExtrasConfig.MAIL_TITLE_MAX, ExtrasConfig.MAIL_CONTENT_MAX);
            return;
        }
        if (targetName.isEmpty()) { fail(resp, "extras.msg.mail_target_empty"); return; }
        if (title.isEmpty() && content.isEmpty() && player.getMainHandItem().isEmpty() && money <= 0) {
            fail(resp, "extras.msg.mail_empty");
            return;
        }

        // 离线玩家也能收：按名字查 profile 缓存拿 UUID
        UUID targetId = resolveUuid(player.server, targetName);
        if (targetId == null) { fail(resp, "extras.msg.player_offline", targetName); return; }

        ExtrasData.Mail m = new ExtrasData.Mail();
        m.title = title;
        m.content = content;
        m.kind = "admin";
        // 以服务器名义
        m.sender = "";
        m.fromServer = true;
        m.money = Math.max(0L, money);
        if (!player.getMainHandItem().isEmpty()) {
            m.items.add(player.getMainHandItem().copy());
        }
        ExtrasManager.get(targetId).addMail(m);
        ExtrasManager.markDirty();
        ServerPlayer online = player.server.getPlayerList().getPlayer(targetId);
        if (online != null) {
            sendState(online);
            ExtrasNotices.mail(online, "extras.notice.mail.title", new Object[0],
                    "extras.notice.mail.admin", new Object[0]);
        }

        ok(resp, "extras.msg.admin_mail_ok", targetName);
    }

    // ════════════════════════════════════════════
    //  开箱
    // ════════════════════════════════════════════

    /** 玩家上次开箱时间（防脚本连点） */
    private static final Map<UUID, Long> lastCrateOpen = new ConcurrentHashMap<>();
    /** 开箱用的随机源（复用，避免每次 new） */
    private static final java.util.Random CRATE_RND = new java.util.Random();

    /**
     * 开箱。支持一次连开 {@code count} 个（1 ~ {@link ExtrasConfig#CRATE_MAX_BATCH}），
     * 连开时价格按次累计、只扣一次，不播转盘只回汇总清单。
     */
    private static void handleCrateOpen(ServerPlayer player, JsonObject req, JsonObject resp) {
        String crateId = req.has("crate") ? req.get("crate").getAsString() : "";
        ExtrasConfigFile.CrateSet crate = ExtrasConfigFile.crateOf(crateId);
        if (crate == null) { fail(resp, "extras.msg.crate_not_found"); return; }
        boolean hasValidItem = false;
        for (ExtrasConfigFile.CrateItem it : crate.items) {
            if (it.stack != null && !it.stack.isEmpty()) { hasValidItem = true; break; }
        }
        if (!hasValidItem) { fail(resp, "extras.msg.crate_empty"); return; }

        int count = req.has("count") ? req.get("count").getAsInt() : 1;
        count = Math.max(1, Math.min(ExtrasConfig.CRATE_MAX_BATCH, count));

        long now = System.currentTimeMillis();
        Long last = lastCrateOpen.get(player.getUUID());
        if (last != null && now - last < ExtrasConfig.CRATE_COOLDOWN_MS) {
            fail(resp, "extras.msg.too_fast");
            return;
        }

        long unit = Math.max(0L, crate.price);
        long price = unit * count;
        int balance = SellBoxEconomy.getBalance(player);
        if (price > 0 && balance < price) {
            fail(resp, "extras.msg.balance_low", balance);
            return;
        }
        if (price > 0 && !SellBoxEconomy.deductBalance(player, (int) price)) {
            fail(resp, "extras.msg.balance_low", balance);
            return;
        }
        lastCrateOpen.put(player.getUUID(), now);

        ExtrasData data = ExtrasManager.get(player.getUUID());
        List<ItemStack> drops = new ArrayList<>();
        List<ExtrasConfigFile.CrateItem> pickedOnes = new ArrayList<>();
        for (int n = 0; n < count; n++) {
            ExtrasConfigFile.CrateItem picked = pickCrateItem(crate, data);
            pickedOnes.add(picked);
            drops.add(picked.stack.copy());
        }
        ExtrasManager.markDirty();
        claimRewards(player, 0, drops);

        resp.addProperty("crate", crate.id);
        resp.addProperty("count", count);
        resp.addProperty("balance", SellBoxEconomy.getBalance(player));
        // 保底进度：还差几次触发；pityMax = 0 表示该箱没开保底
        resp.addProperty("pity", data.cratePity.getOrDefault(crate.id, 0));
        resp.addProperty("pityMax", crate.pityEnabled() ? crate.pityCount : 0);

        if (count == 1) {
            ExtrasConfigFile.CrateItem picked = pickedOnes.get(0);
            ItemStack won = drops.get(0);

            // 转盘条：随机填充 + 在固定位置塞中奖物，客户端据此播动画
            List<ExtrasConfigFile.CrateItem> reelPool = new ArrayList<>();
            for (ExtrasConfigFile.CrateItem it : crate.items) {
                if (it.stack != null && !it.stack.isEmpty()) reelPool.add(it);
            }
            JsonArray reel = new JsonArray();
            int stopAt = Math.min(ExtrasConfig.CRATE_REEL_STOP, ExtrasConfig.CRATE_REEL_SIZE - 1);
            for (int i = 0; i < ExtrasConfig.CRATE_REEL_SIZE; i++) {
                ExtrasConfigFile.CrateItem src = (i == stopAt)
                        ? picked
                        : reelPool.get(CRATE_RND.nextInt(reelPool.size()));
                ItemStack s = src.stack.copy();
                s.setCount(1);
                JsonObject ro = new JsonObject();
                ro.addProperty("name", ExtrasItems.nameOf(s));
                ro.addProperty("id", ExtrasItems.keyOf(s));
                ro.addProperty("rarity", src.rarity);
                reel.add(ro);
            }

            ok(resp, "extras.msg.crate_won", ExtrasItems.nameOf(won), won.getCount());
            resp.addProperty("stopAt", stopAt);
            resp.add("reel", reel);
            JsonObject wo = new JsonObject();
            wo.addProperty("name", ExtrasItems.nameOf(won));
            wo.addProperty("id", ExtrasItems.keyOf(won));
            wo.addProperty("count", won.getCount());
            wo.addProperty("rarity", picked.rarity);
            resp.add("won", wo);

        } else {
            ok(resp, "extras.msg.crate_won_batch", count);
            JsonArray wons = new JsonArray();
            for (int i = 0; i < drops.size(); i++) {
                ItemStack s = drops.get(i);
                JsonObject wo = new JsonObject();
                wo.addProperty("name", ExtrasItems.nameOf(s));
                wo.addProperty("id", ExtrasItems.keyOf(s));
                wo.addProperty("count", s.getCount());
                wo.addProperty("rarity", pickedOnes.get(i).rarity);
                wons.add(wo);
            }
            resp.add("wons", wons);

        }
    }

    /**
     * 抽一件奖品（带保底）。
     * <p>
     * 箱子配了 {@code pityRarity + pityCount} 时，累计 {@code pityCount} 次没开出
     * 「该品质及以上」就强制从满足条件的奖品里抽；开出高品质则计数归零。
     */
    private static ExtrasConfigFile.CrateItem pickCrateItem(ExtrasConfigFile.CrateSet crate, ExtrasData data) {
        boolean pity = crate.pityEnabled();
        int streak = data.cratePity.getOrDefault(crate.id, 0);

        ExtrasConfigFile.CrateItem picked;
        boolean forced = false;
        if (pity && streak + 1 >= crate.pityCount) {
            List<ExtrasConfigFile.CrateItem> pool = ExtrasConfigFile.itemsAtLeast(crate, crate.pityRarity);
            // 配了保底但奖池里没有目标品质：不要假装触发保底并把计数清零
            if (!pool.isEmpty()) {
                picked = weightedPick(pool, crate);
                forced = true;
            } else {
                picked = weightedPick(crate.items, crate);
            }
        } else {
            picked = weightedPick(crate.items, crate);
        }

        if (pity) {
            boolean hit = ExtrasConfigFile.rarityRank(picked.rarity)
                    >= ExtrasConfigFile.rarityRank(crate.pityRarity);
            data.cratePity.put(crate.id, (forced || hit) ? 0 : streak + 1);
        }
        return picked;
    }

    /** 按权重从给定池子里抽一件（自动跳过空物品，防止损坏配置抽到 null/空栈） */
    private static ExtrasConfigFile.CrateItem weightedPick(List<ExtrasConfigFile.CrateItem> pool,
                                                           ExtrasConfigFile.CrateSet crate) {
        List<ExtrasConfigFile.CrateItem> candidates = new ArrayList<>();
        if (pool != null) {
            for (ExtrasConfigFile.CrateItem it : pool) {
                if (it.stack != null && !it.stack.isEmpty()) candidates.add(it);
            }
        }
        if (candidates.isEmpty()) {
            for (ExtrasConfigFile.CrateItem it : crate.items) {
                if (it.stack != null && !it.stack.isEmpty()) candidates.add(it);
            }
        }
        if (candidates.isEmpty()) return null;
        double total = 0;
        for (ExtrasConfigFile.CrateItem it : candidates) total += Math.max(0, it.chance);
        if (total <= 0) return candidates.get(CRATE_RND.nextInt(candidates.size()));
        double roll = CRATE_RND.nextDouble() * total;
        double acc = 0;
        ExtrasConfigFile.CrateItem picked = candidates.get(candidates.size() - 1);
        for (ExtrasConfigFile.CrateItem it : candidates) {
            acc += Math.max(0, it.chance);
            if (roll <= acc) { picked = it; break; }
        }
        return picked;
    }

    // ════════════════════════════════════════════
    //  管理员：签到规则 / 开箱箱体
    // ════════════════════════════════════════════

    private static void handleAdminSigninSet(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        String mode = req.has("mode") ? req.get("mode").getAsString() : "linear";
        long base = req.has("base") ? req.get("base").getAsLong() : 0L;
        long step = req.has("step") ? req.get("step").getAsLong() : 0L;
        int cap = req.has("cap") ? req.get("cap").getAsInt() : 10;
        int day = req.has("day") ? req.get("day").getAsInt() : 0;
        long exact = req.has("exact") ? req.get("exact").getAsLong() : -1L;

        ExtrasConfigFile.setSigninMode(mode);
        ExtrasConfigFile.setSigninNumbers(base, step, cap);
        if (day > 0 && exact >= 0) ExtrasConfigFile.setSigninExact(day, exact);
        ExtrasConfigFile.save();
        ok(resp, "extras.msg.admin_signin_saved");
    }

    private static void handleAdminSigninMode(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        ExtrasConfigFile.setSigninMode(req.has("mode") ? req.get("mode").getAsString() : "linear");
        ExtrasConfigFile.save();
        ok(resp, "extras.msg.admin_signin_saved");
    }

    /** 加/更新一条里程碑：day + money + 手持物当额外奖励 */
    private static void handleAdminSigninMile(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        int day = req.has("day") ? req.get("day").getAsInt() : 0;
        long money = req.has("money") ? req.get("money").getAsLong() : 0L;
        if (day <= 0 || day > 3650) { fail(resp, "extras.msg.admin_bad_value"); return; }
        ExtrasConfigFile.Milestone m = ExtrasConfigFile.putMilestone(day, money, selectedItem(player, req));
        ExtrasConfigFile.save();
        ok(resp, "extras.msg.admin_mile_ok", day, m == null ? 0 : m.items.size());
    }

    private static void handleAdminSigninMileDel(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        int day = req.has("day") ? req.get("day").getAsInt() : 0;
        ExtrasConfigFile.removeMilestone(day);
        ExtrasConfigFile.save();
        ok(resp, "extras.msg.admin_mile_removed", day);
    }

    private static void handleAdminCrateCreate(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        String name = req.has("name") ? req.get("name").getAsString().trim() : "";
        if (name.isEmpty() || name.length() > 32) { fail(resp, "extras.msg.admin_bad_value"); return; }
        if (ExtrasConfigFile.getCrates().size() >= ExtrasConfig.CRATE_MAX_SETS) { fail(resp, "extras.msg.admin_bad_value"); return; }
        ExtrasConfigFile.CrateSet c = ExtrasConfigFile.createCrate(name);
        ExtrasConfigFile.save();
        ok(resp, "extras.msg.admin_crate_created", c.id);
    }

    private static void handleAdminCrateRemove(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        String id = req.has("crate") ? req.get("crate").getAsString() : "";
        ExtrasConfigFile.removeCrate(id);
        ExtrasManager.clearCratePity(id);
        ExtrasConfigFile.save();
        ok(resp, "extras.msg.admin_crate_removed", id);
    }

    private static void handleAdminCratePrice(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        String id = req.has("crate") ? req.get("crate").getAsString() : "";
        long price = req.has("price") ? req.get("price").getAsLong() : 0L;
        if (ExtrasConfigFile.crateOf(id) == null) { fail(resp, "extras.msg.crate_not_found"); return; }
        ExtrasConfigFile.setCratePrice(id, price);
        ExtrasConfigFile.save();
        ok(resp, "extras.msg.admin_crate_price_ok", price);
    }

    /** 保存箱名 + 单价（管理页的「保存」按钮） */
    private static void handleAdminCrateUpdate(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        String id = req.has("crate") ? req.get("crate").getAsString() : "";
        String name = req.has("name") ? req.get("name").getAsString().trim() : "";
        long price = req.has("price") ? req.get("price").getAsLong() : 0L;
        if (ExtrasConfigFile.crateOf(id) == null) { fail(resp, "extras.msg.crate_not_found"); return; }
        if (name.isEmpty() || name.length() > 32) { fail(resp, "extras.msg.admin_bad_value"); return; }
        ExtrasConfigFile.setCrateName(id, name);
        ExtrasConfigFile.setCratePrice(id, price);
        ExtrasConfigFile.save();
        ok(resp, "extras.msg.admin_crate_saved", price);
    }

    private static void handleAdminCrateItemAdd(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        String id = req.has("crate") ? req.get("crate").getAsString() : "";
        double chance = req.has("chance") ? req.get("chance").getAsDouble() : 1.0;
        String rarity = req.has("rarity") ? req.get("rarity").getAsString() : "common";
        ItemStack held = selectedItem(player, req);
        if (held.isEmpty()) { fail(resp, "extras.msg.admin_empty_hand"); return; }
        if (!ExtrasConfigFile.addCrateItem(id, held, chance, rarity)) {
            fail(resp, "extras.msg.admin_bad_value");
            return;
        }
        ExtrasConfigFile.save();
        ok(resp, "extras.msg.admin_crate_item_ok", ExtrasItems.nameOf(held), chance);
    }

    /** 优先读取客户端选中的物品；旧包/没带 item 时回落到手持物。strict=false 时剥离 NBT。 */
    private static ItemStack selectedItem(ServerPlayer player, JsonObject req) {
        if (req.has("item") && req.get("item").isJsonObject()) {
            ItemStack s = ExtrasConfigFile.readItem(req.getAsJsonObject("item"));
            if (!s.isEmpty()) {
                boolean strict = !req.has("strict") || req.get("strict").getAsBoolean();
                return strict ? s : new ItemStack(s.getItem(), s.getCount());
            }
        }
        return player.getMainHandItem();
    }

    private static void handleAdminCrateIcon(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        String id = req.has("crate") ? req.get("crate").getAsString() : "";
        ItemStack icon = selectedItem(player, req);
        if (icon.isEmpty()) { fail(resp, "extras.msg.admin_empty_hand"); return; }
        if (!ExtrasConfigFile.setCrateIcon(id, icon)) { fail(resp, "extras.msg.admin_bad_value"); return; }
        ExtrasConfigFile.save();
        ok(resp, "extras.msg.admin_crate_icon_ok");
    }

    private static void handleAdminCrateItemDel(ServerPlayer player, JsonObject req, JsonObject resp) {
        if (!requireAdmin(player, resp)) return;
        String id = req.has("crate") ? req.get("crate").getAsString() : "";
        int index = req.has("index") ? req.get("index").getAsInt() : -1;
        ExtrasConfigFile.removeCrateItem(id, index);
        ExtrasConfigFile.save();
        ok(resp, "extras.msg.admin_crate_item_removed");
    }

    // ════════════════════════════════════════════
    //  状态同步
    // ════════════════════════════════════════════

    public static void sendState(ServerPlayer player) {
        try {
            ExtrasData data = ExtrasManager.get(player.getUUID());
            JsonObject o = new JsonObject();
            o.addProperty("balance", SellBoxEconomy.getBalance(player));

            JsonObject sign = new JsonObject();
            int liveStreak = data.effectiveStreak();
            sign.addProperty("streak", liveStreak);
            sign.addProperty("best", data.bestStreak);
            sign.addProperty("today", data.signedToday());
            sign.addProperty("lastDay", data.lastSignDay);
            JsonArray days = new JsonArray();
            for (long d : data.signedDays) days.add(d);
            sign.add("days", days);
            JsonArray miles = new JsonArray();
            // 断签后本轮里程已失效，界面不要再显示为已领取
            if (liveStreak > 0) {
                for (int m : data.claimedMilestones) miles.add(m);
            }
            sign.add("milestones", miles);
            o.add("sign", sign);

            JsonArray mails = new JsonArray();
            for (ExtrasData.Mail m : ExtrasData.windowForState(data.mails)) {
                JsonObject mo = new JsonObject();
                mo.addProperty("id", m.id);
                mo.addProperty("title", m.title);
                mo.addProperty("content", m.content);
                mo.addProperty("kind", m.kind);
                mo.addProperty("sender", m.sender);
                mo.addProperty("money", m.money);
                mo.addProperty("claimed", m.claimed);
                mo.addProperty("sentAt", m.sentAt);
                mo.addProperty("transferAmount", m.transferAmount);
                mo.addProperty("record", m.record);
                mo.addProperty("fromServer", m.fromServer);
                if (m.payload != null && !m.payload.isEmpty()) mo.addProperty("payload", m.payload);
                mo.addProperty("count", m.items.size());
                if (!m.items.isEmpty()) {
                    JsonArray its = new JsonArray();
                    for (ItemStack s : m.items) {
                        if (s == null || s.isEmpty()) continue;
                        JsonObject io = new JsonObject();
                        io.addProperty("id", ExtrasItems.keyOf(s));
                        io.addProperty("name", ExtrasItems.nameOf(s));
                        io.addProperty("count", s.getCount());
                        its.add(io);
                    }
                    mo.add("items", its);
                }
                mails.add(mo);
            }
            o.add("mails", mails);

            JsonArray ex = new JsonArray();
            for (var e : ExtrasConfigFile.getExchangeRates().entrySet()) {
                var id = net.minecraft.resources.ResourceLocation.tryParse(e.getKey());
                if (id == null) continue;
                ItemStack stack = new ItemStack(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id));
                JsonObject eo = new JsonObject();
                eo.addProperty("id", e.getKey());
                eo.addProperty("name", ExtrasItems.nameOf(stack));
                eo.addProperty("value", e.getValue());
                int owned = 0;
                for (ItemStack inv : player.getInventory().items) {
                    if (ExtrasItems.keyOf(inv).equals(e.getKey())) owned += inv.getCount();
                }
                for (ItemStack inv : player.getInventory().offhand) {
                    if (ExtrasItems.keyOf(inv).equals(e.getKey())) owned += inv.getCount();
                }
                eo.addProperty("owned", owned);
                ex.add(eo);
            }
            o.add("exchange", ex);
            o.addProperty("exchangeOut", ExtrasConfigFile.isExchangeOut());
            o.addProperty("autoExchange", ExtrasConfigFile.isAutoExchange());
            JsonObject sweeper = new JsonObject();
            sweeper.addProperty("enabled", ExtrasConfigFile.isSweeperEnabled());
            sweeper.addProperty("interval", ExtrasConfigFile.getSweeperInterval());
            sweeper.addProperty("overload", ExtrasConfigFile.getSweeperOverload());
            sweeper.addProperty("dustbins", ExtrasConfigFile.getSweeperDustbins());
            JsonArray sbl = new JsonArray();
            for (String v : ExtrasConfigFile.getSweeperBlacklist()) sbl.add(v);
            sweeper.add("blacklist", sbl);
            JsonArray swl = new JsonArray();
            for (String v : ExtrasConfigFile.getSweeperWhitelist()) swl.add(v);
            sweeper.add("whitelist", swl);
            JsonArray sel = new JsonArray();
            for (String v : ExtrasConfigFile.getSweeperExtraEntities()) sel.add(v);
            sweeper.add("extraEntities", sel);
            sweeper.addProperty("chatMessage", ExtrasConfigFile.getSweeperChatMessage());
            sweeper.addProperty("countdownMessage", ExtrasConfigFile.getSweeperCountdownMessage());
            sweeper.addProperty("afterMessage", ExtrasConfigFile.getSweeperAfterMessage());
            o.add("sweeper", sweeper);

            JsonArray friends = new JsonArray();
            for (var e : data.friends.entrySet()) {
                JsonObject fo = new JsonObject();
                fo.addProperty("uuid", e.getKey().toString());
                fo.addProperty("name", e.getValue());
                fo.addProperty("online", player.server.getPlayerList().getPlayer(e.getKey()) != null);
                friends.add(fo);
            }
            o.add("friends", friends);

            JsonArray reqs = new JsonArray();
            for (UUID u : data.friendRequests) {
                JsonObject ro = new JsonObject();
                ro.addProperty("uuid", u.toString());
                ro.addProperty("name", nameOf(player.server, u));
                reqs.add(ro);
            }
            o.add("requests", reqs);

            // 管理模式：额外下发兑换码列表（管理模式由 OP 指令开启，与权限等级无关）
            boolean admin = adminMode.contains(player.getUUID());
            o.addProperty("admin", admin);
            if (admin) {
                JsonArray codes = new JsonArray();
                for (ExtrasConfigFile.RedeemCode c : ExtrasConfigFile.getRedeemCodes().values()) {
                    JsonObject co = new JsonObject();
                    co.addProperty("code", c.code);
                    co.addProperty("title", c.title);
                    co.addProperty("money", c.money);
                    co.addProperty("uses", c.uses);
                    co.addProperty("maxUses", c.maxUses);
                    co.addProperty("claimed", c.claimedBy.size());
                    co.addProperty("expireAt", c.expireAt);
                    co.addProperty("itemCount", c.items.size());
                    codes.add(co);
                }
                o.add("codes", codes);

                // 在线玩家名（管理员发邮件时选人用）
                JsonArray onlines = new JsonArray();
                for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
                    onlines.add(p.getName().getString());
                }
                o.add("online", onlines);
            }

            // 签到规则（所有人都要，签到页要显示奖励数字）
            o.add("signinCfg", sendSigninConfig());

            // 开箱箱体（数量 / 名称 / 价格 + 奖品明细，客户端要画预览与动画转盘）
            JsonArray crates = new JsonArray();
            for (ExtrasConfigFile.CrateSet c : ExtrasConfigFile.getCrates()) {
                JsonObject co = new JsonObject();
                co.addProperty("id", c.id);
                co.addProperty("name", c.name);
                co.addProperty("price", c.price);
                co.addProperty("pityRarity", c.pityRarity);
                co.addProperty("pityCount", c.pityEnabled() ? c.pityCount : 0);
                co.addProperty("pityNow", data.cratePity.getOrDefault(c.id, 0));
                if (c.icon != null && !c.icon.isEmpty()) co.add("icon", ExtrasConfigFile.writeItem(c.icon));
                JsonArray its = new JsonArray();
                for (ExtrasConfigFile.CrateItem it : c.items) {
                    if (it.stack == null || it.stack.isEmpty()) continue;
                    JsonObject io = new JsonObject();
                    io.addProperty("id", ExtrasItems.keyOf(it.stack));
                    io.addProperty("name", ExtrasItems.nameOf(it.stack));
                    io.addProperty("count", it.stack.getCount());
                    io.addProperty("chance", it.chance);
                    io.addProperty("rarity", it.rarity);
                    its.add(io);
                }
                co.add("items", its);
                crates.add(co);
            }
            o.add("crates", crates);

            com.sellbox.mod.network.ModNetwork.sendExtrasState(player, o.toString());
        } catch (Throwable t) {
            SellBoxMod.LOGGER.error("Failed to send extras state", t);
        }
    }

    /** 物品列表里的总件数，用于消息参数 */
    private static int countItems(List<ItemStack> items) {
        int n = 0;
        for (ItemStack s : items) {
            if (s != null && !s.isEmpty()) n += s.getCount();
        }
        return n;
    }
}
