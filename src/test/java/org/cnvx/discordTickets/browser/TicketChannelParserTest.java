package org.cnvx.discordTickets.browser;

import org.cnvx.discordtickets.browser.ParsedTicketChannel;
import org.cnvx.discordtickets.browser.TicketChannelParser;
import org.cnvx.discordtickets.model.ServerType;
import org.cnvx.discordtickets.model.TicketCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketChannelParserTest {

    @Test
    void parsesSkyblockAvailableChannel() {
        ParsedTicketChannel parsed =
                TicketChannelParser.parse(
                        ServerType.SKYBLOCK_MANIACS,
                        "1 👹 | 10256"
                ).orElseThrow();

        assertAll(
                () -> assertEquals(
                        TicketCategory.BASIC,
                        parsed.category()
                ),
                () -> assertEquals(
                        "10256",
                        parsed.ticketNumber()
                )
        );
    }

    @Test
    void parsesSkyblockClaimedChannel() {
        ParsedTicketChannel parsed =
                TicketChannelParser.parse(
                        ServerType.SKYBLOCK_MANIACS,
                        "✅-1👹｜10272"
                ).orElseThrow();

        assertAll(
                () -> assertEquals(
                        TicketCategory.BASIC,
                        parsed.category()
                ),
                () -> assertEquals(
                        "10272",
                        parsed.ticketNumber()
                )
        );
    }

    @Test
    void parsesKuudraCategories() {
        assertAll(
                () -> assertEquals(
                        TicketCategory.BASIC,
                        TicketChannelParser.parse(
                                ServerType.KUUDRA_GANG,
                                "basic-6643"
                        ).orElseThrow().category()
                ),
                () -> assertEquals(
                        TicketCategory.HOT,
                        TicketChannelParser.parse(
                                ServerType.KUUDRA_GANG,
                                "hot-6141"
                        ).orElseThrow().category()
                ),
                () -> assertEquals(
                        TicketCategory.BURNING,
                        TicketChannelParser.parse(
                                ServerType.KUUDRA_GANG,
                                "burning-8100"
                        ).orElseThrow().category()
                ),
                () -> assertEquals(
                        TicketCategory.FIERY,
                        TicketChannelParser.parse(
                                ServerType.KUUDRA_GANG,
                                "fiery-9100"
                        ).orElseThrow().category()
                ),
                () -> assertEquals(
                        TicketCategory.INFERNAL,
                        TicketChannelParser.parse(
                                ServerType.KUUDRA_GANG,
                                "infernal-10100"
                        ).orElseThrow().category()
                )
        );
    }

    @Test
    void ignoresUnrelatedChannels() {
        assertTrue(
                TicketChannelParser.parse(
                        ServerType.KUUDRA_GANG,
                        "reviews"
                ).isEmpty()
        );

        assertTrue(
                TicketChannelParser.parse(
                        ServerType.SKYBLOCK_MANIACS,
                        "mm-chat"
                ).isEmpty()
        );
    }

    @Test
    void handlesBadgeTextAfterChannelName() {
        ParsedTicketChannel parsed =
                TicketChannelParser.parse(
                        ServerType.KUUDRA_GANG,
                        "hot-6141\n1"
                ).orElseThrow();

        assertEquals(
                TicketCategory.HOT,
                parsed.category()
        );
    }
}