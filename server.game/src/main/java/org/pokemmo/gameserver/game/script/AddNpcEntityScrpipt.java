package org.pokemmo.gameserver.game.script;

import org.pokemmo.gameserver.game.entity.NpcEntity;

public class AddNpcEntityScrpipt {
   private NpcEntity npcEntity;
   public AddNpcEntityScrpipt(NpcEntity npcEntity) {
       this.npcEntity = npcEntity;
   }
   public NpcEntity getNpcEntity() {
       return npcEntity;
   }
}
