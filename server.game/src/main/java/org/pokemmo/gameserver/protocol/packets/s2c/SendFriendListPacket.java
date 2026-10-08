package org.pokemmo.gameserver.protocol.packets.s2c;

import org.pokemmo.gameserver.game.pool.GameSessionPool;
import org.pokemmo.gameserver.services.friend.FriendService;
import org.server.OutgoingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

import java.util.List;

/** S2C 0x63: replaces the client's local friend list. */
public final class SendFriendListPacket extends OutgoingPacket {
    private static final int MAX_ENTRIES = 0xFF;

    private final List<FriendService.FriendEntry> friends;

    public SendFriendListPacket() {
        this(List.of());
    }

    public SendFriendListPacket(List<FriendService.FriendEntry> friends) {
        this.friends = List.copyOf(friends == null ? List.of() : friends);
    }

    @Override
    public void encode(ByteBufEx buffer) {
        if (friends.size() > MAX_ENTRIES) {
            throw new IllegalStateException("好友列表条目数超过单包上限: " + friends.size());
        }
        buffer.writeByte(0);
        buffer.writeByte(friends.size());
        for (FriendService.FriendEntry friend : friends) {
            buffer.writeLongLE(friend.friendId());
            buffer.writeIntLE(friend.addTimeEpochSeconds());

            Session friendSession = GameSessionPool.getPlayerSessionInPool(friend.friendId());
            buffer.writeByte(friendSession != null && friendSession.isActive() ? 1 : 0);

            // The client reads a name plus seven legacy metadata fields. Only
            // the name is currently confirmed by the protocol evidence.
            buffer.writeUtf16LE(friend.friendName() == null ? "" : friend.friendName());
            buffer.writeByte(0);
            buffer.writeIntLE(0);
            buffer.writeByte(0);
            buffer.writeShortLE(0);
            buffer.writeShortLE(0);
            buffer.writeShortLE(0);
            buffer.writeShortLE(0);
        }
    }
}
