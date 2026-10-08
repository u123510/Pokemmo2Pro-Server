package org.pokemmo.gameserver.services.story;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViridianCatchStoreTest {
    @Test
    void completionUsesAnIndependentKantoStoryLineBit() {
        short initial = 0;
        short completed = ViridianCatchStore.withComplete(initial);
        assertTrue(ViridianCatchStore.isComplete(completed));
        assertEquals(1 << 4, completed);
    }

    @Test
    void completionPreservesOtherStoryLineBits() {
        short initial = (short) ((1 << 0) | (1 << 2));
        short completed = ViridianCatchStore.withComplete(initial);
        assertTrue((completed & (1 << 0)) != 0);
        assertTrue((completed & (1 << 2)) != 0);
        assertTrue(ViridianCatchStore.isComplete(completed));
    }

    @Test
    void unrelatedFlagsDoNotCompleteTutorial() {
        assertFalse(ViridianCatchStore.isComplete((short) 0));
        assertFalse(ViridianCatchStore.isComplete((short) (1 << 3)));
    }

    @Test
    void completionIsIdempotent() {
        short once = ViridianCatchStore.withComplete((short) 0);
        short twice = ViridianCatchStore.withComplete(once);
        assertEquals(once, twice);
    }
}
