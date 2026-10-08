package org.pokemmo.gameserver.game.map;

import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class EncounterLoadStatistics {
    private final Map<String, RegionStats> byRegion = new HashMap<>();

    EncounterLoadStatistics() {
        byRegion.put("kanto", new RegionStats());
        byRegion.put("hoenn", new RegionStats());
        byRegion.put("sinnoh", new RegionStats());
        byRegion.put("unmapped", new RegionStats());
    }

    String regionForPath(Path path) {
        for (Path part : path.toAbsolutePath()) {
            String name = part.toString().toLowerCase(Locale.ROOT);
            if (name.equals("kanto")
                    || name.equals("hoenn")
                    || name.equals("sinnoh")) {
                return name;
            }
            if (name.equals("unmapped")) {
                return "unmapped";
            }
        }
        return "unmapped";
    }

    void recordFile(String region) {
        stats(region).fileCount++;
    }

    void recordEncounter(String region, String mapName) {
        RegionStats stats = stats(region);
        stats.recordCount++;
        stats.mapKeys.add(WildEncounterManager.normalizeMapKey(mapName));
    }

    void log(Logger logger) {
        logger.info(
                "野外遭遇总计: {} 个文件，{} 条支持记录，覆盖 {} 个地图键",
                byRegion.values().stream().mapToInt(stats -> stats.fileCount).sum(),
                byRegion.values().stream().mapToInt(stats -> stats.recordCount).sum(),
                byRegion.values().stream().mapToInt(stats -> stats.mapKeys.size()).sum()
        );
        byRegion.forEach((region, stats) -> logger.info(
                "地区 {} 野外遭遇: {} 个文件，{} 条支持记录，覆盖 {} 个地图键",
                region,
                stats.fileCount,
                stats.recordCount,
                stats.mapKeys.size()
        ));
    }

    private RegionStats stats(String region) {
        return byRegion.computeIfAbsent(region, ignored -> new RegionStats());
    }

    private static final class RegionStats {
        private int fileCount;
        private int recordCount;
        private final Set<String> mapKeys = new HashSet<>();
    }
}
