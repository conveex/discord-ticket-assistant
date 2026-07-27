package org.cnvx.discordtickets.browser;

public enum TicketClaimStatus {

    /**
     * Discord confirmó que el usuario configurado reclamó el ticket.
     */
    CLAIMED_BY_US,

    /**
     * Discord confirmó que otra persona lo reclamó.
     */
    CLAIMED_BY_OTHER,

    /**
     * El canal abrió, pero no se encontró el botón esperado.
     */
    BUTTON_NOT_FOUND,

    /**
     * Se encontró el botón, pero el clic no pudo completarse.
     */
    CLICK_FAILED,

    /**
     * El clic se realizó, pero no apareció confirmación.
     *
     * Es un resultado incierto: quizá el clic sí funcionó.
     */
    CONFIRMATION_TIMEOUT,

    /**
     * Fallo antes de completar el clic: navegación, carga del canal,
     * DOM inesperado, etcétera.
     */
    TECHNICAL_FAILURE
}
