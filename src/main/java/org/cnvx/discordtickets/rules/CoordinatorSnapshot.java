package org.cnvx.discordtickets.rules;

import org.cnvx.discordtickets.model.TicketCandidate;
import org.cnvx.discordtickets.model.TicketCategory;

import java.util.List;
import java.util.Objects;

public record CoordinatorSnapshot(
        boolean paused,
        int maximumSlots,
        TicketCategory lockedCategory,
        List<TicketCandidate> reservedTickets,
        List<TicketCandidate> activeTickets,
        int terminalTicketCount
) {

    public CoordinatorSnapshot {
        if (maximumSlots <= 0) {
            throw new IllegalArgumentException(
                    "El máximo de espacios debe ser mayor que cero."
            );
        }

        Objects.requireNonNull(
                reservedTickets,
                "La lista de reservados es obligatoria."
        );

        Objects.requireNonNull(
                activeTickets,
                "La lista de activos es obligatoria."
        );

        if (terminalTicketCount < 0) {
            throw new IllegalArgumentException(
                    "La cantidad de tickets terminados no puede ser negativa."
            );
        }

        reservedTickets = List.copyOf(reservedTickets);
        activeTickets = List.copyOf(activeTickets);
    }

    public int occupiedSlots() {
        return reservedTickets.size() + activeTickets.size();
    }

    public int availableSlots() {
        return maximumSlots - occupiedSlots();
    }
}