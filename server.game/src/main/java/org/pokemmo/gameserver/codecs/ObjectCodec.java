package org.pokemmo.gameserver.codecs;

import org.server.bytes.ByteBufEx;

public interface ObjectCodec<T> {
    T decode(ByteBufEx buffer);
    void decode(ByteBufEx buffer, T object);
    void encode(ByteBufEx buffer, T object);
}
