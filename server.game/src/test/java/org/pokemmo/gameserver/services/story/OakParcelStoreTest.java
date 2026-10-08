package org.pokemmo.gameserver.services.story;

import org.junit.jupiter.api.Test;
import org.pokemmo.gameserver.game.story.OakParcelProgress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OakParcelStoreTest {
    @Test
    void noParcelActionBeforeOpeningCompletion() {
        for (OakParcelStore.Action action : OakParcelStore.Action.values()) {
            assertThrows(IllegalStateException.class, () -> OakParcelStore.shouldApply(progress(3, 3), action, true));
        }
    }

    @Test
    void initialPickupCanAdoptAnExistingParcelWithoutMintingAnother() {
        assertTrue(OakParcelStore.shouldApply(progress(4, 3), OakParcelStore.Action.PICKUP, false));
        assertTrue(OakParcelStore.shouldApply(progress(4, 0), OakParcelStore.Action.PICKUP, true));
        assertFalse(OakParcelStore.shouldApply(progress(4, 1), OakParcelStore.Action.PICKUP, true));
    }

    @Test
    void onlyMissingUndeliveredParcelCanBeReissued() {
        assertTrue(OakParcelStore.shouldApply(progress(4, 1), OakParcelStore.Action.PICKUP, false));
        assertFalse(OakParcelStore.shouldApply(progress(5, 2), OakParcelStore.Action.PICKUP, false));
        assertFalse(OakParcelStore.shouldApply(progress(6, 2), OakParcelStore.Action.PICKUP, false));
    }

    @Test
    void deliveryRequiresTheClaimCheckpointAndActualParcel() {
        assertThrows(IllegalStateException.class, () -> OakParcelStore.shouldApply(progress(4, 3), OakParcelStore.Action.DELIVER, true));
        assertThrows(IllegalStateException.class, () -> OakParcelStore.shouldApply(progress(4, 1), OakParcelStore.Action.DELIVER, false));
        assertTrue(OakParcelStore.shouldApply(progress(4, 1), OakParcelStore.Action.DELIVER, true));
        assertFalse(OakParcelStore.shouldApply(progress(5, 2), OakParcelStore.Action.DELIVER, true));
    }

    @Test
    void fiveBallRewardRequiresDeliveryAndAcceptsLegacyDeliveredCheckpoint() {
        assertThrows(IllegalStateException.class, () -> OakParcelStore.shouldApply(progress(4, 1), OakParcelStore.Action.RECEIVE_POKEDEX, true));
        assertTrue(OakParcelStore.shouldApply(progress(5, 2), OakParcelStore.Action.RECEIVE_POKEDEX, false));
        assertTrue(OakParcelStore.shouldApply(progress(5, 3), OakParcelStore.Action.RECEIVE_POKEDEX, false));
    }

    @Test
    void completedAndLaterLegacyStagesNeverAwardAgain() {
        for (int stage = 6; stage <= 9; stage++) {
            for (OakParcelStore.Action action : OakParcelStore.Action.values()) {
                assertFalse(OakParcelStore.shouldApply(progress(stage, 3), action, false));
                assertFalse(OakParcelStore.shouldApply(progress(stage, 2), action, true));
            }
        }
    }

    private static OakParcelProgress progress(int stage, int parcel) {
        return new OakParcelProgress((short) stage, (short) parcel);
    }
}
