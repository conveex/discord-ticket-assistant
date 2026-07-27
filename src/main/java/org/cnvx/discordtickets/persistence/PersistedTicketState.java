package org.cnvx.discordtickets.persistence;

import org.cnvx.discordtickets.model.TicketCandidate;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record PersistedTicketState(
        String minecraftUsername,
        List<TicketCandidate> occupiedTickets,
        Instant savedAt
) {

    public PersistedTicketState {
        Objects.requireNonNull(
                minecraftUsername,
                "El nombre de Minecraft es obligatorio."
        );

        Objects.requireNonNull(
                occupiedTickets,
                "La lista de tickets ocupados es obligatoria."
        );

        Objects.requireNonNull(
                savedAt,
                "La fecha de guardado es obligatoria."
        );

        minecraftUsername = minecraftUsername.trim();
        occupiedTickets = List.copyOf(occupiedTickets);

        if (!occupiedTickets.isEmpty()
                && minecraftUsername.isBlank()) {
            throw new IllegalArgumentException(
                    "No puede persistirse un estado ocupado "
                            + "sin nombre de Minecraft."
            );
        }

        Set<Object> uniqueIds = new HashSet<>();

        for (TicketCandidate ticket : occupiedTickets) {
            Objects.requireNonNull(
                    ticket,
                    "La lista no puede contener tickets nulos."
            );

            if (!uniqueIds.add(ticket.id())) {
                throw new IllegalArgumentException(
                        "El estado contiene un ticket duplicado: "
                                + ticket.id()
                );
            }
        }
    }

    public boolean isEmpty() {
        return occupiedTickets.isEmpty();
    }
}
