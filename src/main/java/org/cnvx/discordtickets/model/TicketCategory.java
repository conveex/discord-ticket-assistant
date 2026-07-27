package org.cnvx.discordtickets.model;

import java.util.Locale;

public enum TicketCategory {

    BASIC(1, "basic", "Basic"),
    HOT(2, "hot", "Hot"),
    BURNING(3, "burning", "Burning"),
    FIERY(4, "fiery", "Fiery"),
    INFERNAL(5, "infernal", "Infernal");

    private final int skyblockNumber;
    private final String kuudraPrefix;
    private final String displayName;

    TicketCategory(int skyblockNumber, String kuudraPrefix, String displayName) {
        this.skyblockNumber = skyblockNumber;
        this.kuudraPrefix = kuudraPrefix;
        this.displayName = displayName;
    }

    public int skyblockNumber() {
        return skyblockNumber;
    }

    public String kuudraPrefix() {
        return kuudraPrefix;
    }

    public String displayName() {
        return displayName;
    }

    public static TicketCategory fromSkyblockNumber(int number) {
        for (TicketCategory category : values()) {
            if (category.skyblockNumber == number) {
                return category;
            }
        }
        throw new IllegalArgumentException(
                "No existe una categoría de Skyblock Maniacs para el número: " +  number
        );
    }

    public static TicketCategory fromKuudraPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException(
                    "El prefijo de Kuudra no puede estar vacío."
            );
        }

        String normalizedPrefix = prefix
                .trim()
                .toLowerCase(Locale.ROOT);

        for (TicketCategory category : values()) {
            if (category.kuudraPrefix().equals(normalizedPrefix)) {
                return category;
            }
        }

        throw new IllegalArgumentException(
                "No existe una categoría de Kuudra Gang para el prefijo: " + prefix
        );
    }


}
