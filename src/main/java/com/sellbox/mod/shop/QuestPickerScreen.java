package com.sellbox.mod.shop;

import com.sellbox.mod.network.ModNetwork;
import com.sellbox.mod.util.FontUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.stream.Collectors;

public class QuestPickerScreen extends Screen {
    private final Screen parentScreen;
    private final String category;
    private final int entryIndex;
    private final Long currentQuestId;
    private final String currentQuestName;

    private List<GroupNode> groups = new ArrayList<>();
    private List<GroupNode> filteredGroups = new ArrayList<>();
    private int scrollOffset = 0;

    private EditBox searchBox;
    private boolean searchFocused = false;
    private int panelX, panelY, panelW, panelH;
    private int searchX, searchY, searchW, searchH;
    private int listX, listY, listW, listH;
    private int lineH = 16;
    private int btnY, btnW, btnH;
    private int curQuestY;

    private static class GroupNode {
        final String groupName;
        final List<ChapterNode> chapters = new ArrayList<>();
        boolean expanded = true;
        GroupNode(String groupName) { this.groupName = groupName; }
    }

    private static class ChapterNode {
        final String chapterName;
        final List<QuestEntry> quests = new ArrayList<>();
        boolean expanded = true;
        ChapterNode(String chapterName) { this.chapterName = chapterName; }
    }

    private static record QuestEntry(long questId, String questName, String searchLower) {}

    public QuestPickerScreen(Screen parentScreen, String category, int entryIndex, Long currentQuestId, String currentQuestName) {
        super(Component.literal("选择前置任务"));
        this.parentScreen = parentScreen;
        this.category = category;
        this.entryIndex = entryIndex;
        this.currentQuestId = currentQuestId;
        this.currentQuestName = currentQuestName;
    }

    @Override
    protected void init() {
        if (minecraft == null) minecraft = Minecraft.getInstance();
        if (font == null) font = Minecraft.getInstance().font;
        super.init();

        panelW = Math.min(420, width - 40);
        panelH = Math.min(340, height - 40);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        int pad = 8;
        int titleH = 16;
        int curQuestH = 12;
        searchH = 14;
        btnH = 16;

        int titleY = panelY + pad;
        curQuestY = titleY + titleH + 2;
        int searchAreaY = curQuestY + curQuestH + 4;
        searchX = panelX + pad + 2;
        searchY = searchAreaY;
        searchW = panelW - pad * 2 - 4;

        listX = panelX + pad;
        listY = searchY + searchH + pad;
        listW = panelW - pad * 2;
        listH = panelH - (listY - panelY) - btnH - pad * 2;
        if (listH < 20) listH = 20;

        btnY = listY + listH + pad;
        btnW = 80;

        searchBox = new EditBox(font, searchX + 2, searchY + 2, searchW - 4, searchH - 4, Component.empty());
        searchBox.setMaxLength(50);
        searchBox.setTextColor(0xFFFFFF);
        searchBox.setBordered(false);
        searchBox.setResponder(s -> onSearch());
        addRenderableWidget(searchBox);

        ModNetwork.CHANNEL.sendToServer(new ModNetwork.RequestQuestListPacket(category, entryIndex));
    }

    public void onQuestData(List<ModNetwork.SyncQuestListPacket.QuestEntry> quests) {
        Map<String, GroupNode> groupMap = new LinkedHashMap<>();
        for (var qe : quests) {
            String gName = qe.chapterGroupName();
            if (gName == null || gName.isEmpty()) gName = qe.chapterName();
            GroupNode gn = groupMap.computeIfAbsent(gName, GroupNode::new);
            ChapterNode cn = null;
            for (ChapterNode c : gn.chapters) {
                if (c.chapterName.equals(qe.chapterName())) {
                    cn = c;
                    break;
                }
            }
            if (cn == null) {
                cn = new ChapterNode(qe.chapterName());
                gn.chapters.add(cn);
            }
            cn.quests.add(new QuestEntry(qe.questId(), qe.questName(), qe.questName().toLowerCase(Locale.ROOT)));
        }
        groups = new ArrayList<>(groupMap.values());
        applyFilter();
    }

    private void onSearch() {
        String query = searchBox != null ? searchBox.getValue().toLowerCase(Locale.ROOT) : "";
        if (!query.isEmpty()) {
            for (GroupNode gn : groups) {
                boolean anyMatch = false;
                for (ChapterNode cn : gn.chapters) {
                    boolean chMatch = cn.quests.stream().anyMatch(q -> q.searchLower().contains(query));
                    cn.expanded = chMatch;
                    anyMatch |= chMatch;
                }
                gn.expanded = anyMatch;
            }
        }
        applyFilter();
    }

    private void applyFilter() {
        String query = searchBox != null ? searchBox.getValue().toLowerCase(Locale.ROOT) : "";
        filteredGroups = new ArrayList<>();
        for (GroupNode gn : groups) {
            if (query.isEmpty()) {
                filteredGroups.add(gn);
            } else {
                GroupNode gnCopy = new GroupNode(gn.groupName);
                gnCopy.expanded = true;
                for (ChapterNode cn : gn.chapters) {
                    List<QuestEntry> matched = cn.quests.stream()
                            .filter(q -> q.searchLower().contains(query))
                            .collect(Collectors.toList());
                    if (!matched.isEmpty()) {
                        ChapterNode cnCopy = new ChapterNode(cn.chapterName);
                        cnCopy.expanded = true;
                        cnCopy.quests.addAll(matched);
                        gnCopy.chapters.add(cnCopy);
                    }
                }
                if (!gnCopy.chapters.isEmpty()) {
                    filteredGroups.add(gnCopy);
                }
            }
        }
        scrollOffset = 0;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0 && mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH) {
            int maxScroll = Math.max(0, getTotalLines() - listH / lineH);
            scrollOffset = Math.max(0, Math.min(scrollOffset - (int) Math.signum(delta) * 3, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private int getTotalLines() {
        int total = 0;
        for (GroupNode gn : filteredGroups) {
            total++;
            if (gn.expanded) {
                for (ChapterNode cn : gn.chapters) {
                    total++;
                    if (cn.expanded) total += cn.quests.size();
                }
            }
        }
        return total;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            boolean inSearch = mouseX >= searchX && mouseX < searchX + searchW && mouseY >= searchY && mouseY < searchY + searchH;
            searchBox.setFocused(inSearch);
            searchFocused = inSearch;

            int clearX = panelX + panelW / 2 - btnW - 4;
            if (mouseX >= clearX && mouseX < clearX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.SetEntryQuestPacket(category, entryIndex, null));
                goBack();
                return true;
            }
            int backX = panelX + panelW / 2 + 4;
            if (mouseX >= backX && mouseX < backX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
                goBack();
                return true;
            }
            if (mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH) {
                int relY = (int) (mouseY - listY) + scrollOffset * lineH;
                int y = 0;
                for (GroupNode gn : filteredGroups) {
                    int groupStart = y;
                    y += lineH;
                    if (relY >= groupStart && relY < y) {
                        gn.expanded = !gn.expanded;
                        return true;
                    }
                    if (gn.expanded) {
                        for (ChapterNode cn : gn.chapters) {
                            int chStart = y;
                            y += lineH;
                            if (relY >= chStart && relY < y) {
                                cn.expanded = !cn.expanded;
                                return true;
                            }
                            if (cn.expanded) {
                                for (QuestEntry qe : cn.quests) {
                                    int questStart = y;
                                    y += lineH;
                                    if (relY >= questStart && relY < y) {
                                        ModNetwork.CHANNEL.sendToServer(new ModNetwork.SetEntryQuestPacket(category, entryIndex, qe.questId()));
                                        goBack();
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox.isFocused() && keyCode == 256) {
            searchBox.setFocused(false);
            searchFocused = false;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void goBack() {
        if (minecraft != null) {
            minecraft.setScreen(parentScreen != null ? parentScreen : null);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        fillRoundedRect(g, panelX, panelY, panelW, panelH, 0xFF333333);

        g.fill(panelX + 4, panelY + 18, panelX + panelW - 4, panelY + 19, 0xFF555555);

        String titleStr = " ═══ " + title.getString() + " ═══ ";
        FontUtil.drawAutoFitCentered(g, font, titleStr, panelX + panelW / 2, panelY + 4, panelW - 20, 0xFFFFAA);

        String curQuestStr;
        int curColor;
        if (currentQuestId != null) {
            String name = currentQuestName;
            if (name != null && !name.isEmpty()) {
                curQuestStr = "当前前置: " + name;
                curColor = 0x88FF88;
            } else {
                curQuestStr = "当前前置: 任务 #" + Long.toHexString(currentQuestId).toUpperCase();
                curColor = 0x88FF88;
            }
        } else {
            curQuestStr = "当前前置: 无";
            curColor = 0x888888;
        }
        g.drawString(font, curQuestStr, panelX + 10, curQuestY, curColor);

        int borderCol = searchFocused ? 0x55FFFFFF : 0x55AAAAAA;
        g.fill(searchX + 2, searchY, searchX + searchW - 2, searchY + 1, borderCol);
        g.fill(searchX + 2, searchY + searchH - 1, searchX + searchW - 2, searchY + searchH, borderCol);
        g.fill(searchX, searchY + 2, searchX + 1, searchY + searchH - 2, borderCol);
        g.fill(searchX + searchW - 1, searchY + 2, searchX + searchW, searchY + searchH - 2, borderCol);

        if (searchBox.getValue().isEmpty() && !searchFocused) {
            g.drawString(font, "§7搜索任务名称...", searchX + 4, searchY + 3, 0x777777);
        }

        super.render(g, mouseX, mouseY, partialTick);

        g.fill(listX, listY, listX + listW, listY + listH, 0xFF222222);
        g.fill(listX + listW - 1, listY, listX + listW, listY + listH, 0xFF333333);

        int visibleRows = listH / lineH;
        int startLine = scrollOffset;
        int endLine = startLine + visibleRows;
        int currentLine = 0;

        for (GroupNode gn : filteredGroups) {
            int rowY = listY + (currentLine - startLine) * lineH;
            boolean rowHovered = mouseX >= listX && mouseX < listX + listW && mouseY >= rowY && mouseY < rowY + lineH;

            if (currentLine >= startLine && currentLine < endLine) {
                g.fill(listX, rowY, listX + listW, rowY + lineH, rowHovered ? 0xFF444466 : 0xFF2D2D2D);
                g.fill(listX, rowY + lineH - 1, listX + listW, rowY + lineH, 0xFF333333);
                String arrow = gn.expanded ? "▼" : "▶";
                String text = " " + gn.groupName + "  " + arrow;
                g.drawString(font, text, listX + 3, rowY + 3, 0xFFFFAA);
            }
            currentLine++;

            if (gn.expanded) {
                for (ChapterNode cn : gn.chapters) {
                    if (currentLine >= startLine && currentLine < endLine) {
                        int cy = listY + (currentLine - startLine) * lineH;
                        boolean cHovered = mouseX >= listX && mouseX < listX + listW && mouseY >= cy && mouseY < cy + lineH;
                        g.fill(listX, cy, listX + listW, cy + lineH, cHovered ? 0xFF444466 : 0xFF2D2D2D);
                        String arrow = cn.expanded ? "▼" : "▶";
                        String text = "   " + cn.chapterName + "  " + arrow;
                        g.drawString(font, text, listX + 3, cy + 3, 0xFFCC66);
                    }
                    currentLine++;

                    if (cn.expanded) {
                        for (QuestEntry qe : cn.quests) {
                            if (currentLine >= startLine && currentLine < endLine) {
                                int qy = listY + (currentLine - startLine) * lineH;
                                boolean qHovered = mouseX >= listX && mouseX < listX + listW && mouseY >= qy && mouseY < qy + lineH;
                                if (qHovered) {
                                    g.fill(listX, qy, listX + listW, qy + lineH, 0xFF444488);
                                    g.drawString(font, "     " + qe.questName(), listX + 3, qy + 3, 0xFFFFFF);
                                } else {
                                    g.drawString(font, "     " + qe.questName(), listX + 3, qy + 3, 0xCCCCCC);
                                }
                            }
                            currentLine++;
                        }
                    }
                }
            }
        }

        int total = getTotalLines();
        if (total > visibleRows) {
            int sbH = Math.max(8, listH * visibleRows / total);
            int sbY = listY + listH * scrollOffset / total;
            g.fill(listX + listW - 3, sbY, listX + listW, sbY + sbH, 0xFF888888);
        }

        if (groups.isEmpty() && searchBox.getValue().isEmpty()) {
            g.drawString(font, "§7正在获取任务列表...", listX + 5, listY + 5, 0x888888);
        }

        int clearX = panelX + panelW / 2 - btnW - 4;
        int backX = panelX + panelW / 2 + 4;
        drawButton(g, clearX, btnY, btnW, btnH, "清除",
                mouseX >= clearX && mouseX < clearX + btnW && mouseY >= btnY && mouseY < btnY + btnH);
        drawButton(g, backX, btnY, btnW, btnH, "返回",
                mouseX >= backX && mouseX < backX + btnW && mouseY >= btnY && mouseY < btnY + btnH);
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, String text, boolean hovered) {
        int bg = hovered ? 0xAA44AA44 : 0x88333333;
        g.fill(x, y, x + w, y + h, bg);
        FontUtil.drawAutoFitCentered(g, font, text, x + w / 2, y + (h - 8) / 2 + 1, w - 6, 0x88FF88);
    }

    private void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + h, color);
        g.fill(x, y + 2, x + w, y + h - 2, color);
    }
}
