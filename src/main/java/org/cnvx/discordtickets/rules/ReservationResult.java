package org.cnvx.discordtickets.rules;

import org.cnvx.discordtickets.model.TicketCategory;

import java.util.Objects;

public record ReservationResult(
        ReservationDecision decision,
        TicketCategory lockedCategory,
        int occupiedSlots,
        int maximumSlots
) {

    public ReservationResult {
        Objects.requireNonNull(
                decision,
                "La decisión de la reserva es obligatoria."
        );

        if (occupiedSlots < 0) {
            throw new IllegalArgumentException(
                    "La ocupación no puede ser negativa."
            );
        }

        if (maximumSlots <= 0) {
            throw new IllegalArgumentException(
                    "El máximo de espacios debe ser mayor que cero."
            );
        }

        if (occupiedSlots > maximumSlots) {
            throw new IllegalArgumentException(
                    "La ocupación no puede superar el máximo."
            );
        }
    }

    public boolean wasReserved() {
        return decision == ReservationDecision.RESERVED;
    }
}
