package org.pokemmo.gameserver.game.move;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MoveManager {
    private static final HashMap<Short, PokemonMoveData> pokemonMoves = new HashMap<>(746);
    private static final Logger logger = LoggerFactory.getLogger(MoveManager.class);
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    public MoveManager (String moveFilePath, String customMoveDirectoryPath) {
        loadMoves(moveFilePath);
        loadCustomMoves(customMoveDirectoryPath);
    }
    public void loadMoves(String binFilePath)  {
        MoveDataReader moveDataReader = new MoveDataReader(new File(binFilePath));
        try {
            List<PokemonMoveData> pokemonMoveDataList = moveDataReader.readPokemonMoveData();
            for (PokemonMoveData pokemonMoveData : pokemonMoveDataList) {
                pokemonMoves.put(pokemonMoveData.getMoveIndexId(), pokemonMoveData);
            }
            logger.info("成功加载 {} 个技能信息 (Move.bin)", pokemonMoves.size());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 加载自定义技能目录：目录及其子目录（如 gen6、gen9）下每个 *.json 文件对应一个技能，
     * 文件内容为单个技能对象（字段与 move.json 导出格式一致，属性/伤害类型/目标用枚举名字符串），
     * 文件名建议使用技能中文名。相同 moveIndexId 会覆盖主文件中的技能；
     * 目录不存在时跳过；单个文件解析失败不影响其他文件。
     */
    public void loadCustomMoves(String directoryPath) {
        File directory = new File(directoryPath);
        if (!directory.isDirectory()) {
            logger.info("自定义技能目录不存在，跳过加载: {}", directoryPath);
            return;
        }
        List<File> files = new ArrayList<>();
        try (var paths = Files.walk(directory.toPath())) {
            paths.filter(p -> Files.isRegularFile(p) && p.toString().toLowerCase().endsWith(".json"))
                    .forEach(p -> files.add(p.toFile()));
        } catch (IOException e) {
            logger.error("遍历自定义技能目录失败: {}", directoryPath, e);
            return;
        }
        files.sort(File::compareTo);
        int loadedCount = 0;
        for (File file : files) {
            try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                JsonMoveInfoConfig config = gson.fromJson(reader, JsonMoveInfoConfig.class);
                if (config == null || config.getMovePokemonType() == null
                        || config.getMoveDamageType() == null || config.getMoveTargetType() == null) {
                    logger.error("自定义技能文件缺少必填字段，跳过: {}", file.getAbsolutePath());
                    continue;
                }
                pokemonMoves.put(config.getMoveIndexId(), config.toPokemonMoveData());
                loadedCount++;
            } catch (Exception e) {
                logger.error("解析自定义技能文件失败: {}", file.getAbsolutePath(), e);
            }
        }
        logger.info("成功加载 {} 个自定义技能信息, 当前总计 {}", loadedCount, pokemonMoves.size());
    }
    public static PokemonMoveData getPokemonMove(short moveIndexId) {
        return pokemonMoves.get(moveIndexId);
    }
}
