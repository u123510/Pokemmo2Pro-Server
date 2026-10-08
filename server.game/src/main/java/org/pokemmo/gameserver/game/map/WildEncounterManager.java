package org.pokemmo.gameserver.game.map;

import com.google.gson.annotations.SerializedName;
import org.pokemmo.gameserver.game.pokemon.PokemonManager;
import org.pokemmo.gameserver.util.JsonUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Loads regional GBA/NDS encounter tables without changing their JSON shape. */
public final class WildEncounterManager extends JsonUtil {
    public static final int MAX_ENCOUNTER_RATE = 1600;

    private static final Pattern SPECIES_DEFINE = Pattern.compile(
            "^\\s*#define\\s+(SPECIES_[A-Z0-9_]+)\\s+(\\d+)\\s*$"
    );

    private final Map<String, List<WildMapEncounter>> encountersByMap = new HashMap<>();
    private final EnumMap<EncounterArea, int[]> slotRates = new EnumMap<>(EncounterArea.class);
    private final EnumMap<FishingRod, int[]> fishingSlotGroups = new EnumMap<>(FishingRod.class);
    private final Map<String, Integer> speciesIds = new HashMap<>();
    private final Set<String> warnedSpecies = new HashSet<>();
    private final EncounterLoadStatistics encounterStats = new EncounterLoadStatistics();

    public WildEncounterManager(String encounterRootPath, String speciesHeaderPath) {
        loadSpeciesIds(speciesHeaderPath);
        loadEncounterTables(encounterRootPath);
    }

    public int getEncounterRate(MapData map, EncounterArea area) {
        WildEncounterTable table = getTable(map, area);
        return table == null ? 0 : Math.max(0, table.encounterRate);
    }

    public Optional<WildEncounterSelection> selectEncounter(
            MapData map, EncounterArea area, Random random) {
        if (area == EncounterArea.FISHING) {
            return Optional.empty();
        }
        WildEncounterTable table = getTable(map, area);
        if (table == null || table.mons == null || table.mons.isEmpty()) {
            return Optional.empty();
        }
        int slot = chooseSlot(area, table.mons.size(), null, random);
        return createSelection(map, area, table, slot, random);
    }

    public Optional<WildEncounterSelection> selectFishingEncounter(
            MapData map, FishingRod rod, Random random) {
        if (rod == null) {
            return Optional.empty();
        }
        WildEncounterTable table = getTable(map, EncounterArea.FISHING);
        if (table == null || table.mons == null || table.mons.isEmpty()) {
            return Optional.empty();
        }
        int slot = chooseSlot(
                EncounterArea.FISHING,
                table.mons.size(),
                fishingSlotGroups.get(rod),
                random
        );
        return createSelection(map, EncounterArea.FISHING, table, slot, random);
    }

    private Optional<WildEncounterSelection> createSelection(
            MapData map,
            EncounterArea area,
            WildEncounterTable table,
            int slot,
            Random random) {
        if (slot < 0 || slot >= table.mons.size()) {
            return Optional.empty();
        }
        WildPokemonSlot pokemonSlot = table.mons.get(slot);
        Integer pokemonIndexId = resolveSpeciesId(pokemonSlot.species);
        if (pokemonIndexId == null) {
            warnUnknownSpecies(pokemonSlot.species);
            return Optional.empty();
        }
        if (PokemonManager.getPokemonoexData(pokemonIndexId) == null) {
            getLogger().warn("野外遭遇表中的宝可梦未加载: species={}, id={}, map={}",
                    pokemonSlot.species, pokemonIndexId, map.getMapKey());
            return Optional.empty();
        }
        int minLevel = Math.max(1, pokemonSlot.minLevel);
        int maxLevel = Math.max(minLevel, pokemonSlot.maxLevel);
        int level = minLevel + random.nextInt(maxLevel - minLevel + 1);
        return Optional.of(new WildEncounterSelection(
                pokemonIndexId,
                (short) level,
                pokemonSlot.species,
                map.getMapKey(),
                area
        ));
    }

    public static String normalizeMapKey(String mapName) {
        if (mapName == null) {
            return "";
        }
        String normalized = mapName.toUpperCase(Locale.ROOT);
        if (normalized.startsWith("MAP_")) {
            normalized = normalized.substring(4);
        }
        normalized = normalized.replaceAll("[^A-Z0-9]", "");
        if (normalized.equals("CINNABARCITY")) {
            return "CINNABARISLAND";
        }
        return normalized;
    }

    private WildEncounterTable getTable(MapData map, EncounterArea area) {
        if (map == null || area == null) {
            return null;
        }
        List<WildMapEncounter> encounters = encountersByMap.get(normalizeMapKey(map.getMapKey()));
        if (encounters == null || encounters.isEmpty()) {
            return null;
        }
        return encounters.get(0).table(area);
    }

    private int chooseSlot(EncounterArea area, int slotCount, int[] allowedSlots, Random random) {
        int[] rates = slotRates.get(area);
        if (slotCount <= 0) {
            return -1;
        }
        if (allowedSlots == null) {
            allowedSlots = new int[Math.min(slotCount, rates == null ? slotCount : rates.length)];
            for (int i = 0; i < allowedSlots.length; i++) {
                allowedSlots[i] = i;
            }
        }
        if (allowedSlots.length == 0) {
            return -1;
        }
        if (rates == null || rates.length == 0) {
            return allowedSlots[random.nextInt(allowedSlots.length)];
        }
        int total = 0;
        for (int slot : allowedSlots) {
            if (slot >= 0 && slot < slotCount && slot < rates.length) {
                total += Math.max(0, rates[slot]);
            }
        }
        if (total <= 0) {
            return allowedSlots[random.nextInt(allowedSlots.length)];
        }
        int roll = random.nextInt(total);
        for (int slot : allowedSlots) {
            if (slot < 0 || slot >= slotCount || slot >= rates.length) {
                continue;
            }
            roll -= Math.max(0, rates[slot]);
            if (roll < 0) {
                return slot;
            }
        }
        return allowedSlots[allowedSlots.length - 1];
    }

    private void loadSpeciesIds(String speciesHeaderPath) {
        try {
            for (String line : Files.readAllLines(Path.of(speciesHeaderPath), StandardCharsets.UTF_8)) {
                Matcher matcher = SPECIES_DEFINE.matcher(line);
                if (matcher.matches()) {
                    speciesIds.put(matcher.group(1), Integer.parseInt(matcher.group(2)));
                }
            }
            getLogger().info("成功加载 {} 个宝可梦常量", speciesIds.size());
        } catch (IOException | RuntimeException exception) {
            getLogger().error("加载宝可梦常量失败: {}", speciesHeaderPath, exception);
        }
    }
    private void loadEncounterTables(String encounterRootPath) {
        Path encounterPath = Path.of(encounterRootPath);
        if (Files.isRegularFile(encounterPath)) {
            loadEncounterFile(encounterPath);
            return;
        }
        List<Path> splitFiles = new ArrayList<>();
        collectMapJsonFiles(encounterPath.getParent() == null
                ? null
                : encounterPath.getParent().resolve("map"), splitFiles);
        collectNamedFiles(encounterPath.getParent() == null
                ? null
                : encounterPath.getParent().resolve("map"), "Wild_Encounters.jsonc", splitFiles);
        collectDirectJsoncFiles(encounterPath.resolve("unmapped"), splitFiles);
        if (splitFiles.isEmpty()) {
            Path legacyFile = encounterPath.resolve("wild_encounters.json");
            if (Files.isRegularFile(legacyFile)) {
                loadEncounterFile(legacyFile);
            } else {
                getLogger().warn("未找到野外遭遇表文件: {}", encounterRootPath);
            }
        } else {
            splitFiles.stream().distinct().forEach(this::loadEncounterFile);
        }
        encounterStats.log(getLogger());
    }
    private void collectMapJsonFiles(Path root, List<Path> target) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> !path.getParent().getFileName().toString().equalsIgnoreCase("terrain"))
                    .sorted()
                    .forEach(target::add);
        } catch (IOException exception) {
            getLogger().error("扫描 openmmo 地图遭遇表失败: {}", root, exception);
        }
    }
    private void collectNamedFiles(Path root, String fileName, List<Path> target) {
        if (root == null || !Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> fileName.equalsIgnoreCase(path.getFileName().toString()))
                    .sorted()
                    .forEach(target::add);
        } catch (IOException exception) {
            getLogger().error("扫描地图野外遭遇表失败: {}", root, exception);
        }
    }
    private void collectDirectJsoncFiles(Path root, List<Path> target) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> paths = Files.list(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jsonc"))
                    .sorted()
                    .forEach(target::add);
        } catch (IOException exception) {
            getLogger().error("扫描未匹配地图的野外遭遇表失败: {}", root, exception);
        }
    }
    private void loadEncounterFile(Path encounterTablePath) {
        try {
            String region = encounterStats.regionForPath(encounterTablePath);
            if (encounterTablePath.getFileName().toString().endsWith(".json")) {
                loadOpenMmoMapFile(encounterTablePath, region);
                return;
            }
            WildEncounterDocument document = getGson().fromJson(
                    Files.readString(encounterTablePath, StandardCharsets.UTF_8),
                    WildEncounterDocument.class
            );
            if (document == null || document.wildEncounterGroups == null) {
                throw new IllegalStateException("wild_encounter_groups 为空");
            }
            for (WildEncounterGroup group : document.wildEncounterGroups) {
                if (group == null) {
                    continue;
                }
                loadSlotRates(group.fields);
                if (group.encounters == null) {
                    continue;
                }
                for (WildMapEncounter encounter : group.encounters) {
                    if (isSupportedEncounter(encounter, region)) {
                        encountersByMap.computeIfAbsent(
                                normalizeMapKey(encounter.map), ignored -> new ArrayList<>()
                        ).add(encounter);
                        encounterStats.recordEncounter(region, encounter.map);
                    }
                }
            }
            encounterStats.recordFile(region);
        } catch (IOException | RuntimeException exception) {
            getLogger().error("加载野外遭遇表失败: {}", encounterTablePath, exception);
        }
    }
    private void loadOpenMmoMapFile(Path mapPath, String region) throws IOException {
        OpenMmoMapConfig config = getGson().fromJson(
                Files.readString(mapPath, StandardCharsets.UTF_8),
                OpenMmoMapConfig.class
        );
        if (config == null) {
            return;
        }
        for (OpenMmoMapConfig.EncounterFieldConfig field : config.encounterFields) {
            if (field == null || field.type == null || field.encounter_rates == null) {
                continue;
            }
            EncounterArea area = EncounterArea.fromJsonName(field.type);
            if (area != null) {
                slotRates.put(area, field.encounter_rates);
            }
            if (area == EncounterArea.FISHING && field.groups != null) {
                for (FishingRod rod : FishingRod.values()) {
                    int[] slots = field.groups.get(rod.jsonName);
                    if (slots != null) {
                        fishingSlotGroups.put(rod, slots);
                    }
                }
            }
        }
        for (OpenMmoMapConfig.WildEncounterConfig source : config.wildEncounters) {
            WildMapEncounter encounter = new WildMapEncounter();
            encounter.map = source.map;
            encounter.baseLabel = source.base_label;
            encounter.landMons = convertEncounterTable(source.land_mons);
            encounter.waterMons = convertEncounterTable(source.water_mons);
            encounter.rockSmashMons = convertEncounterTable(source.rock_smash_mons);
            encounter.fishingMons = convertEncounterTable(source.fishing_mons);
            if (isSupportedEncounter(encounter, region)) {
                encountersByMap.computeIfAbsent(
                        normalizeMapKey(encounter.map), ignored -> new ArrayList<>()
                ).add(encounter);
                encounterStats.recordEncounter(region, encounter.map);
            }
        }
        encounterStats.recordFile(region);
    }
    private WildEncounterTable convertEncounterTable(
            OpenMmoMapConfig.EncounterTableConfig source
    ) {
        if (source == null) {
            return null;
        }
        WildEncounterTable table = new WildEncounterTable();
        table.encounterRate = source.encounter_rate;
        table.mons = new ArrayList<>();
        for (OpenMmoMapConfig.EncounterSlotConfig slot : source.mons) {
            WildPokemonSlot converted = new WildPokemonSlot();
            converted.minLevel = slot.min_level;
            converted.maxLevel = slot.max_level;
            converted.species = slot.species;
            table.mons.add(converted);
        }
        return table;
    }
    private void loadSlotRates(List<WildEncounterField> fields) {
        if (fields == null) {
            return;
        }
        for (WildEncounterField field : fields) {
            if (field == null || field.type == null || field.encounterRates == null) {
                continue;
            }
            EncounterArea area = EncounterArea.fromJsonName(field.type);
            if (area != null) {
                slotRates.put(area, field.encounterRates);
            }
            if (area == EncounterArea.FISHING && field.groups != null) {
                for (FishingRod rod : FishingRod.values()) {
                    int[] slots = field.groups.get(rod.jsonName);
                    if (slots != null) {
                        fishingSlotGroups.put(rod, slots);
                    }
                }
            }
        }
    }

    private Integer resolveSpeciesId(String species) {
        if (species == null) {
            return null;
        }
        try {
            return Integer.parseInt(species);
        } catch (NumberFormatException ignored) {
            return speciesIds.get(species);
        }
    }

    private boolean isSupportedEncounter(WildMapEncounter encounter, String region) {
        return encounter != null
                && encounter.map != null
                && encounter.baseLabel != null
                && (!"kanto".equals(region) && !"unmapped".equals(region)
                || encounter.baseLabel.endsWith("_FireRed"));
    }

    private void warnUnknownSpecies(String species) {
        if (species != null && warnedSpecies.add(species)) {
            getLogger().warn("野外遭遇表中的物种常量未找到: {}", species);
        }
    }

    public enum EncounterArea {
        LAND("land_mons"),
        WATER("water_mons"),
        ROCK_SMASH("rock_smash_mons"),
        FISHING("fishing_mons");

        private final String jsonName;

        EncounterArea(String jsonName) {
            this.jsonName = jsonName;
        }

        private static EncounterArea fromJsonName(String jsonName) {
            for (EncounterArea area : values()) {
                if (area.jsonName.equals(jsonName)) {
                    return area;
                }
            }
            return null;
        }
    }

    public enum FishingRod {
        OLD_ROD("old_rod"),
        GOOD_ROD("good_rod"),
        SUPER_ROD("super_rod");

        private final String jsonName;

        FishingRod(String jsonName) {
            this.jsonName = jsonName;
        }
    }

    public record WildEncounterSelection(
            int pokemonIndexId,
            short level,
            String speciesConstant,
            String mapKey,
            EncounterArea area
    ) {
    }

    private static final class WildEncounterDocument {
        @SerializedName("wild_encounter_groups")
        private List<WildEncounterGroup> wildEncounterGroups;
    }

    private static final class WildEncounterGroup {
        private String label;
        @SerializedName("for_maps")
        private boolean forMaps;
        private List<WildEncounterField> fields;
        private List<WildMapEncounter> encounters;
    }

    private static final class WildEncounterField {
        private String type;
        @SerializedName("encounter_rates")
        private int[] encounterRates;
        private Map<String, int[]> groups;
    }

    private static final class WildMapEncounter {
        private String map;
        @SerializedName("base_label")
        private String baseLabel;
        @SerializedName("land_mons")
        private WildEncounterTable landMons;
        @SerializedName("water_mons")
        private WildEncounterTable waterMons;
        @SerializedName("rock_smash_mons")
        private WildEncounterTable rockSmashMons;
        @SerializedName("fishing_mons")
        private WildEncounterTable fishingMons;

        private WildEncounterTable table(EncounterArea area) {
            return switch (area) {
                case LAND -> landMons;
                case WATER -> waterMons;
                case ROCK_SMASH -> rockSmashMons;
                case FISHING -> fishingMons;
            };
        }
    }

    private static final class WildEncounterTable {
        @SerializedName("encounter_rate")
        private int encounterRate;
        private List<WildPokemonSlot> mons;
    }

    private static final class WildPokemonSlot {
        @SerializedName("min_level")
        private int minLevel;
        @SerializedName("max_level")
        private int maxLevel;
        private String species;
    }
}
