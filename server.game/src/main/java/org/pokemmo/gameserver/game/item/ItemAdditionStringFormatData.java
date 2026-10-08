package org.pokemmo.gameserver.game.item;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public class ItemAdditionStringFormatData {
   private byte replaceIndex;
   private byte stringType;
   private List<Short> stringFormatDatas = new ArrayList<>();

}
