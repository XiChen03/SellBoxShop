package com.sellbox.mod.shop;

import com.sellbox.mod.gui.GuiDraw;
import com.sellbox.mod.integration.JEIIntegration;
import com.sellbox.mod.integration.jei.JeiBridge;
import com.sellbox.mod.network.ModNetwork;
import com.sellbox.mod.util.PinyinUtil;
import com.sellbox.mod.util.FontUtil;
import com.sellbox.mod.util.ItemIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ShopScreen extends Screen {
    /** 主题对象身份恒定，切换主题时就地刷新，界面无需重开 */
    private final com.sellbox.mod.gui.GuiTheme T = com.sellbox.mod.gui.GuiTheme.get();
    private int imageWidth = 420;
    private int imageHeight = 260;
    private int leftPanelW = 100;
    private int categoryHeight = 14;

    ShopMenu menu;
    Inventory playerInventory;

    List<String> categories;
    String currentCategory;
    private List<ShopEntry> allEntries;
    private List<ShopEntry> filteredEntries;
    private Map<String, List<ShopEntry>> categoryCache = new HashMap<>();
    private int balance;

    private String searchText = "";
    private boolean searchFocused;
    private int scrollOffset = 0;

    // ── 编辑模式：Del 删除 + Ctrl+Z 撤销 ──
    /** 鼠标当前悬停的条目（用于 Del） */
    private ShopEntry hoveredEntry = null;
    /** 悬停条目所在分类（跨分类搜索时可能 != currentCategory） */
    private String hoveredEntryCategory = null;
    /** 最近一次删除的可撤销记录 */
    private static final class DeletedItem {
        final String category;
        final int index;
        final ShopEntry entry;
        DeletedItem(String category, int index, ShopEntry entry) {
            this.category = category; this.index = index; this.entry = entry;
        }
    }
    private final java.util.Deque<DeletedItem> undoStack = new java.util.ArrayDeque<>();
    private static final int UNDO_LIMIT = 20;

    private int categoryScrollOffset = 0;
    private boolean isDraggingCatScrollbar = false;
    private int catDragStartMouseY, catDragStartOffset;
    private static final int GRID_COLS = 8;
    private static final int GRID_ROWS = 4;
    /** 左侧栏顶部标题条高度（现代主题用它承载 CATEGORIES 标题） */
    private static final int HEADER_BAND_HEIGHT = 34;
    private int gridSpacingX = 44;
    private int gridSpacingY = 48;
    private int topOffset = 0;

    private boolean contextMenuOpen = false;
    private ShopEntry contextMenuEntry = null;
    private int contextMenuIndex = -1;
    private String contextMenuCategory = null;
    private int contextMenuX = 0;
    private int contextMenuY = 0;
    /** 条目 key -> 已交易次数，由服务端按接收者下发；用于限量转红与禁用点击 */
    private java.util.Map<String, Integer> limitUsed = java.util.Map.of();
    /** 条目 -> 它实际所属分类；跨分类搜索时列表里混着别的分类的条目 */
    private final java.util.Map<ShopEntry, String> entryCategory = new java.util.IdentityHashMap<>();

    public ShopScreen(ShopMenu menu, Inventory playerInv, Component title) {
        super(title);
        this.menu = menu;
        this.playerInventory = playerInv;
        this.categories = new ArrayList<>();
    }

    @Override
    protected void init() {
        super.init();
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        if (this.font == null) this.font = Minecraft.getInstance().font;

        // 基础布局尺寸（8列 x 4行），基准值取当前主题，主题换了面板随之变化
        int cellW = Math.max(24, T.baseGridSpacingX);
        int cellH = Math.max(28, T.baseGridSpacingY);
        int leftW = Math.max(60, T.baseLeftPanelWidth);
        int catH = Math.max(8, T.baseCategoryHeight);

        int baseGridW = GRID_COLS * (cellW + 2);
        int baseGridH = GRID_ROWS * cellH;
        int baseWidth = leftW + 14 + baseGridW + 12;
        int baseHeight = 38 + baseGridH + 14 + 24;

        // 自适应缩放：优先按屏幕高度缩放，若宽度不足则进一步缩小
        double scale = Math.min((double) height / (baseHeight + 48), (double) width / (baseWidth + 16));
        scale = Math.max(0.55, Math.min(2.5, scale));

        leftPanelW = Math.max(60, (int) Math.round(leftW * scale));
        categoryHeight = Math.max(8, (int) Math.round(catH * scale));
        gridSpacingX = Math.max(20, (int) Math.round(cellW * scale)) + 2;
        gridSpacingY = Math.max(24, (int) Math.round(cellH * scale)) + 11;
        imageWidth = Math.max(200, leftPanelW + 14 + GRID_COLS * gridSpacingX + 12);
        imageHeight = Math.max(160, 38 + GRID_ROWS * gridSpacingY + 14);

        int centeredTop = (height - imageHeight) / 2;
        topOffset = Math.min(centeredTop, Math.max(5, height - imageHeight - 48));

        if (!categories.isEmpty()) {
            if (currentCategory == null || !categories.contains(currentCategory)) {
                currentCategory = categories.get(0);
            }
            categoryCache.clear();
            for (String cat : categories) {
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.ShopOpenPacket(cat));
            }
        }
    }

    /** 搜索前把所有分类的数据都拉一遍（只补缺失的，避免刷屏） */
    private void ensureAllCategoriesCached() {
        if (categories == null || categories.isEmpty()) return;
        for (String cat : categories) {
            if (categoryCache.containsKey(cat)) continue;
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.ShopOpenPacket(cat));
        }
    }

    private void filterEntries() {
        String lower = searchText.toLowerCase(Locale.ROOT);
        entryCategory.clear();
        if (searchText.isEmpty()) {
            filteredEntries = allEntries != null ? new ArrayList<>(allEntries) : new ArrayList<>();
            if (allEntries != null && currentCategory != null) {
                for (ShopEntry e : allEntries) entryCategory.put(e, currentCategory);
            }
        } else {
            filteredEntries = new ArrayList<>();
            for (Map.Entry<String, List<ShopEntry>> catEntry : categoryCache.entrySet()) {
                List<ShopEntry> catEntries = catEntry.getValue();
                if (catEntries == null) continue;
                for (ShopEntry entry : catEntries) {
                    String name = entry.getDisplayName().getString();
                    if (PinyinUtil.matches(name, lower) || entry.itemId().toString().contains(lower)) {
                        filteredEntries.add(entry);
                        entryCategory.put(entry, catEntry.getKey());
                    }
                }
            }
        }
    }

    /** 条目实际所属分类：跨分类搜索时未必等于 currentCategory */
    private String categoryOf(ShopEntry entry) {
        String cat = entryCategory.get(entry);
        return cat != null ? cat : currentCategory;
    }

    private void sendOpenRequest() {
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ShopOpenPacket(currentCategory));
    }

    /** 背景已经在 render 里自绘，阻止原版在 super.render 时再盖一层渐变/泥土背景 */
    @Override
    public void renderBackground(GuiGraphics guiGraphics) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        if (this.font == null) this.font = Minecraft.getInstance().font;
        int left = (this.width - imageWidth) / 2;
        int top = topOffset;
        guiGraphics.fill(0, 0, this.width, this.height, T.screenBackground);
        fillRoundedRect(guiGraphics, left, top, imageWidth, imageHeight, T.mainPanelBg);
        fillRoundedRect(guiGraphics, left, top, leftPanelW, imageHeight, T.leftPanelBg);
        if (T.panelBorder != 0) {
            drawPanelBorder(guiGraphics, left, top, imageWidth, imageHeight, T.panelBorder);
        }
        if (T.headerBandBg != 0) {
            fillRoundedRect(guiGraphics, left, top, imageWidth, HEADER_BAND_HEIGHT, T.headerBandBg);
        }
        guiGraphics.fill(left + leftPanelW, top + 2, left + leftPanelW + 1, top + imageHeight - 2, T.panelDivider);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        renderSearchBorder(guiGraphics, left, top);
        renderCategories(guiGraphics, left, top, mouseX, mouseY);
        renderItems(guiGraphics, left, top, mouseX, mouseY);
        renderBalance(guiGraphics, left, top, mouseX, mouseY);
        if (ModNetwork.clientEditMode) {
            renderEditModeIndicator(guiGraphics, left, top);
        }
        renderItemTooltips(guiGraphics, left, top, mouseX, mouseY);
        if (contextMenuOpen) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 300);
            renderContextMenu(guiGraphics, mouseX, mouseY);
            guiGraphics.pose().popPose();
        }
    }

    private void renderSearchBorder(GuiGraphics guiGraphics, int left, int top) {
        int sx = left + leftPanelW + 10;
        int sy = top + 11;
        int sw = imageWidth - leftPanelW - 24;
        int sh = 20;
        if (T.searchBg != 0) {
            fillRoundedRect(guiGraphics, sx, sy, sw, sh, T.searchBg);
        }
        GuiDraw.inputBox(guiGraphics, sx, sy, sw, sh, searchFocused);

        if (searchText.isEmpty() && !searchFocused) {
            FontUtil.drawAutoFitText(guiGraphics, font, "没有钱？将有价格的物品放入出货箱以赚取货币~", GuiDraw.innerTextX(sx), GuiDraw.innerTextY(sy, sh), sw - 8, T.textHint);
        } else if (!searchText.isEmpty()) {
            FontUtil.drawAutoFitText(guiGraphics, font, searchText, GuiDraw.innerTextX(sx), GuiDraw.innerTextY(sy, sh), sw - 8, T.searchText);
        }

        if (searchFocused) {
            int cursorX = sx + 4 + (searchText.isEmpty() ? 0 : font.width(searchText));
            guiGraphics.fill(cursorX, sy + 3, cursorX + 1, sy + sh - 3, T.searchCursor);
        }
    }

    private void renderEditModeIndicator(GuiGraphics guiGraphics, int left, int top) {
        Component editLabel = Component.literal("[编辑模式]");
        int labW = this.font.width(editLabel);
        guiGraphics.drawString(this.font, editLabel, left + imageWidth - labW - 6, top + imageHeight - 14, T.textError, GuiDraw.shadow());
    }

    private void renderCategories(GuiGraphics guiGraphics, int left, int top, int mouseX, int mouseY) {
        Component title = Component.translatable("gui.shop.categories");
        int titleW = this.font.width(title);
        float titleScale = T.titleFontScale;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(left + (leftPanelW - titleW * titleScale) / 2, top + 13, 0);
        guiGraphics.pose().scale(titleScale, titleScale, 1.0f);
        guiGraphics.drawString(this.font, title, 0, 0, T.catTitleText, GuiDraw.shadow());
        guiGraphics.pose().popPose();

        int catStartY = top + 40;
        int catEndY = top + imageHeight - 60;
        int available = catEndY - catStartY;
        int maxVisible = Math.max(1, available / (categoryHeight + 4));

        for (int i = categoryScrollOffset; i < categories.size() && i < categoryScrollOffset + maxVisible; i++) {
            int localIdx = i - categoryScrollOffset;
            int y = catStartY + localIdx * (categoryHeight + 4);
            String cat = categories.get(i);
            int bgX = left + 5;
            int bgY = y - 1;
            int bgW = leftPanelW - 10;
            int bgH = categoryHeight + 2;

            boolean isSearching = !searchText.isEmpty();
            boolean hovered = mouseX >= bgX && mouseX <= bgX + bgW &&
                              mouseY >= bgY && mouseY <= bgY + bgH;
            boolean selected = cat.equals(currentCategory) && !isSearching;

            // 底色：现代主题用纯色药丸（catPillBg / catPillSelectedBg），经典主题退化为填色 + 边框
            int fillColor = 0;
            if (selected) {
                fillColor = T.catPillSelectedBg != 0 ? T.catPillSelectedBg : T.catSelectedFill;
            } else if (hovered) {
                fillColor = T.catHoveredFill;
            } else if (T.catPillBg != 0) {
                fillColor = T.catPillBg;
            }
            if (fillColor != 0) {
                fillRoundedRect(guiGraphics, bgX, bgY, bgW, bgH, fillColor);
            }
            if (T.drawCategoryBorder) {
                drawRoundedBorder(guiGraphics, bgX, bgY, bgW, bgH, T.catBorder);
            }

            int textY = bgY + (bgH - this.font.lineHeight) / 2;
            int textColor = selected && T.catPillSelectedBg != 0 ? T.textPrimary : T.catText;
            FontUtil.drawAutoFitCentered(guiGraphics, this.font, cat, bgX + bgW / 2, textY, bgW - 4, textColor);
        }

        renderCategoryScrollbar(guiGraphics, left, catStartY, available, maxVisible);

        if (ModNetwork.clientEditMode) {
            int btnY = top + imageHeight - 28;
            int btnH = 18;
            boolean hovered = mouseX >= left + 5 && mouseX <= left + leftPanelW - 5 &&
                              mouseY >= btnY && mouseY <= btnY + btnH;
            int btnColor = hovered ? T.addSlotBgHovered : T.addSlotBgNormal;
            fillRoundedRect(guiGraphics, left + 5, btnY, leftPanelW - 10, btnH, btnColor);
            Component addCat = Component.literal("+ 添加分类");
            FontUtil.drawAutoFitCentered(guiGraphics, this.font, addCat.getString(), left + leftPanelW / 2, btnY + 5, leftPanelW - 14, T.addSlotText);
        }
    }

    private void renderCategoryScrollbar(GuiGraphics gui, int left, int trackTop, int trackH, int visibleCount) {
        int maxScroll = Math.max(0, categories.size() - visibleCount);
        if (maxScroll <= 0) return;
        int sbX = left + leftPanelW - 2;
        gui.fill(sbX, trackTop, sbX + 1, trackTop + trackH, T.scrollbarTrack);
        int thumbH = Math.max(6, trackH * visibleCount / categories.size());
        int thumbY = trackTop + (trackH - thumbH) * categoryScrollOffset / maxScroll;
        gui.fill(sbX, thumbY, sbX + 1, thumbY + thumbH, T.scrollbarThumb);
    }

    private void renderItems(GuiGraphics guiGraphics, int left, int top, int mouseX, int mouseY) {
        List<ShopEntry> display = filteredEntries != null ? filteredEntries : allEntries;
        if (display == null) return;

        int gridStartX = left + leftPanelW + 14;
        int gridStartY = top + 38;
        int totalItems = display.size();
        int extraSlot = ModNetwork.clientEditMode ? 1 : 0;
        int totalSlots = totalItems + extraSlot;
        int endIndex = Math.min(scrollOffset + GRID_COLS * GRID_ROWS, totalSlots);

        if (!ModNetwork.clientEditMode && totalSlots == 0) {
            FontUtil.drawAutoFitText(guiGraphics, this.font, Component.translatable("gui.shop.no_items"),
                    left + leftPanelW + 20, top + 50, imageWidth - leftPanelW - 40, T.textDisabled);
            return;
        }

        for (int i = scrollOffset; i < endIndex; i++) {
            int col = (i - scrollOffset) % GRID_COLS;
            int row = (i - scrollOffset) / GRID_COLS;
            int x = gridStartX + col * gridSpacingX;
            int y = gridStartY + row * gridSpacingY;

            if (i >= totalItems) {
                renderAddSlot(guiGraphics, x, y, mouseX, mouseY);
            } else {
                ShopEntry entry = display.get(i);

                int gap = 2;
                int bx = x + gap;
                int by = y + gap;
                int bw = gridSpacingX - gap * 2;
                int bh = gridSpacingY - gap * 2;
                if (T.cellBg != 0) {
                    fillRoundedRect(guiGraphics, bx, by, bw, bh, T.cellBg);
                }
                if (T.drawCellBorder) {
                    drawRoundedBorder(guiGraphics, bx, by, bw, bh, T.cellBorder);
                }

                boolean hovered = mouseX >= x && mouseX <= x + gridSpacingX &&
                                  mouseY >= y && mouseY <= y + gridSpacingY;
                if (hovered) {
                    fillRoundedRect(guiGraphics, bx + 1, by + 1, bw - 2, bh - 2, T.cellHoveredFill);
                }

                ItemStack stack = entry.createStack();
                ItemIcons.render(guiGraphics, stack, x + (gridSpacingX - 16) / 2, y + (gridSpacingY - 16) / 2);

                int qty = entry.quantity();
                if (qty > 1) {
                    String qtyStr = String.valueOf(qty);
                    int qtyX = x + (gridSpacingX + 16) / 2 - font.width(qtyStr) + 7;
                    int qtyY = y + (gridSpacingY + 16) / 2 - 4;
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(0, 0, 200);
                    guiGraphics.pose().translate(qtyX, qtyY, 0);
                    guiGraphics.pose().scale(T.qtyTextScale, T.qtyTextScale, 1.0f);
                    guiGraphics.drawString(this.font, qtyStr, 0, 0, T.textPrimary, GuiDraw.shadow());
                    guiGraphics.pose().popPose();
                }

                String tradeStr;
                if ("barter".equals(entry.entryType()) && entry.barterItemId() != null) {
                    tradeStr = "以物易物";
                } else {
                    tradeStr = entry.tradeType() == ShopEntry.TradeType.BUY ? "购买" : "出售";
                }
                guiGraphics.pose().pushPose();
                float tradeScale = T.tradeTypeScale;
                int tradeX = x + (gridSpacingX - (int)(font.width(tradeStr) * tradeScale)) / 2;
                int tradeY = y + 2;
                guiGraphics.pose().translate(tradeX, tradeY, 0);
                guiGraphics.pose().scale(tradeScale, tradeScale, 1.0f);
                guiGraphics.drawString(this.font, tradeStr, 0, 0, T.textTradeType, GuiDraw.shadow());
                guiGraphics.pose().popPose();

                String bottomStr;
                if ("barter".equals(entry.entryType()) && entry.barterItemId() != null) {
                    ItemStack offerStack = entry.createBarterOfferStack();
                    bottomStr = offerStack.isEmpty() ? "?" : offerStack.getHoverName().getString();
                } else {
                    bottomStr = "¥" + entry.price();
                }
                FontUtil.drawAutoFitCentered(guiGraphics, this.font, bottomStr, x + gridSpacingX / 2, y + gridSpacingY - 10, gridSpacingX - 4, T.textPrice);
            }
        }

        renderItemScrollbar(guiGraphics, left, top, gridStartY);
    }

    private void renderItemScrollbar(GuiGraphics gui, int left, int top, int gridStartY) {
        List<ShopEntry> display = filteredEntries != null ? filteredEntries : allEntries;
        int totalItems = display != null ? display.size() : 0;
        int extraSlot = ModNetwork.clientEditMode ? 1 : 0;
        int totalSlots = totalItems + extraSlot;
        int visibleSlots = GRID_COLS * GRID_ROWS;
        int overflow = totalSlots - visibleSlots;
        int maxScroll = overflow > 0 ? ((overflow + GRID_COLS - 1) / GRID_COLS) * GRID_COLS : 0;
        if (maxScroll <= 0) return;
        int trackTop = gridStartY;
        int trackH = GRID_ROWS * gridSpacingY;
        int sbX = left + leftPanelW + 14 + GRID_COLS * gridSpacingX + 3;
        gui.fill(sbX, trackTop, sbX + 3, trackTop + trackH, T.scrollbarTrack);
        int thumbH = Math.max(6, trackH * visibleSlots / totalSlots);
        int thumbY = trackTop + (trackH - thumbH) * scrollOffset / maxScroll;
        gui.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, T.scrollbarThumb);
    }

    private void renderAddSlot(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        int gap = 2;
        int bx = x + gap;
        int by = y + gap;
        int bw = gridSpacingX - gap * 2;
        int bh = gridSpacingY - gap * 2;
        boolean hovered = mouseX >= x && mouseX <= x + gridSpacingX &&
                          mouseY >= y && mouseY <= y + gridSpacingY;
        if (T.cellBg != 0) {
            fillRoundedRect(guiGraphics, bx, by, bw, bh, T.cellBg);
        }
        if (T.drawCellBorder) {
            drawRoundedBorder(guiGraphics, bx, by, bw, bh, T.addSlotBorder);
        }
        fillRoundedRect(guiGraphics, bx + 1, by + 1, bw - 2, bh - 2,
                hovered ? T.addSlotBgHovered : T.addSlotBgNormal);

        Component plus = Component.literal("+");
        int pw = this.font.width(plus);
        guiGraphics.drawString(this.font, plus, x + (gridSpacingX - pw) / 2, y + (gridSpacingY - 9) / 2, T.addSlotText, GuiDraw.shadow());
    }

    private void renderItemTooltips(GuiGraphics guiGraphics, int left, int top, int mouseX, int mouseY) {
        // 每帧重置悬停目标，鼠标不在任何条目上时就清空（Del 才不会误删）
        hoveredEntry = null;
        hoveredEntryCategory = null;
        List<ShopEntry> display = filteredEntries != null ? filteredEntries : allEntries;
        if (display == null) return;
        int gridStartX = left + leftPanelW + 14;
        int gridStartY = top + 38;
        int totalItems = display.size();
        int extraSlot = ModNetwork.clientEditMode ? 1 : 0;
        int totalSlots = totalItems + extraSlot;
        int endIndex = Math.min(scrollOffset + GRID_COLS * GRID_ROWS, totalSlots);
        for (int i = scrollOffset; i < endIndex; i++) {
            int col = (i - scrollOffset) % GRID_COLS;
            int row = (i - scrollOffset) / GRID_COLS;
            int x = gridStartX + col * gridSpacingX;
            int y = gridStartY + row * gridSpacingY;
            if (mouseX >= x && mouseX <= x + gridSpacingX &&
                mouseY >= y && mouseY <= y + gridSpacingY) {
                if (i >= totalItems) {
                    guiGraphics.renderTooltip(this.font, Component.literal("添加商品"), mouseX, mouseY);
                } else {
                    ShopEntry entry = display.get(i);
                    // 记录悬停条目，供编辑模式的 Del 删除使用
                    hoveredEntry = entry;
                    hoveredEntryCategory = categoryOf(entry);
                    int qty = entry.quantity();
                    String itemName = entry.getDisplayName().getString();
                    String preview;
                    if ("barter".equals(entry.entryType()) && entry.barterItemId() != null) {
                        String offerName = entry.createBarterOfferStack().isEmpty() ? "?" : entry.createBarterOfferStack().getHoverName().getString();
                        int offerQ = Math.max(1, entry.barterItemQty());
                        int goodsQ = Math.max(1, entry.quantity());
                        preview = offerName + " x" + offerQ + " → " + itemName + " x" + goodsQ;
                    } else if (entry.tradeType() == ShopEntry.TradeType.BUY) {
                        preview = "¥" + entry.price() + " → " + itemName + (qty > 1 ? " x" + qty : "");
                    } else {
                        preview = itemName + (qty > 1 ? " x" + qty : "") + " → ¥" + entry.price();
                    }
                    java.util.List<Component> lines = new java.util.ArrayList<>(java.util.List.of(
                            entry.getDisplayName(),
                            Component.literal(preview).withStyle(net.minecraft.ChatFormatting.GOLD)));
                    if (ModNetwork.clientEditMode && entry.requiredQuestId() != null) {
                        String qName = entry.requiredQuestName() != null ? entry.requiredQuestName()
                                : "任务 #" + Long.toHexString(entry.requiredQuestId()).toUpperCase();
                        lines.add(Component.literal("前置任务：" + qName).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                    }
                    // 交易限量：无限则不显示；额度用尽转红
                    if (entry.hasTradeLimit()) {
                        boolean done = isLimitExhausted(entry);
                        String limitLabel = entry.limitPerPlayer() ? "限量: " : "总限量: ";
                        String limitText = limitLabel + entry.tradeLimit()
                                + (done ? "  已售罄" : "  剩 " + limitRemaining(entry));
                        lines.add(Component.literal(limitText)
                                .withStyle(done ? net.minecraft.ChatFormatting.RED
                                        : net.minecraft.ChatFormatting.GREEN));
                    }
                    List<ShopEntry> related = JEIIntegration.findRelatedEntries(entry);
                    if (!related.isEmpty()) {
                        lines.add(Component.literal(""));
                        lines.add(Component.literal("—— 其他兑换方式 ——").withStyle(net.minecraft.ChatFormatting.GRAY));
                        for (ShopEntry r : related) {
                            String rItemName = r.getDisplayName().getString();
                            int rQty = Math.max(1, r.quantity());
                            if ("barter".equals(r.entryType()) && r.barterItemId() != null) {
                                String offerName = r.createBarterOfferStack().isEmpty() ? "?" : r.createBarterOfferStack().getHoverName().getString();
                                int oQ = Math.max(1, r.barterItemQty());
                                lines.add(Component.literal("  " + offerName + " x" + oQ + " → " + rItemName + " x" + rQty).withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
                            } else if (r.tradeType() == ShopEntry.TradeType.BUY) {
                                lines.add(Component.literal("  ◆" + r.price() + " → " + rItemName + (rQty > 1 ? " x" + rQty : "")).withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
                            } else {
                                lines.add(Component.literal("  " + rItemName + (rQty > 1 ? " x" + rQty : "") + " → ◆" + r.price()).withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
                            }
                        }
                    }
                    guiGraphics.renderTooltip(this.font, lines, java.util.Optional.empty(), mouseX, mouseY);
                }
                return;
            }
        }
    }

    private void renderBalance(GuiGraphics guiGraphics, int left, int top, int mouseX, int mouseY) {
        int balX = left + 4;
        int balY = top + imageHeight - 60;
        int balW = leftPanelW - 8;
        int balH = 30;

        // 余额卡：底色三档兜底（balanceBg → cellBg → listRowBg），边框两档兜底（balanceBorder → 主题框色），
        // 保证任何主题下「余额」都有一个看得见的框
        int balBgCol = T.balanceBg != 0 ? T.balanceBg : (T.cellBg != 0 ? T.cellBg : T.listRowBg);
        if (balBgCol != 0) fillRoundedRect(guiGraphics, balX, balY, balW, balH, balBgCol);
        int borderCol = T.balanceBorder != 0 ? T.balanceBorder
                : (T.panelDivider != 0 ? T.panelDivider : GuiDraw.frameColor());
        if (borderCol != 0) {
            drawRoundedBorder(guiGraphics, balX, balY, balW, balH, borderCol);
        }

        Component label = Component.translatable("gui.shop.balance");
        int labelW = this.font.width(label);
        guiGraphics.drawString(this.font, label, left + (leftPanelW - labelW) / 2, balY + 3, T.balanceText, GuiDraw.shadow());

        Component amount = Component.literal("¥" + String.format("%,d", balance));
        FontUtil.drawAutoFitCentered(guiGraphics, this.font, amount.getString(), left + leftPanelW / 2, balY + 15, leftPanelW - 12, T.textPrimary);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (contextMenuOpen) {
            handleContextMenuClick(mouseX, mouseY);
            return true;
        }

        int left = (this.width - imageWidth) / 2;
        int top = topOffset;

        int sx = left + leftPanelW + 10;
        int sy = top + 11;
        int sw = imageWidth - leftPanelW - 24;
        int sh = 20;
        if (mouseX >= sx && mouseX <= sx + sw && mouseY >= sy && mouseY <= sy + sh) {
            searchFocused = true;
            return true;
        } else {
            searchFocused = false;
        }

        int catStartY = top + 40;
        int catEndY = top + imageHeight - 60;
        int available = catEndY - catStartY;
        int maxVisible = Math.max(1, available / (categoryHeight + 4));

        int catSbX = left + leftPanelW - 2;
        if (mouseX >= catSbX && mouseX < catSbX + 1 && mouseY >= catStartY && mouseY < catStartY + available) {
            int catMaxScroll = Math.max(0, categories.size() - maxVisible);
            if (catMaxScroll > 0) {
                int thumbH = Math.max(6, available * maxVisible / categories.size());
                int oldThumbY = catStartY + (available - thumbH) * categoryScrollOffset / catMaxScroll;
                if (mouseY >= oldThumbY && mouseY <= oldThumbY + thumbH) {
                    isDraggingCatScrollbar = true;
                    catDragStartMouseY = (int) mouseY;
                    catDragStartOffset = categoryScrollOffset;
                } else {
                    categoryScrollOffset = (int) ((mouseY - catStartY) / (double) available * catMaxScroll);
                    categoryScrollOffset = Math.max(0, Math.min(categoryScrollOffset, catMaxScroll));
                }
            }
            return true;
        }

        for (int i = categoryScrollOffset; i < categories.size() && i < categoryScrollOffset + maxVisible; i++) {
            int localIdx = i - categoryScrollOffset;
            int y = catStartY + localIdx * (categoryHeight + 4);
            int bgX = left + 5;
            int bgY = y - 1;
            int bgW = leftPanelW - 10;
            int bgH = categoryHeight + 2;
            if (mouseX >= bgX && mouseX <= bgX + bgW &&
                mouseY >= bgY && mouseY <= bgY + bgH) {
                String clicked = categories.get(i);
                if (button == 0) {
                    if (!clicked.equals(currentCategory) || !searchText.isEmpty()) {
                        currentCategory = clicked;
                        scrollOffset = 0;
                        searchText = "";
                        sendOpenRequest();
                    }
                } else if (button == 1 && ModNetwork.clientEditMode) {
                    openCategoryContextMenu((int) mouseX, (int) mouseY, clicked);
                }
                return true;
            }
        }

        if (ModNetwork.clientEditMode) {
            int btnY = top + imageHeight - 28;
            int btnH = 18;
            if (mouseX >= left + 5 && mouseX <= left + leftPanelW - 5 &&
                mouseY >= btnY && mouseY <= btnY + btnH) {
                Minecraft.getInstance().setScreen(new AddCategoryScreen(this));
                return true;
            }
        }

        List<ShopEntry> display = filteredEntries != null ? filteredEntries : allEntries;
        if (display != null) {
            int gridStartX = left + leftPanelW + 14;
            int gridStartY = top + 38;
            int totalItems = display.size();
            int extraSlot = ModNetwork.clientEditMode ? 1 : 0;
            int totalSlots = totalItems + extraSlot;
            int endIndex = Math.min(scrollOffset + GRID_COLS * GRID_ROWS, totalSlots);
            for (int i = scrollOffset; i < endIndex; i++) {
                int col = (i - scrollOffset) % GRID_COLS;
                int row = (i - scrollOffset) / GRID_COLS;
                int x = gridStartX + col * gridSpacingX;
                int y = gridStartY + row * gridSpacingY;
                if (mouseX >= x && mouseX <= x + gridSpacingX &&
                    mouseY >= y && mouseY <= y + gridSpacingY) {
                    if (i >= totalItems) {
                        if (button == 0) {
                            Minecraft.getInstance().setScreen(new AddItemTypeScreen(this, currentCategory));
                        }
                    } else {
                        ShopEntry entry = display.get(i);
                        if (button == 0) {
                            // 额度已用尽：点击无效（服务端也会再拦一道）
                            if (!isLimitExhausted(entry)) {
                                openTradeConfirm(entry);
                            }
                        } else if (button == 1) {
                            if (ModNetwork.clientEditMode) {
                                int realIndex = allEntries != null ? allEntries.indexOf(entry) : -1;
                                if (realIndex >= 0) {
                                    openContextMenu((int) mouseX, (int) mouseY, entry, realIndex);
                                }
                            }
                        }
                    }
                    return true;
                }
            }
        }

        int gridStartY2 = top + 38;
        int itemSbX = left + leftPanelW + 14 + GRID_COLS * gridSpacingX + 3;
        int itemTrackH = GRID_ROWS * gridSpacingY;
        List<ShopEntry> display2 = filteredEntries != null ? filteredEntries : allEntries;
        int totalItems2 = display2 != null ? display2.size() : 0;
        int extraSlot2 = ModNetwork.clientEditMode ? 1 : 0;
        int totalSlots2 = totalItems2 + extraSlot2;
        int visibleSlots = GRID_COLS * GRID_ROWS;
        int overflow2 = totalSlots2 - visibleSlots;
        int itemMaxScroll = overflow2 > 0 ? ((overflow2 + GRID_COLS - 1) / GRID_COLS) * GRID_COLS : 0;
        if (mouseX >= itemSbX && mouseX < itemSbX + 3 && mouseY >= gridStartY2 && mouseY < gridStartY2 + itemTrackH && itemMaxScroll > 0) {
            scrollOffset = (int) ((mouseY - gridStartY2) / (double) itemTrackH * itemMaxScroll);
            scrollOffset = Math.max(0, Math.min(scrollOffset, itemMaxScroll));
            scrollOffset = (scrollOffset / GRID_COLS) * GRID_COLS;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void openTradeConfirm(ShopEntry entry) {
        Minecraft.getInstance().setScreen(new TradeConfirmScreen(this, entry));
    }

    private void openContextMenu(int mx, int my, ShopEntry entry, int index) {
        this.contextMenuEntry = entry;
        this.contextMenuIndex = index;
        this.contextMenuCategory = null;
        this.contextMenuX = mx;
        this.contextMenuY = my;
        this.contextMenuOpen = true;
    }

    private void openCategoryContextMenu(int mx, int my, String category) {
        this.contextMenuEntry = null;
        this.contextMenuIndex = -1;
        this.contextMenuCategory = category;
        this.contextMenuX = mx;
        this.contextMenuY = my;
        this.contextMenuOpen = true;
    }

    private void closeContextMenu() {
        contextMenuOpen = false;
        contextMenuEntry = null;
        contextMenuIndex = -1;
        contextMenuCategory = null;
    }

    private List<String> buildContextMenuOptions() {
        List<String> options = new ArrayList<>();
        options.add("编辑");
        options.add("复制");
        options.add("删除");
        if (contextMenuCategory == null && contextMenuEntry != null) {
            options.add("交易限量");
        }
        if (contextMenuCategory == null && contextMenuEntry != null) {
            options.add("前置任务");
        }
        if (contextMenuCategory != null) {
            int idx = categories.indexOf(contextMenuCategory);
            if (idx > 0) options.add("向前移动");
            if (idx >= 0 && idx < categories.size() - 1) options.add("向后移动");
        } else {
            if (contextMenuIndex > 0) {
                options.add("向前移动");
            }
            if (allEntries != null && contextMenuIndex >= 0 && contextMenuIndex < allEntries.size() - 1) {
                options.add("向后移动");
            }
        }
        return options;
    }

    private void handleContextMenuClick(double mouseX, double mouseY) {
        List<String> options = buildContextMenuOptions();
        if (options.isEmpty()) {
            closeContextMenu();
            return;
        }
        int optH = 12;
        int maxW = 0;
        for (String opt : options) {
            maxW = Math.max(maxW, font.width(opt));
        }
        int menuW = maxW + 12;
        int menuH = options.size() * optH + 4;
        int menuX = Math.min(contextMenuX, width - menuW - 2);
        int menuY = Math.min(contextMenuY, height - menuH - 2);
        menuX = Math.max(2, menuX);
        menuY = Math.max(2, menuY);
        if (mouseX < menuX || mouseX > menuX + menuW || mouseY < menuY || mouseY > menuY + menuH) {
            closeContextMenu();
            return;
        }
        for (int i = 0; i < options.size(); i++) {
            int optY = menuY + 2 + i * optH;
            if (mouseY >= optY && mouseY <= optY + optH) {
                executeContextMenuAction(options.get(i));
                closeContextMenu();
                return;
            }
        }
    }

    private void executeContextMenuAction(String action) {
        if (contextMenuCategory != null) {
            switch (action) {
                case "编辑" -> Minecraft.getInstance().setScreen(new EditCategoryScreen(this, contextMenuCategory));
                case "复制" -> {
                    ModNetwork.CHANNEL.sendToServer(new ModNetwork.ShopEditPacket("copy_category", contextMenuCategory, "", "", 0, -1, false, "", 1, ""));
                    refreshFromConfig();
                }
                case "删除" -> {
                    ModNetwork.CHANNEL.sendToServer(new ModNetwork.ShopEditPacket("remove_category", contextMenuCategory, "", "", 0, -1, false, "", 1, ""));
                    refreshFromConfig();
                }
                case "向前移动" -> {
                    ModNetwork.CHANNEL.sendToServer(new ModNetwork.ShopEditPacket("move_category_up", contextMenuCategory, "", "", 0, -1, false, "", 1, ""));
                    refreshFromConfig();
                }
                case "向后移动" -> {
                    ModNetwork.CHANNEL.sendToServer(new ModNetwork.ShopEditPacket("move_category_down", contextMenuCategory, "", "", 0, -1, false, "", 1, ""));
                    refreshFromConfig();
                }
            }
            return;
        }
        if (contextMenuEntry == null || contextMenuIndex < 0) return;
        switch (action) {
            case "编辑" -> {
                if ("xp".equals(contextMenuEntry.entryType())) {
                    Minecraft.getInstance().setScreen(
                            new AddXpScreen(this, currentCategory, contextMenuIndex, contextMenuEntry));
                } else if ("command".equals(contextMenuEntry.entryType())) {
                    Minecraft.getInstance().setScreen(
                            new AddCommandScreen(this, currentCategory, contextMenuIndex, contextMenuEntry));
                } else {
                    Minecraft.getInstance().setScreen(
                            new ShopItemPickerScreen(this, currentCategory, contextMenuIndex, contextMenuEntry));
                }
            }
            case "交易限量" -> Minecraft.getInstance().setScreen(
                    new SetItemLimitScreen(this, currentCategory, contextMenuIndex, contextMenuEntry));
            case "复制" -> ModNetwork.CHANNEL.sendToServer(
                    new ModNetwork.ShopEditPacket("copy_item", currentCategory, "", "", 0, contextMenuIndex, false, "", 1, ""));
            case "删除" -> ModNetwork.CHANNEL.sendToServer(
                    new ModNetwork.ShopEditPacket("remove_item", currentCategory, "", "", 0, contextMenuIndex, false, "", 1, ""));
            case "前置任务" -> Minecraft.getInstance().setScreen(
                    new QuestPickerScreen(this, currentCategory, contextMenuIndex,
                            contextMenuEntry != null ? contextMenuEntry.requiredQuestId() : null,
                            contextMenuEntry != null ? contextMenuEntry.requiredQuestName() : null));
            case "向前移动" -> ModNetwork.CHANNEL.sendToServer(
                    new ModNetwork.ShopEditPacket("move_item_up", currentCategory, "", "", 0, contextMenuIndex, false, "", 1, ""));
            case "向后移动" -> ModNetwork.CHANNEL.sendToServer(
                    new ModNetwork.ShopEditPacket("move_item_down", currentCategory, "", "", 0, contextMenuIndex, false, "", 1, ""));
        }
    }

    private void renderContextMenu(GuiGraphics gui, int mouseX, int mouseY) {
        if (!contextMenuOpen) return;
        if (contextMenuCategory == null && contextMenuEntry == null) return;
        List<String> options = buildContextMenuOptions();
        if (options.isEmpty()) return;
        int optH = 12;
        int maxW = 0;
        for (String opt : options) {
            maxW = Math.max(maxW, font.width(opt));
        }
        int menuW = maxW + 12;
        int menuH = options.size() * optH + 4;
        int menuX = Math.min(contextMenuX, width - menuW - 2);
        int menuY = Math.min(contextMenuY, height - menuH - 2);
        menuX = Math.max(2, menuX);
        menuY = Math.max(2, menuY);
        gui.fill(menuX, menuY, menuX + menuW, menuY + menuH, T.ctxMenuBg);
        gui.fill(menuX, menuY, menuX + menuW, menuY + 1, T.ctxMenuBorder);
        gui.fill(menuX, menuY + menuH - 1, menuX + menuW, menuY + menuH, T.ctxMenuBorder);
        gui.fill(menuX, menuY, menuX + 1, menuY + menuH, T.ctxMenuBorder);
        gui.fill(menuX + menuW - 1, menuY, menuX + menuW, menuY + menuH, T.ctxMenuBorder);
        for (int i = 0; i < options.size(); i++) {
            int optY = menuY + 2 + i * optH;
            boolean hovered = mouseX >= menuX && mouseX <= menuX + menuW &&
                              mouseY >= optY && mouseY <= optY + optH;
            if (hovered) {
                gui.fill(menuX + 1, optY, menuX + menuW - 1, optY + optH, T.ctxMenuItemHover);
            }
            FontUtil.drawAutoFitText(gui, font, options.get(i), menuX + 6, optY + 2, menuW - 12, T.textPrimary);
        }
    }

    /** 按当前主题的 cornerRadius 画圆角矩形 */
    private void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (color == 0 || w <= 0 || h <= 0) return;
        int r = Math.max(0, Math.min(T.cornerRadius, Math.min(w, h) / 2));
        if (r <= 2) {
            // 经典主题只有 2px 切角，用原快速路径
            g.fill(x + 2, y, x + w - 2, y + h, color);
            g.fill(x, y + 2, x + w, y + h - 2, color);
            return;
        }
        g.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int dy = r - i - 1;
            int inset = r - (int) Math.round(Math.sqrt((double) (r * r - dy * dy)));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
        }
    }

    /** 画 1px 圆角边框（形状与 fillRoundedRect 对应） */
    private void drawRoundedBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (color == 0 || w <= 0 || h <= 0) return;
        int r = Math.max(0, Math.min(T.cornerRadius, Math.min(w, h) / 2));
        if (r <= 2) {
            g.fill(x + 2, y, x + w - 2, y + 1, color);
            g.fill(x + 2, y + h - 1, x + w - 2, y + h, color);
            g.fill(x, y + 2, x + 1, y + h - 2, color);
            g.fill(x + w - 1, y + 2, x + w, y + h - 2, color);
            return;
        }
        g.fill(x, y + r, x + 1, y + h - r, color);
        g.fill(x + w - 1, y + r, x + w, y + h - r, color);
        g.fill(x + r, y, x + w - r, y + 1, color);
        g.fill(x + r, y + h - 1, x + w - r, y + h, color);
        for (int i = 0; i < r; i++) {
            int dy = r - i - 1;
            int inset = r - (int) Math.round(Math.sqrt((double) (r * r - dy * dy)));
            g.fill(x + inset, y + i, x + inset + 1, y + i + 1, color);
            g.fill(x + w - inset - 1, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - i - 1, x + inset + 1, y + h - i, color);
            g.fill(x + w - inset - 1, y + h - i - 1, x + w - inset, y + h - i, color);
        }
    }

    /** 面板外框 */
    private void drawPanelBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        drawRoundedBorder(g, x, y, w, h, color);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        var inventoryKey = this.minecraft.options.keyInventory;
        var shopKey = com.sellbox.mod.shop.ShopKeyHandler.getShopKey();
        boolean isToggleKey = (inventoryKey != null && inventoryKey.matches(keyCode, scanCode))
                || (shopKey != null && shopKey.matches(keyCode, scanCode));

        if (searchFocused) {
            if (keyCode == 259) {
                if (!searchText.isEmpty()) {
                    searchText = searchText.substring(0, searchText.length() - 1);
                    filterEntries();
                    scrollOffset = 0;
                }
                return true;
            }
            if (keyCode == 256) {
                searchFocused = false;
                return true;
            }
            // 搜索框聚焦时 E/K 作为字母正常输入（charTyped），不取消输入状态、不关闭商店
            return true;
        }
        if (contextMenuOpen) {
            closeContextMenu();
            return true;
        }
        // 编辑模式：Del 删除悬停商品，Ctrl+Z 撤销
        if (ModNetwork.clientEditMode) {
            if (keyCode == 261) {          // Del
                deleteHoveredEntry();
                return true;
            }
            if (keyCode == 90 && (modifiers & 0x2) != 0) {   // Ctrl+Z
                undoLastDelete();
                return true;
            }
        }
        // 按下背包键（E）或商店键（K）时关闭商店
        if (isToggleKey) {
            this.minecraft.setScreen(null);
            return true;
        }
        if (keyCode == 256) {
            this.minecraft.setScreen(null);
            return true;
        }
        if (JeiBridge.isAvailable() && (keyCode == 82 || keyCode == 85)) {
            double mouseX = minecraft.mouseHandler.xpos() / minecraft.getWindow().getGuiScale();
            double mouseY = minecraft.mouseHandler.ypos() / minecraft.getWindow().getGuiScale();
            ShopEntry entry = getEntryAt(mouseX, mouseY);
            if (entry != null) {
                ItemStack stack = entry.createStack();
                if (keyCode == 82) {
                    JeiBridge.showRecipes(stack);
                } else {
                    JeiBridge.showUses(stack);
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchFocused) {
            searchText = searchText + codePoint;
            ensureAllCategoriesCached();
            filterEntries();
            scrollOffset = 0;
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingCatScrollbar) {
            int catStartY = topOffset + 40;
            int catEndY = topOffset + imageHeight - 60;
            int available = catEndY - catStartY;
            int maxVisible = Math.max(1, available / (categoryHeight + 4));
            int catMaxScroll = Math.max(0, categories.size() - maxVisible);
            if (catMaxScroll > 0) {
                int thumbH = Math.max(6, available * maxVisible / categories.size());
                int trackH = available - thumbH;
                double delta = mouseY - catDragStartMouseY;
                int newOffset = catDragStartOffset + (int)(delta / trackH * catMaxScroll);
                categoryScrollOffset = Math.max(0, Math.min(newOffset, catMaxScroll));
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDraggingCatScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int left = (this.width - imageWidth) / 2;
        int top = topOffset;

        if (mouseX >= left && mouseX <= left + leftPanelW) {
            int catVisible = Math.max(1, (imageHeight - 60 - 55) / (categoryHeight + 4));
            int maxCatScroll = Math.max(0, categories.size() - catVisible);
            if (delta < 0) {
                categoryScrollOffset = Math.min(categoryScrollOffset + 1, maxCatScroll);
            } else if (delta > 0) {
                categoryScrollOffset = Math.max(0, categoryScrollOffset - 1);
            }
            return true;
        }

        int itemAreaStartX = left + leftPanelW + 14;
        int itemAreaEndX = itemAreaStartX + GRID_COLS * gridSpacingX + 6;
        int itemAreaStartY = top + 38;
        int itemAreaEndY = itemAreaStartY + GRID_ROWS * gridSpacingY;
        if (mouseX >= itemAreaStartX && mouseX <= itemAreaEndX && mouseY >= itemAreaStartY && mouseY <= itemAreaEndY) {
            List<ShopEntry> display = filteredEntries != null ? filteredEntries : allEntries;
            int total = display != null ? display.size() : 0;
            int extraSlot = ModNetwork.clientEditMode ? 1 : 0;
            int overflow = total + extraSlot - GRID_COLS * GRID_ROWS;
            // 对齐到整行（向上取整，保证最后一行也能滚到）
            int maxScroll = overflow > 0 ? ((overflow + GRID_COLS - 1) / GRID_COLS) * GRID_COLS : 0;
            if (delta < 0) {
                scrollOffset = Math.min(scrollOffset + GRID_COLS, maxScroll);
            } else if (delta > 0) {
                scrollOffset = Math.max(0, scrollOffset - GRID_COLS);
            }
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private ShopEntry getEntryAt(double mouseX, double mouseY) {
        List<ShopEntry> display = filteredEntries != null ? filteredEntries : allEntries;
        if (display == null) return null;
        int left = (this.width - imageWidth) / 2;
        int top = topOffset;
        int gridStartX = left + leftPanelW + 14;
        int gridStartY = top + 38;
        int totalItems = display.size();
        int extraSlot = ModNetwork.clientEditMode ? 1 : 0;
        int totalSlots = totalItems + extraSlot;
        int endIndex = Math.min(scrollOffset + GRID_COLS * GRID_ROWS, totalSlots);
        for (int i = scrollOffset; i < endIndex; i++) {
            int col = (i - scrollOffset) % GRID_COLS;
            int row = (i - scrollOffset) / GRID_COLS;
            int x = gridStartX + col * gridSpacingX;
            int y = gridStartY + row * gridSpacingY;
            if (mouseX >= x && mouseX <= x + gridSpacingX &&
                mouseY >= y && mouseY <= y + gridSpacingY) {
                if (i < totalItems) {
                    return display.get(i);
                }
            }
        }
        return null;
    }

    public void updateData(String category, List<ShopEntry> entries, int balance, List<String> newCategories) {
        updateData(category, entries, balance, newCategories, java.util.Map.of());
    }

    public void updateData(String category, List<ShopEntry> entries, int balance, List<String> newCategories,
                           java.util.Map<String, Integer> usedCounts) {
        closeContextMenu();
        this.categories = newCategories;
        if (currentCategory == null || category.equals(currentCategory)) {
            // 只有「切换了分类」才把滚动位置归零；
            // 同一分类的数据刷新（复制/编辑/删除商品后服务端回推）必须保留 scrollOffset，
            // 否则在第五排以后操作会突然跳回最上面。
            boolean categoryChanged = !category.equals(this.currentCategory);
            this.currentCategory = category;
            this.allEntries = entries;
            if (categoryChanged) {
                scrollOffset = 0;
            }
        }
        this.balance = balance;
        // 该分类的计数整体替换，避免残留上一次的数据
        java.util.Map<String, Integer> merged = new java.util.HashMap<>(limitUsed);
        String prefix = com.sellbox.mod.shop.TradeLimitTracker.keyPrefix(category);
        merged.keySet().removeIf(k -> k.startsWith(prefix));
        if (usedCounts != null) merged.putAll(usedCounts);
        this.limitUsed = merged;
        categoryCache.put(category, entries);
        filterEntries();
        JeiBridge.setSellBoxRecipes(category, entries);
    }

    /** Del：删除鼠标悬停的商品（仅编辑模式）*/
    private void deleteHoveredEntry() {
        if (hoveredEntry == null) return;
        String cat = hoveredEntryCategory != null ? hoveredEntryCategory : currentCategory;
        java.util.List<ShopEntry> list = categoryCache.get(cat);
        if (list == null) list = allEntries;
        if (list == null) return;
        int idx = list.indexOf(hoveredEntry);
        if (idx < 0) return;

        undoStack.push(new DeletedItem(cat, idx, hoveredEntry));
        while (undoStack.size() > UNDO_LIMIT) undoStack.removeLast();

        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ShopEditPacket(
                "remove_item", cat, "", "", 0, idx, false, "", 1, ""));
        hoveredEntry = null;
        hoveredEntryCategory = null;
        refreshFromConfig();
    }

    /** Ctrl+Z：撤销最近一次 Del 删除 */
    private void undoLastDelete() {
        DeletedItem d = undoStack.poll();
        if (d == null) {
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.literal("\u00a77没有可撤销的删除"), true);
            }
            return;
        }
        ShopEntry e = d.entry;
        // 1.20.1 的 ShopEntry.nbt 是 CompoundTag，包里传字符串（服务端再 parseTag）
        String nbtStr = e.nbt() != null ? e.nbt().toString() : "";
        String barterNbtStr = e.barterNbt() != null ? e.barterNbt().toString() : "";
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ShopEditPacket(
                "add_item", d.category, e.itemId().toString(), e.tradeType().name().toLowerCase(),
                e.price(), d.index, e.requireOp(), e.extraData(), e.quantity(), e.customName(),
                e.barterItemId() != null ? e.barterItemId().toString() : "",
                e.barterItemQty(), nbtStr, e.strictMatch(),
                barterNbtStr, e.barterStrictMatch()));
        refreshFromConfig();
    }

    /** 该条目的交易额度是否已被用尽 */
    public boolean isLimitExhausted(ShopEntry entry) {
        if (entry == null || !entry.hasTradeLimit()) return false;
        String cat = categoryOf(entry);
        if (cat == null) return false;
        String key = com.sellbox.mod.shop.TradeLimitTracker.keyOf(cat, entry);
        return limitUsed.getOrDefault(key, 0) >= entry.tradeLimit();
    }

    /** 该条目的交易额度剩余次数，未设限量时返回 -1 */
    public int limitRemaining(ShopEntry entry) {
        if (entry == null || !entry.hasTradeLimit()) return -1;
        String cat = categoryOf(entry);
        if (cat == null) return entry.tradeLimit();
        String key = com.sellbox.mod.shop.TradeLimitTracker.keyOf(cat, entry);
        return Math.max(0, entry.tradeLimit() - limitUsed.getOrDefault(key, 0));
    }

    /**
     * 服务端改了配置后重新拉数据。
     * <p>分类被删掉时不能盲选 {@code categories.get(0)}——那会强制跳回最上面。
     * 这里保底选择：旧分类还在就保持；否则选它原来的位置（被删后接替它的那个分类）；
     * 只有当前位置也越界了才回到第一个。
     */
    public void refreshFromConfig() {
        int oldIndex = currentCategory != null ? categories.indexOf(currentCategory) : -1;
        closeContextMenu();
        if (currentCategory == null || !categories.contains(currentCategory)) {
            if (categories.isEmpty()) {
                currentCategory = null;
            } else if (oldIndex >= 0) {
                currentCategory = categories.get(Math.min(oldIndex, categories.size() - 1));
            } else {
                currentCategory = categories.get(0);
            }
            scrollOffset = 0;
        }
        if (currentCategory != null) {
            sendOpenRequest();
        }
    }
}
