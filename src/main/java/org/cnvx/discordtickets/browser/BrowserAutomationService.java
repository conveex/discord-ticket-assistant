package org.cnvx.discordtickets.browser;

import org.cnvx.discordtickets.model.ServerType;

import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BrowserAutomationService implements AutoCloseable {

    private final ExecutorService executor;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private DiscordBrowserSession browserSession;

    public BrowserAutomationService() {
        executor = Executors.newSingleThreadExecutor(task -> {
            Thread t = new Thread(task, "discord-playwright-thread");
            t.setDaemon(true);
            return t;
        });
    }

    public CompletableFuture<Void> open(List<String> discordUrls) {
        Objects.requireNonNull(discordUrls, "discordUrls");

        if (closed.get()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(
                            "El servicio del navegador ya fue cerrado."
                    )
            );
        }

        return CompletableFuture.runAsync(() -> {
            if (browserSession != null) {
                throw new IllegalStateException(
                        "El navegador ya esta abierto."
                );
            }

            browserSession = new DiscordBrowserSession();
            browserSession.open(discordUrls);
        }, executor);
    }

    public CompletableFuture<List<ChannelObservation>>
    inspectMentionedTickets(
            Set<ServerType> enabledServers
    ) {
        Objects.requireNonNull(
                enabledServers,
                "enabledServers"
        );

        if (closed.get()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(
                            "El servicio del navegador ya fue cerrado."
                    )
            );
        }

        Set<ServerType> servers =
                Set.copyOf(enabledServers);

        return CompletableFuture.supplyAsync(() -> {
            if (browserSession == null) {
                throw new IllegalStateException(
                        "El navegador todavía no está abierto."
                );
            }

            DiscordDomInspector inspector =
                    new DiscordDomInspector();

            List<ChannelObservation> observations =
                    new ArrayList<>();

            if (servers.contains(ServerType.SKYBLOCK_MANIACS)) {
                observations.addAll(
                        inspector.findMentionedTicketChannels(
                                browserSession.skyblockPage(),
                                ServerType.SKYBLOCK_MANIACS
                        )
                );
            }

            if (servers.contains(ServerType.KUUDRA_GANG)) {
                observations.addAll(
                        inspector.findMentionedTicketChannels(
                                browserSession.kuudraPage(),
                                ServerType.KUUDRA_GANG
                        )
                );
            }

            return List.copyOf(observations);
        }, executor);
    }

    public CompletableFuture<TicketClaimResult> claimTicket(
            ChannelObservation observation,
            String configuredUsername
    ) {
        Objects.requireNonNull(
                observation,
                "La observación del ticket es obligatoria."
        );

        Objects.requireNonNull(
                configuredUsername,
                "El usuario configurado es obligatorio."
        );

        if (configuredUsername.isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "El usuario configurado no puede estar vacío."
                    )
            );
        }

        if (closed.get()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(
                            "El servicio del navegador ya fue cerrado."
                    )
            );
        }

        return CompletableFuture.supplyAsync(() -> {
            if (browserSession == null) {
                throw new IllegalStateException(
                        "El navegador todavía no está abierto."
                );
            }

            DiscordTicketClaimer claimer =
                    new DiscordTicketClaimer();

            return claimer.claim(
                    browserSession.pageFor(
                            observation.server()
                    ),
                    observation,
                    configuredUsername
            );
        }, executor);
    }

    @Override
    public void close() throws Exception {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        Future<?> closeOperation = executor.submit(() -> {
            if (browserSession != null) {
                browserSession.close();
                browserSession = null;
            }
        });

        try {
            closeOperation.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException exception) {
            System.err.println(
                    "No fue posible cerrar limpiamente el navegador: "
                            + exception.getMessage()
            );
        } finally {
            executor.shutdownNow();
        }
    }
}
