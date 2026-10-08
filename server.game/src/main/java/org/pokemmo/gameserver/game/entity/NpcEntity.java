package org.pokemmo.gameserver.game.entity;

import lombok.Getter;
import lombok.Setter;
import org.pokemmo.db.jooq.tables.records.PokemonRecord;


import java.util.Arrays;
@Getter @Setter
public class NpcEntity extends Entity implements Cloneable {
    private String npcName;
    /** Map resource script key; kept separate from the stable entity name. */
    private String interactionScriptName;
    /** Per-character event flag used to hide this map entity after a story action. */
    private String hideFlag;
    /** True for an entity declared by the immutable map resource. */
    private boolean mapResourceEntity;
    /** Optional server-side shop binding, independent of story scripts and runtime IDs. */
    private String shopId;

    private volatile boolean isLoad;

    private byte npcModelRegionIndexId;
    private short npcModelIndexId;
    private byte defaultToward;
    private byte moveMentType;
    private byte movementLeashX;
    private byte movementLeashY;
    private byte interactType;
    private boolean isTrainer;
    private short npcTrainId;
    private byte trainAggroRange;

    private boolean isDoubleBattle;

    private boolean isFarmland;

    private boolean isResetNpcModel;
    private byte newNpcModelLength;
    private byte newNpcModelWidth;

    private volatile boolean canInteract = true;

    private boolean isLowHeight;

    private boolean isCompanion;

    private boolean isTree;

    private boolean isWantRematch;

    private boolean hasFollowedPokemon;
    private short followPokemonIndexId;

    private boolean isOtherPlayer;

    private boolean isWantedPokemon;
    private short wantedPokemonIndexId;
    private byte wantedPokemonAmount;

    public boolean unk6;

    private boolean isSpriteScaleOverride;
    private float spriteScaleOverride;

    private boolean isOtherPlayerSkin;

    private boolean hasSpecMoveBehavior = false;

    private short npcTeamIndex;
    private PokemonRecord[] fightTeam;
    @Override
    public String getEntityName(){
        return npcName;
    }
    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
    public void setEntityGameId(long entityGameId){
        super.setEntityGameId(entityGameId);
    }
    public NpcEntity(long entityGameId, boolean isLoad, String npcName, int defaultToward, int moveMentType, int movementLeashX, int movementLeashY,int regionIndexId, int mapHeaderIdOrGbaMapGroupId, int gbaMapId,int npcModelRegionIndex,int npcModelIndexId,int x, int y, int z){
        super(entityGameId, regionIndexId, mapHeaderIdOrGbaMapGroupId, gbaMapId, x, y, z,defaultToward);
        this.isLoad = isLoad;
        this.npcName = npcName;
        this.npcModelRegionIndexId = (byte) npcModelRegionIndex;
        this.npcModelIndexId = (short) npcModelIndexId;
        this.defaultToward = (byte) defaultToward;
        this.moveMentType = (byte) moveMentType;
        this.movementLeashX = (byte) movementLeashX;
        this.movementLeashY = (byte) movementLeashY;
    }
    public void setIsTrainer(boolean isTrainer, int npcTrainId, int trainAggroRange, boolean isDoubleBattle){
        this.isTrainer = isTrainer;
        this.npcTrainId = (short) npcTrainId;
        this.trainAggroRange = (byte) trainAggroRange;
        this.isDoubleBattle = isDoubleBattle;
    }
    public void setIsResetNpcModel(boolean isResetNpcModel, int npcModelLength, int npcModelWidth) {
        this.isResetNpcModel = isResetNpcModel;
        this.newNpcModelLength = (byte) npcModelLength;
        this.newNpcModelWidth = (byte) npcModelWidth;
    }
    public void setHasFollowedPokemon(boolean hasFollowedPokemon,int followPokemonIndexId){
        this.hasFollowedPokemon = hasFollowedPokemon;
        this.followPokemonIndexId = (short) followPokemonIndexId;
    }
    public void setIsWantedPokemon(boolean isWantedPokemon,int wantedPokemonIndexId,int wantedPokemonAmount){
        this.isWantedPokemon = isWantedPokemon;
        this.wantedPokemonIndexId = (short) wantedPokemonIndexId;
        this.wantedPokemonAmount = (byte) wantedPokemonAmount;
    }
    public void setIsSpriteScaleOverride(boolean isSpriteScaleOverride,float spriteScaleOverride) {
       this.isSpriteScaleOverride = isSpriteScaleOverride;
       this.spriteScaleOverride = spriteScaleOverride;
    }
    public void addFightPokemon(PokemonRecord pokemonRecord){
        this.fightTeam = Arrays.copyOf(this.fightTeam, this.fightTeam.length + 1);
        this.fightTeam[this.fightTeam.length - 1] = pokemonRecord;
    }
    public void setSpriteScaleOverride(float spriteScaleOverride){
        this.spriteScaleOverride = spriteScaleOverride;
        this.isSpriteScaleOverride = true;
    }
}
