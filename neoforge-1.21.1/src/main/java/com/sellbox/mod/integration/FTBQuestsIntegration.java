package com.sellbox.mod.integration;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.shop.network.SyncQuestListPacket;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * FTB Quests 集成（纯反射，无编译期依赖）。
 *
 * <p>兼容 2001.x（MC 1.20.1）与 2101.x（MC 1.21.1），只使用两个大版本都稳定存在的成员：
 * {@code FTBQuestsAPI.api()}、{@code API#getQuestFile(boolean)}、{@code BaseQuestFile#getAllChapters()}、
 * {@code Chapter#getQuests()/getGroup()}、{@code QuestObjectBase#getId()/getRawTitle()/getTitle()}、
 * {@code TeamData#get(Player)}、{@code TeamData#isCompleted(QuestObject)}。
 *
 * <p>注意两点历史坑：
 * <ul>
 *   <li>2101.x 的 {@code getRawTitle()} 内部已经查过 {@code TranslationManager}，2001.x 则根本没有该类。
 *       旧实现反射 {@code TranslationManager} 的私有字段和 {@code makeKey}，任一环节对不上就把整个
 *       {@code loaded} 置为 false，结果前置任务列表直接为空。</li>
 *   <li>分组不能跳过 id == 0：0 正是 FTB Quests 的 {@code DefaultChapterGroup}，绝大多数整合包
 *       的章节就放在这个默认分组里。</li>
 * </ul>
 */
public class FTBQuestsIntegration {
    private static boolean loaded = false;
    private static boolean warnedOnce = false;

    private static Method apiMethod;
    private static Method getQuestFileMethod;

    private static Method getAllChaptersMethod;   // 可选
    private static Method getChapterGroupsMethod; // 可选（getAllChapters 缺失时兜底）
    private static Method getGroupChaptersMethod; // 可选
    private static Method getQuestByIdMethod;     // 可选

    private static Method chapterGetQuestsMethod;
    private static Method chapterGetGroupMethod;  // 可选

    private static Method getIdMethod;
    private static java.lang.reflect.Field idField;      // 可选：早期 2001.1.x 只有公有字段 id
    private static Method getIDStaticMethod;             // 可选：QuestObjectBase.getID(obj)
    private static Method getRawTitleMethod;
    private static Method getTitleMethod;
    private static Method componentGetStringMethod;

    private static Method teamDataGetMethod;      // 可选
    private static Method isCompletedMethod;      // 可选

    static {
        try {
            Class<?> apiClass = Class.forName("dev.ftb.mods.ftbquests.api.FTBQuestsAPI");
            apiMethod = apiClass.getMethod("api");

            Class<?> apiType = apiMethod.getReturnType();
            try {
                getQuestFileMethod = apiType.getMethod("getQuestFile", boolean.class);
            } catch (NoSuchMethodException e) {
                getQuestFileMethod = apiClass.getMethod("api").invoke(null).getClass()
                        .getMethod("getQuestFile", boolean.class);
            }

            Class<?> questFileClass = getQuestFileMethod.getReturnType();
            getAllChaptersMethod = findMethod(questFileClass, "getAllChapters");
            getChapterGroupsMethod = findMethod(questFileClass, "getChapterGroups");
            getQuestByIdMethod = findMethod(questFileClass, "getQuest", long.class);

            Class<?> chapterClass = Class.forName("dev.ftb.mods.ftbquests.quest.Chapter");
            chapterGetQuestsMethod = chapterClass.getMethod("getQuests");
            chapterGetGroupMethod = findMethod(chapterClass, "getGroup");

            Class<?> groupClass = Class.forName("dev.ftb.mods.ftbquests.quest.ChapterGroup");
            getGroupChaptersMethod = findMethod(groupClass, "getChapters");

            Class<?> questObjectBaseClass = Class.forName("dev.ftb.mods.ftbquests.quest.QuestObjectBase");
            getIdMethod = findMethod(questObjectBaseClass, "getId");
            if (getIdMethod == null) {
                try {
                    idField = questObjectBaseClass.getField("id");
                } catch (Throwable ignored) {
                }
                getIDStaticMethod = findMethod(questObjectBaseClass, "getID", questObjectBaseClass);
            }
            getRawTitleMethod = questObjectBaseClass.getMethod("getRawTitle");
            getTitleMethod = questObjectBaseClass.getMethod("getTitle");
            componentGetStringMethod = net.minecraft.network.chat.Component.class.getMethod("getString");

            // 完成状态：拿不到就按「未做限制」处理，绝不能因为反射失败把商品藏起来
            try {
                Class<?> teamDataClass = Class.forName("dev.ftb.mods.ftbquests.quest.TeamData");
                teamDataGetMethod = findMethod(teamDataClass, "get",
                        net.minecraft.world.entity.player.Player.class);
                Class<?> questObjectClass = Class.forName("dev.ftb.mods.ftbquests.quest.QuestObject");
                isCompletedMethod = findMethod(teamDataClass, "isCompleted", questObjectClass);
                if (isCompletedMethod == null) {
                    isCompletedMethod = findMethod(teamDataClass, "isCompleted", questObjectBaseClass);
                }
            } catch (Throwable ignored) {
                teamDataGetMethod = null;
                isCompletedMethod = null;
            }

            if (getAllChaptersMethod == null && getChapterGroupsMethod == null) {
                throw new NoSuchMethodException("no chapter listing method on " + questFileClass.getName());
            }

            loaded = true;
            SellBoxMod.LOGGER.info(
                    "FTB Quests integration loaded (chapters={}, groups={}, completion={})",
                    getAllChaptersMethod != null ? "getAllChapters" : "-",
                    getChapterGroupsMethod != null ? "getChapterGroups" : "-",
                    isCompletedMethod != null ? "ok" : "unavailable");
        } catch (Throwable t) {
            loaded = false;
            SellBoxMod.LOGGER.info("FTB Quests not found, quest prerequisite integration disabled: {}", t.toString());
        }
    }

    @Nullable
    private static Method findMethod(Class<?> owner, String name, Class<?>... params) {
        try {
            Method m = owner.getMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean isLoaded() {
        return loaded;
    }

    @Nullable
    private static Object questFile() throws Exception {
        Object api = apiMethod.invoke(null);
        return api == null ? null : getQuestFileMethod.invoke(api, false);
    }

    /** 所有章节（含默认分组），按整合包里的顺序 */
    private static List<Object> collectChapters(Object file) throws Exception {
        List<Object> chapters = new ArrayList<>();
        if (getAllChaptersMethod != null) {
            Object res = getAllChaptersMethod.invoke(file);
            if (res instanceof Collection<?> c) {
                chapters.addAll(c);
                return chapters;
            }
        }
        if (getChapterGroupsMethod != null && getGroupChaptersMethod != null) {
            Object groups = getChapterGroupsMethod.invoke(file);
            if (groups instanceof Collection<?> gs) {
                for (Object group : gs) {
                    if (group == null) continue;
                    Object chs = getGroupChaptersMethod.invoke(group);
                    if (chs instanceof Collection<?> c) chapters.addAll(c);
                }
            }
        }
        return chapters;
    }

    /** 标题：2001.x 的 getRawTitle() 是字面量，2101.x 已经内部解析过翻译表 */
    @Nullable
    private static String title(Object obj) {
        try {
            Object raw = getRawTitleMethod.invoke(obj);
            if (raw instanceof String s && !isPlaceholder(s)) return s;
        } catch (Throwable ignored) {
        }
        try {
            Object comp = getTitleMethod.invoke(obj);
            if (comp != null) {
                Object str = componentGetStringMethod.invoke(comp);
                if (str instanceof String s && !isPlaceholder(s)) return s;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean isPlaceholder(String s) {
        if (s.isEmpty()) return true;
        String lower = s.toLowerCase(Locale.ROOT);
        return lower.equals("unnamed") || lower.equals("ftbquests.unnamed")
                || lower.equals("unnamed group") || s.equals("未命名");
    }

    private static long idOf(Object obj) {
        try {
            if (getIdMethod != null) {
                Object id = getIdMethod.invoke(obj);
                if (id instanceof Number n) return n.longValue();
            }
            if (idField != null) {
                Object id = idField.get(obj);
                if (id instanceof Number n) return n.longValue();
            }
            if (getIDStaticMethod != null) {
                Object id = getIDStaticMethod.invoke(null, obj);
                if (id instanceof Number n) return n.longValue();
            }
        } catch (Throwable ignored) {
        }
        return 0L;
    }

    public static boolean isQuestCompleted(ServerPlayer player, long questId) {
        if (!loaded) return true;
        if (getQuestByIdMethod == null || teamDataGetMethod == null || isCompletedMethod == null) {
            return true;
        }
        try {
            Object quest = getQuestByIdMethod.invoke(questFile(), questId);
            if (quest == null) return true;
            Object teamData = teamDataGetMethod.invoke(null, player);
            if (teamData == null) return true;
            return (boolean) isCompletedMethod.invoke(teamData, quest);
        } catch (Throwable t) {
            if (!warnedOnce) {
                warnedOnce = true;
                SellBoxMod.LOGGER.warn("FTB Quests completion check failed, prerequisites are not enforced", t);
            }
            return true;
        }
    }

    @Nullable
    public static String getQuestNameById(long questId) {
        if (!loaded || getQuestByIdMethod == null) return null;
        try {
            Object quest = getQuestByIdMethod.invoke(questFile(), questId);
            if (quest == null) return null;
            String name = title(quest);
            return name != null ? name : Long.toHexString(questId).toUpperCase(Locale.ROOT);
        } catch (Throwable t) {
            return null;
        }
    }

    public static List<SyncQuestListPacket.QuestGroupInfo> getAllQuests() {
        List<SyncQuestListPacket.QuestGroupInfo> result = new ArrayList<>();
        if (!loaded) return result;

        try {
            Object file = questFile();
            if (file == null) return result;

            Map<Object, String> groupNames = new LinkedHashMap<>();
            Map<Object, List<SyncQuestListPacket.QuestInfo>> groupQuests = new LinkedHashMap<>();

            for (Object chapter : collectChapters(file)) {
                if (chapter == null) continue;
                long chapterId = idOf(chapter);
                String chapterName = title(chapter);
                if (chapterName == null) {
                    chapterName = "章 #" + Long.toHexString(chapterId).toUpperCase(Locale.ROOT);
                }

                Object group = null;
                if (chapterGetGroupMethod != null) {
                    try {
                        group = chapterGetGroupMethod.invoke(chapter);
                    } catch (Throwable ignored) {
                    }
                }
                Object groupKey = group != null ? group : "chapter:" + chapterId;
                if (!groupNames.containsKey(groupKey)) {
                    String groupName = group != null ? title(group) : null;
                    if (groupName == null) groupName = chapterName;
                    groupNames.put(groupKey, groupName);
                }

                List<SyncQuestListPacket.QuestInfo> quests =
                        groupQuests.computeIfAbsent(groupKey, k -> new ArrayList<>());
                Object questList = chapterGetQuestsMethod.invoke(chapter);
                if (questList instanceof Collection<?> c) {
                    for (Object quest : c) {
                        if (quest == null) continue;
                        long questId = idOf(quest);
                        String questName = title(quest);
                        if (questName == null) {
                            questName = Long.toHexString(questId).toUpperCase(Locale.ROOT);
                        }
                        quests.add(new SyncQuestListPacket.QuestInfo(questId, questName, chapterName));
                    }
                }
            }

            int total = 0;
            for (Map.Entry<Object, List<SyncQuestListPacket.QuestInfo>> e : groupQuests.entrySet()) {
                if (e.getValue().isEmpty()) continue;
                total += e.getValue().size();
                result.add(new SyncQuestListPacket.QuestGroupInfo(groupNames.get(e.getKey()), e.getValue()));
            }
            SellBoxMod.LOGGER.info("FTB Quests: collected {} group(s), {} quest(s)", result.size(), total);
        } catch (Throwable t) {
            SellBoxMod.LOGGER.error("Failed to collect FTB Quests list", t);
        }
        return result;
    }
}
