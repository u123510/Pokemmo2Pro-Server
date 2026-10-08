package org.pokemmo.gameserver.game.story;

import org.pokemmo.gameserver.game.character.CharacterData;

/** The opening remains complete while the parcel and Pokedex chapter advances. */
public record OakParcelProgress(short labStage, short parcelStatus) {
    public enum Phase { OPENING, PICKUP, DELIVER, POKEDEX, COMPLETE }

    public OakParcelProgress {
        if (labStage < 0 || labStage > 9 || parcelStatus < 0 || parcelStatus > 3) {
            throw new IllegalArgumentException("大木包裹剧情存档无效");
        }
    }

    public Phase phase() {
        if (labStage < PalletStoryProgress.COMPLETE) return Phase.OPENING;
        if (labStage >= 6) return Phase.COMPLETE;
        if (labStage == 5 || parcelStatus == 2) return Phase.POKEDEX;
        if (parcelStatus == 1) return Phase.DELIVER;
        // 3 was the schema's uninitialized/custom default before this chapter existed.
        return Phase.PICKUP;
    }

    public String phaseName() {
        return switch (phase()) {
            case OPENING -> "等待完成真新镇开场";
            case PICKUP -> "前往常磐市领取包裹";
            case DELIVER -> "回研究所交付包裹";
            case POKEDEX -> "领取图鉴与精灵球";
            case COMPLETE -> "本章已完成";
        };
    }

    public void apply(CharacterData character) {
        character.setOakLabStatus(labStage);
        character.setOakParcelStatus(parcelStatus);
    }

    public static OakParcelProgress from(CharacterData character) {
        return new OakParcelProgress(character.getOakLabStatus(), character.getOakParcelStatus());
    }
}
