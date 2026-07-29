package org.cnvx.discordtickets.desktop;

import com.sun.jna.Native;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.util.Objects;
import java.util.function.IntUnaryOperator;

/**
 * Solicita a Windows que no suspenda automáticamente el sistema.
 *
 * No mantiene encendida la pantalla y tampoco impide una suspensión
 * solicitada explícitamente por el usuario.
 *
 * Las operaciones activate() y release() deben ejecutarse siempre
 * desde el mismo hilo. TicketAssistantApp las invocará desde el
 * JavaFX Application Thread.
 */
public final class WindowsSystemAwakeController
        implements AutoCloseable {

    public static final int ES_SYSTEM_REQUIRED = 0x00000001;
    public static final int ES_CONTINUOUS = 0x80000000;

    private interface Kernel32PowerApi extends StdCallLibrary {

        Kernel32PowerApi INSTANCE = Native.load(
                "kernel32",
                Kernel32PowerApi.class,
                W32APIOptions.DEFAULT_OPTIONS
        );

        int SetThreadExecutionState(int executionState);
    }

    private final IntUnaryOperator executionStateFunction;

    private Thread ownerThread;
    private boolean active;

    public WindowsSystemAwakeController() {
        this(
                Kernel32PowerApi.INSTANCE
                        ::SetThreadExecutionState
        );
    }

    /*
     * Constructor visible para las pruebas del mismo paquete.
     */
    public WindowsSystemAwakeController(
            IntUnaryOperator executionStateFunction
    ) {
        this.executionStateFunction = Objects.requireNonNull(
                executionStateFunction,
                "La función nativa es obligatoria."
        );
    }

    public synchronized void activate() {
        ensureOwnerThread();

        if (active) {
            return;
        }

        int previousState = executionStateFunction.applyAsInt(
                ES_CONTINUOUS | ES_SYSTEM_REQUIRED
        );

        if (previousState == 0) {
            throw new IllegalStateException(
                    "Windows rechazó la solicitud para mantener "
                            + "el sistema despierto. Código nativo: "
                            + Native.getLastError()
            );
        }

        active = true;
    }

    public synchronized void release() {
        ensureOwnerThread();

        if (!active) {
            return;
        }

        int previousState = executionStateFunction.applyAsInt(
                ES_CONTINUOUS
        );

        if (previousState == 0) {
            throw new IllegalStateException(
                    "Windows no pudo liberar la protección contra "
                            + "suspensión. Código nativo: "
                            + Native.getLastError()
            );
        }

        active = false;
    }

    public synchronized boolean isActive() {
        return active;
    }

    private void ensureOwnerThread() {
        Thread currentThread = Thread.currentThread();

        if (ownerThread == null) {
            ownerThread = currentThread;
            return;
        }

        if (ownerThread != currentThread) {
            throw new IllegalStateException(
                    "La protección contra suspensión debe modificarse "
                            + "siempre desde el mismo hilo."
            );
        }
    }

    @Override
    public synchronized void close() {
        if (active) {
            release();
        }
    }
}