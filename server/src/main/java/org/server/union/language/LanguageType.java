package org.server.union.language;

public enum LanguageType {
    ENGLISH(0),
    FRENCH(1),
    GERMAN(2),
    SPANISH(3),
    PORTUGUESE(4),
    ITALIAN(5),
    DUTCH(6),
    POLISH(7),
    GREEK(8),
    TURKISH(9),
    FILIPINO(10),
    RUSSIAN(11),
    korean(12),
    JAPANESE(13),
    CHINESE(14),
    OTHER(15);
    private byte type;
    private static final LanguageType[] allTypeArray = {ENGLISH, FRENCH, GERMAN, SPANISH, PORTUGUESE, ITALIAN, DUTCH, POLISH, GREEK, TURKISH, FILIPINO, RUSSIAN, korean, JAPANESE, CHINESE, OTHER};
    LanguageType(int type) {
        this.type = (byte) type;
    }
    public byte getType() {
        return type;
    }
    public static LanguageType getByType(byte type) {
        return allTypeArray[type];
    }
}
