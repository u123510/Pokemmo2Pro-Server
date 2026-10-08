package org.pokemmo.gameserver.game.trainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class KantoTrainerCatalogTest {
    private static final Path RESOURCE = Files.isDirectory(Path.of("resource"))
            ? Path.of("resource") : Path.of("../resource");

    @Test
    void sidecarContainsOriginalKantoBindingsAndGymTeams() {
        TrainerTeamManager manager = new TrainerTeamManager(
                RESOURCE.resolve("trainer/Trainer.jsonc").toString());

        TrainerTeamData brock = manager.getTrainerTeam((short) 414);
        assertNotNull(brock);
        assertEquals(2, brock.getTrainerBattleTeams().get(0).getTrainerPokemonDatas().length);

        TrainerTeamData towerGrunt = manager.getTrainerTeamByScript(
                "PokemonTower_7F_EventScript_Grunt1");
        assertNotNull(towerGrunt);
        assertEquals(TrainerLevelType.LowTrainer, towerGrunt.getTrainerLevelType());
        assertEquals(2, manager.getTrainerNpcBindingByScript(
                "Route10_EventScript_Carol").sightRange());
    }
}
