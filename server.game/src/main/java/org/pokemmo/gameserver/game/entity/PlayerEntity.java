package org.pokemmo.gameserver.game.entity;

import lombok.Getter;
import lombok.Setter;
import org.pokemmo.gameserver.game.permission.PermissionType;
@Getter @Setter
public class PlayerEntity extends Entity {
    private byte sex;
    private String playerName;
    private byte transportation;
    private EntityNameplateType entityNameplateType;
    private PermissionType permission;
    private short modelRegionIndexId;
    private short modelIndexId;
    private short followPokemonIndexId;
    private short followPokemonRarity;
    private String unionName;
    public PlayerEntity(long entityGameId, int regionIndexId, int mapHeaderIdOrGbaMapGroupId, int gbaMapId, int x, int y, int z, int toward, int sex, String playerName, int transportation, EntityNameplateType entityNameplateType, PermissionType permission, int modelRegionIndexId, int modelIndexId, int followPokemonIndexId, int followPokemonRarity, String unionName) {
        super(entityGameId, regionIndexId, mapHeaderIdOrGbaMapGroupId, gbaMapId, x, y, z, toward);
        this.sex = (byte) sex;
        this.playerName = playerName;
        this.transportation = (byte) transportation;
        this.entityNameplateType = entityNameplateType;
        this.permission = permission;
        this.modelRegionIndexId = (short) modelRegionIndexId;
        this.modelIndexId = (short) modelIndexId;
        this.followPokemonIndexId = (short) followPokemonIndexId;
        this.followPokemonRarity = (short) followPokemonRarity;
        this.unionName = unionName;
    }

    @Override
    public String getEntityName() {
        return playerName;
    }

    public void upDatePos(byte regionIndexId, byte mapHeaderIdOrGbaMapGroupId, byte gbaMapId, short x, short y, byte z, byte moveToward) {
        upDateRegionIndexId(regionIndexId);
        upDateMapHeaderIdOrGbaMapGroupId(mapHeaderIdOrGbaMapGroupId);
        upDateGbaMapId(gbaMapId);
        upDateX(x);
        upDateY(y);
        upDateZ(z);
        upDateToward(moveToward);
    }
}