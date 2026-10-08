package org.pokemmo.gameserver.game.item;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @AllArgsConstructor
public class ItemAdditionStringData {
    //技能附加信息字符串索引id
    int additionStringIndexId;
    List<ItemAdditionStringFormatData> stringFormatDatas = new ArrayList<>();
}
