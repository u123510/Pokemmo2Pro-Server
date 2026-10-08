package org.pokemmo.gameserver.game.entity;

import java.util.Objects;

/** The 14 fixed fields follow f.uk_0.vl0(); conditional event execution is not implemented. */
public record EventNpcSpawnRequest(NpcSpawnRequest npc, NpcSpawnAppearance appearance) {
    private static final int BASE_ARGUMENTS = 14;
    private static final int MAX_CONDITIONS = 64;

    public EventNpcSpawnRequest {
        Objects.requireNonNull(npc, "NPC 参数不能为空");
        Objects.requireNonNull(appearance, "事件外观参数不能为空");
        if (appearance.eventId() < 0) throw new IllegalArgumentException("事件生成的事件编号必须在 0..6 范围内");
    }

    public static EventNpcSpawnRequest parse(String[] args) {
        if (args == null || args.length < BASE_ARGUMENTS) {
            throw new IllegalArgumentException("eventspawnnpc 需要 14 个基础参数，不能使用 spawnnpc 的六参数格式");
        }
        int count = integer(args[13], "条件数量");
        if (count < 0 || count > MAX_CONDITIONS || args.length != BASE_ARGUMENTS + count * 2) {
            throw new IllegalArgumentException("条件数量必须在 0..64，且参数数量必须为 14 + 条件数量 * 2");
        }
        int eventId = integer(args[0], "事件编号");
        if (eventId < 0 || eventId > 6) throw new IllegalArgumentException("事件编号必须在 0..6 范围内");
        NpcSpawnRequest npc = new NpcSpawnRequest(integer(args[1], "外观编号"),
                integer(args[6], "脚本偏移"), integer(args[2], "外观地区"),
                integer(args[3], "移动类型"), integer(args[4], "横向范围"), integer(args[5], "纵向范围"));
        int flagId = integer(args[7], "标志编号");
        int flagValue = integer(args[8], "标志值");
        if (flagId < -1 || flagId > 65535 || flagValue < -1 || flagValue > 65535) {
            throw new IllegalArgumentException("标志编号和值必须在 -1..65535 范围内");
        }
        boolean updateExisting = bool(args[9], "更新已有 NPC");
        boolean sparkles = bool(args[10], "闪光");
        boolean ignoreDuplicates = bool(args[11], "忽略重复检查");
        float scale;
        try {
            scale = Float.parseFloat(args[12]);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("缩放必须是数值，例如 1.0", exception);
        }
        NpcSpawnAppearance appearance = new NpcSpawnAppearance(eventId, sparkles, scale);
        if (flagId != -1 || flagValue != -1) {
            throw new IllegalArgumentException("事件标志条件尚未接入，标志编号和值请都填 -1");
        }
        if (updateExisting) {
            throw new IllegalArgumentException("事件工具更新已有 NPC 尚未接入，请取消该选项；不会覆盖原生 NPC");
        }
        if (ignoreDuplicates) {
            throw new IllegalArgumentException("不支持忽略重复检查，请取消该选项并使用空位置");
        }
        if (count != 0) {
            throw new IllegalArgumentException("附加事件条件尚未接入，条件数量请填 0；不会忽略条件直接生成");
        }
        return new EventNpcSpawnRequest(npc, appearance);
    }

    private static int integer(String text, String name) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " 必须是 32 位十进制整数", exception);
        }
    }

    private static boolean bool(String text, String name) {
        if ("true".equalsIgnoreCase(text)) return true;
        if ("false".equalsIgnoreCase(text)) return false;
        throw new IllegalArgumentException(name + " 必须明确填写 true 或 false");
    }
}
