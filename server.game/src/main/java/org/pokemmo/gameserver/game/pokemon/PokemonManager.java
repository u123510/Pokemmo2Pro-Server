package org.pokemmo.gameserver.game.pokemon;
import lombok.Getter;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.move.MoveManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.services.GameServerService;
import org.pokemmo.gameserver.util.JsonUtil;
import org.pokemmo.gameserver.util.SnowflakeIdGenerator;
import org.server.Session;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

@Getter
public class PokemonManager extends JsonUtil {
    private static final HashMap<Short, PokemonDexData> pokemonDexDataHashMap = new HashMap<>(900);
    // Temporary wild-encounter rates; restore to 50000 and 10 after testing.
    private static final int WILD_SHINY_RATE_DENOMINATOR = 1;
    private static final int WILD_SECRET_SHINY_RATE_DENOMINATOR = 2;
    //队伍首位对战外触发的特性
    //威吓，压迫感，火焰之躯
    public static ArrayList<Short> outBattleAbility = new ArrayList<>(Arrays.asList(new Short[]{22,46,49}));
    //在对战登场直接触发的特性
    //降雨，威吓，扬沙，压迫感，日照，气闸，慢启动，降雪，紧张感，分析，变身者
    private static ArrayList<Short> debutDirectTriggerAbility = new ArrayList<>(Arrays.asList(new Short[]{2, 22, 45, 46, 70, 76, 88, 112, 117, 127, 150}));
    //在对战登场条件触发的特性
    //阴晴不定，察觉，多属性，花之礼物
    private static ArrayList<Short> debutConditionTriggerAbility = new ArrayList<>(Arrays.asList(new Short[]{59,119,121,122}));
    public PokemonManager(String pokemonInfoFilePath) {
        this(pokemonInfoFilePath, Path.of(pokemonInfoFilePath).toAbsolutePath().normalize().getParent().toString());
    }

    public PokemonManager (String pokemonInfoFilePath, String customSpeciesDirectoryPath) {
        loadPokemonsInfo(pokemonInfoFilePath);
        loadCustomPokemons(customSpeciesDirectoryPath);
    }

    public static ArrayList<Short> getDebutDirectTriggerAbility() {
        return debutDirectTriggerAbility;
    }
    public static ArrayList<Short> getDebutConditionTriggerAbility() {
        return debutConditionTriggerAbility;
    }
    public void loadPokemonsInfo(String jsonFilePath) {
        try (Reader reader = new FileReader(jsonFilePath))
        {
            JsonPokemonInfoConfigs config = getGson().fromJson(reader, JsonPokemonInfoConfigs.class);
            // 转换JSON配置为Script对象
            int fileCount = 0;
            for (JsonPokemonInfoConfig pokemonInfoConfig : config.getPokemonInfoConfigs()) {
                pokemonDexDataHashMap.put(pokemonInfoConfig.getId(), pokemonInfoConfig.toPokemonInfo());
                fileCount++;
            }
            getLogger().info("成功加载 {} 个宝可梦信息 ({}), 当前总计 {}",
                    fileCount, new File(jsonFilePath).getName(), pokemonDexDataHashMap.size());
        } catch (IOException e) {
            getLogger().error("加载宝可梦技能配置失败: {}", e.getMessage(), e);
        } catch (Exception e) {
            getLogger().error("解析宝可梦技能配置失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 加载自定义宝可梦目录：目录及其子目录（如 gen1、gen7）下每个 *.json 文件对应一只宝可梦，
     * 文件内容为单个宝可梦对象（结构与 Pokemon.jsonc 数组内条目一致），文件名建议使用宝可梦中文名。
     * 相同 id 会覆盖已加载的条目；目录不存在时跳过；单个文件解析失败不影响其他文件。
     */
    public void loadCustomPokemons(String directoryPath) {
        File directory = new File(directoryPath);
        if (!directory.isDirectory()) {
            getLogger().info("自定义宝可梦目录不存在，跳过加载: {}", directoryPath);
            return;
        }
        List<File> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(directory.toPath())) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().toLowerCase().endsWith(".json"))
                    .forEach(path -> files.add(path.toFile()));
        } catch (IOException e) {
            getLogger().error("遍历自定义宝可梦目录失败: {}", directoryPath, e);
            return;
        }
        files.sort(File::compareTo);
        int loadedCount = 0;
        for (File file : files) {
            try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                JsonPokemonInfoConfig pokemonInfoConfig = getGson().fromJson(reader, JsonPokemonInfoConfig.class);
                if (pokemonInfoConfig == null) {
                    getLogger().error("自定义宝可梦文件内容为空，跳过: {}", file.getAbsolutePath());
                    continue;
                }
                pokemonDexDataHashMap.put(pokemonInfoConfig.getId(), pokemonInfoConfig.toPokemonInfo());
                loadedCount++;
            } catch (Exception e) {
                getLogger().error("解析自定义宝可梦文件失败: {}", file.getAbsolutePath(), e);
            }
        }
        getLogger().info("成功加载 {} 个自定义宝可梦信息, 当前总计 {}", loadedCount, pokemonDexDataHashMap.size());
    }
    public static PokemonDexData getPokemonoexData(int pokemonIndexId) {
        return pokemonDexDataHashMap.get((short) pokemonIndexId);
    }

    public static short calculateMaxHp(PokemonData pokemon) {
        if (pokemon == null || pokemon.getPokemonDexData() == null
                || pokemon.getPokemonIvs() == null || pokemon.getPokemonEvs() == null
                || pokemon.getPokemonIvs().length <= PokemonStatType.HP.getType()
                || pokemon.getPokemonEvs().length <= PokemonStatType.HP.getType()) {
            return 0;
        }
        return pokemon.getPokemonDexData().getPokemonAbilityValue(
                PokemonStatType.HP,
                pokemon.getPokemonIvs()[PokemonStatType.HP.getType()],
                pokemon.getPokemonEvs()[PokemonStatType.HP.getType()],
                pokemon.getLevel(),
                pokemon.getNatureType());
    }

    /**
     * Returns the loaded Pokedex data used by server-side filters.
     */
    public static Collection<PokemonDexData> getAllPokemonDexData() {
        return List.copyOf(pokemonDexDataHashMap.values());
    }

    private static short[] getMovesBasePp(short[] moves) {
        short[] movesPp = new short[4];
        for(int i = 0;i<4;i++){
            if(moves[i]>0){
                movesPp[i] = MoveManager.getPokemonMove(moves[i]).getMoveBasePp();
            }
        }
        return movesPp;
    }

    public static PokemonData createWildPokemon(long trainerId,
                                                String trainerName,
                                                short regionIndexId,
                                                short catchAddress,
                                                short pokemonIndexId,
                                                short pokemonLevel,
                                                int containerId,
                                                short containerPosition,
                                                Random random,
                                                SnowflakeIdGenerator snowflakeIdGenerator) {
        PokemonDexData pokemonInfo = getPokemonoexData(pokemonIndexId);
        if (pokemonInfo == null) {
            return null;
        }

        short[] pokemonIvs = new short[6];
        short[] pokemonEvs = new short[6];
        for (int i = 0; i < pokemonIvs.length; i++) {
            pokemonIvs[i] = (short) random.nextInt(31);
        }

        int personalityValue = random.nextInt(Integer.MAX_VALUE);
        PokemonNatureType natureType = PokemonNatureType.getByPersonalityValue(personalityValue);
        short maxHp = pokemonInfo.getPokemonAbilityValue(
                PokemonStatType.HP,
                pokemonIvs[PokemonStatType.HP.getType()],
                pokemonEvs[PokemonStatType.HP.getType()],
                pokemonLevel,
                natureType
        );
        short[] moves = pokemonInfo.genderWildPokemonMoves(pokemonLevel);

        PokemonData pokemon = new PokemonData.Builder()
                .setPokemonDexData(pokemonInfo)
                .setPokemonId(snowflakeIdGenerator.nextId())
                .setTrainerId(trainerId)
                .setContainerPos(containerPosition)
                .setPokemonIndexId(pokemonIndexId)
                .setPersonalityValue(personalityValue)
                .setPokemonNatureType(natureType)
                .setLevel(pokemonLevel)
                .setPokemonCurrentHp(maxHp)
                .setPokemonMaxHp(maxHp)
                .setPokemonIvs(pokemonIvs)
                .setPokemonEvs(pokemonEvs)
                .setMoves(moves)
                .setMovesPp(getMovesBasePp(moves))
                .setAbilityIndex((short) 0)
                .setBallType((short) 3)
                .build();

        pokemon.setContainerId(containerId);
        pokemon.setOriginalTrainerId(trainerId);
        pokemon.setOtName(trainerName);
        pokemon.setName("");
        pokemon.setExp(pokemonInfo.getGetExpSpeedType().getExpByLevel(pokemonLevel));
        pokemon.setCatchAddress(catchAddress);
        pokemon.setCatchLevel(pokemonLevel);
        pokemon.setCatchRegion(regionIndexId);
        pokemon.setEggValue((short) 0);
        pokemon.setHiddenPowerType((short) -1);
        pokemon.setCatchTime(LocalDateTime.now());

        applyWildRarityAndParticle(pokemon, random);
        return pokemon;
    }
    public static PokemonData genderWildPokemon(Session characterSession, short pokemonIndexId, short pokemonLevel,short pokemonBallType) {
        CharacterManager characterManager = characterSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        GameServerService characterService = characterManager.getCharacterService();
        Random random = characterManager.getRandom();
        long trainerId = characterManager.getCharacterData().getPlayerEntity().getEntityGameId();
        PokemonDexData pokemonInfo = getPokemonoexData(pokemonIndexId);
        if (pokemonInfo == null) {
            // 没找到该宝可梦的信息
            return null;
        }
        long pokemonId = characterManager.getSnowflakeIdGenerator().nextId();
        PokemonData pokemon = new PokemonData.Builder()
                .setPokemonId(pokemonId)
                .setTrainerId(trainerId)
                .build();
        //宝可梦个体值
        short[] pokemonIvs = {0,0,0,0,0,0};
        short[] pokemonEvs = {0,0,0,0,0,0};
        for(int i = 0;i<6;i++){
            pokemonIvs[i] = (short) random.nextInt(31);
        }
        //设置宝可梦的容器与位置
        short position = characterManager.findNextFreePartyPosition();
        boolean isPartyFull = false;
        if(position == -1){
            isPartyFull = true;
            position = characterService.findNextFreePcBoxPosition(trainerId);
            if(position == -1){
                return null;
            }
        }
        if(isPartyFull){
            pokemon.setContainerId(characterService.getContainerByType(PokemonContainerType.PC).getId());
        }
        else{
            pokemon.setContainerId(characterService.getContainerByType(PokemonContainerType.PARTY).getId());
        }
        pokemon.setContainerPosition(position);
        pokemon.setPokemonIndexId(pokemonIndexId);
        int personalityValue = random.nextInt();
        PokemonNatureType natureType = PokemonNatureType.getByPersonalityValue(personalityValue);
        pokemon.setPersonalityValue(personalityValue);
        pokemon.setOriginalTrainerId(trainerId);
        pokemon.setOtName(characterManager.getCharacterData().getPlayerEntity().getEntityName());
        pokemon.setName("");
        pokemon.setLevel(pokemonLevel);
        pokemon.setCurrentHp(pokemonInfo.getPokemonAbilityValue(PokemonStatType.HP,pokemonIvs[0],pokemonEvs[0],pokemonLevel,natureType));
        //TODO 野生宝可梦可能存在道具
        pokemon.setExp(pokemonInfo.getGetExpSpeedType().getExpByLevel(pokemonLevel));
        //亲密度默认为0
        //设置技能与pp
        short[] moves = pokemonInfo.genderWildPokemonMoves(pokemonLevel);
        pokemon.setMoves(moves);
        pokemon.setMovesPp(getMovesBasePp(moves));
        //获取玩家当前的地图位置
        short characterRegionIndexId = characterManager.getCharacterData().getPlayerEntity().getRegionIndexId();
        //设置宝可梦的捕获地址
        pokemon.setCatchAddress(characterManager.getCurrentMapDatas()[0].getRomMapHeaderIndex());
        pokemon.setCatchLevel(pokemonLevel);
        pokemon.setCatchRegion(characterRegionIndexId);
        //TODO 根据捕捉的球类型设定
        pokemon.setBallType(pokemonBallType);
        //TODO 设置特性
        applyWildRarityAndParticle(pokemon, random);
        pokemon.setCatchTime(LocalDateTime.now());
        return pokemon;
    }

    private static void applyWildRarityAndParticle(PokemonData pokemon, Random random) {
        boolean isShiny = random.nextInt(WILD_SHINY_RATE_DENOMINATOR) == 0;
        boolean isSecret = isShiny
                && random.nextInt(WILD_SECRET_SHINY_RATE_DENOMINATOR) == 0;
        pokemon.setShiny(isShiny);
        pokemon.setSecret(isSecret);
        if (isShiny) {
            // Particle value 0 is the client's reserved shiny debut effect. The
            // SECRET rarity bit makes the client choose its secret-shiny variant;
            // particle ID 4 is a normal custom particle (ghost), not shiny.
            pokemon.setCurrentSelectParticleEffectType(PokemonData.SHINY_DEBUT_PARTICLE_EFFECT);
        }
    }
    public static PokemonData genderGiftPokemon(Session characterSession, short giftId) {
        CharacterManager characterManager = characterSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        GameServerService characterService = characterManager.getCharacterService();
        Random random = characterManager.getRandom();
        long trainerId = characterManager.getCharacterData().getPlayerEntity().getEntityGameId();
        GiftPokemonInfo giftPokemonInfo = characterManager.getScriptManager().getGiftPokemonManager().getGiftPokemonInfoByGiftId(giftId);
        if(giftPokemonInfo == null){
            return null;
        }
        short pokemonIndexId = giftPokemonInfo.getPokemonIndexId();
        short pokemonLevel = giftPokemonInfo.getLevel();
        short[] moves = giftPokemonInfo.getMoves();
        byte ballType = giftPokemonInfo.getBallType();
        PokemonDexData pokemonInfo = getPokemonoexData(pokemonIndexId);
        if(pokemonInfo == null){
            return null;
        }
        long pokemonId = characterManager.getSnowflakeIdGenerator().nextId();
        PokemonData pokemon = new PokemonData.Builder()
                .setPokemonId(pokemonId)
                .setTrainerId(trainerId)
                .build();
        //宝可梦个体值
        short[] pokemonIvs = {15,15,15,15,15,15};
        short[] pokemonEvs = {0,0,0,0,0,0};
        short partyPosition = characterManager.findNextFreePartyPosition();
        if(partyPosition == -1){
            //队伍不存在空闲位置
            return null;
        }
        pokemon.setContainerId(characterService.getContainerByType(PokemonContainerType.PARTY).getId());
        pokemon.setContainerPosition(partyPosition);
        pokemon.setPokemonIndexId(pokemonIndexId);
        int[] possibleRemainders = {0, 6, 12, 18, 24};
        int selectedIndex = random.nextInt(possibleRemainders.length);
        int remainder = possibleRemainders[selectedIndex];
        // 生成一个随机基数，然后计算personalityValue
        int base = random.nextInt(Integer.MAX_VALUE / 25);
        int personalityValue = base * 25 + remainder;
        PokemonNatureType natureType = PokemonNatureType.getByPersonalityValue(personalityValue);
        pokemon.setPersonalityValue(personalityValue);
        pokemon.setOtName(characterManager.getCharacterData().getPlayerEntity().getEntityName());
        pokemon.setName("");
        pokemon.setLevel(pokemonLevel);
        pokemon.setCurrentHp(pokemonInfo.getPokemonAbilityValue(PokemonStatType.HP,pokemonIvs[0],pokemonEvs[0],pokemonLevel,natureType));
        pokemon.setExp(pokemonInfo.getGetExpSpeedType().getExpByLevel(pokemonLevel));
        pokemon.setMoves(moves);
        pokemon.setMovesPp(getMovesBasePp(moves));
        short characterRegionIndexId = characterManager.getCharacterData().getPlayerEntity().getRegionIndexId();
        //设置宝可梦的捕获地址
        pokemon.setCatchAddress(characterManager.getCurrentMapDatas()[0].getRomMapHeaderIndex());
        pokemon.setCatchLevel(pokemonLevel);
        pokemon.setCatchRegion(characterRegionIndexId);
        pokemon.setBallType(ballType);
        pokemon.setPokemonIvs(pokemonIvs);
        //设置礼物宝可梦缎带
        pokemon.addNormalRibbon(2);
        pokemon.setCatchTime(LocalDateTime.now());
        return pokemon;
    }

}
