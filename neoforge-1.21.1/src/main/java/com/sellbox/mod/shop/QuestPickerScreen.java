package com.sellbox.mod.shop;

import com.sellbox.mod.gui.GuiDraw;
import com.sellbox.mod.gui.GuiTheme;
import com.sellbox.mod.shop.network.RequestQuestListPacket;
import com.sellbox.mod.shop.network.SetEntryQuestPacket;
import com.sellbox.mod.shop.network.SyncQuestListPacket;
import com.sellbox.mod.util.FontUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.stream.Collectors;

public class QuestPickerScreen extends Screen {
    /** 主题对象身份恒定，切换主题就地刷新 */
    private final GuiTheme T = GuiTheme.get();
    private final Screen parentScreen;
    private final String category;
    private final int entryIndex;
    private final Long currentQuestId;
    private final String currentQuestName;

    private List<GroupData> groups = new ArrayList<>();
    private List<GroupData> filteredGroups = new ArrayList<>();
    private int scrollOffset = 0;

    private EditBox searchBox;
    private boolean searchFocused = false;
    private int panelX, panelY, panelW, panelH;
    private int searchX, searchY, searchW, searchH;
    private int listX, listY, listW, listH;
    private int lineH = 16;
    private int btnY, btnW, btnH;
    private int curQuestY;

    private static class GroupData {
        final String groupName;
        final List<ChapterData> chapters = new ArrayList<>();
        boolean expanded = true;
        GroupData(String n) { groupName = n; }
    }

    private static class ChapterData {
        final String chapterName;
        final List<QuestEntry> quests = new ArrayList<>();
        boolean expanded = false;
        ChapterData(String n) { chapterName = n; }
    }

    private static record QuestEntry(long questId, String questName, String searchLower) {}

    public static String getQuestName(long questId) {
        return null;
    }

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

        searchBox = new EditBox(font, GuiDraw.innerTextX(searchX), GuiDraw.innerTextY(searchY, searchH), GuiDraw.innerWidth(searchW), searchH - 4, Component.empty());
        searchBox.setMaxLength(50);
        searchBox.setTextColor(T.textPrimary);
        searchBox.setBordered(false);
        searchBox.setResponder(s -> onSearch());
        addRenderableWidget(searchBox);

        PacketDistributor.sendToServer(new RequestQuestListPacket());
    }

    public void onQuestData(List<SyncQuestListPacket.QuestGroupInfo> data) {
        Map<String, GroupData> groupMap = new LinkedHashMap<>();
        for (SyncQuestListPacket.QuestGroupInfo g : data) {
            GroupData gd = groupMap.computeIfAbsent(g.groupName, GroupData::new);
            Map<String, ChapterData> chMap = new LinkedHashMap<>();
            for (SyncQuestListPacket.QuestInfo q : g.quests) {
                ChapterData cd = chMap.computeIfAbsent(q.chapterName, ChapterData::new);
                cd.quests.add(new QuestEntry(q.questId, q.questName, q.questName.toLowerCase(Locale.ROOT)));
            }
            gd.chapters.addAll(chMap.values());
        }
        groups = new ArrayList<>(groupMap.values());
        applyFilter();
    }

    private void onSearch() {
        String query = searchBox != null ? searchBox.getValue().toLowerCase(Locale.ROOT) : "";
        if (!query.isEmpty()) {
            for (GroupData gd : groups) {
                boolean groupHasMatch = false;
                for (ChapterData cd : gd.chapters) {
                    cd.expanded = cd.quests.stream().anyMatch(q -> q.searchLower().contains(query));
                    if (cd.expanded) groupHasMatch = true;
                }
                gd.expanded = groupHasMatch;
            }
        }
        applyFilter();
    }

    private void applyFilter() {
        String query = searchBox != null ? searchBox.getValue().toLowerCase(Locale.ROOT) : "";
        filteredGroups = new ArrayList<>();
        for (GroupData gd : groups) {
            if (query.isEmpty()) {
                filteredGroups.add(gd);
            } else {
                GroupData copy = new GroupData(gd.groupName);
                for (ChapterData cd : gd.chapters) {
                    List<QuestEntry> matched = cd.quests.stream()
                            .filter(q -> q.searchLower().contains(query))
                            .collect(Collectors.toList());
                    if (!matched.isEmpty()) {
                        ChapterData cdCopy = new ChapterData(cd.chapterName);
                        cdCopy.expanded = true;
                        cdCopy.quests.addAll(matched);
                        copy.chapters.add(cdCopy);
                    }
                }
                if (!copy.chapters.isEmpty()) {
                    copy.expanded = true;
                    filteredGroups.add(copy);
                }
            }
        }
        scrollOffset = 0;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH) {
            int maxScroll = Math.max(0, getTotalLines() - listH / lineH);
            scrollOffset = Math.max(0, Math.min(scrollOffset - (int) Math.signum(scrollY) * 3, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int getTotalLines() {
        int total = 0;
        for (GroupData gd : filteredGroups) {
            total++; // group header
            if (gd.expanded) {
                for (ChapterData cd : gd.chapters) {
                    total++; // chapter header
                    if (cd.expanded) total += cd.quests.size();
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
                PacketDistributor.sendToServer(new SetEntryQuestPacket(category, entryIndex, null));
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
                for (GroupData gd : filteredGroups) {
                    int gHeaderStart = y;
                    y += lineH;
                    if (relY >= gHeaderStart && relY < y) {
                        gd.expanded = !gd.expanded;
                        return true;
                    }
                    if (gd.expanded) {
                        for (ChapterData cd : gd.chapters) {
                            int cHeaderStart = y;
                            y += lineH;
                            if (relY >= cHeaderStart && relY < y) {
                                cd.expanded = !cd.expanded;
                                return true;
                            }
                            if (cd.expanded) {
                                for (QuestEntry qe : cd.quests) {
                                    int questStart = y;
                                    y += lineH;
                                    if (relY >= questStart && relY < y) {
                                        PacketDistributor.sendToServer(new SetEntryQuestPacket(category, entryIndex, qe.questId()));
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
        fillRoundedRect(g, panelX, panelY, panelW, panelH, T.popupBg);
        g.fill(panelX + 4, panelY + 18, panelX + panelW - 4, panelY + 19, T.separatorLine);

        String titleStr = " ═══ " + title.getString() + " ═══ ";
        FontUtil.drawAutoFitCentered(g, font, titleStr, panelX + panelW / 2, panelY + 4, panelW - 20, T.textTitle);

        String curQuestStr;
        int curColor;
        if (currentQuestId != null) {
            String name = currentQuestName;
            if (name != null && !name.isEmpty()) {
                curQuestStr = "当前前置: " + name;
                curColor = T.textPrice;
            } else {
                curQuestStr = "当前前置: 任务 #" + Long.toHexString(currentQuestId).toUpperCase();
                curColor = T.textPrice;
            }
        } else {
            curQuestStr = "当前前置: 无";
            curColor = T.textMuted;
        }
        g.drawString(font, curQuestStr, panelX + 10, curQuestY, curColor, GuiDraw.shadow());

        GuiDraw.inputBox(g, searchX, searchY, searchW, searchH, searchFocused);

        if (searchBox.getValue().isEmpty() && !searchFocused) {
            g.drawString(font, "§7搜索任务名称...", GuiDraw.innerTextX(searchX), GuiDraw.innerTextY(searchY, searchH), T.textHint, GuiDraw.shadow());
        }

        super.render(g, mouseX, mouseY, partialTick);

        g.fill(listX, listY, listX + listW, listY + listH, T.itemListBg);
        g.fill(listX + listW - 1, listY, listX + listW, listY + listH, T.popupBg);

        int visibleRows = listH / lineH;
        int startLine = scrollOffset;
        int endLine = startLine + visibleRows;
        int currentLine = 0;

        for (GroupData gd : filteredGroups) {
            int gHeaderY = listY + (currentLine - startLine) * lineH;
            boolean gHovered = mouseX >= listX && mouseX < listX + listW && mouseY >= gHeaderY && mouseY < gHeaderY + lineH;

            if (currentLine >= startLine && currentLine < endLine) {
                g.fill(listX, gHeaderY, listX + listW, gHeaderY + lineH, gHovered ? T.questGroupHover : T.slotInnerFill);
                g.fill(listX, gHeaderY + lineH - 1, listX + listW, gHeaderY + lineH, T.popupBg);
                String indicator = gd.expanded ? "§7▼" : "§7▶";
                g.drawString(font, " §e—" + gd.groupName + "—" + indicator, listX + 3, gHeaderY + 3, T.textTitle, GuiDraw.shadow());
            }
            currentLine++;

            if (gd.expanded) {
                for (ChapterData cd : gd.chapters) {
                    if (currentLine >= startLine && currentLine < endLine) {
                        int cHeaderY = listY + (currentLine - startLine) * lineH;
                        boolean cHovered = mouseX >= listX && mouseX < listX + listW && mouseY >= cHeaderY && mouseY < cHeaderY + lineH;
                        g.fill(listX, cHeaderY, listX + listW, cHeaderY + lineH, cHovered ? T.questChapterHover : T.questChapterBg);
                        String indicator = cd.expanded ? "§7▼" : "§7▶";
                        g.drawString(font, "  " + indicator + " " + cd.chapterName, listX + 12, cHeaderY + 3, T.catText, GuiDraw.shadow());
                    }
                    currentLine++;

                    if (cd.expanded) {
                        for (QuestEntry qe : cd.quests) {
                            if (currentLine >= startLine && currentLine < endLine) {
                                int qy = listY + (currentLine - startLine) * lineH;
                                boolean qHovered = mouseX >= listX && mouseX < listX + listW && mouseY >= qy && mouseY < qy + lineH;
                                if (qHovered) {
                                    g.fill(listX, qy, listX + listW, qy + lineH, T.questItemHover);
                                    g.drawString(font, "   " + qe.questName(), listX + 18, qy + 3, T.textPrimary, GuiDraw.shadow());
                                } else {
                                    g.drawString(font, "   " + qe.questName(), listX + 18, qy + 3, T.textSecondary, GuiDraw.shadow());
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
            g.fill(listX + listW - 3, sbY, listX + listW, sbY + sbH, T.textMuted);
        }

        if (groups.isEmpty() && searchBox.getValue().isEmpty()) {
            g.drawString(font, "§7正在获取任务列表...", listX + 5, listY + 5, T.textMuted, GuiDraw.shadow());
        }

        int clearX = panelX + panelW / 2 - btnW - 4;
        int backX = panelX + panelW / 2 + 4;
        drawButton(g, clearX, btnY, btnW, btnH, "清除",
                mouseX >= clearX && mouseX < clearX + btnW && mouseY >= btnY && mouseY < btnY + btnH);
        drawButton(g, backX, btnY, btnW, btnH, "返回",
                mouseX >= backX && mouseX < backX + btnW && mouseY >= btnY && mouseY < btnY + btnH);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, String text, boolean hovered) {
        int bg = hovered ? T.btnBgGreenHovered : T.btnBgNormal;
        g.fill(x, y, x + w, y + h, bg);
        FontUtil.drawAutoFitCentered(g, font, text, x + w / 2, y + (h - 8) / 2 + 1, w - 6, T.textPrice);
    }

    private void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + h, color);
        g.fill(x, y + 2, x + w, y + h - 2, color);
    }
}
