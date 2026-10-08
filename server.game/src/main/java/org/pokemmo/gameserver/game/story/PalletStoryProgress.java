package org.pokemmo.gameserver.game.story;

import org.pokemmo.gameserver.game.character.CharacterData;

/** Persisted checkpoints; later legacy Kanto stages must never be reset to the opening. */
public record PalletStoryProgress(short stage, short starter) {
    public static final short NEW = 0;
    public static final short ESCORT = 1;
    public static final short CHOOSE = 2;
    public static final short RIVAL = 3;
    public static final short COMPLETE = 4;

    public PalletStoryProgress {
        if (stage < NEW || stage > 9 || starter < 0 || starter > 3) {
            throw new IllegalArgumentException("真新镇剧情存档数值无效");
        }
        if (stage == RIVAL && starter == 3) {
            throw new IllegalArgumentException("旧剧情存档缺少初始宝可梦选择，请先核对存档");
        }
    }

    public boolean completed() {
        return stage >= COMPLETE;
    }

    /** Keep legacy fields readable, but never replay the prelude with an already-claimed starter. */
    public void validateOpeningCheckpoint() {
        if (stage < RIVAL && starter != 3) {
            throw new IllegalStateException("开场存档冲突: oak_lab_status=" + stage
                    + ", first_partner_status[1]=" + starter
                    + "。阶段0..2要求初始选择为3（未选择），0/1/2表示已经选择"
                    + "；请完整退出角色后核对两项字段，不会自动重置已有宝可梦或存档");
        }
    }

    public static PalletStoryProgress from(CharacterData character) {
        if (character == null || character.getFirstPartnerStatus() == null
                || character.getFirstPartnerStatus().length != 5) {
            throw new IllegalArgumentException("角色剧情存档尚未加载");
        }
        return new PalletStoryProgress(character.getOakLabStatus(), character.getFirstPartnerStatus()[0]);
    }

    public void apply(CharacterData character) {
        character.setOakLabStatus(stage);
        character.getFirstPartnerStatus()[0] = starter;
    }
}
