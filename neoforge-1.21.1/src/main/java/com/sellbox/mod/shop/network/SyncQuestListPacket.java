package com.sellbox.mod.shop.network;

import com.sellbox.mod.SellBoxMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record SyncQuestListPacket(List<QuestGroupInfo> groups) implements CustomPacketPayload {
    public static final Type<SyncQuestListPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "sync_quest_list"));

    public static final StreamCodec<FriendlyByteBuf, SyncQuestListPacket> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.groups.size());
                for (QuestGroupInfo g : payload.groups) {
                    buf.writeUtf(g.groupName);
                    buf.writeVarInt(g.quests.size());
                    for (QuestInfo q : g.quests) {
                        buf.writeLong(q.questId);
                        buf.writeUtf(q.questName);
                        buf.writeUtf(q.chapterName);
                    }
                }
            },
            buf -> {
                int gCount = buf.readVarInt();
                List<QuestGroupInfo> groups = new ArrayList<>();
                for (int i = 0; i < gCount; i++) {
                    String gName = buf.readUtf();
                    int qCount = buf.readVarInt();
                    List<QuestInfo> quests = new ArrayList<>();
                    for (int j = 0; j < qCount; j++) {
                        long id = buf.readLong();
                        String name = buf.readUtf();
                        String ch = buf.readUtf();
                        quests.add(new QuestInfo(id, name, ch));
                    }
                    groups.add(new QuestGroupInfo(gName, quests));
                }
                return new SyncQuestListPacket(groups);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static class QuestInfo {
        public final long questId;
        public final String questName;
        public final String chapterName;

        public QuestInfo(long questId, String questName, String chapterName) {
            this.questId = questId;
            this.questName = questName;
            this.chapterName = chapterName;
        }
    }

    public static class QuestGroupInfo {
        public final String groupName;
        public final List<QuestInfo> quests;

        public QuestGroupInfo(String groupName, List<QuestInfo> quests) {
            this.groupName = groupName;
            this.quests = quests;
        }
    }
}
