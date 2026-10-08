package org.pokemmo.gameserver.game.holiday;

public enum HolidayType {
    NONE(-1),
    HALLOWEEN(0),
    XMAS(1),
    CNY(2),
    APRIL_FOOLS(3),
    MOON_CAKE_FESTIVAL(4),
    TENTH_ANNIVERSARY(5),
    ANNIVERSARY(6);
    private byte type;
    private static HolidayType[] allTypeArray = values();
    HolidayType(int type) {
        this.type = (byte) type;
    }
    public byte getType() {
        return type;
    }
    public static HolidayType getByType(int type) {
      for(HolidayType holidayType : allTypeArray) {
        if(holidayType.getType() == (byte) type) {
          return holidayType;
        }
      }
      return null;
    }
}
