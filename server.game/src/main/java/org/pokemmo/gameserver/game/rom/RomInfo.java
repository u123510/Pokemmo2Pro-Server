package org.pokemmo.gameserver.game.rom;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class RomInfo {
    private final String code;
    private final byte romRevision;
    private final RomType romType;
}
