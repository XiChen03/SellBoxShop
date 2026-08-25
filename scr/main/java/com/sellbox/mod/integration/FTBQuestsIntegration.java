package com.sellbox.mod.integration;

import com.sellbox.mod.SellBoxMod;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.*;

public class FTBQuestsIntegration {
    private static boolean classFound = false;
    private static Object apiInstance;
    private static Method getQuestFileMethod;
    private static Method getAllChaptersMethod;
    private static Method getChapterNameMethod;
    private static Method getChapterQuestsMethod;
    private static Method getChapterGroupMethod;
    private static Method getGroupNameMethod;
    private static boolean hasGroupSupport = false;
    private static Method getQuestNameMethod;
    private static Method getOrCreateTeamDataMethod;
    private static Method isCompletedMethod;
    private static Method questIdMethod;
    private static boolean useAlternativeCheck = false;
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
            Method apiMethod = apiClass.getMethod("api");
            apiInstance = apiMethod.invoke(null);
            getQuestFileMethod = apiInstance.getClass().getMethod("getQuestFile", boolean.class);

            Class<?> questFileClass = getQuestFileMethod.getReturnType();
            Class<?> questClass = Class.forName("dev.ftb.mods.ftbquests.quest.Quest");
            Class<?> questObjectBaseClass = Class.forName("dev.ftb.mods.ftbquests.quest.QuestObjectBase");
            questIdMethod = questObjectBaseClass.getMethod("getId");

            Class<?> chapterClass = Class.forName("dev.ftb.mods.ftbquests.quest.Chapter");
            getAllChaptersMethod = questFileClass.getMethod("getAllChapters");
            getChapterNameMethod = chapterClass.getMethod("getTitle");
            getChapterQuestsMethod = chapterClass.getMethod("getQuests");

            try {
                getChapterGroupMethod = chapterClass.getMethod("getGroup");
                Class<?> groupClass = getChapterGroupMethod.getReturnType();
                getGroupNameMethod = groupClass.getMethod("getTitle");
                hasGroupSupport = true;
            } catch (NoSuchMethodException e) {
                hasGroupSupport = false;
            }

            getQuestNameMethod = questClass.getMethod("getTitle");

            Class<?> questFileInterface = Class.forName("dev.ftb.mods.ftbquests.api.QuestFile");
            Class<?> teamDataClass = Class.forName("dev.ftb.mods.ftbquests.quest.TeamData");
            getOrCreateTeamDataMethod = questFileInterface.getMethod("getOrCreateTeamData", net.minecraft.world.entity.Entity.class);

            // Try different methods for checking quest completion
            isCompletedMethod = tryGetIsCompleted(teamDataClass, questObjectBaseClass);
            if (isCompletedMethod == null) {
                isCompletedMethod = tryGetIsCompleted(teamDataClass, questClass);
            }
            if (isCompletedMethod == null) {
                // Try Quest.allTasksCompleted(TeamData) instead
                try {
                    isCompletedMethod = questClass.getMethod("allTasksCompleted", teamDataClass);
                    SellBoxMod.LOGGER.info("FTB Quests: using Quest.allTasksCompleted");
                } catch (NoSuchMethodException e3) {
                    useAlternativeCheck = true;
                    SellBoxMod.LOGGER.warn("FTB Quests: no completion check method found, quest filtering disabled");
                }
            }

            classFound = true;
            SellBoxMod.LOGGER.info("FTB Quests integration class found");
        } catch (Exception e) {
            SellBoxMod.LOGGER.info("FTB Quests not found, quest prerequisite integration disabled: {}", e.getMessage());
        }
    }

    @Nullable
    private static Method tryGetIsCompleted(Class<?> teamDataClass, Class<?> paramType) {
        try {
            return teamDataClass.getMethod("isCompleted", paramType);
        } catch (NoSuchMethodException e) {
            try {
                return teamDataClass.getMethod("isComplete", paramType);
            } catch (NoSuchMethodException e2) {
                return null;
            }
        }
    }

    public static boolean isLoaded() {
        return classFound;
    }

    @Nullable
    public static List<QuestInfo> getAllQuests() {
        if (!classFound) return null;
        try {
            Object file = getQuestFile();
            if (file == null) return null;

            serverQuestNameCache.clear();
            List<QuestInfo> result = new ArrayList<>();
            List<?> chapters = (List<?>) getAllChaptersMethod.invoke(file);
            for (Object chapter : chapters) {
                Object titleObj = getChapterNameMethod.invoke(chapter);
                String chapterName = (String) titleObj.getClass().getMethod("getString").invoke(titleObj);
                String groupName = "";
                if (hasGroupSupport) {
                    try {
                        Object group = getChapterGroupMethod.invoke(chapter);
                        Object groupTitle = getGroupNameMethod.invoke(group);
                        groupName = (String) groupTitle.getClass().getMethod("getString").invoke(groupTitle);
                    } catch (Exception ignored) {}
                }
                Collection<?> quests = (Collection<?>) getChapterQuestsMethod.invoke(chapter);
                for (Object quest : quests) {
                    long id = (long) questIdMethod.invoke(quest);
                    Object questTitleObj = getQuestNameMethod.invoke(quest);
                    String questName = (String) questTitleObj.getClass().getMethod("getString").invoke(questTitleObj);
                    serverQuestNameCache.put(id, questName);
                    result.add(new QuestInfo(id, questName, chapterName, groupName));
                }
            }
            return result;
        } catch (Exception e) {
            SellBoxMod.LOGGER.error("Failed to get FTB Quests list", e);
            return null;
        }
    }

    public static boolean isQuestCompleted(ServerPlayer player, long questId) {
        if (!classFound) return false;
        if (useAlternativeCheck) return false;
        try {
            Object file = getQuestFile();
            if (file == null) return false;

            Object quest = null;
            try {
                Method getQuestMethod = file.getClass().getMethod("getQuest", long.class);
                quest = getQuestMethod.invoke(file, questId);
            } catch (NoSuchMethodException e) {
                try {
                    Method getMethod = file.getClass().getMethod("get", long.class);
                    quest = getMethod.invoke(file, questId);
                } catch (NoSuchMethodException e2) {
                    return false;
                }
            }
            if (quest == null) return false;

            Object teamData = getOrCreateTeamDataMethod.invoke(file, player);
            try {
                // Try both invocation patterns: TeamData.isCompleted(quest) and Quest.allTasksCompleted(teamData)
                try {
                    return (boolean) isCompletedMethod.invoke(teamData, quest);
                } catch (IllegalArgumentException e) {
                    return (boolean) isCompletedMethod.invoke(quest, teamData);
                }
            } catch (Exception e) {
                SellBoxMod.LOGGER.error("Failed to invoke quest completion check", e);
                return false;
            }
        } catch (Exception e) {
            SellBoxMod.LOGGER.error("Failed to check quest completion", e);
            return false;
        }
    }

    @Nullable
    private static Object getQuestFile() {
        try {
            return getQuestFileMethod.invoke(apiInstance, false);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public static String getQuestNameById(long questId) {
        if (!classFound) return null;
        // Check cache first (populated by getAllQuests)
        String cached = serverQuestNameCache.get(questId);
        if (cached != null) return cached;
        // Fallback: fetch individually
        try {
            Object file = getQuestFile();
            if (file == null) return null;
            Object quest = null;
            try {
                quest = file.getClass().getMethod("getQuest", long.class).invoke(file, questId);
            } catch (NoSuchMethodException e) {
                try {
                    quest = file.getClass().getMethod("get", long.class).invoke(file, questId);
                } catch (NoSuchMethodException e2) {
                    return null;
                }
            }
            if (quest == null) return null;
            Object titleObj = getQuestNameMethod.invoke(quest);
            String name = (String) titleObj.getClass().getMethod("getString").invoke(titleObj);
            if (name != null) serverQuestNameCache.put(questId, name);
            return name;
        } catch (Exception e) {
            SellBoxMod.LOGGER.error("getQuestNameById({}) failed", questId, e);
            return null;
        }
    }
}
