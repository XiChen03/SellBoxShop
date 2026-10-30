package com.sellbox.mod.gui;

import com.sellbox.mod.network.ModPayloads;
import com.sellbox.mod.network.PricingPayload;
import com.sellbox.mod.shop.PlayerInventoryPickerScreen;
import com.sellbox.mod.shop.ShopButton;
import com.sellbox.mod.util.FontUtil;
import com.sellbox.mod.util.PinyinUtil;
import com.sellbox.mod.util.ItemSortUtil;
import com.sellbox.mod.util.ItemIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.stream.Collectors;

public class PricingScreen extends Screen {
    private final GuiTheme T = GuiTheme.get();
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 240;
    private static final int LIST_LEFT = 12;
    private static final int LIST_TOP = 66;
    private static final int LIST_WIDTH = 316;
    private static final int ROW_HEIGHT = 20;
    private static final int VISIBLE_ROWS = 7;
    private static final int LIST_BOTTOM = LIST_TOP + VISIBLE_ROWS * ROW_HEIGHT;

    private List<ItemInfo> allItems = new ArrayList<>();
    private List<ItemInfo> nbtItems = new ArrayList<>();
    private List<ItemInfo> filteredItems = new ArrayList<>();
    private Set<String> selectedKeys = new HashSet<>();
    private com.sellbox.mod.shop.ShopButton pickBtn;
    private ItemInfo hoveredItemInfo;
    private boolean hoveredIsNbtArea;

    private String searchQuery = "";
    private com.sellbox.mod.util.ItemSortUtil.SortMode sortMode = com.sellbox.mod.util.ItemSortUtil.SortMode.FIRST_LETTER;
    private FilterMode filterMode = FilterMode.PRICED;
    private int scrollOffset = 0;

    private EditBox searchBox;
    private EditBox priceBox;

    private static final int SCROLLBAR_WIDTH = 6;
    private int scrollbarTrackTop;
    private int scrollbarTrackHeight;
    private boolean isDraggingScrollbar;
    private int dragStartMouseY;
    private int dragStartScrollOffset;

    private float sf;

    /** 本次打开定价 GUI 期间通过背包选品添加的键：关闭时若未定价（价格≤0）则清除，下次需重新添加 */
    private final Set<String> sessionAddedKeys = new HashSet<>();

    private enum FilterMode { ALL, PRICED, UNPRICED, NBT }

    private static record ItemInfo(Item item, ResourceLocation id, String key, Component name, int price, String nameLower, String pinyin,
                                   net.minecraft.nbt.CompoundTag variantNbt, String nbtSnbt) {
        ItemInfo(Item item, ResourceLocation id, String key, Component name, int price, String nameLower, String pinyin) {
            this(item, id, key, name, price, nameLower, pinyin, null, null);
        }
    }

    /** 背包选品定价按钮（仅在 NBT 分类显示） */

    public PricingScreen() {
        super(Component.translatable("gui.sellbox.pricing_title"));
    }

    @Override
    protected void init() {
        super.init();
        rebuildItemList();

        int cx = (width - PANEL_WIDTH) / 2;
        int cy = (height - PANEL_HEIGHT) / 2;
        scrollbarTrackTop = cy + LIST_TOP;
        scrollbarTrackHeight = VISIBLE_ROWS * ROW_HEIGHT;
        sf = (float) getMinecraft().getWindow().getGuiScaledHeight() / 256f;
        if (sf < 0.5f) sf = 0.5f;
        if (sf > 2f) sf = 2f;

        int searchH = 16;
        searchBox = new EditBox(font, cx + LIST_LEFT + 2, cy + 26 + 1, 156, searchH - 2, Component.literal(""));
        searchBox.setBordered(false);
        searchBox.setMaxLength(64);
        searchBox.setResponder(this::onSearchChanged);
        addRenderableWidget(searchBox);

        int sortBtnH = 14;
        int sortBtnX = cx + LIST_LEFT + 166;
        addRenderableWidget(new ShopButton(sortBtnX, cy + 24, 24, sortBtnH,
                Component.translatable("gui.sellbox.pricing_sort_id"),
                btn -> { sortMode = ItemSortUtil.SortMode.ID; applyFilter(); }));

        addRenderableWidget(new ShopButton(sortBtnX + 28, cy + 24, 48, sortBtnH,
                Component.translatable("gui.sellbox.pricing_sort_first_letter"),
                btn -> { sortMode = ItemSortUtil.SortMode.FIRST_LETTER; applyFilter(); }));

        // 背包选品定价按钮：仅在 NBT 定价分类显示（默认隐藏）
        pickBtn = new ShopButton(sortBtnX + 80, cy + 24, 72, sortBtnH,
                Component.translatable("gui.sellbox.pricing_pick_inventory"),
                btn -> openInventoryPicker());
        pickBtn.visible = false;
        addRenderableWidget(pickBtn);

        int filterRowY = cy + 46;
        int filterBtnH = 14;
        addRenderableWidget(new ShopButton(cx + LIST_LEFT, filterRowY, 50, filterBtnH,
                Component.translatable("gui.sellbox.pricing_filter_all"),
                btn -> { filterMode = FilterMode.ALL; applyFilter(); }));

        addRenderableWidget(new ShopButton(cx + LIST_LEFT + 54, filterRowY, 60, filterBtnH,
                Component.translatable("gui.sellbox.pricing_filter_priced"),
                btn -> { filterMode = FilterMode.PRICED; applyFilter(); }));

        addRenderableWidget(new ShopButton(cx + LIST_LEFT + 118, filterRowY, 60, filterBtnH,
                Component.translatable("gui.sellbox.pricing_filter_unpriced"),
                btn -> { filterMode = FilterMode.UNPRICED; applyFilter(); }));

        addRenderableWidget(new ShopButton(cx + LIST_LEFT + 182, filterRowY, 70, filterBtnH,
                Component.translatable("gui.sellbox.pricing_filter_nbt"),
                btn -> { filterMode = FilterMode.NBT; applyFilter(); }));

        int bottomY = cy + PANEL_HEIGHT - 26;
        int gap = 4;

        priceBox = new EditBox(font, cx + LIST_LEFT + 2, bottomY + 1, 76, 14, Component.literal("0"));
        priceBox.setBordered(false);
        priceBox.setMaxLength(9);
        priceBox.setFilter(s -> s.matches("-?\\d*"));
        addRenderableWidget(priceBox);

        int btnX = cx + LIST_LEFT + 80 + gap;
        addRenderableWidget(new ShopButton(btnX, bottomY, 50, 16,
                Component.translatable("gui.sellbox.pricing_apply"),
                btn -> applyPrices()));

        btnX += 50 + gap;
        addRenderableWidget(new ShopButton(btnX, bottomY, 60, 16,
                Component.literal("删除定价"),
                btn -> deletePrices()));

        btnX += 60 + gap;
        addRenderableWidget(new ShopButton(btnX, bottomY, 60, 16,
                Component.translatable("gui.sellbox.pricing_clear_sel"),
                btn -> clearSelection()));

        btnX += 60 + gap;
        addRenderableWidget(new ShopButton(btnX, bottomY, 50, 16,
                Component.literal("完成"),
                btn -> onClose()));

        // 所有控件创建后重新过滤：确保背包选品定价按钮可见性按当前分类正确（init 开头的 rebuildItemList 在 pickBtn 创建前执行）
        applyFilter();
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        int cx = (width - PANEL_WIDTH) / 2;
        int cy = (height - PANEL_HEIGHT) / 2;

        renderBackground(gui, mouseX, mouseY, partialTick);

        gui.fill(cx, cy, cx + PANEL_WIDTH, cy + PANEL_HEIGHT, T.popupBg);
        gui.fill(cx + 2, cy, cx + PANEL_WIDTH - 2, cy + 1, T.accentStripe);
        FontUtil.drawAutoFitText(gui, font, title.getString(), cx + LIST_LEFT, cy + 8, PANEL_WIDTH - 20, T.textTitle);
 
        boolean searchFocused = searchBox != null && searchBox.isFocused();
        drawBox(gui, cx + LIST_LEFT, cy + 26, 160, 16, searchFocused);
        if (searchBox != null && searchBox.getValue().isEmpty() && !searchFocused) {
            FontUtil.drawAutoFitText(gui, font, Component.translatable("gui.sellbox.pricing_search").getString(),
                    cx + LIST_LEFT + (int) Math.round(6 * sf), cy + 28 + (int) Math.round(3 * sf), 148, T.textHint);
        }
 
        boolean priceFocused = priceBox != null && priceBox.isFocused();
        drawBox(gui, cx + LIST_LEFT, cy + PANEL_HEIGHT - 26, 80, 16, priceFocused);
        if (priceBox != null && priceBox.getValue().isEmpty() && !priceFocused) {
            FontUtil.drawAutoFitText(gui, font, "价格", cx + LIST_LEFT + (int) Math.round(6 * sf), cy + PANEL_HEIGHT - 24, 68, T.textHint);
        }

        for (var widget : renderables) {
            widget.render(gui, mouseX, mouseY, partialTick);
        }

        gui.enableScissor(cx + LIST_LEFT, cy + LIST_TOP, cx + LIST_LEFT + LIST_WIDTH, cy + LIST_BOTTOM);
        renderItemList(gui, cx, cy, mouseX, mouseY);
        gui.disableScissor();

        renderScrollbar(gui, cx, cy);

        int selCount = selectedKeys.size();
        Component selText = Component.translatable("gui.sellbox.pricing_selected", selCount);
        FontUtil.drawAutoFitText(gui, font, selText.getString(), cx + LIST_LEFT + 260, cy + 48, 60, T.textMuted);

        // 悬浮显示（最后绘制，保证置顶不被列表元素遮挡）：图标 → 原版物品标签；NBT 文本区 → NBT 数据框
        if (hoveredItemInfo != null && hoveredItemInfo.variantNbt() != null) {
            if (hoveredIsNbtArea) {
                renderNbtBox(gui, font, hoveredItemInfo.variantNbt().toString(), mouseX, mouseY);
            } else {
                var registries = minecraft.level != null ? minecraft.level.registryAccess() : null;
                ItemStack tipStack = com.sellbox.mod.util.ItemVariantUtil.stackFromComponentsSnbt(hoveredItemInfo.item(), hoveredItemInfo.variantNbt(), registries);
                net.minecraft.world.item.TooltipFlag flag = net.minecraft.world.item.TooltipFlag.Default.NORMAL;
                java.util.List<Component> tips = tipStack.getTooltipLines(Item.TooltipContext.EMPTY, minecraft.player, flag);
                gui.renderComponentTooltip(font, tips, mouseX, mouseY);
            }
        }
    }

    /** 透明背景（与 1.20.1 一致，避免默认 dirt 背景干扰） */
    @Override
    public void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        gui.fill(0, 0, this.width, this.height, T.screenBackground);
    }

    /** 渲染 NBT 数据悬浮框：完整 SNBT 按宽度换行多行显示 */
    private void renderNbtBox(GuiGraphics g, net.minecraft.client.gui.Font font, String snbt, int mouseX, int mouseY) {
        java.util.List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (char c : snbt.toCharArray()) {
            cur.append(c);
            if (font.width(cur.toString()) > 200) {
                lines.add(cur.toString());
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());

        int lineH = font.lineHeight + 1;
        int boxW = 240;
        int boxH = lines.size() * lineH + 8;
        int boxX = mouseX + 8;
        if (boxX + boxW > width) boxX = Math.max(2, mouseX - boxW - 8);
        int boxY = mouseY + 6;
        if (boxY + boxH > height) boxY = Math.max(2, mouseY - boxH - 6);
        g.fill(boxX, boxY, boxX + boxW, boxY + boxH, T.nbtBoxOuter);
        g.fill(boxX + 1, boxY + 1, boxX + boxW - 1, boxY + boxH - 1, T.nbtBoxInner);
        int ty = boxY + 4;
        for (String line : lines) {
            g.drawString(font, line, boxX + 4, ty, T.textSecondary);
            ty += lineH;
        }
    }

    private void renderItemList(GuiGraphics gui, int cx, int cy, int mouseX, int mouseY) {
        int y = cy + LIST_TOP;
        int endY = cy + LIST_BOTTOM;
        hoveredItemInfo = null;
        hoveredIsNbtArea = false;

        for (int i = scrollOffset; i < filteredItems.size() && y + ROW_HEIGHT <= endY; i++, y += ROW_HEIGHT) {
            ItemInfo info = filteredItems.get(i);
            boolean hovered = mouseX >= cx + LIST_LEFT && mouseX < cx + LIST_LEFT + LIST_WIDTH && mouseY >= y && mouseY < y + ROW_HEIGHT;
            boolean selected = selectedKeys.contains(info.key());

            if (hovered) {
                gui.fill(cx + LIST_LEFT, y, cx + LIST_LEFT + LIST_WIDTH, y + ROW_HEIGHT, T.cellHoveredFill);
                // 悬浮目标：图标区域 → 原版物品标签；NBT 文本区 → NBT 数据框
                if (mouseX >= cx + LIST_LEFT + 18 && mouseX < cx + LIST_LEFT + 34) {
                    hoveredItemInfo = info;
                    hoveredIsNbtArea = false;
                } else if (info.nbtSnbt() != null && mouseX >= cx + LIST_LEFT + 38) {
                    hoveredItemInfo = info;
                    hoveredIsNbtArea = true;
                }
            }

            String check = selected ? "☑" : "☐";
            FontUtil.drawAutoFitText(gui, font, check, cx + LIST_LEFT + 2, y + 4, 12, selected ? T.textTitle : T.textDisabled);

            var registries = minecraft.level != null ? minecraft.level.registryAccess() : null;
            ItemStack render = com.sellbox.mod.util.ItemVariantUtil.stackFromComponentsSnbt(info.item(), info.variantNbt(), registries);
            ItemIcons.render(gui, render, cx + LIST_LEFT + 18, y + 2);
            FontUtil.drawAutoFitText(gui, font, info.name().getString(), cx + LIST_LEFT + 38, y + 4, LIST_WIDTH - 80, T.textSecondary);

            String priceStr = info.price() > 0 ? String.valueOf(info.price()) : "-";
            int priceColor = info.price() > 0 ? T.textPrice : T.textDisabled;
            FontUtil.drawAutoFitText(gui, font, priceStr, cx + LIST_LEFT + LIST_WIDTH - font.width(priceStr) - 4, y + 4, 60, priceColor);
        }
    }

    private void renderScrollbar(GuiGraphics gui, int cx, int cy) {
        int maxScroll = Math.max(0, currentListSize() - VISIBLE_ROWS);
        if (maxScroll <= 0) return;

        int sbX = cx + LIST_LEFT + LIST_WIDTH + 4;
        int totalHeight = VISIBLE_ROWS * ROW_HEIGHT;

        gui.fill(sbX, scrollbarTrackTop, sbX + SCROLLBAR_WIDTH, scrollbarTrackTop + totalHeight, T.scrollbarTrack);

        int thumbHeight = Math.max(12, totalHeight * VISIBLE_ROWS / currentListSize());
        int thumbY = scrollbarTrackTop + (totalHeight - thumbHeight) * scrollOffset / maxScroll;
        gui.fill(sbX, thumbY, sbX + SCROLLBAR_WIDTH, thumbY + thumbHeight, T.scrollbarThumb);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        int cx = (width - PANEL_WIDTH) / 2;
        int cy = (height - PANEL_HEIGHT) / 2;
        int listX = cx + LIST_LEFT;
        int listY = cy + LIST_TOP;

        int sbX = cx + LIST_LEFT + LIST_WIDTH + 4;
        if (mouseX >= sbX && mouseX < sbX + SCROLLBAR_WIDTH && mouseY >= listY && mouseY < listY + VISIBLE_ROWS * ROW_HEIGHT) {
            int maxScroll = Math.max(0, currentListSize() - VISIBLE_ROWS);
            if (maxScroll > 0) {
                int totalHeight = VISIBLE_ROWS * ROW_HEIGHT;
                int thumbHeight = Math.max(12, totalHeight * VISIBLE_ROWS / currentListSize());
                int thumbY = listY + (totalHeight - thumbHeight) * scrollOffset / maxScroll;
                if (mouseY >= thumbY && mouseY < thumbY + thumbHeight) {
                    isDraggingScrollbar = true;
                    dragStartMouseY = (int) mouseY;
                    dragStartScrollOffset = scrollOffset;
                } else {
                    scrollOffset = (int) ((mouseY - listY) / (double) totalHeight * maxScroll);
                    scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
                }
                return true;
            }
        }

        if (mouseX >= listX && mouseX < listX + LIST_WIDTH && mouseY >= listY && mouseY < listY + VISIBLE_ROWS * ROW_HEIGHT) {
            int idx = scrollOffset + (int) ((mouseY - listY) / ROW_HEIGHT);
            if (idx >= 0 && idx < filteredItems.size()) {
                ItemInfo info = filteredItems.get(idx);
                String k = info.key();
                if (selectedKeys.contains(k)) selectedKeys.remove(k);
                else selectedKeys.add(k);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDraggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingScrollbar) {
            int cx = (width - PANEL_WIDTH) / 2;
            int cy = (height - PANEL_HEIGHT) / 2;
            int listY = cy + LIST_TOP;
            int totalHeight = VISIBLE_ROWS * ROW_HEIGHT;
            int maxScroll = Math.max(0, currentListSize() - VISIBLE_ROWS);
            if (maxScroll > 0) {
                int thumbHeight = Math.max(12, totalHeight * VISIBLE_ROWS / currentListSize());
                int delta = (int) mouseY - dragStartMouseY;
                double pixelsPerScroll = (double) (totalHeight - thumbHeight) / maxScroll;
                scrollOffset = dragStartScrollOffset + (int) Math.round(delta / pixelsPerScroll);
                scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, currentListSize() - VISIBLE_ROWS);
        if (scrollY < 0) {
            scrollOffset = Math.min(scrollOffset + 1, maxScroll);
        } else if (scrollY > 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
        }
        return true;
    }

    private void onSearchChanged(String query) {
        searchQuery = query.toLowerCase().trim();
        scrollOffset = 0;
        applyFilter();
    }

    private void applyFilter() {
        // 背包选品定价按钮仅 NBT 分类显示
        if (pickBtn != null) pickBtn.visible = filterMode == FilterMode.NBT;
        List<ItemInfo> source = (filterMode == FilterMode.NBT) ? nbtItems : allItems;
        filteredItems = source.stream()
                .filter(this::matchesFilter)
                .filter(this::matchesSearch)
                .sorted(ItemSortUtil.comparator(sortMode, info -> info.id(), info -> info.nameLower()))
                .collect(Collectors.toList());
        int maxScroll = Math.max(0, filteredItems.size() - VISIBLE_ROWS);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    private int currentListSize() {
        return filteredItems.size();
    }

    private boolean matchesFilter(ItemInfo info) {
        return switch (filterMode) {
            case ALL -> true;
            case PRICED -> info.price() > 0;
            case UNPRICED -> info.price() <= 0;
            case NBT -> true;
        };
    }

    private boolean matchesSearch(ItemInfo info) {
        if (searchQuery.isEmpty()) return true;
        if (info.id.toString().toLowerCase().contains(searchQuery)) return true;
        if (com.sellbox.mod.util.PinyinUtil.matches(info.nameLower(), searchQuery)) return true;
        return false;
    }

    private void rebuildItemList() {
        allItems.clear();
        nbtItems.clear();
        selectedKeys.clear();
        Registry<Item> registry = BuiltInRegistries.ITEM;
        for (Item item : registry) {
            ResourceLocation id = registry.getKey(item);
            if (id == null || id.toString().equals("minecraft:air")) continue;
            Component name = item.getName(new ItemStack(item));
            String idStr = id.toString();
            int price = ModPayloads.clientPrices.getOrDefault(idStr, 0);
            String nameStr = name.getString();
            allItems.add(new ItemInfo(item, id, idStr, name, price,
                    nameStr.toLowerCase(),
                    PinyinUtil.toPinyin(nameStr).toLowerCase()));
        }
        // NBT 精确价格条目（/sellbox handsetnbt 指令或背包选品定价设置的物品）：clientPrices 中键含 "|" 的为 NBT 级
        for (var entry : ModPayloads.clientPrices.entrySet()) {
            String key = entry.getKey();
            int pipeIdx = key.indexOf('|');
            if (pipeIdx <= 0) continue;
            String idStr = key.substring(0, pipeIdx);
            String snbt = key.substring(pipeIdx + 1);
            ResourceLocation nid = ResourceLocation.tryParse(idStr);
            if (nid == null || !registry.containsKey(nid)) continue;
            Item nitem = registry.get(nid);
            ItemStack nstack = new ItemStack(nitem);
            net.minecraft.nbt.CompoundTag vnbt = null;
            if (!snbt.isEmpty()) {
                try { vnbt = net.minecraft.nbt.TagParser.parseTag(snbt); } catch (Exception ignored) { }
            }
            String baseName = nstack.getHoverName().getString();
            String shortSnbt = snbt.length() > 18 ? snbt.substring(0, 18) + "…" : snbt;
            Component nbtName = Component.literal(baseName + " §7" + shortSnbt);
            String searchStr = (baseName + " " + snbt).toLowerCase();
            nbtItems.add(new ItemInfo(nitem, nid, key, nbtName, entry.getValue(),
                    searchStr, PinyinUtil.toPinyin(baseName).toLowerCase(),
                    vnbt, snbt));
        }
        applyFilter();
    }

    private void applyPrices() {
        int price;
        try {
            price = Integer.parseInt(priceBox.getValue());
        } catch (NumberFormatException e) {
            return;
        }
        if (selectedKeys.isEmpty()) return;
        if (price < 0) price = 0;

        Map<String, Integer> priceMap = new HashMap<>();
        for (String key : selectedKeys) {
            priceMap.put(key, price);
            ModPayloads.clientPrices.put(key, price);
        }
        if (!priceMap.isEmpty()) {
            PacketDistributor.sendToServer(new PricingPayload.SetPricesPayload(priceMap));
        }
        rebuildItemList();
    }

    private void deletePrices() {
        if (selectedKeys.isEmpty()) return;

        Map<String, Integer> priceMap = new HashMap<>();
        for (String key : selectedKeys) {
            priceMap.put(key, 0);
            ModPayloads.clientPrices.put(key, 0);
        }
        if (!priceMap.isEmpty()) {
            PacketDistributor.sendToServer(new PricingPayload.SetPricesPayload(priceMap));
        }
        rebuildItemList();
    }

    private void clearSelection() {
        selectedKeys.clear();
    }

    private void openInventoryPicker() {
        Minecraft.getInstance().setScreen(new com.sellbox.mod.shop.PlayerInventoryPickerScreen(this, this::onPickFromInventory));
    }

    /** 背包选品回调：选中的物品（带 NBT）按当前价格框数值设置 NBT 精确价格，无 NBT 则按 ID 定价 */
    private void onPickFromInventory(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        int price;
        try {
            price = Integer.parseInt(priceBox != null ? priceBox.getValue() : "0");
        } catch (NumberFormatException e) {
            price = 0;
        }
        if (price < 0) price = 0;
        String idStr = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        // 始终生成 itemId|完整组件SNBT 键（无任何组件时用 {} 占位），保证任何背包选品都出现在 NBT 定价列表
        var registries = minecraft.level != null ? minecraft.level.registryAccess() : null;
        String snbt = registries != null ? com.sellbox.mod.util.ItemVariantUtil.brewNbtSnbt(stack, registries) : null;
        String key = idStr + "|" + (snbt != null ? snbt : "{}");
        sessionAddedKeys.add(key);
        ModPayloads.clientPrices.put(key, price);
        Map<String, Integer> m = new HashMap<>();
        m.put(key, price);
        PacketDistributor.sendToServer(new PricingPayload.SetPricesPayload(m));
        rebuildItemList();
    }

    private void drawBox(GuiGraphics g, int x, int y, int w, int h, boolean focused) {
        int col = focused ? T.inputBorderFocused : T.inputBorderUnfocused;
        g.fill(x + 2, y, x + w - 2, y + 1, col);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, col);
        g.fill(x, y + 2, x + 1, y + h - 2, col);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, col);
    }

    @Override
    public void onClose() {
        // 背包选品添加但未定价（价格≤0）的条目：关闭 GUI 时清除本地缓存并通知服务端删除，下次需重新添加
        if (!sessionAddedKeys.isEmpty()) {
            List<String> deleteNbtKeys = new ArrayList<>();
            Map<String, Integer> deleteIdMap = new HashMap<>();
            for (String key : sessionAddedKeys) {
                Integer p = ModPayloads.clientPrices.get(key);
                if (p == null || p <= 0) {
                    ModPayloads.clientPrices.remove(key);
                    if (key.indexOf('|') >= 0) {
                        deleteNbtKeys.add(key); // NBT 级键：删除服务端 NBT 价格
                    } else {
                        deleteIdMap.put(key, 0); // 纯 ID 键：价格置 0 即删除 ID 级价格
                    }
                }
            }
            if (!deleteNbtKeys.isEmpty()) {
                PacketDistributor.sendToServer(new PricingPayload.DeleteNbtPricesPayload(deleteNbtKeys));
            }
            if (!deleteIdMap.isEmpty()) {
                PacketDistributor.sendToServer(new PricingPayload.SetPricesPayload(deleteIdMap));
            }
            sessionAddedKeys.clear();
        }
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        this.minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
