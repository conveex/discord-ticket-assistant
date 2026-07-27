package org.cnvx.discordtickets.monitoring;

import org.cnvx.discordtickets.browser.ChannelObservation;
import org.cnvx.discordtickets.model.ServerType;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class DiscordMonitoringLoop implements AutoCloseable {

    private final TicketObservationSource observationSource;
    private final ScheduledExecutorService scheduler;

    private final AtomicBoolean active =
            new AtomicBoolean(false);

    private final AtomicBoolean inspectionInFlight =
            new AtomicBoolean(false);

    private final AtomicBoolean closed =
            new AtomicBoolean(false);

    private volatile Set<ServerType> enabledServers =
            Set.of();

    private volatile Consumer<List<ChannelObservation>>
            observationHandler = observations -> {
    };

    private volatile Consumer<Throwable> errorHandler =
            error -> {
            };

    public DiscordMonitoringLoop(
            TicketObservationSource observationSource,
            Duration interval
    ) {
        this.observationSource = Objects.requireNonNull(
                observationSource,
                "La fuente de observaciones es obligatoria."
        );

        Objects.requireNonNull(
                interval,
                "El intervalo de inspección es obligatorio."
        );

        long intervalMillis = interval.toMillis();

        if (intervalMillis < 250) {
            throw new IllegalArgumentException(
                    "El intervalo de inspección debe ser "
                            + "de al menos 250 milisegundos."
            );
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(
                task -> {
                    Thread thread = new Thread(
                            task,
                            "discord-monitoring-scheduler"
                    );

                    thread.setDaemon(true);
                    return thread;
                }
        );

        scheduler.scheduleWithFixedDelay(
                this::tick,
                0,
                intervalMillis,
                TimeUnit.MILLISECONDS
        );
    }

    public void start(
            Set<ServerType> enabledServers,
            Consumer<List<ChannelObservation>> observationHandler,
            Consumer<Throwable> errorHandler
    ) {
        Objects.requireNonNull(
                enabledServers,
                "Los servidores habilitados son obligatorios."
        );

        Objects.requireNonNull(
                observationHandler,
                "El manejador de observaciones es obligatorio."
        );

        Objects.requireNonNull(
                errorHandler,
                "El manejador de errores es obligatorio."
        );

        if (enabledServers.isEmpty()) {
            throw new IllegalArgumentException(
                    "Debe vigilarse al menos un servidor."
            );
        }

        ensureOpen();

        this.enabledServers = Set.copyOf(enabledServers);
        this.observationHandler = observationHandler;
        this.errorHandler = errorHandler;

        active.set(true);
    }

    public void pause() {
        active.set(false);
    }

    public void resume() {
        ensureOpen();

        if (enabledServers.isEmpty()) {
            throw new IllegalStateException(
                    "El monitor todavía no ha sido configurado."
            );
        }

        active.set(true);
    }

    public boolean isActive() {
        return active.get();
    }

    private void tick() {
        if (closed.get() || !active.get()) {
            return;
        }

        /*
         * La inspección es asíncrona. Aunque el temporizador vuelva
         * a ejecutar tick(), no permitimos acumular inspecciones.
         */
        if (!inspectionInFlight.compareAndSet(false, true)) {
            return;
        }

        Set<ServerType> serversSnapshot =
                enabledServers;

        try {
            CompletableFuture<List<ChannelObservation>> inspection =
                    observationSource.inspect(serversSnapshot);

            if (inspection == null) {
                throw new IllegalStateException(
                        "La fuente de observaciones devolvió null."
                );
            }

            inspection.whenComplete(
                    (observations, error) -> {
                        try {
                            if (closed.get() || !active.get()) {
                                return;
                            }

                            if (error != null) {
                                notifyError(unwrap(error));
                                return;
                            }

                            List<ChannelObservation> safeObservations =
                                    observations == null
                                            ? List.of()
                                            : List.copyOf(observations);

                            observationHandler.accept(
                                    safeObservations
                            );
                        } catch (RuntimeException exception) {
                            notifyError(exception);
                        } finally {
                            inspectionInFlight.set(false);
                        }
                    }
            );
        } catch (RuntimeException exception) {
            inspectionInFlight.set(false);
            notifyError(exception);
        }
    }

    private Throwable unwrap(Throwable error) {
        if (error.getCause() != null) {
            return error.getCause();
        }

        return error;
    }

    private void notifyError(Throwable error) {
        try {
            errorHandler.accept(error);
        } catch (RuntimeException ignored) {
            /*
             * Un fallo del manejador no debe terminar el scheduler.
             */
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException(
                    "El monitor de Discord ya fue cerrado."
            );
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        active.set(false);
        scheduler.shutdownNow();
    }
}