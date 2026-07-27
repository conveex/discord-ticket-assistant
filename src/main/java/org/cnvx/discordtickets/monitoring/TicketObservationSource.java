package org.cnvx.discordtickets.monitoring;

import org.cnvx.discordtickets.browser.ChannelObservation;
import org.cnvx.discordtickets.model.ServerType;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface TicketObservationSource {

    CompletableFuture<List<ChannelObservation>> inspect(
            Set<ServerType> enabledServers
    );
}
