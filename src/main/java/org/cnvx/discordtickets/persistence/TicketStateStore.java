package org.cnvx.discordtickets.persistence;

import org.cnvx.discordtickets.model.ServerType;
import org.cnvx.discordtickets.model.TicketCandidate;
import org.cnvx.discordtickets.model.TicketCategory;
import org.cnvx.discordtickets.model.TicketId;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

public final class TicketStateStore {

    private static final String FORMAT_VERSION = "1";

    private final Path stateFile;

    public TicketStateStore(Path stateFile) {
        this.stateFile = Objects.requireNonNull(
                stateFile,
                "La ruta del archivo de estado es obligatoria."
        ).toAbsolutePath().normalize();
    }

    public static TicketStateStore defaultStore() {
        Path stateFile = Path.of(
                System.getProperty("user.home"),
                ".discord-ticket-assistant",
                "state",
                "active-tickets.properties"
        );

        return new TicketStateStore(stateFile);
    }

    public Path stateFile() {
        return stateFile;
    }

    public Optional<PersistedTicketState> load() {
        if (!Files.exists(stateFile)) {
            return Optional.empty();
        }

        Properties properties = new Properties();

        try (InputStream input =
                     Files.newInputStream(stateFile)) {

            properties.load(input);

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "No fue posible leer el estado guardado en: "
                            + stateFile,
                    exception
            );
        }

        String version = require(
                properties,
                "format.version"
        );

        if (!FORMAT_VERSION.equals(version)) {
            throw new IllegalStateException(
                    "La versión del archivo de estado no es compatible: "
                            + version
            );
        }

        String minecraftUsername =
                properties.getProperty(
                        "minecraft.username",
                        ""
                ).trim();

        Instant savedAt = parseInstant(
                require(properties, "saved.at"),
                "saved.at"
        );

        int ticketCount = parseNonNegativeInteger(
                require(properties, "ticket.count"),
                "ticket.count"
        );

        List<TicketCandidate> occupiedTickets =
                new ArrayList<>();

        for (int index = 0; index < ticketCount; index++) {
            occupiedTickets.add(
                    readTicket(properties, index)
            );
        }

        return Optional.of(
                new PersistedTicketState(
                        minecraftUsername,
                        occupiedTickets,
                        savedAt
                )
        );
    }

    public void save(PersistedTicketState state) {
        Objects.requireNonNull(
                state,
                "El estado que se guardará es obligatorio."
        );

        /*
         * Si ya no existen tickets ocupados, eliminamos el archivo.
         * Así, la ausencia del archivo significa ocupación cero.
         */
        if (state.isEmpty()) {
            delete();
            return;
        }

        Properties properties = new Properties();

        properties.setProperty(
                "format.version",
                FORMAT_VERSION
        );

        properties.setProperty(
                "saved.at",
                state.savedAt().toString()
        );

        properties.setProperty(
                "minecraft.username",
                state.minecraftUsername()
        );

        properties.setProperty(
                "ticket.count",
                Integer.toString(
                        state.occupiedTickets().size()
                )
        );

        for (int index = 0;
             index < state.occupiedTickets().size();
             index++) {

            writeTicket(
                    properties,
                    index,
                    state.occupiedTickets().get(index)
            );
        }

        Path parentDirectory = stateFile.getParent();

        try {
            Files.createDirectories(parentDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "No fue posible crear el directorio de estado: "
                            + parentDirectory,
                    exception
            );
        }

        Path temporaryFile = stateFile.resolveSibling(
                stateFile.getFileName() + ".tmp"
        );

        try {
            try (OutputStream output =
                         Files.newOutputStream(temporaryFile)) {

                properties.store(
                        output,
                        "Discord Ticket Assistant - occupied tickets"
                );
            }

            moveAtomicallyWhenPossible(
                    temporaryFile,
                    stateFile
            );

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "No fue posible guardar el estado en: "
                            + stateFile,
                    exception
            );
        } finally {
            try {
                Files.deleteIfExists(temporaryFile);
            } catch (IOException ignored) {
                // El archivo temporal se limpiará posteriormente.
            }
        }
    }

    public void delete() {
        try {
            Files.deleteIfExists(stateFile);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "No fue posible eliminar el estado guardado: "
                            + stateFile,
                    exception
            );
        }
    }

    private TicketCandidate readTicket(
            Properties properties,
            int index
    ) {
        String prefix = "ticket." + index + ".";

        String serverValue = require(
                properties,
                prefix + "server"
        );

        String categoryValue = require(
                properties,
                prefix + "category"
        );

        String channelId = require(
                properties,
                prefix + "channel.id"
        );

        String channelName = require(
                properties,
                prefix + "channel.name"
        );

        Instant detectedAt = parseInstant(
                require(
                        properties,
                        prefix + "detected.at"
                ),
                prefix + "detected.at"
        );

        ServerType server;

        try {
            server = ServerType.valueOf(serverValue);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Servidor inválido en el archivo de estado: "
                            + serverValue,
                    exception
            );
        }

        TicketCategory category;

        try {
            category =
                    TicketCategory.valueOf(categoryValue);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Categoría inválida en el archivo de estado: "
                            + categoryValue,
                    exception
            );
        }

        return new TicketCandidate(
                new TicketId(server, channelId),
                category,
                channelName,
                detectedAt
        );
    }

    private void writeTicket(
            Properties properties,
            int index,
            TicketCandidate ticket
    ) {
        String prefix = "ticket." + index + ".";

        properties.setProperty(
                prefix + "server",
                ticket.id().server().name()
        );

        properties.setProperty(
                prefix + "category",
                ticket.category().name()
        );

        properties.setProperty(
                prefix + "channel.id",
                ticket.id().channelKey()
        );

        properties.setProperty(
                prefix + "channel.name",
                ticket.channelName()
        );

        properties.setProperty(
                prefix + "detected.at",
                ticket.detectedAt().toString()
        );
    }

    private String require(
            Properties properties,
            String key
    ) {
        String value = properties.getProperty(key);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Falta la propiedad obligatoria: " + key
            );
        }

        return value.trim();
    }

    private int parseNonNegativeInteger(
            String rawValue,
            String propertyName
    ) {
        try {
            int value = Integer.parseInt(rawValue);

            if (value < 0) {
                throw new IllegalStateException(
                        "La propiedad "
                                + propertyName
                                + " no puede ser negativa."
                );
            }

            return value;

        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "La propiedad "
                            + propertyName
                            + " no contiene un entero válido.",
                    exception
            );
        }
    }

    private Instant parseInstant(
            String rawValue,
            String propertyName
    ) {
        try {
            return Instant.parse(rawValue);
        } catch (DateTimeParseException exception) {
            throw new IllegalStateException(
                    "La propiedad "
                            + propertyName
                            + " no contiene una fecha válida.",
                    exception
            );
        }
    }

    private void moveAtomicallyWhenPossible(
            Path source,
            Path target
    ) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }
}