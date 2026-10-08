package org.pokemmo.gameserver.game.entity;

/** Event ID is a saved editor category, not an instruction to activate a holiday. */
public record NpcSpawnAppearance(int eventId, boolean sparkles, float spriteScale) {
    public static final NpcSpawnAppearance DEFAULT = new NpcSpawnAppearance(-1, false, 1.0f);

    public NpcSpawnAppearance {
        if (eventId < -1 || eventId > 6) {
            throw new IllegalArgumentException("事件编号必须在 -1..6 范围内，-1 表示普通 NPC");
        }
        if (!Float.isFinite(spriteScale) || spriteScale < 0.25f || spriteScale > 4.0f) {
            throw new IllegalArgumentException("NPC 缩放必须是 0.25..4.0 的有限数值");
        }
    }

    public void applyTo(NpcEntity npc) {
        // Existing 0x12 flags: 2048 -> MO.I80 visual effect, 4096 -> float scale.
        npc.setUnk6(sparkles);
        npc.setIsSpriteScaleOverride(spriteScale != 1.0f, spriteScale);
    }
}
