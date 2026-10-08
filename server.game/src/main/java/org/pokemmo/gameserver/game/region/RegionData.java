package org.pokemmo.gameserver.game.region;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.pokemmo.gameserver.game.map.MapConnection;
import org.pokemmo.gameserver.game.map.MapConnectionType;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.map.MapHashUtils;
import java.util.HashMap;
@Getter @Setter @AllArgsConstructor
public class RegionData {
    private RegionType regionType;
    private final HashMap<Integer, MapData> regionMaps= new HashMap<>();
    //通过地图头与地图id查询该地区的地图数据
    public MapData getMapData(byte mapHeaderIdOrGbaMapGroupId, byte gbaMapId) {
        return regionMaps.get(MapHashUtils.getHash(regionType.getType(),mapHeaderIdOrGbaMapGroupId,gbaMapId));
    }
    //查询跟与目标地图连接的地图
    public HashMap<MapConnectionType,MapData> getConnectMaps(MapData targetMapData){
        HashMap<MapConnectionType,MapData> connectMaps = new HashMap<>(0);
        for (MapConnection mapConnection : targetMapData.getMapConnections().values()) {
            MapData connectMap = getMapData(mapConnection.getConnectMapHeaderIdOrGbaMapGroup(),mapConnection.getConnectGbaMapId());
            if(connectMap != null) {
                connectMaps.put(mapConnection.getMapConnectionType(),connectMap);
            }
        }
        return connectMaps;
    }
}
