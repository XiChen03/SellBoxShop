package com.sellbox.mod.playershop;

import com.sellbox.mod.shop.PlayerInventoryPickerScreen;
import com.sellbox.mod.shop.ShopButton;
import com.sellbox.mod.shop.ShopEntry;
import com.sellbox.mod.network.ModNetwork;
import com.sellbox.mod.util.FontUtil;
import com.sellbox.mod.util.PinyinUtil;
import com.sellbox.mod.util.ItemIcons;
import com.sellbox.mod.util.ItemSampleStacks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class PlayerShopItemPickerScreen extends Screen {
    private final com.sellbox.mod.gui.GuiTheme T = com.sellbox.mod.gui.GuiTheme.get();
    private float sf;
    private int panelLeft, panelTop, panelW, panelH;
    private int cx;

    // 顶部：搜索框 + 背包按钮
    private EditBox searchBox;
    private int searchX, searchY, searchW, searchH;
    private int bpBtnX, bpBtnY, bpBtnW, bpBtnH;

    // 物品网格（9列）
    private int gridX, gridTop, gridRows, cellW, cellH;
    private static final int COLS = 9;
    private int gridW, gridH;

    // 右侧选中物品展示
    private int infoLeft, infoTop, infoW, infoH;

    // JEI 物品候选条
    private int jeiTop, jeiH, jeiItemW, jeiItemH;
    private int jeiOffset = 0;

    // 模式切换 + 单价 + 自定义物品名 + 匹配按钮
    private int modeBarY, modeBtnH;
    private int priceBoxX, priceBoxY, priceBoxW, priceBoxH;
    private EditBox priceBox;
    private int modeSellBtnX, modeBuyBtnX, modeBtnW, matchBtnX, matchBtnW;
    private EditBox customNameBox;
    private int customNameX, customNameY, customNameW, customNameH;
    private boolean barterMode = false;
    private Item secondSelectedItem;
    private boolean selectingSecond = false;
    private boolean offerLocked = false;
    private boolean goodsLocked = false;
    private String barterOfferQty = "1";
    private String barterGoodsQty = "1";
    // NBT 支持：从背包选择的物品携带完整 NBT 副本；strictMatch 控制 ID+NBT 精确匹配
    private ItemStack selectedStack = ItemStack.EMPTY;
    private ItemStack gridHoverStack = ItemStack.EMPTY;
    private boolean strictMatch = false;
    private ItemStack offerStack = ItemStack.EMPTY;
    private ItemStack goodsStack = ItemStack.EMPTY;
    private boolean offerStrictMatch = false;
    private boolean goodsStrictMatch = false;
    private boolean isDraggingScrollbar = false;
    private int dragStartMouseY, dragStartOffset;
    private EditBox qtyBox;
    private int qtyBoxX, qtyBoxY, qtyBoxW, qtyBoxH;
    private ShopButton qtyConfirmBtn;
    private ShopButton unlockBtn;

    // 底部按钮
    private int confirmBtnX, backBtnX, btnRowY, btnW, btnH;

    // 分隔线颜色
    private static final int SEP_COLOR = 0xFF555555;

    private String searchQuery = "";
    private com.sellbox.mod.util.ItemSortUtil.SortMode sortMode = com.sellbox.mod.util.ItemSortUtil.SortMode.NAME;
    private final PlayerShopScreen parentScreen;
    private final BlockPos marketPos;
    private final int editIndex;
    private final ShopEntry editEntry;

    private List<ItemInfo> allItems = new ArrayList<>();
    private List<ItemInfo> filteredItems = new ArrayList<>();
    private Item selectedItem;
    private int scrollOffset = 0;
    private ShopEntry.TradeType tradeType = ShopEntry.TradeType.SELL;
    @Nullable
    private UUID editingOwnerUUID = null;

    private static record ItemInfo(Item item, ResourceLocation id, Component name, String nameLower, String pinyin, String searchLower, @Nullable CompoundTag variantNbt, ItemStack sample) {
        ItemInfo(Item item, ResourceLocation id, Component name, String nameLower, String pinyin) {
            this(item, id, name, nameLower, pinyin, nameLower, null, new ItemStack(item));
        }
    }

    public PlayerShopItemPickerScreen(PlayerShopScreen parent) {
        this(parent, -1, null);
    }

    public PlayerShopItemPickerScreen(PlayerShopScreen parent, int editIndex, ShopEntry editEntry) {
        super(Component.translatable(editIndex >= 0 ? "gui.sellbox.edit_item" : "gui.sellbox.select_item"));
        this.parentScreen = parent;
        this.marketPos = parent.getMarketPos();
        this.editIndex = editIndex;
        this.editEntry = editEntry;
    }

    public void setEditingOwnerUUID(@Nullable UUID uuid) {
        this.editingOwnerUUID = uuid;
    }

    @Override
    protected void init() {
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        if (this.font == null) this.font = Minecraft.getInstance().font;

        super.init();
        rebuildItems();

        if (editEntry != null) {
            for (ItemInfo info : allItems) {
                if (info.id.equals(editEntry.itemId())) {
                    selectedItem = info.item;
                    break;
                }
            }
            tradeType = editEntry.tradeType();
            // NBT 回填：普通条目恢复 NBT 模板与严格匹配标志
            if (editEntry.nbt() != null && selectedItem != null) {
                selectedStack = new ItemStack(selectedItem);
                selectedStack.setTag(editEntry.nbt().copy());
            }
            strictMatch = editEntry.strictMatch();
            // 以物易物编辑：恢复出价物、商品物、数量、锁定状态
            if ("barter".equals(editEntry.entryType()) && editEntry.barterItemId() != null) {
                barterMode = true;
                for (ItemInfo info : allItems) {
                    if (info.id.equals(editEntry.barterItemId())) {
                        selectedItem = info.item;
                        break;
                    }
                }
                for (ItemInfo info : allItems) {
                    if (info.id.equals(editEntry.itemId())) {
                        secondSelectedItem = info.item;
                        break;
                    }
                }
                barterOfferQty = String.valueOf(Math.max(1, editEntry.barterItemQty()));
                barterGoodsQty = String.valueOf(Math.max(1, editEntry.quantity()));
                offerLocked = true;
                goodsLocked = true;
                selectingSecond = false;
                // NBT 回填：出价物（barterNbt）+ 商品物（nbt）
                if (editEntry.barterNbt() != null && selectedItem != null) {
                    offerStack = new ItemStack(selectedItem);
                    offerStack.setTag(editEntry.barterNbt().copy());
                }
                offerStrictMatch = editEntry.barterStrictMatch();
                if (editEntry.nbt() != null && secondSelectedItem != null) {
                    goodsStack = new ItemStack(secondSelectedItem);
                    goodsStack.setTag(editEntry.nbt().copy());
                }
                goodsStrictMatch = editEntry.strictMatch();
            }
        }

        // 整体自适应缩放：以 460x460 虚拟设计空间为基准，宽高双轴取小并夹紧；
        // 之后 init 中所有布局均为虚拟设计坐标，render 内统一 pose.scale(sf) 绘制，
        // 鼠标事件统一除以 sf 还原为虚拟坐标，保证各区域/按钮位置隔离且无重叠。
        sf = (float) Math.min((double) height / 460.0, (double) width / 450.0);
        if (sf < 0.55f) sf = 0.55f;
        if (sf > 2.5f) sf = 2.5f;
        float vw = width / sf;
        float vh = height / sf;

        // 面板尺寸（虚拟坐标）：宽度上限 440，垂直方向占满可用高度
        panelW = (int) Math.min(vw - 10, 440);
        if (panelW < 240) panelW = 240;
        panelLeft = (int) ((vw - panelW) / 2);
        cx = panelLeft + panelW / 2;
        panelTop = 18;
        panelH = (int) (vh - panelTop - 5);
        if (panelH < 120) panelH = 120;

        // ======== 顶部搜索栏（宽度与网格对齐） ========
        cellW = 20;
        cellH = 20;
        gridW = COLS * cellW;
        int gridPadL = 10;
        gridX = panelLeft + gridPadL;

        searchH = 18;
        searchY = panelTop + 14;
        searchX = gridX;
        int searchBoxInset = 2;

        bpBtnH = searchH;
        String bpText = Component.translatable("gui.sellbox.open_inventory_pick").getString();
        bpBtnW = font.width(bpText) + 12;
        bpBtnX = panelLeft + panelW - bpBtnW - 10;
        bpBtnY = searchY;

        // 以物易物/排序按钮：先算出位置，用于隔离搜索框宽度（搜索框绝不越过排序按钮）
        String barterLbl = barterMode ? Component.translatable("gui.sellbox.barter_mode").getString() : Component.translatable("gui.sellbox.money_mode").getString();
        int barterBW = font.width(Component.translatable("gui.sellbox.barter_mode").getString()) + 12;
        int barterBX = bpBtnX - barterBW - 4;
        String sortLbl = Component.translatable("gui.sellbox.sort_label", sortMode.getDisplayName()).getString();
        int sortBW = font.width(sortLbl) + 12;
        int sortBX = barterBX - sortBW - 4;

        // 搜索框与右侧按钮区域隔离：宽度不超过排序按钮左缘
        searchW = Math.min(gridW, Math.max(8, sortBX - searchX - 6));

        searchBox = new EditBox(font, searchX + searchBoxInset, searchY + 1,
                searchW - searchBoxInset * 2, searchH - 2, Component.literal(""));
        searchBox.setBordered(false);
        searchBox.setMaxLength(64);
        searchBox.setResponder(q -> { searchQuery = q.toLowerCase().trim(); scrollOffset = 0; jeiOffset = 0; applyFilter(); });
        addRenderableWidget(searchBox);

        addRenderableWidget(new ShopButton(bpBtnX, bpBtnY, bpBtnW, bpBtnH, Component.literal(bpText),
                btn -> Minecraft.getInstance().setScreen(new PlayerInventoryPickerScreen(this, this::selectFromInventory))));

        // 以物易物切换按钮（背包按钮左侧）
        addRenderableWidget(new ShopButton(barterBX, searchY, barterBW, searchH, Component.literal(barterLbl),
                btn -> {
                    if (barterMode) {
                        String curQty = qtyBox != null ? qtyBox.getValue() : "1";
                        if (selectingSecond) barterGoodsQty = curQty;
                        else barterOfferQty = curQty;
                    }
                    barterMode = !barterMode;
                    if (!barterMode) {
                        selectingSecond = false;
                        offerLocked = false;
                        goodsLocked = false;
                    }
                    btn.setMessage(Component.literal(barterMode ? Component.translatable("gui.sellbox.barter_mode").getString() : Component.translatable("gui.sellbox.money_mode").getString()));
                }));

        // 排序切换按钮（以物易物按钮左侧）
        addRenderableWidget(new ShopButton(sortBX, bpBtnY, sortBW, bpBtnH, Component.literal(sortLbl),
                btn -> {
                    sortMode = sortMode.next();
                    btn.setMessage(Component.literal(Component.translatable("gui.sellbox.sort_label", sortMode.getDisplayName()).getString()));
                    applyFilter();
                }));

        // ======== 分隔线1 ========
        int sep1Y = searchY + searchH + 4;

        // ======== 物品网格 (左) + 信息面板 (右) ========
        gridTop = sep1Y + 2;

        // 信息面板
        infoLeft = gridX + gridW + 8;
        infoW = panelLeft + panelW - 10 - infoLeft;
        infoTop = gridTop;

        // JEI 行 + 模式栏 + 底部按钮 固定高度（整体上移20px）
        int jeiRowH = 24;
        int modeBarH = 20;
        int btnRowH = 22;
        int bottomFixed = 4 + jeiRowH + 4
                + modeBarH + 4 + btnRowH + 4 - 23;
        int maxGridBottom = panelTop + panelH - bottomFixed;
        gridH = maxGridBottom - gridTop - 2;
        gridRows = Math.max(1, (gridH / cellH) - 2);
        gridH = gridRows * cellH;

        // 信息面板高度 = 网格高度
        infoH = gridH;

        // ======== JEI 物品候选条 ========
        jeiTop = gridTop + gridH + 4;
        jeiH = 22;
        jeiItemW = 20;
        jeiItemH = 20;

        // ======== 分隔线2 ========
        int sep2Y = jeiTop + jeiH + 2;

        // ======== 模式切换 + 单价 ========
        modeBarY = sep2Y + 2;
        modeBtnH = 18;
        modeBtnW = 60;
        String modeLabel = Component.translatable("gui.sellbox.mode_label").getString() + ":";
        int modeLabelW = font.width(modeLabel);
        int modeLabelX = panelLeft + 10;
        modeSellBtnX = modeLabelX + modeLabelW + 8;
        modeBuyBtnX = modeSellBtnX + modeBtnW + 4;
        matchBtnW = 66;
        matchBtnX = modeSellBtnX + modeBtnW + 6;

        priceBoxW = 90;
        priceBoxH = modeBtnH;
        priceBoxX = panelLeft + panelW - priceBoxW - 10;
        priceBoxY = modeBarY;

        priceBox = new EditBox(font, priceBoxX + 2, priceBoxY + 1,
                priceBoxW - 4, priceBoxH - 2, Component.literal(""));
        priceBox.setBordered(false);
        priceBox.setMaxLength(9);
        priceBox.setFilter(s -> s.matches("\\d*"));
        if (editEntry != null) {
            priceBox.setValue(String.valueOf(editEntry.price()));
        }
        addRenderableWidget(priceBox);

        // 自定义物品名（紧挨价格框左侧；modeRight 包含匹配按钮，避免重叠）：按可用空间收缩，绝不与匹配按钮/价格框重叠
        int modeRight = matchBtnX + matchBtnW;
        if (modeRight > priceBoxX - 4) {
            matchBtnW = Math.max(30, priceBoxX - 4 - matchBtnX);
            modeRight = matchBtnX + matchBtnW;
        }
        customNameW = Math.min(90, priceBoxX - 4 - modeRight);
        customNameX = priceBoxX - customNameW - 4;
        customNameY = modeBarY;
        customNameH = modeBtnH;
        if (customNameW >= 24) {
            customNameBox = new EditBox(font, customNameX + 2, customNameY + 1,
                    customNameW - 4, customNameH - 2, Component.literal(""));
            customNameBox.setBordered(false);
            customNameBox.setMaxLength(64);
            if (editEntry != null && editEntry.customName() != null) customNameBox.setValue(editEntry.customName());
            addRenderableWidget(customNameBox);
        }

        // 数量输入框 + 确认 + 解锁按钮（信息面板右下角）
        qtyBoxW = 56;
        qtyBoxH = priceBoxH;
        qtyBoxY = infoTop + infoH - qtyBoxH - 4;
        int qtyBtnW = 32;
        int unlockBtnW = 32;
        int gap = 3;
        int rightPad = 4;
        int unlockBtnX = infoLeft + infoW - unlockBtnW - rightPad;
        int qtyCfmX = unlockBtnX - gap - qtyBtnW;
        qtyBoxX = qtyCfmX - gap - qtyBoxW;
        qtyBox = new EditBox(font, qtyBoxX + 2, qtyBoxY + 1,
                qtyBoxW - 4, qtyBoxH - 2, Component.literal(""));
        qtyBox.setBordered(false);
        qtyBox.setFilter(s -> s.matches("\\d*"));
        qtyBox.setMaxLength(4);
        if (barterMode) qtyBox.setValue(selectingSecond ? barterGoodsQty : barterOfferQty);
        else if (editEntry != null) qtyBox.setValue(String.valueOf(Math.max(1, editEntry.quantity())));
        addRenderableWidget(qtyBox);

        qtyConfirmBtn = new ShopButton(qtyCfmX, qtyBoxY, qtyBtnW, qtyBoxH,
                Component.translatable("gui.sellbox.confirm"), btn -> onQtyConfirm());
        addRenderableWidget(qtyConfirmBtn);

        unlockBtn = new ShopButton(unlockBtnX, qtyBoxY, unlockBtnW, qtyBoxH,
                Component.translatable("gui.sellbox.unlock"), btn -> onUnlock());
        addRenderableWidget(unlockBtn);

        // 重置锁定状态：仅新增或非 barter 编辑时重置（编辑 barter 条目时保留恢复的锁定）
        if (editEntry == null || !"barter".equals(editEntry.entryType())) {
            offerLocked = false;
            goodsLocked = false;
        }

        // ======== 分隔线3 ========
        int sep3Y = modeBarY + modeBtnH + 2;

        // ======== 底部按钮 ========
        btnRowY = sep3Y + 2;
        btnW = 65;
        btnH = 18;
        int btnGap = 12;
        confirmBtnX = cx - btnW - btnGap / 2;
        backBtnX = cx + btnGap / 2;

        // 背景底边在确认/取消按钮下方 5px，去掉多余空白
        panelH = (btnRowY + btnH + 5) - panelTop;
        if (panelH < 120) panelH = 120;

        String confirmText = Component.translatable(editIndex >= 0 ? "gui.sellbox.confirm_edit" : "gui.sellbox.confirm_add").getString();
        addRenderableWidget(new ShopButton(confirmBtnX, btnRowY, btnW, btnH,
                Component.literal(confirmText), btn -> confirm()));
        addRenderableWidget(new ShopButton(backBtnX, btnRowY, btnW, btnH,
                Component.translatable("gui.sellbox.cancel"), btn -> minecraft.setScreen(parentScreen)));
    }

    private void scrollToItem(Item item) {
        if (item == null) return;
        int idx = -1;
        for (int i = 0; i < filteredItems.size(); i++) {
            if (filteredItems.get(i).item == item) { idx = i; break; }
        }
        if (idx < 0) return;
        int vis = COLS * gridRows;
        int row = idx / COLS;
        int scrollRow = scrollOffset / COLS;
        if (row < scrollRow) {
            scrollOffset = (row / COLS) * COLS;
        } else if (row >= scrollRow + gridRows) {
            scrollOffset = ((row - gridRows + 1) / COLS) * COLS;
        }
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, filteredItems.size() - vis)));
        scrollOffset = (scrollOffset / COLS) * COLS;
    }

    private void onQtyConfirm() {
        String val = qtyBox != null ? qtyBox.getValue() : "1";
        if (val.isEmpty()) val = "1";
        if (barterMode) {
            if (selectingSecond) {
                barterGoodsQty = val;
                goodsLocked = true;
            } else {
                barterOfferQty = val;
                offerLocked = true;
            }
            if (offerLocked && goodsLocked) return;
            selectingSecond = !selectingSecond;
            if (qtyBox != null) qtyBox.setValue(selectingSecond ? barterGoodsQty : barterOfferQty);
        }
    }

    private void onUnlock() {
        if (!barterMode) return;
        String curQty = qtyBox != null ? qtyBox.getValue() : "1";
        if (selectingSecond) {
            barterGoodsQty = curQty;
            goodsLocked = false;
        } else {
            barterOfferQty = curQty;
            offerLocked = false;
        }
        if (qtyBox != null) qtyBox.setValue(selectingSecond ? barterGoodsQty : barterOfferQty);
    }

    public void selectFromInventory(ItemStack stack) {
        if (stack.isEmpty()) return;
        // 从背包选择物品：携带了具体物品的 NBT，强制使用严格匹配
        strictMatch = true;
        if (barterMode) {
            if (selectingSecond) {
                goodsStrictMatch = true;
                secondSelectedItem = stack.getItem();
                goodsStack = stack.copy();
            } else {
                offerStrictMatch = true;
                selectedItem = stack.getItem();
                offerStack = stack.copy();
            }
        } else {
            selectedItem = stack.getItem();
            selectedStack = stack.copy();
        }
        scrollToItem(stack.getItem());
        if (searchBox != null) searchBox.setValue("");
    }

    private boolean isItemInPlayerInventory(Item item) {
        if (minecraft.player == null) return false;
        for (ItemStack s : minecraft.player.getInventory().items) {
            if (!s.isEmpty() && s.getItem() == item) return true;
        }
        return false;
    }

    private void confirm() {
        if (barterMode) {
            String cur = qtyBox != null ? qtyBox.getValue() : "1";
            if (selectingSecond) {
                barterGoodsQty = cur;
                goodsLocked = true;
            } else {
                barterOfferQty = cur;
                offerLocked = true;
            }
            if (selectedItem == null || secondSelectedItem == null) return;
            int offerQty;
            try { offerQty = Integer.parseInt(barterOfferQty); if (offerQty < 1) offerQty = 1; if (offerQty > 9999) offerQty = 9999; } catch (NumberFormatException e) { offerQty = 1; }
            int goodsQty;
            try { goodsQty = Integer.parseInt(barterGoodsQty); if (goodsQty < 1) goodsQty = 1; if (goodsQty > 9999) goodsQty = 9999; } catch (NumberFormatException e) { goodsQty = 1; }
            ResourceLocation offerId = BuiltInRegistries.ITEM.getKey(selectedItem);
            ResourceLocation goodsId = BuiltInRegistries.ITEM.getKey(secondSelectedItem);
            String cName = customNameBox != null ? customNameBox.getValue().trim() : "";
            // 单条以物易物：itemId=goodsId, price=goodsQty(商品数量), quantity=offerQty(出价数量), barterItemId=offerId, barterItemQty=offerQty
            String action = editIndex >= 0 ? "edit_barter_item" : "add_barter_item";
            int idx = editIndex >= 0 ? editIndex : -1;
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.PlayerShopEditPacket(action, idx, goodsId, goodsQty, offerQty, cName, marketPos, ShopEntry.TradeType.SELL, offerId, offerQty, editingOwnerUUID,
                    goodsStack != null && !goodsStack.isEmpty() && goodsStack.getTag() != null ? goodsStack.getTag().toString() : "", goodsStrictMatch,
                    offerStack != null && !offerStack.isEmpty() && offerStack.getTag() != null ? offerStack.getTag().toString() : "", offerStrictMatch));
        } else {
            if (selectedItem == null) return;
            int price;
            try {
                String priceStr = priceBox != null ? priceBox.getValue() : "1";
                if (priceStr.trim().isEmpty()) priceStr = "1";
                price = Integer.parseInt(priceStr);
                if (price < 1) price = 1;
            } catch (NumberFormatException e) { return; } // 非法输入 → 放弃本次确认
            int qty;
            try {
                qty = Integer.parseInt(qtyBox != null ? qtyBox.getValue() : "1");
                if (qty < 1) qty = 1;
                if (qty > 9999) qty = 9999;
            } catch (NumberFormatException e) { qty = 1; }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(selectedItem);
            String action = editIndex >= 0 ? "edit_item" : "add_item";
            int idx = editIndex >= 0 ? editIndex : -1;
            String cName = customNameBox != null ? customNameBox.getValue().trim() : "";
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.PlayerShopEditPacket(action, idx, id, price, qty, cName, marketPos, tradeType, null, 1, editingOwnerUUID,
                    selectedStack != null && !selectedStack.isEmpty() && selectedStack.getTag() != null ? selectedStack.getTag().toString() : "", strictMatch,
                    null, false));
        }
        minecraft.setScreen(parentScreen);
        parentScreen.refreshFromConfig();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        if (this.font == null) this.font = Minecraft.getInstance().font;

        int smx = mx, smy = my; // 屏幕坐标（悬浮提示用）
        mx = (int) Math.round(mx / (double) sf);
        my = (int) Math.round(my / (double) sf);
        g.pose().pushPose();
        g.pose().scale(sf, sf, 1f);
        fillRoundedRect(g, panelLeft, panelTop, panelW, panelH, T.popupBg);
        g.fill(panelLeft + 2, panelTop, panelLeft + panelW - 2, panelTop + 1, T.accentStripe);

        // 标题（凸起小标题）
        int tabW = font.width(title) + 16;
        int tabH = 14;
        int tabX = panelLeft + 10;
        int tabY = panelTop - tabH + 2;
        fillRoundedRect(g, tabX, tabY, tabW, tabH + 1, T.cellBorder);
        g.fill(tabX + 2, tabY + tabH, tabX + tabW - 2, tabY + tabH + 1, T.popupBg);
        g.drawString(font, title, tabX + (tabW - font.width(title)) / 2, tabY + (tabH - font.lineHeight) / 2 + 1, T.textTitle);

        super.render(g, mx, my, pt);

        // ======== 搜索框自定义边框 ========
        boolean searchFocused = searchBox != null && searchBox.isFocused();
        drawBox(g, searchX, searchY, searchW, searchH, searchFocused);
        if (searchBox != null && searchBox.getValue().isEmpty() && !searchFocused) {
            FontUtil.drawAutoFitText(g, font, Component.translatable("gui.sellbox.search_items").getString(), searchX + 6, searchY + 3, searchW - 12, T.textHint);
        }

        // ======== 分隔线1 ========
        int sep1Y = searchY + searchH + 4;
        g.fill(panelLeft + 4, sep1Y, panelLeft + panelW - 4, sep1Y + 1, SEP_COLOR);

        // ======== 物品网格 ========
        g.enableScissor(v2s(gridX), v2s(gridTop), v2s(gridX + gridW), v2s(gridTop + gridH));
        gridHoverStack = ItemStack.EMPTY;
        int endIdx = Math.min(scrollOffset + COLS * gridRows, filteredItems.size());
        for (int i = scrollOffset; i < endIdx; i++) {
            int local = i - scrollOffset;
            int col = local % COLS;
            int row = local / COLS;
            int x = gridX + col * cellW;
            int y = gridTop + row * cellH;
            boolean hovered = mx >= x && mx < x + cellW && my >= y && my < y + cellH;
            boolean sel = filteredItems.get(i).item == selectedItem;
            if (sel) g.fill(x + 1, y + 1, x + cellW - 1, y + cellH - 1, T.cellSelectedFill);
            else if (hovered) g.fill(x + 1, y + 1, x + cellW - 1, y + cellH - 1, T.cellHoveredFill);
            if (sel) {
                int c = T.catBorder;
                g.fill(x + 2, y, x + cellW - 2, y + 1, c);
                g.fill(x + 2, y + cellH - 1, x + cellW - 2, y + cellH, c);
                g.fill(x, y + 2, x + 1, y + cellH - 2, c);
                g.fill(x + cellW - 1, y + 2, x + cellW, y + cellH - 2, c);
            }
            ItemIcons.render(g, renderable(filteredItems.get(i)), x + (cellW - 16) / 2, y + (cellH - 16) / 2);
            if (hovered) gridHoverStack = renderable(filteredItems.get(i)).copy();
            if (isItemInPlayerInventory(filteredItems.get(i).item)) {
                g.fill(x + cellW - 5, y + cellH - 5, x + cellW - 1, y + cellH - 1, T.cellInInventory);
            }
        }
        g.disableScissor();

        // 网格滚动条
        int vis = COLS * gridRows;
        int maxScroll = Math.max(0, filteredItems.size() - vis);
        if (maxScroll > 0) {
            int sbX = gridX + gridW + 2;
            int sbW = 3;
            g.fill(sbX, gridTop, sbX + sbW, gridTop + gridH, T.scrollbarTrack);
            int thumbH = Math.max(6, gridH * vis / filteredItems.size());
            int thumbY = gridTop + (gridH - thumbH) * scrollOffset / maxScroll;
            g.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, T.scrollbarThumb);
        }

        // ======== 信息面板（右侧选中物品） ========
        g.fill(infoLeft, infoTop, infoLeft + infoW, infoTop + infoH, T.infoPanelBg);
        if (barterMode) {
            renderBarterInfo(g);
        } else if (selectedItem != null) {
            ItemStack selStack = (selectedStack != null && !selectedStack.isEmpty() && selectedStack.getItem() == selectedItem)
                    ? selectedStack : new ItemStack(selectedItem);
            // 大图标
            int iconSize = 32;
            int iconX = infoLeft + (infoW - iconSize) / 2;
            int iconY = infoTop + 8;
            ItemIcons.render(g, selStack, iconX, iconY);
            // 物品名（优先显示自定义物品名）
            String customName = customNameBox != null ? customNameBox.getValue().trim() : "";
            String rawName = customName.isEmpty() ? selStack.getHoverName().getString() : customName;
            int nameY = iconY + iconSize + 6;
            int nameMaxW = infoW - 8;
            FontUtil.drawAutoFitCentered(g, font, rawName, infoLeft + infoW / 2, nameY, nameMaxW, T.textCategoryName);
            // ID
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(selectedItem);
            String idStr = id.toString();
            int idY = nameY + 10;
            FontUtil.drawAutoFitCentered(g, font, idStr, infoLeft + infoW / 2, idY, infoW - 8, T.textDisabled);
            // 背包标记
            int detailY = idY;
            if (isItemInPlayerInventory(selectedItem)) {
                String owned = Component.translatable("gui.sellbox.in_inventory").getString();
                FontUtil.drawAutoFitCentered(g, font, owned, infoLeft + infoW / 2, idY + 10, infoW - 8, T.textInInventory);
                detailY = idY + 10;
            }
            // 匹配状态（严格匹配 / ID相同 + NBT 标记）
            String matchStr = (strictMatch ? "严格匹配" : "ID相同")
                    + ((selStack.getTag() != null) ? " (含NBT)" : "");
            FontUtil.drawAutoFitCentered(g, font, matchStr, infoLeft + infoW / 2, detailY + 10, infoW - 8, T.textMatchMode);
        } else {
            String hint = Component.translatable("gui.sellbox.select_item_hint").getString();
            FontUtil.drawAutoFitCentered(g, font, hint, infoLeft + infoW / 2, infoTop + infoH / 2, infoW - 8, T.textHint);
        }

        // 实时预览（数量框上方）
        int previewY = qtyBoxY - font.lineHeight - 2;
        String priceVal = priceBox != null ? priceBox.getValue() : "";
        String qtyVal = qtyBox != null ? qtyBox.getValue() : "1";
        if (barterMode) {
            String offerName = selectedItem != null ? selectedItem.getName(new ItemStack(selectedItem)).getString() : "?";
            String goodsName = secondSelectedItem != null ? secondSelectedItem.getName(new ItemStack(secondSelectedItem)).getString() : "?";
            String offerQtyStr = offerLocked ? "x" + barterOfferQty : "";
            String goodsQtyStr = goodsLocked ? "x" + barterGoodsQty : "";
            String curQtyStr = "x" + qtyVal;
            String preview;
            if (!selectingSecond) {
                preview = offerName + curQtyStr + " → " + goodsName + goodsQtyStr;
            } else {
                preview = offerName + offerQtyStr + " → " + goodsName + curQtyStr;
            }
            FontUtil.drawAutoFitText(g, font, preview, infoLeft + 4, previewY, infoW - 8, T.textTotal);
        } else if (selectedItem != null) {
            String itemName = selectedItem.getName(new ItemStack(selectedItem)).getString();
            String qtyStr = qtyVal.isEmpty() || qtyVal.equals("1") ? "" : "x" + qtyVal;
            String preview = "¥" + (priceVal.isEmpty() ? "1" : priceVal) + " → " + itemName + qtyStr;
            FontUtil.drawAutoFitText(g, font, preview, infoLeft + 4, previewY, infoW - 8, T.textTotal);
        }

        // ======== 分隔线2 ========
        int sep2Y = jeiTop + jeiH + 2;
        g.fill(panelLeft + 4, sep2Y, panelLeft + panelW - 4, sep2Y + 1, SEP_COLOR);

        // ======== JEI 物品候选条 ========
        renderJeiRow(g, mx, my);

        // ======== 分隔线3 ========
        int sep3Y = modeBarY + modeBtnH + 2;
        g.fill(panelLeft + 4, sep3Y, panelLeft + panelW - 4, sep3Y + 1, SEP_COLOR);

        // ======== 模式切换标签 ========
        int modeLabelX = panelLeft + 10;
        String modeLbl = Component.translatable("gui.sellbox.mode_label").getString() + ":";
        g.drawString(font, modeLbl, modeLabelX, modeBarY + (modeBtnH - font.lineHeight) / 2, T.textMuted);
        // 仅显示出售按钮（店主仅可使用出售）
        FontUtil.drawAutoFitCentered(g, font, Component.translatable("gui.sellbox.sell_label").getString(), modeSellBtnX + modeBtnW / 2, modeBarY + (modeBtnH - font.lineHeight) / 2, modeBtnW - 4, T.textPrimary);
        // 匹配模式切换按钮（仅普通商品显示；以物易物在出价/商品名字下方各自切换）
        if (!barterMode) {
            g.fill(matchBtnX, modeBarY, matchBtnX + matchBtnW, modeBarY + modeBtnH, strictMatch ? T.strictMatchOn : T.strictMatchOff);
            String matchLbl = strictMatch ? "严格匹配" : "ID相同";
            FontUtil.drawAutoFitCentered(g, font, matchLbl, matchBtnX + matchBtnW / 2, modeBarY + (modeBtnH - font.lineHeight) / 2, matchBtnW - 4, T.textPrimary);
        }

        // ======== 价格框边框 ========
        boolean priceFocused = priceBox != null && priceBox.isFocused();
        drawBox(g, priceBoxX, priceBoxY, priceBoxW, priceBoxH, priceFocused);
        if (priceBox != null && priceBox.getValue().isEmpty() && !priceFocused) {
            FontUtil.drawAutoFitText(g, font, Component.translatable("gui.sellbox.enter_price").getString(), priceBoxX + 6, priceBoxY + 3, priceBoxW - 12, T.textHint);
        }
        // 自定义物品名框边框 + 占位文字
        boolean nameFocused = customNameBox != null && customNameBox.isFocused();
        if (customNameBox != null) drawBox(g, customNameX, customNameY, customNameW, customNameH, nameFocused);
        if (customNameBox != null && customNameBox.getValue().isEmpty() && !nameFocused) {
            FontUtil.drawAutoFitText(g, font, Component.translatable("gui.sellbox.custom_name").getString(), customNameX + 6, customNameY + 3, customNameW - 12, T.textHint);
        }
        // 数量框边框 + 占位文字（信息面板右下角）
        boolean qtyFocused = qtyBox != null && qtyBox.isFocused();
        String qtyLabel = barterMode
                ? (selectingSecond ? Component.translatable("gui.sellbox.goods_qty").getString() : Component.translatable("gui.sellbox.offer_qty").getString())
                : Component.translatable("gui.sellbox.quantity_label").getString();
        int qtyLabelW = font.width(qtyLabel);
        int qtyLabelX = Math.max(infoLeft + 2, qtyBoxX - qtyLabelW - 4);
        FontUtil.drawAutoFitText(g, font, qtyLabel, qtyLabelX, qtyBoxY + (qtyBoxH - font.lineHeight) / 2, qtyBoxX - qtyLabelX - 2, T.textSecondary);
        drawBox(g, qtyBoxX, qtyBoxY, qtyBoxW, qtyBoxH, qtyFocused);
        if (qtyBox != null && qtyBox.getValue().isEmpty() && !qtyFocused) {
            FontUtil.drawAutoFitText(g, font, Component.translatable("gui.sellbox.quantity_hint").getString(), qtyBoxX + 6, qtyBoxY + 3, qtyBoxW - 12, T.textHint);
        }

        g.pose().popPose();

        // ======== 悬浮提示（必须在 pose 缩放之外，用屏幕坐标绘制） ========
        if (mx >= bpBtnX && mx <= bpBtnX + bpBtnW && my >= bpBtnY && my <= bpBtnY + bpBtnH) {
            g.renderTooltip(font, Component.translatable("gui.sellbox.click_to_inventory"), smx, smy);
        }
        if (!gridHoverStack.isEmpty()) g.renderTooltip(font, gridHoverStack, smx, smy);
    }

    /** 虚拟设计坐标 → 屏幕坐标（enableScissor 不经过 pose，需手动换算） */
    private int v2s(double v) {
        return (int) Math.round(v * sf);
    }

    private void renderBarterInfo(GuiGraphics g) {
        int half = infoW / 2;
        int iconSize = 24;
        int iconY = infoTop + 16;
        // 出价物品 = selectedItem（买家支付），商品物品 = secondSelectedItem（买家获得）
        Item offerItem = selectedItem;
        Item goodsItem = secondSelectedItem;
        // 标签：出价 / 商品（带锁定状态）
        String offerLabel = offerLocked ? Component.translatable("gui.sellbox.offer_locked").getString() : (selectingSecond ? Component.translatable("gui.sellbox.offer").getString() : Component.translatable("gui.sellbox.offer_selecting").getString());
        String goodsLabel = goodsLocked ? Component.translatable("gui.sellbox.goods_locked").getString() : (!selectingSecond ? Component.translatable("gui.sellbox.goods").getString() : Component.translatable("gui.sellbox.goods_selecting").getString());
        FontUtil.drawAutoFitCentered(g, font, offerLabel, infoLeft + half / 2, infoTop + 4, half - 4, selectingSecond ? T.textDisabled : T.textModeLabel);
        FontUtil.drawAutoFitCentered(g, font, goodsLabel, infoLeft + half + half / 2, infoTop + 4, half - 4, selectingSecond ? T.textModeLabel : T.textDisabled);
        // 左侧：出价物品
        if (offerItem != null) {
            int ix1 = infoLeft + (half - iconSize) / 2;
            ItemStack offerStackRender = (offerStack != null && !offerStack.isEmpty() && offerStack.getItem() == offerItem)
                    ? offerStack : new ItemStack(offerItem);
            ItemIcons.render(g, offerStackRender, ix1, iconY);
            int borderCol = offerLocked ? T.barterLockedBorder : (selectingSecond ? T.barterDisabledBorder : T.catBorder);
            drawItemBorderColor(g, ix1, iconY, iconSize, borderCol);
            String name1 = offerStackRender.getHoverName().getString();
            int nameY1 = iconY + iconSize + 2;
            FontUtil.drawAutoFitCentered(g, font, name1, infoLeft + half / 2, nameY1, half - 4,
                    offerLocked ? T.textLocked : (selectingSecond ? T.textDisabled : T.textCategoryName));
            // 出价物匹配模式（名字下方，可点击切换）
            int offerModeY = nameY1 + font.lineHeight + 2;
            String offerMode = offerStrictMatch ? "严格NBT" : "物品ID";
            FontUtil.drawAutoFitCentered(g, font, offerMode, infoLeft + half / 2, offerModeY, half - 4,
                    offerStrictMatch ? T.textTotal : T.textDisabled);
            if (offerLocked) {
                String qtyStr = "x" + barterOfferQty;
                FontUtil.drawAutoFitCentered(g, font, qtyStr, infoLeft + half / 2, offerModeY + font.lineHeight + 2, half - 4, T.textLocked);
            }
        } else {
            String hint = selectingSecond ? Component.translatable("gui.sellbox.locked").getString() : Component.translatable("gui.sellbox.select_item_hint").getString();
            FontUtil.drawAutoFitCentered(g, font, hint, infoLeft + half / 2, iconY + iconSize / 2, half - 4, T.textHint);
        }
        // 箭头
        String arrow = " → ";
        int arrowX = infoLeft + half - font.width(arrow) / 2;
        int arrowY = iconY + iconSize / 2 - font.lineHeight / 2;
        g.drawString(font, arrow, arrowX, arrowY, T.textModeLabel);
        // 右侧：商品物品
        if (goodsItem != null) {
            int ix2 = infoLeft + half + (half - iconSize) / 2;
            ItemStack goodsStackRender = (goodsStack != null && !goodsStack.isEmpty() && goodsStack.getItem() == goodsItem)
                    ? goodsStack : new ItemStack(goodsItem);
            ItemIcons.render(g, goodsStackRender, ix2, iconY);
            int borderCol = goodsLocked ? T.barterLockedBorder : (!selectingSecond ? T.barterDisabledBorder : T.catBorder);
            drawItemBorderColor(g, ix2, iconY, iconSize, borderCol);
            String name2 = goodsStackRender.getHoverName().getString();
            int nameY2 = iconY + iconSize + 2;
            FontUtil.drawAutoFitCentered(g, font, name2, infoLeft + half + half / 2, nameY2, half - 4,
                    goodsLocked ? T.textLocked : (!selectingSecond ? T.textDisabled : T.textCategoryName));
            // 商品物匹配模式（名字下方，可点击切换）
            int goodsModeY = nameY2 + font.lineHeight + 2;
            String goodsMode = goodsStrictMatch ? "严格NBT" : "物品ID";
            FontUtil.drawAutoFitCentered(g, font, goodsMode, infoLeft + half + half / 2, goodsModeY, half - 4,
                    goodsStrictMatch ? T.textTotal : T.textDisabled);
            if (goodsLocked) {
                String qtyStr = "x" + barterGoodsQty;
                FontUtil.drawAutoFitCentered(g, font, qtyStr, infoLeft + half + half / 2, goodsModeY + font.lineHeight + 2, half - 4, T.textLocked);
            }
        } else {
            String hint = !selectingSecond ? Component.translatable("gui.sellbox.locked").getString() : Component.translatable("gui.sellbox.select_item_hint").getString();
            FontUtil.drawAutoFitCentered(g, font, hint, infoLeft + half + half / 2, iconY + iconSize / 2, half - 4, T.textHint);
        }
    }

    private void drawItemBorderColor(GuiGraphics g, int x, int y, int size, int color) {
        g.fill(x, y, x + size, y + 1, color);
        g.fill(x, y + size - 1, x + size, y + size, color);
        g.fill(x, y, x + 1, y + size, color);
        g.fill(x + size - 1, y, x + size, y + size, color);
    }

    private void drawItemBorder(GuiGraphics g, int x, int y, int size) {
        g.fill(x, y, x + size, y + 1, 0xFF444444);
        g.fill(x, y + size - 1, x + size, y + size, 0xFF444444);
        g.fill(x, y, x + 1, y + size, 0xFF444444);
        g.fill(x + size - 1, y, x + size, y + size, 0xFF444444);
    }

    private void renderJeiRow(GuiGraphics g, int mx, int my) {
        if (filteredItems.isEmpty()) return;
        int jeiStartX = panelLeft + 10;
        int maxInRow = (panelW - 20) / (jeiItemW + 1);
        maxInRow = Math.max(1, maxInRow);

        // 限制 jeiOffset
        jeiOffset = Math.min(jeiOffset, Math.max(0, filteredItems.size() - maxInRow));
        jeiOffset = Math.max(0, jeiOffset);

        for (int i = 0; i < maxInRow && (jeiOffset + i) < filteredItems.size(); i++) {
            int idx = jeiOffset + i;
            int x = jeiStartX + i * (jeiItemW + 1);
            int y = jeiTop + (jeiH - jeiItemH) / 2;
            boolean hover = mx >= x && mx < x + jeiItemW && my >= y && my < y + jeiItemH;
            boolean sel = filteredItems.get(idx).item == selectedItem;
            if (sel) g.fill(x, y, x + jeiItemW, y + jeiItemH, T.cellSelectedFill);
            else if (hover) g.fill(x, y, x + jeiItemW, y + jeiItemH, T.cellHoveredFill);
            g.fill(x + 2, y, x + jeiItemW - 2, y + 1, 0x66444444);
            g.fill(x + 2, y + jeiItemH - 1, x + jeiItemW - 2, y + jeiItemH, 0x66444444);
            g.fill(x, y + 2, x + 1, y + jeiItemH - 2, 0x66444444);
            g.fill(x + jeiItemW - 1, y + 2, x + jeiItemW, y + jeiItemH - 2, 0x66444444);
            ItemIcons.render(g, renderable(filteredItems.get(idx)), x + (jeiItemW - 16) / 2, y + (jeiItemH - 16) / 2);
            if (hover) gridHoverStack = renderable(filteredItems.get(idx)).copy();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        mx = Math.round(mx / (double) sf);
        my = Math.round(my / (double) sf);
        if (super.mouseClicked(mx, my, button)) return true;

        // 匹配模式切换（仅普通商品；以物易物在出价/商品名字下方各自切换）
        if (my >= modeBarY && my <= modeBarY + modeBtnH &&
                !barterMode && mx >= matchBtnX && mx <= matchBtnX + matchBtnW) {
            strictMatch = !strictMatch;
            return true;
        }

        // 网格点击
        int endIdx = Math.min(scrollOffset + COLS * gridRows, filteredItems.size());
        for (int i = scrollOffset; i < endIdx; i++) {
            int col = (i - scrollOffset) % COLS;
            int row = (i - scrollOffset) / COLS;
            int x = gridX + col * cellW;
            int y = gridTop + row * cellH;
            if (mx >= x && mx < x + cellW && my >= y && my < y + cellH) {
                ItemInfo clickedInfo = filteredItems.get(i);
                Item clicked = clickedInfo.item;
                if (barterMode) {
                    if (selectingSecond) {
                        if (!goodsLocked) applySelection(clickedInfo, true);
                    } else {
                        if (!offerLocked) applySelection(clickedInfo, false);
                    }
                } else {
                    if (button == 1 && clicked == selectedItem) {
                        selectedItem = null;
                        selectedStack = null;
                    } else {
                        applySelection(clickedInfo, false);
                    }
                }
                scrollToItem(clicked);
                return true;
            }
        }

        // 网格滚动条点击（支持拖动）
        int vis = COLS * gridRows;
        int maxScroll = Math.max(0, filteredItems.size() - vis);
        if (maxScroll > 0) {
            int sbX = gridX + gridW + 2;
            if (mx >= sbX && mx < sbX + 3 && my >= gridTop && my < gridTop + gridH) {
                int thumbH = Math.max(6, (int)(gridH * (double)vis / Math.max(1, filteredItems.size())));
                int oldThumbY = gridTop + (gridH - thumbH) * scrollOffset / maxScroll;
                if (my >= oldThumbY && my <= oldThumbY + thumbH) {
                    isDraggingScrollbar = true;
                    dragStartMouseY = (int) my;
                    dragStartOffset = scrollOffset;
                } else {
                    scrollOffset = (int) ((my - gridTop) / (double) gridH * maxScroll);
                    scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
                    scrollOffset = (scrollOffset / COLS) * COLS;
                }
                return true;
            }
        }

        // 以物易物信息栏点击：点击模式行切换严格NBT/物品ID；点击物品追踪至左侧网格；点击空白区域切换出价/商品
        if (barterMode && mx >= infoLeft && mx < infoLeft + infoW && my >= infoTop && my < infoTop + infoH) {
            int half = infoW / 2;
            int iconSize = 24;
            int iconY = infoTop + 16;
            int nameY = iconY + iconSize + 2;
            int modeY = nameY + font.lineHeight + 2;
            int modeBottom = modeY + font.lineHeight;
            int itemBottom = modeBottom + 6;
            boolean onLeft = mx >= infoLeft && mx < infoLeft + half;
            boolean onRight = mx >= infoLeft + half && mx < infoLeft + infoW;
            // 模式行切换（出价物/商品物各自独立）
            if (my >= modeY && my < modeBottom) {
                if (onLeft && selectedItem != null) {
                    offerStrictMatch = !offerStrictMatch;
                    return true;
                }
                if (onRight && secondSelectedItem != null) {
                    goodsStrictMatch = !goodsStrictMatch;
                    return true;
                }
            }
            // 点击物品区域追踪至左侧网格
            if (my >= iconY && my < itemBottom) {
                if (onLeft && selectedItem != null) {
                    scrollToItem(selectedItem);
                    return true;
                }
                if (onRight && secondSelectedItem != null) {
                    scrollToItem(secondSelectedItem);
                    return true;
                }
            }
            // 点击空白区域切换出价/商品
            boolean newSelecting = mx >= infoLeft + infoW / 2;
            if (newSelecting != selectingSecond) {
                String curQty = qtyBox != null ? qtyBox.getValue() : "1";
                if (selectingSecond) barterGoodsQty = curQty;
                else barterOfferQty = curQty;
                selectingSecond = newSelecting;
                if (qtyBox != null) qtyBox.setValue(selectingSecond ? barterGoodsQty : barterOfferQty);
            }
            return true;
        }

        // JEI 行点击
        boolean jeiClicked = clickJeiRow(mx, my, button);
        if (jeiClicked) return true;

        // 非以物易物模式：点击信息栏任意位置追踪至左侧网格
        if (!barterMode && selectedItem != null && mx >= infoLeft && mx < infoLeft + infoW && my >= infoTop && my < infoTop + infoH) {
            scrollToItem(selectedItem);
            return true;
        }

        // 点击空白处取消输入框焦点
        for (var w : children()) {
            if (w instanceof net.minecraft.client.gui.components.EditBox eb) eb.setFocused(false);
        }
        // 以物易物模式禁用价格框
        if (barterMode && priceBox != null) priceBox.setFocused(false);
        return false;
    }

    private boolean clickJeiRow(double mx, double my, int button) {
        if (filteredItems.isEmpty()) return false;
        int jeiStartX = panelLeft + 10;
        int maxInRow = (panelW - 20) / (jeiItemW + 1);
        maxInRow = Math.max(1, maxInRow);
        for (int i = 0; i < maxInRow && (jeiOffset + i) < filteredItems.size(); i++) {
            int idx = jeiOffset + i;
            int x = jeiStartX + i * (jeiItemW + 1);
            int y = jeiTop + (jeiH - jeiItemH) / 2;
            if (mx >= x && mx < x + jeiItemW && my >= y && my < y + jeiItemH) {
                ItemInfo clickedInfo = filteredItems.get(idx);
                Item clicked = clickedInfo.item;
                if (barterMode) {
                    if (selectingSecond) {
                        if (!goodsLocked) applySelection(clickedInfo, true);
                    } else {
                        if (!offerLocked) applySelection(clickedInfo, false);
                    }
                } else {
                    if (button == 1 && clicked == selectedItem) {
                        selectedItem = null;
                        selectedStack = null;
                    } else {
                        applySelection(clickedInfo, false);
                    }
                }
                scrollToItem(clicked);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        mx = Math.round(mx / (double) sf);
        my = Math.round(my / (double) sf);
        if (isDraggingScrollbar) {
            int vis = COLS * gridRows;
            int maxScroll = Math.max(0, filteredItems.size() - vis);
            if (maxScroll > 0) {
                int thumbH = Math.max(6, (int)(gridH * (double)vis / Math.max(1, filteredItems.size())));
                int trackH = gridH - thumbH;
                double delta = my - dragStartMouseY;
                int newOffset = dragStartOffset + (int)(delta / trackH * maxScroll);
                scrollOffset = Math.max(0, Math.min(newOffset, maxScroll));
                scrollOffset = (scrollOffset / COLS) * COLS;
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        isDraggingScrollbar = false;
        return super.mouseReleased(Math.round(mx / (double) sf), Math.round(my / (double) sf), button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        mx = Math.round(mx / (double) sf);
        my = Math.round(my / (double) sf);
        int halfW = panelLeft + panelW / 2;
        if (mx < halfW) {
            int max = Math.max(0, filteredItems.size() - COLS * gridRows);
            if (delta < 0) scrollOffset = Math.min(scrollOffset + COLS, max);
            else if (delta > 0) scrollOffset = Math.max(0, scrollOffset - COLS);
        } else {
            int maxInRow = (panelW - 20) / (jeiItemW + 1);
            maxInRow = Math.max(1, maxInRow);
            if (delta < 0) jeiOffset = Math.min(jeiOffset + 1, Math.max(0, filteredItems.size() - maxInRow));
            else if (delta > 0) jeiOffset = Math.max(0, jeiOffset - 1);
        }
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            return super.charTyped(codePoint, modifiers);
        }
        // 其他输入框聚焦时直接交给 super 处理
        boolean otherFocused = (priceBox != null && priceBox.isFocused())
                || (customNameBox != null && customNameBox.isFocused())
                || (qtyBox != null && qtyBox.isFocused());
        if (otherFocused) {
            return super.charTyped(codePoint, modifiers);
        }
        // 无输入框聚焦时输入发送到搜索框
        if (Character.isLetterOrDigit(codePoint) || codePoint == ' ') {
            if (searchBox != null) {
                searchBox.setValue(searchBox.getValue() + codePoint);
            }
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        boolean otherFocused = (priceBox != null && priceBox.isFocused())
                || (customNameBox != null && customNameBox.isFocused())
                || (qtyBox != null && qtyBox.isFocused());
        if (otherFocused) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == 256) { minecraft.setScreen(parentScreen); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private void applyFilter() {
        String lower = searchQuery.toLowerCase();
        filteredItems = allItems.stream()
                .filter(info -> lower.isEmpty() || info.id.toString().contains(lower) || PinyinUtil.matches(info.searchLower, lower))
                .sorted(com.sellbox.mod.util.ItemSortUtil.comparator(sortMode, info -> info.id, info -> info.nameLower))
                .collect(Collectors.toList());
        int vis = COLS * gridRows;
        int max = Math.max(0, filteredItems.size() - vis);
        scrollOffset = Math.min(scrollOffset, max);
        scrollOffset = (scrollOffset / COLS) * COLS;
    }

    private void rebuildItems() {
        allItems.clear();
        // 代表性栈只在重建列表时取一次：背包里是玩家自己的真实物品（含 NBT/组件），
        // 创造栏里是模组登记的展示形态，两者都拿不到才退回裸栈
        Map<Item, ItemStack> samples = ItemSampleStacks.snapshot();
        Registry<Item> reg = BuiltInRegistries.ITEM;
        for (Item item : reg) {
            ResourceLocation id = reg.getKey(item);
            if (id == null || id.toString().equals("minecraft:air")) continue;
            if (ModNetwork.clientBlacklist != null && ModNetwork.clientBlacklist.contains(id.toString())) continue;
            String nameStr = item.getName(new ItemStack(item)).getString();
            ItemStack sample = samples.get(item);
            if (sample == null || sample.isEmpty()) sample = new ItemStack(item);
            // 展示名取代表性栈（和创造模式物品栏一致），裸栈名只作为搜索关键词保留
            Component displayName = item.getName(sample);
            String displayStr = displayName.getString();
            String searchText = displayStr.equals(nameStr) ? displayStr : nameStr + " " + displayStr;
            allItems.add(new ItemInfo(item, id, displayName, displayStr.toLowerCase(),
                    PinyinUtil.toPinyin(displayStr).toLowerCase(), searchText.toLowerCase(), null, sample));
        }
        applyFilter();
    }

    /** 应用选中：变体条目（携带 NBT）设置对应选中栈并强制严格匹配 */
    private void applySelection(ItemInfo info, boolean second) {
        Item clicked = info.item;
        ItemStack variantStack = ItemStack.EMPTY;
        if (info.variantNbt != null) {
            variantStack = new ItemStack(clicked);
            variantStack.setTag(info.variantNbt.copy());
        }
        if (second) {
            secondSelectedItem = clicked;
            goodsStack = variantStack;
            if (info.variantNbt != null) goodsStrictMatch = true;
        } else if (barterMode) {
            selectedItem = clicked;
            offerStack = variantStack;
            if (info.variantNbt != null) offerStrictMatch = true;
        } else {
            selectedItem = clicked;
            selectedStack = variantStack;
            if (info.variantNbt != null) strictMatch = true;
        }
    }

    /** 渲染用栈：变体条目携带 NBT，否则用创造栏登记的代表性栈（部分模组靠 NBT/组件选模型） */
    private ItemStack renderable(ItemInfo info) {
        if (info.variantNbt != null) {
            ItemStack s = info.sample().copy();
            s.setTag(info.variantNbt.copy());
            return s;
        }
        return info.sample().copy();
    }

    private void drawBox(GuiGraphics g, int x, int y, int w, int h, boolean focused) {
        int col = focused ? T.inputBorderFocused : T.inputBorderUnfocused;
        g.fill(x + 2, y, x + w - 2, y + 1, col);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, col);
        g.fill(x, y + 2, x + 1, y + h - 2, col);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, col);
    }

    private void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + h, color);
        g.fill(x, y + 2, x + w, y + h - 2, color);
    }
}
