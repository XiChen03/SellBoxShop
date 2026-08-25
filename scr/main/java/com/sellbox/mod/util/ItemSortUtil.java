package com.sellbox.mod.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.function.Function;

public class ItemSortUtil {

    public enum SortMode {
        NAME("名称"),
        ID("ID"),
        MOD("模组"),
        FIRST_LETTER("首字母");

        private final String displayName;

        SortMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public SortMode next() {
            SortMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public static <T> Comparator<T> comparator(SortMode mode, Function<T, ResourceLocation> idFn, Function<T, String> nameFn) {
        return switch (mode) {
            case NAME -> (a, b) -> nameFn.apply(a).compareToIgnoreCase(nameFn.apply(b));
            case ID -> (a, b) -> idFn.apply(a).toString().compareToIgnoreCase(idFn.apply(b).toString());
            case MOD -> (a, b) -> {
                int cmp = idFn.apply(a).getNamespace().compareToIgnoreCase(idFn.apply(b).getNamespace());
                if (cmp != 0) return cmp;
                return nameFn.apply(a).compareToIgnoreCase(nameFn.apply(b));
            };
            case FIRST_LETTER -> (a, b) -> {
                String na = nameFn.apply(a);
                String nb = nameFn.apply(b);
                char fa = PinyinUtil.getFirstLetter(na.isEmpty() ? ' ' : na.charAt(0));
                char fb = PinyinUtil.getFirstLetter(nb.isEmpty() ? ' ' : nb.charAt(0));
                int cmp = Character.compare(fa, fb);
                if (cmp != 0) return cmp;
                return na.compareToIgnoreCase(nb);
            };
        };
    }

    public static Comparator<Item> itemComparator(SortMode mode) {
        return comparator(mode,
                BuiltInRegistries.ITEM::getKey,
                item -> item.getName(new ItemStack(item)).getString());
    }
}