package org.pokemmo.gameserver.game.move;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
@Getter @Setter @AllArgsConstructor
public class MoveAdditionStringData {
    //技能附加信息字符串索引id
    int additionStringIndexId;
    List<MoveAdditionStringFormatData> stringFormatDatas = new ArrayList<>();
}
