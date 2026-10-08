package org.pokemmo.gameserver.codecs;
import org.pokemmo.gameserver.game.skin.SkinType;

import java.util.List;

public class Codecs {
    public static final CharacterCodec CHARACTER_CODEC_NO_MAC = new CharacterCodec(false);
    public static final CharacterCodec CHARACTER_CODEC_WITH_MAC = new CharacterCodec(true);
    public static final SkinCodec SKIN_CODEC_1 = new SkinCodec(true, List.of(SkinType.values()));
    public static final SkinCodec SKIN_CODEC_2 = new SkinCodec(false, List.of(SkinType.values()));
    public static final CharacterGuildCodec CHARACTER_GUILD_CODEC = new CharacterGuildCodec();
    public static final GameEventFlagsCodec GAMEEVENTFLAGS_CODEC = new GameEventFlagsCodec();
    public static final PokemonCodec POKEMON_CODEC = new PokemonCodec();
    public static final ChatMessageCodec CHAT_MESSAGE_CODEC = new ChatMessageCodec();
    public static final GameLocalStringCodec GAME_LOCAL_STRING_CODEC = new GameLocalStringCodec();
    public static final ItemCodec ITEM_CODEC = new ItemCodec();
    public static final InstanceInfoCodec INSTANCE_INFO_CODEC = new InstanceInfoCodec();
}
