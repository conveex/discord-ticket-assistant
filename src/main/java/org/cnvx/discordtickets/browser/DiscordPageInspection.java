package org.cnvx.discordtickets.browser;

import java.util.List;
import java.util.Objects;

public record DiscordPageInspection(
        List<ChannelObservation> observations,
        DiscordPageHealth health
) {

    public DiscordPageInspection {
        Objects.requireNonNull(
                observations,
                "Las observaciones son obligatorias."
        );

        Objects.requireNonNull(
                health,
                "El diagnóstico de la página es obligatorio."
        );

        observations = List.copyOf(observations);
    }
}