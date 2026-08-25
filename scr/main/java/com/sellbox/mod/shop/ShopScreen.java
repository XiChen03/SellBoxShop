package com.sellbox.mod.shop;

import com.sellbox.mod.integration.JEIIntegration;
import com.sellbox.mod.integration.jei.JeiBridge;
import com.sellbox.mod.network.ModNetwork;
import com.sellbox.mod.util.PinyinUtil;
import com.sellbox.mod.util.FontUtil;
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
    private int categoryScrollOffset = 0;
    private boolean isDraggingCatScrollbar = false;
    private int catDragStartMouseY, catDragStartOffset;
    private static final int GRID_COLS = 6;
    private static final int GRID_ROWS = 4;
    private int gridSpacingX = 44;
    private int gridSpacingY = 48;
    private int topOffset = 0;

    private boolean contextMenuOpen = false;
    private ShopEntry contextMenuEntry = null;
    private int contextMenuIndex = -1;
    private String contextMenuCategory = null;
    private int contextMenuX = 0;
    private int contextMenuY = 0;

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
        double scale = ShopUI.getScale(height);
        imageWidth = Math.max(200, (int) Math.round(420 * scale));
        imageHeight = Math.max(160, (int) Math.round(260 * scale));
        leftPanelW = Math.max(60, (int) Math.round(100 * scale));
        categoryHeight = Math.max(8, (int) Math.round(14 * scale));
        gridSpacingX = Math.max(20, (int) Math.round(44 * scale)) + 2;
        gridSpacingY = Math.max(24, (int) Math.round(48 * scale)) + 11;

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

    private void filterEntries() {
        String lower = searchText.toLowerCase(Locale.ROOT);
        if (searchText.isEmpty()) {
            filteredEntries = allEntries != null ? new ArrayList<>(allEntries) : new ArrayList<>();
        } else {
            filteredEntries = new ArrayList<>();
            for (List<ShopEntry> catEntries : categoryCache.values()) {
                if (catEntries == null) continue;
                for (ShopEntry entry : catEntries) {
                    String name = entry.getDisplayName().getString();
                    if (PinyinUtil.matches(name, lower) || entry.itemId().toString().contains(lower)) {
                        filteredEntries.add(entry);
                    }
                }
            }
        }
    }

    private void sendOpenRequest() {
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ShopOpenPacket(currentCategory));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        if (this.font == null) this.font = Minecraft.getInstance().font;
        int left = (this.width - imageWidth) / 2;
        int top = topOffset;

        guiGraphics.fill(0, 0, this.width, this.height, 0x00000000);
        fillRoundedRect(guiGraphics, left, top, imageWidth, imageHeight, 0xFF333333);
        fillRoundedRect(guiGraphics, left, top, leftPanelW, imageHeight, 0xFF3A3A3A);
        guiGraphics.fill(left + leftPanelW, top + 2, left + leftPanelW + 1, top + imageHeight - 2, 0xFF555555);

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
        int col = searchFocused ? 0x55FFFFFF : 0x55AAAAAA;
        guiGraphics.fill(sx + 2, sy, sx + sw - 2, sy + 1, col);
        guiGraphics.fill(sx + 2, sy + sh - 1, sx + sw - 2, sy + sh, col);
        guiGraphics.fill(sx, sy + 2, sx + 1, sy + sh - 2, col);
        guiGraphics.fill(sx + sw - 1, sy + 2, sx + sw, sy + sh - 2, col);

        if (searchText.isEmpty() && !searchFocused) {
            FontUtil.drawAutoFitText(guiGraphics, font, "没有钱？将有价格的物品放入出货箱以赚取货币~", sx + 4, sy + 4, sw - 8, 0x777777);
        } else if (!searchText.isEmpty()) {
            FontUtil.drawAutoFitText(guiGraphics, font, searchText, sx + 4, sy + 4, sw - 8, 0xFFFFFF);
        }

        if (searchFocused) {
            int cursorX = sx + 4 + (searchText.isEmpty() ? 0 : font.width(searchText));
            guiGraphics.fill(cursorX, sy + 3, cursorX + 1, sy + sh - 3, 0xFFFFFFFF);
        }
    }

    private void renderEditModeIndicator(GuiGraphics guiGraphics, int left, int top) {
        Component editLabel = Component.literal("[编辑模式]");
        int labW = this.font.width(editLabel);
        guiGraphics.drawString(this.font, editLabel, left + imageWidth - labW - 6, top + imageHeight - 14, 0xFFFF4444);
    }

    private void renderCategories(GuiGraphics guiGraphics, int left, int top, int mouseX, int mouseY) {
        Component title = Component.translatable("gui.shop.categories");
        int titleW = this.font.width(title);
        float titleScale = 1.3f;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(left + (leftPanelW - titleW * titleScale) / 2, top + 13, 0);
        guiGraphics.pose().scale(titleScale, titleScale, 1.0f);
        guiGraphics.drawString(this.font, title, 0, 0, 0xFFAA00);
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

            int borderColor = 0xFF4488FF;
            guiGraphics.fill(bgX + 2, bgY, bgX + bgW - 2, bgY + 1, borderColor);
            guiGraphics.fill(bgX + 2, bgY + bgH - 1, bgX + bgW - 2, bgY + bgH, borderColor);
            guiGraphics.fill(bgX, bgY + 2, bgX + 1, bgY + bgH - 2, borderColor);
            guiGraphics.fill(bgX + bgW - 1, bgY + 2, bgX + bgW, bgY + bgH - 2, borderColor);

            if (selected) {
                guiGraphics.fill(bgX + 2, bgY + 1, bgX + bgW - 2, bgY + bgH - 1, 0x444488FF);
            } else if (hovered) {
                guiGraphics.fill(bgX + 2, bgY + 1, bgX + bgW - 2, bgY + bgH - 1, 0x22FFFFFF);
            }

            int textY = bgY + (bgH - this.font.lineHeight) / 2;
            FontUtil.drawAutoFitCentered(guiGraphics, this.font, cat, bgX + bgW / 2, textY, bgW - 4, 0x88CCFF);
        }

        renderCategoryScrollbar(guiGraphics, left, catStartY, available, maxVisible);

        if (ModNetwork.clientEditMode) {
            int btnY = top + imageHeight - 28;
            int btnH = 18;
            boolean hovered = mouseX >= left + 5 && mouseX <= left + leftPanelW - 5 &&
                              mouseY >= btnY && mouseY <= btnY + btnH;
            int btnColor = hovered ? 0xAA44AA44 : 0x88333333;
            guiGraphics.fill(left + 5, btnY, left + 5 + leftPanelW - 10, btnY + btnH, btnColor);
            Component addCat = Component.literal("+ 添加分类");
            FontUtil.drawAutoFitCentered(guiGraphics, this.font, addCat.getString(), left + leftPanelW / 2, btnY + 5, leftPanelW - 14, 0x88FF88);
        }
    }

    private void renderCategoryScrollbar(GuiGraphics gui, int left, int trackTop, int trackH, int visibleCount) {
        int maxScroll = Math.max(0, categories.size() - visibleCount);
        if (maxScroll <= 0) return;
        int sbX = left + leftPanelW - 2;
        gui.fill(sbX, trackTop, sbX + 1, trackTop + trackH, 0x80333333);
        int thumbH = Math.max(6, trackH * visibleCount / categories.size());
        int thumbY = trackTop + (trackH - thumbH) * categoryScrollOffset / maxScroll;
        gui.fill(sbX, thumbY, sbX + 1, thumbY + thumbH, 0xCCAAAAAA);
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
                    left + leftPanelW + 20, top + 50, imageWidth - leftPanelW - 40, 0x888888);
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
                guiGraphics.fill(bx, by, bx + bw, by + 1, 0xFF444444);
                guiGraphics.fill(bx, by + bh - 1, bx + bw, by + bh, 0xFF444444);
                guiGraphics.fill(bx, by, bx + 1, by + bh, 0xFF444444);
                guiGraphics.fill(bx + bw - 1, by, bx + bw, by + bh, 0xFF444444);

                boolean hovered = mouseX >= x && mouseX <= x + gridSpacingX &&
                                  mouseY >= y && mouseY <= y + gridSpacingY;
                if (hovered) {
                    guiGraphics.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, 0x44AAFF88);
                }

                ItemStack stack = entry.createStack();
                guiGraphics.renderFakeItem(stack, x + (gridSpacingX - 16) / 2, y + (gridSpacingY - 16) / 2);

                int qty = entry.quantity();
                if (qty > 1) {
                    String qtyStr = String.valueOf(qty);
                    int qtyX = x + (gridSpacingX + 16) / 2 - font.width(qtyStr) + 7;
                    int qtyY = y + (gridSpacingY + 16) / 2 - 4;
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(0, 0, 200);
                    guiGraphics.pose().translate(qtyX, qtyY, 0);
                    guiGraphics.pose().scale(0.6f, 0.6f, 1.0f);
                    guiGraphics.drawString(this.font, qtyStr, 0, 0, 0xFFFFFF);
                    guiGraphics.pose().popPose();
                }

                String tradeStr;
                if ("barter".equals(entry.entryType()) && entry.barterItemId() != null) {
                    tradeStr = "以物易物";
                } else {
                    tradeStr = entry.tradeType() == ShopEntry.TradeType.BUY ? "购买" : "出售";
                }
                guiGraphics.pose().pushPose();
                float tradeScale = 0.85f;
                int tradeX = x + (gridSpacingX - (int)(font.width(tradeStr) * tradeScale)) / 2;
                int tradeY = y + 2;
                guiGraphics.pose().translate(tradeX, tradeY, 0);
                guiGraphics.pose().scale(tradeScale, tradeScale, 1.0f);
                guiGraphics.drawString(this.font, tradeStr, 0, 0, 0xFFFFAA);
                guiGraphics.pose().popPose();

                String bottomStr;
                if ("barter".equals(entry.entryType()) && entry.barterItemId() != null) {
                    ItemStack offerStack = entry.createBarterOfferStack();
                    bottomStr = offerStack.isEmpty() ? "?" : offerStack.getHoverName().getString();
                } else {
                    bottomStr = "¥" + entry.price();
                }
                FontUtil.drawAutoFitCentered(guiGraphics, this.font, bottomStr, x + gridSpacingX / 2, y + gridSpacingY - 10, gridSpacingX - 4, 0x88FF88);
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
        int maxScroll = Math.max(0, totalSlots - visibleSlots);
        if (maxScroll <= 0) return;
        int trackTop = gridStartY;
        int trackH = GRID_ROWS * gridSpacingY;
        int sbX = left + leftPanelW + 14 + GRID_COLS * gridSpacingX + 3;
        gui.fill(sbX, trackTop, sbX + 3, trackTop + trackH, 0x80333333);
        int thumbH = Math.max(6, trackH * visibleSlots / totalSlots);
        int thumbY = trackTop + (trackH - thumbH) * scrollOffset / maxScroll;
        gui.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, 0xCCAAAAAA);
    }

    private void renderAddSlot(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        int gap = 2;
        int bx = x + gap;
        int by = y + gap;
        int bw = gridSpacingX - gap * 2;
        int bh = gridSpacingY - gap * 2;
        boolean hovered = mouseX >= x && mouseX <= x + gridSpacingX &&
                          mouseY >= y && mouseY <= y + gridSpacingY;
        int bgColor = hovered ? 0xAA44AA44 : 0x88333333;
        guiGraphics.fill(bx, by, bx + bw, by + 1, 0x66444444);
        guiGraphics.fill(bx, by + bh - 1, bx + bw, by + bh, 0x66444444);
        guiGraphics.fill(bx, by, bx + 1, by + bh, 0x66444444);
        guiGraphics.fill(bx + bw - 1, by, bx + bw, by + bh, 0x66444444);
        guiGraphics.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, bgColor);

        Component plus = Component.literal("+");
        int pw = this.font.width(plus);
        guiGraphics.drawString(this.font, plus, x + (gridSpacingX - pw) / 2, y + (gridSpacingY - 9) / 2, 0x88FF88);
    }

    private void renderItemTooltips(GuiGraphics guiGraphics, int left, int top, int mouseX, int mouseY) {
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

        int borderCol = 0x55FFD700;
        guiGraphics.fill(balX + 2, balY, balX + balW - 2, balY + 1, borderCol);
        guiGraphics.fill(balX + 2, balY + balH - 1, balX + balW - 2, balY + balH, borderCol);
        guiGraphics.fill(balX, balY + 2, balX + 1, balY + balH - 2, borderCol);
        guiGraphics.fill(balX + balW - 1, balY + 2, balX + balW, balY + balH - 2, borderCol);

        Component label = Component.translatable("gui.shop.balance");
        int labelW = this.font.width(label);
        guiGraphics.drawString(this.font, label, left + (leftPanelW - labelW) / 2, balY + 3, 0xFFD700);

        Component amount = Component.literal("¥" + String.format("%,d", balance));
        FontUtil.drawAutoFitCentered(guiGraphics, this.font, amount.getString(), left + leftPanelW / 2, balY + 15, leftPanelW - 12, 0xFFFFFF);
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
                            openTradeConfirm(entry);
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
        int itemMaxScroll = Math.max(0, totalSlots2 - visibleSlots);
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
        gui.fill(menuX, menuY, menuX + menuW, menuY + menuH, 0xCC222222);
        gui.fill(menuX, menuY, menuX + menuW, menuY + 1, 0xFF888888);
        gui.fill(menuX, menuY + menuH - 1, menuX + menuW, menuY + menuH, 0xFF888888);
        gui.fill(menuX, menuY, menuX + 1, menuY + menuH, 0xFF888888);
        gui.fill(menuX + menuW - 1, menuY, menuX + menuW, menuY + menuH, 0xFF888888);
        for (int i = 0; i < options.size(); i++) {
            int optY = menuY + 2 + i * optH;
            boolean hovered = mouseX >= menuX && mouseX <= menuX + menuW &&
                              mouseY >= optY && mouseY <= optY + optH;
            if (hovered) {
                gui.fill(menuX + 1, optY, menuX + menuW - 1, optY + optH, 0x444488FF);
            }
            FontUtil.drawAutoFitText(gui, font, options.get(i), menuX + 6, optY + 2, menuW - 12, 0xFFFFFF);
        }
    }

    private void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + h, color);
        g.fill(x, y + 2, x + w, y + h - 2, color);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
            return true;
        }
        if (contextMenuOpen) {
            closeContextMenu();
            return true;
        }
        if (keyCode == 256) {
            if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
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
            int maxScroll = Math.max(0, total + extraSlot - GRID_COLS * GRID_ROWS);
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
        closeContextMenu();
        this.categories = newCategories;
        if (currentCategory == null || category.equals(currentCategory)) {
            this.currentCategory = category;
            this.allEntries = entries;
            scrollOffset = 0;
        }
        this.balance = balance;
        categoryCache.put(category, entries);
        filterEntries();
        JeiBridge.setSellBoxRecipes(category, entries);
    }

    public void refreshFromConfig() {
        closeContextMenu();
        if (currentCategory == null || !categories.contains(currentCategory)) {
            currentCategory = categories.isEmpty() ? null : categories.get(0);
        }
        if (currentCategory != null) {
            sendOpenRequest();
        }
    }
}