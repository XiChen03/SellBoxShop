package com.sellbox.mod.extras.client;

import com.google.gson.JsonObject;
import com.sellbox.mod.extras.ExtrasConfigFile;
import com.sellbox.mod.extras.ExtrasItems;
import net.minecraft.world.item.ItemStack;

/**
 * 客户端请求构造函数。
 * <p>
 * 所有请求都打成 {@code {"action":..., ...}} 的 JSON，交给 {@code ModPayloads.sendExtrasRequest}。
 */
public final class ExtrasActions {
    private ExtrasActions() {}

    public static void open() {
        send("open");
    }

    public static void signIn() {
        send("sign_in");
    }

    public static void claimMail(String id) {
        JsonObject o = base("mail_claim");
        o.addProperty("id", id);
        com.sellbox.mod.network.ModNetwork.sendExtrasRequest(o.toString());
    }

    public static void claimAllMails() {
        send("mail_claim_all");
    }

    /** 手动清空邮箱（保留仍有未领取附件的邮件） */
    public static void clearMails() {
        send(base("mail_clear"));
    }

    public static void deleteMail(String id) {
        JsonObject o = base("mail_delete");
        o.addProperty("id", id);
        com.sellbox.mod.network.ModNetwork.sendExtrasRequest(o.toString());
    }

    public static void transfer(String target, int amount) {
        JsonObject o = base("transfer");
        o.addProperty("target", target);
        o.addProperty("amount", amount);
        com.sellbox.mod.network.ModNetwork.sendExtrasRequest(o.toString());
    }

    public static void exchangeInAll() {
        JsonObject o = new JsonObject();
        o.addProperty("action", "exchange_in_all");
        send(o);
    }

    public static void sweeperToggle(boolean enabled) {
        JsonObject o = new JsonObject();
        o.addProperty("action", "admin_sweeper_toggle");
        o.addProperty("enabled", enabled);
        send(o);
    }

    public static void sweeperSave(int interval, int overload, int dustbins, String chat,
                                   String countdown, String after) {
        JsonObject o = new JsonObject();
        o.addProperty("action", "admin_sweeper_save");
        o.addProperty("interval", interval);
        o.addProperty("overload", overload);
        o.addProperty("dustbins", dustbins);
        o.addProperty("chat", chat == null ? "" : chat);
        o.addProperty("countdown", countdown == null ? "" : countdown);
        o.addProperty("after", after == null ? "" : after);
        send(o);
    }

    public static void sweeperListAdd(String kind, String id) {
        JsonObject o = new JsonObject();
        o.addProperty("action", "admin_sweeper_list_add");
        o.addProperty("kind", kind);
        o.addProperty("id", id);
        send(o);
    }

    public static void sweeperListRemove(String kind, String id) {
        JsonObject o = new JsonObject();
        o.addProperty("action", "admin_sweeper_list_remove");
        o.addProperty("kind", kind);
        o.addProperty("id", id);
        send(o);
    }

    public static void sweeperClean() {
        JsonObject o = new JsonObject();
        o.addProperty("action", "admin_sweeper_clean");
        send(o);
    }

    public static void sweeperDustbin(int index) {
        JsonObject o = new JsonObject();
        o.addProperty("action", "sweeper_dustbin");
        o.addProperty("index", index);
        send(o);
    }

    public static void adminAutoExchange(boolean enabled) {
        JsonObject o = new JsonObject();
        o.addProperty("action", "admin_auto_exchange");
        o.addProperty("enabled", enabled);
        send(o);
    }

    public static void adminExchangeOut(boolean enabled) {
        JsonObject o = new JsonObject();
        o.addProperty("action", "admin_exchange_out");
        o.addProperty("enabled", enabled);
        send(o);
    }

    public static void exchangeIn(String itemId, int count) {
        JsonObject o = base("exchange_in");
        o.addProperty("item", itemId);
        o.addProperty("count", count);
        com.sellbox.mod.network.ModNetwork.sendExtrasRequest(o.toString());
    }

    public static void exchangeOut(String itemId, int count) {
        JsonObject o = base("exchange_out");
        o.addProperty("item", itemId);
        o.addProperty("count", count);
        com.sellbox.mod.network.ModNetwork.sendExtrasRequest(o.toString());
    }

    public static void redeem(String code) {
        JsonObject o = base("redeem");
        o.addProperty("code", code);
        com.sellbox.mod.network.ModNetwork.sendExtrasRequest(o.toString());
    }

    public static void friendAdd(String name) {
        JsonObject o = base("friend_add");
        o.addProperty("name", name);
        com.sellbox.mod.network.ModNetwork.sendExtrasRequest(o.toString());
    }

    public static void friendAccept(String uuid) {
        JsonObject o = base("friend_accept");
        o.addProperty("uuid", uuid);
        com.sellbox.mod.network.ModNetwork.sendExtrasRequest(o.toString());
    }

    public static void friendRemove(String uuid) {
        JsonObject o = base("friend_remove");
        o.addProperty("uuid", uuid);
        com.sellbox.mod.network.ModNetwork.sendExtrasRequest(o.toString());
    }


    // ════════════════════════════════════════════
    //  管理员动作
    // ════════════════════════════════════════════

    /** 设置某个物品的兑换单价 */
    public static void adminRateSet(String itemId, long value) {
        JsonObject o = base("admin_rate_set");
        o.addProperty("item", itemId);
        o.addProperty("value", value);
        send(o);
    }

    /** 把选中的物品加进兑换表 */
    public static void adminRateHold(long value, ItemStack stack, boolean strict) {
        JsonObject o = base("admin_rate_hold");
        o.addProperty("value", value);
        putItem(o, stack, strict);
        send(o);
    }

    public static void adminRateRemove(String itemId) {
        JsonObject o = base("admin_rate_remove");
        o.addProperty("item", itemId);
        send(o);
    }

    /** 创建兑换码（手持物品会作为奖励） */
    public static void adminCodeCreate(String code, long money, int maxUses) {
        JsonObject o = base("admin_code_create");
        o.addProperty("code", code);
        o.addProperty("money", money);
        o.addProperty("maxUses", maxUses);
        send(o);
    }

    public static void adminCodeRemove(String code) {
        JsonObject o = base("admin_code_remove");
        o.addProperty("code", code);
        send(o);
    }

    /** 给玩家发邮件（手持物品会作为附件） */
    public static void adminMailSend(String target, String title, String content, long money) {
        JsonObject o = base("admin_mail_send");
        o.addProperty("target", target);
        o.addProperty("title", title);
        o.addProperty("content", content);
        o.addProperty("money", money);
        send(o);
    }

    // ════════════════════════════════════════════
    //  邮箱
    // ════════════════════════════════════════════

    /** 玩家 → 玩家发邮件（可选手持物附件 + 可选金币） */
    public static void mailSend(String target, String title, String content, long money, boolean attach) {
        JsonObject o = base("mail_send");
        o.addProperty("target", target);
        o.addProperty("title", title);
        o.addProperty("content", content);
        o.addProperty("money", money);
        o.addProperty("attach", attach);
        send(o);
    }

    /** 管理员群发（含离线玩家）；welcome = 同时存成新玩家欢迎邮件 */
    public static void adminMailBroadcast(String title, String content, long money, boolean welcome) {
        JsonObject o = base("admin_mail_broadcast");
        o.addProperty("title", title);
        o.addProperty("content", content);
        o.addProperty("money", money);
        o.addProperty("welcome", welcome);
        send(o);
    }

    /** 把当前表单 + 手持物存成新玩家欢迎邮件 */
    public static void adminWelcomeAdd(String title, String content, long money) {
        JsonObject o = base("admin_welcome_add");
        o.addProperty("title", title);
        o.addProperty("content", content);
        o.addProperty("money", money);
        send(o);
    }

    public static void adminWelcomeClear() {
        send("admin_welcome_clear");
    }

    // ════════════════════════════════════════════
    //  开箱
    // ════════════════════════════════════════════

    /** 花金币开一次箱 */
    public static void crateOpen(String crateId, int count) {
        JsonObject o = new JsonObject();
        o.addProperty("action", "crate_open");
        o.addProperty("crate", crateId);
        o.addProperty("count", Math.max(1, count));
        send(o);
    }

    /** 管理：设置箱体保底 */
    public static void adminCratePity(String crateId, String rarity, int count) {
        JsonObject o = new JsonObject();
        o.addProperty("action", "admin_crate_pity");
        o.addProperty("crate", crateId);
        o.addProperty("rarity", rarity);
        o.addProperty("count", count);
        send(o);
    }

    /** 管理：就地改奖池某一项的权重 / 品质 */
    public static void adminCrateItemSet(String crateId, int index, double chance, String rarity) {
        JsonObject o = new JsonObject();
        o.addProperty("action", "admin_crate_item_set");
        o.addProperty("crate", crateId);
        o.addProperty("index", index);
        if (chance >= 0) o.addProperty("chance", chance);
        if (rarity != null && !rarity.isEmpty()) o.addProperty("rarity", rarity);
        send(o);
    }

    /** 管理：改里程碑金币 */
    /** 管理：修改里程碑天数/金币，保留原物品 */
    public static void adminMileUpdate(int oldDay, int newDay, long money) {
        JsonObject o = new JsonObject();
        o.addProperty("action", "admin_mile_update");
        o.addProperty("oldDay", oldDay);
        o.addProperty("day", newDay);
        o.addProperty("money", money);
        send(o);
    }

    public static void adminMileMoney(int day, long money) {
        JsonObject o = new JsonObject();
        o.addProperty("action", "admin_mile_money");
        o.addProperty("day", day);
        o.addProperty("money", money);
        send(o);
    }

    /** 管理：删里程碑里的第 index 件物品 */
    public static void adminMileItemDel(int day, int index) {
        JsonObject o = new JsonObject();
        o.addProperty("action", "admin_mile_item_del");
        o.addProperty("day", day);
        o.addProperty("index", index);
        send(o);
    }

    public static void crateOpen(String crateId) {
        JsonObject o = base("crate_open");
        o.addProperty("crate", crateId);
        send(o);
    }

    // ════════════════════════════════════════════
    //  管理员：签到规则 / 开箱配置
    // ════════════════════════════════════════════

    /** 保存签到规则（mode = linear / table，同时写逐日精确值） */
    public static void adminSigninSet(String mode, long base, long step, int cap, int day, long exact) {
        JsonObject o = base("admin_signin_set");
        o.addProperty("mode", mode);
        o.addProperty("base", base);
        o.addProperty("step", step);
        o.addProperty("cap", cap);
        o.addProperty("day", day);
        o.addProperty("exact", exact);
        send(o);
    }

    public static void adminSigninMode(String mode) {
        JsonObject o = base("admin_signin_mode");
        o.addProperty("mode", mode);
        send(o);
    }

    /** 加/更新连续 day 天的额外奖励（手持物会一并加进去） */
    public static void adminSigninMile(int day, long money, ItemStack stack, boolean strict) {
        JsonObject o = base("admin_signin_mile");
        o.addProperty("day", day);
        o.addProperty("money", money);
        putItem(o, stack, strict);
        send(o);
    }

    public static void adminSigninMileDel(int day) {
        JsonObject o = base("admin_signin_mile_del");
        o.addProperty("day", day);
        send(o);
    }

    public static void adminCrateCreate(String name) {
        JsonObject o = base("admin_crate_create");
        o.addProperty("name", name);
        send(o);
    }

    public static void adminCrateRemove(String crateId) {
        JsonObject o = base("admin_crate_remove");
        o.addProperty("crate", crateId);
        send(o);
    }

    public static void adminCratePrice(String crateId, long price) {
        JsonObject o = base("admin_crate_price");
        o.addProperty("crate", crateId);
        o.addProperty("price", price);
        send(o);
    }

    /** 管理：同时保存箱名和单价 */
    public static void adminCrateUpdate(String crateId, String name, long price) {
        JsonObject o = base("admin_crate_update");
        o.addProperty("crate", crateId);
        o.addProperty("name", name);
        o.addProperty("price", price);
        send(o);
    }

    /** 把选中的物品加进某个箱子，chance 是百分比权重 */
    public static void adminCrateItemAdd(String crateId, double chance, String rarity,
                                         ItemStack stack, boolean strict) {
        JsonObject o = base("admin_crate_item_add");
        o.addProperty("crate", crateId);
        o.addProperty("chance", chance);
        o.addProperty("rarity", rarity);
        putItem(o, stack, strict);
        send(o);
    }

    /** 设置箱子图标 */
    public static void adminCrateIcon(String crateId, ItemStack stack, boolean strict) {
        JsonObject o = base("admin_crate_icon");
        o.addProperty("crate", crateId);
        putItem(o, stack, strict);
        send(o);
    }

    private static void putItem(JsonObject o, ItemStack stack, boolean strict) {
        if (stack == null || stack.isEmpty()) return;
        ItemStack sent = strict ? stack.copy() : new ItemStack(stack.getItem(), stack.getCount());
        o.add("item", ExtrasConfigFile.writeItem(sent));
        o.addProperty("strict", strict);
    }

    public static void adminCrateItemDel(String crateId, int index) {
        JsonObject o = base("admin_crate_item_del");
        o.addProperty("crate", crateId);
        o.addProperty("index", index);
        send(o);
    }

    private static void send(JsonObject o) {
        com.sellbox.mod.network.ModNetwork.sendExtrasRequest(o.toString());
    }

    private static JsonObject base(String action) {
        JsonObject o = new JsonObject();
        o.addProperty("action", action);
        return o;
    }

    private static void send(String action) {
        com.sellbox.mod.network.ModNetwork.sendExtrasRequest(base(action).toString());
    }
}
