package org.pokemmo.gameserver.protocol.packets.c2s;

import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.interact.TrainerInteractionService;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.protocol.packets.s2c.SendHasEventPacket;

public class NpctriggerBattlePacket extends IncomingPacket {
    private byte unk;
    private byte isTwoNpcTrigger;
    private long npcId;
    private short triggerNpcX;
    private short triggerNpcY;
    private long npc2Id;
    private short triggerNpc2X;
    private short triggerNpc2Y;
    @Override
    public void decode(ByteBufEx buffer) {
        unk = buffer.readByte();
        isTwoNpcTrigger = buffer.readByte();
        npcId = buffer.readLongLE();
        triggerNpcX = buffer.readShortLE();
        triggerNpcY = buffer.readShortLE();
        if(isTwoNpcTrigger == 1) {
            npc2Id = buffer.readLongLE();
            triggerNpc2X  = buffer.readShortLE();
            triggerNpc2Y = buffer.readShortLE();
        }
    }

    @Override
    public void handle(Session session) throws Exception {
         CharacterManager manager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
         if (manager != null && manager.getBattleManager() != null) {
             return;
         }
         if (manager != null && TrainerInteractionService.startPendingBattle(manager)) {
             return;
         }
         session.send(new SendHasEventPacket(false));
    }
}
