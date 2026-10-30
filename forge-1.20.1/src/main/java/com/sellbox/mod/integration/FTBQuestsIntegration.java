package com.sellbox.mod.integration;

import com.sellbox.mod.SellBoxMod;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * FTB Quests 集成（纯反射，无编译期依赖）。
 *
 * <p>兼容 2001.x（MC 1.20.1）与 2101.x（MC 1.21.1），只使用两个大版本都稳定存在的成员：
 * {@code FTBQuestsAPI.api()}、{@code API#getQuestFile(boolean)}、{@code BaseQuestFile#getAllChapters()}、
 * {@code Chapter#getQuests()/getGroup()}、{@code QuestObjectBase#getRawTitle()/getTitle()}、
 * {@code TeamData#get(Player)}、{@code TeamData#isCompleted(QuestObject)}。
 *
 * <p>对象 id 的取法在版本间有差异：2001.4.x 起有 {@code getId()}，更早的 2001.1.x 只有公有字段
 * {@code id}，所以依次尝试 方法 → 字段 → 静态 {@code QuestObjectBase.getID(obj)}。
 */
public class FTBQuestsIntegration {
    private static boolean classFound = false;
    private static boolean warnedOnce = false;

    private static Method apiMethod;
    private static Method getQuestFileMethod;

    private static Method getAllChaptersMethod;   // 可选
    private static Method getChapterGroupsMethod; // 可选（getAllChapters 缺失时兜底）
    private static Method getGroupChaptersMethod; // 可选
    private static Method getQuestByIdMethod;     // 可选

    private static Method chapterGetQuestsMethod;
    private static Method chapterGetGroupMethod;  // 可选

    private static Method getIdMethod;            // 可选
    private static Field idField;                 // 可选
    private static Method getIDStaticMethod;      // 可选
    private static Method getRawTitleMethod;
    private static Method getTitleMethod;
    private static Method componentGetStringMethod;

    private static Method teamDataGetMethod;      // 可选
    private static Method isCompletedMethod;      // 可选

    private static final Map<Long, String> serverQuestNameCache = new HashMap<>();

    public static class QuestInfo {
        public final long questId;
        public final String questName;
        public final String chapterName;
        public final String chapterGroupName;

        public QuestInfo(long questId, String questName, String chapterName, String chapterGroupName) {
            this.questId = questId;
            this.questName = questName;
            this.chapterName = chapterName;
            this.chapterGroupName = chapterGroupName;
        }
    }

    static {
        try {
            Class<?> apiClass = Class.forName("dev.ftb.mods.ftbquests.api.FTBQuestsAPI");
            apiMethod = apiClass.getMethod("api");
            Object api = apiMethod.invoke(null);

            Class<?> apiType = apiMethod.getReturnType();
            try {
                getQuestFileMethod = apiType.getMethod("getQuestFile", boolean.class);
            } catch (NoSuchMethodException e) {
                getQuestFileMethod = api.getClass().getMethod("getQuestFile", boolean.class);
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
            getRawTitleMethod = findMethod(questObjectBaseClass, "getRawTitle");
            getTitleMethod = findMethod(questObjectBaseClass, "getTitle");
            componentGetStringMethod = net.minecraft.network.chat.Component.class.getMethod("getString");

            // 完成状态：拿不到就按「未做限制」处理，不能因为反射失败把商品藏起来
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

            classFound = true;
            SellBoxMod.LOGGER.info(
                    "FTB Quests integration loaded (chapters={}, id={}, completion={})",
                    getAllChaptersMethod != null ? "getAllChapters" : "getChapterGroups",
                    getIdMethod != null ? "getId" : (idField != null ? "field" : (getIDStaticMethod != null ? "getID" : "?")),
                    isCompletedMethod != null ? "ok" : "unavailable");
        } catch (Throwable t) {
            classFound = false;
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
        return classFound;
    }

    @Nullable
    private static Object questFile() throws Exception {
        Object api = apiMethod.invoke(null);
        return api == null ? null : getQuestFileMethod.invoke(api, false);
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

    @Nullable
    private static String title(Object obj) {
        try {
            if (getRawTitleMethod != null) {
                Object raw = getRawTitleMethod.invoke(obj);
                if (raw instanceof String s && !isPlaceholder(s)) return s;
            }
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

    /**
     * 全部任务（扁平列表，带章节名与分组名）。
     * FTB Quests 未安装/反射初始化失败时返回 null，调用方据此提示"未安装"。
     */
    @Nullable
    public static List<QuestInfo> getAllQuests() {
        if (!classFound) return null;
        try {
            Object file = questFile();
            if (file == null) return null;

            serverQuestNameCache.clear();
            List<QuestInfo> result = new ArrayList<>();

            for (Object chapter : collectChapters(file)) {
                if (chapter == null) continue;
                long chapterId = idOf(chapter);
                String chapterName = title(chapter);
                if (chapterName == null) {
                    chapterName = "章 #" + Long.toHexString(chapterId).toUpperCase(Locale.ROOT);
                }

                String groupName = "";
                if (chapterGetGroupMethod != null) {
                    try {
                        Object group = chapterGetGroupMethod.invoke(chapter);
                        String gn = group != null ? title(group) : null;
                        if (gn != null) groupName = gn;
                    } catch (Throwable ignored) {
                    }
                }

                Object questList = chapterGetQuestsMethod.invoke(chapter);
                if (!(questList instanceof Collection<?> c)) continue;
                for (Object quest : c) {
                    if (quest == null) continue;
                    long id = idOf(quest);
                    String questName = title(quest);
                    if (questName == null) questName = Long.toHexString(id).toUpperCase(Locale.ROOT);
                    serverQuestNameCache.put(id, questName);
                    result.add(new QuestInfo(id, questName, chapterName, groupName));
                }
            }

            SellBoxMod.LOGGER.info("FTB Quests: collected {} quest(s)", result.size());
            return result;
        } catch (Throwable t) {
            SellBoxMod.LOGGER.error("Failed to get FTB Quests list", t);
            return null;
        }
    }

    public static boolean isQuestCompleted(ServerPlayer player, long questId) {
        if (!classFound) return true;
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
        if (!classFound) return null;
        String cached = serverQuestNameCache.get(questId);
        if (cached != null) return cached;
        if (getQuestByIdMethod == null) return null;
        try {
            Object quest = getQuestByIdMethod.invoke(questFile(), questId);
            if (quest == null) return null;
            String name = title(quest);
            if (name == null) name = Long.toHexString(questId).toUpperCase(Locale.ROOT);
            serverQuestNameCache.put(questId, name);
            return name;
        } catch (Throwable t) {
            return null;
        }
    }
}
