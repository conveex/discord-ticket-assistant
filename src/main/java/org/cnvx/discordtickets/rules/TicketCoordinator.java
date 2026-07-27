package org.cnvx.discordtickets.rules;

import org.cnvx.discordtickets.model.TicketCandidate;
import org.cnvx.discordtickets.model.TicketCategory;
import org.cnvx.discordtickets.model.TicketId;

import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class TicketCoordinator {

    public static final int DEFAULT_MAXIMUM_TICKETS = 3;

    private final int maximumTickets;

    /*
     * Tickets que ya obtuvieron un espacio, pero cuyo clic todavía
     * no ha sido confirmado.
     */
    private final Map<TicketId, TicketCandidate> reservedTickets =
            new LinkedHashMap<>();

    /*
     * Tickets cuyo clic fue confirmado como reclamado por el usuario.
     */
    private final Map<TicketId, TicketCandidate> activeTickets =
            new LinkedHashMap<>();

    /*
     * Tickets que ya no deben volver a procesarse:
     * completados o reclamados por otra persona.
     */
    private final Set<TicketId> terminalTicketIds =
            new LinkedHashSet<>();

    /*
     * La aplicación comienza pausada. Más adelante el botón
     * "Iniciar vigilancia" llamará a resume().
     */
    private boolean paused = true;

    /*
     * null significa que todavía no existe una categoría bloqueada.
     */
    private TicketCategory lockedCategory;

    public TicketCoordinator() {
        this(DEFAULT_MAXIMUM_TICKETS);
    }

    public TicketCoordinator(int maximumTickets) {
        if (maximumTickets <= 0) {
            throw new IllegalArgumentException(
                    "El máximo de tickets debe ser mayor que cero."
            );
        }

        this.maximumTickets = maximumTickets;
    }

    /**
     * Intenta apartar un lugar para un ticket.
     *
     * La operación completa es atómica: comprobación de reglas,
     * bloqueo de categoría y creación de la reserva.
     */
    public synchronized ReservationResult reserve(
            TicketCandidate ticket
    ) {
        Objects.requireNonNull(
                ticket,
                "El ticket candidato es obligatorio."
        );

        if (paused) {
            return result(ReservationDecision.PAUSED);
        }

        if (isKnown(ticket.id())) {
            return result(ReservationDecision.DUPLICATE);
        }

        if (occupiedSlots() >= maximumTickets) {
            return result(ReservationDecision.LIMIT_REACHED);
        }

        if (lockedCategory != null
                && lockedCategory != ticket.category()) {
            return result(ReservationDecision.CATEGORY_MISMATCH);
        }

        if (lockedCategory == null) {
            lockedCategory = ticket.category();
        }

        reservedTickets.put(ticket.id(), ticket);

        verifyInvariants();

        return result(ReservationDecision.RESERVED);
    }

    /**
     * Indica que el clic tuvo éxito y que la confirmación de Discord
     * muestra a nuestro usuario como reclamante.
     */
    public synchronized boolean confirmReservation(TicketId ticketId) {
        Objects.requireNonNull(
                ticketId,
                "El identificador del ticket es obligatorio."
        );

        TicketCandidate ticket = reservedTickets.remove(ticketId);

        if (ticket == null) {
            return false;
        }

        activeTickets.put(ticketId, ticket);

        verifyInvariants();

        return true;
    }

    /**
     * Cancela una reserva por un fallo técnico temporal.
     *
     * El ticket podrá intentarse nuevamente si vuelve a detectarse.
     */
    public synchronized boolean cancelReservation(TicketId ticketId) {
        Objects.requireNonNull(
                ticketId,
                "El identificador del ticket es obligatorio."
        );

        TicketCandidate removed = reservedTickets.remove(ticketId);

        if (removed == null) {
            return false;
        }

        unlockCategoryWhenEmpty();
        verifyInvariants();

        return true;
    }

    /**
     * Indica que otra persona reclamó el ticket antes que nosotros.
     *
     * Se libera el espacio, pero el mismo ticket ya no podrá
     * procesarse nuevamente.
     */
    public synchronized boolean markClaimedByOther(TicketId ticketId) {
        Objects.requireNonNull(
                ticketId,
                "El identificador del ticket es obligatorio."
        );

        TicketCandidate removed = reservedTickets.remove(ticketId);

        if (removed == null) {
            return false;
        }

        terminalTicketIds.add(ticketId);

        unlockCategoryWhenEmpty();
        verifyInvariants();

        return true;
    }

    /**
     * Finalización manual desde la interfaz.
     */
    public synchronized boolean completeActiveTicket(
            TicketId ticketId
    ) {
        Objects.requireNonNull(
                ticketId,
                "El identificador del ticket es obligatorio."
        );

        TicketCandidate removed = activeTickets.remove(ticketId);

        if (removed == null) {
            return false;
        }

        terminalTicketIds.add(ticketId);

        unlockCategoryWhenEmpty();
        verifyInvariants();

        return true;
    }

    public synchronized void pause() {
        paused = true;
    }

    public synchronized void resume() {
        paused = false;
    }

    public synchronized boolean isPaused() {
        return paused;
    }

    public synchronized CoordinatorSnapshot snapshot() {
        return new CoordinatorSnapshot(
                paused,
                maximumTickets,
                lockedCategory,
                reservedTickets.values().stream().toList(),
                activeTickets.values().stream().toList(),
                terminalTicketIds.size()
        );
    }

    private boolean isKnown(TicketId ticketId) {
        return reservedTickets.containsKey(ticketId)
                || activeTickets.containsKey(ticketId)
                || terminalTicketIds.contains(ticketId);
    }

    private int occupiedSlots() {
        return reservedTickets.size() + activeTickets.size();
    }

    private ReservationResult result(
            ReservationDecision decision
    ) {
        return new ReservationResult(
                decision,
                lockedCategory,
                occupiedSlots(),
                maximumTickets
        );
    }

    private void unlockCategoryWhenEmpty() {
        if (occupiedSlots() == 0) {
            lockedCategory = null;
        }
    }

    /**
     * Comprueba errores internos inmediatamente.
     *
     * Si alguna modificación futura rompe una regla, preferimos
     * obtener una excepción clara en lugar de aceptar un cuarto
     * ticket o mezclar categorías silenciosamente.
     */
    private void verifyInvariants() {
        int occupied = occupiedSlots();

        if (occupied > maximumTickets) {
            throw new IllegalStateException(
                    "La ocupación superó el máximo permitido."
            );
        }

        if (occupied == 0) {
            if (lockedCategory != null) {
                throw new IllegalStateException(
                        "Existe una categoría bloqueada sin tickets ocupados."
                );
            }

            return;
        }

        if (lockedCategory == null) {
            throw new IllegalStateException(
                    "Existen tickets ocupados sin categoría bloqueada."
            );
        }

        for (TicketCandidate ticket : reservedTickets.values()) {
            verifyTicketCategory(ticket);
        }

        for (TicketCandidate ticket : activeTickets.values()) {
            verifyTicketCategory(ticket);
        }

        for (TicketId ticketId : reservedTickets.keySet()) {
            if (activeTickets.containsKey(ticketId)) {
                throw new IllegalStateException(
                        "Un ticket no puede estar reservado y activo "
                                + "al mismo tiempo: "
                                + ticketId
                );
            }
        }
    }

    /**
     * Restaura tickets de una sesión anterior.
     *
     * Todos se colocan como activos por seguridad, incluso si antes
     * del cierre alguno todavía estaba en estado reservado.
     */
    public synchronized void restoreOccupiedTicketsAsActive(
            List<TicketCandidate> tickets
    ) {
        Objects.requireNonNull(
                tickets,
                "Los tickets restaurados son obligatorios."
        );

        if (!reservedTickets.isEmpty()
                || !activeTickets.isEmpty()) {
            throw new IllegalStateException(
                    "No se puede restaurar estado sobre "
                            + "un coordinador que ya tiene tickets."
            );
        }

        if (tickets.size() > maximumTickets) {
            throw new IllegalArgumentException(
                    "El estado restaurado supera el máximo de "
                            + maximumTickets
                            + " tickets."
            );
        }

        if (tickets.isEmpty()) {
            lockedCategory = null;
            verifyInvariants();
            return;
        }

        TicketCategory restoredCategory =
                tickets.getFirst().category();

        Set<TicketId> restoredIds =
                new HashSet<>();

        for (TicketCandidate ticket : tickets) {
            Objects.requireNonNull(
                    ticket,
                    "No puede restaurarse un ticket nulo."
            );

            if (ticket.category() != restoredCategory) {
                throw new IllegalArgumentException(
                        "El estado restaurado mezcla categorías: "
                                + restoredCategory
                                + " y "
                                + ticket.category()
                );
            }

            if (!restoredIds.add(ticket.id())) {
                throw new IllegalArgumentException(
                        "El estado restaurado contiene un duplicado: "
                                + ticket.id()
                );
            }

            if (terminalTicketIds.contains(ticket.id())) {
                throw new IllegalArgumentException(
                        "No puede restaurarse un ticket terminado: "
                                + ticket.id()
                );
            }

            activeTickets.put(ticket.id(), ticket);
        }

        lockedCategory = restoredCategory;

        /*
         * No llamamos a resume(). La aplicación debe continuar
         * pausada hasta que el usuario pulse Iniciar vigilancia.
         */
        verifyInvariants();
    }

    private void verifyTicketCategory(TicketCandidate ticket) {
        if (ticket.category() != lockedCategory) {
            throw new IllegalStateException(
                    "El ticket "
                            + ticket.id()
                            + " no coincide con la categoría bloqueada "
                            + lockedCategory
            );
        }
    }
}