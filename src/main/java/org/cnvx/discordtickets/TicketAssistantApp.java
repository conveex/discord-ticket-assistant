package org.cnvx.discordtickets;

import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.Screen;
import org.cnvx.discordtickets.browser.BrowserAutomationService;
import org.cnvx.discordtickets.browser.ChannelObservation;
import org.cnvx.discordtickets.browser.TicketClaimResult;
import org.cnvx.discordtickets.config.DiscordUrls;
import org.cnvx.discordtickets.config.MonitoringConfiguration;
import org.cnvx.discordtickets.model.ServerType;
import org.cnvx.discordtickets.model.TicketCandidate;
import org.cnvx.discordtickets.model.TicketCategory;
import org.cnvx.discordtickets.model.TicketId;
import org.cnvx.discordtickets.rules.CoordinatorSnapshot;
import org.cnvx.discordtickets.rules.ReservationDecision;
import org.cnvx.discordtickets.rules.ReservationResult;
import org.cnvx.discordtickets.rules.TicketCoordinator;
import org.cnvx.discordtickets.util.UsernameNormalizer;
import org.cnvx.discordtickets.monitoring.DiscordMonitoringLoop;
import org.cnvx.discordtickets.monitoring.TicketDiscoveryTracker;
import org.cnvx.discordtickets.persistence.PersistedTicketState;
import org.cnvx.discordtickets.persistence.TicketStateStore;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

import java.util.Optional;
import java.util.stream.Stream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class TicketAssistantApp extends Application {

    private final TicketCoordinator coordinator =
            new TicketCoordinator();

    private final ObservableList<TicketCandidate> activeTicketItems =
            FXCollections.observableArrayList();

    private BrowserAutomationService browserService;
    private MonitoringConfiguration monitoringConfiguration;

    private boolean browserReady;

    private Label browserStatusLabel;
    private Label monitoringStatusLabel;
    private Label occupancyLabel;
    private Label lockedCategoryLabel;

    private TextArea logArea;
    private TextField minecraftUsernameField;

    private CheckBox skyblockCheckBox;
    private CheckBox kuudraCheckBox;

    private CheckBox basicCheckBox;
    private CheckBox hotCheckBox;
    private CheckBox burningCheckBox;
    private CheckBox fieryCheckBox;
    private CheckBox infernalCheckBox;

    private Button startButton;
    private Button pauseButton;
    private Button resumeButton;

    private ListView<TicketCandidate> activeTicketsListView;

    private static final DateTimeFormatter MONITOR_TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private final TicketDiscoveryTracker discoveryTracker =
            new TicketDiscoveryTracker();

    private DiscordMonitoringLoop monitoringLoop;

    private Label automaticMonitorLabel;

    private CheckBox includeExistingMentionsCheckBox;

    private String lastMonitoringError;

    private TicketStateStore ticketStateStore;

    private String lastPersistenceError;

    /*
     * Tickets que llegaron después de la línea base y, por tanto,
     * pueden ser procesados.
     */
    private final Set<TicketId> eligibleTicketIds =
            new HashSet<>();

    /*
     * Evita repetir cada segundo mensajes como "límite alcanzado".
     */
    private final Map<TicketId, ReservationDecision>
            waitingDecisionByTicket =
            new HashMap<>();

    /*
     * Si hubo un error técnico antes del clic, no intentamos hacer
     * clic cada segundo. Se elimina cuando desaparece la mención.
     */
    private final Set<TicketId> temporarilyFailedTicketIds =
            new HashSet<>();

    public static void launchApplication(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        browserService = new BrowserAutomationService();

        ticketStateStore = TicketStateStore.defaultStore();

        monitoringLoop = new DiscordMonitoringLoop(
                browserService::inspectMentionedTickets,
                Duration.ofSeconds(1)
        );

        Label title = new Label("Auto-aceptador de Carries");
        title.setStyle(
                "-fx-font-size: 22px;"
                        + "-fx-font-weight: bold;"
        );

        browserStatusLabel = new Label("Navegador: iniciando...");
        monitoringStatusLabel = new Label("Vigilancia: NO INICIADA");
        occupancyLabel = new Label("Ocupación: 0 / 3");
        lockedCategoryLabel = new Label(
                "Categoría bloqueada: Ninguna"
        );
        automaticMonitorLabel =
                new Label("Monitor automático: DETENIDO");

        VBox statusSection = new VBox(
                5,
                browserStatusLabel,
                monitoringStatusLabel,
                automaticMonitorLabel,
                occupancyLabel,
                lockedCategoryLabel
        );

        minecraftUsernameField = new TextField();
        minecraftUsernameField.setPromptText("Ejemplo: convecs");

        VBox usernameSection = new VBox(
                6,
                new Label("Usuario de Minecraft del Carry"),
                minecraftUsernameField
        );

        skyblockCheckBox = new CheckBox("Skyblock Maniacs");
        skyblockCheckBox.setSelected(true);

        kuudraCheckBox = new CheckBox("Kuudra Gang");
        kuudraCheckBox.setSelected(true);

        HBox serverSelection = new HBox(
                18,
                skyblockCheckBox,
                kuudraCheckBox
        );

        basicCheckBox = new CheckBox("Basic");
        hotCheckBox = new CheckBox("Hot");
        burningCheckBox = new CheckBox("Burning");
        fieryCheckBox = new CheckBox("Fiery");
        infernalCheckBox = new CheckBox("Infernal");

        FlowPane categorySelection = new FlowPane(
                12,
                8,
                basicCheckBox,
                hotCheckBox,
                burningCheckBox,
                fieryCheckBox,
                infernalCheckBox
        );

        includeExistingMentionsCheckBox = new CheckBox(
                "Procesar menciones que ya existan al iniciar"
        );

        includeExistingMentionsCheckBox.setSelected(false);

        startButton = new Button("Iniciar vigilancia");
        startButton.setDisable(true);
        startButton.setDefaultButton(true);
        startButton.setOnAction(event -> startMonitoring());

        pauseButton = new Button("Pausar");
        pauseButton.setDisable(true);
        pauseButton.setOnAction(event -> pauseMonitoring());

        resumeButton = new Button("Reanudar");
        resumeButton.setDisable(true);
        resumeButton.setOnAction(event -> resumeMonitoring());

        HBox monitoringButtons = new HBox(
                10,
                startButton,
                pauseButton,
                resumeButton
        );

        activeTicketsListView = new ListView<>(activeTicketItems);
        activeTicketsListView.setPrefHeight(190);
        activeTicketsListView.setMinHeight(150);

        activeTicketsListView.setCellFactory(list -> new ListCell<>() {

            private final Label ticketLabel = new Label();
            private final Region spacer = new Region();
            private final Button completeButton = new Button("Completar");

            private final HBox row = new HBox(
                    12,
                    ticketLabel,
                    spacer,
                    completeButton
            );

            {
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("ticket-row");

                HBox.setHgrow(spacer, Priority.ALWAYS);

                completeButton
                        .getStyleClass()
                        .add("ticket-complete-button");

                completeButton.setOnAction(event -> {
                    TicketCandidate ticket = getItem();

                    if (ticket != null) {
                        completeTicket(ticket);
                    }
                });
            }

            @Override
            protected void updateItem(
                    TicketCandidate ticket,
                    boolean empty
            ) {
                super.updateItem(ticket, empty);

                if (empty || ticket == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                ticketLabel.setText(
                        ticket.id().server().displayName()
                                + " | "
                                + ticket.category().displayName()
                                + " | "
                                + ticket.channelName()
                );

                setText(null);
                setGraphic(row);
            }
        });

        VBox activeTicketsSection = new VBox(
                7,
                new Label("Tickets activos"),
                activeTicketsListView
        );

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefRowCount(9);

        VBox content = new VBox(
                12,
                title,
                statusSection,
                new Separator(),
                usernameSection,
                new Label("Servidores que se vigilarán"),
                serverSelection,
                new Label("Categorías que pueden aceptarse"),
                categorySelection,
                new Label("Comportamiento inicial"),
                includeExistingMentionsCheckBox,
                monitoringButtons,
                new Separator(),
                activeTicketsSection,
                new Separator(),
                new Label("Registro"),
                logArea
        );

        content.setPadding(new Insets(18));
        content.setFillWidth(true);
        content.setMinWidth(640);

        ScrollPane scrollPane = new ScrollPane(content);

        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);
        scrollPane.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER
        );
        scrollPane.setVbarPolicy(
                ScrollPane.ScrollBarPolicy.AS_NEEDED
        );

        Rectangle2D visualBounds =
                Screen.getPrimary().getVisualBounds();

        double initialWidth = Math.min(
                860,
                visualBounds.getWidth() * 0.92
        );

        double initialHeight = Math.min(
                760,
                visualBounds.getHeight() * 0.88
        );

        Scene scene = new Scene(
                scrollPane,
                initialWidth,
                initialHeight
        );

        var darkStyle = TicketAssistantApp.class.getResource(
                "/styles/app-dark.css"
        );

        if (darkStyle == null) {
            throw new IllegalStateException(
                    "No se encontró /styles/app-dark.css"
            );
        }

        scene.getStylesheets().add(
                darkStyle.toExternalForm()
        );

        stage.setTitle("Asistente de carries");
        stage.getIcons().add(loadApplicationIcon());
        stage.setScene(scene);

        stage.setMinWidth(660);
        stage.setMinHeight(500);

        stage.setMaxWidth(visualBounds.getWidth());
        stage.setMaxHeight(visualBounds.getHeight());

        stage.setResizable(true);
        stage.show();
        stage.centerOnScreen();

        refreshCoordinatorView();
        restorePersistedStateIfPresent();
        openDiscordBrowser();
    }

    private void openDiscordBrowser() {
        DiscordUrls urls;

        try {
            urls = DiscordUrls.load();
        } catch (RuntimeException exception) {
            browserStatusLabel.setText(
                    "Navegador: configuración incompleta"
            );

            appendLog("ERROR: " + exception.getMessage());
            return;
        }

        appendLog("Abriendo Chrome...");
        appendLog(
                "Perfil: ~/.discord-ticket-assistant/chrome-profile"
        );

        browserService.open(urls.asList())
                .whenComplete((ignored, error) ->
                        Platform.runLater(() -> {
                            if (error == null) {
                                browserReady = true;

                                browserStatusLabel.setText(
                                        "Navegador: abierto"
                                );

                                startButton.setDisable(false);

                                appendLog(
                                        "Chrome abrió las dos pestañas "
                                                + "correctamente."
                                );
                            } else {
                                browserReady = false;

                                browserStatusLabel.setText(
                                        "Navegador: error"
                                );

                                Throwable cause =
                                        error.getCause() != null
                                                ? error.getCause()
                                                : error;

                                appendLog(
                                        "ERROR AL ABRIR CHROME: "
                                                + cause.getMessage()
                                );
                            }
                        })
                );
    }

    private void startMonitoring() {
        if (!browserReady) {
            showValidationError(
                    "Chrome todavía no se encuentra listo."
            );
            return;
        }

        MonitoringConfiguration configuration;

        try {
            configuration = readConfigurationFromForm();

            validateConfigurationAgainstRestoredState(
                    configuration
            );

        } catch (IllegalArgumentException exception) {
            showValidationError(exception.getMessage());
            return;
        }

        monitoringConfiguration = configuration;

        coordinator.resume();

        eligibleTicketIds.clear();
        waitingDecisionByTicket.clear();
        temporarilyFailedTicketIds.clear();

        discoveryTracker.beginSession(
                configuration.includeExistingMentions()
        );

        lastMonitoringError = null;

        monitoringLoop.start(
                configuration.enabledServers(),
                this::handleAutomaticInspection,
                this::handleAutomaticInspectionError
        );

        automaticMonitorLabel.setText(
                "Monitor automático: ACTIVO"
        );

        setConfigurationControlsDisabled(true);

        startButton.setDisable(true);
        pauseButton.setDisable(false);
        resumeButton.setDisable(true);

        appendLog(
                "Vigilancia iniciada para el jugador: "
                        + configuration.minecraftUsername()
        );

        appendLog(
                "Servidores: "
                        + formatServers(
                        configuration.enabledServers()
                )
        );

        appendLog(
                "Categorías: "
                        + formatCategories(
                        configuration.enabledCategories()
                )
        );

        if (configuration.includeExistingMentions()) {
            appendLog(
                    "Las menciones existentes serán consideradas tickets nuevos."
            );
        } else {
            appendLog(
                    "La primera inspección establecerá una línea base; "
                            + "solo se reportarán menciones posteriores."
            );
        }

        refreshCoordinatorView();
    }

    private void pauseMonitoring() {
        coordinator.pause();

        monitoringLoop.pause();

        automaticMonitorLabel.setText(
                "Monitor automático: PAUSADO"
        );

        pauseButton.setDisable(true);
        resumeButton.setDisable(false);

        appendLog("Vigilancia pausada.");

        refreshCoordinatorView();
    }

    private void resumeMonitoring() {
        if (monitoringConfiguration == null) {
            showValidationError(
                    "No existe una configuración activa."
            );
            return;
        }

        coordinator.resume();

        monitoringLoop.resume();

        automaticMonitorLabel.setText(
                "Monitor automático: ACTIVO"
        );

        pauseButton.setDisable(false);
        resumeButton.setDisable(true);

        appendLog("Vigilancia reanudada.");

        refreshCoordinatorView();
    }

    private MonitoringConfiguration readConfigurationFromForm() {
        String minecraftUsername = UsernameNormalizer.normalize(
                minecraftUsernameField.getText()
        );

        if (minecraftUsername.isBlank()) {
            minecraftUsernameField.requestFocus();

            throw new IllegalArgumentException(
                    "Debes introducir el usuario de Minecraft."
            );
        }

        EnumSet<ServerType> servers =
                EnumSet.noneOf(ServerType.class);

        if (skyblockCheckBox.isSelected()) {
            servers.add(ServerType.SKYBLOCK_MANIACS);
        }

        if (kuudraCheckBox.isSelected()) {
            servers.add(ServerType.KUUDRA_GANG);
        }

        EnumSet<TicketCategory> categories =
                EnumSet.noneOf(TicketCategory.class);

        if (basicCheckBox.isSelected()) {
            categories.add(TicketCategory.BASIC);
        }

        if (hotCheckBox.isSelected()) {
            categories.add(TicketCategory.HOT);
        }

        if (burningCheckBox.isSelected()) {
            categories.add(TicketCategory.BURNING);
        }

        if (fieryCheckBox.isSelected()) {
            categories.add(TicketCategory.FIERY);
        }

        if (infernalCheckBox.isSelected()) {
            categories.add(TicketCategory.INFERNAL);
        }

        return new MonitoringConfiguration(
                minecraftUsername,
                servers,
                categories,
                includeExistingMentionsCheckBox.isSelected()
        );
    }

    private void completeTicket(TicketCandidate ticket) {
        boolean completed = coordinator.completeActiveTicket(
                ticket.id()
        );

        if (completed) {
            appendLog(
                    "Ticket marcado manualmente como completado: "
                            + ticket.channelName()
            );
        } else {
            appendLog(
                    "No fue posible completar el ticket: "
                            + ticket.channelName()
            );
        }

        refreshCoordinatorView();
        persistCurrentState();
    }

    private void refreshCoordinatorView() {
        CoordinatorSnapshot snapshot = coordinator.snapshot();

        if (monitoringConfiguration == null) {
            monitoringStatusLabel.setText(
                    "Vigilancia: NO INICIADA"
            );
        } else if (snapshot.paused()) {
            monitoringStatusLabel.setText(
                    "Vigilancia: PAUSADA"
            );
        } else {
            monitoringStatusLabel.setText(
                    "Vigilancia: ACTIVA"
            );
        }

        occupancyLabel.setText(
                "Ocupación: "
                        + snapshot.occupiedSlots()
                        + " / "
                        + snapshot.maximumSlots()
        );

        lockedCategoryLabel.setText(
                "Categoría bloqueada: "
                        + displayCategory(
                        snapshot.lockedCategory()
                )
        );

        activeTicketItems.setAll(
                snapshot.activeTickets()
        );
    }

    private void setConfigurationControlsDisabled(
            boolean disabled
    ) {
        includeExistingMentionsCheckBox.setDisable(disabled);

        minecraftUsernameField.setDisable(disabled);

        skyblockCheckBox.setDisable(disabled);
        kuudraCheckBox.setDisable(disabled);

        basicCheckBox.setDisable(disabled);
        hotCheckBox.setDisable(disabled);
        burningCheckBox.setDisable(disabled);
        fieryCheckBox.setDisable(disabled);
        infernalCheckBox.setDisable(disabled);
    }

    private String displayCategory(
            TicketCategory category
    ) {
        return category == null
                ? "Ninguna"
                : category.displayName();
    }

    private String formatServers(
            Set<ServerType> servers
    ) {
        return servers.stream()
                .map(ServerType::displayName)
                .collect(Collectors.joining(", "));
    }

    private String formatCategories(
            Set<TicketCategory> categories
    ) {
        return categories.stream()
                .map(TicketCategory::displayName)
                .collect(Collectors.joining(", "));
    }

    private void showValidationError(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Configuración incompleta");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void appendLog(String message) {
        logArea.appendText(
                message + System.lineSeparator()
        );
    }

    private Image loadApplicationIcon() {
        var iconUrl = TicketAssistantApp.class.getResource(
                "/images/icon.png"
        );

        if (iconUrl == null) {
            throw new IllegalStateException(
                    "No se encontró el icono /images/icon.png"
            );
        }

        return new Image(iconUrl.toExternalForm());
    }

    private void handleAutomaticInspection(
            List<ChannelObservation> observations
    ) {
        Platform.runLater(() -> {
            if (monitoringConfiguration == null
                    || coordinator.isPaused()) {
                return;
            }

            lastMonitoringError = null;

            automaticMonitorLabel.setText(
                    "Monitor automático: ACTIVO · última revisión "
                            + LocalTime.now().format(
                            MONITOR_TIME_FORMAT
                    )
            );

            /*
             * Solo estas observaciones se anuncian como nuevas.
             */
            List<ChannelObservation> newObservations =
                    discoveryTracker.findNew(observations);

            for (ChannelObservation observation : newObservations) {
                TicketId ticketId = ticketIdOf(observation);

                eligibleTicketIds.add(ticketId);

                logNewObservation(observation);
            }

            Set<TicketId> currentlyVisibleIds =
                    observations.stream()
                            .map(this::ticketIdOf)
                            .collect(Collectors.toSet());

            /*
             * Si la mención desaparece, dejamos de considerarlo
             * candidato pendiente.
             */
            eligibleTicketIds.retainAll(currentlyVisibleIds);

            waitingDecisionByTicket
                    .keySet()
                    .retainAll(currentlyVisibleIds);

            temporarilyFailedTicketIds
                    .retainAll(currentlyVisibleIds);

            /*
             * Se recorren todas las observaciones elegibles, no solo
             * las nuevas. De esta manera un ticket bloqueado por límite
             * o categoría se vuelve a evaluar después.
             */
            for (ChannelObservation observation : observations) {
                TicketId ticketId = ticketIdOf(observation);

                if (eligibleTicketIds.contains(ticketId)) {
                    tryStartAutomaticClaim(observation);
                }
            }
        });
    }

    private void logNewObservation(
            ChannelObservation observation
    ) {
        if (!monitoringConfiguration.acceptsServer(
                observation.server()
        )) {
            return;
        }

        if (!monitoringConfiguration.acceptsCategory(
                observation.category()
        )) {
            appendLog(
                    "Ticket detectado pero ignorado por configuración: "
                            + observation.server().displayName()
                            + " | "
                            + observation.category().displayName()
                            + " | "
                            + observation.channelName()
            );

            return;
        }

        appendLog(
                "NUEVO TICKET DETECTADO AUTOMÁTICAMENTE: "
                        + observation.server().displayName()
                        + " | "
                        + observation.category().displayName()
                        + " | "
                        + observation.channelName()
                        + " | ID: "
                        + observation.channelId()
        );
    }

    private void tryStartAutomaticClaim(
            ChannelObservation observation
    ) {
        if (!monitoringConfiguration.acceptsServer(
                observation.server()
        )) {
            return;
        }

        if (!monitoringConfiguration.acceptsCategory(
                observation.category()
        )) {
            return;
        }

        TicketId ticketId = ticketIdOf(observation);

        if (temporarilyFailedTicketIds.contains(ticketId)) {
            return;
        }

        TicketCandidate ticket =
                candidateFrom(observation);

        ReservationResult reservation =
                coordinator.reserve(ticket);

        switch (reservation.decision()) {
            case RESERVED -> {
                waitingDecisionByTicket.remove(ticketId);

                appendLog(
                        "Espacio reservado; intentando reclamar: "
                                + ticketSummary(observation)
                );

                refreshCoordinatorView();

                persistCurrentState();

                browserService.claimTicket(
                        observation,
                        monitoringConfiguration.minecraftUsername()
                ).whenComplete((claimResult, error) ->
                        Platform.runLater(() ->
                                finishAutomaticClaim(
                                        ticket,
                                        claimResult,
                                        error
                                )
                        )
                );
            }

            case DUPLICATE -> {
                /*
                 * Ya está reservado, activo o terminado.
                 */
                waitingDecisionByTicket.remove(ticketId);
            }

            case LIMIT_REACHED,
                 CATEGORY_MISMATCH -> logWaitingDecision(
                    ticket,
                    reservation
            );

            case PAUSED -> {
                // No se inicia ninguna acción.
            }
        }
    }

    private void logWaitingDecision(
            TicketCandidate ticket,
            ReservationResult result
    ) {
        ReservationDecision previous =
                waitingDecisionByTicket.put(
                        ticket.id(),
                        result.decision()
                );

        if (previous == result.decision()) {
            return;
        }

        switch (result.decision()) {
            case LIMIT_REACHED -> appendLog(
                    "Ticket pendiente por límite de 3: "
                            + ticket.channelName()
            );

            case CATEGORY_MISMATCH -> appendLog(
                    "Ticket pendiente por categoría: "
                            + ticket.channelName()
                            + ". Categoría activa: "
                            + displayCategory(
                            result.lockedCategory()
                    )
            );

            default -> {
                // No se registran otros estados aquí.
            }
        }
    }

    private void finishAutomaticClaim(
            TicketCandidate ticket,
            TicketClaimResult claimResult,
            Throwable error
    ) {
        waitingDecisionByTicket.remove(ticket.id());

        if (error != null) {
            coordinator.cancelReservation(ticket.id());

            temporarilyFailedTicketIds.add(ticket.id());

            appendLog(
                    "ERROR AL RECLAMAR "
                            + ticket.channelName()
                            + ": "
                            + readableError(error)
            );

            refreshCoordinatorView();
            persistCurrentState();
            return;
        }

        if (claimResult == null) {
            coordinator.cancelReservation(ticket.id());

            temporarilyFailedTicketIds.add(ticket.id());

            appendLog(
                    "ERROR: la reclamación devolvió un resultado vacío: "
                            + ticket.channelName()
            );

            refreshCoordinatorView();
            persistCurrentState();
            return;
        }

        switch (claimResult.status()) {
            case CLAIMED_BY_US -> {
                boolean confirmed =
                        coordinator.confirmReservation(
                                ticket.id()
                        );

                if (confirmed) {
                    appendLog(
                            "TICKET RECLAMADO CORRECTAMENTE: "
                                    + ticket.channelName()
                                    + " | reclamante: "
                                    + claimResult.claimedBy()
                    );
                } else {
                    appendLog(
                            "Discord confirmó el ticket, pero la reserva "
                                    + "ya no existía: "
                                    + ticket.channelName()
                    );
                }
            }

            case CLAIMED_BY_OTHER -> {
                coordinator.markClaimedByOther(
                        ticket.id()
                );

                appendLog(
                        "Ticket reclamado por otra persona: "
                                + ticket.channelName()
                                + " | reclamante: "
                                + claimResult.claimedBy()
                );
            }

            case BUTTON_NOT_FOUND,
                 CLICK_FAILED,
                 TECHNICAL_FAILURE -> {
                coordinator.cancelReservation(
                        ticket.id()
                );

                temporarilyFailedTicketIds.add(
                        ticket.id()
                );

                appendLog(
                        "No fue posible reclamar "
                                + ticket.channelName()
                                + ": "
                                + claimResult.detail()
                );
            }

            case CONFIRMATION_TIMEOUT -> {
                /*
                 * El clic sí se envió. Liberar el espacio podría provocar
                 * que terminemos con cuatro tickets si Discord sí aceptó
                 * la reclamación.
                 *
                 * Por seguridad se registra como activo incierto.
                 */
                boolean registered =
                        coordinator.confirmReservation(
                                ticket.id()
                        );

                if (registered) {
                    appendLog(
                            "ADVERTENCIA: se hizo clic en "
                                    + ticket.channelName()
                                    + ", pero no apareció confirmación. "
                                    + "Se registró como activo por seguridad; "
                                    + "verifícalo manualmente."
                    );
                }
            }
        }

        refreshCoordinatorView();
        persistCurrentState();
    }

    private TicketId ticketIdOf(
            ChannelObservation observation
    ) {
        return new TicketId(
                observation.server(),
                observation.channelId()
        );
    }

    private TicketCandidate candidateFrom(
            ChannelObservation observation
    ) {
        return new TicketCandidate(
                ticketIdOf(observation),
                observation.category(),
                observation.channelName(),
                Instant.now()
        );
    }

    private String ticketSummary(
            ChannelObservation observation
    ) {
        return observation.server().displayName()
                + " | "
                + observation.category().displayName()
                + " | "
                + observation.channelName();
    }

    private String readableError(Throwable error) {
        Throwable current = error;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();

        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }

    private void handleAutomaticInspectionError(
            Throwable error
    ) {
        Platform.runLater(() -> {
            String message = error.getMessage();

            if (message == null || message.isBlank()) {
                message = error.getClass().getSimpleName();
            }

            automaticMonitorLabel.setText(
                    "Monitor automático: ERROR"
            );

            /*
             * Evita escribir el mismo error una vez por segundo.
             */
            if (!message.equals(lastMonitoringError)) {
                lastMonitoringError = message;

                appendLog(
                        "ERROR EN MONITOREO AUTOMÁTICO: "
                                + message
                );
            }
        });
    }

    private void restorePersistedStateIfPresent() {
        Optional<PersistedTicketState> persistedStateOptional;

        try {
            persistedStateOptional =
                    ticketStateStore.load();
        } catch (RuntimeException exception) {
            handleInvalidPersistedState(exception);
            return;
        }

        if (persistedStateOptional.isEmpty()) {
            appendLog(
                    "No se encontraron tickets ocupados "
                            + "de una sesión anterior."
            );
            return;
        }

        PersistedTicketState persistedState =
                persistedStateOptional.get();

        if (persistedState.isEmpty()) {
            ticketStateStore.delete();
            return;
        }

        String ticketDescription =
                persistedState.occupiedTickets()
                        .stream()
                        .map(ticket ->
                                "• "
                                        + ticket.id()
                                        .server()
                                        .displayName()
                                        + " | "
                                        + ticket.category()
                                        .displayName()
                                        + " | "
                                        + ticket.channelName()
                        )
                        .collect(Collectors.joining(
                                System.lineSeparator()
                        ));

        ButtonType restoreButton = new ButtonType(
                "Restaurar tickets",
                ButtonBar.ButtonData.OK_DONE
        );

        ButtonType discardButton = new ButtonType(
                "Descartar estado",
                ButtonBar.ButtonData.CANCEL_CLOSE
        );

        Alert alert = new Alert(
                Alert.AlertType.CONFIRMATION
        );

        alert.setTitle("Estado anterior encontrado");

        alert.setHeaderText(
                "Se encontraron "
                        + persistedState.occupiedTickets().size()
                        + " tickets que ocupaban espacios."
        );

        alert.setContentText(
                "Guardado: "
                        + persistedState.savedAt()
                        + System.lineSeparator()
                        + System.lineSeparator()
                        + ticketDescription
                        + System.lineSeparator()
                        + System.lineSeparator()
                        + "Por seguridad, todos se restaurarán "
                        + "como tickets activos."
        );

        alert.getButtonTypes().setAll(
                restoreButton,
                discardButton
        );

        Optional<ButtonType> result =
                alert.showAndWait();

        boolean explicitlyDiscarded =
                result.isPresent()
                        && result.get() == discardButton;

        if (explicitlyDiscarded) {
            ticketStateStore.delete();

            appendLog(
                    "El estado anterior fue descartado manualmente."
            );

            return;
        }

        coordinator.restoreOccupiedTicketsAsActive(
                persistedState.occupiedTickets()
        );

        minecraftUsernameField.setText(
                persistedState.minecraftUsername()
        );

        appendLog(
                "Se restauraron "
                        + persistedState.occupiedTickets().size()
                        + " tickets ocupados."
        );

        appendLog(
                "La vigilancia continúa pausada hasta "
                        + "que pulses Iniciar vigilancia."
        );

        refreshCoordinatorView();
    }

    private void handleInvalidPersistedState(
            RuntimeException exception
    ) {
        ButtonType discardButton = new ButtonType(
                "Descartar archivo",
                ButtonBar.ButtonData.OK_DONE
        );

        ButtonType closeButton = new ButtonType(
                "Cerrar aplicación",
                ButtonBar.ButtonData.CANCEL_CLOSE
        );

        Alert alert = new Alert(Alert.AlertType.ERROR);

        alert.setTitle("Estado guardado inválido");

        alert.setHeaderText(
                "No fue posible recuperar con seguridad "
                        + "los tickets de la sesión anterior."
        );

        alert.setContentText(
                exception.getMessage()
                        + System.lineSeparator()
                        + System.lineSeparator()
                        + "Archivo:"
                        + System.lineSeparator()
                        + ticketStateStore.stateFile()
                        + System.lineSeparator()
                        + System.lineSeparator()
                        + "No inicies la vigilancia hasta confirmar "
                        + "manualmente cuántos tickets siguen activos."
        );

        alert.getButtonTypes().setAll(
                discardButton,
                closeButton
        );

        Optional<ButtonType> result =
                alert.showAndWait();

        if (result.isPresent()
                && result.get() == discardButton) {

            ticketStateStore.delete();

            appendLog(
                    "El archivo de estado inválido fue descartado."
            );

            return;
        }

        Platform.exit();
    }

    private void persistCurrentState() {
        if (ticketStateStore == null) {
            return;
        }

        CoordinatorSnapshot snapshot =
                coordinator.snapshot();

        List<TicketCandidate> occupiedTickets =
                Stream.concat(
                        snapshot.activeTickets().stream(),
                        snapshot.reservedTickets().stream()
                ).toList();

        String minecraftUsername;

        if (monitoringConfiguration != null) {
            minecraftUsername =
                    monitoringConfiguration.minecraftUsername();
        } else {
            minecraftUsername =
                    UsernameNormalizer.normalize(
                            minecraftUsernameField.getText()
                    );
        }

        try {
            ticketStateStore.save(
                    new PersistedTicketState(
                            minecraftUsername,
                            occupiedTickets,
                            Instant.now()
                    )
            );

            lastPersistenceError = null;

        } catch (RuntimeException exception) {
            String message = exception.getMessage();

            if (message == null || message.isBlank()) {
                message =
                        exception.getClass().getSimpleName();
            }

            if (!message.equals(lastPersistenceError)) {
                lastPersistenceError = message;

                appendLog(
                        "ERROR AL GUARDAR ESTADO: "
                                + message
                );
            }
        }
    }

    private void validateConfigurationAgainstRestoredState(
            MonitoringConfiguration configuration
    ) {
        CoordinatorSnapshot snapshot =
                coordinator.snapshot();

        if (snapshot.occupiedSlots() == 0) {
            return;
        }

        TicketCategory lockedCategory =
                snapshot.lockedCategory();

        if (!configuration.acceptsCategory(
                lockedCategory
        )) {
            throw new IllegalArgumentException(
                    "Existen tickets restaurados de categoría "
                            + lockedCategory.displayName()
                            + ". Debes habilitar esa categoría "
                            + "antes de iniciar la vigilancia."
            );
        }
    }

    @Override
    public void stop() throws Exception {
        coordinator.pause();

        persistCurrentState();

        if (monitoringLoop != null) {
            monitoringLoop.close();
        }

        if (browserService != null) {
            browserService.close();
        }
    }
}