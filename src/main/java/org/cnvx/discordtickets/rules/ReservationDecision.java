package org.cnvx.discordtickets.rules;

/**
 * RESERVED
 * El ticket obtuvo un espacio y puede intentarse el clic.
 *
 * PAUSED
 * La vigilancia está pausada.
 *
 * DUPLICATE
 * El ticket ya fue detectado, reservado, activado o terminado.
 *
 * LIMIT_REACHED
 * Ya existen 3 tickets activos o reservados.
 *
 * CATEGORY_MISMATCH
 * La categoría no coincide con la categoría actualmente bloqueada.
 */

public enum ReservationDecision {

    RESERVED,

    PAUSED,

    DUPLICATE,

    LIMIT_REACHED,

    CATEGORY_MISMATCH
}
