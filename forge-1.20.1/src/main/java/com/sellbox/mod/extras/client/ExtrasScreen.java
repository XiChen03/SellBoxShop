package com.sellbox.mod.extras.client;

import com.sellbox.mod.gui.GuiDraw;
import com.sellbox.mod.gui.GuiTheme;
import com.sellbox.mod.util.FontUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 扩展功能主面板：签到 / 邮箱 / 货币兑换 / 转账 / 兑换码 / 好友。
 * <p>
 * 左边是竖向标签栏，右边是内容区。整个界面按 {@link #sf} 缩放，
 * 设计坐标固定为 {@link #DESIGN_W} × {@link #DESIGN_H}，所以任何分辨率都能用。
 * <p>
 * 配色全部走 {@link GuiTheme}，跟随玩家选的主题；文案全部走语言文件（zh_cn / en_us）。
 */
public class ExtrasScreen extends Screen {
    final GuiTheme T = GuiTheme.get();

    // ── 设计坐标 ──
    static final int DESIGN_W = 420;
    static final int DESIGN_H = 256;
    static final int SIDEBAR_W = 96;
    static final int TAB_H = 26;

    /** 内容区左上角（设计坐标） */
    static final int CONTENT_LEFT = SIDEBAR_W + 12;
    static final int CONTENT_TOP = 26;

    /** 常规页的两行基线（相对 CONTENT_TOP） */
    static final int HOME_ROW1 = 30;
    static final int HOME_ROW2 = 58;

    private float sf = 1f;
    private int panelLeft, panelTop, panelW, panelH;
    private int layoutW = -1, layoutH = -1;

    // ── 当前页 ──
    private static final int TAB_SIGN = 0;
    private static final int TAB_MAIL = 1;
    private static final int TAB_EXCHANGE = 2;
    private static final int TAB_CRATE = 3;
    private static final int TAB_TRANSFER = 4;
    private static final int TAB_REDEEM = 5;
    private static final int TAB_FRIEND = 6;
    private static final int TAB_ADMIN = 7;

    private int tab = 0;
    private static final String[] TAB_KEYS = {
            "extras.tab.sign", "extras.tab.mail", "extras.tab.exchange",
            "extras.tab.crate", "extras.tab.transfer", "extras.tab.redeem",
            "extras.tab.friend"
    };

    // ── 输入框 ──
    EditBox transferTargetBox, transferAmountBox;
    EditBox redeemBox, friendBox;
    int boxW, boxH, boxX, boxY;
    /** 输入框在设计坐标系里的位置（render 里排按钮要用） */
    int boxDX, boxDY, boxDW, boxDH;
    static final int FRIEND_BOX_W = 110;

    // ── 管理员（由 /sellbox extras admin 开启后才出现） ──
    static final String ADMIN_TAB_KEY = "extras.tab.admin";
    static final String[] ADMIN_SUBTAB_KEYS = {
            "extras.admin.signin", "extras.admin.mail", "extras.admin.rate",
            "extras.admin.crate", "extras.admin.codes", "extras.admin.sweeper"
    };
    static final int ADMIN_TAB_SIGNIN = 0, ADMIN_TAB_MAIL = 1, ADMIN_TAB_RATE = 2,
            ADMIN_TAB_CRATE = 3, ADMIN_TAB_CODES = 4, ADMIN_TAB_SWEEPER = 5;
    int adminTab = 0;
    int adminScroll = 0;
    EditBox adminValueBox;
    EditBox adminCodeBox, adminCodeMoneyBox, adminCodeUsesBox;
    EditBox adminTargetBox, adminTitleBox, adminMoneyBox;
    /** 管理·签到页的输入框 */
    EditBox adminBaseBox, adminStepBox, adminCapBox, adminDayBox, adminDayMoneyBox, adminMileDayBox, adminMileMoneyBox;
    /** 管理·开箱页的输入框 */
    EditBox adminCrateNameBox, adminCratePriceBox, adminChanceBox;
    /** 管理·扫地女仆 */
    EditBox sweeperIntervalBox, sweeperOverloadBox, sweeperDustbinBox, sweeperDustbinIndexBox,
            sweeperChatBox, sweeperCountdownBox, sweeperAfterBox;
    boolean sweeperShowBlacklist = true, sweeperShowWhitelist = false, sweeperShowEntities = false;
    int sweeperScrollBlacklist = 0, sweeperScrollWhitelist = 0, sweeperScrollEntities = 0;
    /** 管理·开箱页的品质下拉当前值 */
    String adminRarityValue = "rare";
    /** 管理·开箱页：正在编辑的奖池行（-1 表示未编辑） */
    int adminCrateEditIndex = -1;
    String adminCrateEditName = "";
    /** 管理·签到页：正在编辑的里程碑天数（0 表示未编辑） */
    int adminMileEditDay = 0;
    String adminPityRarityValue = "common";
    boolean adminRarityDropdownOpen = false;
    boolean adminPityRarityDropdownOpen = false;
    /** 管理·开箱页：保底次数 */
    EditBox adminPityCountBox;

    /** 管理·签到页：展开查看/删除物品的里程碑天数（0 = 都不展开） */
    int mileDetailDay = 0;

    // ── 开箱页 ──
    /** 当前查看的箱子序号 */
    int crateTab = 0;
    /** 箱体页签横向滚动的起始下标（箱子太多时用箭头翻页） */
    int crateTabStart = 0;
    /** 管理·签到的逐日奖励分页（每页 9 条） */
    int adminExactPage = 0;
    /** 开箱动画进行中时锁定界面交互 */
    long crateAnimStart = 0L;
    /** 结果卡片出现时刻：用于 10 秒倒计时 */
    long crateRevealAt = 0L;
    /** 十连：是否已点过跳过 */
    boolean crateBatchSkipped = false;
    /** 十连：结果页开始计时的时间 */
    long crateBatchRevealAt = 0L;
    /** 开箱/连开汇总显示时禁止页面 tooltip */
    boolean crateModal = false;

    // ── 转账页的「选好友」下拉 ──
    boolean friendPickerOpen = false;
    int friendPickerScroll = 0;

    // ── 邮箱页的「写邮件」面板（玩家对玩家） ──
    boolean composeOpen = false;
    EditBox cpTargetBox, cpTitleBox, cpContentBox, cpMoneyBox;
    /** 是否附带手持物 */
    boolean composeAttach = true;
    /** 写邮件面板·收件人好友选择下拉 */
    boolean composePickerOpen = false;
    int composePickerScroll = 0;

    // ── 管理页·发邮件：群发开关 ──
    boolean adminToAll = false;
    boolean adminWelcome = false;
    /** 管理·发邮件：在线玩家下拉 */
    boolean adminTargetPickerOpen = false;
    int adminTargetPickerScroll = 0;

    /** 月历当前显示的月份（null = 本月） */
    java.time.YearMonth shownMonth;
    /** 悬停提示（渲染时赋值，整帧画完再画 tooltip） */
    String hoverTip;
    int hoverTipX, hoverTipY;
    /** 物品 tooltip：必须等缩放 pose 弹栈、回到屏幕坐标后再画 */
    net.minecraft.world.item.ItemStack hoverItemStack = net.minecraft.world.item.ItemStack.EMPTY;
    int hoverItemX, hoverItemY;
    /** 当前帧鼠标的屏幕坐标（页面拿到的是设计坐标，画物品 tooltip 时要用屏幕坐标） */
    int screenMouseX, screenMouseY;

    // ── 列表滚动 ──
    int scroll = 0;

    // ── 每帧的命中区（用于点击判定，坐标为设计坐标） ──
    private final List<int[]> hitRects = new ArrayList<>();
    private final List<Runnable> hitActions = new ArrayList<>();

    /**
     * 输入框 → 设计坐标 {x, y, w, h}。
     * <p>y 是「框底左上角」，不是文字基线 —— 两者必须分开记：
     * EditBox 非 bordered 时把字画在 getY()，而框底是从 y 到 y+h，所以
     * 建框时要把 EditBox 的 y 内缩 (h-9)/2 才能让文字在视觉上居中。
     */
    private final java.util.Map<EditBox, int[]> boxRects = new java.util.IdentityHashMap<>();

    private String lastStateJson = "";

    public ExtrasScreen() {
        super(Component.translatable("extras.title"));
    }

    @Override
    protected void init() {
        // 先算好布局，super.init() 内部的 rebuildWidgets 才能拿到正确的坐标
        updateLayout();
        super.init();
    }

    /** 按当前窗口重新计算缩放/居中位置；拖动窗口时会实时调用 */
    private void updateLayout() {
        layoutW = this.width;
        layoutH = this.height;
        // 不再放大整个 pose：能 1:1 放下时直接用 sf=1，字体保持原版像素清晰；
        // 只有窗口确实小于设计尺寸时才整体缩小。
        float fit = Math.min(this.height / (float) (DESIGN_H + 8),
                this.width / (float) (DESIGN_W + 16));
        if (fit >= 0.95f) {
            sf = 1.0f;
        } else {
            sf = Math.max(0.55f, Math.min(1.0f, fit));
        }
        panelW = (int) (DESIGN_W * sf);
        panelH = (int) (DESIGN_H * sf);
        panelLeft = (this.width - panelW) / 2;
        panelTop = (this.height - panelH) / 2;
    }

    /** 设计坐标 → 屏幕坐标（用于 scissor 等不吃 pose 变换的 API） */
    int toScreenX(int dx) {
        return panelLeft + Math.round(dx * sf);
    }

    int toScreenY(int dy) {
        return panelTop + Math.round(dy * sf);
    }

    /** Screen.font 在 net.minecraft.client.gui 里声明为 protected，同包非子类不能直接读 */
    Font font() {
        return this.font;
    }

    @Override
    public void rebuildWidgets() {
        clearWidgets();
        boxRects.clear();
        transferTargetBox = null;
        transferAmountBox = null;
        redeemBox = null;
        friendBox = null;
        adminValueBox = null;
        adminCodeBox = null;
        adminCodeMoneyBox = null;
        adminCodeUsesBox = null;
        adminTargetBox = null;
        adminTitleBox = null;
        adminMoneyBox = null;
        adminBaseBox = null;
        adminStepBox = null;
        adminCapBox = null;
        adminDayBox = null;
        adminDayMoneyBox = null;
        adminMileDayBox = null;
        adminMileMoneyBox = null;
        adminCrateNameBox = null;
        adminCratePriceBox = null;
        adminChanceBox = null;
        adminPityCountBox = null;
        adminRarityDropdownOpen = false;
        adminPityRarityDropdownOpen = false;
        friendPickerOpen = false;
        adminTargetPickerOpen = false;
        composePickerOpen = false;
        composePickerScroll = 0;

        // 写邮件面板：切页后关闭，同页刷新时保留
        boolean keepCompose = composeOpen;
        String keepTarget = cpTargetBox == null ? null : cpTargetBox.getValue();
        String keepTitle = cpTitleBox == null ? null : cpTitleBox.getValue();
        String keepContent = cpContentBox == null ? null : cpContentBox.getValue();
        String keepMoney = cpMoneyBox == null ? null : cpMoneyBox.getValue();
        cpTargetBox = null;
        cpTitleBox = null;
        cpContentBox = null;
        cpMoneyBox = null;
        composeOpen = false;

        boxDX = CONTENT_LEFT;
        boxDY = CONTENT_TOP + HOME_ROW1;
        boxDW = 150;
        boxDH = 16;
        boxX = panelLeft + (int) (boxDX * sf);
        boxY = panelTop + (int) (boxDY * sf);

        // 非管理员时不该有管理页：先纠正 tab，避免重建出一屏隐藏的管理输入框
        if (!ExtrasClientState.admin && tab >= TAB_KEYS.length) {
            tab = 0;
            scroll = 0;
        }

        if (tab >= TAB_KEYS.length) {
            buildAdminBoxes();
        } else if (tab == TAB_TRANSFER) {
            transferTargetBox = makeBoxAt(boxDX, boxDY, 72, boxDH, ExtrasUi.tr("extras.transfer.target"), 16);
            transferAmountBox = makeBoxAt(boxDX + 100, boxDY, 56, boxDH, ExtrasUi.tr("extras.transfer.amount"), 16);
        } else if (tab == TAB_REDEEM) {
            redeemBox = makeBoxAt(boxDX, boxDY, 150, boxDH, ExtrasUi.tr("extras.redeem.placeholder"), 32);
        } else if (tab == TAB_FRIEND) {
            friendBox = makeBoxAt(boxDX, boxDY, 110, boxDH, ExtrasUi.tr("extras.friend.placeholder"), 16);
        }

        if (tab == TAB_MAIL && keepCompose) {
            composeOpen = true;
            ExtrasHomePages.buildComposeBoxes(this);
            // 窗口缩放会重建控件，把已经输入的内容恢复回去
            if (cpTargetBox != null && keepTarget != null) cpTargetBox.setValue(keepTarget);
            if (cpTitleBox != null && keepTitle != null) cpTitleBox.setValue(keepTitle);
            if (cpContentBox != null && keepContent != null) cpContentBox.setValue(keepContent);
            if (cpMoneyBox != null && keepMoney != null) cpMoneyBox.setValue(keepMoney);
        }
    }

    /** 管理页内容区顶部（子页签高 14 + 间距 6，相对面板原点） */
    static final int ADMIN_TOP = CONTENT_TOP + 14 + 6;

    // ── 管理·签到页的行基线（相对 ADMIN_TOP）──
    static final int SIGN_MODE_Y = 0;
    static final int SIGN_PARAM_Y = 18;
    static final int SIGN_EXACT_TITLE_Y = 38;
    static final int SIGN_EXACT_ROW_Y = 50;
    static final int SIGN_EXACT_CHIP_Y = 70;
    static final int SIGN_MILE_TITLE_Y = 112;
    static final int SIGN_MILE_ROW_Y = 124;
    static final int SIGN_MILE_LIST_Y = 144;

    // ── 管理·开箱页的行基线（相对 ADMIN_TOP）──
    static final int CRATE_TAB_Y = 0;
    static final int CRATE_NAME_Y = 18;
    static final int CRATE_DROP_Y = 38;
    static final int CRATE_PITY_Y = 74;
    static final int CRATE_LIST_TITLE_Y = 94;
    static final int CRATE_LIST_Y = 108;

    /** 管理页的输入框：位置随子页签变化（必须与各页绘制用的基线一致） */
    private void buildAdminBoxes() {
        int l = SIDEBAR_W + 12;
        int top = ADMIN_TOP;
        if (adminTab == ADMIN_TAB_RATE) {
            adminValueBox = makeBoxAt(l + 28, top, 56, 16, ExtrasUi.tr("extras.admin.value"), 16);
        } else if (adminTab == ADMIN_TAB_CODES) {
            adminCodeBox = makeBoxAt(l + 46, top, 150, 16, ExtrasUi.tr("extras.admin.code"), 32);
            adminCodeMoneyBox = makeBoxAt(l + 46, top + 20, 60, 16, ExtrasUi.tr("extras.admin.money"), 16);
            adminCodeUsesBox = makeBoxAt(l + 150, top + 20, 50, 16, "-1", 6);
            adminCodeUsesBox.setValue("-1");
        } else if (adminTab == ADMIN_TAB_MAIL) {
            adminTargetBox = makeBoxAt(l + 34, top, 150, 16, ExtrasUi.tr("extras.admin.target"), 16);
            adminTitleBox = makeBoxAt(l + 34, top + 20, 150, 16, ExtrasUi.tr("extras.admin.title"), 40);
            adminMoneyBox = makeBoxAt(l + 34, top + 40, 56, 16, ExtrasUi.tr("extras.admin.money"), 16);
        } else if (adminTab == ADMIN_TAB_SIGNIN) {
            // 签到：基础/加成/封顶 + 逐日精确值 + 里程；用当前配置预填，避免保存时误清空
            adminBaseBox = makeBoxAt(l + 24, top + SIGN_PARAM_Y, 46, 14, "0", 10);
            adminBaseBox.setValue(String.valueOf(ExtrasClientState.signinBase));
            adminStepBox = makeBoxAt(l + 102, top + SIGN_PARAM_Y, 46, 14, "0", 10);
            adminStepBox.setValue(String.valueOf(ExtrasClientState.signinStep));
            adminCapBox = makeBoxAt(l + 180, top + SIGN_PARAM_Y, 40, 14, "30", 6);
            adminCapBox.setValue(String.valueOf(ExtrasClientState.signinCap));
            adminDayBox = makeBoxAt(l + 24, top + SIGN_EXACT_ROW_Y, 40, 14, "7", 5);
            adminDayMoneyBox = makeBoxAt(l + 96, top + SIGN_EXACT_ROW_Y, 56, 14, "0", 16);
            adminMileDayBox = makeBoxAt(l + 24, top + SIGN_MILE_ROW_Y, 40, 14, "7", 5);
            adminMileMoneyBox = makeBoxAt(l + 96, top + SIGN_MILE_ROW_Y, 56, 14, "0", 16);
        } else if (adminTab == ADMIN_TAB_SWEEPER) {
            sweeperIntervalBox = makeBoxAt(l + 28, top, 40, 14, "600", 6);
            sweeperIntervalBox.setValue(String.valueOf(ExtrasClientState.sweeperInterval));
            sweeperOverloadBox = makeBoxAt(l + 116, top, 50, 14, "640", 8);
            sweeperOverloadBox.setValue(String.valueOf(ExtrasClientState.sweeperOverload));
            sweeperDustbinBox = makeBoxAt(l + 214, top, 34, 14, "8", 3);
            sweeperDustbinBox.setValue(String.valueOf(ExtrasClientState.sweeperDustbins));
            sweeperDustbinIndexBox = makeBoxAt(l + 220, top + 20, 30, 14, "1", 3);
            sweeperDustbinIndexBox.setValue("1");
            sweeperChatBox = makeBoxAt(l, DESIGN_H - 32, 288, 14, "消息", 120);
            sweeperChatBox.setValue(ExtrasClientState.sweeperChatMessage);
            sweeperCountdownBox = makeBoxAt(l, DESIGN_H - 74, 288, 14, "倒计时", 120);
            sweeperCountdownBox.setValue(ExtrasClientState.sweeperCountdownMessage);
            sweeperAfterBox = makeBoxAt(l, DESIGN_H - 53, 288, 14, "完成", 120);
            sweeperAfterBox.setValue(ExtrasClientState.sweeperAfterMessage);
        } else {
            // 开箱：箱名 + 价格 + 概率/品质 + 保底；同样用当前箱体预填
            adminCrateNameBox = makeBoxAt(l + 30, top + CRATE_NAME_Y, 80, 14, "name", 32);
            adminCratePriceBox = makeBoxAt(l + 136, top + CRATE_NAME_Y, 50, 14, "0", 16);
            adminChanceBox = makeBoxAt(l + 30, top + CRATE_DROP_Y, 56, 14, "1.0", 10);
            adminPityCountBox = makeBoxAt(l + 152, top + CRATE_PITY_Y, 40, 14, "20", 6);
            adminRarityValue = "rare";
            adminPityRarityValue = "common";
            adminCrateEditIndex = -1;
            adminCrateEditName = "";
            adminMileEditDay = 0;
            var crates = ExtrasClientState.crates;
            if (!crates.isEmpty()) {
                int idx = Math.max(0, Math.min(crateTab, crates.size() - 1));
                var crate = crates.get(idx);
                adminCrateNameBox.setValue(crate.name);
                adminCratePriceBox.setValue(String.valueOf(crate.price));
                adminPityRarityValue = crate.pityRarity;
                adminPityCountBox.setValue(String.valueOf(crate.pityCount));
            }
        }
    }

    /** 用设计坐标建输入框 */
    EditBox makeBoxAt(int dx, int dy, int dw, int dh, String hint) {
        return makeBox(dx, dy, dw, dh, hint, 64);
    }

    /** 用设计坐标建输入框，并指定最大长度 */
    EditBox makeBoxAt(int dx, int dy, int dw, int dh, String hint, int maxLength) {
        return makeBox(dx, dy, dw, dh, hint, maxLength);
    }

    /**
     * 建一个无边框输入框。
     * <p>关键：非 bordered 的 EditBox 把文字画在 {@code getY()}，而框体是从 y 到 y+h，
     * 所以 EditBox 的 y 必须内缩 (h-9)/2，文字才会在框里居中；框底另记在
     * {@link #boxRects} 里，由 {@link #drawBoxAround} 画。
     */
    private EditBox makeBox(int dx, int dy, int dw, int dh, String hint, int maxLength) {
        int pad = GuiDraw.textPadX();
        int sx = panelLeft + (int) ((dx + pad) * sf);
        int sy = panelTop + (int) ((dy + GuiDraw.innerTextY(0, dh)) * sf);
        int sw = (int) (Math.max(8, dw - pad * 2) * sf);
        EditBox box = new EditBox(this.font, sx, sy, sw, (int) (dh * sf), Component.literal(hint));
        box.setMaxLength(Math.max(1, maxLength));
        box.setBordered(false);
        box.setHint(Component.literal(hint));
        addRenderableWidget(box);
        boxRects.put(box, new int[]{dx, dy, dw, dh});
        return box;
    }

    // ════════════════════════════════════════════
    //  渲染
    // ════════════════════════════════════════════

    /** 关闭原版模糊背景，避免它把我们画好的内容糊掉 */
    @Override
    public void renderBackground(GuiGraphics g) {
        // 1.20.1 没有模糊背景，由 render 里的 GuiDraw.scrim 负责压暗
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        GuiDraw.scrim(g, this.width, this.height);

        // 每帧重建命中区，避免滚动/切换页后残留旧按钮
        hitRects.clear();
        hitActions.clear();
        hoverTip = null;
        hoverItemStack = net.minecraft.world.item.ItemStack.EMPTY;

        // 拖动窗口时 Screen.init 不一定会及时触发，这里再做一次兜底
        if (this.width != layoutW || this.height != layoutH) {
            updateLayout();
            rebuildWidgets();
        }

        // 鼠标 → 设计坐标
        screenMouseX = mx;
        screenMouseY = my;
        int lx = (int) ((mx - panelLeft) / sf);
        int ly = (int) ((my - panelTop) / sf);

        g.pose().pushPose();
        g.pose().translate(panelLeft, panelTop, 0);
        g.pose().scale(sf, sf, 1f);

        renderPanelBackground(g);
        renderSidebar(g, lx, ly);
        renderContent(g, lx, ly);

        g.pose().popPose();

        // 输入框由 super 处理（不受设计坐标缩放影响）
        super.render(g, mx, my, pt);

        // 品质下拉必须在输入框之后画，否则会被 EditBox 盖住；只覆盖选项区域，不挡整页
        if (tab >= TAB_KEYS.length && adminTab == ADMIN_TAB_CRATE
                && (adminRarityDropdownOpen || adminPityRarityDropdownOpen)) {
            g.pose().pushPose();
            g.pose().translate(panelLeft, panelTop, 0);
            g.pose().scale(sf, sf, 1f);
            ExtrasAdminPages.renderDeferredDropdown(this, g, CONTENT_LEFT, ADMIN_TOP,
                    DESIGN_W - 12, DESIGN_H - 12, lx, ly);
            g.pose().popPose();
        }

        // 右下角提醒：HUD 层会被 Screen 盖住，所以面板打开时在屏幕坐标里自己再画一次
        if (ExtrasNoticeOverlay.DRAW_IN_SCREEN) ExtrasNoticeOverlay.draw(g);

        // 月历悬停提示：必须在 super.render 之后画，否则会被输入框盖住；
        // design 坐标要先换算回屏幕坐标，否则缩放/居中后 tooltip 会飘到错位置。
        boolean dropdownOpen = tab >= TAB_KEYS.length && adminTab == ADMIN_TAB_CRATE
                && (adminRarityDropdownOpen || adminPityRarityDropdownOpen);
        if (!crateModal && !dropdownOpen && hoverTip != null) {
            int tw = font.width(hoverTip);
            int tx = panelLeft + (int) (hoverTipX * sf) - tw / 2;
            int ty = panelTop + (int) (hoverTipY * sf);
            g.renderTooltip(font, Component.literal(hoverTip), tx, ty);
        }
        // 物品 tooltip 同理：不能在缩放的 pose 里用屏幕鼠标坐标画
        if (!crateModal && !dropdownOpen && hoverItemStack != null && !hoverItemStack.isEmpty()) {
            g.renderTooltip(font, hoverItemStack, hoverItemX, hoverItemY);
        }
    }

    /** 关掉面板后回到游戏，提醒依然在 HUD 右下角显示 */
    @Override
    public void onClose() {
        // 关掉面板时丢弃未播完的开箱动画，避免下次打开又重播/卡在「开箱中」
        crateAnimStart = 0L;
        crateRevealAt = 0L;
        crateBatchSkipped = false;
        crateBatchRevealAt = 0L;
        crateModal = false;
        ExtrasClientState.lastCrateResult = null;
        ExtrasClientState.lastCrateBatch = null;
        // 响应包是一次性的，留在缓冲区里会导致重开界面时重播旧动画/旧报错
        com.sellbox.mod.network.ModNetwork.clearExtrasResponse();
        this.minecraft.setScreen(null);
    }

    private void renderPanelBackground(GuiGraphics g) {
        GuiDraw.panelWithStripe(g, 0, 0, DESIGN_W, DESIGN_H);

        if (T.leftPanelBg != 0) {
            GuiDraw.fillRounded(g, 0, 0, SIDEBAR_W, DESIGN_H, T.leftPanelBg);
        }
        g.fill(SIDEBAR_W, 6, SIDEBAR_W + 1, DESIGN_H - 6, T.panelDivider);

        g.drawString(font, ExtrasUi.tr("extras.title"), 10, 8, T.textTitle, GuiDraw.shadow());

        String bal = ExtrasUi.tr("extras.balance", ExtrasClientState.balance);
        int bw = font.width(bal);
        // 右上角：余额 + 关闭按钮（×）
        g.drawString(font, bal, DESIGN_W - bw - 28, 8, T.textTotal, GuiDraw.shadow());
        int closeX = DESIGN_W - 18;
        GuiDraw.fillRounded(g, closeX, 4, 12, 12, T.btnBgNormal);
        if (T.btnBorder != 0) GuiDraw.borderRounded(g, closeX, 4, 12, 12, T.btnBorder);
        g.drawString(font, "×", closeX + (12 - font.width("×")) / 2,
                4 + (12 - font.lineHeight) / 2 + 1, T.textPrimary, GuiDraw.shadow());
        addHit(closeX, 4, 12, 12, this::onClose);
    }

    private void renderSidebar(GuiGraphics g, int mx, int my) {
        int y = 26;
        int tabs = tabCount();
        for (int i = 0; i < tabs; i++) {
            boolean sel = i == tab;
            boolean hover = mx >= 6 && mx < SIDEBAR_W - 6 && my >= y && my < y + TAB_H;
            boolean hov = hover && !sel;

            GuiDraw.listRow(g, 6, y, SIDEBAR_W - 12, TAB_H - 4, sel, hov);

            String label = ExtrasUi.tr(tabKey(i));
            FontUtil.drawAutoFitCentered(g, font, label, 6 + (SIDEBAR_W - 12) / 2,
                    y + (TAB_H - 4 - font.lineHeight) / 2 + 1, SIDEBAR_W - 16,
                    sel || hov ? T.textPrimary : T.textMuted);

            final int idx = i;
            addHit(6, y, SIDEBAR_W - 12, TAB_H - 4, () -> {
                tab = idx;
                scroll = 0;
                rebuildWidgets();
            });

            y += TAB_H;
        }
    }

    private void renderContent(GuiGraphics g, int mx, int my) {
        int left = CONTENT_LEFT;
        int top = CONTENT_TOP;
        int right = DESIGN_W - 12;
        int bottom = DESIGN_H - 12;

        // 内容区加一圈细框，页面元素再多也不会显得“散”
        int frameColor = T.separatorLine != 0 ? T.separatorLine : GuiDraw.frameColor();
        if (frameColor != 0) {
            GuiDraw.borderRounded(g, left - 3, top - 3, right - left + 6, bottom - top + 6, frameColor);
        }

        if (tab >= tabCount()) {
            tab = 0;
            scroll = 0;
        }
        switch (tab) {
            case TAB_SIGN -> ExtrasHomePages.renderSign(this, g, left, top, right, bottom, mx, my);
            case TAB_MAIL -> ExtrasHomePages.renderMail(this, g, left, top, right, bottom, mx, my);
            case TAB_EXCHANGE -> ExtrasHomePages.renderExchange(this, g, left, top, right, bottom, mx, my);
            case TAB_CRATE -> ExtrasCratePage.renderCrate(this, g, left, top, right, bottom, mx, my);
            case TAB_TRANSFER -> ExtrasHomePages.renderTransfer(this, g, left, top, right, bottom, mx, my);
            case TAB_REDEEM -> ExtrasHomePages.renderRedeem(this, g, left, top, right, bottom, mx, my);
            case TAB_FRIEND -> ExtrasHomePages.renderFriend(this, g, left, top, right, bottom, mx, my);
            case TAB_ADMIN -> ExtrasAdminPages.renderAdmin(this, g, left, top, right, bottom, mx, my);
            default -> ExtrasHomePages.renderFriend(this, g, left, top, right, bottom, mx, my);
        }
    }

    /** 标签 + 输入框外框（设计坐标） */
    void labelBox(GuiGraphics g, String label, int lx, int ly, EditBox box) {
        g.drawString(font, label, lx, ly + 4, T.textMuted, GuiDraw.shadow());
        if (box != null) drawBoxAround(g, box);
    }

    private int tabCount() {
        return ExtrasClientState.admin ? TAB_KEYS.length + 1 : TAB_KEYS.length;
    }

    private String tabKey(int i) {
        return i < TAB_KEYS.length ? TAB_KEYS[i] : ADMIN_TAB_KEY;
    }

    /** 勾选框 + 文字（整行可点） */
    void chk(GuiGraphics g, int x, int y, int size, boolean on,
                     String label, int mx, int my, Runnable toggle) {
        int boxY = y + 3;
        GuiDraw.fillRounded(g, x, boxY, size, size, on ? T.catSelectedFill : T.btnBgNormal);
        GuiDraw.borderRounded(g, x, boxY, size, size, GuiDraw.frameColor());
        g.drawString(font, label, x + size + 4, y + 4,
                on ? T.textPrimary : T.textMuted, GuiDraw.shadow());
        int totalW = size + 4 + font.width(label);
        addHit(x, y, totalW, 14, toggle);
    }

    /** 通用小按钮：返回右边界，方便链式排布 */
    int button(GuiGraphics g, int x, int y, int w, int h, String label,
                       boolean primary, int mx, int my, Runnable action) {
        boolean hover = action != null && mx >= x && mx < x + w && my >= y && my < y + h;
        GuiDraw.fillRounded(g, x, y, w, h, hover ? T.btnBgHovered : T.btnBgNormal);
        if (T.btnBorder != 0) GuiDraw.borderRounded(g, x, y, w, h, T.btnBorder);
        FontUtil.drawAutoFitCentered(g, font, label, x + w / 2,
                y + (h - font.lineHeight) / 2 + 1, w - 4,
                primary ? T.textPrimary : T.textMuted);
        if (action != null) addHit(x, y, w, h, action);
        return x + w;
    }

    /** 固定宽度小按钮（按文字自动算宽） */
    int buttonAuto(GuiGraphics g, int x, int y, int h, String label,
                           boolean primary, int mx, int my, Runnable action) {
        return button(g, x, y, font.width(label) + 14, h, label, primary, mx, my, action);
    }

    // ════════════════════════════════════════════
    //  转账页：选好友下拉
    // ════════════════════════════════════════════

    // ════════════════════════════════════════════
    //  开箱页
    // ════════════════════════════════════════════

                // ════════════════════════════════════════════
    //  管理 · 签到规则
    // ════════════════════════════════════════════

    // ════════════════════════════════════════════
    //  管理 · 开箱配置
    // ════════════════════════════════════════════

    // ════════════════════════════════════════════
    //  辅助
    // ════════════════════════════════════════════

    /** 两个颜色按 t 线性混合（t=0 取 a，t=1 取 b），保留 a 的 alpha */
    /** 给 EditBox 补一层主题边框（EditBox 自己是无边框的，框底另有记录） */
    void drawBoxAround(GuiGraphics g, EditBox box) {
        int[] r = boxRects.get(box);
        int dx, dy, dw, dh;
        if (r != null) {
            dx = r[0];
            dy = r[1];
            dw = r[2];
            dh = r[3];
        } else {
            dw = Math.round(box.getWidth() / sf);
            dh = Math.round(box.getHeight() / sf);
            dx = Math.round((box.getX() - panelLeft) / sf) - GuiDraw.textPadX();
            dy = Math.round((box.getY() - panelTop) / sf) - GuiDraw.innerTextY(0, dh);
        }
        GuiDraw.inputBox(g, dx, dy, dw, dh, box.isFocused());
    }

    void addHit(int x, int y, int w, int h, Runnable action) {
        hitRects.add(new int[]{x, y, w, h});
        hitActions.add(action);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        int lx = (int) ((mouseX - panelLeft) / sf);
        int ly = (int) ((mouseY - panelTop) / sf);

        // 点击任何非输入框区域都取消输入焦点
        setFocused(null);
        for (int i = hitRects.size() - 1; i >= 0; i--) {
            int[] r = hitRects.get(i);
            if (lx >= r[0] && lx < r[0] + r[2] && ly >= r[1] && ly < r[1] + r[3]) {
                hitActions.get(i).run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int dir = (int) Math.signum(scrollY);
        if (dir == 0) return true;

        // 下拉列表打开时优先滚它，否则超过 8 个好友/在线玩家就永远选不到
        if (composePickerOpen && tab == TAB_MAIL) {
            composePickerScroll = clampPickerScroll(composePickerScroll, dir, ExtrasClientState.friends.size());
            return true;
        }
        if (friendPickerOpen && tab == TAB_TRANSFER) {
            friendPickerScroll = clampPickerScroll(friendPickerScroll, dir, ExtrasClientState.friends.size());
            return true;
        }
        if (adminTargetPickerOpen && tab >= TAB_KEYS.length) {
            adminTargetPickerScroll = clampPickerScroll(adminTargetPickerScroll, dir, ExtrasClientState.onlinePlayers.size());
            return true;
        }

        if (tab == TAB_MAIL || tab == TAB_EXCHANGE || tab == TAB_FRIEND || tab == TAB_CRATE) {
            int step = tab == TAB_FRIEND ? 12 : 2;   // 好友页是像素滚动
            scroll -= dir * step;
            if (scroll < 0) scroll = 0;
            return true;
        }
        if (tab >= TAB_KEYS.length && adminTab == ADMIN_TAB_SWEEPER) {
            int lx = (int) ((mouseX - panelLeft) / sf);
            int ly = (int) ((mouseY - panelTop) / sf);
            if (ExtrasSweeperPage.handleScroll(this, lx, ly, dir)) return true;
        }
        if (tab >= TAB_KEYS.length) {
            // 签到页的里程碑列表按行滚，1 行一步更跟手；其它列表沿用 2 行
            int step = adminTab == ADMIN_TAB_SIGNIN ? 1 : 2;
            adminScroll -= dir * step;
            if (adminScroll < 0) adminScroll = 0;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    /** 下拉列表最多同时显示 8 行 */
    private static int clampPickerScroll(int current, int dir, int size) {
        int max = Math.max(0, size - 8);
        return Math.max(0, Math.min(current - dir, max));
    }

    @Override
    public void tick() {
        super.tick();
        // 管理员在服务端关闭扩展功能后，已经打开的界面立即退出
        if (com.sellbox.mod.network.ModNetwork.clientConfigSynced
                && !com.sellbox.mod.network.ModNetwork.clientExtrasEnabled) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(
                        Component.translatable("extras.msg.disabled"), true);
            }
            this.onClose();
            return;
        }
        // 只在包内容变化时重新解析，避免每 tick 重复 JSON 解析
        String state = com.sellbox.mod.network.ModNetwork.clientExtrasState;
        if (state != null && !state.equals(lastStateJson)) {
            lastStateJson = state;
            ExtrasClientState.apply(state);
            ExtrasClientState.applySweeper(state);
            // 管理员模式可能被指令关掉：把当前管理页踢回第一页，避免残留的管理输入框盖在普通页上
            if (!ExtrasClientState.admin && tab >= TAB_KEYS.length) {
                tab = 0;
                scroll = 0;
                rebuildWidgets();
            }
        }

        // 响应包是一次性的：取走并立刻清空，否则同一响应会被重复处理，
        // 重开界面时还会重播上一次的开箱动画/报错。
        String resp = com.sellbox.mod.network.ModNetwork.clientExtrasResponse;
        if (resp != null && !resp.isEmpty()) {
            com.sellbox.mod.network.ModNetwork.clearExtrasResponse();
            ExtrasClientState.applyResponse(resp);

            // 服务端确认成功后再关闭/清空表单；失败时保留玩家输入，避免白打一遍字
            if (ExtrasClientState.lastOk) {
                if ("mail_send".equals(ExtrasClientState.lastAction) && composeOpen) {
                    composeOpen = false;
                    composePickerOpen = false;
                    rebuildWidgets();
                } else if ("redeem".equals(ExtrasClientState.lastAction) && redeemBox != null) {
                    redeemBox.setValue("");
                } else if ("friend_add".equals(ExtrasClientState.lastAction) && friendBox != null) {
                    friendBox.setValue("");
                }
            }

            if (ExtrasClientState.lastCrateBatch != null) {
                // 连开走汇总面板，不需要转盘计时
                crateAnimStart = 0L;
            } else if (ExtrasClientState.lastCrateResult != null) {
                if (crateAnimStart <= 0L) crateAnimStart = System.currentTimeMillis();
            } else {
                // 失败/普通动作响应：解除「开箱中」并给出反馈
                crateAnimStart = 0L;
                crateRevealAt = 0L;
                crateBatchSkipped = false;
                crateBatchRevealAt = 0L;
                crateModal = false;
            }

            boolean cratePayload = resp.contains("\"reel\"") || resp.contains("\"wons\"");
            if (!cratePayload) {
                if (!ExtrasClientState.lastError.isEmpty()) {
                    ExtrasNoticeQueue.pushPlain(ExtrasClientState.lastError, "error");
                } else if (!ExtrasClientState.lastMessage.isEmpty()
                        && IMPORTANT_SUCCESS.contains(ExtrasClientState.lastAction)) {
                    ExtrasNoticeQueue.pushPlain(ExtrasClientState.lastMessage, "ok");
                }
            }
        }
    }

    /** 只有这些成功动作保留右下角提示，其余（管理员保存、兑换、复制等）不再刷屏 */
    private static final Set<String> IMPORTANT_SUCCESS = Set.of(
            "sign_in", "redeem", "transfer", "mail_claim", "mail_claim_all");

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
