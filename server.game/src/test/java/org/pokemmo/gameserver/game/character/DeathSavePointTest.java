package org.pokemmo.gameserver.game.character;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pokemmo.gameserver.game.battle.BattleType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathSavePointTest {
    @TempDir Path temporary;

    @Test
    void suppliedPointsHaveNewIdsAndKeepHomeDestinationAndOpaqueOptions() {
        DeathSavePointCatalog catalog = new DeathSavePointCatalog(resource());
        DeathSavePoint home = catalog.get(200);
        assertEquals("UNOVA_HOME", home.key());
        assertEquals("Unova_Map_391", home.sourceMap());
        assertEquals("Unova_Map_390", home.destinationMap());
        assertEquals(6, home.x());
        assertEquals(8, home.y());
        assertEquals(1, home.option1());
        assertEquals(0, home.option2());
        assertEquals(213, catalog.forSourceMap("Unova_Map_425").id());
    }

    @Test
    void legacyIdsAreReadAliasesWithoutChangingNewIdsOrAcceptingNeighboringIds() {
        DeathSavePointCatalog catalog = new DeathSavePointCatalog(resource());
        for (int legacy = 21; legacy <= 34; legacy++) {
            assertEquals(catalog.get(legacy + 179), catalog.get(legacy));
        }
        for (int invalid : new int[] {20, 35, 199, 214}) {
            assertThrows(IllegalArgumentException.class, () -> catalog.get(invalid));
        }
    }

    @Test
    void rejectsDuplicateIdsKeysAndSourceMaps() throws IOException {
        String original = Files.readString(resource());
        reject(original.replace("POINT 201", "POINT 200"));
        reject(original.replace("UNOVA_CENTER_001", "UNOVA_HOME"));
        reject(original.replace("Unova_Map_001 Unova_Map_001", "Unova_Map_391 Unova_Map_001"));
    }

    @Test
    void rejectsTruncationOverflowAndOldIdsInNewConfiguration() throws IOException {
        String original = Files.readString(resource());
        reject(original.replace("6 8 0 1 0", "6 8 0 1"));
        reject(original.replace("6 8 0 1 0", "32768 8 0 1 0"));
        reject(original.replace("6 8 0 1 0", "6 8 128 1 0"));
        reject(original.replace("POINT 200", "POINT 21"));
        reject(original.replace("Unova_Map_390", "Sinnoh_Map_390"));
        reject(original.replace("POINT 200 UNOVA_HOME Unova_Map_391 Unova_Map_390 6 8 0 1 0", ""));
    }

    @Test
    void onlyOrdinaryWildAndTrainerBattlesUseTheseCheckpoints() {
        assertTrue(DeathSaveRuntime.supports(BattleType.WildBattle));
        assertTrue(DeathSaveRuntime.supports(BattleType.GymLeaderBattle));
        assertFalse(DeathSaveRuntime.supports(BattleType.PlayerBattle));
        assertFalse(DeathSaveRuntime.supports(BattleType.CooperativeTrainerBattle));
        assertFalse(DeathSaveRuntime.supports(BattleType.BossBattle));
        assertFalse(DeathSaveRuntime.supports(null));
    }

    private void reject(String text) throws IOException {
        Path file = temporary.resolve("invalid.points");
        Files.writeString(file, text);
        assertThrows(IllegalArgumentException.class, () -> new DeathSavePointCatalog(file));
    }

    private static Path resource() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path file = directory.resolve("resource/death_save/unova.points");
            if (Files.isRegularFile(file)) return file;
            directory = directory.getParent();
        }
        throw new IllegalStateException("Missing Unova checkpoint resource");
    }
}
