package org.pokemmo.gameserver.game.trainer;

import org.pokemmo.gameserver.util.JsonUtil;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

public class TrainerTeamManager extends JsonUtil {
    private final HashMap<Short, TrainerTeamData> trainerTeams = new HashMap<>(20);
    private final Map<String, TrainerNpcBinding> trainerBindingByScript = new HashMap<>();
    public TrainerTeamManager(String trainerTeamFilePath) {
        loadTrainerTeams(trainerTeamFilePath);
        java.io.File sidecar = new java.io.File(
                new java.io.File(trainerTeamFilePath).getParent(), "KantoTrainer.jsonc");
        if (sidecar.isFile()) {
            loadTrainerTeams(sidecar.getPath());
        }
    }
    public void loadTrainerTeams(String jsonFilePath) {
        try (Reader reader = new FileReader(jsonFilePath))
        {
            JsonTrainerTeamConfigs config = getGson().fromJson(reader, JsonTrainerTeamConfigs.class);
            // 转换JSON配置为Script对象
            for (JsonTrainerTeamConfig trainerTeamConfig : config.getTrainerTeams()) {
                trainerTeams.put(trainerTeamConfig.getTrainerTeamId(), trainerTeamConfig.toTrainerTeam());
            }
            if (config.getNpcBindings() != null) {
                for (JsonTrainerBindingConfig binding : config.getNpcBindings()) {
                    if (binding.getScript() != null && !binding.getScript().isBlank()) {
                        trainerBindingByScript.put(binding.getScript(), new TrainerNpcBinding(
                                binding.getTrainerTeamId(), Math.max(1, binding.getSightRange())));
                    }
                }
            }
            getLogger().info("成功加载 {} 个训练队伍", trainerTeams.size());
        } catch (IOException e) {
            getLogger().error("加载训练队伍配置失败: {}", e.getMessage(), e);
        } catch (Exception e) {
            getLogger().error("解析训练队伍配置失败: {}", e.getMessage(), e);
        }
    }
    public TrainerTeamData getTrainerTeam(short trainerTeamId) {
        return trainerTeams.get(trainerTeamId);
    }

    public TrainerTeamData getTrainerTeamByScript(String script) {
        TrainerNpcBinding binding = trainerBindingByScript.get(script);
        return binding == null ? null : trainerTeams.get(binding.trainerTeamId());
    }

    public short getTrainerTeamIdByScript(String script) {
        TrainerNpcBinding binding = trainerBindingByScript.get(script);
        return binding == null ? (short) -1 : binding.trainerTeamId();
    }

    public TrainerNpcBinding getTrainerNpcBindingByScript(String script) {
        return trainerBindingByScript.get(script);
    }

}
