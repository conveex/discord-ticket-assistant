package org.cnvx.discordTickets.rules;

import org.cnvx.discordtickets.model.ServerType;
import org.cnvx.discordtickets.model.TicketCandidate;
import org.cnvx.discordtickets.model.TicketCategory;
import org.cnvx.discordtickets.model.TicketId;
import org.cnvx.discordtickets.rules.CoordinatorSnapshot;
import org.cnvx.discordtickets.rules.ReservationDecision;
import org.cnvx.discordtickets.rules.ReservationResult;
import org.cnvx.discordtickets.rules.TicketCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TicketCoordinatorTest {

    private TicketCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new TicketCoordinator();
        coordinator.resume();
    }

    @Test
    void firstReservationLocksItsCategory() {
        TicketCandidate ticket = ticket(
                ServerType.SKYBLOCK_MANIACS,
                "1-basic-10256",
                TicketCategory.BASIC
        );

        ReservationResult result = coordinator.reserve(ticket);
        CoordinatorSnapshot snapshot = coordinator.snapshot();

        assertAll(
                () -> assertEquals(
                        ReservationDecision.RESERVED,
                        result.decision()
                ),
                () -> assertEquals(
                        TicketCategory.BASIC,
                        snapshot.lockedCategory()
                ),
                () -> assertEquals(
                        1,
                        snapshot.occupiedSlots()
                ),
                () -> assertEquals(
                        1,
                        snapshot.reservedTickets().size()
                ),
                () -> assertTrue(
                        snapshot.activeTickets().isEmpty()
                )
        );
    }

    @Test
    void acceptsThreeTicketsOfTheSameCategory() {
        ReservationResult first = coordinator.reserve(
                ticket(
                        ServerType.SKYBLOCK_MANIACS,
                        "1-basic-1001",
                        TicketCategory.BASIC
                )
        );

        ReservationResult second = coordinator.reserve(
                ticket(
                        ServerType.KUUDRA_GANG,
                        "basic-1002",
                        TicketCategory.BASIC
                )
        );

        ReservationResult third = coordinator.reserve(
                ticket(
                        ServerType.SKYBLOCK_MANIACS,
                        "1-basic-1003",
                        TicketCategory.BASIC
                )
        );

        assertAll(
                () -> assertTrue(first.wasReserved()),
                () -> assertTrue(second.wasReserved()),
                () -> assertTrue(third.wasReserved()),
                () -> assertEquals(
                        3,
                        coordinator.snapshot().occupiedSlots()
                ),
                () -> assertEquals(
                        0,
                        coordinator.snapshot().availableSlots()
                )
        );
    }

    @Test
    void rejectsFourthTicketEvenWhenCategoryMatches() {
        coordinator.reserve(ticket(
                ServerType.SKYBLOCK_MANIACS,
                "1-basic-1001",
                TicketCategory.BASIC
        ));

        coordinator.reserve(ticket(
                ServerType.KUUDRA_GANG,
                "basic-1002",
                TicketCategory.BASIC
        ));

        coordinator.reserve(ticket(
                ServerType.SKYBLOCK_MANIACS,
                "1-basic-1003",
                TicketCategory.BASIC
        ));

        ReservationResult fourth = coordinator.reserve(
                ticket(
                        ServerType.KUUDRA_GANG,
                        "basic-1004",
                        TicketCategory.BASIC
                )
        );

        assertAll(
                () -> assertEquals(
                        ReservationDecision.LIMIT_REACHED,
                        fourth.decision()
                ),
                () -> assertEquals(
                        3,
                        coordinator.snapshot().occupiedSlots()
                )
        );
    }

    @Test
    void rejectsDifferentCategoryWhileOneIsLocked() {
        coordinator.reserve(ticket(
                ServerType.SKYBLOCK_MANIACS,
                "1-basic-1001",
                TicketCategory.BASIC
        ));

        ReservationResult hotTicket = coordinator.reserve(
                ticket(
                        ServerType.KUUDRA_GANG,
                        "hot-2001",
                        TicketCategory.HOT
                )
        );

        assertAll(
                () -> assertEquals(
                        ReservationDecision.CATEGORY_MISMATCH,
                        hotTicket.decision()
                ),
                () -> assertEquals(
                        TicketCategory.BASIC,
                        coordinator.snapshot().lockedCategory()
                ),
                () -> assertEquals(
                        1,
                        coordinator.snapshot().occupiedSlots()
                )
        );
    }

    @Test
    void confirmationMovesTicketFromReservedToActive() {
        TicketCandidate ticket = ticket(
                ServerType.KUUDRA_GANG,
                "basic-6643",
                TicketCategory.BASIC
        );

        coordinator.reserve(ticket);

        boolean confirmed = coordinator.confirmReservation(ticket.id());
        CoordinatorSnapshot snapshot = coordinator.snapshot();

        assertAll(
                () -> assertTrue(confirmed),
                () -> assertTrue(
                        snapshot.reservedTickets().isEmpty()
                ),
                () -> assertEquals(
                        1,
                        snapshot.activeTickets().size()
                ),
                () -> assertEquals(
                        1,
                        snapshot.occupiedSlots()
                ),
                () -> assertEquals(
                        TicketCategory.BASIC,
                        snapshot.lockedCategory()
                )
        );
    }

    @Test
    void cancellingLastReservationUnlocksCategory() {
        TicketCandidate basicTicket = ticket(
                ServerType.KUUDRA_GANG,
                "basic-6643",
                TicketCategory.BASIC
        );

        coordinator.reserve(basicTicket);

        boolean cancelled = coordinator.cancelReservation(
                basicTicket.id()
        );

        CoordinatorSnapshot emptySnapshot = coordinator.snapshot();

        ReservationResult hotReservation = coordinator.reserve(
                ticket(
                        ServerType.KUUDRA_GANG,
                        "hot-7721",
                        TicketCategory.HOT
                )
        );

        assertAll(
                () -> assertTrue(cancelled),
                () -> assertEquals(
                        0,
                        emptySnapshot.occupiedSlots()
                ),
                () -> assertNull(
                        emptySnapshot.lockedCategory()
                ),
                () -> assertEquals(
                        ReservationDecision.RESERVED,
                        hotReservation.decision()
                ),
                () -> assertEquals(
                        TicketCategory.HOT,
                        coordinator.snapshot().lockedCategory()
                )
        );
    }

    @Test
    void completingLastActiveTicketUnlocksCategory() {
        TicketCandidate ticket = ticket(
                ServerType.SKYBLOCK_MANIACS,
                "1-basic-10256",
                TicketCategory.BASIC
        );

        coordinator.reserve(ticket);
        coordinator.confirmReservation(ticket.id());

        boolean completed = coordinator.completeActiveTicket(
                ticket.id()
        );

        CoordinatorSnapshot snapshot = coordinator.snapshot();

        assertAll(
                () -> assertTrue(completed),
                () -> assertEquals(
                        0,
                        snapshot.occupiedSlots()
                ),
                () -> assertNull(
                        snapshot.lockedCategory()
                ),
                () -> assertEquals(
                        1,
                        snapshot.terminalTicketCount()
                )
        );
    }

    @Test
    void pauseRejectsReservationsAndResumeAllowsThem() {
        coordinator.pause();

        TicketCandidate ticket = ticket(
                ServerType.KUUDRA_GANG,
                "burning-4432",
                TicketCategory.BURNING
        );

        ReservationResult whilePaused = coordinator.reserve(ticket);

        coordinator.resume();

        ReservationResult afterResume = coordinator.reserve(ticket);

        assertAll(
                () -> assertEquals(
                        ReservationDecision.PAUSED,
                        whilePaused.decision()
                ),
                () -> assertEquals(
                        ReservationDecision.RESERVED,
                        afterResume.decision()
                ),
                () -> assertFalse(
                        coordinator.snapshot().paused()
                )
        );
    }

    @Test
    void duplicateTicketCannotBeReservedTwice() {
        TicketCandidate ticket = ticket(
                ServerType.SKYBLOCK_MANIACS,
                "2-hot-2022",
                TicketCategory.HOT
        );

        ReservationResult first = coordinator.reserve(ticket);
        ReservationResult duplicateWhileReserved =
                coordinator.reserve(ticket);

        coordinator.confirmReservation(ticket.id());

        ReservationResult duplicateWhileActive =
                coordinator.reserve(ticket);

        coordinator.completeActiveTicket(ticket.id());

        ReservationResult duplicateAfterCompletion =
                coordinator.reserve(ticket);

        assertAll(
                () -> assertEquals(
                        ReservationDecision.RESERVED,
                        first.decision()
                ),
                () -> assertEquals(
                        ReservationDecision.DUPLICATE,
                        duplicateWhileReserved.decision()
                ),
                () -> assertEquals(
                        ReservationDecision.DUPLICATE,
                        duplicateWhileActive.decision()
                ),
                () -> assertEquals(
                        ReservationDecision.DUPLICATE,
                        duplicateAfterCompletion.decision()
                )
        );
    }

    @Test
    void ticketClaimedByOtherFreesSpaceButBecomesTerminal() {
        TicketCandidate ticket = ticket(
                ServerType.KUUDRA_GANG,
                "fiery-8877",
                TicketCategory.FIERY
        );

        coordinator.reserve(ticket);

        boolean marked = coordinator.markClaimedByOther(
                ticket.id()
        );

        ReservationResult repeated = coordinator.reserve(ticket);
        CoordinatorSnapshot snapshot = coordinator.snapshot();

        assertAll(
                () -> assertTrue(marked),
                () -> assertEquals(
                        0,
                        snapshot.occupiedSlots()
                ),
                () -> assertNull(
                        snapshot.lockedCategory()
                ),
                () -> assertEquals(
                        1,
                        snapshot.terminalTicketCount()
                ),
                () -> assertEquals(
                        ReservationDecision.DUPLICATE,
                        repeated.decision()
                )
        );
    }

    @Test
    void concurrentReservationsNeverExceedMaximum() throws Exception {
        int attempts = 12;

        ExecutorService executor =
                Executors.newFixedThreadPool(attempts);

        CountDownLatch readyLatch =
                new CountDownLatch(attempts);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        List<Future<ReservationResult>> futures =
                new ArrayList<>();

        try {
            for (int index = 0; index < attempts; index++) {
                int ticketNumber = index;

                futures.add(executor.submit(() -> {
                    readyLatch.countDown();

                    boolean started = startLatch.await(
                            3,
                            TimeUnit.SECONDS
                    );

                    if (!started) {
                        throw new IllegalStateException(
                                "La prueba concurrente no pudo comenzar."
                        );
                    }

                    return coordinator.reserve(
                            ticket(
                                    ServerType.SKYBLOCK_MANIACS,
                                    "1-basic-" + ticketNumber,
                                    TicketCategory.BASIC
                            )
                    );
                }));
            }

            assertTrue(
                    readyLatch.await(3, TimeUnit.SECONDS),
                    "No todos los hilos estuvieron listos."
            );

            startLatch.countDown();

            long successfulReservations = 0;

            for (Future<ReservationResult> future : futures) {
                ReservationResult result = future.get(
                        3,
                        TimeUnit.SECONDS
                );

                if (result.wasReserved()) {
                    successfulReservations++;
                }
            }

            CoordinatorSnapshot snapshot = coordinator.snapshot();

            long finalSuccessfulReservations = successfulReservations;
            assertAll(
                    () -> assertEquals(
                            3,
                            finalSuccessfulReservations
                    ),
                    () -> assertEquals(
                            3,
                            snapshot.occupiedSlots()
                    ),
                    () -> assertEquals(
                            0,
                            snapshot.availableSlots()
                    ),
                    () -> assertEquals(
                            TicketCategory.BASIC,
                            snapshot.lockedCategory()
                    )
            );
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void completingOneOfSeveralTicketsKeepsCategoryLocked() {
        TicketCandidate first = ticket(
                ServerType.SKYBLOCK_MANIACS,
                "1-basic-1001",
                TicketCategory.BASIC
        );

        TicketCandidate second = ticket(
                ServerType.KUUDRA_GANG,
                "basic-1002",
                TicketCategory.BASIC
        );

        coordinator.reserve(first);
        coordinator.confirmReservation(first.id());

        coordinator.reserve(second);
        coordinator.confirmReservation(second.id());

        boolean completed = coordinator.completeActiveTicket(
                first.id()
        );

        CoordinatorSnapshot snapshot = coordinator.snapshot();

        assertAll(
                () -> assertTrue(completed),
                () -> assertEquals(
                        1,
                        snapshot.occupiedSlots()
                ),
                () -> assertEquals(
                        TicketCategory.BASIC,
                        snapshot.lockedCategory()
                ),
                () -> assertEquals(
                        1,
                        snapshot.activeTickets().size()
                )
        );
    }

    @Test
    void cancellingOneReservationKeepsCategoryLockedWhenAnotherExists() {
        TicketCandidate first = ticket(
                ServerType.SKYBLOCK_MANIACS,
                "3-burning-3001",
                TicketCategory.BURNING
        );

        TicketCandidate second = ticket(
                ServerType.KUUDRA_GANG,
                "burning-3002",
                TicketCategory.BURNING
        );

        coordinator.reserve(first);
        coordinator.reserve(second);

        boolean cancelled = coordinator.cancelReservation(
                first.id()
        );

        CoordinatorSnapshot snapshot = coordinator.snapshot();

        assertAll(
                () -> assertTrue(cancelled),
                () -> assertEquals(
                        1,
                        snapshot.occupiedSlots()
                ),
                () -> assertEquals(
                        TicketCategory.BURNING,
                        snapshot.lockedCategory()
                ),
                () -> assertEquals(
                        1,
                        snapshot.reservedTickets().size()
                )
        );
    }

    @Test
    void restoresOccupiedTicketsAsActive() {
        TicketCoordinator restoredCoordinator =
                new TicketCoordinator();

        TicketCandidate first = ticket(
                ServerType.SKYBLOCK_MANIACS,
                "1531017241632571432",
                TicketCategory.BURNING
        );

        TicketCandidate second = ticket(
                ServerType.KUUDRA_GANG,
                "1531018000000000000",
                TicketCategory.BURNING
        );

        restoredCoordinator.restoreOccupiedTicketsAsActive(
                List.of(first, second)
        );

        CoordinatorSnapshot snapshot =
                restoredCoordinator.snapshot();

        assertAll(
                () -> assertTrue(snapshot.paused()),
                () -> assertEquals(
                        2,
                        snapshot.occupiedSlots()
                ),
                () -> assertEquals(
                        2,
                        snapshot.activeTickets().size()
                ),
                () -> assertTrue(
                        snapshot.reservedTickets().isEmpty()
                ),
                () -> assertEquals(
                        TicketCategory.BURNING,
                        snapshot.lockedCategory()
                )
        );
    }

    @Test
    void rejectsRestoredTicketsFromDifferentCategories() {
        TicketCoordinator restoredCoordinator =
                new TicketCoordinator();

        TicketCandidate basic = ticket(
                ServerType.SKYBLOCK_MANIACS,
                "1001",
                TicketCategory.BASIC
        );

        TicketCandidate hot = ticket(
                ServerType.KUUDRA_GANG,
                "1002",
                TicketCategory.HOT
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> restoredCoordinator
                        .restoreOccupiedTicketsAsActive(
                                List.of(basic, hot)
                        )
        );
    }

    private static TicketCandidate ticket(
            ServerType server,
            String channelKey,
            TicketCategory category
    ) {
        return new TicketCandidate(
                new TicketId(server, channelKey),
                category,
                channelKey,
                Instant.parse("2026-07-19T12:00:00Z")
        );
    }
}