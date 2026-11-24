package com.sellbox.mod.extras.client;

import com.sellbox.mod.gui.GuiDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/** 管理 · 扫地女仆：开关、间隔、阈值、垃圾箱和三类过滤表。 */
final class ExtrasSweeperPage {
    private ExtrasSweeperPage() {
    }

    static void render(ExtrasScreen host, GuiGraphics g, int l, int t, int r, int b, int mx, int my) {
        g.drawString(host.font(), ExtrasUi.tr("extras.sweeper.interval"), l, t + 3, host.T.textMuted, GuiDraw.shadow());
        if (host.sweeperIntervalBox != null) host.drawBoxAround(g, host.sweeperIntervalBox);
        g.drawString(host.font(), ExtrasUi.tr("extras.sweeper.overload"), l + 76, t + 3, host.T.textMuted, GuiDraw.shadow());
        if (host.sweeperOverloadBox != null) host.drawBoxAround(g, host.sweeperOverloadBox);
        g.drawString(host.font(), ExtrasUi.tr("extras.sweeper.dustbins"), l + 176, t + 3, host.T.textMuted, GuiDraw.shadow());
        if (host.sweeperDustbinBox != null) host.drawBoxAround(g, host.sweeperDustbinBox);
        host.button(g, r - 42, t, 42, 14, ExtrasUi.tr("extras.admin.save"), true, mx, my, () -> {
            int interval = ExtrasUi.parseInt(host.sweeperIntervalBox);
            int overload = ExtrasUi.parseInt(host.sweeperOverloadBox);
            int dustbins = ExtrasUi.parseInt(host.sweeperDustbinBox);
            if (interval < 0 || overload < 1 || dustbins < 1) {
                ExtrasNoticeQueue.pushPlain(ExtrasUi.tr("extras.msg.admin_bad_value"), "error");
                return;
            }
            ExtrasActions.sweeperSave(interval, overload, dustbins,
                    host.sweeperChatBox == null ? "" : host.sweeperChatBox.getValue(),
                    host.sweeperCountdownBox == null ? "" : host.sweeperCountdownBox.getValue(),
                    host.sweeperAfterBox == null ? "" : host.sweeperAfterBox.getValue());
        });

        host.button(g, l, t + 18, 64, 14,
                ExtrasUi.tr(ExtrasClientState.sweeperEnabled ? "extras.sweeper.on" : "extras.sweeper.off"),
                ExtrasClientState.sweeperEnabled, mx, my,
                () -> ExtrasActions.sweeperToggle(!ExtrasClientState.sweeperEnabled));
        host.button(g, l + 68, t + 18, 56, 14, ExtrasUi.tr("extras.sweeper.clean"), true, mx, my,
                ExtrasActions::sweeperClean);
        host.button(g, l + 128, t + 18, 60, 14, ExtrasUi.tr("extras.sweeper.open"), false, mx, my, () -> {
            int index = ExtrasUi.parseInt(host.sweeperDustbinIndexBox);
            ExtrasActions.sweeperDustbin(Math.max(1, index) - 1);
        });
        g.drawString(host.font(), ExtrasUi.tr("extras.sweeper.index"), l + 194, t + 21,
                host.T.textMuted, GuiDraw.shadow());
        if (host.sweeperDustbinIndexBox != null) host.drawBoxAround(g, host.sweeperDustbinIndexBox);

        int y = t + 38;
        y = list(host, g, l, y, r, mx, my, "blacklist", ExtrasUi.tr("extras.sweeper.blacklist"),
                ExtrasClientState.sweeperBlacklist, false, host.sweeperShowBlacklist,
                () -> {
                    host.sweeperShowBlacklist = !host.sweeperShowBlacklist;
                    host.sweeperShowWhitelist = false;
                    host.sweeperShowEntities = false;
                });
        y = list(host, g, l, y, r, mx, my, "whitelist", ExtrasUi.tr("extras.sweeper.whitelist"),
                ExtrasClientState.sweeperWhitelist, false, host.sweeperShowWhitelist,
                () -> {
                    host.sweeperShowWhitelist = !host.sweeperShowWhitelist;
                    host.sweeperShowBlacklist = false;
                    host.sweeperShowEntities = false;
                });
        list(host, g, l, y, r, mx, my, "entities", ExtrasUi.tr("extras.sweeper.entities"),
                ExtrasClientState.sweeperExtraEntities, true, host.sweeperShowEntities,
                () -> {
                    host.sweeperShowEntities = !host.sweeperShowEntities;
                    host.sweeperShowBlacklist = false;
                    host.sweeperShowWhitelist = false;
                });
        int baseY = com.sellbox.mod.extras.client.ExtrasScreen.DESIGN_H;
        g.drawString(host.font(), ExtrasUi.tr("extras.sweeper.countdown"), l, baseY - 84,
                host.T.textMuted, GuiDraw.shadow());
        if (host.sweeperCountdownBox != null) host.drawBoxAround(g, host.sweeperCountdownBox);
        g.drawString(host.font(), ExtrasUi.tr("extras.sweeper.after"), l, baseY - 63,
                host.T.textMuted, GuiDraw.shadow());
        if (host.sweeperAfterBox != null) host.drawBoxAround(g, host.sweeperAfterBox);
        g.drawString(host.font(), ExtrasUi.tr("extras.sweeper.chat"), l, baseY - 42,
                host.T.textMuted, GuiDraw.shadow());
        if (host.sweeperChatBox != null) host.drawBoxAround(g, host.sweeperChatBox);
    }

    static boolean handleScroll(ExtrasScreen host, int lx, int ly, int dir) {
        boolean[] hit = {false};
        int y = com.sellbox.mod.extras.client.ExtrasScreen.ADMIN_TOP + 38;
        y = scrollOne(host, lx, ly, dir, y, ExtrasClientState.sweeperBlacklist,
                host.sweeperShowBlacklist, host.sweeperScrollBlacklist,
                v -> host.sweeperScrollBlacklist = v, hit);
        y = scrollOne(host, lx, ly, dir, y, ExtrasClientState.sweeperWhitelist,
                host.sweeperShowWhitelist, host.sweeperScrollWhitelist,
                v -> host.sweeperScrollWhitelist = v, hit);
        scrollOne(host, lx, ly, dir, y, ExtrasClientState.sweeperExtraEntities,
                host.sweeperShowEntities, host.sweeperScrollEntities,
                v -> host.sweeperScrollEntities = v, hit);
        return hit[0];
    }

    private static int scrollOne(ExtrasScreen host, int lx, int ly, int dir, int y,
                                 java.util.List<String> values, boolean expanded, int scroll,
                                 java.util.function.IntConsumer setter, boolean[] hit) {
        int rows = Math.max(1, Math.min(3, values.size()));
        int height = expanded ? 12 + rows * 9 + 4 : 14;
        if (expanded && lx >= 108 && lx <= 408 && ly >= y + 12 && ly < y + 12 + rows * 9) {
            int max = Math.max(0, values.size() - 3);
            setter.accept(Math.max(0, Math.min(max, scroll - dir)));
            hit[0] = true;
        }
        return y + height;
    }

    private static int list(ExtrasScreen host, GuiGraphics g, int l, int y, int r, int mx, int my,
                            String kind, String title, List<String> values, boolean entities,
                            boolean expanded, Runnable toggle) {
        g.drawString(host.font(), title, l, y, host.T.textTitle, GuiDraw.shadow());
        host.button(g, r - 30, y - 1, 14, 12, expanded ? "▲" : "▼", false, mx, my, toggle);
        host.button(g, r - 14, y - 1, 14, 12, "+", true, mx, my,
                () -> Minecraft.getInstance().setScreen(new ExtrasRegistryPickerScreen(host, entities, id -> {
                    if (!values.contains(id)) values.add(id);
                    ExtrasActions.sweeperListAdd(kind, id);
                })));
        y += 12;
        if (!expanded) return y + 2;
        int max = Math.max(0, values.size() - 3);
        int scroll = switch (kind) {
            case "blacklist" -> host.sweeperScrollBlacklist;
            case "whitelist" -> host.sweeperScrollWhitelist;
            default -> host.sweeperScrollEntities;
        };
        scroll = Math.max(0, Math.min(max, scroll));
        switch (kind) {
            case "blacklist" -> host.sweeperScrollBlacklist = scroll;
            case "whitelist" -> host.sweeperScrollWhitelist = scroll;
            default -> host.sweeperScrollEntities = scroll;
        }
        int rows = Math.min(3, values.size());
        for (int i = 0; i < rows; i++) {
            String id = values.get(scroll + i);
            String name = displayName(entities, id);
            String text = name + "  (" + id + ")";
            g.drawString(host.font(), host.font().plainSubstrByWidth(text, r - l - 32),
                    l + 2, y + 1, host.T.textMuted, GuiDraw.shadow());
            final String rid = id;
            host.addHit(r - 20, y, 10, 9, () -> {
                values.remove(rid);
                ExtrasActions.sweeperListRemove(kind, rid);
            });
            g.drawString(host.font(), "x", r - 17, y + 1, host.T.textError, GuiDraw.shadow());
            y += 9;
        }
        GuiDraw.scrollbar(g, r - 5, y - rows * 9, rows * 9, scroll, max);
        return y + 4;
    }

    private static String displayName(boolean entities, String id) {
        try {
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key == null) return id;
            if (entities) {
                var type = BuiltInRegistries.ENTITY_TYPE.get(key);
                return type == null ? id : Component.translatable(type.getDescriptionId()).getString();
            }
            ItemStack stack = ExtrasClientState.itemStack(id);
            return stack.isEmpty() ? id : stack.getHoverName().getString();
        } catch (Throwable ignored) {
            return id;
        }
    }
}
