package com.sellbox.mod.playershop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.sellbox.mod.SellBoxMod;
import net.minecraft.resources.ResourceLocation;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerShopBlacklist {
    private static final Set<ResourceLocation> blacklistedItems = ConcurrentHashMap.newKeySet();
    private static Path blacklistFile;
    private static volatile boolean dirty = false;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void init(Path configDir) {
        blacklistFile = configDir.resolve("SellBox").resolve("sellbox_blacklist.txt");
        load();
    }

    public static boolean isBlacklisted(ResourceLocation id) {
        return id != null && blacklistedItems.contains(id);
    }

    public static boolean add(ResourceLocation id) {
        if (id == null) return false;
        boolean added = blacklistedItems.add(id);
        if (added) dirty = true;
        return added;
    }

    public static boolean remove(ResourceLocation id) {
        if (id == null) return false;
        boolean removed = blacklistedItems.remove(id);
        if (removed) dirty = true;
        return removed;
    }

    public static Set<ResourceLocation> getAll() {
        return Collections.unmodifiableSet(blacklistedItems);
    }

    public static Set<String> getAsStringSet() {
        Set<String> result = new LinkedHashSet<>();
        for (ResourceLocation id : blacklistedItems) {
            result.add(id.toString());
        }
        return result;
    }

    public static void flushSave() {
        if (dirty) {
            dirty = false;
            save();
        }
    }

    public static void reload() {
        flushSave();
        load();
    }

    private static void load() {
        blacklistedItems.clear();
        if (blacklistFile == null || !Files.exists(blacklistFile)) return;
        try (Reader reader = Files.newBufferedReader(blacklistFile)) {
            Type listType = new TypeToken<List<String>>() {}.getType();
            List<String> list = GSON.fromJson(reader, listType);
            if (list != null) {
                for (String s : list) {
                    ResourceLocation id = ResourceLocation.tryParse(s);
                    if (id != null) {
                        blacklistedItems.add(id);
                    }
                }
            }
        } catch (Exception e) {
            SellBoxMod.LOGGER.error("Failed to load blacklist", e);
        }
    }

    private static void save() {
        if (blacklistFile == null) return;
        try {
            Files.createDirectories(blacklistFile.getParent());
            List<String> list = new ArrayList<>();
            for (ResourceLocation id : blacklistedItems) {
                list.add(id.toString());
            }
            try (Writer writer = Files.newBufferedWriter(blacklistFile)) {
                GSON.toJson(list, writer);
            }
        } catch (IOException e) {
            SellBoxMod.LOGGER.error("Failed to save blacklist", e);
        }
    }
}
