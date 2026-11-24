package com.sellbox.mod.extras;

/**
 * 扩展模块的常量配置。
 * <p>
 * 全部先写死成常量，避免和现有 {@code SellBoxConfig} 的 Forge/NeoForge 两套配置体系纠缠；
 * 以后要开放给服务器管理员时，再迁移到各自的配置文件。
 */
public final class ExtrasConfig {
    private ExtrasConfig() {}

    // ── 签到 ──
    /** 每日签到基础奖励（星币） */
    public static final long SIGNIN_BASE_REWARD = 100L;
    /** 每多连续一天，额外加成的星币 */
    public static final long SIGNIN_STREAK_STEP = 20L;
    /** 连续奖励封顶（天） */
    public static final int SIGNIN_STREAK_CAP = 10;
    /** 里程奖励：连续 N 天一次性额外奖励 */
    public static final java.util.Map<Integer, Long> SIGNIN_MILESTONES = java.util.Map.of(
            3, 300L,
            5, 600L,
            7, 1000L,
            14, 2000L,
            30, 5000L
    );

    // ── 邮箱 ──
    /** 邮箱容量上限 */
    public static final int MAX_MAILS = 100;
    /** 邮件保留期：超过这个时间的「无未领取附件」邮件会被自动清理（7 天） */
    public static final long MAIL_KEEP_MS = 7L * 24L * 60L * 60L * 1000L;
    /** 自动清理的检查间隔（1 小时） */
    public static final long MAIL_PURGE_INTERVAL_MS = 60L * 60L * 1000L;

    // ── 转账 ──
    /** 单次转账最小金额 */
    public static final int TRANSFER_MIN = 1;
    /** 单次转账最大金额 */
    public static final int TRANSFER_MAX = 1_000_000;
    /** 转账冷却（毫秒） */
    public static final long TRANSFER_COOLDOWN_MS = 2_000L;

    // ── 好友 ──
    /** 好友上限 */
    public static final int MAX_FRIENDS = 50;
    /** 待处理申请上限 */
    public static final int MAX_FRIEND_REQUESTS = 20;

    // ── 兑换 ──
    /** 单次兑换的物品数量上限 */
    public static final int EXCHANGE_MAX_STACK = 64;

    // ── 开箱 ──
    /** 单次开箱的动画时长（毫秒） */
    public static final long CRATE_ANIM_MS = 3000L;
    /** 动画转盘上的物品格数 */
    public static final int CRATE_REEL_SIZE = 44;
    /** 中奖格在转盘里的位置（后面还要滑几格才停） */
    public static final int CRATE_REEL_STOP = 38;
    /** 一局最多能开出几个奖品 */
    public static final int CRATE_MAX_DROP = 1;
    /** 开箱冷却（毫秒），防止脚本连点 */
    public static final long CRATE_COOLDOWN_MS = 600L;
    /** 单个箱体最多配置多少个奖品 */
    public static final int CRATE_MAX_ITEMS = 60;

    /** 最多能建多少个箱体 */
    public static final int CRATE_MAX_SETS = 24;
    /** 单次最多连开几个（客户端「×10」与服务端上限） */
    public static final int CRATE_MAX_BATCH = 10;
    /** 单个箱子单价上限，避免价格 × 连开次数溢出 int 余额 */
    public static final long CRATE_MAX_PRICE = 100_000_000L;
    /** 保底：同一箱体累计多少次没出「目标品质及以上」就必出（数据存在每人每箱） */
    public static final int CRATE_PITY_DEFAULT = 20;
    /** 对了保底用的品质排序：越靠后越稀有 */
    public static final java.util.List<String> CRATE_RARITY_ORDER =
            java.util.List.of("common", "uncommon", "rare", "epic", "legendary", "mythic");

    // ── 邮箱互联 ──
    /** 单封玩家互发邮件的正文长度上限 */
    public static final int MAIL_CONTENT_MAX = 200;
    /** 单封玩家互发邮件的标题长度上限 */
    public static final int MAIL_TITLE_MAX = 40;
    /** 玩家互发邮件的冷却（毫秒） */
    public static final long MAIL_COOLDOWN_MS = 3_000L;
    /** 同一玩家信箱里最多保留多少封「交易流水」记录邮件 */
    public static final int MAX_RECEIPTS = 60;
    /** 状态包里最多带多少封「已领取」邮件（未领取的永远全量带） */
    public static final int MAIL_STATE_RECENT = 30;

    /** 群发邮件时是否跳过从未上线的玩家（false = 只发给有数据的玩家） */
    public static final boolean BROADCAST_ONLY_KNOWN = true;
}
