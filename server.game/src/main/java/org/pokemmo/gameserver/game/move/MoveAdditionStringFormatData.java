package org.pokemmo.gameserver.game.move;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
@Getter @Setter
public class MoveAdditionStringFormatData {
   private byte replaceIndex;
   private byte stringType;
   private List<Short> stringFormatDatas = new ArrayList<>();

}
