package org.cnvx.discordTickets.persistence;

import org.cnvx.discordtickets.model.ServerType;
import org.cnvx.discordtickets.model.TicketCandidate;
import org.cnvx.discordtickets.model.TicketCategory;
import org.cnvx.discordtickets.model.TicketId;
import org.cnvx.discordtickets.persistence.PersistedTicketState;
import org.cnvx.discordtickets.persistence.TicketStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketStateStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void savesAndLoadsOccupiedTickets() {
        Path stateFile = temporaryDirectory.resolve(
                "state.properties"
        );

        TicketStateStore store =
                new TicketStateStore(stateFile);

        TicketCandidate skyblockTicket =
                new TicketCandidate(
                        new TicketId(
                                ServerType.SKYBLOCK_MANIACS,
                                "1531017241632571432"
                        ),
                        TicketCategory.BURNING,
                        "3👹｜9013",
                        Instant.parse(
                                "2026-07-26T18:00:00Z"
                        )
                );

        TicketCandidate kuudraTicket =
                new TicketCandidate(
                        new TicketId(
                                ServerType.KUUDRA_GANG,
                                "1531015119524466698"
                        ),
                        TicketCategory.BURNING,
                        "burning-6200",
                        Instant.parse(
                                "2026-07-26T18:01:00Z"
                        )
                );

        PersistedTicketState originalState =
                new PersistedTicketState(
                        "convecs_",
                        List.of(
                                skyblockTicket,
                                kuudraTicket
                        ),
                        Instant.parse(
                                "2026-07-26T18:05:00Z"
                        )
                );

        store.save(originalState);

        PersistedTicketState loadedState =
                store.load().orElseThrow();

        assertAll(
                () -> assertTrue(
                        Files.exists(stateFile)
                ),
                () -> assertEquals(
                        originalState.minecraftUsername(),
                        loadedState.minecraftUsername()
                ),
                () -> assertEquals(
                        originalState.savedAt(),
                        loadedState.savedAt()
                ),
                () -> assertEquals(
                        originalState.occupiedTickets(),
                        loadedState.occupiedTickets()
                )
        );
    }

    @Test
    void savingEmptyStateDeletesExistingFile() {
        Path stateFile = temporaryDirectory.resolve(
                "state.properties"
        );

        TicketStateStore store =
                new TicketStateStore(stateFile);

        TicketCandidate ticket =
                new TicketCandidate(
                        new TicketId(
                                ServerType.KUUDRA_GANG,
                                "1001"
                        ),
                        TicketCategory.HOT,
                        "hot-6184",
                        Instant.parse(
                                "2026-07-26T18:00:00Z"
                        )
                );

        store.save(
                new PersistedTicketState(
                        "convecs_",
                        List.of(ticket),
                        Instant.now()
                )
        );

        assertTrue(Files.exists(stateFile));

        store.save(
                new PersistedTicketState(
                        "",
                        List.of(),
                        Instant.now()
                )
        );

        assertFalse(Files.exists(stateFile));
    }

    @Test
    void returnsEmptyWhenFileDoesNotExist() {
        TicketStateStore store =
                new TicketStateStore(
                        temporaryDirectory.resolve(
                                "missing.properties"
                        )
                );

        assertTrue(store.load().isEmpty());
    }
}