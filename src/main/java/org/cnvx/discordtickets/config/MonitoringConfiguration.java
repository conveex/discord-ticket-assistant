package org.cnvx.discordtickets.config;

import org.cnvx.discordtickets.model.ServerType;
import org.cnvx.discordtickets.model.TicketCategory;

import java.util.Objects;
import java.util.Set;

public record MonitoringConfiguration(
        String minecraftUsername,
        Set<ServerType> enabledServers,
        Set<TicketCategory> enabledCategories,
        boolean includeExistingMentions
) {

    public MonitoringConfiguration {
        Objects.requireNonNull(
                minecraftUsername,
                "El usuario de Minecraft es obligatorio."
        );

        Objects.requireNonNull(
                enabledServers,
                "Los servidores habilitados son obligatorios."
        );

        Objects.requireNonNull(
                enabledCategories,
                "Las categorías habilitadas son obligatorias."
        );

        minecraftUsername = minecraftUsername.trim();

        if (minecraftUsername.isBlank()) {
            throw new IllegalArgumentException(
                    "El usuario de Minecraft no puede estar vacío."
            );
        }

        if (enabledServers.isEmpty()) {
            throw new IllegalArgumentException(
                    "Debe habilitarse al menos un servidor."
            );
        }

        if (enabledCategories.isEmpty()) {
            throw new IllegalArgumentException(
                    "Debe habilitarse al menos una categoría."
            );
        }

        enabledServers = Set.copyOf(enabledServers);
        enabledCategories = Set.copyOf(enabledCategories);
    }

    public boolean acceptsServer(ServerType server) {
        return enabledServers.contains(server);
    }

    public boolean acceptsCategory(TicketCategory category) {
        return enabledCategories.contains(category);
    }
}