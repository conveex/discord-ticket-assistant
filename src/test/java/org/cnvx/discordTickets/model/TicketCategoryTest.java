package org.cnvx.discordTickets.model;

import org.cnvx.discordtickets.model.TicketCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TicketCategoryTest {

    @Test
    void mapsSkyblockNumbersToInternalCategories() {
        assertAll(
                () -> assertEquals(
                        TicketCategory.BASIC,
                        TicketCategory.fromSkyblockNumber(1)
                ),
                () -> assertEquals(
                        TicketCategory.HOT,
                        TicketCategory.fromSkyblockNumber(2)
                ),
                () -> assertEquals(
                        TicketCategory.BURNING,
                        TicketCategory.fromSkyblockNumber(3)
                ),
                () -> assertEquals(
                        TicketCategory.FIERY,
                        TicketCategory.fromSkyblockNumber(4)
                ),
                () -> assertEquals(
                        TicketCategory.INFERNAL,
                        TicketCategory.fromSkyblockNumber(5)
                )
        );
    }

    @Test
    void mapsKuudraPrefixesToInternalCategories() {
        assertAll(
                () -> assertEquals(
                        TicketCategory.BASIC,
                        TicketCategory.fromKuudraPrefix("basic")
                ),
                () -> assertEquals(
                        TicketCategory.HOT,
                        TicketCategory.fromKuudraPrefix("HOT")
                ),
                () -> assertEquals(
                        TicketCategory.BURNING,
                        TicketCategory.fromKuudraPrefix(" burning ")
                ),
                () -> assertEquals(
                        TicketCategory.FIERY,
                        TicketCategory.fromKuudraPrefix("fiery")
                ),
                () -> assertEquals(
                        TicketCategory.INFERNAL,
                        TicketCategory.fromKuudraPrefix("infernal")
                )
        );
    }

    @Test
    void rejectsUnknownSkyblockNumber() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TicketCategory.fromSkyblockNumber(6)
        );
    }

    @Test
    void rejectsUnknownKuudraPrefix() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TicketCategory.fromKuudraPrefix("unknown")
        );
    }
}