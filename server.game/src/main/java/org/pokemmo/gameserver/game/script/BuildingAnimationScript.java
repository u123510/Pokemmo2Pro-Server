package org.pokemmo.gameserver.game.script;

import lombok.Getter;

@Getter
public class BuildingAnimationScript extends GameScript {
     private short buildingIndexId;
     private short doorIndexId;
     private boolean isOpen;
      public BuildingAnimationScript(int buildingIndexId, int doorIndexId, boolean isOpen) {
          super(ScriptActionType.BUILDING_ANIMATION);
          this.buildingIndexId = (short) buildingIndexId;
          this.doorIndexId = (short) doorIndexId;
          this.isOpen = isOpen;
      }
}
