package com.sellbox.mod.playershop;

import com.sellbox.mod.network.ModPayloads;
import com.sellbox.mod.shop.ShopButton;
import com.sellbox.mod.shop.ShopEntry;
import com.sellbox.mod.util.PinyinUtil;
import com.sellbox.mod.util.FontUtil;
import com.sellbox.mod.playershop.network.PlayerShopOpenPayload;
import com.sellbox.mod.playershop.network.PlayerShopNamePayload;
import com.sellbox.mod.util.ItemIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerShopScreen extends Screen {
    private final com.sellbox.mod.gui.GuiTheme T = com.sellbox.mod.gui.GuiTheme.get();
    private final BlockPos marketPos;
    private int imageWidth = 420;
    private int imageHeight = 260;
    private int leftPanelW = 100;
    private int shopEntryHeight = 14;

    private Map<UUID, PlayerShopConfig.PlayerShopInfo> allShops = new HashMap<>();
    private Map<UUID, String> ownerNames = new HashMap<>();
    private Map<UUID, Map<String, Integer>> allStockCounts = new HashMap<>();
    private Set<UUID> allUnloadedOwners = new HashSet<>();
    private List<UUID> sortedShopKeys = new ArrayList<>();
    private UUID currentShopOwner;
    private List<ShopEntry> allEntries;
    private List<ShopEntry> filteredEntries;
    private int balance;

    private String searchText = "";
    private boolean searchFocused;
    private int scrollOffset = 0;
    private int shopListScrollOffset = 0;
    private boolean isDraggingCatScrollbar = false;
    private int catDragStartMouseY, catDragStartOffset;
    private static final int GRID_COLS = 8;
    private static final int GRID_ROWS = 4;
    private int gridSpacingX = 44;
    private int gridSpacingY = 48;
    private int topOffset = 0;

    private boolean contextMenuOpen = false;
    private ShopEntry contextMenuEntry = null;
    private int contextMenuIndex = -1;
    private int contextMenuX = 0;
    private int contextMenuY = 0;

    private EditBox shopNameBox;
    private boolean shopNameBoxFocused;
    private ShopButton shopNameConfirmBtn;
    private ShopButton shopNameClearBtn;
    private int nameBoxX, nameBoxY, nameBoxW, nameBoxH, nameBoxInnerPad;

    public PlayerShopScreen(BlockPos marketPos) {
        super(Component.translatable("gui.sellbox.player_shop"));
        this.marketPos = marketPos;
    }

    @Override
    protected void init() {
        super.init();
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        if (this.font == null) this.font = Minecraft.getInstance().font;

        // 基础布局尺寸（8列 x 4行）
        int baseGridW = GRID_COLS * (44 + 2);
        int baseGridH = GRID_ROWS * 48;
        int baseWidth = 100 + 14 + baseGridW + 12;
        int baseHeight = 38 + baseGridH + 14 + 24;

        // 自适应缩放：优先按屏幕高度缩放，若宽度不足则进一步缩小
        double scale = Math.min((double) height / (baseHeight + 48), (double) width / (baseWidth + 16));
        scale = Math.max(0.55, Math.min(2.5, scale));

        leftPanelW = Math.max(60, (int) Math.round(100 * scale));
        shopEntryHeight = Math.max(8, (int) Math.round(14 * scale));
        gridSpacingX = Math.max(20, (int) Math.round(44 * scale)) + 2;
        gridSpacingY = Math.max(24, (int) Math.round(48 * scale)) + 11;
        imageWidth = Math.max(200, leftPanelW + 14 + GRID_COLS * gridSpacingX + 12);
        imageHeight = Math.max(160, 38 + GRID_ROWS * gridSpacingY + 14);

        int centeredTop = (height - imageHeight) / 2;
        topOffset = Math.min(centeredTop, Math.max(5, height - imageHeight - 48));

        int left = (this.width - imageWidth) / 2;
        int top = topOffset;

        // 命名框：位置/尺寸随界面缩放计算，绘制与控件共用同一组字段
        nameBoxW = Math.max(60, gridSpacingX * 3 - 4);
        nameBoxH = Math.max(font.lineHeight + 4, (int) Math.round(16 * scale));
        nameBoxX = left + leftPanelW + 14;
        nameBoxY = top + imageHeight - nameBoxH - 6;
        nameBoxInnerPad = Math.max(2, (int) Math.round(3 * scale));

        int nameTextY = nameBoxY + (nameBoxH - font.lineHeight) / 2;
        shopNameBox = new EditBox(font, nameBoxX + nameBoxInnerPad, nameTextY,
                nameBoxW - nameBoxInnerPad * 2, font.lineHeight,
                Component.translatable("gui.sellbox.shop_name_hint"));
        shopNameBox.setBordered(false);
        shopNameBox.setMaxLength(32);
        shopNameBoxFocused = false;
        addRenderableWidget(shopNameBox);
        shopNameBox.setVisible(false);

        int confirmBtnW = Math.max(30, (int) Math.round(40 * scale));
        int confirmBtnH = nameBoxH;
        int confirmBtnX = nameBoxX + nameBoxW + (int) Math.round(4 * scale);
        int confirmBtnY = nameBoxY;
        shopNameConfirmBtn = new ShopButton(confirmBtnX, confirmBtnY, confirmBtnW, confirmBtnH,
                Component.translatable("gui.sellbox.lock_name"), btn -> confirmSetShopName());
        shopNameConfirmBtn.visible = false;
        shopNameConfirmBtn.setTooltip(Tooltip.create(Component.translatable("gui.sellbox.lock_name_tooltip")));
        addRenderableWidget(shopNameConfirmBtn);

        int clearBtnX = confirmBtnX + confirmBtnW + (int) Math.round(2 * scale);
        shopNameClearBtn = new ShopButton(clearBtnX, confirmBtnY, confirmBtnW, confirmBtnH,
                Component.translatable("gui.sellbox.clear_name"), btn -> clearSetShopName());
        shopNameClearBtn.visible = false;
        shopNameClearBtn.setTooltip(Tooltip.create(Component.translatable("gui.sellbox.clear_name_tooltip")));
        addRenderableWidget(shopNameClearBtn);

        requestAllShopData();
    }

    private void requestAllShopData() {
        PacketDistributor.sendToServer(new PlayerShopOpenPayload(marketPos));
    }

    private String getShopCurrentName() {
        if (currentShopOwner == null) return "";
        var info = allShops.get(currentShopOwner);
        return info != null && info.shopName() != null ? info.shopName() : "";
    }

    private boolean isMyShop() {
        UUID myUuid = minecraft.player != null ? minecraft.player.getUUID() : null;
        if (myUuid != null && myUuid.equals(currentShopOwner)) return true;
        if (com.sellbox.mod.network.ModPayloads.clientAdminEdit
                && minecraft.player != null && minecraft.player.hasPermissions(2)
                && currentShopOwner != null) return true;
        return false;
    }

    private UUID getEditTargetUuid() {
        UUID myUuid = minecraft.player != null ? minecraft.player.getUUID() : null;
        if (myUuid != null && myUuid.equals(currentShopOwner)) return null;
        if (com.sellbox.mod.network.ModPayloads.clientAdminEdit
                && minecraft.player != null && minecraft.player.hasPermissions(2)
                && currentShopOwner != null) return currentShopOwner;
        return null;
    }

    public void updateShopData(Map<UUID, PlayerShopConfig.PlayerShopInfo> shops, Map<UUID, Map<String, Integer>> stockCounts, Set<UUID> unloadedOwners) {
        allShops.clear();
        allShops.putAll(shops);
        allStockCounts.clear();
        allStockCounts.putAll(stockCounts);
        allUnloadedOwners.clear();
        allUnloadedOwners.addAll(unloadedOwners);

        UUID myUuid = minecraft.player != null ? minecraft.player.getUUID() : null;

        sortedShopKeys.clear();
        List<UUID> named = new ArrayList<>();
        List<UUID> unnamed = new ArrayList<>();

        for (var entry : allShops.entrySet()) {
            if (entry.getKey().equals(myUuid)) continue;
            if (entry.getValue() != null && entry.getValue().shopName() != null && !entry.getValue().shopName().isEmpty()) {
                named.add(entry.getKey());
            } else {
                unnamed.add(entry.getKey());
            }
        }

        named.sort(Comparator.comparing(uuid -> {
            String name = allShops.get(uuid).shopName();
            if (name == null || name.isEmpty()) return "\uffff";
            String firstChar = name.substring(0, 1);
            String py = PinyinUtil.toPinyin(firstChar);
            return py.isEmpty() ? firstChar.toLowerCase() : py;
        }));

        unnamed.sort(Comparator.comparing(uuid -> {
            String name = getPlayerName(uuid);
            return name.toLowerCase();
        }));

        if (myUuid != null && allShops.containsKey(myUuid)) {
            sortedShopKeys.add(myUuid);
        }
        sortedShopKeys.addAll(named);
        sortedShopKeys.addAll(unnamed);

        if (currentShopOwner == null && !sortedShopKeys.isEmpty()) {
            currentShopOwner = sortedShopKeys.get(0);
        }

        refreshCurrentEntries();
        boolean mine = isMyShop();
        if (shopNameBox != null) {
            shopNameBox.setValue(getShopCurrentName());
            shopNameBox.setVisible(mine);
        }
        if (shopNameConfirmBtn != null) {
            shopNameConfirmBtn.visible = mine;
        }
        if (shopNameClearBtn != null) {
            shopNameClearBtn.visible = mine;
        }
    }

    private void refreshCurrentEntries() {
        if (currentShopOwner != null && allShops.containsKey(currentShopOwner)) {
            var info = allShops.get(currentShopOwner);
            allEntries = info != null ? new ArrayList<>(info.items()) : new ArrayList<>();
        } else {
            allEntries = new ArrayList<>();
        }
        filterEntries();
    }

    public void updateData(Map<UUID, String> shopNames, Map<UUID, List<ShopEntry>> shopItems, Map<UUID, Map<String, Integer>> stockCounts, Set<UUID> unloadedOwners, Map<UUID, String> ownerNames) {
        allStockCounts.clear();
        allStockCounts.putAll(stockCounts);
        allUnloadedOwners.clear();
        allUnloadedOwners.addAll(unloadedOwners);
        this.ownerNames.clear();
        this.ownerNames.putAll(ownerNames);

        Map<UUID, PlayerShopConfig.PlayerShopInfo> shops = new HashMap<>();
        for (var entry : shopItems.entrySet()) {
            UUID uuid = entry.getKey();
            String name = shopNames.getOrDefault(uuid, "");
            shops.put(uuid, new PlayerShopConfig.PlayerShopInfo(name, entry.getValue()));
        }
        updateShopData(shops, stockCounts, unloadedOwners);
    }

    public void updateBalance(int bal) {
        this.balance = bal;
    }

    private void selectShop(UUID uuid) {
        currentShopOwner = uuid;
        scrollOffset = 0;
        searchText = "";
        searchFocused = false;
        refreshCurrentEntries();
        boolean mine = isMyShop();
        if (shopNameBox != null) {
            shopNameBox.setValue(getShopCurrentName());
            shopNameBox.setVisible(mine);
            shopNameBox.setFocused(false);
        }
        if (shopNameConfirmBtn != null) {
            shopNameConfirmBtn.visible = mine;
        }
        if (shopNameClearBtn != null) {
            shopNameClearBtn.visible = mine;
        }
    }

    private String getPlayerName(UUID uuid) {
        if (minecraft.player != null && minecraft.player.getUUID().equals(uuid)) {
            return minecraft.player.getName().getString();
        }
        if (minecraft.level != null) {
            var player = minecraft.level.getPlayerByUUID(uuid);
            if (player != null) return player.getName().getString();
        }
        String cached = ownerNames.get(uuid);
        if (cached != null && !cached.isEmpty()) return cached;
        return uuid.toString().substring(0, 8);
    }

    private void confirmSetShopName() {
        if (shopNameBox == null) return;
        if (!isMyShop()) return;
        String name = shopNameBox.getValue().trim();
        PacketDistributor.sendToServer(new PlayerShopNamePayload(name, marketPos));
    }

    private void clearSetShopName() {
        if (shopNameBox == null) return;
        if (!isMyShop()) return;
        shopNameBox.setValue("");
        PacketDistributor.sendToServer(new PlayerShopNamePayload("", marketPos));
    }

    BlockPos getMarketPos() {
        return marketPos;
    }

    public void refreshFromConfig() {
        requestAllShopData();
    }

    private void filterEntries() {
        String lower = searchText.toLowerCase(Locale.ROOT);
        if (searchText.isEmpty()) {
            filteredEntries = allEntries != null ? new ArrayList<>(allEntries) : new ArrayList<>();
        } else {
            filteredEntries = new ArrayList<>();
            if (allEntries != null) {
                for (ShopEntry entry : allEntries) {
                    String name = entry.getDisplayName().getString();
                    if (PinyinUtil.matches(name, lower) || entry.itemId().toString().contains(lower)) {
                        filteredEntries.add(entry);
                    }
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int left = (this.width - imageWidth) / 2;
        int top = topOffset;

        renderSearchBorder(guiGraphics, left, top);
        renderShopList(guiGraphics, left, top, mouseX, mouseY);
        renderItems(guiGraphics, left, top, mouseX, mouseY);
        renderBalance(guiGraphics, left, top, mouseX, mouseY);
        renderEditModeIndicator(guiGraphics, left, top);
        renderItemTooltips(guiGraphics, left, top, mouseX, mouseY);
        renderShopNameArea(guiGraphics, left, top, mouseX, mouseY);
        if (contextMenuOpen) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 300);
            renderContextMenu(guiGraphics, mouseX, mouseY);
            guiGraphics.pose().popPose();
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
    }

    public void renderBackground(GuiGraphics guiGraphics) {
        int left = (this.width - imageWidth) / 2;
        int top = topOffset;

        guiGraphics.fill(0, 0, this.width, this.height, T.screenBackground);
        fillRoundedRect(guiGraphics, left, top, imageWidth, imageHeight, T.mainPanelBg);
        fillRoundedRect(guiGraphics, left, top, leftPanelW, imageHeight, T.leftPanelBg);
        guiGraphics.fill(left + leftPanelW, top + 2, left + leftPanelW + 1, top + imageHeight - 2, T.panelDivider);
    }

    private void renderSearchBorder(GuiGraphics guiGraphics, int left, int top) {
        int sx = left + leftPanelW + 10;
        int sy = top + 11;
        int sw = imageWidth - leftPanelW - 24;
        int sh = 20;
        int col = searchFocused ? T.inputBorderFocused : T.inputBorderUnfocused;
        guiGraphics.fill(sx + 2, sy, sx + sw - 2, sy + 1, col);
        guiGraphics.fill(sx + 2, sy + sh - 1, sx + sw - 2, sy + sh, col);
        guiGraphics.fill(sx, sy + 2, sx + 1, sy + sh - 2, col);
        guiGraphics.fill(sx + sw - 1, sy + 2, sx + sw, sy + sh - 2, col);

        if (searchText.isEmpty() && !searchFocused) {
            FontUtil.drawAutoFitText(guiGraphics, font, Component.translatable("gui.sellbox.search_hint").getString(), sx + 4, sy + 4, sw - 8, 0x777777);
        } else if (!searchText.isEmpty()) {
            FontUtil.drawAutoFitText(guiGraphics, font, searchText, sx + 4, sy + 4, sw - 8, 0xFFFFFF);
        }

        if (searchFocused) {
            int cursorX = sx + 4 + (searchText.isEmpty() ? 0 : font.width(searchText));
            guiGraphics.fill(cursorX, sy + 3, cursorX + 1, sy + sh - 3, 0xFFFFFFFF);
        }
    }

    private void renderEditModeIndicator(GuiGraphics guiGraphics, int left, int top) {
        UUID myUuid = minecraft.player != null ? minecraft.player.getUUID() : null;
        if (myUuid != null && myUuid.equals(currentShopOwner)) {
            Component editLabel = Component.translatable("gui.sellbox.my_shop");
            int labW = this.font.width(editLabel);
            guiGraphics.drawString(this.font, editLabel, left + imageWidth - labW - 6, top + imageHeight - 14, 0xFFFFAA00);
        }
    }

    private void renderShopList(GuiGraphics guiGraphics, int left, int top, int mouseX, int mouseY) {
        Component title = Component.translatable("gui.sellbox.shop_list");
        int titleW = this.font.width(title);
        float titleScale = 1.3f;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(left + (leftPanelW - titleW * titleScale) / 2, top + 13, 0);
        guiGraphics.pose().scale(titleScale, titleScale, 1.0f);
        guiGraphics.drawString(this.font, title, 0, 0, T.catTitleText);
        guiGraphics.pose().popPose();

        int catStartY = top + 40;
        int catEndY = top + imageHeight - 60;
        int available = catEndY - catStartY;
        int maxVisible = Math.max(1, available / (shopEntryHeight + 4));

        for (int i = shopListScrollOffset; i < sortedShopKeys.size() && i < shopListScrollOffset + maxVisible; i++) {
            int localIdx = i - shopListScrollOffset;
            int y = catStartY + localIdx * (shopEntryHeight + 4);
            UUID shopUuid = sortedShopKeys.get(i);
            int bgX = left + 5;
            int bgY = y - 1;
            int bgW = leftPanelW - 10;
            int bgH = shopEntryHeight + 2;

            boolean isSearching = !searchText.isEmpty();
            boolean hovered = mouseX >= bgX && mouseX <= bgX + bgW &&
                              mouseY >= bgY && mouseY <= bgY + bgH;
            UUID myUuid = minecraft.player != null ? minecraft.player.getUUID() : null;
            boolean selected = shopUuid.equals(currentShopOwner) && !isSearching;

            int borderColor = 0xFF4488FF;
            guiGraphics.fill(bgX + 2, bgY, bgX + bgW - 2, bgY + 1, borderColor);
            guiGraphics.fill(bgX + 2, bgY + bgH - 1, bgX + bgW - 2, bgY + bgH, borderColor);
            guiGraphics.fill(bgX, bgY + 2, bgX + 1, bgY + bgH - 2, borderColor);
            guiGraphics.fill(bgX + bgW - 1, bgY + 2, bgX + bgW, bgY + bgH - 2, borderColor);

            if (selected) {
                guiGraphics.fill(bgX + 2, bgY + 1, bgX + bgW - 2, bgY + bgH - 1, T.catSelectedFill);
            } else if (hovered) {
                guiGraphics.fill(bgX + 2, bgY + 1, bgX + bgW - 2, bgY + bgH - 1, T.catHoveredFill);
            }

            var info = allShops.get(shopUuid);
            String label;
            int color;
            if (myUuid != null && shopUuid.equals(myUuid)) {
                label = Component.translatable("gui.sellbox.my_shop").getString();
                color = T.myShopLabel;
            } else {
                if (info != null && info.shopName() != null && !info.shopName().isEmpty()) {
                    label = info.shopName();
                } else {
                    label = getPlayerName(shopUuid);
                }
                color = T.catText;
            }

            int textY = bgY + (bgH - this.font.lineHeight) / 2;
            FontUtil.drawAutoFitCentered(guiGraphics, this.font, label, bgX + bgW / 2, textY, bgW - 4, color);

            if (hovered && myUuid != null) {
                String ownerLabel = "店主：" + getPlayerName(shopUuid);
                guiGraphics.renderTooltip(this.font, Component.literal(ownerLabel), mouseX, mouseY);
            }
        }

        renderShopListScrollbar(guiGraphics, left, catStartY, available, maxVisible);
    }

    private void renderShopListScrollbar(GuiGraphics gui, int left, int trackTop, int trackH, int visibleCount) {
        int maxScroll = Math.max(0, sortedShopKeys.size() - visibleCount);
        if (maxScroll <= 0) return;
        int sbX = left + leftPanelW - 2;
        gui.fill(sbX, trackTop, sbX + 1, trackTop + trackH, T.scrollbarTrack);
        int thumbH = Math.max(6, trackH * visibleCount / Math.max(1, sortedShopKeys.size()));
        int thumbY = trackTop + (trackH - thumbH) * shopListScrollOffset / maxScroll;
        gui.fill(sbX, thumbY, sbX + 1, thumbY + thumbH, T.scrollbarThumb);
    }

    private void renderItems(GuiGraphics guiGraphics, int left, int top, int mouseX, int mouseY) {
        List<ShopEntry> display = filteredEntries != null ? filteredEntries : allEntries;
        if (display == null) return;

        int gridStartX = left + leftPanelW + 14;
        int gridStartY = top + 38;
        int totalItems = display.size();
        int extraSlot = isMyShop() ? 1 : 0;
        int totalSlots = totalItems + extraSlot;
        int endIndex = Math.min(scrollOffset + GRID_COLS * GRID_ROWS, totalSlots);

        if (!isMyShop() && totalSlots == 0) {
            FontUtil.drawAutoFitText(guiGraphics, this.font, Component.translatable("gui.sellbox.select_shop_prompt").getString(),
                    left + leftPanelW + 20, top + 50, imageWidth - leftPanelW - 40, 0x888888);
            return;
        }

        if (totalSlots == 0 && isMyShop()) {
            FontUtil.drawAutoFitText(guiGraphics, this.font, Component.translatable("gui.sellbox.add_item_prompt").getString(),
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
                guiGraphics.fill(bx, by, bx + bw, by + 1, T.cellBorder);
                guiGraphics.fill(bx, by + bh - 1, bx + bw, by + bh, T.cellBorder);
                guiGraphics.fill(bx, by, bx + 1, by + bh, T.cellBorder);
                guiGraphics.fill(bx + bw - 1, by, bx + bw, by + bh, T.cellBorder);

                boolean hovered = mouseX >= x && mouseX <= x + gridSpacingX &&
                                  mouseY >= y && mouseY <= y + gridSpacingY;
                // 库存染色：有货绿、无货红（只在悬停时染色图标）
                int stock = getStockForEntry(shopOwner(), entry);
                int stainColor = stock > 0 ? 0x5044FF44 : 0x50FF4444;
                if (hovered) {
                    guiGraphics.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, stainColor);
                }

                ItemStack stack = (minecraft.level != null)
                        ? entry.createStack(minecraft.level.registryAccess()) : entry.createStack();
                ItemIcons.render(guiGraphics, stack, x + (gridSpacingX - 16) / 2, y + (gridSpacingY - 16) / 2);

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

                boolean barter = "barter".equals(entry.entryType()) && entry.barterItemId() != null;
                String tradeStr;
                if (barter) {
                    tradeStr = Component.translatable("gui.sellbox.barter_trade").getString();
                } else if (isMyShop()) {
                    tradeStr = Component.translatable("gui.sellbox.sell_label").getString();
                } else {
                    tradeStr = Component.translatable("gui.sellbox.buy_label").getString();
                }
                guiGraphics.pose().pushPose();
                float tradeScale = 0.85f;
                int tradeX = x + (gridSpacingX - (int)(font.width(tradeStr) * tradeScale)) / 2;
                int tradeY = y + 2;
                guiGraphics.pose().translate(tradeX, tradeY, 0);
                guiGraphics.pose().scale(tradeScale, tradeScale, 1.0f);
                guiGraphics.drawString(this.font, tradeStr, 0, 0, 0xFFFFAA);
                guiGraphics.pose().popPose();

                String priceStr;
                if (barter) {
                    ItemStack offerStack = (minecraft.level != null)
                            ? entry.createBarterOfferStack(minecraft.level.registryAccess()) : entry.createBarterOfferStack();
                    priceStr = offerStack.isEmpty() ? "?" : offerStack.getHoverName().getString();
                } else {
                    priceStr = "¥" + entry.price();
                }
                FontUtil.drawAutoFitCentered(guiGraphics, this.font, priceStr, x + gridSpacingX / 2, y + gridSpacingY - 10, gridSpacingX - 4, T.textPrice);
            }
        }

        renderItemScrollbar(guiGraphics, left, top, gridStartY);
    }

    private UUID shopOwner() { return currentShopOwner; }

    private int getStockForEntry(UUID owner, ShopEntry entry) {
        if (owner == null) return 0;
        Map<String, Integer> stocks = allStockCounts.get(owner);
        if (stocks == null) return 0;
        boolean isBarter = entry.isBarter();
        boolean strict = isBarter ? entry.barterGoodsStrict() : entry.strictMatch();
        if (!strict) {
            // 宽松（仅 ID）条目：聚合所有同 ID 库存
            Integer stock = stocks.get(entry.itemId().toString());
            return stock != null ? stock : 0;
        }
        // 严格条目：用条目模板（带 NBT）的键精确查询库存
        if (minecraft.level == null) return 0;
        var registries = minecraft.level.registryAccess();
        ItemStack template = isBarter ? entry.createBarterGoodsStack(registries) : entry.createStack(registries);
        String snbt = com.sellbox.mod.util.ItemVariantUtil.brewNbtSnbt(template, registries);
        Integer stock = stocks.get(entry.itemId().toString() + "|" + (snbt != null ? snbt : "{}"));
        return stock != null ? stock : 0;
    }

    private void renderItemScrollbar(GuiGraphics gui, int left, int top, int gridStartY) {
        List<ShopEntry> display = filteredEntries != null ? filteredEntries : allEntries;
        int totalItems = display != null ? display.size() : 0;
        int extraSlot = isMyShop() ? 1 : 0;
        int totalSlots = totalItems + extraSlot;
        int visibleSlots = GRID_COLS * GRID_ROWS;
        // 按整行计算最大滚动量，与 mouseScrolled 保持一致
        int maxRow = Math.max(0, (int) Math.ceil((double) totalSlots / GRID_COLS) - GRID_ROWS);
        int maxScroll = maxRow * GRID_COLS;
        if (maxScroll <= 0) return;
        int trackTop = gridStartY;
        int trackH = GRID_ROWS * gridSpacingY;
        int sbX = left + leftPanelW + 14 + GRID_COLS * gridSpacingX + 3;
        gui.fill(sbX, trackTop, sbX + 3, trackTop + trackH, T.scrollbarTrack);
        int thumbH = Math.max(6, trackH * GRID_ROWS / Math.max(1, maxRow + GRID_ROWS));
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
        int bgColor = hovered ? 0xAA44AA44 : 0x88333333;
        guiGraphics.fill(bx, by, bx + bw, by + 1, 0x66444444);
        guiGraphics.fill(bx, by + bh - 1, bx + bw, by + bh, 0x66444444);
        guiGraphics.fill(bx, by, bx + 1, by + bh, 0x66444444);
        guiGraphics.fill(bx + bw - 1, by, bx + bw, by + bh, 0x66444444);
        guiGraphics.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, bgColor);

        Component plus = Component.literal("+");
        int pw = this.font.width(plus);
        guiGraphics.drawString(this.font, plus, x + (gridSpacingX - pw) / 2, y + (gridSpacingY - 9) / 2, T.addSlotText);
    }

    private void renderItemTooltips(GuiGraphics guiGraphics, int left, int top, int mouseX, int mouseY) {
        List<ShopEntry> display = filteredEntries != null ? filteredEntries : allEntries;
        if (display == null) return;
        int gridStartX = left + leftPanelW + 14;
        int gridStartY = top + 38;
        int totalItems = display.size();
        int extraSlot = isMyShop() ? 1 : 0;
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
                    guiGraphics.renderTooltip(this.font, Component.translatable("gui.sellbox.add_item_tooltip"), mouseX, mouseY);
                } else {
                    ShopEntry entry = display.get(i);
                    ItemStack stack = (minecraft.level != null)
                            ? entry.createStack(minecraft.level.registryAccess()) : entry.createStack();
                    String itemName = entry.getDisplayName().getString();
                    String preview;
                    boolean isBarter = "barter".equals(entry.entryType()) && entry.barterItemId() != null;
                    if (isBarter) {
                        ItemStack offerStack = (minecraft.level != null)
                                ? entry.createBarterOfferStack(minecraft.level.registryAccess()) : entry.createBarterOfferStack();
                        String offerName = offerStack.isEmpty() ? "?" : offerStack.getHoverName().getString();
                        int offerQ = Math.max(1, entry.barterItemQty());
                        int goodsQ = Math.max(1, entry.quantity());
                        preview = offerName + " x" + offerQ + " → " + itemName + " x" + goodsQ;
                    } else {
                        int qty = entry.quantity();
                        preview = "¥" + entry.price() + " → " + itemName + (qty > 1 ? " x" + qty : "");
                    }
                    java.util.List<Component> lines = new java.util.ArrayList<>(java.util.List.of(
                            entry.getDisplayName(),
                            Component.literal(preview).withStyle(net.minecraft.ChatFormatting.GOLD)));
                    int stock = getStockForEntry(currentShopOwner, entry);
                    boolean hasUnloaded = allUnloadedOwners.contains(currentShopOwner);
                    lines.add(Component.translatable("gui.sellbox.stock_tooltip", stock).withStyle(net.minecraft.ChatFormatting.AQUA));
                    if (stock > 0) {
                        lines.add(Component.literal("处于加载区块可购买").withStyle(net.minecraft.ChatFormatting.GREEN));
                    }
                    if (hasUnloaded) {
                        lines.add(Component.literal("处于未加载区块无法购买").withStyle(net.minecraft.ChatFormatting.RED));
                    }
                    if (stock <= 0 && !hasUnloaded) {
                        lines.add(Component.translatable("gui.sellbox.no_stock").withStyle(net.minecraft.ChatFormatting.RED));
                    }
                    guiGraphics.renderTooltip(this.font, lines, java.util.Optional.empty(), mouseX, mouseY);
                }
                return;
            }
        }
    }

    private void renderBalance(GuiGraphics guiGraphics, int left, int top, int mouseX, int mouseY) {
        int bal = ModPayloads.clientBalance;
        if (bal < 0) bal = balance;

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

        Component amount = Component.literal("¥" + String.format("%,d", bal));
        FontUtil.drawAutoFitCentered(guiGraphics, this.font, amount.getString(), left + leftPanelW / 2, balY + 15, leftPanelW - 12, 0xFFFFFF);
    }

    private void renderShopNameArea(GuiGraphics guiGraphics, int left, int top, int mouseX, int mouseY) {
        if (isMyShop() && shopNameBox != null && nameBoxW > 0) {
            boolean focused = shopNameBox.isFocused();
            int col = focused ? 0x55FFFFFF : 0x55AAAAAA;
            guiGraphics.fill(nameBoxX + 2, nameBoxY, nameBoxX + nameBoxW - 2, nameBoxY + 1, col);
            guiGraphics.fill(nameBoxX + 2, nameBoxY + nameBoxH - 1, nameBoxX + nameBoxW - 2, nameBoxY + nameBoxH, col);
            guiGraphics.fill(nameBoxX, nameBoxY + 2, nameBoxX + 1, nameBoxY + nameBoxH - 2, col);
            guiGraphics.fill(nameBoxX + nameBoxW - 1, nameBoxY + 2, nameBoxX + nameBoxW, nameBoxY + nameBoxH - 2, col);

            if (shopNameBox.getValue().isEmpty() && !focused) {
                FontUtil.drawAutoFitText(guiGraphics, font, Component.translatable("gui.sellbox.shop_name_placeholder").getString(),
                        nameBoxX + nameBoxInnerPad, nameBoxY + (nameBoxH - font.lineHeight) / 2,
                        nameBoxW - nameBoxInnerPad * 2, 0x777777);
            }
        }
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
        int maxVisible = Math.max(1, available / (shopEntryHeight + 4));

        int catSbX = left + leftPanelW - 2;
        if (mouseX >= catSbX && mouseX < catSbX + 1 && mouseY >= catStartY && mouseY < catStartY + available) {
            int catMaxScroll = Math.max(0, sortedShopKeys.size() - maxVisible);
            if (catMaxScroll > 0) {
                int thumbH = Math.max(6, available * maxVisible / Math.max(1, sortedShopKeys.size()));
                int oldThumbY = catStartY + (available - thumbH) * shopListScrollOffset / catMaxScroll;
                if (mouseY >= oldThumbY && mouseY <= oldThumbY + thumbH) {
                    isDraggingCatScrollbar = true;
                    catDragStartMouseY = (int) mouseY;
                    catDragStartOffset = shopListScrollOffset;
                } else {
                    shopListScrollOffset = (int) ((mouseY - catStartY) / (double) available * catMaxScroll);
                    shopListScrollOffset = Math.max(0, Math.min(shopListScrollOffset, catMaxScroll));
                }
            }
            return true;
        }

        for (int i = shopListScrollOffset; i < sortedShopKeys.size() && i < shopListScrollOffset + maxVisible; i++) {
            int localIdx = i - shopListScrollOffset;
            int y = catStartY + localIdx * (shopEntryHeight + 4);
            int bgX = left + 5;
            int bgY = y - 1;
            int bgW = leftPanelW - 10;
            int bgH = shopEntryHeight + 2;
            if (mouseX >= bgX && mouseX <= bgX + bgW &&
                mouseY >= bgY && mouseY <= bgY + bgH) {
                UUID clicked = sortedShopKeys.get(i);
                if (button == 0) {
                    selectShop(clicked);
                }
                return true;
            }
        }

        List<ShopEntry> display = filteredEntries != null ? filteredEntries : allEntries;
        if (display != null) {
            int gridStartX = left + leftPanelW + 14;
            int gridStartY = top + 38;
            int totalItems = display.size();
            int extraSlot = isMyShop() ? 1 : 0;
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
                            openAddItemPicker();
                        }
                    } else {
                        ShopEntry entry = display.get(i);
                        if (button == 0) {
                            openTradeConfirm(entry);
                        } else if (button == 1 && isMyShop()) {
                            int realIndex = allEntries != null ? allEntries.indexOf(entry) : -1;
                            if (realIndex >= 0) {
                                openContextMenu((int) mouseX, (int) mouseY, entry, realIndex);
                            }
                        }
                    }
                    return true;
                }
            }
        }

        int gridStartY = top + 38;
        int itemSbX = left + leftPanelW + 14 + GRID_COLS * gridSpacingX + 3;
        int itemTrackH = GRID_ROWS * gridSpacingY;
        List<ShopEntry> display2 = filteredEntries != null ? filteredEntries : allEntries;
        int totalItems2 = display2 != null ? display2.size() : 0;
        int extraSlot2 = isMyShop() ? 1 : 0;
        int totalSlots2 = totalItems2 + extraSlot2;
        int visibleSlots = GRID_COLS * GRID_ROWS;
        int itemMaxRow = Math.max(0, (int) Math.ceil((double) totalSlots2 / GRID_COLS) - GRID_ROWS);
        int itemMaxScroll = itemMaxRow * GRID_COLS;
        if (mouseX >= itemSbX && mouseX < itemSbX + 3 && mouseY >= gridStartY && mouseY < gridStartY + itemTrackH && itemMaxScroll > 0) {
            scrollOffset = (int) ((mouseY - gridStartY) / (double) itemTrackH * itemMaxScroll);
            scrollOffset = Math.max(0, Math.min(scrollOffset, itemMaxScroll));
            scrollOffset = (scrollOffset / GRID_COLS) * GRID_COLS;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void openTradeConfirm(ShopEntry entry) {
        if (currentShopOwner == null || minecraft.player == null) return;
        UUID myUuid = minecraft.player.getUUID();
        if (myUuid.equals(currentShopOwner) && !isMyShop()) return;
        minecraft.setScreen(new PlayerTradeConfirmScreen(this, entry, marketPos, currentShopOwner));
    }

    private void openAddItemPicker() {
        if (!isMyShop() || minecraft.player == null) return;
        minecraft.setScreen(new PlayerShopItemPickerScreen(this, -1, null, getEditTargetUuid()));
    }

    private void openContextMenu(int mx, int my, ShopEntry entry, int index) {
        this.contextMenuEntry = entry;
        this.contextMenuIndex = index;
        this.contextMenuX = mx;
        this.contextMenuY = my;
        this.contextMenuOpen = true;
    }

    private void closeContextMenu() {
        contextMenuOpen = false;
        contextMenuEntry = null;
        contextMenuIndex = -1;
    }

    private List<String> buildContextMenuOptions() {
        List<String> options = new ArrayList<>();
        options.add(Component.translatable("gui.sellbox.edit_item").getString());
        options.add(Component.translatable("gui.sellbox.copy").getString());
        options.add(Component.translatable("gui.sellbox.delete").getString());
        if (contextMenuIndex > 0) {
            options.add(Component.translatable("gui.sellbox.move_up").getString());
        }
        if (allEntries != null && contextMenuIndex >= 0 && contextMenuIndex < allEntries.size() - 1) {
            options.add(Component.translatable("gui.sellbox.move_down").getString());
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
        if (contextMenuEntry == null || contextMenuIndex < 0) return;
        String editLabel = Component.translatable("gui.sellbox.edit_item").getString();
        String copyLabel = Component.translatable("gui.sellbox.copy").getString();
        String delLabel = Component.translatable("gui.sellbox.delete").getString();
        String upLabel = Component.translatable("gui.sellbox.move_up").getString();
        String downLabel = Component.translatable("gui.sellbox.move_down").getString();

        if (action.equals(editLabel)) {
            minecraft.setScreen(new PlayerShopItemPickerScreen(this, contextMenuIndex, contextMenuEntry, getEditTargetUuid()));
        } else {
            String act = action.equals(copyLabel) ? "copy_item"
                    : action.equals(delLabel) ? "remove_item"
                    : action.equals(upLabel) ? "move_item_up"
                    : action.equals(downLabel) ? "move_item_down" : null;
            if (act != null) {
                UUID tgt = getEditTargetUuid();
                PacketDistributor.sendToServer(new com.sellbox.mod.playershop.network.PlayerShopEditPayload(act, contextMenuIndex,
                        contextMenuEntry.itemId(), contextMenuEntry.price(), contextMenuEntry.quantity(),
                        contextMenuEntry.customName(), marketPos, contextMenuEntry.tradeType(), null, 1, tgt));
                requestAllShopData();
            }
        }
    }

    private void renderContextMenu(GuiGraphics gui, int mouseX, int mouseY) {
        if (!contextMenuOpen || contextMenuEntry == null) return;
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
            FontUtil.drawAutoFitText(gui, font, options.get(i), menuX + 6, optY + 2, menuW - 12, 0xFFFFFF);
        }
    }

    private void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + h, color);
        g.fill(x, y + 2, x + w, y + h - 2, color);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        var mc = Minecraft.getInstance();
        var inventoryKey = mc.options.keyInventory;
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
        if (shopNameBox != null && shopNameBox.isFocused()) {
            // 命名框聚焦时 E/K 正常输入字母（charTyped），不取消聚焦、不关闭商店
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (contextMenuOpen) {
            closeContextMenu();
            return true;
        }
        // 按背包键（E）或商店键（K）时关闭商店
        if (isToggleKey) {
            this.minecraft.setScreen(null);
            return true;
        }
        if (keyCode == 256) {
            this.minecraft.setScreen(null);
            return true;
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
            int maxVisible = Math.max(1, available / (shopEntryHeight + 4));
            int catMaxScroll = Math.max(0, sortedShopKeys.size() - maxVisible);
            if (catMaxScroll > 0) {
                int thumbH = Math.max(6, available * maxVisible / Math.max(1, sortedShopKeys.size()));
                int trackH = available - thumbH;
                double delta = mouseY - catDragStartMouseY;
                int newOffset = catDragStartOffset + (int)(delta / trackH * catMaxScroll);
                shopListScrollOffset = Math.max(0, Math.min(newOffset, catMaxScroll));
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int left = (this.width - imageWidth) / 2;
        int top = topOffset;

        if (mouseX >= left && mouseX <= left + leftPanelW) {
            int catVisible = Math.max(1, (imageHeight - 60 - 55) / (shopEntryHeight + 4));
            int maxCatScroll = Math.max(0, sortedShopKeys.size() - catVisible);
            if (scrollY < 0) {
                shopListScrollOffset = Math.min(shopListScrollOffset + 1, maxCatScroll);
            } else if (scrollY > 0) {
                shopListScrollOffset = Math.max(0, shopListScrollOffset - 1);
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
            int extraSlot = isMyShop() ? 1 : 0;
            int totalSlots = total + extraSlot;
            // 按整行计算最大滚动量，保证每行位置锁死、整行滚动
            int maxRow = Math.max(0, (int) Math.ceil((double) totalSlots / GRID_COLS) - GRID_ROWS);
            int maxScroll = maxRow * GRID_COLS;
            if (scrollY < 0) {
                scrollOffset = Math.min(scrollOffset + GRID_COLS, maxScroll);
            } else if (scrollY > 0) {
                scrollOffset = Math.max(0, scrollOffset - GRID_COLS);
            }
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    void onNamePayloadHandled(UUID owner, String newName) {
        var info = allShops.get(owner);
        if (info != null) {
            allShops.put(owner, new PlayerShopConfig.PlayerShopInfo(newName, info.items()));
        }
        if (currentShopOwner != null && currentShopOwner.equals(owner) && shopNameBox != null) {
            shopNameBox.setValue(newName != null ? newName : "");
        }
    }
}
