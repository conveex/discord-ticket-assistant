package org.cnvx.discordtickets.browser;

import org.cnvx.discordtickets.model.ServerType;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class BrowserAutomationService
        implements AutoCloseable {

    private static final long RECOVERY_BACKOFF_MILLIS =
            Duration.ofSeconds(5).toMillis();

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(
                        task,
                        "discord-playwright-thread"
                );

                thread.setDaemon(false);
                return thread;
            });

    private final AtomicBoolean closed =
            new AtomicBoolean(false);

    private volatile Consumer<BrowserStatusEvent>
            statusListener = event -> {
    };

    private DiscordBrowserSession browserSession;

    private List<String> configuredUrls =
            List.of();

    private long nextRecoveryAttemptAtMillis;

    private static final int
            STRUCTURAL_FAILURES_BEFORE_RECOVERY = 8;

    private static final int
            MENTION_MISMATCHES_BEFORE_RECOVERY = 4;

    private static final long
            SERVER_RECOVERY_COOLDOWN_MILLIS =
            Duration.ofSeconds(30).toMillis();

    private final Map<ServerType, Integer>
            structuralFailureCounts =
            new EnumMap<>(ServerType.class);

    private final Map<ServerType, Integer>
            mentionMismatchCounts =
            new EnumMap<>(ServerType.class);

    private final Map<ServerType, Integer>
            recoveredMentionCounts =
            new EnumMap<>(ServerType.class);

    private final Map<ServerType, Long>
            nextServerRecoveryAtMillis =
            new EnumMap<>(ServerType.class);

    public void setStatusListener(
            Consumer<BrowserStatusEvent> statusListener
    ) {
        this.statusListener = Objects.requireNonNull(
                statusListener,
                "El listener de estado es obligatorio."
        );
    }

    public CompletableFuture<Void> open(
            List<String> urls
    ) {
        Objects.requireNonNull(
                urls,
                "Las URL del navegador son obligatorias."
        );

        List<String> urlsCopy =
                List.copyOf(urls);

        return CompletableFuture.runAsync(() -> {
            ensureNotClosed();

            configuredUrls = urlsCopy;

            notifyStatus(
                    BrowserConnectionState.STARTING,
                    "Abriendo Chrome automatizado."
            );

            try {
                recreateBrowserSession();

                notifyStatus(
                        BrowserConnectionState.READY,
                        "Chrome abrió las pestañas de Discord."
                );

            } catch (RuntimeException exception) {
                nextRecoveryAttemptAtMillis =
                        System.currentTimeMillis()
                                + RECOVERY_BACKOFF_MILLIS;

                notifyStatus(
                        BrowserConnectionState.ERROR,
                        readableMessage(exception)
                );

                throw exception;
            }
        }, executor);
    }

    public CompletableFuture<List<ChannelObservation>>
    inspectMentionedTickets(
            Set<ServerType> enabledServers
    ) {
        Objects.requireNonNull(
                enabledServers,
                "Los servidores habilitados son obligatorios."
        );

        Set<ServerType> servers =
                Set.copyOf(enabledServers);

        return CompletableFuture.supplyAsync(() -> {
            ensureNotClosed();
            ensureBrowserAvailable();

            DiscordDomInspector inspector =
                    new DiscordDomInspector();

            List<ChannelObservation> observations =
                    new ArrayList<>();

            if (servers.contains(
                    ServerType.SKYBLOCK_MANIACS
            )) {
                observations.addAll(
                        inspectServerPage(
                                inspector,
                                ServerType.SKYBLOCK_MANIACS
                        )
                );
            }

            if (servers.contains(
                    ServerType.KUUDRA_GANG
            )) {
                observations.addAll(
                        inspectServerPage(
                                inspector,
                                ServerType.KUUDRA_GANG
                        )
                );
            }

            return List.copyOf(observations);
        }, executor);
    }

    public CompletableFuture<TicketClaimResult> claimTicket(
            ChannelObservation observation,
            String minecraftUsername
    ) {
        Objects.requireNonNull(
                observation,
                "La observación del ticket es obligatoria."
        );

        Objects.requireNonNull(
                minecraftUsername,
                "El nombre de Minecraft es obligatorio."
        );

        if (minecraftUsername.isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "El nombre de Minecraft "
                                    + "no puede estar vacío."
                    )
            );
        }

        return CompletableFuture.supplyAsync(() -> {
            ensureNotClosed();
            ensureBrowserAvailable();

            DiscordTicketClaimer claimer =
                    new DiscordTicketClaimer();

            return claimer.claim(
                    browserSession.pageFor(
                            observation.server()
                    ),
                    observation,
                    minecraftUsername
            );
        }, executor);
    }

    private void ensureBrowserAvailable() {
        if (browserSession != null
                && browserSession.isHealthy()) {
            return;
        }

        if (configuredUrls.isEmpty()) {
            throw new IllegalStateException(
                    "No se configuraron las URL de Discord."
            );
        }

        long now = System.currentTimeMillis();

        if (now < nextRecoveryAttemptAtMillis) {
            throw new IllegalStateException(
                    "Chrome no está disponible. "
                            + "El reintento automático está pendiente."
            );
        }

        nextRecoveryAttemptAtMillis =
                now + RECOVERY_BACKOFF_MILLIS;

        notifyStatus(
                BrowserConnectionState.RECOVERING,
                "Chrome o una de sus pestañas fue cerrada. "
                        + "Intentando recuperarla."
        );

        try {
            recreateBrowserSession();

            nextRecoveryAttemptAtMillis = 0;

            notifyStatus(
                    BrowserConnectionState.READY,
                    "Chrome fue recuperado automáticamente."
            );

        } catch (RuntimeException exception) {
            notifyStatus(
                    BrowserConnectionState.ERROR,
                    readableMessage(exception)
            );

            throw exception;
        }
    }

    private void recreateBrowserSession() {
        if (browserSession != null) {
            browserSession.close();
        }

        DiscordBrowserSession newSession =
                new DiscordBrowserSession();

        try {
            newSession.open(configuredUrls);
            browserSession = newSession;

        } catch (RuntimeException exception) {
            newSession.close();
            browserSession = null;
            throw exception;
        }
    }

    private void ensureNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException(
                    "El servicio del navegador ya fue cerrado."
            );
        }
    }

    private void notifyStatus(
            BrowserConnectionState state,
            String detail
    ) {
        try {
            statusListener.accept(
                    new BrowserStatusEvent(
                            state,
                            detail
                    )
            );
        } catch (RuntimeException ignored) {
            // El listener no debe terminar el hilo de Playwright.
        }
    }

    private String readableMessage(
            Throwable error
    ) {
        Throwable current = error;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();

        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        Future<?> closeFuture;

        try {
            closeFuture = executor.submit(() -> {
                if (browserSession != null) {
                    browserSession.close();
                    browserSession = null;
                }

                notifyStatus(
                        BrowserConnectionState.CLOSED,
                        "Chrome automatizado fue cerrado."
                );
            });

        } catch (RuntimeException exception) {
            executor.shutdownNow();
            return;
        }

        try {
            closeFuture.get(
                    10,
                    TimeUnit.SECONDS
            );
        } catch (Exception ignored) {
            closeFuture.cancel(true);
        } finally {
            executor.shutdownNow();
        }
    }

    public CompletableFuture<Optional<TicketClaimResult>>
    inspectClaimConfirmation(
            ServerType server,
            String minecraftUsername
    ) {
        Objects.requireNonNull(
                server,
                "El servidor es obligatorio."
        );

        Objects.requireNonNull(
                minecraftUsername,
                "El nombre de Minecraft es obligatorio."
        );

        return CompletableFuture.supplyAsync(() -> {
            ensureNotClosed();
            ensureBrowserAvailable();

            DiscordTicketClaimer claimer =
                    new DiscordTicketClaimer();

            return claimer.inspectCurrentChannelConfirmation(
                    browserSession.pageFor(server),
                    minecraftUsername
            );
        }, executor);
    }

    private List<ChannelObservation> inspectServerPage(
            DiscordDomInspector inspector,
            ServerType server
    ) {
        var page = browserSession.pageFor(server);

        String expectedGuildId =
                browserSession.expectedGuildId(server);

        DiscordPageInspection inspection =
                inspector.inspect(
                        page,
                        server,
                        expectedGuildId
                );

        DiscordPageHealth health =
                inspection.health();

        if (!health.structurallyHealthyFor(
                expectedGuildId
        )) {
            int failureCount =
                    structuralFailureCounts.merge(
                            server,
                            1,
                            Integer::sum
                    );

            if (failureCount
                    >= STRUCTURAL_FAILURES_BEFORE_RECOVERY) {

                return recoverServerAndInspect(
                        inspector,
                        server,
                        "La pestaña dejó de mostrar una "
                                + "barra lateral válida."
                );
            }

            return inspection.observations();
        }

        structuralFailureCounts.put(server, 0);

        boolean unexplainedGuildMention =
                health.guildMentionCount() > 0
                        && inspection.observations().isEmpty();

        if (!unexplainedGuildMention) {
            mentionMismatchCounts.put(server, 0);

            if (health.guildMentionCount() == 0
                    || !inspection.observations().isEmpty()) {

                recoveredMentionCounts.remove(server);
            }

            return inspection.observations();
        }

        int mismatchCount =
                mentionMismatchCounts.merge(
                        server,
                        1,
                        Integer::sum
                );

        Integer alreadyRecoveredForCount =
                recoveredMentionCounts.get(server);

        boolean recoveryAlreadyAttempted =
                alreadyRecoveredForCount != null
                        && alreadyRecoveredForCount
                        == health.guildMentionCount();

        if (mismatchCount
                < MENTION_MISMATCHES_BEFORE_RECOVERY
                || recoveryAlreadyAttempted) {

            return inspection.observations();
        }

        recoveredMentionCounts.put(
                server,
                health.guildMentionCount()
        );

        return recoverServerAndInspect(
                inspector,
                server,
                "Discord muestra "
                        + health.guildMentionCount()
                        + " mención(es), pero la barra lateral "
                        + "no expuso ningún canal mencionado."
        );
    }

    private List<ChannelObservation> recoverServerAndInspect(
            DiscordDomInspector inspector,
            ServerType server,
            String reason
    ) {
        long now = System.currentTimeMillis();

        long nextAllowedRecovery =
                nextServerRecoveryAtMillis.getOrDefault(
                        server,
                        0L
                );

        if (now < nextAllowedRecovery) {
            return List.of();
        }

        nextServerRecoveryAtMillis.put(
                server,
                now + SERVER_RECOVERY_COOLDOWN_MILLIS
        );

        notifyStatus(
                BrowserConnectionState.RECOVERING,
                "Reconectando "
                        + server.displayName()
                        + ": "
                        + reason
        );

        try {
            var replacementPage =
                    browserSession.replacePage(server);

            structuralFailureCounts.put(server, 0);
            mentionMismatchCounts.put(server, 0);

            DiscordPageInspection recoveredInspection =
                    inspector.inspect(
                            replacementPage,
                            server,
                            browserSession.expectedGuildId(server)
                    );

            notifyStatus(
                    BrowserConnectionState.READY,
                    server.displayName()
                            + " fue reconectado automáticamente."
            );

            return recoveredInspection.observations();

        } catch (RuntimeException exception) {
            notifyStatus(
                    BrowserConnectionState.ERROR,
                    "No fue posible reconectar "
                            + server.displayName()
                            + ": "
                            + readableMessage(exception)
            );

            throw exception;
        }
    }

    public CompletableFuture<Void> reconnectServer(
            ServerType server
    ) {
        Objects.requireNonNull(
                server,
                "El servidor es obligatorio."
        );

        return CompletableFuture.runAsync(() -> {
            ensureNotClosed();
            ensureBrowserAvailable();

            notifyStatus(
                    BrowserConnectionState.RECOVERING,
                    "Renovando la pestaña de "
                            + server.displayName()
            );

            browserSession.replacePage(server);

            resetServerDiagnostics(server);

            notifyStatus(
                    BrowserConnectionState.READY,
                    server.displayName()
                            + " quedó listo nuevamente."
            );
        }, executor);
    }

    public CompletableFuture<Void> reconnectAll() {
        return CompletableFuture.runAsync(() -> {
            ensureNotClosed();
            ensureBrowserAvailable();

            notifyStatus(
                    BrowserConnectionState.RECOVERING,
                    "Reconectando las pestañas de Discord."
            );

            browserSession.replacePage(
                    ServerType.SKYBLOCK_MANIACS
            );

            browserSession.replacePage(
                    ServerType.KUUDRA_GANG
            );

            resetServerDiagnostics(
                    ServerType.SKYBLOCK_MANIACS
            );

            resetServerDiagnostics(
                    ServerType.KUUDRA_GANG
            );

            notifyStatus(
                    BrowserConnectionState.READY,
                    "Las pestañas de Discord fueron reconectadas."
            );
        }, executor);
    }

    private void resetServerDiagnostics(ServerType server) {
        structuralFailureCounts.remove(server);
        mentionMismatchCounts.remove(server);
        recoveredMentionCounts.remove(server);
        nextServerRecoveryAtMillis.remove(server);
    }
}