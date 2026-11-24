package com.sellbox.mod.shop;

import com.sellbox.mod.gui.GuiDraw;
import com.sellbox.mod.shop.network.ShopEditPayload;
import com.sellbox.mod.util.FontUtil;
import com.sellbox.mod.util.PinyinUtil;
import com.sellbox.mod.util.ItemIcons;
import com.sellbox.mod.util.ItemSampleStacks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.stream.Collectors;

public class ShopItemPickerScreen extends Screen {
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

    // 模式切换 + 单价 + 自定义物品名
    private int modeBarY, modeBtnH;
    private int priceBoxX, priceBoxY, priceBoxW, priceBoxH;
    private EditBox priceBox;
    private int modeSellBtnX, modeBuyBtnX, modeBtnW;
    private int matchBtnX, matchBtnW;
    private EditBox customNameBox;
    private int customNameX, customNameY, customNameW, customNameH;
    private boolean barterMode = false;
    private Item secondSelectedItem;
    private boolean selectingSecond = false;
    private boolean offerLocked = false;
    private boolean goodsLocked = false;
    private String barterOfferQty = "1";
    private String barterGoodsQty = "1";
    private boolean isDraggingScrollbar = false;
    private int dragStartMouseY, dragStartOffset;
    private EditBox qtyBox;
    private int qtyBoxX, qtyBoxY, qtyBoxW, qtyBoxH;
    private ShopButton qtyConfirmBtn;
    private ShopButton unlockBtn;

    // 底部按钮
    private int confirmBtnX, backBtnX, btnRowY, btnW, btnH;

    // 分隔线颜色
    private int sepColor() { return T.separatorLine; }

    private String searchQuery = "";
    private com.sellbox.mod.util.ItemSortUtil.SortMode sortMode = com.sellbox.mod.util.ItemSortUtil.SortMode.NAME;
    private final ShopScreen parentScreen;
    private final String categoryName;
    private final int editIndex;
    private final ShopEntry editEntry;

    // ── 多选模式 ──
    /** 预览清单最多显示几行 */
    private static final int MULTI_PREVIEW_ROWS = 3;

    private boolean multiSelect = false;
    /** 多选已选中的条目，保持点击顺序 */
    private final List<ItemInfo> multiPicked = new ArrayList<>();
    private int multiToggleX, multiToggleY, multiToggleW, multiToggleH;
    /** 多选文字说明的右边界（让开「数量:」标签） */
    private int multiLabelRightX = 0;
    /** 预览区悬停时需要显示的物品栈 */
    private ItemStack previewHoverStack = ItemStack.EMPTY;

    private List<ItemInfo> allItems = new ArrayList<>();
    private List<ItemInfo> filteredItems = new ArrayList<>();
    private Item selectedItem;
    private ItemStack selectedStack; // 普通商品严格匹配模式下从背包选中的原始物品（含 NBT）
    private boolean strictMatch = false; // 普通商品：false=ID相同，true=严格匹配(NBT/组件一致)
    private ItemStack offerStack; // 以物易物出价物：严格匹配模式下从背包选中的原始物品
    private ItemStack goodsStack; // 以物易物商品物：总是保留从背包选中的 NBT
    private boolean barterOfferStrict = false; // 以物易物出价物：false=物品ID，true=严格NBT
    private boolean barterGoodsStrict = false; // 以物易物商品物：false=物品ID，true=严格NBT
    private ItemStack gridHoverStack = ItemStack.EMPTY; // 网格/JEI 悬浮条目展示用的物品栈（变体带品质 NBT）
    private int scrollOffset = 0;
    private ShopEntry.TradeType tradeType = ShopEntry.TradeType.BUY;

    private static record ItemInfo(Item item, ResourceLocation id, Component name, String nameLower, String pinyin, String searchLower, net.minecraft.nbt.CompoundTag variantNbt, ItemStack sample) {}

    public ShopItemPickerScreen(ShopScreen parent, String categoryName) {
        this(parent, categoryName, -1, null);
    }

    public ShopItemPickerScreen(ShopScreen parent, String categoryName, int editIndex, ShopEntry editEntry) {
        super(Component.literal(editIndex >= 0 ? "编辑物品" : "选择物品"));
        this.parentScreen = parent;
        this.categoryName = categoryName;
        this.editIndex = editIndex;
        this.editEntry = editEntry;
    }

    @Override
    protected void init() {
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
                // 以物易物编辑：恢复出价物/商品物严格匹配状态与 NBT
                barterOfferStrict = editEntry.barterOfferStrict();
                barterGoodsStrict = editEntry.barterGoodsStrict();
                if (minecraft.level != null) {
                    if (editEntry.barterOfferNbt() != null && selectedItem != null) {
                        var op = ShopEntry.componentPatchFromJson(editEntry.barterOfferNbt(), minecraft.level.registryAccess());
                        if (!op.isEmpty()) {
                            ItemStack s = new ItemStack(selectedItem);
                            s.applyComponents(op);
                            offerStack = s;
                        }
                    }
                    if (editEntry.nbt() != null && secondSelectedItem != null) {
                        var gp = ShopEntry.componentPatchFromJson(editEntry.nbt(), minecraft.level.registryAccess());
                        if (!gp.isEmpty()) {
                            ItemStack g = new ItemStack(secondSelectedItem);
                            g.applyComponents(gp);
                            goodsStack = g;
                        }
                    }
                }
            } else {
                // 普通商品编辑：恢复匹配模式，严格匹配时还原带 NBT 的物品用于展示
                strictMatch = editEntry.strictMatch();
                if (editEntry.nbt() != null && selectedItem != null && minecraft.level != null) {
                    var patch = ShopEntry.componentPatchFromJson(editEntry.nbt(), minecraft.level.registryAccess());
                    if (!patch.isEmpty()) {
                        ItemStack s = new ItemStack(selectedItem);
                        s.applyComponents(patch);
                        selectedStack = s;
                    }
                }
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
        String bpText = "打开背包选取";
        bpBtnW = font.width(bpText) + 12;
        bpBtnX = panelLeft + panelW - bpBtnW - 10;
        bpBtnY = searchY;

        // 以物易物/排序按钮：先算出位置，用于隔离搜索框宽度（搜索框绝不越过排序按钮）
        String barterLbl = barterMode ? "以物易物" : "以钱易物";
        int barterBW = font.width("以物易物") + 12;
        int barterBX = bpBtnX - barterBW - 4;
        String sortLbl = "排序:" + sortMode.getDisplayName();
        int sortBW = font.width(sortLbl) + 12;
        int sortBX = barterBX - sortBW - 4;

        // 搜索框与右侧按钮区域隔离：宽度不超过排序按钮左缘
        searchW = Math.min(gridW, Math.max(8, sortBX - searchX - 6));

        searchBox = new EditBox(font, GuiDraw.innerTextX(searchX), GuiDraw.innerTextY(searchY, searchH),
                GuiDraw.innerWidth(searchW), searchH - 2, Component.literal(""));
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
                    btn.setMessage(Component.literal(barterMode ? "以物易物" : "以钱易物"));
                }));

        // 排序切换按钮（以物易物按钮左侧）
        addRenderableWidget(new ShopButton(sortBX, bpBtnY, sortBW, bpBtnH, Component.literal(sortLbl),
                btn -> {
                    sortMode = sortMode.next();
                    btn.setMessage(Component.literal("排序:" + sortMode.getDisplayName()));
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
        String modeLabel = "模式切换:";
        int modeLabelW = font.width(modeLabel);
        int modeLabelX = panelLeft + 10;
        int buttonStartX = modeLabelX + modeLabelW + 8;
        modeSellBtnX = buttonStartX;
        modeBuyBtnX = modeSellBtnX + modeBtnW + 4;
        matchBtnW = 66;
        matchBtnX = modeBuyBtnX + modeBtnW + 4;

        priceBoxW = 90;
        priceBoxH = modeBtnH;
        priceBoxX = panelLeft + panelW - priceBoxW - 10;
        priceBoxY = modeBarY;

        priceBox = new EditBox(font, GuiDraw.innerTextX(priceBoxX), GuiDraw.innerTextY(priceBoxY, priceBoxH),
                GuiDraw.innerWidth(priceBoxW), priceBoxH - 2, Component.literal(""));
        priceBox.setBordered(false);
        priceBox.setMaxLength(9);
        priceBox.setFilter(s -> s.matches("\\d*"));
        if (editEntry != null) priceBox.setValue(String.valueOf(editEntry.price()));
        addRenderableWidget(priceBox);

        // 自定义物品名（紧挨匹配按钮右侧）：按可用空间收缩，绝不与匹配按钮/价格框重叠
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
            customNameBox = new EditBox(font, GuiDraw.innerTextX(customNameX), GuiDraw.innerTextY(customNameY, customNameH),
                    GuiDraw.innerWidth(customNameW), customNameH - 2, Component.literal(""));
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

        // ── 多选开关：与数量框同一行，靠左 ──
        // 右边界必须让开「数量:」标签，否则「多选 N 项」会和它叠在一起
        multiToggleW = 14;
        multiToggleH = 14;
        multiToggleX = infoLeft + 4;
        multiToggleY = qtyBoxY;
        String qtyLabelText = barterMode ? (selectingSecond ? "商品数量:" : "出价数量:") : "数量:";
        multiLabelRightX = Math.max(multiToggleX + multiToggleW + 20,
                qtyBoxX - font.width(qtyLabelText) - 8);
        qtyBox = new EditBox(font, GuiDraw.innerTextX(qtyBoxX), GuiDraw.innerTextY(qtyBoxY, qtyBoxH),
                GuiDraw.innerWidth(qtyBoxW), qtyBoxH - 2, Component.literal(""));
        qtyBox.setBordered(false);
        qtyBox.setFilter(s -> s.matches("\\d*"));
        qtyBox.setMaxLength(4);
        if (barterMode) {
            qtyBox.setValue(selectingSecond ? barterGoodsQty : barterOfferQty);
        } else if (editEntry != null) {
            // 普通商品编辑：读取原数量
            qtyBox.setValue(String.valueOf(Math.max(1, editEntry.quantity())));
        }
        addRenderableWidget(qtyBox);

        qtyConfirmBtn = new ShopButton(qtyCfmX, qtyBoxY, qtyBtnW, qtyBoxH,
                Component.literal("确定"), btn -> onQtyConfirm());
        addRenderableWidget(qtyConfirmBtn);

        unlockBtn = new ShopButton(unlockBtnX, qtyBoxY, unlockBtnW, qtyBoxH,
                Component.literal("解锁"), btn -> onUnlock());
        addRenderableWidget(unlockBtn);

        // 重置锁定状态：仅新增或非 barter 编辑时重置（编辑 barter 条目时保留恢复的锁定）
        if (editEntry == null || !"barter".equals(editEntry.entryType())) {
            offerLocked = false;
            goodsLocked = false;
        }

        // 模式切换按钮（自定义绘制，不添加为widget避免冲突）
        // 在 mouseClicked 中处理点击

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

        String confirmText = editIndex >= 0 ? "确认修改" : "确认添加";
        addRenderableWidget(new ShopButton(confirmBtnX, btnRowY, btnW, btnH,
                Component.literal(confirmText), btn -> confirm()));
        addRenderableWidget(new ShopButton(backBtnX, btnRowY, btnW, btnH,
                Component.literal("取消"), btn -> minecraft.setScreen(parentScreen)));

        panelH = btnRowY + btnH + 5 - panelTop;
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
                barterGoodsStrict = true;
                secondSelectedItem = stack.getItem();
                goodsStack = stack.copy(); // 商品物总是保留从背包选的 NBT
            } else {
                barterOfferStrict = true;
                selectedItem = stack.getItem();
                offerStack = stack.copy(); // 出价物保留从背包选的 NBT（严格匹配时参与比对）
            }
        } else {
            selectedItem = stack.getItem();
            selectedStack = stack.copy(); // 保留从背包选的 NBT（严格匹配时参与比对）
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
        // 多选模式：把已选清单逐条按当前价格/数量添加
        if (multiSelect && !multiPicked.isEmpty() && !barterMode) {
            int price = 0;
            String priceStr = priceBox != null ? priceBox.getValue().trim() : "";
            if (!priceStr.isEmpty()) {
                try { price = Math.max(0, Integer.parseInt(priceStr)); } catch (NumberFormatException ignored) {}
            }
            int qty = 1;
            String qtyStr = qtyBox != null ? qtyBox.getValue().trim() : "1";
            if (!qtyStr.isEmpty()) {
                try { qty = Math.max(1, Math.min(9999, Integer.parseInt(qtyStr))); } catch (NumberFormatException ignored) {}
            }
            String cName = customNameBox != null ? customNameBox.getValue().trim() : "";
            for (ItemInfo info : multiPicked) {
                String nbtStr = null;
                if (minecraft.level != null) {
                    ItemStack src = info.variantNbt != null ? renderable(info) : info.sample();
                    nbtStr = ShopEntry.componentPatchToJson(src.getComponentsPatch(), minecraft.level.registryAccess());
                }
                PacketDistributor.sendToServer(new ShopEditPayload("add_item", categoryName,
                        info.id.toString(), tradeType.name().toLowerCase(), price, -1, false, "",
                        qty, cName, null, 1, info.variantNbt != null, nbtStr));
            }
            minecraft.setScreen(parentScreen);
            parentScreen.refreshFromConfig();
            return;
        }

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
            // 出价物 = selectedItem（买家支付），商品物 = secondSelectedItem（买家获得）
            ResourceLocation offerId = BuiltInRegistries.ITEM.getKey(selectedItem);
            ResourceLocation goodsId = BuiltInRegistries.ITEM.getKey(secondSelectedItem);
            String cName = customNameBox != null ? customNameBox.getValue().trim() : "";
            String action = editIndex >= 0 ? "edit_barter_item" : "add_barter_item";
            int idx = editIndex >= 0 ? editIndex : -1;
            // 商品物 NBT：总是保留从背包选中的 NBT（格子选中则为空）
            String goodsNbtStr = null;
            if (minecraft.level != null && secondSelectedItem != null) {
                ItemStack g = (goodsStack != null && goodsStack.getItem() == secondSelectedItem)
                        ? goodsStack : new ItemStack(secondSelectedItem);
                goodsNbtStr = ShopEntry.componentPatchToJson(g.getComponentsPatch(), minecraft.level.registryAccess());
            }
            // 出价物严格匹配：受匹配按钮控制；无论是否严格都保存选中出价物的 NBT（非严格时服务端不使用）
            String offerNbtStr = null;
            if (minecraft.level != null && selectedItem != null) {
                ItemStack o = (offerStack != null && offerStack.getItem() == selectedItem)
                        ? offerStack : new ItemStack(selectedItem);
                offerNbtStr = ShopEntry.componentPatchToJson(o.getComponentsPatch(), minecraft.level.registryAccess());
            }
            // extraData = goodsId, tradeType="sell" (占位), price = goodsQty（商品数量）, quantity = offerQty（出价数量）, barterItemId = offerId, barterItemQty = offerQty
            PacketDistributor.sendToServer(new ShopEditPayload(action, categoryName, goodsId.toString(), "sell", goodsQty, idx, false, "", offerQty, cName, offerId.toString(), offerQty, false, goodsNbtStr, barterOfferStrict, offerNbtStr, barterGoodsStrict));
        } else {
            if (selectedItem == null) return;
            int price;
            String priceStr = priceBox != null ? priceBox.getValue().trim() : "";
            if (priceStr.isEmpty()) priceStr = "0"; // 默认 0 元商品
            try {
                price = Integer.parseInt(priceStr);
                if (price < 0) price = 0;
            } catch (NumberFormatException e) { return; }
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
            // 总是序列化选中物品的组件补丁（NBT）随请求上传（服务端按 strictMatch 决定是否参与比对，但 NBT 需保留用于编辑回显）
            String nbtStr = null;
            if (minecraft.level != null) {
                ItemStack src = (selectedStack != null && selectedStack.getItem() == selectedItem)
                        ? selectedStack : new ItemStack(selectedItem);
                nbtStr = ShopEntry.componentPatchToJson(src.getComponentsPatch(), minecraft.level.registryAccess());
            }
            PacketDistributor.sendToServer(new ShopEditPayload(action, categoryName, id.toString(), tradeType.name().toLowerCase(), price, idx, false, "", qty, cName, null, 1, strictMatch, nbtStr));
        }
        minecraft.setScreen(parentScreen);
        parentScreen.refreshFromConfig();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
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
        // 页签：底色用 popupBg（够实，现代主题也看得见），描边用 frameColor。
        // 之前整块用 frameColor 填充，现代主题下 cellBorder=0 → 退化成半透明白，
        // 页签等于没画，文字就"飘"在面板外面了。
        fillRoundedRect(g, tabX, tabY, tabW, tabH + 1, T.popupBg);
        g.fill(tabX + 2, tabY, tabX + tabW - 2, tabY + 1, GuiDraw.frameColor());
        g.fill(tabX, tabY + 2, tabX + 1, tabY + tabH - 1, GuiDraw.frameColor());
        g.fill(tabX + tabW - 1, tabY + 2, tabX + tabW, tabY + tabH - 1, GuiDraw.frameColor());
        g.fill(tabX + 2, tabY + tabH, tabX + tabW - 2, tabY + tabH + 1, T.popupBg);
        g.drawString(font, title, tabX + (tabW - font.width(title)) / 2, tabY + (tabH - font.lineHeight) / 2 + 1, T.textTitle, GuiDraw.shadow());

        super.render(g, mx, my, pt);

        // ======== 搜索框自定义边框 ========
        boolean searchFocused = searchBox != null && searchBox.isFocused();
        drawBox(g, searchX, searchY, searchW, searchH, searchFocused);
        if (searchBox != null && searchBox.getValue().isEmpty() && !searchFocused) {
            FontUtil.drawAutoFitText(g, font, "搜索物品...", GuiDraw.innerTextX(searchX), GuiDraw.innerTextY(searchY, searchH), searchW - 12, T.textHint);
        }

        // ======== 分隔线1 ========
        int sep1Y = searchY + searchH + 4;
        g.fill(panelLeft + 4, sep1Y, panelLeft + panelW - 4, sep1Y + 1, sepColor());

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
            ItemInfo curInfo = filteredItems.get(i);
            boolean multiPickedHere = multiSelect && isMultiPicked(curInfo);
            // 多选模式下高亮只认「是否在清单里」；否则右键取消后 selectedItem 还指着它，染色会残留
            boolean sel = multiSelect ? multiPickedHere : (curInfo.item == selectedItem);
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
        } else if (multiSelect && !multiPicked.isEmpty()) {
            // 多选模式：顶部排列所有已选图标
            renderMultiIconGrid(g, mx, my);
        } else if (selectedItem != null) {
            ItemStack selStack = (selectedStack != null && selectedStack.getItem() == selectedItem)
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
            FontUtil.drawAutoFitCentered(g, font, idStr, infoLeft + infoW / 2, idY, infoW - 8, T.textMuted);
            // 背包标记
            int detailY = idY;
            if (isItemInPlayerInventory(selectedItem)) {
                String owned = "背包已拥有";
                FontUtil.drawAutoFitCentered(g, font, owned, infoLeft + infoW / 2, idY + 10, infoW - 8, T.textInInventory);
                detailY = idY + 10;
            }
            // 模式显示
            String modeStr = "交易模式: " + (tradeType == ShopEntry.TradeType.BUY ? "购买" : "出售");
            FontUtil.drawAutoFitCentered(g, font, modeStr, infoLeft + infoW / 2, detailY + 10, infoW - 8, T.textTitle);
            if (!barterMode) {
                String matchStr = (strictMatch ? "严格匹配" : "ID相同")
                        + ((selectedStack != null && selectedStack.getItem() == selectedItem && !selectedStack.getComponentsPatch().isEmpty()) ? " (含NBT)" : "");
                FontUtil.drawAutoFitCentered(g, font, matchStr, infoLeft + infoW / 2, detailY + 20, infoW - 8, T.textMatchMode);
            }
        } else {
            String hint = "请选择一个物品";
            FontUtil.drawAutoFitCentered(g, font, hint, infoLeft + infoW / 2, infoTop + infoH / 2, infoW - 8, T.textHint);
        }

        // ======== 多选开关（预览行左边的小方块） ========
        renderMultiToggle(g, smx, smy);

        // 多选模式下：预览区改成"已选清单"，按点击顺序逐行列出
        if (multiSelect && !multiPicked.isEmpty()) {
            renderMultiPreview(g, mx, my);
        } else {
            renderSinglePreview(g);
        }

        // ======== 分隔线2 ========
        int sep2Y = jeiTop + jeiH + 2;
        g.fill(panelLeft + 4, sep2Y, panelLeft + panelW - 4, sep2Y + 1, sepColor());

        // ======== JEI 物品候选条 ========
        renderJeiRow(g, mx, my);

        // ======== 分隔线3 ========
        int sep3Y = modeBarY + modeBtnH + 2;
        g.fill(panelLeft + 4, sep3Y, panelLeft + panelW - 4, sep3Y + 1, sepColor());

        // ======== 模式切换标签 ========
        int modeLabelX = panelLeft + 10;
        int buttonStartX = modeSellBtnX;
        String modeLbl = "模式切换:";
        g.drawString(font, modeLbl, modeLabelX, modeBarY + (modeBtnH - font.lineHeight) / 2, T.textMuted, GuiDraw.shadow());
        // 高亮当前选中的模式按钮
        int highlightX = (tradeType == ShopEntry.TradeType.SELL) ? modeSellBtnX : modeBuyBtnX;
        g.fill(highlightX, modeBarY, highlightX + modeBtnW, modeBarY + modeBtnH, T.catSelectedFill);
        // 绘制按钮文字
        FontUtil.drawAutoFitCentered(g, font, "出售模式", modeSellBtnX + modeBtnW / 2, modeBarY + (modeBtnH - font.lineHeight) / 2, modeBtnW - 4, T.textPrimary);
        FontUtil.drawAutoFitCentered(g, font, "购买模式", modeBuyBtnX + modeBtnW / 2, modeBarY + (modeBtnH - font.lineHeight) / 2, modeBtnW - 4, T.textPrimary);

        // ======== 匹配模式切换按钮（严格匹配 / ID相同，仅普通商品；以物易物在出价/商品名字下方各自切换） ========
        if (!barterMode) {
            g.fill(matchBtnX, modeBarY, matchBtnX + matchBtnW, modeBarY + modeBtnH, strictMatch ? T.strictMatchOn : T.strictMatchOff);
            String matchLbl = strictMatch ? "严格匹配" : "ID相同";
            FontUtil.drawAutoFitCentered(g, font, matchLbl, matchBtnX + matchBtnW / 2, modeBarY + (modeBtnH - font.lineHeight) / 2, matchBtnW - 4, T.textPrimary);
        }

        // ======== 价格框边框 ========
        boolean priceFocused = priceBox != null && priceBox.isFocused();
        drawBox(g, priceBoxX, priceBoxY, priceBoxW, priceBoxH, priceFocused);
        if (priceBox != null && priceBox.getValue().isEmpty() && !priceFocused) {
            FontUtil.drawAutoFitText(g, font, "输入价格", GuiDraw.innerTextX(priceBoxX), GuiDraw.innerTextY(priceBoxY, priceBoxH), priceBoxW - 12, T.textHint);
        }
        // 自定义物品名框边框 + 占位文字
        boolean nameFocused = customNameBox != null && customNameBox.isFocused();
        if (customNameBox != null) drawBox(g, customNameX, customNameY, customNameW, customNameH, nameFocused);
        if (customNameBox != null && customNameBox.getValue().isEmpty() && !nameFocused) {
            FontUtil.drawAutoFitText(g, font, "自定义物品名", GuiDraw.innerTextX(customNameX), GuiDraw.innerTextY(customNameY, customNameH), customNameW - 12, T.textHint);
        }
        // 数量框边框 + 占位文字（信息面板右下角）
        boolean qtyFocused = qtyBox != null && qtyBox.isFocused();
        String qtyLabel = barterMode ? (selectingSecond ? "商品数量:" : "出价数量:") : "数量:";
        int qtyLabelW = font.width(qtyLabel);
        int qtyLabelX = Math.max(infoLeft + 2, qtyBoxX - qtyLabelW - 4);
        FontUtil.drawAutoFitText(g, font, qtyLabel, qtyLabelX, qtyBoxY + (qtyBoxH - font.lineHeight) / 2, qtyBoxX - qtyLabelX - 2, T.textSecondary);
        drawBox(g, qtyBoxX, qtyBoxY, qtyBoxW, qtyBoxH, qtyFocused);
        if (qtyBox != null && qtyBox.getValue().isEmpty() && !qtyFocused) {
            FontUtil.drawAutoFitText(g, font, "数量", GuiDraw.innerTextX(qtyBoxX), GuiDraw.innerTextY(qtyBoxY, qtyBoxH), qtyBoxW - 12, T.textHint);
        }

        g.pose().popPose();

        // ======== 悬浮提示（必须在 pose 缩放之外，用屏幕坐标绘制） ========
        if (mx >= bpBtnX && mx <= bpBtnX + bpBtnW && my >= bpBtnY && my <= bpBtnY + bpBtnH) {
            g.renderTooltip(font, Component.literal("点击进入背包选择"), smx, smy);
        }
        if (!barterMode && mx >= matchBtnX && mx <= matchBtnX + matchBtnW
                && my >= modeBarY && my <= modeBarY + modeBtnH) {
            g.renderTooltip(font, Component.literal("商品匹配方式：严格匹配需物品ID+NBT完全一致"), smx, smy);
        }
        // 悬浮品质卡：网格/JEI 悬浮变体条目时显示带 BrewLevel 品质 NBT 的原版资料卡
        if (!gridHoverStack.isEmpty()) g.renderTooltip(font, gridHoverStack, smx, smy);
        // 多选预览区悬浮：显示该行的物品资料卡
        else if (!previewHoverStack.isEmpty()) g.renderTooltip(font, previewHoverStack, smx, smy);
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
        String offerLabel = offerLocked ? "出价 [已锁定]" : (selectingSecond ? "出价" : "出价 [选择中]");
        String goodsLabel = goodsLocked ? "商品 [已锁定]" : (!selectingSecond ? "商品" : "商品 [选择中]");
        FontUtil.drawAutoFitCentered(g, font, offerLabel, infoLeft + half / 2, infoTop + 4, half - 4, selectingSecond ? T.textMuted : T.textTitle);
        FontUtil.drawAutoFitCentered(g, font, goodsLabel, infoLeft + half + half / 2, infoTop + 4, half - 4, selectingSecond ? T.textTitle : T.textMuted);
        // 左侧：出价物品
        if (offerItem != null) {
            int ix1 = infoLeft + (half - iconSize) / 2;
            ItemStack offerStackRender = (offerStack != null && offerStack.getItem() == offerItem)
                    ? offerStack : new ItemStack(offerItem);
            ItemIcons.render(g, offerStackRender, ix1, iconY);
            int borderCol = offerLocked ? T.barterLockedBorder : (selectingSecond ? T.textMuted : T.btnBorder);
            drawItemBorderColor(g, ix1, iconY, iconSize, borderCol);
            String name1 = offerItem.getName(offerStackRender).getString();
            int nameY1 = iconY + iconSize + 2;
            FontUtil.drawAutoFitCentered(g, font, name1, infoLeft + half / 2, nameY1, half - 4,
                    offerLocked ? T.textPrice : (selectingSecond ? T.textMuted : T.catText));
            // 出价物匹配模式（名字下方，可点击切换）
            int offerModeY = nameY1 + font.lineHeight + 2;
            String offerMode = barterOfferStrict ? "严格NBT" : "物品ID";
            FontUtil.drawAutoFitCentered(g, font, offerMode, infoLeft + half / 2, offerModeY, half - 4,
                    barterOfferStrict ? T.textTotal : T.textMuted);
            if (offerLocked) {
                String qtyStr = "x" + barterOfferQty;
                FontUtil.drawAutoFitCentered(g, font, qtyStr, infoLeft + half / 2, offerModeY + font.lineHeight + 2, half - 4, T.textPrice);
            }
        } else {
            String hint = selectingSecond ? "已锁定" : "选择物品";
            FontUtil.drawAutoFitCentered(g, font, hint, infoLeft + half / 2, iconY + iconSize / 2, half - 4, T.textHint);
        }
        // 箭头
        String arrow = " → ";
        int arrowX = infoLeft + half - font.width(arrow) / 2;
        int arrowY = iconY + iconSize / 2 - font.lineHeight / 2;
        g.drawString(font, arrow, arrowX, arrowY, T.textTitle, GuiDraw.shadow());
        // 右侧：商品物品
        if (goodsItem != null) {
            int ix2 = infoLeft + half + (half - iconSize) / 2;
            ItemStack goodsStackRender = (goodsStack != null && goodsStack.getItem() == goodsItem)
                    ? goodsStack : new ItemStack(goodsItem);
            ItemIcons.render(g, goodsStackRender, ix2, iconY);
            int borderCol = goodsLocked ? T.barterLockedBorder : (!selectingSecond ? T.textMuted : T.btnBorder);
            drawItemBorderColor(g, ix2, iconY, iconSize, borderCol);
            String name2 = goodsItem.getName(goodsStackRender).getString();
            int nameY2 = iconY + iconSize + 2;
            FontUtil.drawAutoFitCentered(g, font, name2, infoLeft + half + half / 2, nameY2, half - 4,
                    goodsLocked ? T.textPrice : (!selectingSecond ? T.textMuted : T.catText));
            // 商品物匹配模式（名字下方，可点击切换）
            int goodsModeY = nameY2 + font.lineHeight + 2;
            String goodsMode = barterGoodsStrict ? "严格NBT" : "物品ID";
            FontUtil.drawAutoFitCentered(g, font, goodsMode, infoLeft + half + half / 2, goodsModeY, half - 4,
                    barterGoodsStrict ? T.textTotal : T.textMuted);
            if (goodsLocked) {
                String qtyStr = "x" + barterGoodsQty;
                FontUtil.drawAutoFitCentered(g, font, qtyStr, infoLeft + half + half / 2, goodsModeY + font.lineHeight + 2, half - 4, T.textPrice);
            }
        } else {
            String hint = !selectingSecond ? "已锁定" : "选择物品";
            FontUtil.drawAutoFitCentered(g, font, hint, infoLeft + half + half / 2, iconY + iconSize / 2, half - 4, T.textHint);
        }
    }

    private void drawItemBorderColor(GuiGraphics g, int x, int y, int size, int color) {
        GuiDraw.iconFrame(g, x, y, size);
    }

    private void drawItemBorder(GuiGraphics g, int x, int y, int size) {
        GuiDraw.iconFrame(g, x, y, size);
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
            g.fill(x + 2, y, x + jeiItemW - 2, y + 1, GuiDraw.frameColor());
            g.fill(x + 2, y + jeiItemH - 1, x + jeiItemW - 2, y + jeiItemH, GuiDraw.frameColor());
            g.fill(x, y + 2, x + 1, y + jeiItemH - 2, GuiDraw.frameColor());
            g.fill(x + jeiItemW - 1, y + 2, x + jeiItemW, y + jeiItemH - 2, GuiDraw.frameColor());
            ItemIcons.render(g, renderable(filteredItems.get(idx)), x + (jeiItemW - 16) / 2, y + (jeiItemH - 16) / 2);
            if (hover) gridHoverStack = renderable(filteredItems.get(idx)).copy();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        mx = Math.round(mx / (double) sf);
        my = Math.round(my / (double) sf);
        if (super.mouseClicked(mx, my, button)) return true;

        // 模式按钮点击
        if (my >= modeBarY && my <= modeBarY + modeBtnH) {
            if (mx >= modeSellBtnX && mx <= modeSellBtnX + modeBtnW) {
                tradeType = ShopEntry.TradeType.SELL;
                return true;
            }
            if (mx >= modeBuyBtnX && mx <= modeBuyBtnX + modeBtnW) {
                tradeType = ShopEntry.TradeType.BUY;
                return true;
            }
            // 匹配模式切换（仅普通商品；以物易物在出价/商品名字下方各自切换）
            if (!barterMode && mx >= matchBtnX && mx <= matchBtnX + matchBtnW) {
                strictMatch = !strictMatch;
                return true;
            }
        }

        // 多选开关点击
        if (mx >= multiToggleX && mx < multiToggleX + multiToggleW
                && my >= multiToggleY && my < multiToggleY + multiToggleH) {
            multiSelect = !multiSelect;
            if (!multiSelect) multiPicked.clear();
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
                } else if (multiSelect) {
                    // 多选模式：左键切换加入/移除，右键只移除
                    boolean removed;
                    if (button == 1) {
                        removed = multiPicked.removeIf(picked -> picked.item == clickedInfo.item);
                    } else {
                        removed = multiPicked.removeIf(picked -> picked.item == clickedInfo.item);
                        if (!removed) multiPicked.add(clickedInfo);
                    }
                    // 被移除的那一项要同时清掉「当前选中」，否则左上的高亮不会消失
                    if (removed && selectedItem == clickedInfo.item) {
                        selectedItem = null;
                        selectedStack = null;
                    } else if (!removed) {
                        selectedItem = clickedInfo.item;
                        selectedStack = null;
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
                    barterOfferStrict = !barterOfferStrict;
                    return true;
                }
                if (onRight && secondSelectedItem != null) {
                    barterGoodsStrict = !barterGoodsStrict;
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

        // 多选：右键点右侧排列的图标 = 从清单里移除该物品
        if (multiSelect && !barterMode && !multiPicked.isEmpty()
                && mx >= infoLeft && mx < infoLeft + infoW
                && my >= infoTop + 8 && my < multiPreviewBottomY() - multiPreviewHeight() - 4) {
            int iconSize = 16;
            int cell = iconSize + 2;
            int cols = Math.max(1, (infoW - 8) / cell);
            int slot = (int) (((mx - (infoLeft + 4)) / cell) + ((my - (infoTop + 8)) / cell) * cols);
            int localX = (int) ((mx - (infoLeft + 4)) % cell);
            int localY = (int) ((my - (infoTop + 8)) % cell);
            if (slot >= 0 && slot < multiPicked.size() && localX < iconSize && localY < iconSize) {
                if (button == 1) {
                    ItemInfo removedInfo = multiPicked.remove(slot);
                    if (removedInfo != null && selectedItem == removedInfo.item) {
                        selectedItem = null;
                        selectedStack = null;
                    }
                    return true;
                }
                // 左键：定位到左侧网格中对应的物品
                scrollToItem(multiPicked.get(slot).item);
                return true;
            }
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
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        mx = Math.round(mx / (double) sf);
        my = Math.round(my / (double) sf);
        int halfW = panelLeft + panelW / 2;
        if (mx < halfW) {
            int max = Math.max(0, filteredItems.size() - COLS * gridRows);
            if (sy < 0) scrollOffset = Math.min(scrollOffset + COLS, max);
            else if (sy > 0) scrollOffset = Math.max(0, scrollOffset - COLS);
        } else {
            int maxInRow = (panelW - 20) / (jeiItemW + 1);
            maxInRow = Math.max(1, maxInRow);
            if (sy < 0) jeiOffset = Math.min(jeiOffset + 1, Math.max(0, filteredItems.size() - maxInRow));
            else if (sy > 0) jeiOffset = Math.max(0, jeiOffset - 1);
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
            variantStack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(info.variantNbt.copy()));
        }
        if (second) {
            secondSelectedItem = clicked;
            goodsStack = variantStack;
            if (info.variantNbt != null) barterGoodsStrict = true;
        } else if (barterMode) {
            selectedItem = clicked;
            offerStack = variantStack; // 以物易物出价物：变体带品质 NBT，强制严格
            if (info.variantNbt != null) barterOfferStrict = true;
        } else {
            selectedItem = clicked;
            selectedStack = variantStack;
            if (info.variantNbt != null) strictMatch = true;
        }
        if (barterMode) return;
    }

    /** 渲染用栈：变体条目携带 custom_data 品质 NBT，否则用创造栏登记的代表性栈（部分模组靠 NBT/组件选模型） */
    private ItemStack renderable(ItemInfo info) {
        if (info.variantNbt != null) {
            ItemStack s = info.sample().copy();
            s.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(info.variantNbt.copy()));
            return s;
        }
        return info.sample().copy();
    }


    /** 单个物品的预览行（原有逻辑） */
    private void renderSinglePreview(GuiGraphics g) {
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
            FontUtil.drawAutoFitText(g, font, preview, infoLeft + 22, previewY, infoW - 26, T.textTotal);
        } else if (selectedItem != null) {
            String itemName = selectedItem.getName(new ItemStack(selectedItem)).getString();
            String qtyStr = qtyVal.isEmpty() || qtyVal.equals("1") ? "" : "x" + qtyVal;
            String preview = "¥" + (priceVal.isEmpty() ? "0" : priceVal) + " → " + itemName + qtyStr;
            FontUtil.drawAutoFitText(g, font, preview, infoLeft + 22, previewY, infoW - 26, T.textTotal);
        }
    }

    /** 多选开关：小方块 + 勾 + 文字说明 */
    private void renderMultiToggle(GuiGraphics g, int smx, int smy) {
        int x = multiToggleX, y = multiToggleY, w = multiToggleW, h = multiToggleH;
        boolean hover = smx >= x && smx < x + w && smy >= y && smy < y + h;
        GuiDraw.inputBox(g, x, y, w, h, false);
        if (multiSelect) {
            // 勾选态：实心色块 + 对勾
            GuiDraw.fillRounded(g, x + 1, y + 1, w - 2, h - 2, T.cellSelectedFill);
            g.drawString(font, "v", x + 4, y + 3, T.textPrimary, GuiDraw.shadow());
        } else if (hover) {
            GuiDraw.fillRounded(g, x + 1, y + 1, w - 2, h - 2, T.cellHoveredFill);
        }
        // 文字说明：勾选后变亮色，未勾选用次要色
        String label = multiSelect
                ? "多选 " + multiPicked.size() + " 项"
                : "多选";
        int labelX = x + w + 4;
        int labelW = Math.max(8, multiLabelRightX - labelX);
        FontUtil.drawAutoFitText(g, font, label, labelX,
                y + (h - font.lineHeight) / 2 + 1, labelW,
                multiSelect ? T.textPrimary : T.textMuted);
    }

    /**
     * 多选预览：上方图标网格 + 下方文字清单。
     * <p>图标按选中顺序从左到右排列（每行 {@link #MULTI_ICON_COLS} 个），
     * 鼠标悬停任一图标/清单行都会弹出该物品的资料卡。
     */
    private void renderMultiPreview(GuiGraphics g, int mx, int my) {
        previewHoverStack = ItemStack.EMPTY;

        int lineH = font.lineHeight + 2;
        int maxRows = Math.min(multiPicked.size(), MULTI_PREVIEW_ROWS);
        boolean overflow = multiPicked.size() > MULTI_PREVIEW_ROWS;

        // 从多选开关那一行的上方往上排，底部留 4px，避免和「多选 N 项 / 数量」这行重叠
        int bottom = multiPreviewBottomY();
        int listTop = bottom - multiPreviewHeight();
        int start = Math.max(0, multiPicked.size() - maxRows);

        for (int i = 0; i < maxRows; i++) {
            ItemInfo info = multiPicked.get(start + i);
            int rowY = listTop + i * lineH;
            boolean hover = mx >= infoLeft && mx < infoLeft + infoW && my >= rowY && my < rowY + lineH;
            String label = (start + i + 1) + ". " + info.name.getString();
            if (hover) {
                g.fill(infoLeft, rowY - 1, infoLeft + infoW, rowY + lineH - 1, T.cellHoveredFill);
                previewHoverStack = renderable(info);
            }
            FontUtil.drawAutoFitText(g, font, label, infoLeft + 22, rowY, infoW - 26,
                    hover ? T.textPrimary : T.textTotal);
        }
        if (overflow) {
            FontUtil.drawAutoFitText(g, font, "…共 " + multiPicked.size() + " 项",
                    infoLeft + 22, listTop + maxRows * lineH, infoW - 26, T.textMuted);
        }
    }

    /** 清单区底部（多选开关那一行的上沿，留 4px 间隙） */
    private int multiPreviewBottomY() {
        return multiToggleY - 4;
    }

    /** 清单区总高：可见行 + 可能的「…共 N 项」提示行 */
    private int multiPreviewHeight() {
        if (!multiSelect || multiPicked.isEmpty()) return 0;
        int rows = Math.min(multiPicked.size(), MULTI_PREVIEW_ROWS);
        if (multiPicked.size() > MULTI_PREVIEW_ROWS) rows++;
        return rows * (font.lineHeight + 2);
    }

    /**
     * 信息面板顶部的图标排列区（多选时显示所有已选物品）。
     * 画在单个大图标的位置，按行铺满面板宽度。
     */
    private void renderMultiIconGrid(GuiGraphics g, int mx, int my) {
        int iconSize = 16;
        int cell = iconSize + 2;
        int cols = Math.max(1, (infoW - 8) / cell);
        int areaTop = infoTop + 8;
        // 底部按真实清单高度让位，不再写死 4 行
        int areaBottom = multiPreviewBottomY() - multiPreviewHeight() - 4;
        int maxRows = Math.max(1, (areaBottom - areaTop) / cell);
        int maxIcons = cols * maxRows;

        int count = Math.min(multiPicked.size(), maxIcons);
        for (int i = 0; i < count; i++) {
            ItemInfo info = multiPicked.get(i);
            int col = i % cols;
            int row = i / cols;
            int x = infoLeft + 4 + col * cell;
            int y = areaTop + row * cell;
            boolean hover = mx >= x && mx < x + iconSize && my >= y && my < y + iconSize;
            if (hover) {
                g.fill(x - 1, y - 1, x + iconSize + 1, y + iconSize + 1, T.cellHoveredFill);
                previewHoverStack = renderable(info);
            }
            ItemIcons.render(g, renderable(info), x, y);
            // 序号角标（左下角小字），方便对应下方清单
            String idx = String.valueOf(i + 1);
            g.pose().pushPose();
            g.pose().translate(x + 1, y + iconSize - 5, 200);
            g.pose().scale(0.6f, 0.6f, 1.0f);
            g.drawString(font, idx, 0, 0, T.textPrimary, GuiDraw.shadow());
            g.pose().popPose();
        }
        if (multiPicked.size() > maxIcons) {
            String more = "+" + (multiPicked.size() - maxIcons);
            FontUtil.drawAutoFitText(g, font, more, infoLeft + 4,
                    areaTop + maxRows * cell, infoW - 8, T.textMuted);
        }
    }

    /** 该条目是否已在多选清单里（按 item 比较，和 toggle 逻辑一致） */
    private boolean isMultiPicked(ItemInfo info) {
        for (ItemInfo picked : multiPicked) {
            if (picked.item == info.item) return true;
        }
        return false;
    }

    private void drawBox(GuiGraphics g, int x, int y, int w, int h, boolean focused) {
        GuiDraw.inputBox(g, x, y, w, h, focused);
    }

    private void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + h, color);
        g.fill(x, y + 2, x + w, y + h - 2, color);
    }
}
