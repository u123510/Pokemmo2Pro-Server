package org.pokemmo.gameserver.protocol.packets.c2s;
import org.pokemmo.gameserver.game.character.CharacterData;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.story.StoryService;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.protocol.packets.s2c.SendLoadPlayerPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendRenderScreenPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendVariantSkinInfoPacket;
public class RequestPlayerPacket extends IncomingPacket {
    private CharacterManager characterManager;
    private CharacterData characterData;
    @Override
    public void decode(ByteBufEx buffer) {}

    @Override
    public void handle(Session session) throws Exception {
        characterManager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        characterData = characterManager.getCharacterData();
        session.send(
                new SendLoadPlayerPacket(characterData),
                //发送玩家的皮肤信息
                new SendVariantSkinInfoPacket(characterData),
                //发送黑屏
                new SendRenderScreenPacket(true)
        );
        //设置玩家为加载完成
        characterManager.completeMapLoad();
        //地图初始化完成后再同步附近玩家，避免客户端清理提前到达的实体。
        characterManager.synchronizeVisiblePlayersAfterMapLoad();
        StoryService.onMapReady(characterManager);
    }
}
