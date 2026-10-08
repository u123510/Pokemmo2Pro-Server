package org.pokemmo.gameserver.game.interact;

public enum GameInteractionType {
    MSG_OBTAIN(0),
    MSG_FIND(1),
    MSG_FACEPLAYER(2),
    MSG_SIGN(3),
    MSG_NOCLOSE(4),
    MSG_YESNO(5),
    MSG_LOCK(6),
    MSG_ITEM(7),
    MSG_NAV_TOOL(8),
    MSG_SELECT_PARTY_MONSTER(9),
    BUY_COINS(10),
    VENDING_MACHINE(11),
    MOVE_TUTOR(12),
    MAP_SELECTOR(13),
    ITEM_SELECTION(14),
    BRAILLE(15),
    REQUEST_BATTLE(16),
    REQUEST_FRIEND(17),
    REQUEST_TRADE(18),
    REQUEST_LINK(19),
    REQUEST_GUILD_INVITE(20),
    MSG_SELECT_DAYCARE_MONSTER(21),
    MSG_BREED_DIALOG(22),
    MSG_SELECT_MONSTER_SKILL(23),
    BERRY_PLANTING_DIALOG(24),
    BERRY_BLEND_DIALOG(25),
    COINS_SHOP_MONSTERS(26),
    MSG_STRING(27),
    MOVE_TUTOR_COST(28),
    UNK(29),
    STARTER_SELECT_DIALOG(30),
    MULTICHOICE(31),
    MSG_NOYES(32),
    WAIT(33),
    WAIT_CANCELABLE(34),
    BF_DIALOG(35),
    NDS(36),
    NDS_WAIT_BUTTON(37),
    NDS_INPUT_WAIT_BUTTON(38),
    NDS_INPUT_YESNO(39),
    NDS_INPUT_MULTICHOICE(40),
    NDS_SHOW_AT(41),
    NDS_CLOSE_SHOW_AT(42),
    ITEM_SELECTION_COUNTS(43),
    MSG_LOCK_AUTOCLOSE(44),
    MONSTER_SELECT(45),
    UNK2(46),
    CLOSE(47),
    CLIENT_ONLY(48),
    CLIENT_ONLY_MULTICHOICE(49);
    private byte type;
    GameInteractionType(int type) {
        this.type = (byte)type;
    }
    public byte getType(){
        return this.type;
    }

    /**
     * Returns the value used in the client-facing interaction packet.
     *
     * The resource/script value is not the same as the client wire value for
     * several interaction types. Keep this mapping in one place so a script
     * cannot accidentally open a different client widget (for example, wire
     * value 31 is the coin-shop widget, while MULTICHOICE uses wire value 36).
     */
    public byte getProtocolType() {
        return switch (this) {
            case MSG_SELECT_PARTY_MONSTER -> (byte) 12;
            case BUY_COINS -> (byte) 13;
            case VENDING_MACHINE -> (byte) 14;
            case MOVE_TUTOR -> (byte) 15;
            case MAP_SELECTOR -> (byte) 16;
            case ITEM_SELECTION -> (byte) 18;
            case BRAILLE -> (byte) 19;
            case REQUEST_BATTLE -> (byte) 20;
            case REQUEST_FRIEND -> (byte) 21;
            case REQUEST_TRADE -> (byte) 22;
            case REQUEST_LINK -> (byte) 23;
            case REQUEST_GUILD_INVITE -> (byte) 24;
            case MSG_SELECT_DAYCARE_MONSTER -> (byte) 25;
            case MSG_BREED_DIALOG -> (byte) 26;
            case MSG_SELECT_MONSTER_SKILL -> (byte) 27;
            case BERRY_PLANTING_DIALOG -> (byte) 28;
            case BERRY_BLEND_DIALOG -> (byte) 29;
            case COINS_SHOP_MONSTERS -> (byte) 31;
            case MSG_STRING -> (byte) 32;
            case MOVE_TUTOR_COST -> (byte) 33;
            case UNK -> (byte) 34;
            case STARTER_SELECT_DIALOG -> (byte) 35;
            case MULTICHOICE -> (byte) 36;
            case MSG_NOYES -> (byte) 40;
            case WAIT -> (byte) 42;
            case WAIT_CANCELABLE -> (byte) 43;
            case BF_DIALOG -> (byte) 44;
            case NDS -> (byte) 45;
            case NDS_WAIT_BUTTON -> (byte) 46;
            case NDS_INPUT_WAIT_BUTTON -> (byte) 47;
            case NDS_INPUT_YESNO -> (byte) 48;
            case NDS_INPUT_MULTICHOICE -> (byte) 49;
            case NDS_SHOW_AT -> (byte) 50;
            case NDS_CLOSE_SHOW_AT -> (byte) 51;
            case ITEM_SELECTION_COUNTS -> (byte) 52;
            case MSG_LOCK_AUTOCLOSE -> (byte) 53;
            case MONSTER_SELECT -> (byte) 54;
            case UNK2 -> (byte) 55;
            case CLOSE -> (byte) 100;
            case CLIENT_ONLY -> (byte) -1;
            case CLIENT_ONLY_MULTICHOICE -> (byte) -2;
            default -> this.type;
        };
    }
    public static GameInteractionType getGameInteractionType(int type) {
        for (GameInteractionType interactionType : values()) {
            if (interactionType.getType() == type) {
                return interactionType;
            }
        }
        return null;
    }
}
