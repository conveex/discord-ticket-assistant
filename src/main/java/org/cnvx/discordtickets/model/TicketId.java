package org.cnvx.discordtickets.model;

import java.util.Objects;

public record TicketId(
        ServerType server,
        String channelKey
) {
    public TicketId {
        Objects.requireNonNull(
                server,
                "El servidor del ticket es obligatorio."
        );

        Objects.requireNonNull(
                channelKey,
                "La clave del canal es obligatoria."
        );

        channelKey = channelKey.trim();

        if (channelKey.isBlank()) {
            throw new IllegalArgumentException(
                    "La clave del canal no puede estar vacía."
            );
        }
    }

    @Override
    public String toString() {
        return server.name() + ":" + channelKey;
    }
}
