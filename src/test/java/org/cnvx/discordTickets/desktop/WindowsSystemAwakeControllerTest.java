package org.cnvx.discordTickets.desktop;

import org.cnvx.discordtickets.desktop.WindowsSystemAwakeController;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowsSystemAwakeControllerTest {

    @Test
    void activatesAndReleasesSystemProtection() {
        List<Integer> calls = new ArrayList<>();

        WindowsSystemAwakeController controller =
                new WindowsSystemAwakeController(flags -> {
                    calls.add(flags);
                    return 1;
                });

        controller.activate();
        controller.release();

        assertAll(
                () -> assertEquals(2, calls.size()),
                () -> assertEquals(
                        WindowsSystemAwakeController.ES_CONTINUOUS
                                | WindowsSystemAwakeController
                                .ES_SYSTEM_REQUIRED,
                        calls.get(0)
                ),
                () -> assertEquals(
                        WindowsSystemAwakeController.ES_CONTINUOUS,
                        calls.get(1)
                ),
                () -> assertFalse(controller.isActive())
        );
    }

    @Test
    void repeatedActivationIsIdempotent() {
        List<Integer> calls = new ArrayList<>();

        WindowsSystemAwakeController controller =
                new WindowsSystemAwakeController(flags -> {
                    calls.add(flags);
                    return 1;
                });

        controller.activate();
        controller.activate();

        assertAll(
                () -> assertTrue(controller.isActive()),
                () -> assertEquals(1, calls.size())
        );
    }

    @Test
    void repeatedReleaseIsIdempotent() {
        List<Integer> calls = new ArrayList<>();

        WindowsSystemAwakeController controller =
                new WindowsSystemAwakeController(flags -> {
                    calls.add(flags);
                    return 1;
                });

        controller.activate();
        controller.release();
        controller.release();

        assertEquals(2, calls.size());
    }

    @Test
    void reportsNativeFailure() {
        WindowsSystemAwakeController controller =
                new WindowsSystemAwakeController(flags -> 0);

        assertThrows(
                IllegalStateException.class,
                controller::activate
        );
    }
}