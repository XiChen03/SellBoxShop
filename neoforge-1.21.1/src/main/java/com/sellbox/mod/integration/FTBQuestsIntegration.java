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
    private static Method getAltTitleMethod;   // 仅章节兜底用
    // ── 1.21.1 标题存在 TranslationManager 里，需要按语言逐个探测 ──
    private static Method getQuestFileMethod2;         // QuestObjectBase#getQuestFile()
    private static Method getTranslationManagerMethod; // BaseQuestFile#getTranslationManager()
    private static Method getStringTranslationMethod;  // TranslationManager#getStringTranslation
    private static java.lang.reflect.Field translationMapField;  // TranslationManager#map
    private static Object titleTranslationKey;         // TranslationKey.TITLE
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
            getRawTitleMethod = findMethod(questObjectBaseClass, "getRawTitle");
            getTitleMethod = findMethod(questObjectBaseClass, "getTitle");
            getAltTitleMethod = findMethod(questObjectBaseClass, "getAltTitle");
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

            // ── 标题翻译表：1.21.1 的标题在 ftbquests/lang/*.snbt 里 ──
            try {
                getQuestFileMethod2 = findMethod(questObjectBaseClass, "getQuestFile");
                Class<?> baseQuestFileClass = Class.forName("dev.ftb.mods.ftbquests.quest.BaseQuestFile");
                getTranslationManagerMethod = findMethod(baseQuestFileClass, "getTranslationManager");
                Class<?> tmClass = Class.forName("dev.ftb.mods.ftbquests.quest.translation.TranslationManager");
                translationMapField = tmClass.getDeclaredField("map");
                translationMapField.setAccessible(true);
                Class<?> keyClass = Class.forName("dev.ftb.mods.ftbquests.quest.translation.TranslationKey");
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object tk = Enum.valueOf((Class<? extends Enum>) keyClass.asSubclass(Enum.class), "TITLE");
                titleTranslationKey = tk;
                getStringTranslationMethod = tmClass.getMethod("getStringTranslation",
                        questObjectBaseClass, String.class, keyClass);
                SellBoxMod.LOGGER.info("FTB Quests: translation-table title probe enabled");
            } catch (Throwable t) {
                getQuestFileMethod2 = null;
                getTranslationManagerMethod = null;
                translationMapField = null;
                titleTranslationKey = null;
                getStringTranslationMethod = null;
                SellBoxMod.LOGGER.debug("FTB Quests: translation-table probe unavailable ({})", t.toString());
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

    /**
     * 取显示名。
     * <p>1.21.1 起 FTB Quests 把标题挪进了 {@code TranslationManager}（{@code ftbquests/lang/*.snbt}），
     * 而 {@code getRawTitle()} 只查「文件 locale」和「fallback locale（通常 en_us）」两张表。
     * 中文整合包的标题往往只在 {@code zh_cn.snbt} 里，于是两边都查不到、返回空串，
     * 再被 {@code getTitle()} 兜成图标物品名（就是界面上那些 "Oak Log"）。
     * <p>所以这里既扫翻译表，也优先采用对象自身的标题。
     * <p>取名字的优先级（任务、章节、分组一致）：
     * <ol>
     *   <li>{@code getRawTitle()} —— 对象自身的原始标题</li>
     *   <li>{@code getTitle()} —— 对象自身的标题组件；客户端的可翻译标题会在这里被解析出来</li>
     *   <li>翻译表全语言扫描 —— 解决服务端 locale 与整合包语言不一致</li>
     *   <li>（仅章节）{@code getAltTitle()} —— 文件名 / 章节序号这类有意义的兜底</li>
     * </ol>
     * 任务的 {@code getAltTitle()} 是图标物品名（会显示成 "Oak Log" 这种），所以任务不使用它，
     * 由调用方退回「任务 N」这种中性标签。
     */
    @Nullable
    private static String title(Object obj) {
        String raw = invokeString(getRawTitleMethod, obj);
        if (valid(raw)) return raw;

        // 任务自身的标题组件。注意服务端的 getTitle() 在原始标题为空时会直接返回 getAltTitle()，
        // 而任务的 altTitle 是<b>图标物品名</b>（所以会出现 "Oak Log" 这种假名字）。
        // 判定方法：getTitle() 与 getAltTitle() 相同 => 是 altTitle 兜底，不是真标题，丢弃。
        String self = invokeComponent(getTitleMethod, obj);
        String alt = invokeComponent(getAltTitleMethod, obj);
        boolean altIsFake = valid(alt) && alt.equals(self);
        if (valid(self) && !altIsFake) return self;

        // 再从翻译表里找真标题（任务/章节/分组都适用）
        String fromLang = titleFromTranslationTables(obj);
        if (valid(fromLang)) return fromLang;

        // 章节的 altTitle 是有意义的兜底（文件名 / 章节序号），任务的 altTitle 是图标物品名，不能用
        if (obj.getClass().getName().endsWith(".Chapter") && valid(alt)) return alt;

        if (!titleWarned) {
            titleWarned = true;
            SellBoxMod.LOGGER.warn(
                    "FTB Quests: no readable title for {} (raw='{}', title='{}', lang='{}'); "
                            + "names fall back to generic labels",
                    obj.getClass().getSimpleName(), raw, self, fromLang);
        }
        return null;
    }

    private static boolean titleWarned = false;

    /**
     * 直接扫 TranslationManager 里所有语言的表，取第一个非空标题。
     * 这样就不受「服务端 locale 与整合包语言不一致」的影响。
     */
    @Nullable
    private static String titleFromTranslationTables(Object obj) {
        if (getQuestFileMethod2 == null || getTranslationManagerMethod == null
                || getStringTranslationMethod == null || titleTranslationKey == null) {
            return null;
        }
        try {
            Object file = getQuestFileMethod2.invoke(obj);
            if (file == null) return null;
            Object tm = getTranslationManagerMethod.invoke(file);
            if (tm == null) return null;

            java.util.List<String> locales = new java.util.ArrayList<>();
            // 优先当前 locale、中文、en_us，其余按表里出现的顺序
            try {
                Object cur = file.getClass().getMethod("getLocale").invoke(file);
                if (cur instanceof String s && !s.isEmpty()) locales.add(s);
            } catch (Throwable ignored) {
            }
            locales.add("zh_cn");
            locales.add("en_us");
            if (translationMapField != null) {
                try {
                    if (translationMapField.get(tm) instanceof java.util.Map<?, ?> map) {
                        for (Object k : map.keySet()) {
                            if (k instanceof String s && !locales.contains(s)) locales.add(s);
                        }
                    }
                } catch (Throwable ignored) {
                }
            }

            // 注意：getStringTranslation 是 TranslationManager 的实例方法，
            // receiver 必须是 tm，而不是任务对象；旧代码误把 obj 当 receiver，异常被吞掉后
            // 所有标题都退回任务 ID，这就是 1.21.1 显示 id 的根本原因。
            for (String locale : locales) {
                try {
                    Object res = getStringTranslationMethod.invoke(tm, obj, locale, titleTranslationKey);
                    if (res instanceof java.util.Optional<?> opt && opt.isPresent()
                            && opt.get() instanceof String s && valid(s)) {
                        return s;
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Nullable
    private static String invokeString(@Nullable Method m, Object target) {
        if (m == null) return null;
        try {
            Object v = m.invoke(target);
            return v instanceof String s ? s.trim() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static String invokeComponent(@Nullable Method m, Object target) {
        if (m == null) return null;
        try {
            Object comp = m.invoke(target);
            if (comp == null) return null;
            Object s = componentGetStringMethod.invoke(comp);
            return s instanceof String str ? str.trim() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean valid(@Nullable String s) {
        return s != null && !s.isEmpty() && !isPlaceholder(s) && !looksLikeTranslationKey(s);
    }

    /** 未解析的翻译键：无空格且含点，例如 {@code ftbquests.chapter} */
    private static boolean looksLikeTranslationKey(String s) {
        if (s.indexOf(' ') >= 0 || s.indexOf('\u00a7') >= 0) return false;
        if (s.indexOf('.') < 0) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-';
            if (!ok) return false;
        }
        return true;
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

            int chapterIndex = 0;
            for (Object chapter : collectChapters(file)) {
                if (chapter == null) continue;
                long chapterId = idOf(chapter);
                String chapterName = title(chapter);
                if (chapterName == null) {
                    // 读不到标题时用序号，比一串十六进制 ID 可读得多
                    chapterName = "第 " + (chapterIndex + 1) + " 章";
                }
                chapterIndex++;

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
