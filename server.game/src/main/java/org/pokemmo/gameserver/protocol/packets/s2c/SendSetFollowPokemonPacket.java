package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

public class SendSetFollowPokemonPacket extends OutgoingPacket {
    private final long entityGameId;
    private final short pokemonIndexId;
    private final byte followerPokemonRarity;
    private final boolean ignoreFollowError;

    public SendSetFollowPokemonPacket(long entityGameId, int pokemonIndexId, int followerPokemonRarity,
                                      boolean ignoreFollowError) {
        this.entityGameId = entityGameId;
        this.pokemonIndexId = (short) pokemonIndexId;
        this.followerPokemonRarity = (byte) followerPokemonRarity;
        this.ignoreFollowError = ignoreFollowError;
    }

    public SendSetFollowPokemonPacket(long entityGameId, int pokemonIndexId, int sex,
                                      boolean isShiny, boolean isAlpha, boolean ignoreFollowError) {
        this(entityGameId, pokemonIndexId, buildFollowerRarity(sex, isShiny, isAlpha), ignoreFollowError);
    }

    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeLongLE(entityGameId);
        buffer.writeShortLE(pokemonIndexId);
        buffer.writeByte(followerPokemonRarity);
        buffer.writeBoolean(ignoreFollowError);
    }

    private static int buildFollowerRarity(int sex, boolean isShiny, boolean isAlpha) {
        int rarity = sex == 1 ? 0x20 : 0;
        if (isShiny) {
            rarity |= 0x40;
        }
        if (isAlpha) {
            rarity |= 0x80;
        }
        return rarity;
    }
}
