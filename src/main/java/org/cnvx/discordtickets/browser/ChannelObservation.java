package org.cnvx.discordtickets.browser;

import org.cnvx.discordtickets.model.ServerType;
import org.cnvx.discordtickets.model.TicketCategory;

import java.util.Objects;

public record ChannelObservation(
        ServerType server,
        String channelId,
        String channelName,
        String ticketNumber,
        TicketCategory category,
        String url
) {

    public ChannelObservation {
        Objects.requireNonNull(server);
        Objects.requireNonNull(channelId);
        Objects.requireNonNull(channelName);
        Objects.requireNonNull(ticketNumber);
        Objects.requireNonNull(category);
        Objects.requireNonNull(url);
    }
}