package org.cnvx.discordTickets.util;

import org.cnvx.discordtickets.util.UsernameNormalizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UsernameNormalizerTest {

    @Test
    void removesAtSymbol() {
        assertEquals(
                "convecs_",
                UsernameNormalizer.normalize("@convecs_")
        );
    }

    @Test
    void removesWhitespace() {
        assertEquals(
                "convecs_",
                UsernameNormalizer.normalize("   convecs_   ")
        );
    }

    @Test
    void removesAtSymbolAndWhitespace() {
        assertEquals(
                "convecs_",
                UsernameNormalizer.normalize("   @convecs_   ")
        );
    }

    @Test
    void convertsNullToEmptyString() {
        assertEquals(
                "",
                UsernameNormalizer.normalize(null)
        );
    }
}