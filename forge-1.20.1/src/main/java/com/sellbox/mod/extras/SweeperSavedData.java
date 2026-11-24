package com.sellbox.mod.extras;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/** 扫地被清理物品的垃圾箱存档 */
public final class SweeperSavedData extends SavedData {
    public static final String NAME = "sellbox_sweeper";
    public static final int SIZE = 54;
    public final List<SimpleContainer> dustbins = new ArrayList<>();
    /** 每个垃圾箱每个槽位的放入时间（毫秒），用于满箱时顶掉最久远的物品 */
    private final List<long[]> slotTimes = new ArrayList<>();

    public SweeperSavedData() {
    }

    public SweeperSavedData(CompoundTag tag) {
        if (!tag.contains("Dustbins", Tag.TAG_LIST)) return;
        ListTag list = tag.getList("Dustbins", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            SimpleContainer c = new SimpleContainer(SIZE);
            c.fromTag(list.getCompound(i).getList("Items", Tag.TAG_COMPOUND));
            dustbins.add(c);
        }
        if (tag.contains("Times", Tag.TAG_LIST)) {
            ListTag times = tag.getList("Times", Tag.TAG_LONG_ARRAY);
            for (int i = 0; i < times.size() && i < dustbins.size(); i++) {
                long[] arr = times.getLongArray(i);
                long[] safe = new long[SIZE];
                System.arraycopy(arr, 0, safe, 0, Math.min(arr.length, SIZE));
                slotTimes.add(safe);
            }
        }
        while (slotTimes.size() < dustbins.size()) slotTimes.add(new long[SIZE]);
        compact();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (SimpleContainer c : dustbins) {
            CompoundTag bin = new CompoundTag();
            bin.put("Items", c.createTag());
            list.add(bin);
        }
        tag.put("Dustbins", list);
        ListTag times = new ListTag();
        for (long[] arr : slotTimes) times.add(new net.minecraft.nbt.LongArrayTag(arr));
        tag.put("Times", times);
        return tag;
    }

    public static SweeperSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                SweeperSavedData::new, SweeperSavedData::new, NAME);
    }

    private static boolean sameItem(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameTags(a, b);
    }

    /** 合并旧的“一个鸡蛋一格”数据 */
    public void compact() {
        ensure(slotTimes.size());
        for (int bi = 0; bi < dustbins.size(); bi++) {
            SimpleContainer bin = dustbins.get(bi);
            for (int i = 0; i < bin.getContainerSize(); i++) {
                ItemStack target = bin.getItem(i);
                if (target.isEmpty()) continue;
                for (int j = i + 1; j < bin.getContainerSize(); j++) {
                    ItemStack src = bin.getItem(j);
                    if (src.isEmpty() || !sameItem(target, src)) continue;
                    int max = Math.max(1, target.getMaxStackSize());
                    int move = Math.min(src.getCount(), max - target.getCount());
                    if (move <= 0) continue;
                    target.grow(move);
                    src.shrink(move);
                    long ti = slotTimes.get(bi)[i] <= 0 ? Long.MAX_VALUE : slotTimes.get(bi)[i];
                    long tj = slotTimes.get(bi)[j] <= 0 ? Long.MAX_VALUE : slotTimes.get(bi)[j];
                    long oldest = Math.min(ti, tj);
                    slotTimes.get(bi)[i] = oldest == Long.MAX_VALUE ? 0 : oldest;
                    if (src.isEmpty()) slotTimes.get(bi)[j] = 0;
                }
            }
        }
        setDirty();
    }

    public void ensure(int count) {
        while (dustbins.size() < count) {
            dustbins.add(new SimpleContainer(SIZE));
            slotTimes.add(new long[SIZE]);
        }
        while (slotTimes.size() < dustbins.size()) slotTimes.add(new long[SIZE]);
        while (dustbins.size() > count) {
            dustbins.remove(dustbins.size() - 1);
            slotTimes.remove(slotTimes.size() - 1);
        }
    }

    public SimpleContainer bin(int index) {
        ensure(ExtrasConfigFile.getSweeperDustbins());
        if (index < 0 || index >= dustbins.size()) return null;
        return dustbins.get(index);
    }

    public boolean add(ItemStack stack) {
        ensure(ExtrasConfigFile.getSweeperDustbins());
        long now = System.currentTimeMillis();
        ItemStack incoming = stack.copy();
        // 1) 先并入同类堆叠，避免每个鸡蛋各占一格
        for (int bi = 0; bi < dustbins.size(); bi++) {
            SimpleContainer bin = dustbins.get(bi);
            for (int si = 0; si < bin.getContainerSize(); si++) {
                ItemStack existing = bin.getItem(si);
                if (existing.isEmpty() || !ItemStack.isSameItemSameTags(existing, incoming)) continue;
                int max = Math.max(1, existing.getMaxStackSize());
                if (existing.getCount() >= max) continue;
                int move = Math.min(incoming.getCount(), max - existing.getCount());
                if (move <= 0) continue;
                existing.grow(move);
                incoming.shrink(move);
                if (incoming.isEmpty()) {
                    setDirty();
                    return true;
                }
            }
        }
        // 2) 再放空位，并记录放入时间
        for (int bi = 0; bi < dustbins.size(); bi++) {
            SimpleContainer bin = dustbins.get(bi);
            for (int si = 0; si < bin.getContainerSize(); si++) {
                if (bin.getItem(si).isEmpty()) {
                    bin.setItem(si, incoming);
                    slotTimes.get(bi)[si] = now;
                    setDirty();
                    return true;
                }
            }
        }
        // 2) 全满：找全箱中放入时间最早的槽位，用新物品顶掉
        int oldestBin = -1, oldestSlot = -1;
        long oldest = Long.MAX_VALUE;
        for (int bi = 0; bi < dustbins.size(); bi++) {
            SimpleContainer bin = dustbins.get(bi);
            long[] times = slotTimes.get(bi);
            for (int si = 0; si < bin.getContainerSize(); si++) {
                if (bin.getItem(si).isEmpty()) continue;
                long t = times[si];
                if (t <= 0) t = Long.MIN_VALUE; // 旧存档没有时间，视为最久
                if (t < oldest) {
                    oldest = t;
                    oldestBin = bi;
                    oldestSlot = si;
                }
            }
        }
        if (oldestBin < 0) return false;
        dustbins.get(oldestBin).setItem(oldestSlot, incoming);
        slotTimes.get(oldestBin)[oldestSlot] = now;
        setDirty();
        return true;
    }
}
