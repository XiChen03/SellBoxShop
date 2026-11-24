package com.sellbox.mod.extras.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 右下角「成就式」提醒队列（客户端）。
 * <p>
 * 服务端通过 {@code ExtrasNoticePayload} 下发翻译键 + 参数：
 * <pre>
 * { "titleKey":"extras.notice.sold.title", "titleArgs":["铁锭", 64],
 *   "textKey":"extras.notice.sold.text",  "textArgs":[128],
 *   "icon":"money", "kind":"sale" }
 * </pre>
 * 中文/英文由客户端本地化，服务端不用关心语言。
 * <p>
 * 语义色（金币/物品/成功失败）不跟主题走，其余用主题的 {@code popupBg} / {@code popupBorder}。
 */
public final class ExtrasNoticeQueue {
    private ExtrasNoticeQueue() {}

    /** 一条提醒最多停留多久（毫秒） */
    private static final long LIFETIME_MS = 4200L;
    /** 同屏最多堆叠几条 */
    private static final int MAX_VISIBLE = 4;

    /** 一条待显示的提醒 */
    public static final class Notice {
        public String title = "";
        public String text = "";
        public String icon = "info";
        public String kind = "info";
        public long bornAt = System.currentTimeMillis();
        /** 延迟到该时刻后才显示 */
        public long showAt = 0L;
    }

    private static final Deque<Notice> QUEUE = new ArrayDeque<>();

    /** 由网络回调调用（主线程） */
    public static void push(String json) {
        if (json == null || json.isEmpty()) return;
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            Notice n = new Notice();
            n.title = resolve("titleKey", "title", o);
            n.text = resolve("textKey", "text", o);
            n.icon = o.has("icon") ? o.get("icon").getAsString() : "info";
            n.kind = o.has("kind") ? o.get("kind").getAsString() : "info";
            long delay = o.has("delayMs") ? Math.max(0L, o.get("delayMs").getAsLong()) : 0L;
            n.bornAt = System.currentTimeMillis() + delay;
            n.showAt = System.currentTimeMillis() + delay;
            enqueue(n);
        } catch (Throwable ignored) {
        }
    }

    /** 本地提示（操作成功/失败反馈），文本已经本地化好 */
    public static void pushPlain(String text, String kind) {
        if (text == null || text.isEmpty()) return;
        Notice n = new Notice();
        n.title = "";
        n.text = text;
        n.kind = kind == null || kind.isEmpty() ? "info" : kind;
        n.bornAt = System.currentTimeMillis();
        enqueue(n);
    }

    private static void enqueue(Notice n) {
        synchronized (QUEUE) {
            QUEUE.addFirst(n);
            while (QUEUE.size() > 32) QUEUE.removeLast();
        }
    }

    /** 取 {@code keyField} 翻译键 + {@code argsField} 参数，翻译成玩家语言；没有键就退回 {@code plainField} 原文 */
    private static String resolve(String keyField, String plainField, JsonObject o) {
        if (o.has(keyField)) {
            String key = o.get(keyField).getAsString();
            if (!key.isEmpty()) {
                Object[] args = readArgs(o.has(keyField.replace("Key", "Args"))
                        ? o.getAsJsonArray(keyField.replace("Key", "Args")) : null);
                try {
                    return Component.translatable(key, args).getString();
                } catch (Throwable ignored) {
                }
            }
        }
        return o.has(plainField) ? o.get(plainField).getAsString() : "";
    }

    private static Object[] readArgs(JsonArray arr) {
        if (arr == null) return new Object[0];
        Object[] out = new Object[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            JsonElement e = arr.get(i);
            out[i] = e.getAsJsonPrimitive().isNumber() ? (Object) e.getAsLong() : e.getAsString();
        }
        return out;
    }

    /** 当前可见的快照（最新的在最前，最多 MAX_VISIBLE 条） */
    public static List<Notice> visible() {
        List<Notice> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        synchronized (QUEUE) {
            QUEUE.removeIf(n -> now >= n.showAt && now - n.bornAt > LIFETIME_MS);
            int i = 0;
            for (Notice n : QUEUE) {
                if (now < n.showAt) continue;
                if (i++ >= MAX_VISIBLE) break;
                out.add(n);
            }
        }
        return out;
    }

    public static boolean isEmpty() {
        synchronized (QUEUE) {
            return QUEUE.isEmpty();
        }
    }

    public static void clear() {
        synchronized (QUEUE) {
            QUEUE.clear();
        }
    }
}
