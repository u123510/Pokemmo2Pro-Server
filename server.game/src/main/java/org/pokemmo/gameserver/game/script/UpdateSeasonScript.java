package org.pokemmo.gameserver.game.script;

public class UpdateSeasonScript {
     private byte season;
     public UpdateSeasonScript(byte season) {
         this.season = season;
     }
     public byte getSeason() {
         return season;
     }
}
