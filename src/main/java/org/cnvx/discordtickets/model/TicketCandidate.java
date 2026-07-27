package org.cnvx.discordtickets.model;

import java.time.Instant;
import java.util.Objects;

public record TicketCandidate(
        TicketId id,
        TicketCategory category,
        String channelName,
        Instant detectedAt
) {

    public TicketCandidate {
        Objects.requireNonNull(
                id,
                "El identificador del ticket es obligatorio."
        );

        Objects.requireNonNull(
                category,
                "La categoría del ticket es obligatoria."
        );

        Objects.requireNonNull(
                channelName,
                "El nombre del canal es obligatorio."
        );

        Objects.requireNonNull(
                detectedAt,
                "La fecha de detección es obligatoria."
        );

        channelName = channelName.trim();

        if (channelName.isBlank()) {
            throw new IllegalArgumentException(
                    "El nombre del canal no puede estar vacío."
            );
        }
    }
}