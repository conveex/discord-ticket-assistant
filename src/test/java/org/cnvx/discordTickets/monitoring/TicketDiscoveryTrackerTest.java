package org.cnvx.discordTickets.monitoring;

import org.cnvx.discordtickets.browser.ChannelObservation;
import org.cnvx.discordtickets.model.ServerType;
import org.cnvx.discordtickets.model.TicketCategory;
import org.cnvx.discordtickets.monitoring.TicketDiscoveryTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketDiscoveryTrackerTest {

    private TicketDiscoveryTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new TicketDiscoveryTracker();
    }

    @Test
    void ignoresInitialObservationsWhenConfigured() {
        tracker.beginSession(false);

        List<ChannelObservation> firstBatch =
                tracker.findNew(
                        List.of(observation("1001"))
                );

        List<ChannelObservation> secondBatch =
                tracker.findNew(
                        List.of(
                                observation("1001"),
                                observation("1002")
                        )
                );

        assertAll(
                () -> assertTrue(firstBatch.isEmpty()),
                () -> assertEquals(
                        1,
                        secondBatch.size()
                ),
                () -> assertEquals(
                        "1002",
                        secondBatch.getFirst().channelId()
                )
        );
    }

    @Test
    void includesInitialObservationsWhenConfigured() {
        tracker.beginSession(true);

        List<ChannelObservation> firstBatch =
                tracker.findNew(
                        List.of(
                                observation("1001"),
                                observation("1002")
                        )
                );

        assertEquals(2, firstBatch.size());
    }

    @Test
    void doesNotReturnSameTicketTwice() {
        tracker.beginSession(true);

        ChannelObservation ticket =
                observation("1001");

        List<ChannelObservation> first =
                tracker.findNew(List.of(ticket));

        List<ChannelObservation> repeated =
                tracker.findNew(List.of(ticket));

        assertAll(
                () -> assertEquals(1, first.size()),
                () -> assertTrue(repeated.isEmpty()),
                () -> assertEquals(1, tracker.seenCount())
        );
    }

    @Test
    void beginningNewSessionClearsPreviousTickets() {
        tracker.beginSession(true);

        ChannelObservation ticket =
                observation("1001");

        tracker.findNew(List.of(ticket));

        tracker.beginSession(true);

        List<ChannelObservation> afterRestart =
                tracker.findNew(List.of(ticket));

        assertEquals(1, afterRestart.size());
    }

    private static ChannelObservation observation(
            String channelId
    ) {
        return new ChannelObservation(
                ServerType.SKYBLOCK_MANIACS,
                channelId,
                "2👹｜8587",
                "8587",
                TicketCategory.HOT,
                "https://discord.com/channels/server/"
                        + channelId
        );
    }
}
