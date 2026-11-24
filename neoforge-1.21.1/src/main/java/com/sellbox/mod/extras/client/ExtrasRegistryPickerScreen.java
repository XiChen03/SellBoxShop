package com.sellbox.mod.extras.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** 通用注册表选择页：扫描本客户端全部物品或生物，供管理页添加黑白名单。 */
public class ExtrasRegistryPickerScreen extends Screen {
    private final Screen parent;
    private final boolean entities;
    private final Consumer<String> onPick;
    private final List<Entry> all = new ArrayList<>();
    private final List<Entry> shown = new ArrayList<>();
    private EditBox search;
    private int scroll;
    private int left, top, panelW, panelH;

    private record Entry(String id, String name, ItemStack icon, boolean entity) {}

    public ExtrasRegistryPickerScreen(Screen parent, boolean entities, Consumer<String> onPick) {
        super(Component.literal(entities ? "选择生物" : "选择物品"));
        this.parent = parent;
        this.entities = entities;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        super.init();
        panelW = Math.min(360, this.width - 40);
        panelH = Math.min(240, this.height - 40);
        left = (this.width - panelW) / 2;
        top = (this.height - panelH) / 2;
        search = new EditBox(font, left + 10, top + 24, panelW - 20, 14, Component.literal("搜索"));
        search.setResponder(s -> rebuild());
        addRenderableWidget(search);
        all.clear();
        if (entities) {
            for (var key : BuiltInRegistries.ENTITY_TYPE.keySet()) {
                var type = BuiltInRegistries.ENTITY_TYPE.get(key);
                if (type == null) continue;
                String name = Component.translatable(type.getDescriptionId()).getString();
                all.add(new Entry(key.toString(), name, ItemStack.EMPTY, true));
            }
        } else {
            for (var key : BuiltInRegistries.ITEM.keySet()) {
                var item = BuiltInRegistries.ITEM.get(key);
                if (item == null || item == Items.AIR) continue;
                ItemStack stack = new ItemStack(item);
                all.add(new Entry(key.toString(), stack.getHoverName().getString(), stack, false));
            }
        }
        all.sort(Comparator.comparing(e -> e.name.toLowerCase(Locale.ROOT)));
        rebuild();
    }

    private void rebuild() {
        shown.clear();
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        for (Entry e : all) {
            if (q.isEmpty() || e.name.toLowerCase(Locale.ROOT).contains(q)
                    || e.id.toLowerCase(Locale.ROOT).contains(q)) {
                shown.add(e);
            }
        }
        scroll = 0;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, 0xE605070C);
        g.fill(left, top, left + panelW, top + panelH, 0xFF0B0D16);
        g.drawString(font, title, left + 10, top + 8, 0xFFFFFFFF, true);
        search.render(g, mx, my, pt);
        int listTop = top + 44;
        int rowH = 16;
        int rows = (top + panelH - 8 - listTop) / rowH;
        int maxScroll = Math.max(0, shown.size() - rows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        for (int i = 0; i < rows && scroll + i < shown.size(); i++) {
            Entry e = shown.get(scroll + i);
            int y = listTop + i * rowH;
            boolean hov = mx >= left + 6 && mx < left + panelW - 6 && my >= y && my < y + rowH - 1;
            g.fill(left + 6, y, left + panelW - 6, y + rowH - 1, hov ? 0xFF1C2A44 : 0x80101826);
            if (e.entity) {
                g.fill(left + 10, y + 3, left + 22, y + 13, 0xFF55AAFF);
                g.drawString(font, "E", left + 14, y + 4, 0xFF0B0D16, false);
            } else {
                ItemStack icon = e.icon;
                if (!icon.isEmpty()) com.sellbox.mod.util.ItemIcons.render(g, icon, left + 10, y - 1);
            }
            g.drawString(font, font.plainSubstrByWidth(e.name, panelW - 70), left + 28, y + 4, 0xFFFFFFFF, false);
            g.drawString(font, e.id, left + panelW - 8 - font.width(e.id), y + 4, 0xFF8A93A6, false);
        }
        if (shown.isEmpty()) g.drawString(font, "没有匹配项", left + 10, listTop + 4, 0xFF8A93A6, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (search != null && search.mouseClicked(mx, my, button)) return true;
        if (button == 0) {
            int listTop = top + 44;
            int rowH = 16;
            int rows = (top + panelH - 8 - listTop) / rowH;
            if (mx >= left + 6 && mx < left + panelW - 6) {
                int row = (int) ((my - listTop) / rowH);
                if (row >= 0 && row < rows && scroll + row < shown.size()) {
                    onPick.accept(shown.get(scroll + row).id);
                    Minecraft.getInstance().setScreen(parent);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY) * 3);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (search != null && search.isFocused()) return search.keyPressed(key, scan, mods);
        if (key == 256) { Minecraft.getInstance().setScreen(parent); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
