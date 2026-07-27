package org.cnvx.discordTickets.browser;

import org.cnvx.discordtickets.browser.ClaimConfirmationMatcher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimConfirmationMatcherTest {

    @Test
    void extractsSkyblockClaimant() {
        String message =
                "@NatalielsGoated, your ticket has been "
                        + "claimed by @convecs_.";

        assertEquals(
                "@convecs_",
                ClaimConfirmationMatcher
                        .extractClaimedBy(message)
                        .orElseThrow()
        );
    }

    @Test
    void extractsKuudraClaimant() {
        String message =
                "Your ticket was claimed by @[21] convecs_.";

        assertEquals(
                "@[21] convecs_",
                ClaimConfirmationMatcher
                        .extractClaimedBy(message)
                        .orElseThrow()
        );
    }

    @Test
    void matchesUsernameWithKuudraLevelPrefix() {
        assertTrue(
                ClaimConfirmationMatcher
                        .matchesConfiguredUser(
                                "@[21] convecs_",
                                "convecs_"
                        )
        );
    }

    @Test
    void acceptsUsernameWithAtSymbolInConfiguration() {
        assertTrue(
                ClaimConfirmationMatcher
                        .matchesConfiguredUser(
                                "@convecs_",
                                "@convecs_"
                        )
        );
    }

    @Test
    void rejectsOtherUser() {
        assertFalse(
                ClaimConfirmationMatcher
                        .matchesConfiguredUser(
                                "@hardcoreOtaku_",
                                "convecs_"
                        )
        );
    }

    @Test
    void normalizesVisibleNames() {
        assertAll(
                () -> assertEquals(
                        "convecs_",
                        ClaimConfirmationMatcher
                                .normalizeVisibleClaimedBy(
                                        "@convecs_."
                                )
                ),
                () -> assertEquals(
                        "convecs_",
                        ClaimConfirmationMatcher
                                .normalizeVisibleClaimedBy(
                                        "@[21] convecs_."
                                )
                )
        );
    }

    @Test
    void matchesMinecraftNameInSkyblockManiacs() {
        assertTrue(
                ClaimConfirmationMatcher.matchesConfiguredUser(
                        "@convecs_",
                        "convecs_"
                )
        );
    }

    @Test
    void matchesMinecraftNameWithKuudraLevel() {
        assertTrue(
                ClaimConfirmationMatcher.matchesConfiguredUser(
                        "@[22] convecs_",
                        "convecs_"
                )
        );
    }

    @Test
    void matchesMinecraftNameWithSeveralDecorations() {
        assertTrue(
                ClaimConfirmationMatcher.matchesConfiguredUser(
                        "@[22] [MVP] convecs_",
                        "convecs_"
                )
        );
    }

    @Test
    void discordUsernameDoesNotReplaceMinecraftName() {
        assertFalse(
                ClaimConfirmationMatcher.matchesConfiguredUser(
                        "@convecs_",
                        "vpmbrc"
                )
        );
    }
}
