package org.pokemmo.gameserver.protocol.packets.scriptsession;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.server.Session;
@Slf4j
public class InteractorEntityIdUtils {
    public static long getEntityIdByInteractor(Session session, String interactor){
        CharacterManager characterManager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        switch (interactor){
            case "Narration":
                return -1;
            case "Self":
                return characterManager.getCharacterData().getPlayerEntity().getEntityGameId();
            case "LastInteractor":
                return characterManager.getInteractManager().getLastInteractorEntityId();
            default:
                NpcEntity npcEntity = characterManager.getCurrentMapDatas()[0].getNpcEntityHashMap().get(interactor);
                if(npcEntity == null){
                    log.error("交互id获取器错误:{} {}", interactor);
                    return -1;
                }
                return npcEntity.getEntityGameId();
        }
    }
}
