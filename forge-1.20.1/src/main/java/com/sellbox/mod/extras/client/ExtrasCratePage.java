package com.sellbox.mod.extras.client;

import com.sellbox.mod.gui.GuiDraw;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;


/**
 * 扩展面板的开箱页（转盘动画 / 连开汇总 / 保底进度）。
 * <p>
 * 从 ExtrasScreen 拆出来只为控制单个文件的体积；所有状态仍留在
 * ExtrasScreen，这里的方法都是 static，第一个参数是宿主界面。
 * <p>
 * <b>布局</b>：从上到下的行基线全部写死在方法开头的常量里，
 * 奖池列表拿到剩下的空间；所有 x 都相对内容区左边 {@code l}，y 相对内容区顶 {@code t}。
 */
final class ExtrasCratePage {
    private ExtrasCratePage() {}

    /** 箱子图标框边长 */
    private static final int ICON = 34;
    /** 奖池行高 */
    private static final int ROW_H = 16;

    static void renderCrate(ExtrasScreen host, GuiGraphics g, int l, int t, int r, int b, int mx, int my) {
        var list = ExtrasClientState.crates;
        if (list.isEmpty()) {
            GuiDraw.note(g, host.font(), ExtrasUi.tr("extras.crate.empty"), (l + r) / 2, t + 40, r - l);
            return;
        }
        host.crateTab = Math.max(0, Math.min(host.crateTab, list.size() - 1));
        host.crateTabStart = Math.max(0, Math.min(host.crateTabStart, list.size() - 1));
        var crate = list.get(host.crateTab);
        // 动画/汇总期间完全不画普通页面，避免文字批次/图层把奖池文本盖到弹窗上
        if (ExtrasClientState.lastCrateResult != null && host.crateAnimStart <= 0L) {
            host.crateAnimStart = System.currentTimeMillis();
        }
        boolean animating = host.crateAnimStart > 0L
                || ExtrasClientState.lastCrateBatch != null
                || ExtrasClientState.lastCrateResult != null;
        if (animating) {
            if (ExtrasClientState.lastCrateBatch != null) {
                renderCrateBatch(host, g, l, t, r, b, mx, my);
            } else {
                renderCrateAnim(host, g, l, t, r, b, mx, my);
            }
            return;
        }
        host.crateModal = false;
        host.crateRevealAt = 0L;

        // ── 箱体分页（多个箱子时才有） ──
        int tabH = 14;
        int y = t;
        if (list.size() > 1) {
            int start = Math.max(0, Math.min(host.crateTabStart, list.size() - 1));
            host.crateTabStart = start;
            int tx = l;
            int last = start - 1;
            for (int i = start; i < list.size(); i++) {
                String label = list.get(i).name;
                int w = Math.max(40, host.font().width(label) + 14);
                if (tx + w > r - 24) break;      // 右侧留给翻页箭头
                boolean sel = i == host.crateTab;
                boolean hov = !sel && mx >= tx && mx < tx + w && my >= y && my < y + tabH;
                GuiDraw.listRow(g, tx, y, w, tabH, sel, hov);
                g.drawString(host.font(), label, tx + (w - host.font().width(label)) / 2,
                        y + (tabH - host.font().lineHeight) / 2 + 1,
                        sel ? host.T.textPrimary : host.T.textMuted, GuiDraw.shadow());
                final int idx = i;
                host.addHit(tx, y, w, tabH, () -> {
                    host.crateTab = idx;
                    host.scroll = 0;
                });
                last = i;
                tx += w + 3;
            }
            if (start > 0) {
                ExtrasHomePages.drawArrow(host, g, r - 24, y, 10, tabH, "‹", mx, my,
                        () -> host.crateTabStart = Math.max(0, start - 1));
            }
            if (last < list.size() - 1) {
                ExtrasHomePages.drawArrow(host, g, r - 12, y, 10, tabH, "›", mx, my,
                        () -> host.crateTabStart = start + 1);
            }
            y += tabH + 6;
        }

        // ── 箱子主体：左侧图标 + 右侧信息 ──
        GuiDraw.iconFrameRounded(g, l, y, ICON, ICON);
        if (!crate.icon.isEmpty()) {
            com.sellbox.mod.util.ItemIcons.render(g, crate.icon, l + (ICON - 16) / 2, y + (ICON - 16) / 2);
        } else {
            GuiDraw.note(g, host.font(), ExtrasUi.tr("extras.crate.icon"), l + ICON / 2, y + ICON / 2 - 4, ICON - 4);
        }

        int infoX = l + ICON + 10;
        int infoW = r - infoX;
        // 保底进度贴右侧时，箱名要给它让出宽度，否则长箱名会和进度叠在一起
        String pityText = null;
        int pityW = 0;
        if (crate.pityEnabled()) {
            pityText = ExtrasUi.tr("extras.crate.pity", crate.pityNow, crate.pityCount);
            pityW = host.font().width(pityText);
        }
        g.drawString(host.font(), host.font().plainSubstrByWidth(crate.name,
                        Math.max(30, infoW - (pityText == null ? 0 : pityW + 8))),
                infoX, y + 1, host.T.textTitle, GuiDraw.shadow());
        g.drawString(host.font(), ExtrasUi.tr("extras.crate.price", crate.price),
                infoX, y + 12, host.T.textPrice, GuiDraw.shadow());
        g.drawString(host.font(), ExtrasUi.tr("extras.crate.pool", crate.items.size()),
                infoX, y + 23, host.T.textMuted, GuiDraw.shadow());

        // ── 保底进度（箱子配了保底才显示，贴右侧） ──
        if (pityText != null) {
            g.drawString(host.font(), pityText, r - pityW, y + 2,
                    host.T.textHint, GuiDraw.shadow());
        }

        // ── 开箱按钮（贴右下角；×N 在最右） ──
        int bh = 18;
        int by = b - bh;
        int batch = Math.min(com.sellbox.mod.extras.ExtrasConfig.CRATE_MAX_BATCH,
                Math.max(1, (int) (ExtrasClientState.balance / Math.max(1L, crate.price))));
        int bw10 = 34;
        int bx10 = r - bw10;
        boolean afford10 = batch > 1 && !animating;
        GuiDraw.fillRounded(g, bx10, by, bw10, bh, afford10 && mx >= bx10 && mx < bx10 + bw10 && my >= by && my < by + bh
                ? host.T.btnBgHovered : host.T.btnBgNormal);
        if (host.T.btnBorder != 0) GuiDraw.borderRounded(g, bx10, by, bw10, bh, host.T.btnBorder);
        String l10 = ExtrasUi.tr("extras.crate.x10", Math.max(2, batch));
        g.drawString(host.font(), l10, bx10 + (bw10 - host.font().width(l10)) / 2,
                by + (bh - host.font().lineHeight) / 2 + 1,
                afford10 ? host.T.textPrimary : host.T.textMuted, GuiDraw.shadow());
        if (afford10) {
            final String id = crate.id;
            final int n = Math.max(2, batch);
            host.addHit(bx10, by, bw10, bh, () -> openCrate(host, id, n));
        }

        int bw = 64;
        int bx = bx10 - 6 - bw;
        boolean afford = ExtrasClientState.balance >= crate.price;
        String openLabel = animating ? ExtrasUi.tr("extras.crate.opening") : ExtrasUi.tr("extras.crate.open");
        boolean hover = !animating && mx >= bx && mx < bx + bw && my >= by && my < by + bh;
        GuiDraw.fillRounded(g, bx, by, bw, bh, hover && afford ? host.T.btnBgHovered : host.T.btnBgNormal);
        if (host.T.btnBorder != 0) GuiDraw.borderRounded(g, bx, by, bw, bh, host.T.btnBorder);
        g.drawString(host.font(), openLabel, bx + (bw - host.font().width(openLabel)) / 2,
                by + (bh - host.font().lineHeight) / 2 + 1,
                animating ? host.T.textMuted : (afford ? host.T.textPrimary : host.T.textError), GuiDraw.shadow());
        if (hover && !afford) {
            host.hoverTip = ExtrasUi.tr("extras.msg.balance_low", ExtrasClientState.balance);
            host.hoverTipX = bx + bw / 2;
            host.hoverTipY = by;
        }
        if (!animating && afford) {
            final String id = crate.id;
            host.addHit(bx, by, bw, bh, () -> openCrate(host, id, 1));
        }

        // ── 奖池明细 ──
        int listTop = y + ICON + 8;
        g.drawString(host.font(), ExtrasUi.tr("extras.crate.items"), l, listTop, host.T.textTitle, GuiDraw.shadow());
        listTop += 12;

        double total = 0;
        for (var it : crate.items) total += Math.max(0, it.chance);
        int maxRows = Math.max(1, (by - 6 - listTop) / ROW_H);
        host.scroll = Math.max(0, Math.min(host.scroll, Math.max(0, crate.items.size() - maxRows)));
        GuiDraw.section(g, l - 2, listTop - 3, r - l + 4, Math.max(24, by - 6 - listTop + 3));
        int ry = listTop;
        for (int i = host.scroll; i < crate.items.size() && i < host.scroll + maxRows; i++) {
            var it = crate.items.get(i);
            boolean hov = mx >= l && mx < r && my >= ry && my < ry + ROW_H - 2;
            GuiDraw.listRow(g, l, ry, r - l, ROW_H - 2, false, hov);

            int rc = ExtrasUi.rarityColor(it.rarity);
            GuiDraw.dot(g, l + 4, ry + 6, 3, rc);
            ItemStack iconStack = ExtrasClientState.itemStack(it.id);
            if (!iconStack.isEmpty()) {
                com.sellbox.mod.util.ItemIcons.render(g, iconStack, l + 10, ry);
                if (hov) {
                    // 交给 ExtrasScreen 在 pose 弹栈后用屏幕坐标画，避免 tooltip 跟着设计坐标缩放
                    host.hoverItemStack = iconStack;
                    host.hoverItemX = host.screenMouseX;
                    host.hoverItemY = host.screenMouseY;
                }
            }
            String nm = it.count > 1 ? it.name + " x" + it.count : it.name;
            g.drawString(host.font(), host.font().plainSubstrByWidth(nm, r - l - 64), l + 28,
                    ry + (ROW_H - 2 - host.font().lineHeight) / 2 + 1, host.T.textPrimary, GuiDraw.shadow());
            String pct = ExtrasUi.pct(it.chance, total);
            g.drawString(host.font(), pct, r - 4 - host.font().width(pct),
                    ry + (ROW_H - 2 - host.font().lineHeight) / 2 + 1, rc, GuiDraw.shadow());
            GuiDraw.divider(g, l + 2, ry + ROW_H - 2, r - l - 4);
            ry += ROW_H;
        }
        GuiDraw.scrollbar(g, r + 4, listTop, Math.max(0, by - 6 - listTop),
                host.scroll, Math.max(0, crate.items.size() - maxRows));

    }

    /** 连开汇总面板：列出这一批开出的东西 */
    static void renderCrateBatch(ExtrasScreen host, GuiGraphics g, int l, int t, int r, int b, int mx, int my) {
        var res = ExtrasClientState.lastCrateBatch;
        if (res == null) return;
        host.crateModal = true;
        host.hoverItemStack = net.minecraft.world.item.ItemStack.EMPTY;
        host.hoverTip = null;

        long now = System.currentTimeMillis();
        long started = res.startedAt > 0 ? res.startedAt : now;
        int n = Math.max(1, res.wons.size());
        long rollMs = 1000L;
        long animEnd = started + n * rollMs;
        if (host.crateBatchSkipped && host.crateBatchRevealAt <= 0L) {
            host.crateBatchRevealAt = now;
        } else if (host.crateBatchRevealAt <= 0L && now >= animEnd) {
            host.crateBatchRevealAt = now;
        }
        boolean showResult = host.crateBatchRevealAt > 0L;
        if (showResult && now - host.crateBatchRevealAt >= 10_000L) {
            host.crateBatchSkipped = false;
            host.crateBatchRevealAt = 0L;
            host.crateModal = false;
            ExtrasClientState.lastCrateBatch = null;
            return;
        }

        g.flush();
        g.fill(l, t, r, b, 0xFF080A12);
        GuiDraw.fillRounded(g, l, t, r - l, b - t, 0xFF0B0D16);
        int border = host.T.popupBorder != 0 ? host.T.popupBorder : GuiDraw.frameColor();
        if (border != 0) GuiDraw.borderRounded(g, l, t, r - l, b - t, border);

        String head = ExtrasUi.tr("extras.crate.batch_title", res.count);
        g.drawString(host.font(), head, l + 10, t + 8, host.T.textTitle, GuiDraw.shadow());
        if (showResult) {
            int remain = Math.max(0, 10 - (int) ((now - host.crateBatchRevealAt) / 1000));
            String auto = ExtrasUi.tr("extras.crate.auto_close", remain);
            g.drawString(host.font(), auto, r - 10 - host.font().width(auto), t + 8,
                    host.T.textHint, GuiDraw.shadow());
        } else {
            host.button(g, r - 68, t + 6, 58, 14, ExtrasUi.tr("extras.crate.skip"), false, mx, my,
                    () -> host.crateBatchSkipped = true);
        }

        host.addHit(l, t, r - l, b - t, () -> {});

        if (showResult) {
            drawBatchGrid(host, g, l, t + 30, r, b, res.wons, res.wons.size());
        } else {
            long elapsed = Math.max(0L, now - started);
            int completed = (int) Math.min(n, elapsed / rollMs);
            int current = completed;
            if (current < n) {
                ExtrasClientState.CrateResult rr = batchReel(res, current);
                float p = Math.min(1f, (elapsed - current * rollMs) / (float) rollMs);
                int rc = ExtrasUi.rarityColor(res.wons.get(current).rarity);
                drawReel(host, g, rr, l, t, r, b, t + 30, p, rc);
            }
            drawBatchGrid(host, g, l, t + 86, r, b, res.wons, completed);
        }

        if (showResult) {
            host.button(g, (l + r) / 2 - 42, b - 24, 84, 16, ExtrasUi.tr("extras.crate.ok"), true, mx, my,
                    () -> {
                        host.crateBatchSkipped = false;
                        host.crateBatchRevealAt = 0L;
                        host.crateModal = false;
                        ExtrasClientState.lastCrateBatch = null;
                    });
        }
    }

    /** 十连的假转盘：中间停在该次奖励上，周围从本批奖励里循环取样 */
    private static ExtrasClientState.CrateResult batchReel(ExtrasClientState.CrateBatchResult batch, int index) {
        ExtrasClientState.CrateResult rr = new ExtrasClientState.CrateResult();
        int n = batch.wons.size();
        int stop = 18;
        for (int j = 0; j < 24; j++) {
            ExtrasClientState.ReelView src = batch.wons.get(Math.floorMod(index * 3 + j * 7, n));
            ExtrasClientState.ReelView v = new ExtrasClientState.ReelView();
            v.name = src.name;
            v.id = src.id;
            v.count = src.count;
            v.rarity = src.rarity;
            rr.reel.add(v);
        }
        ExtrasClientState.ReelView won = batch.wons.get(index);
        ExtrasClientState.ReelView center = rr.reel.get(stop);
        center.name = won.name;
        center.id = won.id;
        center.count = won.count;
        center.rarity = won.rarity;
        rr.stopAt = stop;
        return rr;
    }

    /** 十连结果：2 列 x 5 行，按开奖顺序排列 */
    private static void drawBatchGrid(ExtrasScreen host, GuiGraphics g, int l, int top, int r, int b,
                                      java.util.List<ExtrasClientState.ReelView> wons, int limit) {
        int cols = 2;
        int colW = (r - l - 14) / cols;
        int rowH = 20;
        for (int i = 0; i < wons.size() && i < limit; i++) {
            int col = i % cols;
            int row = i / cols;
            int x = l + 6 + col * colW;
            int y = top + row * rowH;
            if (y + rowH > b - 28) break;
            var w = wons.get(i);
            int rc = ExtrasUi.rarityColor(w.rarity);
            GuiDraw.listRow(g, x, y, colW - 4, rowH - 2, false, false);
            ItemStack icon = ExtrasClientState.itemStack(w.id);
            if (!icon.isEmpty()) com.sellbox.mod.util.ItemIcons.render(g, icon, x + 3, y + 2);
            String nm = w.count > 1 ? w.name + " x" + w.count : w.name;
            g.drawString(host.font(), host.font().plainSubstrByWidth(nm, colW - 28),
                    x + 22, y + 5, rc, GuiDraw.shadow());
        }
    }

    static void openCrate(ExtrasScreen host, String crateId, int count) {
        var list = ExtrasClientState.crates;
        if (host.crateTab >= list.size()) return;
        // 开始新一局前清掉上一次的结果，避免旧结果和新动画互相覆盖
        ExtrasClientState.lastCrateResult = null;
        ExtrasClientState.lastCrateBatch = null;
        host.crateModal = false;
        host.crateRevealAt = 0L;
        host.crateBatchSkipped = false;
        host.crateBatchRevealAt = 0L;
        host.crateAnimStart = System.currentTimeMillis();
        ExtrasActions.crateOpen(crateId, count);
    }

    private static final net.minecraft.resources.ResourceLocation FX_FLASH =
            net.minecraft.resources.ResourceLocation.tryParse("minecraft:textures/particle/flash.png");
    private static final net.minecraft.resources.ResourceLocation FX_GLOW =
            net.minecraft.resources.ResourceLocation.tryParse("minecraft:textures/particle/glow.png");
    private static final net.minecraft.resources.ResourceLocation FX_SPARK =
            net.minecraft.resources.ResourceLocation.tryParse("minecraft:textures/particle/spark_0.png");
    private static final net.minecraft.resources.ResourceLocation FX_GLITTER =
            net.minecraft.resources.ResourceLocation.tryParse("minecraft:textures/particle/glitter_0.png");

    /**
     * 开箱动画：暗场 + 旋转光幕 + 横向转盘 + 中奖卡片。
     * 进度只由本地时间驱动；结果出来后会一直保留，直到玩家点「确认」。
     */
    static void renderCrateAnim(ExtrasScreen host, GuiGraphics g, int l, int t, int r, int b, int mx, int my) {
        var res = ExtrasClientState.lastCrateResult;
        host.crateModal = true;
        host.hoverItemStack = net.minecraft.world.item.ItemStack.EMPTY;
        host.hoverTip = null;
        // 等响应/播动画期间锁住整个面板，避免切箱子导致结果和界面错位
        host.addHit(0, 0, ExtrasScreen.DESIGN_W, ExtrasScreen.DESIGN_H, () -> {});
        // 但右上角关闭按钮要始终可用
        host.addHit(ExtrasScreen.DESIGN_W - 18, 4, 12, 12, host::onClose);

        long now = System.currentTimeMillis();
        long started = Math.max(host.crateAnimStart, res != null ? res.startedAt : host.crateAnimStart);
        long elapsed = Math.max(0L, now - started);

        // 服务器一直没回包才放弃等待；正常结果不再自动关闭
        if (res == null || res.reel.isEmpty()) {
            drawLoading(host, g, l, t, r, b, elapsed);
            if (elapsed > 6000L) {
                host.crateAnimStart = 0;
                host.crateRevealAt = 0L;
                host.crateModal = false;
                ExtrasClientState.lastCrateResult = null;
            }
            return;
        }

        long dur = com.sellbox.mod.extras.ExtrasConfig.CRATE_ANIM_MS;
        float prog = Math.min(1f, elapsed / (float) dur);
        // 结果卡片出现后固定显示 10 秒；期间仍然可以手动确认
        if (prog >= 1f) {
            if (host.crateRevealAt <= 0L) host.crateRevealAt = now;
            if (now - host.crateRevealAt >= 10_000L) {
                host.crateAnimStart = 0L;
                host.crateRevealAt = 0L;
                host.crateModal = false;
                ExtrasClientState.lastCrateResult = null;
                return;
            }
        } else {
            host.crateRevealAt = 0L;
        }
        int centerX = (l + r) / 2;
        int centerY = (t + b) / 2;
        int rc = ExtrasUi.rarityColor(res.wonRarity);
        float[] rgb = rgb(rc);

        // 先把之前页面文字批次刷掉，再覆盖；否则 guiText 批次会在遮罩后统一绘制，造成字透出来
        g.flush();
        // 颜色遮罩只覆盖右侧内容区，侧栏/标题保持可见
        g.fill(l, t, r, b, 0xFF080A12);
        GuiDraw.borderRounded(g, l, t, r - l, b - t,
                host.T.popupBorder != 0 ? host.T.popupBorder : GuiDraw.frameColor());

        // 光效全部用原版粒子贴图，并按内容区裁剪，避免糊到侧栏
        float pulse = 0.65f + 0.35f * (float) Math.sin(elapsed / 260.0);
        g.enableScissor(host.toScreenX(l), host.toScreenY(t), host.toScreenX(r), host.toScreenY(b));
        drawTintedBlit(g, FX_FLASH, centerX - 150, centerY - 150, 300, 300, 32, 32, rgb, 0.055f * pulse);
        drawTintedBlit(g, FX_GLOW, centerX - 60, centerY - 60, 120, 120, 8, 8, rgb, 0.16f * pulse);
        drawSparkles(g, centerX, centerY, elapsed, rgb, 26, 1.0f);

        // 开奖前显示标题；中奖后只保留卡片里的标题，避免上下两个“恭喜获得”
        if (prog < 1f) {
            String head = ExtrasUi.tr("extras.crate.opening_title");
            g.drawString(host.font(), head, centerX - host.font().width(head) / 2, t + 10,
                    host.T.textTitle, GuiDraw.shadow());
        }

        // ── 转盘（出结果后直接收起，避免和卡片叠字） ──
        if (prog < 1f) {
            drawReel(host, g, res, l, t, r, b, (t + b) / 2 - 29, prog, rc);
        }

        // ── 中奖卡片 ──
        if (prog >= 1f) {
            drawRewardCard(host, g, l, t, r, b, mx, my, res, rc, rgb, elapsed, dur);
        } else {
            int barW = r - l - 40;
            int bx = centerX - barW / 2;
            int by = b - 22;
            GuiDraw.fillRounded(g, bx, by, barW, 3, 0x50101018);
            GuiDraw.fillRounded(g, bx, by, (int) (barW * prog), 3, rc);
        }
        g.disableScissor();
    }

    private static void drawLoading(ExtrasScreen host, GuiGraphics g, int l, int t, int r, int b, long elapsed) {
        int cx = (l + r) / 2, cy = (t + b) / 2;
        g.flush();
        g.fill(l, t, r, b, 0xFF080A12);
        float pulse = 0.5f + 0.5f * (float) Math.sin(elapsed / 180.0);
        drawTintedBlit(g, FX_FLASH, cx - 90, cy - 90, 180, 180, 32, 32,
                new float[]{0.55f, 0.85f, 1f}, 0.06f + 0.04f * pulse);
        drawSparkles(g, cx, cy, elapsed, new float[]{0.55f, 0.85f, 1f}, 16, 0.8f);
        String s = ExtrasUi.tr("extras.crate.loading");
        g.drawString(host.font(), s, cx - host.font().width(s) / 2, cy - 4, host.T.textTitle, GuiDraw.shadow());
        String dots = ".".repeat((int) ((elapsed / 350) % 4));
        g.drawString(host.font(), dots, cx - host.font().width(dots) / 2, cy + 9,
                host.T.textHint, GuiDraw.shadow());
    }

    private static void drawReel(ExtrasScreen host, GuiGraphics g, ExtrasClientState.CrateResult res,
                                 int l, int t, int r, int b, int reelTop, float prog, int rarityColor) {
        int cellW = 42, cw = 40, ch = 42;
        int winLeft = l + 12, winRight = r - 12;
        int centerX = (winLeft + winRight) / 2;

        float ease = prog < 0.16f
                ? prog / 0.16f * 0.25f
                : 0.25f + (float) (1 - Math.pow(1 - (prog - 0.16f) / 0.84f, 3)) * 0.75f;
        double pos = ease * res.stopAt;
        int baseIdx = (int) Math.floor(pos);
        float frac = (float) (pos - baseIdx);

        GuiDraw.fillRounded(g, winLeft - 3, reelTop - 4, winRight - winLeft + 6, ch + 8, 0xB00B0D16);
        GuiDraw.borderRounded(g, winLeft - 3, reelTop - 4, winRight - winLeft + 6, ch + 8,
                host.T.separatorLine != 0 ? host.T.separatorLine : 0x66FFFFFF);

        int cellBg = host.T.cellBg != 0 ? host.T.cellBg : 0xB0182030;
        for (int k = -5; k <= 5; k++) {
            int idx = baseIdx + k;
            if (idx < 0 || idx >= res.reel.size()) continue;
            var rv = res.reel.get(idx);
            int x = (int) (centerX + (k - frac) * cellW - cw / 2);
            if (x + cw < winLeft || x > winRight) continue;
            int rc = ExtrasUi.rarityColor(rv.rarity);
            int y = reelTop;
            GuiDraw.fillRounded(g, x, y, cw, ch, cellBg);
            GuiDraw.borderRounded(g, x, y, cw, ch, rc);
            ItemStack icon = ExtrasClientState.itemStack(rv.id);
            if (!icon.isEmpty()) {
                com.sellbox.mod.util.ItemIcons.render(g, icon, x + (cw - 16) / 2, y + 4);
            }
            String nm = host.font().plainSubstrByWidth(rv.name, cw - 4);
            g.drawString(host.font(), nm, x + (cw - host.font().width(nm)) / 2, y + 24,
                    rc, GuiDraw.shadow());
        }

        g.fill(centerX - cw / 2 - 2, reelTop - 4, centerX + cw / 2 + 2, reelTop + ch + 4, 0x22FFFFFF);
        GuiDraw.rect(g, centerX - 1, reelTop - 8, 2, ch + 16,
                host.T.accentStripe != 0 ? host.T.accentStripe : rarityColor);

        g.fill(winLeft, reelTop - 4, winLeft + 22, reelTop + ch + 4, 0x66080A12);
        g.fill(winRight - 22, reelTop - 4, winRight, reelTop + ch + 4, 0x66080A12);
    }

    private static void drawRewardCard(ExtrasScreen host, GuiGraphics g, int l, int t, int r, int b,
                                       int mx, int my, ExtrasClientState.CrateResult res,
                                       int rc, float[] rgb, long elapsed, long dur) {
        int cardW = Math.min(230, r - l - 30);
        int cardH = 108;
        int cx = (l + r) / 2;
        int cy = (t + b) / 2;
        int cardX = cx - cardW / 2;
        int cardY = cy - cardH / 2;

        float pulse = 0.65f + 0.35f * (float) Math.sin(elapsed / 220.0);
        drawTintedBlit(g, FX_FLASH, cardX - 30, cardY - 40, cardW + 60, cardH + 80, 32, 32,
                rgb, 0.05f * pulse);
        drawTintedBlit(g, FX_GLOW, cardX + cardW / 2 - 35, cardY + cardH / 2 - 35, 70, 70, 8, 8,
                rgb, 0.12f * pulse);

        drawSparkles(g, cx, cardY + cardH / 2, elapsed, rgb, 20, 0.7f);

        GuiDraw.fillRounded(g, cardX, cardY, cardW, cardH, 0xFF0B0D16);
        int cardBorder = ExtrasUi.mix(rc, 0xFF101827, 0.35f);
        GuiDraw.borderRounded(g, cardX, cardY, cardW, cardH, cardBorder);
        GuiDraw.rect(g, cardX + 2, cardY + 1, cardW - 4, 2, ExtrasUi.mix(rc, 0xFF101827, 0.15f));

        String title = ExtrasUi.tr("extras.crate.reveal_title");
        g.drawString(host.font(), title, cardX + (cardW - host.font().width(title)) / 2, cardY + 8,
                rc, GuiDraw.shadow());

        ItemStack icon = ExtrasClientState.itemStack(res.wonId);
        String name = res.wonName;
        if (res.wonCount > 1) name += " x" + res.wonCount;
        String rarity = ExtrasUi.rarityName(res.wonRarity);
        int nameW = Math.min(host.font().width(name), cardW - 80);
        int textW = Math.max(nameW, host.font().width(rarity));
        int blockW = 16 + 6 + textW;
        int blockX = cardX + (cardW - blockW) / 2;
        if (!icon.isEmpty()) {
            com.sellbox.mod.util.ItemIcons.render(g, icon, blockX, cardY + 26);
        }
        g.drawString(host.font(), host.font().plainSubstrByWidth(name, cardW - 80),
                blockX + 22, cardY + 28, host.T.textTitle, GuiDraw.shadow());
        g.drawString(host.font(), rarity,
                blockX + 22, cardY + 42, rc, GuiDraw.shadow());

        int remain = Math.max(0, 10 - (int) ((System.currentTimeMillis() - host.crateRevealAt) / 1000));
        String auto = ExtrasUi.tr("extras.crate.auto_close", remain);
        g.drawString(host.font(), auto, cardX + (cardW - host.font().width(auto)) / 2,
                cardY + 64, host.T.textHint, GuiDraw.shadow());

        host.button(g, cx - 42, cardY + cardH - 24, 84, 16,
                ExtrasUi.tr("extras.crate.ok"), true, mx, my, () -> {
                    host.crateAnimStart = 0;
                    ExtrasClientState.lastCrateResult = null;
                });
    }

    private static float[] rgb(int color) {
        return new float[]{((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f};
    }

    private static int hash(int x) {
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return x;
    }

    /** 使用原版 spark/glitter 贴图做围绕中心的闪烁粒子 */
    private static void drawSparkles(GuiGraphics g, int cx, int cy, long elapsed, float[] rgb,
                                     int count, float scale) {
        for (int i = 0; i < count; i++) {
            int h = hash(i * 7349 + (int) (elapsed / 55));
            float ang = (h & 2047) / 2047f * 6.283f;
            float dist = (30.0f + ((h >>> 11) & 127)) * scale;
            int px = cx + (int) (Math.cos(ang) * dist);
            int py = cy + (int) (Math.sin(ang) * dist * 0.62f);
            float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(elapsed / 190.0 + i));
            int size = 5 + ((h >>> 18) & 7);
            var tex = (i & 1) == 0 ? FX_SPARK : FX_GLITTER;
            drawTintedBlit(g, tex, px, py, size, size, 8, 8, rgb, 0.18f + 0.45f * tw);
        }
    }

    private static void drawTintedBlit(GuiGraphics g, net.minecraft.resources.ResourceLocation tex,
                                       int x, int y, int w, int h, int texW, int texH,
                                       float[] rgb, float alpha) {
        if (tex == null) return;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(rgb[0], rgb[1], rgb[2], alpha);
        g.blit(tex, x, y, 0, 0, w, h, texW, texH);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }
}
