package com.sellbox.mod.extras.client;

import com.sellbox.mod.gui.GuiDraw;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

/**
 * 扩展面板的管理页（转换表 / 兑换码 / 发邮件 / 签到 / 开箱）。
 * <p>
 * 从 ExtrasScreen 拆出来只为控制单个文件的体积；所有状态仍留在
 * ExtrasScreen，这里的方法都是 static，第一个参数是宿主界面。
 * <p>
 * <b>布局约定</b>：每一行的 y 必须与 {@code ExtrasScreen#buildAdminBoxes()} 建框时用的
 * y 完全一致（都相对内容区顶部 {@link ExtrasScreen#ADMIN_TOP}），否则标签会飘到框外、
 * 输入框会互相压住。这里的常量就是从那边同步过来的。
 */
final class ExtrasAdminPages {
    private ExtrasAdminPages() {}

    static void renderAdmin(ExtrasScreen host, GuiGraphics g, int l, int t, int r, int b, int mx, int my) {
        int sh = 14;
        int sx = l;
        for (int i = 0; i < host.ADMIN_SUBTAB_KEYS.length; i++) {
            String label = ExtrasUi.tr(host.ADMIN_SUBTAB_KEYS[i]);
            int w = Math.max(40, host.font().width(label) + 10);
            boolean sel = i == host.adminTab;
            boolean hov = !sel && mx >= sx && mx < sx + w && my >= t && my < t + sh;
            GuiDraw.listRow(g, sx, t, w, sh, sel, hov);
            g.drawString(host.font(), label, sx + (w - host.font().width(label)) / 2,
                    t + (sh - host.font().lineHeight) / 2 + 1,
                    sel ? host.T.textPrimary : host.T.textMuted, GuiDraw.shadow());
            final int idx = i;
            host.addHit(sx, t, w, sh, () -> {
                host.adminTab = idx;
                host.adminScroll = 0;
                host.rebuildWidgets();
            });
            sx += w + 3;
        }

        int top = t + sh + 6;
        switch (host.adminTab) {
            case ExtrasScreen.ADMIN_TAB_SIGNIN -> renderAdminSignin(host, g, l, top, r, b, mx, my);
            case ExtrasScreen.ADMIN_TAB_MAIL -> renderAdminMail(host, g, l, top, r, b, mx, my);
            case ExtrasScreen.ADMIN_TAB_RATE -> renderAdminRates(host, g, l, top, r, b, mx, my);
            case ExtrasScreen.ADMIN_TAB_CRATE -> renderAdminCrate(host, g, l, top, r, b, mx, my);
            case ExtrasScreen.ADMIN_TAB_CODES -> renderAdminCodes(host, g, l, top, r, b, mx, my);
            default -> ExtrasSweeperPage.render(host, g, l, top, r, b, mx, my);
        }
    }

    /** 标签左对齐到 x，纵向与输入框内文字对齐 */
    private static int labelY(int boxY, int boxH) {
        return GuiDraw.innerTextY(boxY, boxH);
    }

    // ════════════════════════════════════════════
    //  转换表
    // ════════════════════════════════════════════

    /** 打开扩展模块自带的背包选物品面板（不带商店价格/分类设置） */
    private static void pickItem(ExtrasScreen host, java.util.function.BiConsumer<net.minecraft.world.item.ItemStack, Boolean> cb) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new ExtrasItemPickerScreen(host, cb));
    }

    static void renderAdminRates(ExtrasScreen host, GuiGraphics g, int l, int t, int r, int b, int mx, int my) {
        GuiDraw.section(g, l - 4, t - 4, r - l + 8, 30);
        g.drawString(host.font(), ExtrasUi.tr("extras.admin.value"), l, labelY(t, 16), host.T.textMuted, GuiDraw.shadow());
        if (host.adminValueBox != null) host.drawBoxAround(g, host.adminValueBox);
        host.button(g, l + 88, t, 74, 16, ExtrasUi.tr("extras.admin.add_held"), true, mx, my, () -> {
            long v = ExtrasUi.parseLong(host.adminValueBox);
            if (v <= 0) {
                ExtrasNoticeQueue.pushPlain(ExtrasUi.tr("extras.msg.admin_bad_value"), "error");
                return;
            }
            pickItem(host, (stack, strict) -> ExtrasActions.adminRateHold(v, stack, strict));
        });
        host.button(g, l + 166, t, 66, 16,
                ExtrasUi.tr(ExtrasClientState.autoExchange
                        ? "extras.admin.auto_exchange_on" : "extras.admin.auto_exchange_off"),
                ExtrasClientState.autoExchange, mx, my,
                () -> ExtrasActions.adminAutoExchange(!ExtrasClientState.autoExchange));
        host.button(g, l + 236, t, 60, 16,
                ExtrasUi.tr(ExtrasClientState.exchangeOut
                        ? "extras.admin.exchange_out_on" : "extras.admin.exchange_out_off"),
                ExtrasClientState.exchangeOut, mx, my,
                () -> ExtrasActions.adminExchangeOut(!ExtrasClientState.exchangeOut));

        g.drawString(host.font(), host.font().plainSubstrByWidth(ExtrasUi.tr("extras.admin.hint"), r - l),
                l, t + 22, host.T.textHint, GuiDraw.shadow());

        var list = ExtrasClientState.exchange;
        int listTop = t + 38;
        if (list.isEmpty()) {
            GuiDraw.note(g, host.font(), ExtrasUi.tr("extras.admin.empty"), (l + r) / 2, listTop + 24, r - l);
            return;
        }
        GuiDraw.section(g, l - 2, listTop - 3, r - l + 4, b - listTop + 3);
        int rowH = 18;
        int maxRows = Math.max(1, (b - listTop) / rowH);
        host.adminScroll = Math.max(0, Math.min(host.adminScroll, Math.max(0, list.size() - maxRows)));
        int y = listTop;
        for (int i = host.adminScroll; i < list.size() && i < host.adminScroll + maxRows; i++) {
            var e = list.get(i);
            boolean hov = mx >= l && mx < r && my >= y && my < y + rowH - 2;
            GuiDraw.listRow(g, l, y, r - l, rowH - 2, false, hov);
            String price = ExtrasUi.tr("extras.exchange.rate", e.value);
            int priceX = r - 48 - host.font().width(price);
            // 价格放在第一行右侧；名称只占剩余宽度，物品 ID 单独放第二行，避免三者叠字
            g.drawString(host.font(), host.font().plainSubstrByWidth(e.name,
                            Math.max(20, priceX - (l + 4) - 6)),
                    l + 4, y + 2, host.T.textPrimary, GuiDraw.shadow());
            g.drawString(host.font(), host.font().plainSubstrByWidth(e.itemId, r - l - 64),
                    l + 4, y + 10, host.T.textHint, GuiDraw.shadow());
            g.drawString(host.font(), price, priceX, y + 2, host.T.textPrice, GuiDraw.shadow());
            final String id = e.itemId;
            host.button(g, r - 40, y + 2, 40, rowH - 6, ExtrasUi.tr("extras.admin.remove"), false, mx, my,
                    () -> ExtrasActions.adminRateRemove(id));
            GuiDraw.divider(g, l + 2, y + rowH - 2, r - l - 4);
            y += rowH;
        }
        GuiDraw.scrollbar(g, r + 4, listTop, b - listTop, host.adminScroll,
                Math.max(0, list.size() - maxRows));
    }

    // ════════════════════════════════════════════
    //  兑换码
    // ════════════════════════════════════════════

    /** 三段式随机兑换码：XXXXX-XXXXX-XXXXX */
    private static String randomRedeemCode() {
        final char[] chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
        var rnd = java.util.concurrent.ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(17);
        for (int seg = 0; seg < 3; seg++) {
            if (seg > 0) sb.append('-');
            for (int i = 0; i < 5; i++) sb.append(chars[rnd.nextInt(chars.length)]);
        }
        return sb.toString();
    }

    static void renderAdminCodes(ExtrasScreen host, GuiGraphics g, int l, int t, int r, int b, int mx, int my) {
        GuiDraw.section(g, l - 4, t - 4, r - l + 8, 54);
        if (host.adminCodeBox != null) host.drawBoxAround(g, host.adminCodeBox);
        if (host.adminCodeMoneyBox != null) host.drawBoxAround(g, host.adminCodeMoneyBox);
        if (host.adminCodeUsesBox != null) host.drawBoxAround(g, host.adminCodeUsesBox);

        g.drawString(host.font(), ExtrasUi.tr("extras.admin.code"), l, labelY(t, 16),
                host.T.textMuted, GuiDraw.shadow());
        g.drawString(host.font(), ExtrasUi.tr("extras.admin.money"), l, labelY(t + 20, 16),
                host.T.textMuted, GuiDraw.shadow());
        g.drawString(host.font(), ExtrasUi.tr("extras.admin.uses_limit"), l + 112, labelY(t + 20, 16),
                host.T.textMuted, GuiDraw.shadow());

        host.button(g, r - 64, t, 64, 16, ExtrasUi.tr("extras.admin.random_code"), false, mx, my, () -> {
            if (host.adminCodeBox != null) host.adminCodeBox.setValue(randomRedeemCode());
        });
        host.button(g, r - 64, t + 20, 64, 16, ExtrasUi.tr("extras.admin.create"), true, mx, my, () -> {
            String code = host.adminCodeBox == null ? "" : host.adminCodeBox.getValue().trim();
            if (code.isEmpty()) {
                ExtrasNoticeQueue.pushPlain(ExtrasUi.tr("extras.msg.redeem_empty"), "error");
                return;
            }
            long money = ExtrasUi.parseLong(host.adminCodeMoneyBox);
            if (money < 0) {
                ExtrasNoticeQueue.pushPlain(ExtrasUi.tr("extras.msg.admin_bad_value"), "error");
                return;
            }
            Integer parsedUses = ExtrasUi.parseIntOrNull(host.adminCodeUsesBox);
            if (host.adminCodeUsesBox != null && !host.adminCodeUsesBox.getValue().trim().isEmpty()
                    && (parsedUses == null || parsedUses < -1 || parsedUses == 0)) {
                ExtrasNoticeQueue.pushPlain(ExtrasUi.tr("extras.msg.admin_bad_value"), "error");
                return;
            }
            int uses = parsedUses == null ? -1 : parsedUses;
            ExtrasActions.adminCodeCreate(code, money, uses);
        });

        g.drawString(host.font(), host.font().plainSubstrByWidth(
                        ExtrasUi.tr("extras.admin.hint_code"), r - l),
                l, t + 40, host.T.textHint, GuiDraw.shadow());

        var list = ExtrasClientState.codes;
        int listTop = t + 54;
        if (list.isEmpty()) {
            GuiDraw.note(g, host.font(), ExtrasUi.tr("extras.admin.empty"), (l + r) / 2, listTop + 24, r - l);
            return;
        }
        GuiDraw.section(g, l - 2, listTop - 3, r - l + 4, b - listTop + 3);
        int rowH = 22;
        int maxRows = Math.max(1, (b - listTop) / rowH);
        host.adminScroll = Math.max(0, Math.min(host.adminScroll, Math.max(0, list.size() - maxRows)));
        int y = listTop;
        for (int i = host.adminScroll; i < list.size() && i < host.adminScroll + maxRows; i++) {
            var c = list.get(i);
            boolean hov = mx >= l && mx < r && my >= y && my < y + rowH - 2;
            GuiDraw.listRow(g, l, y, r - l, rowH - 2, false, hov);
            g.drawString(host.font(), c.code, l + 4, y + 2, host.T.textPrimary, GuiDraw.shadow());
            String usesLimit = c.maxUses < 0
                    ? ExtrasUi.tr("extras.admin.unlimited") : String.valueOf(c.maxUses);
            String sub = ExtrasUi.tr("extras.admin.uses", c.uses, usesLimit)
                    + "  " + ExtrasUi.tr("extras.admin.claimed", c.claimed)
                    + "  " + ExtrasUi.tr("extras.admin.money_value", c.money);
            g.drawString(host.font(), host.font().plainSubstrByWidth(sub, r - l - 92),
                    l + 4, y + 11, host.T.textMuted, GuiDraw.shadow());
            final String code = c.code;
            host.button(g, r - 84, y + 2, 36, rowH - 6, ExtrasUi.tr("extras.admin.copy"), false, mx, my, () -> {
                net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(code);
                ExtrasNoticeQueue.pushPlain(ExtrasUi.tr("extras.msg.code_copied"), "ok");
            });
            host.button(g, r - 40, y + 2, 40, rowH - 6, ExtrasUi.tr("extras.admin.remove"), false, mx, my,
                    () -> ExtrasActions.adminCodeRemove(code));
            GuiDraw.divider(g, l + 2, y + rowH - 2, r - l - 4);
            y += rowH;
        }
        GuiDraw.scrollbar(g, r + 4, listTop, b - listTop, host.adminScroll,
                Math.max(0, list.size() - maxRows));
    }

    // ════════════════════════════════════════════
    //  发邮件
    // ════════════════════════════════════════════

    static void renderAdminMail(ExtrasScreen host, GuiGraphics g, int l, int t, int r, int b, int mx, int my) {
        GuiDraw.section(g, l - 4, t - 4, r - l + 8, b - t + 2);
        String asServer = ExtrasUi.tr("extras.admin.as_server");
        g.drawString(host.font(), asServer, r - host.font().width(asServer), labelY(t, 16),
                host.T.textHint, GuiDraw.shadow());

        g.drawString(host.font(), ExtrasUi.tr("extras.admin.target"), l, labelY(t, 16), host.T.textMuted, GuiDraw.shadow());
        if (host.adminTargetBox != null) host.drawBoxAround(g, host.adminTargetBox);

        g.drawString(host.font(), ExtrasUi.tr("extras.admin.title"), l, labelY(t + 20, 16), host.T.textMuted, GuiDraw.shadow());
        if (host.adminTitleBox != null) host.drawBoxAround(g, host.adminTitleBox);

        g.drawString(host.font(), ExtrasUi.tr("extras.admin.money"), l, labelY(t + 40, 16), host.T.textMuted, GuiDraw.shadow());
        if (host.adminMoneyBox != null) host.drawBoxAround(g, host.adminMoneyBox);

        // ── 两个开关 ──
        int sw = 8;
        int sx = l + 96, sy = t + 42;
        host.chk(g, sx, sy, sw, host.adminToAll, ExtrasUi.tr("extras.admin.send_all"), mx, my, () -> {
            host.adminToAll = !host.adminToAll;
            if (!host.adminToAll) host.adminWelcome = false;
        });
        int sx2 = sx + host.font().width(ExtrasUi.tr("extras.admin.send_all")) + sw + 12;
        host.chk(g, sx2, sy, sw, host.adminWelcome, ExtrasUi.tr("extras.admin.as_welcome"), mx, my, () -> {
            host.adminWelcome = !host.adminWelcome;
            // 「欢迎邮件」只对群发有意义：勾上时自动切到群发，避免勾了却不生效
            if (host.adminWelcome) host.adminToAll = true;
        });

        host.button(g, l + 96, t + 64, 56, 16,
                ExtrasUi.tr(host.adminToAll ? "extras.admin.send_all_btn" : "extras.admin.send"),
                true, mx, my, () -> {
                    String title = host.adminTitleBox == null ? "" : host.adminTitleBox.getValue().trim();
                    long money = ExtrasUi.parseLong(host.adminMoneyBox);
                    if (host.adminToAll) {
                        ExtrasActions.adminMailBroadcast(title, "", money, host.adminWelcome);
                    } else {
                        String target = host.adminTargetBox == null ? "" : host.adminTargetBox.getValue().trim();
                        if (target.isEmpty()) {
                            ExtrasNoticeQueue.pushPlain(ExtrasUi.tr("extras.msg.mail_target_empty"), "error");
                            return;
                        }
                        ExtrasActions.adminMailSend(target, title, "", money);
                    }
                });

        g.drawString(host.font(), host.font().plainSubstrByWidth(
                        ExtrasUi.tr(host.adminToAll ? "extras.admin.hint_all" : "extras.admin.hint_mail"), r - l),
                l, t + 86, host.T.textHint, GuiDraw.shadow());

        int saveWelcomeRight = host.buttonAuto(g, l, t + 100, 14,
                ExtrasUi.tr("extras.admin.as_welcome"), false, mx, my, () -> {
                    String title = host.adminTitleBox == null ? "" : host.adminTitleBox.getValue().trim();
                    long money = ExtrasUi.parseLong(host.adminMoneyBox);
                    ExtrasActions.adminWelcomeAdd(title, "", money);
                });
        host.buttonAuto(g, saveWelcomeRight + 4, t + 100, 14,
                ExtrasUi.tr("extras.admin.welcome_clear"), false, mx, my,
                ExtrasActions::adminWelcomeClear);
        // 下拉列表最后画，盖住下面的标题/金币/发送按钮
        renderAdminTargetPicker(host, g, mx, my);
    }

    /** 管理·发邮件：收件人右侧的在线玩家下拉（往下展开，避免压住"标题/金币"两行） */
    static void renderAdminTargetPicker(ExtrasScreen host, GuiGraphics g, int mx, int my) {
        int fbW = 20, fbH = 16;
        int fbX = host.boxDX + 34 + 150 + 4;
        int fbY = ExtrasScreen.ADMIN_TOP;

        boolean fhover = mx >= fbX && mx < fbX + fbW && my >= fbY && my < fbY + fbH;
        GuiDraw.fillRounded(g, fbX, fbY, fbW, fbH,
                (fhover || host.adminTargetPickerOpen) ? host.T.btnBgHovered : host.T.btnBgNormal);
        if (host.T.btnBorder != 0) GuiDraw.borderRounded(g, fbX, fbY, fbW, fbH, host.T.btnBorder);
        String arrow = host.adminTargetPickerOpen ? "\u25B4" : "\u25BE";
        g.drawString(host.font(), arrow, fbX + (fbW - host.font().width(arrow)) / 2,
                fbY + (fbH - host.font().lineHeight) / 2 + 1, host.T.textPrimary, GuiDraw.shadow());
        host.addHit(fbX, fbY, fbW, fbH, () -> {
            host.adminTargetPickerOpen = !host.adminTargetPickerOpen;
            host.adminTargetPickerScroll = 0;
            if (host.adminTargetPickerOpen) host.setFocused(null);
        });

        if (!host.adminTargetPickerOpen) return;
        host.addHit(0, 0, host.DESIGN_W, host.DESIGN_H, () -> host.adminTargetPickerOpen = false);

        var onlines = ExtrasClientState.onlinePlayers;
        int pw = 110, rowH = 12, maxRows = 8;
        int px = fbX, py = fbY + fbH + 2;
        host.adminTargetPickerScroll = Math.max(0, Math.min(host.adminTargetPickerScroll, Math.max(0, onlines.size() - maxRows)));
        int rows = Math.max(1, Math.min(maxRows, onlines.size()));
        int ph = rows * rowH + 2;

        int bg = host.T.popupBg != 0 ? host.T.popupBg : host.T.mainPanelBg;
        GuiDraw.fillRounded(g, px, py, pw, ph, bg);
        int border = host.T.popupBorder != 0 ? host.T.popupBorder : host.T.separatorLine;
        if (border != 0) GuiDraw.borderRounded(g, px, py, pw, ph, border);

        if (onlines.isEmpty()) {
            GuiDraw.note(g, host.font(), ExtrasUi.tr("extras.admin.no_online"), px + pw / 2, py + 3, pw - 8);
            return;
        }

        int yy = py + 1;
        for (int i = host.adminTargetPickerScroll; i < onlines.size() && i < host.adminTargetPickerScroll + maxRows; i++) {
            String name = onlines.get(i);
            boolean hov = mx >= px && mx < px + pw && my >= yy && my < yy + rowH;
            if (hov) GuiDraw.fillRounded(g, px + 1, yy, pw - 2, rowH, host.T.listRowHover);
            g.drawString(host.font(), name, px + 4, yy + 2, host.T.textPrimary, GuiDraw.shadow());
            host.addHit(px, yy, pw, rowH, () -> {
                if (host.adminTargetBox != null) host.adminTargetBox.setValue(name);
                host.adminTargetPickerOpen = false;
            });
            yy += rowH;
        }
    }

    // ════════════════════════════════════════════
    //  签到规则
    // ════════════════════════════════════════════

    static void renderAdminSignin(ExtrasScreen host, GuiGraphics g, int l, int t, int r, int b, int mx, int my) {
        boolean linear = !"table".equals(ExtrasClientState.signinMode);
        int base = t;                     // 与 ExtrasScreen.ADMIN_TOP 对齐

        // 三个分组框：参数 / 逐日奖励 / 连续签到奖励
        GuiDraw.section(g, l - 4, base + ExtrasScreen.SIGN_PARAM_Y - 4, r - l + 8, 22);
        GuiDraw.section(g, l - 4, base + ExtrasScreen.SIGN_EXACT_TITLE_Y - 2, r - l + 8,
                ExtrasScreen.SIGN_MILE_TITLE_Y - ExtrasScreen.SIGN_EXACT_TITLE_Y + 1);
        GuiDraw.section(g, l - 4, base + ExtrasScreen.SIGN_MILE_TITLE_Y - 2, r - l + 8,
                Math.max(24, b - (base + ExtrasScreen.SIGN_MILE_TITLE_Y - 2)));

        // ── 模式切换 ──
        int y = base + ExtrasScreen.SIGN_MODE_Y;
        g.drawString(host.font(), ExtrasUi.tr("extras.admin.mode"), l, y + 3,
                host.T.textMuted, GuiDraw.shadow());
        int mw = 54, mh = 14;
        int m0x = l + 34;
        GuiDraw.listRow(g, m0x, y, mw, mh, linear, !linear && mx >= m0x && mx < m0x + mw && my >= y && my < y + mh);
        String m0 = ExtrasUi.tr("extras.admin.signin_linear");
        g.drawString(host.font(), m0, m0x + (mw - host.font().width(m0)) / 2,
                y + (mh - host.font().lineHeight) / 2 + 1,
                linear ? host.T.textPrimary : host.T.textMuted, GuiDraw.shadow());
        host.addHit(m0x, y, mw, mh, () -> ExtrasActions.adminSigninMode("linear"));

        int mx2 = m0x + mw + 4;
        GuiDraw.listRow(g, mx2, y, mw, mh, !linear, linear && mx >= mx2 && mx < mx2 + mw && my >= y && my < y + mh);
        String m1 = ExtrasUi.tr("extras.admin.signin_table");
        g.drawString(host.font(), m1, mx2 + (mw - host.font().width(m1)) / 2,
                y + (mh - host.font().lineHeight) / 2 + 1,
                !linear ? host.T.textPrimary : host.T.textMuted, GuiDraw.shadow());
        host.addHit(mx2, y, mw, mh, () -> ExtrasActions.adminSigninMode("table"));

        String hint = ExtrasUi.tr("extras.admin.signin_hint");
        g.drawString(host.font(), host.font().plainSubstrByWidth(hint, r - (mx2 + mw) - 6),
                mx2 + mw + 6, y + 3, host.T.textHint, GuiDraw.shadow());

        // ── 参数区：线性模式显示基础/加成/封顶，表模式显示说明 ──
        int py = base + ExtrasScreen.SIGN_PARAM_Y;
        int lb = labelY(py, 14);
        if (host.adminBaseBox != null) host.adminBaseBox.setVisible(linear);
        if (host.adminStepBox != null) host.adminStepBox.setVisible(linear);
        if (host.adminCapBox != null) host.adminCapBox.setVisible(linear);
        if (linear) {
            g.drawString(host.font(), ExtrasUi.tr("extras.admin.base"), l, lb, host.T.textMuted, GuiDraw.shadow());
            if (host.adminBaseBox != null) host.drawBoxAround(g, host.adminBaseBox);
            g.drawString(host.font(), ExtrasUi.tr("extras.admin.step"), l + 78, lb, host.T.textMuted, GuiDraw.shadow());
            if (host.adminStepBox != null) host.drawBoxAround(g, host.adminStepBox);
            g.drawString(host.font(), ExtrasUi.tr("extras.admin.cap"), l + 156, lb, host.T.textMuted, GuiDraw.shadow());
            if (host.adminCapBox != null) host.drawBoxAround(g, host.adminCapBox);

            host.button(g, r - 40, py, 40, 14, ExtrasUi.tr("extras.admin.save"), true, mx, my, () -> {
                Long bv = ExtrasUi.parseLongOrNull(host.adminBaseBox);
                Long sv = ExtrasUi.parseLongOrNull(host.adminStepBox);
                Integer cv = ExtrasUi.parseIntOrNull(host.adminCapBox);
                if ((host.adminBaseBox != null && !host.adminBaseBox.getValue().trim().isEmpty() && bv == null)
                        || (host.adminStepBox != null && !host.adminStepBox.getValue().trim().isEmpty() && sv == null)
                        || (host.adminCapBox != null && !host.adminCapBox.getValue().trim().isEmpty() && cv == null)) {
                    ExtrasNoticeQueue.pushPlain(ExtrasUi.tr("extras.msg.admin_bad_value"), "error");
                    return;
                }
                long baseVal = bv == null ? ExtrasClientState.signinBase : bv;
                long stepVal = sv == null ? ExtrasClientState.signinStep : sv;
                int capVal = cv == null ? ExtrasClientState.signinCap : cv;
                ExtrasActions.adminSigninSet("linear", baseVal, stepVal, Math.max(1, capVal), 0, -1);
            });
        } else {
            g.drawString(host.font(), host.font().plainSubstrByWidth(
                            ExtrasUi.tr("extras.admin.signin_table_note"), r - l),
                    l, lb, host.T.textHint, GuiDraw.shadow());
        }

        // ── 逐日奖励：仅表模式显示，线性模式用说明占位 ──
        int ey = base + ExtrasScreen.SIGN_EXACT_TITLE_Y;
        if (host.adminDayBox != null) host.adminDayBox.setVisible(!linear);
        if (host.adminDayMoneyBox != null) host.adminDayMoneyBox.setVisible(!linear);
        if (linear) {
            g.drawString(host.font(), host.font().plainSubstrByWidth(
                            ExtrasUi.tr("extras.admin.signin_linear_note"), r - l), l, ey,
                    host.T.textTitle, GuiDraw.shadow());
        } else {
            g.drawString(host.font(), ExtrasUi.tr("extras.admin.exact_title"), l, ey,
                    host.T.textTitle, GuiDraw.shadow());

            int ry = base + ExtrasScreen.SIGN_EXACT_ROW_Y;
            int rl = labelY(ry, 14);
            g.drawString(host.font(), ExtrasUi.tr("extras.admin.day_label"), l, rl,
                    host.T.textMuted, GuiDraw.shadow());
            g.drawString(host.font(), ExtrasUi.tr("extras.admin.money"), l + 72, rl,
                    host.T.textMuted, GuiDraw.shadow());
            if (host.adminDayBox != null) host.drawBoxAround(g, host.adminDayBox);
            if (host.adminDayMoneyBox != null) host.drawBoxAround(g, host.adminDayMoneyBox);
            host.button(g, l + 180, ry, 34, 14, ExtrasUi.tr("extras.admin.add"), true, mx, my, () -> {
                int day = ExtrasUi.parseInt(host.adminDayBox);
                long money = ExtrasUi.parseLong(host.adminDayMoneyBox);
                if (day <= 0) {
                    ExtrasNoticeQueue.pushPlain(ExtrasUi.tr("extras.msg.admin_bad_value"), "error");
                    return;
                }
                ExtrasActions.adminSigninSet(
                        ExtrasClientState.signinMode,
                        ExtrasClientState.signinBase, ExtrasClientState.signinStep,
                        ExtrasClientState.signinCap, day, money);
            });

            int cy = base + ExtrasScreen.SIGN_EXACT_CHIP_Y;
            var exact = ExtrasClientState.signinExact;
            if (exact.isEmpty()) {
                g.drawString(host.font(), ExtrasUi.tr("extras.admin.exact_empty"), l, cy,
                        host.T.textHint, GuiDraw.shadow());
            } else {
                int perRow = 3, pageSize = perRow * 3, chipH = 12;
                int pages = Math.max(1, (exact.size() + pageSize - 1) / pageSize);
                host.adminExactPage = Math.max(0, Math.min(host.adminExactPage, pages - 1));
                if (pages > 1) {
                    String pageText = (host.adminExactPage + 1) + "/" + pages;
                    g.drawString(host.font(), pageText, r - 54 - host.font().width(pageText), ey,
                            host.T.textHint, GuiDraw.shadow());
                    host.button(g, r - 36, ey - 2, 14, 12, "‹", false, mx, my,
                            () -> host.adminExactPage = Math.max(0, host.adminExactPage - 1));
                    host.button(g, r - 18, ey - 2, 14, 12, "›", false, mx, my,
                            () -> host.adminExactPage = Math.min(pages - 1, host.adminExactPage + 1));
                }
                int start = host.adminExactPage * pageSize;
                int shown = 0;
                for (int idx = start; idx < exact.size() && shown < pageSize; idx++, shown++) {
                    var d = exact.get(idx);
                    int cx = l + (shown % perRow) * ((r - l) / perRow);
                    int ccy = cy + (shown / perRow) * (chipH + 2);
                    if (ccy + chipH > base + ExtrasScreen.SIGN_MILE_TITLE_Y - 2) break;
                    String txt = d.day + ExtrasUi.tr("extras.admin.day_unit") + " " + d.money;
                    int cwd = Math.min((r - l) / perRow - 3, host.font().width(txt) + 14);
                    int bg = host.T.catPillBg != 0 ? host.T.catPillBg : host.T.listRowBg;
                    if (bg != 0) GuiDraw.fillRounded(g, cx, ccy, cwd, chipH, bg);
                    g.drawString(host.font(), host.font().plainSubstrByWidth(txt, cwd - 12),
                            cx + 4, ccy + 2, host.T.textPrimary, GuiDraw.shadow());
                    g.drawString(host.font(), "x", cx + cwd - 8, ccy + 2, host.T.textError, GuiDraw.shadow());
                    final int day = d.day;
                    host.addHit(cx, ccy, cwd, chipH, () -> ExtrasActions.adminSigninSet(
                            ExtrasClientState.signinMode, ExtrasClientState.signinBase, ExtrasClientState.signinStep,
                            ExtrasClientState.signinCap, day, 0));
                }
            }
        }

        // ── 连续签到奖励 ──
        int my0 = base + ExtrasScreen.SIGN_MILE_TITLE_Y;
        g.drawString(host.font(), ExtrasUi.tr("extras.admin.mile_title"), l, my0, host.T.textTitle, GuiDraw.shadow());

        int mry = base + ExtrasScreen.SIGN_MILE_ROW_Y;
        int mrl = labelY(mry, 14);
        g.drawString(host.font(), ExtrasUi.tr("extras.admin.day_label"), l, mrl,
                host.T.textMuted, GuiDraw.shadow());
        g.drawString(host.font(), ExtrasUi.tr("extras.admin.money"), l + 72, mrl,
                host.T.textMuted, GuiDraw.shadow());
        if (host.adminMileDayBox != null) host.drawBoxAround(g, host.adminMileDayBox);
        if (host.adminMileMoneyBox != null) host.drawBoxAround(g, host.adminMileMoneyBox);
        boolean mileEditing = host.adminMileEditDay > 0;
        if (mileEditing) {
            host.button(g, l + 168, mry, 46, 14, ExtrasUi.tr("extras.admin.save"), true, mx, my, () -> {
                int newDay = ExtrasUi.parseInt(host.adminMileDayBox);
                long money = ExtrasUi.parseLong(host.adminMileMoneyBox);
                if (newDay <= 0) {
                    ExtrasNoticeQueue.pushPlain(ExtrasUi.tr("extras.msg.admin_bad_value"), "error");
                    return;
                }
                if (newDay == host.adminMileEditDay) {
                    ExtrasActions.adminMileMoney(newDay, money);
                } else {
                    ExtrasActions.adminMileUpdate(host.adminMileEditDay, newDay, money);
                }
                host.adminMileEditDay = 0;
            });
            host.button(g, l + 218, mry, 46, 14, ExtrasUi.tr("extras.admin.mile_add_item"), true, mx, my,
                    () -> pickItem(host, (stack, strict) ->
                            ExtrasActions.adminSigninMile(host.adminMileEditDay, 0L, stack, strict)));
            host.button(g, l + 268, mry, 32, 14, ExtrasUi.tr("extras.admin.cancel"), false, mx, my,
                    () -> host.adminMileEditDay = 0);
        } else {
            host.button(g, l + 180, mry, 80, 14, ExtrasUi.tr("extras.admin.mile_add"), true, mx, my, () -> {
                int day = ExtrasUi.parseInt(host.adminMileDayBox);
                long money = ExtrasUi.parseLong(host.adminMileMoneyBox);
                if (day <= 0) {
                    ExtrasNoticeQueue.pushPlain(ExtrasUi.tr("extras.msg.admin_bad_value"), "error");
                    return;
                }
                pickItem(host, (stack, strict) -> ExtrasActions.adminSigninMile(day, money, stack, strict));
            });
        }

        int ly = base + ExtrasScreen.SIGN_MILE_LIST_Y;
        var miles = ExtrasClientState.signinMiles;
        if (miles.isEmpty()) {
            g.drawString(host.font(), ExtrasUi.tr("extras.admin.mile_empty"), l, ly, host.T.textHint, GuiDraw.shadow());
        }
        // 里程碑默认就有 5 条，而可见区只放得下 4 行；不滚动的话最后一条永远看不到。
        int maxStart = Math.max(0, miles.size() - 1);
        host.adminScroll = Math.max(0, Math.min(host.adminScroll, maxStart));
        int skipped = 0;
        for (var m : miles) {
            if (skipped++ < host.adminScroll) continue;
            boolean expanded = (host.mileDetailDay == m.day);
            int rowH = 13;
            int btnDelW = 34, btnEditW = 34, btnExpW = 14;
            int rightX = r - btnDelW;
            int editX = rightX - 4 - btnEditW;
            int expX = editX - 4 - btnExpW;
            if (ly + rowH > b) break;

            boolean hov = mx >= l && mx < expX - 4 && my >= ly && my < ly + rowH;
            GuiDraw.listRow(g, l, ly, r - l, rowH, expanded, hov);
            StringBuilder sb = new StringBuilder(ExtrasUi.tr("extras.sign.pill", m.day, m.money));
            if (!m.items.isEmpty()) sb.append("  +").append(m.items.size()).append(ExtrasUi.tr("extras.admin.items_unit"));
            g.drawString(host.font(), host.font().plainSubstrByWidth(sb.toString(), expX - l - 8),
                    l + 4, ly + 2, host.T.textPrimary, GuiDraw.shadow());

            final int day = m.day;
            host.button(g, expX, ly - 1, btnExpW, rowH, expanded ? "-" : "+", false, mx, my,
                    () -> host.mileDetailDay = (host.mileDetailDay == day) ? 0 : day);
            host.button(g, editX, ly - 1, btnEditW, rowH, ExtrasUi.tr("extras.admin.mile_set"), true, mx, my, () -> {
                // 点「改」先读回上面的天数/金币，再点「保存」提交
                host.adminMileEditDay = day;
                if (host.adminMileDayBox != null) host.adminMileDayBox.setValue(String.valueOf(m.day));
                if (host.adminMileMoneyBox != null) host.adminMileMoneyBox.setValue(String.valueOf(m.money));
            });
            host.button(g, rightX, ly - 1, btnDelW, rowH, ExtrasUi.tr("extras.admin.remove"), false, mx, my, () -> {
                if (host.adminMileEditDay == day) host.adminMileEditDay = 0;
                ExtrasActions.adminSigninMileDel(day);
            });
            ly += rowH + 1;

            if (expanded) {
                if (m.items.isEmpty()) {
                    g.drawString(host.font(), ExtrasUi.tr("extras.admin.mile_no_items"), l + 10, ly + 1,
                            host.T.textHint, GuiDraw.shadow());
                    ly += 12;
                } else {
                    int cx = l + 10, chipH = 12;
                    for (int i = 0; i < m.items.size(); i++) {
                        String nm = m.items.get(i);
                        int cwd = Math.min(r - l - 12, host.font().width(nm) + 16);
                        if (cx + cwd > r - 6) { cx = l + 10; ly += chipH + 2; }
                        if (ly + chipH > b) break;
                        int bg = host.T.catPillBg != 0 ? host.T.catPillBg : host.T.listRowBg;
                        if (bg != 0) GuiDraw.fillRounded(g, cx, ly, cwd, chipH, bg);
                        g.drawString(host.font(), host.font().plainSubstrByWidth(nm, cwd - 12),
                                cx + 4, ly + 2, host.T.textPrimary, GuiDraw.shadow());
                        g.drawString(host.font(), "x", cx + cwd - 8, ly + 2, host.T.textError, GuiDraw.shadow());
                        final int di = day, idx = i;
                        host.addHit(cx, ly, cwd, chipH, () -> ExtrasActions.adminMileItemDel(di, idx));
                        cx += cwd + 3;
                    }
                    ly += chipH + 4;
                }
            }
        }
        GuiDraw.scrollbar(g, r + 4, base + ExtrasScreen.SIGN_MILE_LIST_Y,
                Math.max(0, b - (base + ExtrasScreen.SIGN_MILE_LIST_Y)), host.adminScroll, maxStart);
    }

    // ════════════════════════════════════════════
    //  开箱配置
    // ════════════════════════════════════════════

    static void renderAdminCrate(ExtrasScreen host, GuiGraphics g, int l, int t, int r, int b, int mx, int my) {
        var list = ExtrasClientState.crates;
        host.crateTab = list.isEmpty() ? 0 : Math.max(0, Math.min(host.crateTab, list.size() - 1));

        // ── 箱体分页 ──
        int y = t + ExtrasScreen.CRATE_TAB_Y;
        int start = list.isEmpty() ? 0 : Math.max(0, Math.min(host.crateTabStart, list.size() - 1));
        host.crateTabStart = start;
        // 当前箱体选中的图标显示在箱体页签左侧
        int tabX = l;
        if (!list.isEmpty()) {
            var selected = list.get(host.crateTab);
            if (selected.icon != null && !selected.icon.isEmpty()) {
                com.sellbox.mod.util.ItemIcons.render(g, selected.icon, l, y - 1);
                tabX = l + 20;
            }
        }
        int tx = tabX;
        int last = start - 1;
        for (int i = start; i < list.size(); i++) {
            String label = list.get(i).name;
            int w = Math.max(38, host.font().width(label) + 12);
            if (tx + w > r - 60) break;      // 右侧留给翻页箭头 + 新建按钮
            boolean sel = i == host.crateTab;
            boolean hov = !sel && mx >= tx && mx < tx + w && my >= y && my < y + 14;
            GuiDraw.listRow(g, tx, y, w, 14, sel, hov);
            g.drawString(host.font(), label, tx + (w - host.font().width(label)) / 2,
                    y + (14 - host.font().lineHeight) / 2 + 1,
                    sel ? host.T.textPrimary : host.T.textMuted, GuiDraw.shadow());
            final int idx = i;
            host.addHit(tx, y, w, 14, () -> {
                host.crateTab = idx;
                host.adminCrateEditIndex = -1;
                host.adminCrateEditName = "";
                host.rebuildWidgets();
            });
            last = i;
            tx += w + 3;
        }
        if (start > 0) {
            ExtrasHomePages.drawArrow(host, g, r - 56, y, 10, 14, "‹", mx, my,
                    () -> host.crateTabStart = Math.max(0, start - 1));
        }
        if (last < list.size() - 1) {
            ExtrasHomePages.drawArrow(host, g, r - 44, y, 10, 14, "›", mx, my,
                    () -> host.crateTabStart = start + 1);
        }
        host.button(g, r - 28, y, 28, 14, "+", true, mx, my, () -> {
            String nm = host.adminCrateNameBox == null ? "" : host.adminCrateNameBox.getValue().trim();
            if (nm.isEmpty()) {
                ExtrasNoticeQueue.pushPlain(ExtrasUi.tr("extras.msg.admin_bad_value"), "error");
                return;
            }
            ExtrasActions.adminCrateCreate(nm);
        });

        GuiDraw.section(g, l - 4, t + 16, r - l + 8, 80);
        if (list.isEmpty()) {
            // 没有箱子时也要把「箱名」输入框画出来，否则右上角 + 需要的名字是隐形的；
            // 其余价格/概率/保底输入框没有对应箱体，必须藏起来，否则会只剩提示文字飘在页面上。
            if (host.adminCratePriceBox != null) host.adminCratePriceBox.setVisible(false);
            if (host.adminChanceBox != null) host.adminChanceBox.setVisible(false);
            if (host.adminPityCountBox != null) host.adminPityCountBox.setVisible(false);
            int emptyNy = t + ExtrasScreen.CRATE_NAME_Y;
            g.drawString(host.font(), ExtrasUi.tr("extras.admin.crate_name"), l, labelY(emptyNy, 14),
                    host.T.textMuted, GuiDraw.shadow());
            if (host.adminCrateNameBox != null) host.drawBoxAround(g, host.adminCrateNameBox);
            GuiDraw.note(g, host.font(), ExtrasUi.tr("extras.admin.crate_empty"),
                    (l + r) / 2, emptyNy + 26, r - l);
            return;
        }
        var crate = list.get(host.crateTab);
        // 从「无箱体」状态恢复时，把曾隐藏的输入框重新显示
        if (host.adminCratePriceBox != null) host.adminCratePriceBox.setVisible(true);
        if (host.adminChanceBox != null) host.adminChanceBox.setVisible(true);
        if (host.adminPityCountBox != null) host.adminPityCountBox.setVisible(true);

        // ── 箱名 / 单价 ──
        int ny = t + ExtrasScreen.CRATE_NAME_Y;
        int nl = labelY(ny, 14);
        g.drawString(host.font(), ExtrasUi.tr("extras.admin.crate_name"), l, nl, host.T.textMuted, GuiDraw.shadow());
        if (host.adminCrateNameBox != null) host.drawBoxAround(g, host.adminCrateNameBox);
        g.drawString(host.font(), ExtrasUi.tr("extras.admin.crate_price"), l + 112, nl, host.T.textMuted, GuiDraw.shadow());
        if (host.adminCratePriceBox != null) host.drawBoxAround(g, host.adminCratePriceBox);
        host.button(g, r - 112, ny, 30, 14, ExtrasUi.tr("extras.admin.icon"), false, mx, my,
                () -> pickItem(host, (stack, strict) -> ExtrasActions.adminCrateIcon(crate.id, stack, strict)));
        host.button(g, r - 80, ny, 36, 14, ExtrasUi.tr("extras.admin.remove"), false, mx, my,
                () -> ExtrasActions.adminCrateRemove(crate.id));
        host.button(g, r - 40, ny, 40, 14, ExtrasUi.tr("extras.admin.save"), true, mx, my, () -> {
            String name = host.adminCrateNameBox == null || host.adminCrateNameBox.getValue().trim().isEmpty()
                    ? crate.name : host.adminCrateNameBox.getValue().trim();
            Long parsedPrice = ExtrasUi.parseLongOrNull(host.adminCratePriceBox);
            if (host.adminCratePriceBox != null && !host.adminCratePriceBox.getValue().trim().isEmpty()
                    && parsedPrice == null) {
                ExtrasNoticeQueue.pushPlain(ExtrasUi.tr("extras.msg.admin_bad_value"), "error");
                return;
            }
            long price = parsedPrice == null ? crate.price : parsedPrice;
            ExtrasActions.adminCrateUpdate(crate.id, name, price);
        });

        // ── 加奖品：概率 + 品质 + 手持物 ──
        int dy = t + ExtrasScreen.CRATE_DROP_Y;
        int dl = labelY(dy, 14);
        g.drawString(host.font(), ExtrasUi.tr("extras.admin.chance"), l, dl, host.T.textMuted, GuiDraw.shadow());
        if (host.adminChanceBox != null) host.drawBoxAround(g, host.adminChanceBox);
        g.drawString(host.font(), ExtrasUi.tr("extras.admin.rarity"), l + 96, dl, host.T.textMuted, GuiDraw.shadow());
        boolean editing = host.adminCrateEditIndex >= 0 && host.adminCrateEditIndex < crate.items.size();
        if (editing) {
            host.button(g, l + 178, dy, 40, 14, ExtrasUi.tr("extras.admin.cancel"), false, mx, my, () -> {
                host.adminCrateEditIndex = -1;
                host.adminCrateEditName = "";
            });
            host.button(g, l + 224, dy, 70, 14, ExtrasUi.tr("extras.admin.save"), true, mx, my, () -> {
                double chance = ExtrasUi.parseDouble(host.adminChanceBox, 1.0);
                if (chance <= 0) chance = 1.0;
                ExtrasActions.adminCrateItemSet(crate.id, host.adminCrateEditIndex, chance, host.adminRarityValue);
                host.adminCrateEditIndex = -1;
                host.adminCrateEditName = "";
            });
        } else {
            host.button(g, r - 100, dy, 100, 14, ExtrasUi.tr("extras.admin.add_held"), true, mx, my, () -> {
                double chance = ExtrasUi.parseDouble(host.adminChanceBox, 1.0);
                if (chance <= 0) chance = 1.0;
                final double addChance = chance;
                pickItem(host, (stack, strict) ->
                        ExtrasActions.adminCrateItemAdd(crate.id, addChance, host.adminRarityValue, stack, strict));
            });
        }
        String hint = editing
                ? ExtrasUi.tr("extras.admin.editing", host.adminCrateEditName)
                : ExtrasUi.tr("extras.admin.crate_hint");
        g.drawString(host.font(), host.font().plainSubstrByWidth(hint, r - l),
                l, dy + 18, editing ? host.T.textPrice : host.T.textHint, GuiDraw.shadow());

        // ── 保底设置：品质 + 次数 ──
        int py = t + ExtrasScreen.CRATE_PITY_Y;
        int pl = labelY(py, 14);
        g.drawString(host.font(), ExtrasUi.tr("extras.admin.pity"), l, pl, host.T.textMuted, GuiDraw.shadow());
        g.drawString(host.font(), ExtrasUi.tr("extras.admin.pity_count"), l + 104, pl, host.T.textMuted, GuiDraw.shadow());
        if (host.adminPityCountBox != null) host.drawBoxAround(g, host.adminPityCountBox);
        host.button(g, r - 40, py, 40, 14, ExtrasUi.tr("extras.admin.save"), true, mx, my, () -> {
            String rar = host.adminPityRarityValue == null || host.adminPityRarityValue.isEmpty()
                    ? crate.pityRarity : host.adminPityRarityValue;
            Integer parsedCnt = ExtrasUi.parseIntOrNull(host.adminPityCountBox);
            if (host.adminPityCountBox != null && !host.adminPityCountBox.getValue().trim().isEmpty()
                    && parsedCnt == null) {
                ExtrasNoticeQueue.pushPlain(ExtrasUi.tr("extras.msg.admin_bad_value"), "error");
                return;
            }
            int cnt = parsedCnt == null ? crate.pityCount : parsedCnt;
            ExtrasActions.adminCratePity(crate.id, rar, cnt);
        });
        String pityNow = crate.pityEnabled()
                ? ExtrasUi.tr("extras.crate.pity", crate.pityNow, crate.pityCount)
                : ExtrasUi.tr("extras.admin.pity_off");
        g.drawString(host.font(), pityNow, r - 46 - host.font().width(pityNow), pl,
                crate.pityEnabled() ? host.T.textMuted : host.T.textHint, GuiDraw.shadow());

        // ── 奖池明细 ──
        int ty = t + ExtrasScreen.CRATE_LIST_TITLE_Y;
        g.drawString(host.font(), ExtrasUi.tr("extras.crate.items"), l, ty, host.T.textTitle, GuiDraw.shadow());

        int listTop = t + ExtrasScreen.CRATE_LIST_Y;
        GuiDraw.section(g, l - 2, listTop - 3, r - l + 4, b - listTop + 3);
        double total = 0;
        for (var it : crate.items) total += Math.max(0, it.chance);
        int rowH = 15;
        int maxRows = Math.max(1, (b - listTop) / rowH);
        host.adminScroll = Math.max(0, Math.min(host.adminScroll, Math.max(0, crate.items.size() - maxRows)));
        int ry = listTop;
        for (int i = host.adminScroll; i < crate.items.size() && i < host.adminScroll + maxRows; i++) {
            var it = crate.items.get(i);
            boolean hov = mx >= l && mx < r && my >= ry && my < ry + rowH - 1;
            GuiDraw.listRow(g, l, ry, r - l, rowH - 1, false, hov);
            int rc = ExtrasUi.rarityColor(it.rarity);
            GuiDraw.dot(g, l + 3, ry + 6, 3, rc);
            var iconStack = ExtrasClientState.itemStack(it.id);
            if (!iconStack.isEmpty()) {
                com.sellbox.mod.util.ItemIcons.render(g, iconStack, l + 9, ry);
                if (hov && mx < r - 112) {
                    // 交给 ExtrasScreen 在 pose 弹栈后用屏幕坐标画
                    host.hoverItemStack = iconStack;
                    host.hoverItemX = host.screenMouseX;
                    host.hoverItemY = host.screenMouseY;
                }
            }
            String nm = it.count > 1 ? it.name + " x" + it.count : it.name;
            g.drawString(host.font(), host.font().plainSubstrByWidth(nm, r - l - 124), l + 27, ry + 3,
                    host.T.textPrimary, GuiDraw.shadow());
            String pct = total > 0
                    ? String.format(Locale.ROOT, "%.1f%%", it.chance * 100.0 / total) : "-";
            g.drawString(host.font(), pct, r - 78 - host.font().width(pct), ry + 3, rc, GuiDraw.shadow());
            final int idx = i;
            host.button(g, r - 74, ry, 30, rowH - 2, ExtrasUi.tr("extras.admin.set"), true, mx, my, () -> {
                // 点「改」不立刻写服务器：先把这条数据读回上面的表单，再点「保存」提交
                host.adminCrateEditIndex = idx;
                host.adminCrateEditName = it.count > 1 ? it.name + " x" + it.count : it.name;
                if (host.adminChanceBox != null) {
                    host.adminChanceBox.setValue(String.format(Locale.ROOT, "%.2f", it.chance));
                }
                host.adminRarityValue = it.rarity;
            });
            host.button(g, r - 40, ry, 36, rowH - 2, ExtrasUi.tr("extras.admin.remove"), false, mx, my,
                    () -> ExtrasActions.adminCrateItemDel(crate.id, idx));
            GuiDraw.divider(g, l + 2, ry + rowH - 2, r - l - 4);
            ry += rowH;
        }

        GuiDraw.scrollbar(g, r + 4, listTop, b - listTop, host.adminScroll,
                Math.max(0, crate.items.size() - maxRows));

        // 只隐藏会被下拉选项压住的输入框，页面其它区域保持原样
        boolean ddOpen = host.adminRarityDropdownOpen || host.adminPityRarityDropdownOpen;
        if (host.adminChanceBox != null) host.adminChanceBox.setVisible(!ddOpen);
        if (host.adminPityCountBox != null) host.adminPityCountBox.setVisible(!ddOpen);

        // 品质下拉最后画，保证盖住下面的奖池列表
        renderRarityDropdown(host, g, l + 120, dy, 60, 14,
                false, host.adminRarityValue, mx, my,
                () -> {
                    host.adminRarityDropdownOpen = !host.adminRarityDropdownOpen;
                    host.adminPityRarityDropdownOpen = false;
                },
                value -> {
                    host.adminRarityValue = value;
                    host.adminRarityDropdownOpen = false;
                });
        renderRarityDropdown(host, g, l + 46, py, 50, 14,
                false, host.adminPityRarityValue, mx, my,
                () -> {
                    host.adminPityRarityDropdownOpen = !host.adminPityRarityDropdownOpen;
                    host.adminRarityDropdownOpen = false;
                },
                value -> {
                    host.adminPityRarityValue = value;
                    host.adminPityRarityDropdownOpen = false;
                });
    }

    /** 在 super.render 之后调用，让下拉选项盖住输入框，而不是用全屏黑幕 */
    static void renderDeferredDropdown(ExtrasScreen host, GuiGraphics g, int l, int t, int r, int b, int mx, int my) {
        host.hoverItemStack = net.minecraft.world.item.ItemStack.EMPTY;
        host.hoverTip = null;
        int dy = t + ExtrasScreen.CRATE_DROP_Y;
        int py = t + ExtrasScreen.CRATE_PITY_Y;
        if (host.adminRarityDropdownOpen) {
            renderRarityDropdown(host, g, l + 120, dy, 60, 14, true, host.adminRarityValue, mx, my,
                    () -> {
                        host.adminRarityDropdownOpen = false;
                        host.adminPityRarityDropdownOpen = false;
                    },
                    value -> {
                        host.adminRarityValue = value;
                        host.adminRarityDropdownOpen = false;
                    });
        } else if (host.adminPityRarityDropdownOpen) {
            renderRarityDropdown(host, g, l + 46, py, 50, 14, true, host.adminPityRarityValue, mx, my,
                    () -> {
                        host.adminPityRarityDropdownOpen = false;
                        host.adminRarityDropdownOpen = false;
                    },
                    value -> {
                        host.adminPityRarityValue = value;
                        host.adminPityRarityDropdownOpen = false;
                    });
        }
    }

    /** 品质下拉：按钮 + 展开的 6 个品质选项 */
    private static void renderRarityDropdown(ExtrasScreen host, GuiGraphics g, int x, int y, int w, int h,
                                             boolean open, String selected, int mx, int my,
                                             Runnable toggle, java.util.function.Consumer<String> pick) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        GuiDraw.fillRounded(g, x, y, w, h, hover || open ? host.T.btnBgHovered : host.T.btnBgNormal);
        if (host.T.btnBorder != 0) GuiDraw.borderRounded(g, x, y, w, h, host.T.btnBorder);
        String label = ExtrasUi.rarityName(selected);
        g.drawString(host.font(), host.font().plainSubstrByWidth(label, w - 14), x + 4, y + 3,
                ExtrasUi.rarityColor(selected), GuiDraw.shadow());
        String arrow = open ? "▴" : "▾";
        g.drawString(host.font(), arrow, x + w - 10, y + 3, host.T.textMuted, GuiDraw.shadow());
        host.addHit(x, y, w, h, toggle);

        if (!open) return;

        // 先把整页压暗，并重新画按钮，让下拉和下面的内容彻底隔离开
        host.addHit(0, 0, ExtrasScreen.DESIGN_W, ExtrasScreen.DESIGN_H, toggle);
        g.flush();
        GuiDraw.fillRounded(g, x, y, w, h, host.T.btnBgHovered);
        if (host.T.btnBorder != 0) GuiDraw.borderRounded(g, x, y, w, h, host.T.btnBorder);
        g.drawString(host.font(), host.font().plainSubstrByWidth(label, w - 14), x + 4, y + 3,
                ExtrasUi.rarityColor(selected), GuiDraw.shadow());
        g.drawString(host.font(), arrow, x + w - 10, y + 3, host.T.textMuted, GuiDraw.shadow());

        int rowH = 14;
        int oy = y + h + 2;
        var options = com.sellbox.mod.extras.ExtrasConfig.CRATE_RARITY_ORDER;
        int oh = options.size() * rowH + 2;
        // 不透明底色，避免下面的“保底次数/奖池明细”透出来
        g.fill(x - 2, oy - 2, x + w + 6, oy + oh + 2, 0xFF0B1220);
        GuiDraw.fillRounded(g, x, oy, w + 4, oh, 0xFF141A28);
        int border = ExtrasUi.rarityColor(selected);
        GuiDraw.borderRounded(g, x, oy, w + 4, oh, border);
        int yy = oy + 1;
        for (String r : options) {
            boolean sel = r.equals(selected);
            boolean hov = mx >= x && mx < x + w + 4 && my >= yy && my < yy + rowH;
            if (sel) GuiDraw.fillRounded(g, x + 1, yy, w + 2, rowH, 0x663A5A8A);
            else if (hov) GuiDraw.fillRounded(g, x + 1, yy, w + 2, rowH, host.T.listRowHover);
            g.drawString(host.font(), ExtrasUi.rarityName(r), x + 6, yy + 3,
                    ExtrasUi.rarityColor(r), GuiDraw.shadow());
            host.addHit(x, yy, w + 4, rowH, () -> pick.accept(r));
            yy += rowH;
        }
    }
}
