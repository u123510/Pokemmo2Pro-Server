package org.pokemmo.gameserver.game.skin;
import org.pokemmo.gameserver.game.character.CharacterData;

public enum SkinType {
  FOREHEAD(0,-1, true, false),
  HAT(1,3, true, false),
  HAIR(2,0, false, false),
  EYES(3,1, false, false),
  FACIAL_HAIR(4,2, true, true),
  BACK(5,-1, true, false),
  TOP(6,-1, false, false),
  GLOVES(7,-1, true, false),
  FOOTWEAR(8,-1, false, false),
  LEGGINGS(9,-1, false, false),
  FISHING_ROD(10,-1, false, false),
  BIKE(11,-1, false, false);
  private static final int BIKE_ITEM_INDEX_BASE = 4816;
  private static final int BIKE_ITEM_INDEX_MAX = 4877;
  public static final SkinType[] wearables = {FOREHEAD, HAT, HAIR, EYES, FACIAL_HAIR, BACK, TOP, GLOVES, FOOTWEAR, LEGGINGS, BIKE};
  private final int type;
  private final int id;
  private final boolean unk1;
  private final boolean unk2;

  SkinType(int type,int id, boolean unk1, boolean unk2) {
    this.type = type;
    this.id = id;
    this.unk1 = unk1;
    this.unk2 = unk2;
  }

  public short getSkin(CharacterData character) {
    return switch (this) {
      case FOREHEAD -> character.getSkinData().getForehead();
      case HAT -> character.getSkinData().getHat();
      case HAIR -> character.getSkinData().getHair();
      case EYES -> character.getSkinData().getEyes();
      case FACIAL_HAIR -> character.getSkinData().getFacialHair();
      case BACK -> character.getSkinData().getBack();
      case TOP -> character.getSkinData().getTop();
      case GLOVES -> character.getSkinData().getGloves();
      case FOOTWEAR -> character.getSkinData().getFootwear();
      case LEGGINGS -> character.getSkinData().getLeggings();
      case FISHING_ROD -> character.getSkinData().getFishingRod();
      case BIKE -> character.getSkinData().getBike();
    };
  }
  public byte getColor(CharacterData character) {
    return (byte) switch (this) {
      case FOREHEAD -> character.getSkinData().getForeheadColor();
      case HAT -> character.getSkinData().getHatColor();
      case HAIR -> character.getSkinData().getHairColor();
      case EYES -> character.getSkinData().getEyesColor();
      case FACIAL_HAIR -> character.getSkinData().getFacialHairColor();
      case BACK -> character.getSkinData().getBackColor();
      case TOP -> character.getSkinData().getTopColor();
      case GLOVES -> character.getSkinData().getGlovesColor();
      case FOOTWEAR -> character.getSkinData().getFootwearColor();
      case LEGGINGS -> character.getSkinData().getLeggingsColor();
      case FISHING_ROD, BIKE -> 0;
    };
  }

    public void setSkin(CharacterData character, short skin) {
      switch (this) {
        case FOREHEAD -> character.getSkinData().setForehead(skin);
        case HAT -> character.getSkinData().setHat(skin);
        case HAIR -> character.getSkinData().setHair(skin);
        case EYES -> character.getSkinData().setEyes(skin);
        case FACIAL_HAIR -> character.getSkinData().setFacialHair(skin);
        case BACK -> character.getSkinData().setBack(skin);
        case TOP -> character.getSkinData().setTop(skin);
        case GLOVES -> character.getSkinData().setGloves(skin);
        case FOOTWEAR -> character.getSkinData().setFootwear(skin);
        case LEGGINGS -> character.getSkinData().setLeggings(skin);
        case FISHING_ROD -> character.getSkinData().setFishingRod(skin);
        case BIKE -> character.getSkinData().setBike(skin);
      }
    }
    public void setColor(CharacterData character, short color) {
      switch (this) {
        case FOREHEAD -> character.getSkinData().setForeheadColor(color);
        case HAT -> character.getSkinData().setHatColor(color);
        case HAIR -> character.getSkinData().setHairColor(color);
        case EYES -> character.getSkinData().setEyesColor(color);
        case FACIAL_HAIR -> character.getSkinData().setFacialHairColor(color);
        case BACK -> character.getSkinData().setBackColor(color);
        case TOP -> character.getSkinData().setTopColor(color);
        case GLOVES -> character.getSkinData().setGlovesColor(color);
        case FOOTWEAR -> character.getSkinData().setFootwearColor(color);
        case LEGGINGS -> character.getSkinData().setLeggingsColor(color);
      }
    }
    public int getType() {
       return type;
    }

    public static SkinType getByType(int type) {
      for (SkinType skinType : values()) {
        if (skinType.type == type) {
          return skinType;
        }
      }
      return null;
    }

    /** Returns whether the slot is one of the ten client GTL fashion categories. */
    public static boolean isGtlFashionSlot(int type) {
      return type >= FOREHEAD.type && type <= LEGGINGS.type;
    }

    /** Returns whether an inventory item index maps to a wearable client addon. */
    public static boolean isWearableItemIndex(short itemIndexId) {
      for (SkinType skinType : wearables) {
        if (skinType.getAddonIdFromItemIndex(itemIndexId) >= 0) {
          return true;
        }
      }
      return false;
    }

    /** Converts the client inventory index for a cosmetic item to its addon id. */
    public short getAddonIdFromItemIndex(short itemIndexId) {
      int itemIndex = itemIndexId & 0xFFFF;
      if (this == BIKE && (itemIndex < BIKE_ITEM_INDEX_BASE || itemIndex > BIKE_ITEM_INDEX_MAX)) {
        return -1;
      }
      if (this == HAT && itemIndex >= 4576 && itemIndex < 4816) {
        return (short) (itemIndex - 4320);
      }
      int addonId = itemIndex - (2000 + type * 256);
      return addonId >= 0 && addonId <= 255 ? (short) addonId : -1;
    }
}
