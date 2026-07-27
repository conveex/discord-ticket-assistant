package org.cnvx.discordtickets.monitoring;

import org.cnvx.discordtickets.browser.ChannelObservation;
import org.cnvx.discordtickets.model.TicketId;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class TicketDiscoveryTracker {

    private final Set<TicketId> seenTicketIds =
            new LinkedHashSet<>();

    private boolean initialized;
    private boolean includeInitialBatch;

    public synchronized void beginSession(
            boolean includeInitialBatch
    ) {
        seenTicketIds.clear();

        initialized = false;
        this.includeInitialBatch = includeInitialBatch;
    }

    public synchronized List<ChannelObservation> findNew(
            List<ChannelObservation> observations
    ) {
        Objects.requireNonNull(
                observations,
                "Las observaciones son obligatorias."
        );

        /*
         * Si no se deben incluir menciones existentes, la primera
         * inspección únicamente establece la línea base.
         */
        if (!initialized) {
            initialized = true;

            if (!includeInitialBatch) {
                for (ChannelObservation observation : observations) {
                    seenTicketIds.add(toTicketId(observation));
                }

                return List.of();
            }
        }

        List<ChannelObservation> newObservations =
                new ArrayList<>();

        for (ChannelObservation observation : observations) {
            TicketId ticketId = toTicketId(observation);

            if (seenTicketIds.add(ticketId)) {
                newObservations.add(observation);
            }
        }

        return List.copyOf(newObservations);
    }

    public synchronized int seenCount() {
        return seenTicketIds.size();
    }

    private TicketId toTicketId(
            ChannelObservation observation
    ) {
        Objects.requireNonNull(
                observation,
                "La observación no puede ser null."
        );

        return new TicketId(
                observation.server(),
                observation.channelId()
        );
    }
}
