package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SendBattleDebutPokemonCanActionPacket extends OutgoingPacket {
    private final byte pokemonInTeamIndex;
    private final boolean canAction;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
       buffer.writeByte(pokemonInTeamIndex| (canAction?128:0));
    }
}
