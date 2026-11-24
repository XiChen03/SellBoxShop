package com.sellbox.mod.extras.client;

import com.sellbox.mod.gui.GuiDraw;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

/**
 * 扩展面板的常规页面（签到 / 邮箱 / 转换 / 转账 / 兑换 / 好友）。
 * <p>
 * 从 ExtrasScreen 拆出来只为控制单个文件的体积；所有状态仍留在
 * ExtrasScreen，这里的方法都是 static，第一个参数是宿主界面。
 * <p>
 * <b>布局约定</b>：内容区左上角是 {@code (l, t)}，右下角是 {@code (r, b)}（设计坐标）。
 * 输入框一律由 {@code ExtrasScreen} 建好、这里只负责 {@code drawBoxAround}，
 * 框的 y 与这里的标签 y 必须取自同一批常量，否则标签会飘到框外。
 */
final class ExtrasHomePages {
    private ExtrasHomePages() {}

    /** 内容区第一行输入框的 y（相对内容区顶 t），各页共用 */
    static final int ROW1 = ExtrasScreen.HOME_ROW1;
    /** 第二行（按钮行）的 y */
    static final int ROW2 = ExtrasScreen.HOME_ROW2;

    // ════════════════════════════════════════════
    //  签到
    // ════════════════════════════════════════════

    static void renderSign(ExtrasScreen host, GuiGraphics g, int l, int t, int r, int b, int mx, int my) {
        // 第 1 行：连续 / 最长（右侧留给签到按钮）
        String streakText = ExtrasUi.tr("extras.sign.streak", ExtrasClientState.streak);
        g.drawString(host.font(), streakText, l, t, host.T.textPrimary, GuiDraw.shadow());
        int headRight = l + host.font().width(streakText) + 14;
        String bestText = ExtrasUi.tr("extras.sign.best", ExtrasClientState.bestStreak);
        if (headRight + host.font().width(bestText) < r - 74) {
            g.drawString(host.font(), bestText, headRight, t, host.T.textMuted, GuiDraw.shadow());
        }

        // 签到按钮：贴右下角
        int btnW = 66, btnH = 16;
        int bx = r - btnW, by = b - btnH;
        boolean done = ExtrasClientState.signedToday;
        boolean hover = mx >= bx && mx < bx + btnW && my >= by && my < by + btnH;
        GuiDraw.fillRounded(g, bx, by, btnW, btnH, hover && !done ? host.T.btnBgHovered : host.T.btnBgNormal);
        if (host.T.btnBorder != 0) GuiDraw.borderRounded(g, bx, by, btnW, btnH, host.T.btnBorder);
        String btn = ExtrasUi.tr(done ? "extras.sign.done" : "extras.sign.button");
        g.drawString(host.font(), btn, bx + (btnW - host.font().width(btn)) / 2,
                by + (btnH - host.font().lineHeight) / 2 + 1,
                done ? host.T.textMuted : host.T.textPrimary, GuiDraw.shadow());
        if (!done) host.addHit(bx, by, btnW, btnH, ExtrasActions::signIn);

        // 第 2 行：里程奖励胶囊，放不下就换行
        int rowY = t + 14;
        String mileLabel = ExtrasUi.tr("extras.sign.milestones");
        g.drawString(host.font(), mileLabel, l, rowY, host.T.textTitle, GuiDraw.shadow());
        int px = l + host.font().width(mileLabel) + 8;
        for (var e : ExtrasClientState.signinMiles) {
            boolean claimed = ExtrasClientState.claimedMilestones.contains(e.day);
            boolean reached = ExtrasClientState.streak >= e.day;
            String label = ExtrasUi.tr("extras.sign.pill", e.day, e.money);
            int cw = host.font().width(label) + 10, ch = 12;
            if (px + cw > r && px > l) {          // 放不下就换行
                rowY += ch + 2;
                px = l;
            }
            int bg = claimed
                    ? (host.T.cellBg != 0 ? host.T.cellBg : ExtrasUi.mix(host.T.mainPanelBg, 0xFF000000, 0.12f))
                    : (reached
                        ? (host.T.catPillSelectedBg != 0 ? host.T.catPillSelectedBg : host.T.catSelectedFill)
                        : (host.T.catPillBg != 0 ? host.T.catPillBg : host.T.listRowBg));
            if (bg != 0) GuiDraw.fillRounded(g, px, rowY - 1, cw, ch, bg);
            if (claimed && host.T.separatorLine != 0) GuiDraw.borderRounded(g, px, rowY - 1, cw, ch, host.T.separatorLine);
            g.drawString(host.font(), host.font().plainSubstrByWidth(label, cw - 6),
                    px + (cw - host.font().width(label)) / 2, rowY + 1,
                    claimed ? host.T.textMuted : (reached ? host.T.textPrimary : host.T.textHint), GuiDraw.shadow());
            px += cw + 4;
        }

        renderSignCalendar(host, g, l, rowY + 17, r - l, b - btnH - 6, mx, my);
    }

    /**
     * 签到月历：年月导航 + 星期表头 + 日期格。
     * 已签到用主题的 textPrice 混出一层绿，今天用 accentStripe 描边，
     * 未来日期不铺底色（浅色主题上铺了会变成一片脏灰块）。
     */
    static void renderSignCalendar(ExtrasScreen host, GuiGraphics g, int x, int top, int w, int bottom, int mx, int my) {
        if (bottom - top < 44) return;                       // 空间太小就不画
        java.time.LocalDate today = java.time.LocalDate.now();
        if (host.shownMonth == null) host.shownMonth = java.time.YearMonth.from(today);

        Locale locale = net.minecraft.client.Minecraft.getInstance().getLanguageManager().getJavaLocale();
        int arrowW = 10, navH = 13, weekH = 9;
        int cellW = Math.max(14, Math.min(40, w / 7));
        int cellH = Math.max(12, Math.min(26, (bottom - top - navH - weekH) / 6));

        // ── 年月导航：年份在左，月份靠右 ──
        int nx = x;
        nx = drawArrow(host, g, nx, top, arrowW, navH, "«", mx, my,
                () -> host.shownMonth = host.shownMonth.minusYears(1));
        String year = String.valueOf(host.shownMonth.getYear());
        g.drawString(host.font(), year, nx + 1, top + 3, host.T.textPrimary, GuiDraw.shadow());
        nx += host.font().width(year) + 4;
        drawArrow(host, g, nx, top, arrowW, navH, "»", mx, my,
                () -> host.shownMonth = host.shownMonth.plusYears(1));

        String month = host.shownMonth.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, locale);
        int monthW = host.font().width(month);
        int tail = arrowW + 1 + monthW + 4 + arrowW + 1;
        int mArrow = Math.max(x + 60, x + w - tail);
        drawArrow(host, g, mArrow, top, arrowW, navH, "‹", mx, my,
                () -> host.shownMonth = host.shownMonth.minusMonths(1));
        g.drawString(host.font(), month, mArrow + arrowW + 2, top + 3, host.T.textPrimary, GuiDraw.shadow());
        drawArrow(host, g, mArrow + arrowW + 2 + monthW + 3, top, arrowW, navH, "›", mx, my,
                () -> host.shownMonth = host.shownMonth.plusMonths(1));

        // ── 星期表头（周一起始，与 Java 的 DayOfWeek 一致） ──
        int weekY = top + navH;
        for (int c = 0; c < 7; c++) {
            String dow = java.time.DayOfWeek.of(c + 1)
                    .getDisplayName(java.time.format.TextStyle.NARROW, locale);
            g.drawString(host.font(), dow, x + c * cellW + (cellW - host.font().width(dow)) / 2,
                    weekY + 1, host.T.textMuted, GuiDraw.shadow());
        }

        // ── 日期格 ──
        int cellY = weekY + weekH;
        int firstOffset = host.shownMonth.atDay(1).getDayOfWeek().getValue() - 1;
        int len = host.shownMonth.lengthOfMonth();
        long todayEpoch = today.toEpochDay();
        int signedColor = host.T.textPrice | 0xFF000000;
        int signedBg = ExtrasUi.mix(signedColor, host.T.mainPanelBg, 0.55f);

        for (int d = 1; d <= len; d++) {
            java.time.LocalDate date = host.shownMonth.atDay(d);
            long epoch = date.toEpochDay();
            int idx = firstOffset + d - 1;
            int cx = x + (idx % 7) * cellW + 1;
            int cy = cellY + (idx / 7) * cellH + 1;
            if (cy + cellH - 2 > bottom) break;
            int cw = cellW - 2, ch = cellH - 2;
            boolean isToday = epoch == todayEpoch;
            boolean signed = ExtrasClientState.signedDays.contains(epoch);
            boolean future = epoch > todayEpoch;
            boolean hover = mx >= cx && mx < cx + cw && my >= cy && my < cy + ch;

            int bg = signed ? signedBg : (future ? 0 : host.T.listRowBg);
            if (hover && !future && !signed) bg = host.T.listRowHover;
            if (bg != 0) GuiDraw.fillRounded(g, cx, cy, cw, ch, bg);
            if (signed) GuiDraw.borderRounded(g, cx, cy, cw, ch, signedColor);
            if (isToday) {
                int ring = host.T.accentStripe != 0 ? host.T.accentStripe : host.T.textPrice;
                GuiDraw.borderRounded(g, cx, cy, cw, ch, ring);
            }

            String day = String.valueOf(d);
            g.drawString(host.font(), day, cx + (cw - host.font().width(day)) / 2,
                    cy + (ch - host.font().lineHeight) / 2,
                    signed ? host.T.textPrice : (future ? host.T.textHint : host.T.textPrimary), GuiDraw.shadow());

            if (hover) {
                host.hoverTip = date + "  ·  "
                        + ExtrasUi.tr(signed ? "extras.sign.tip_signed" : (future ? "extras.sign.tip_future" : "extras.sign.tip_missed"));
                host.hoverTipX = cx + cw / 2;
                host.hoverTipY = cy;
            }
        }
    }

    /** 月历导航用的小箭头，返回下一个 x */
    static int drawArrow(ExtrasScreen host, GuiGraphics g, int x, int y, int w, int h, String glyph,
                         int mx, int my, Runnable action) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        if (hover) GuiDraw.fillRounded(g, x, y, w, h, host.T.btnBgHovered);
        g.drawString(host.font(), glyph, x + (w - host.font().width(glyph)) / 2, y + 3,
                hover ? host.T.textPrimary : host.T.textMuted, GuiDraw.shadow());
        host.addHit(x, y, w, h, action);
        return x + w + 1;
    }

    // ════════════════════════════════════════════
    //  邮箱
    // ════════════════════════════════════════════

    static void renderMail(ExtrasScreen host, GuiGraphics g, int l, int t, int r, int b, int mx, int my) {
        // 写信时只显示写信面板：否则「写邮件」按钮会把正在输入的内容重建掉
        if (host.composeOpen) {
            renderCompose(host, g, l, t + 18, r, b, mx, my);
            return;
        }

        int cbh = 14;
        int cly = t;

        // 「清空」贴右，「写邮件」在它左边
        String clr = ExtrasUi.tr("extras.mail.clear");
        int clw = host.font().width(clr) + 14;
        int clx = r - clw;
        host.button(g, clx, cly, clw, cbh, clr, false, mx, my, ExtrasActions::clearMails);

        String cbtn = ExtrasUi.tr("extras.compose.open");
        int cbw = host.font().width(cbtn) + 14;
        host.button(g, clx - 6 - cbw, cly, cbw, cbh, cbtn, true, mx, my, () -> openCompose(host));

        var mails = ExtrasClientState.mails;
        int listTop = t + 18;
        if (mails.isEmpty()) {
            GuiDraw.note(g, host.font(), ExtrasUi.tr("extras.mail.empty"), (l + r) / 2, listTop + 40, r - l);
            return;
        }

        GuiDraw.section(g, l - 2, listTop - 3, r - l + 4, Math.max(30, b - 22 - listTop + 3));
        int rowH = 22;
        int maxRows = Math.max(1, (b - listTop - 22) / rowH);
        host.scroll = Math.max(0, Math.min(host.scroll, Math.max(0, mails.size() - maxRows)));

        boolean anyClaimable = false;
        int y = listTop;
        for (int i = host.scroll; i < mails.size() && i < host.scroll + maxRows; i++) {
            var m = mails.get(i);
            boolean hover = mx >= l && mx < r - 56 && my >= y && my < y + rowH - 2;
            GuiDraw.listRow(g, l, y, r - l - 54, rowH - 2, false, hover);

            String title = m.title == null || m.title.isEmpty()
                    ? ExtrasUi.tr(ExtrasUi.defaultTitleKey(m.kind)) : m.title;
            g.drawString(host.font(), host.font().plainSubstrByWidth(title, r - l - 62),
                    l + 4, y + 3, m.claimed ? host.T.textMuted : host.T.textPrimary, GuiDraw.shadow());

            StringBuilder sub = new StringBuilder();
            if (m.money > 0) sub.append(ExtrasUi.tr("extras.mail.money", m.money)).append(' ');
            if (m.itemCount > 0) sub.append(ExtrasUi.tr("extras.mail.items", m.itemCount)).append(' ');
            String body = ExtrasClientState.mailBody(m);
            if (!body.isEmpty()) sub.append(body).append(' ');
            else if (m.sender != null && !m.sender.isEmpty()) sub.append(ExtrasUi.tr("extras.mail.from", m.sender));
            if (m.claimed) sub.append(ExtrasUi.tr("extras.mail.claimed_tag"));
            g.drawString(host.font(), host.font().plainSubstrByWidth(sub.toString(), r - l - 62),
                    l + 4, y + 12, host.T.textMuted, GuiDraw.shadow());

            int bw = 50, bh = rowH - 6;
            int bx = r - bw;
            boolean canClaim = !m.record && !m.claimed && (m.money > 0 || m.itemCount > 0);
            anyClaimable |= canClaim;
            // 纯文字通知没有可领取奖励，给一个「删除」按钮；否则它会一直占着信箱，按钮却写「已领取」
            boolean canDelete = !canClaim && !m.record && !m.claimed;
            String btnKey = m.record ? "extras.mail.record_tag"
                    : (canClaim ? "extras.mail.claim"
                    : (m.claimed ? "extras.mail.claimed" : "extras.mail.delete"));
            Runnable action = canClaim ? () -> ExtrasActions.claimMail(m.id)
                    : (canDelete ? () -> ExtrasActions.deleteMail(m.id) : null);
            host.button(g, bx, y + 2, bw, bh, ExtrasUi.tr(btnKey), canClaim, mx, my, action);
            GuiDraw.divider(g, l + 2, y + rowH - 2, r - l - 4);
            y += rowH;
        }

        GuiDraw.scrollbar(g, r + 4, listTop, Math.max(0, b - 22 - listTop),
                host.scroll, Math.max(0, mails.size() - maxRows));

        if (anyClaimable) {
            String all = ExtrasUi.tr("extras.mail.claim_all");
            int aw = host.font().width(all) + 12;
            host.button(g, r - aw, b - 18, aw, 18, all, true, mx, my, ExtrasActions::claimAllMails);
        }
    }

    /** 打开写邮件面板 */
    static void openCompose(ExtrasScreen host) {
        if (host.composeOpen) return;
        host.composeOpen = true;
        host.rebuildWidgets();
    }

    /** 写邮件面板的输入框（设计坐标）：全部走 ExtrasScreen.makeBoxAt，框底由 boxRects 记录 */
    static void buildComposeBoxes(ExtrasScreen host) {
        int l = ExtrasScreen.SIDEBAR_W + 12;
        int t = 26 + 18;               // 与 renderCompose 的 base 一致
        int labW = 40;
        host.cpTargetBox = host.makeBoxAt(l + labW + 4, t + 2, 100, 14, ExtrasUi.tr("extras.compose.target"), 16);
        host.cpTitleBox = host.makeBoxAt(l + labW + 4, t + 20, 150, 14, ExtrasUi.tr("extras.compose.title"), 40);
        host.cpContentBox = host.makeBoxAt(l + labW + 4, t + 38, 200, 14, ExtrasUi.tr("extras.compose.content"), 200);
        host.cpMoneyBox = host.makeBoxAt(l + labW + 4, t + 56, 56, 14, ExtrasUi.tr("extras.compose.money"), 16);
    }

    /** 玩家 → 玩家写邮件面板 */
    static void renderCompose(ExtrasScreen host, GuiGraphics g, int l, int t, int r, int b, int mx, int my) {
        int base = t;
        g.drawString(host.font(), ExtrasUi.tr("extras.compose.head"), l, base - 2, host.T.textTitle, GuiDraw.shadow());

        host.labelBox(g, ExtrasUi.tr("extras.compose.target"), l, base + 2, host.cpTargetBox);
        host.labelBox(g, ExtrasUi.tr("extras.compose.title"), l, base + 20, host.cpTitleBox);
        host.labelBox(g, ExtrasUi.tr("extras.compose.content"), l, base + 38, host.cpContentBox);
        host.labelBox(g, ExtrasUi.tr("extras.compose.money"), l, base + 56, host.cpMoneyBox);

        // 「附手持物」勾选框，跟在金币框右边
        host.chk(g, l + 44 + 60 + 6, base + 56, 8, host.composeAttach,
                ExtrasUi.tr("extras.compose.attach"), mx, my, () -> host.composeAttach = !host.composeAttach);

        // 发送 / 取消
        host.button(g, l, base + 78, 56, 16, ExtrasUi.tr("extras.compose.send"), true, mx, my, () -> submitCompose(host));
        host.button(g, l + 62, base + 78, 56, 16, ExtrasUi.tr("extras.compose.cancel"), false, mx, my, () -> closeCompose(host));

        g.drawString(host.font(), ExtrasUi.tr("extras.compose.hint"), l, base + 100, host.T.textHint, GuiDraw.shadow());
        // 下拉列表最后画：它要盖在金币/发送按钮之上，不能被它们压住
        renderComposePicker(host, g, l, base, mx, my);
    }

    /** 写邮件·收件人右侧的「选好友」下拉（往下展开） */
    static void renderComposePicker(ExtrasScreen host, GuiGraphics g, int l, int base, int mx, int my) {
        // 下拉列表会盖住标题/正文/金币输入框；把这三个 widget 隐掉，
        // 否则 EditBox 由 super.render 在最后绘制，文字会浮在下拉列表上面。
        if (host.cpTitleBox != null) host.cpTitleBox.setVisible(!host.composePickerOpen);
        if (host.cpContentBox != null) host.cpContentBox.setVisible(!host.composePickerOpen);
        if (host.cpMoneyBox != null) host.cpMoneyBox.setVisible(!host.composePickerOpen);

        int fbW = 20, fbH = 14;
        int fbX = l + 44 + 100 + 4, fbY = base + 2;
        boolean hover = mx >= fbX && mx < fbX + fbW && my >= fbY && my < fbY + fbH;
        GuiDraw.fillRounded(g, fbX, fbY, fbW, fbH,
                (hover || host.composePickerOpen) ? host.T.btnBgHovered : host.T.btnBgNormal);
        if (host.T.btnBorder != 0) GuiDraw.borderRounded(g, fbX, fbY, fbW, fbH, host.T.btnBorder);
        String arrow = host.composePickerOpen ? "\u25B4" : "\u25BE";
        g.drawString(host.font(), arrow, fbX + (fbW - host.font().width(arrow)) / 2,
                fbY + (fbH - host.font().lineHeight) / 2 + 1, host.T.textPrimary, GuiDraw.shadow());
        host.addHit(fbX, fbY, fbW, fbH, () -> {
            host.composePickerOpen = !host.composePickerOpen;
            host.composePickerScroll = 0;
            if (host.composePickerOpen) host.setFocused(null);
        });

        if (!host.composePickerOpen) return;

        // 点空白处收起（先加，后加的行优先命中）
        host.addHit(0, 0, host.DESIGN_W, host.DESIGN_H, () -> host.composePickerOpen = false);

        var friends = ExtrasClientState.friends;
        int pw = 110, rowH = 12, maxRows = 8;
        int px = fbX, py = fbY + fbH + 2;
        host.composePickerScroll = Math.max(0, Math.min(host.composePickerScroll, Math.max(0, friends.size() - maxRows)));
        int rows = Math.max(1, Math.min(maxRows, friends.size()));
        int ph = rows * rowH + 2;

        int bg = host.T.popupBg != 0 ? host.T.popupBg : host.T.mainPanelBg;
        GuiDraw.fillRounded(g, px, py, pw, ph, bg);
        int border = host.T.popupBorder != 0 ? host.T.popupBorder : host.T.separatorLine;
        if (border != 0) GuiDraw.borderRounded(g, px, py, pw, ph, border);

        if (friends.isEmpty()) {
            GuiDraw.note(g, host.font(), ExtrasUi.tr("extras.transfer.no_friends"), px + pw / 2, py + 3, pw - 8);
            return;
        }

        int yy = py + 1;
        for (int i = host.composePickerScroll; i < friends.size() && i < host.composePickerScroll + maxRows; i++) {
            var f = friends.get(i);
            boolean hov = mx >= px && mx < px + pw && my >= yy && my < yy + rowH;
            if (hov) GuiDraw.fillRounded(g, px + 1, yy, pw - 2, rowH, host.T.listRowHover);
            g.drawString(host.font(), f.name, px + 4, yy + 2,
                    f.online ? host.T.textPrimary : host.T.textMuted, GuiDraw.shadow());
            final String name = f.name;
            host.addHit(px, yy, pw, rowH, () -> {
                if (host.cpTargetBox != null) host.cpTargetBox.setValue(name);
                host.composePickerOpen = false;
            });
            yy += rowH;
        }
    }

    static void submitCompose(ExtrasScreen host) {
        String target = host.cpTargetBox == null ? "" : host.cpTargetBox.getValue().trim();
        String title = host.cpTitleBox == null ? "" : host.cpTitleBox.getValue().trim();
        String content = host.cpContentBox == null ? "" : host.cpContentBox.getValue();
        long money = ExtrasUi.parseLong(host.cpMoneyBox);
        if (target.isEmpty()) {
            ExtrasNoticeQueue.pushPlain(ExtrasUi.tr("extras.msg.mail_target_empty"), "error");
            return;
        }
        if (title.isEmpty() && content.isEmpty() && money <= 0 && !host.composeAttach) {
            ExtrasNoticeQueue.pushPlain(ExtrasUi.tr("extras.msg.mail_empty"), "error");
            return;
        }
        ExtrasActions.mailSend(target, title, content, money, host.composeAttach);
        // 不在这里关闭：等服务器确认成功后再关，失败时保留草稿
    }

    static void closeCompose(ExtrasScreen host) {
        host.composeOpen = false;
        host.composePickerOpen = false;
        host.rebuildWidgets();
    }

    // ════════════════════════════════════════════
    //  转换
    // ════════════════════════════════════════════

    static void renderExchange(ExtrasScreen host, GuiGraphics g, int l, int t, int r, int b, int mx, int my) {
        var list = ExtrasClientState.exchange;
        if (list.isEmpty()) {
            GuiDraw.note(g, host.font(), ExtrasUi.tr("extras.exchange.empty"), (l + r) / 2, t + 40, r - l);
            return;
        }

        GuiDraw.section(g, l - 2, t - 3, r - l + 4, b - t + 1);
        String shiftHint = ExtrasUi.tr("extras.exchange.shift_all");
        g.drawString(host.font(), host.font().plainSubstrByWidth(shiftHint, r - l - 92), l, t,
                host.T.textHint, GuiDraw.shadow());
        host.button(g, r - 86, t - 1, 86, 12, ExtrasUi.tr("extras.exchange.in_all"), true, mx, my,
                ExtrasActions::exchangeInAll);
        int listTop = t + 12;
        int rowH = 20;
        int maxRows = Math.max(1, (b - listTop) / rowH);
        host.scroll = Math.max(0, Math.min(host.scroll, Math.max(0, list.size() - maxRows)));

        int y = listTop;
        for (int i = host.scroll; i < list.size() && i < host.scroll + maxRows; i++) {
            var e = list.get(i);
            boolean hover = mx >= l && mx < r && my >= y && my < y + rowH - 2;
            GuiDraw.listRow(g, l, y, r - l, rowH - 2, false, hover);

            g.drawString(host.font(), host.font().plainSubstrByWidth(e.name, r - l - 96),
                    l + 4, y + 3, host.T.textPrimary, GuiDraw.shadow());
            g.drawString(host.font(), ExtrasUi.tr("extras.exchange.rate", e.value)
                            + " · " + ExtrasUi.tr("extras.exchange.owned", e.owned),
                    l + 4, y + 11, host.T.textMuted, GuiDraw.shadow());

            int bw = 40, bh = rowH - 6;
            int bx2 = r - bw;
            int bx1 = bx2 - bw - 4;
            final String id = e.itemId;
            boolean currency = ExtrasClientState.isCurrencyItem(id);
            boolean canOut = ExtrasClientState.exchangeOut && !currency;
            int inCount = net.minecraft.client.gui.screens.Screen.hasShiftDown()
                    ? Math.max(1, e.owned) : 1;
            int maxStack = Math.min(32, Math.max(1, ExtrasClientState.itemStack(id).getMaxStackSize()));
            int outCount = net.minecraft.client.gui.screens.Screen.hasShiftDown()
                    ? (int) Math.max(1, Math.min(maxStack,
                            ExtrasClientState.balance / Math.max(1L, e.value))) : 1;
            host.button(g, canOut ? bx1 : bx2, y + 2, bw, bh,
                    ExtrasUi.tr("extras.exchange.in"), e.owned > 0, mx, my,
                    e.owned > 0 ? () -> ExtrasActions.exchangeIn(id, inCount) : null);
            if (canOut) {
                host.button(g, bx2, y + 2, bw, bh, ExtrasUi.tr("extras.exchange.out"),
                        ExtrasClientState.balance >= e.value, mx, my,
                        ExtrasClientState.balance >= e.value ? () -> ExtrasActions.exchangeOut(id, outCount) : null);
            }
            GuiDraw.divider(g, l + 2, y + rowH - 2, r - l - 4);
            y += rowH;
        }
        GuiDraw.scrollbar(g, r + 4, listTop, b - listTop, host.scroll, Math.max(0, list.size() - maxRows));
    }

    // ════════════════════════════════════════════
    //  转账
    // ════════════════════════════════════════════

    static void renderTransfer(ExtrasScreen host, GuiGraphics g, int l, int t, int r, int b, int mx, int my) {
        GuiDraw.section(g, l - 4, t + ROW1 - 16, r - l + 8, 64);
        g.drawString(host.font(), ExtrasUi.tr("extras.transfer.title"), l, t, host.T.textTitle, GuiDraw.shadow());
        g.drawString(host.font(), ExtrasUi.tr("extras.transfer.hint"), l, t + 11, host.T.textMuted, GuiDraw.shadow());

        if (host.transferTargetBox != null) host.drawBoxAround(g, host.transferTargetBox);
        if (host.transferAmountBox != null) host.drawBoxAround(g, host.transferAmountBox);

        host.button(g, l, t + ROW1 + 26, 70, 18, ExtrasUi.tr("extras.transfer.confirm"), true, mx, my, () -> {
            String target = host.transferTargetBox == null ? "" : host.transferTargetBox.getValue().trim();
            int amount = ExtrasUi.parseInt(host.transferAmountBox);
            if (target.isEmpty()) {
                ExtrasNoticeQueue.pushPlain(ExtrasUi.tr("extras.msg.transfer_no_target"), "error");
            } else if (amount <= 0) {
                ExtrasNoticeQueue.pushPlain(ExtrasUi.tr("extras.msg.transfer_too_small",
                        com.sellbox.mod.extras.ExtrasConfig.TRANSFER_MIN), "error");
            } else {
                ExtrasActions.transfer(target, amount);
            }
        });

        g.drawString(host.font(), ExtrasUi.tr("extras.transfer.fee_note"), l, t + ROW1 + 52,
                host.T.textHint, GuiDraw.shadow());
        // 下拉列表最后画，避免被确认按钮/提示文字盖住
        renderFriendPicker(host, g, l, t, mx, my);
    }

    /** 转账页·收款人右侧的「选好友」下拉（往下展开，避开确认按钮） */
    static void renderFriendPicker(ExtrasScreen host, GuiGraphics g, int l, int t, int mx, int my) {
        int fbW = 18, fbH = 16;
        int fbX = l + 74, fbY = t + ROW1;

        boolean fhover = mx >= fbX && mx < fbX + fbW && my >= fbY && my < fbY + fbH;
        GuiDraw.fillRounded(g, fbX, fbY, fbW, fbH,
                (fhover || host.friendPickerOpen) ? host.T.btnBgHovered : host.T.btnBgNormal);
        if (host.T.btnBorder != 0) GuiDraw.borderRounded(g, fbX, fbY, fbW, fbH, host.T.btnBorder);
        String arrow = host.friendPickerOpen ? "\u25B4" : "\u25BE";
        g.drawString(host.font(), arrow, fbX + (fbW - host.font().width(arrow)) / 2,
                fbY + (fbH - host.font().lineHeight) / 2 + 1, host.T.textPrimary, GuiDraw.shadow());
        host.addHit(fbX, fbY, fbW, fbH, () -> {
            host.friendPickerOpen = !host.friendPickerOpen;
            host.friendPickerScroll = 0;
            if (host.friendPickerOpen) host.setFocused(null);
        });

        if (!host.friendPickerOpen) return;

        // 点空白处收起（先加，后加的好友行优先命中）
        host.addHit(0, 0, host.DESIGN_W, host.DESIGN_H, () -> host.friendPickerOpen = false);

        var friends = ExtrasClientState.friends;
        int pw = 110, rowH = 12, maxRows = 8;
        int px = l + 98, py = fbY + fbH + 3;
        host.friendPickerScroll = Math.max(0, Math.min(host.friendPickerScroll, Math.max(0, friends.size() - maxRows)));
        int rows = Math.max(1, Math.min(maxRows, friends.size()));
        int ph = rows * rowH + 2;

        int bg = host.T.popupBg != 0 ? host.T.popupBg : host.T.mainPanelBg;
        GuiDraw.fillRounded(g, px, py, pw, ph, bg);
        int border = host.T.popupBorder != 0 ? host.T.popupBorder : host.T.separatorLine;
        if (border != 0) GuiDraw.borderRounded(g, px, py, pw, ph, border);

        if (friends.isEmpty()) {
            GuiDraw.note(g, host.font(), ExtrasUi.tr("extras.transfer.no_friends"), px + pw / 2, py + 3, pw - 8);
            return;
        }

        int yy = py + 1;
        for (int i = host.friendPickerScroll; i < friends.size() && i < host.friendPickerScroll + maxRows; i++) {
            var f = friends.get(i);
            boolean hov = mx >= px && mx < px + pw && my >= yy && my < yy + rowH;
            if (hov) GuiDraw.fillRounded(g, px + 1, yy, pw - 2, rowH, host.T.listRowHover);
            g.drawString(host.font(), f.name, px + 4, yy + 2,
                    f.online ? host.T.textPrimary : host.T.textMuted, GuiDraw.shadow());
            final String name = f.name;
            host.addHit(px, yy, pw, rowH, () -> {
                if (host.transferTargetBox != null) host.transferTargetBox.setValue(name);
                host.friendPickerOpen = false;
            });
            yy += rowH;
        }
    }

    // ════════════════════════════════════════════
    //  兑换
    // ════════════════════════════════════════════

    static void renderRedeem(ExtrasScreen host, GuiGraphics g, int l, int t, int r, int b, int mx, int my) {
        GuiDraw.section(g, l - 4, t + ROW1 - 16, r - l + 8, 64);
        g.drawString(host.font(), ExtrasUi.tr("extras.redeem.title"), l, t, host.T.textTitle, GuiDraw.shadow());
        if (host.redeemBox != null) host.drawBoxAround(g, host.redeemBox);
        host.button(g, l, t + ROW1 + 26, 70, 18, ExtrasUi.tr("extras.redeem.button"), true, mx, my, () -> {
            if (host.redeemBox == null || host.redeemBox.getValue().trim().isEmpty()) {
                ExtrasNoticeQueue.pushPlain(ExtrasUi.tr("extras.msg.redeem_empty"), "error");
                return;
            }
            ExtrasActions.redeem(host.redeemBox.getValue().trim());
            // 不立刻清空：等服务器确认成功后再清，错误时保留兑换码方便修正
        });
    }

    // ════════════════════════════════════════════
    //  好友
    // ════════════════════════════════════════════

    static void renderFriend(ExtrasScreen host, GuiGraphics g, int l, int t, int r, int b, int mx, int my) {
        GuiDraw.section(g, l - 4, t + ROW1 - 16, r - l + 8, 32);
        if (host.friendBox != null) host.drawBoxAround(g, host.friendBox);
        host.button(g, l + 116, t + ROW1, 40, 16, ExtrasUi.tr("extras.friend.add"), true, mx, my, () -> {
            if (host.friendBox == null || host.friendBox.getValue().trim().isEmpty()) {
                ExtrasNoticeQueue.pushPlain(ExtrasUi.tr("extras.msg.friend_no_name"), "error");
                return;
            }
            ExtrasActions.friendAdd(host.friendBox.getValue().trim());
            // 成功后由 ExtrasScreen.tick 清空，失败时保留昵称方便重试
        });

        int viewTop = t + ROW1 + 26;
        int viewBottom = b;
        int rowH = 18, headerH = 12;
        int requests = ExtrasClientState.friendRequests.size();
        int friends = ExtrasClientState.friends.size();
        int totalH = headerH + requests * rowH + 6 + headerH + friends * rowH;
        int maxScroll = Math.max(0, totalH - Math.max(0, viewBottom - viewTop));
        host.scroll = Math.max(0, Math.min(host.scroll, maxScroll));

        GuiDraw.section(g, l - 2, viewTop - 3, r - l + 4, Math.max(30, viewBottom - viewTop + 3));
        int y = viewTop - host.scroll;
        if (y + headerH > viewTop && y < viewBottom) {
            g.drawString(host.font(), ExtrasUi.tr("extras.friend.requests", requests),
                    l, y, host.T.textTitle, GuiDraw.shadow());
        }
        y += headerH;

        for (var f : ExtrasClientState.friendRequests) {
            if (y >= viewBottom) break;
            if (y >= viewTop) {
                boolean rh = mx >= l && mx < r && my >= y && my < y + 16;
                GuiDraw.listRow(g, l, y, r - l, 16, false, rh);
                g.drawString(host.font(), f.name, l + 4, y + 4, host.T.textPrimary, GuiDraw.shadow());
                final String u = f.uuid;
                host.button(g, r - 36, y + 1, 36, 14, ExtrasUi.tr("extras.friend.accept"), true, mx, my,
                        () -> ExtrasActions.friendAccept(u));
                GuiDraw.divider(g, l + 2, y + rowH - 2, r - l - 4);
            }
            y += rowH;
        }

        y += 6;
        if (y + headerH > viewTop && y < viewBottom) {
            g.drawString(host.font(), ExtrasUi.tr("extras.friend.list", friends),
                    l, y, host.T.textTitle, GuiDraw.shadow());
        }
        y += headerH;

        for (var f : ExtrasClientState.friends) {
            if (y >= viewBottom) break;
            if (y >= viewTop) {
                boolean rh = mx >= l && mx < r && my >= y && my < y + 16;
                GuiDraw.listRow(g, l, y, r - l, 16, false, rh);

                // 在线状态：小方点 + 离线时名字压暗（不靠颜色字符，任何字体都能对齐）
                GuiDraw.dot(g, l + 4, y + 7, 3, f.online ? 0xFF55DD55 : 0xFF777777);
                g.drawString(host.font(), f.name, l + 10, y + 4,
                        f.online ? host.T.textPrimary : host.T.textMuted, GuiDraw.shadow());
                final String u = f.uuid;
                host.button(g, r - 36, y + 1, 36, 14, ExtrasUi.tr("extras.friend.remove"), false, mx, my,
                        () -> ExtrasActions.friendRemove(u));
                GuiDraw.divider(g, l + 2, y + rowH - 2, r - l - 4);
            }
            y += rowH;
        }

        // 列表比可视区高时给一个滚动提示，放在列表上方，避免滚到底时盖住最后一行
        if (maxScroll > 0) {
            String hint = ExtrasUi.tr("extras.friend.scroll_hint");
            g.drawString(host.font(), hint, r - host.font().width(hint), viewTop - 9,
                    host.T.textHint, GuiDraw.shadow());
        }
        GuiDraw.scrollbar(g, r + 4, viewTop, viewBottom - viewTop, host.scroll, maxScroll);
    }
}
