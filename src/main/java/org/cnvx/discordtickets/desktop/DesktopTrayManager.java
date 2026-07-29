package org.cnvx.discordtickets.desktop;

import javax.imageio.ImageIO;
import java.awt.AWTException;
import java.awt.EventQueue;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public final class DesktopTrayManager
        implements AutoCloseable {

    private SystemTray systemTray;
    private TrayIcon trayIcon;

    private MenuItem pauseResumeItem;
    private MenuItem stopMonitoringItem;

    public boolean install(
            Runnable openWindowAction,
            Runnable pauseResumeAction,
            Runnable stopMonitoringAction,
            Runnable exitAction
    ) {
        Objects.requireNonNull(openWindowAction);
        Objects.requireNonNull(pauseResumeAction);
        Objects.requireNonNull(stopMonitoringAction);
        Objects.requireNonNull(exitAction);

        if (!SystemTray.isSupported()) {
            return false;
        }

        Image iconImage = loadIcon();

        PopupMenu popupMenu = new PopupMenu();

        MenuItem openItem = new MenuItem(
                "Abrir CarryAssistant"
        );

        pauseResumeItem = new MenuItem(
                "Pausar vigilancia"
        );

        stopMonitoringItem = new MenuItem(
                "Detener vigilancia"
        );

        MenuItem exitItem = new MenuItem(
                "Salir completamente"
        );

        pauseResumeItem.setEnabled(false);
        stopMonitoringItem.setEnabled(false);

        openItem.addActionListener(
                event -> openWindowAction.run()
        );

        pauseResumeItem.addActionListener(
                event -> pauseResumeAction.run()
        );

        stopMonitoringItem.addActionListener(
                event -> stopMonitoringAction.run()
        );

        exitItem.addActionListener(
                event -> exitAction.run()
        );

        popupMenu.add(openItem);
        popupMenu.addSeparator();
        popupMenu.add(pauseResumeItem);
        popupMenu.add(stopMonitoringItem);
        popupMenu.addSeparator();
        popupMenu.add(exitItem);

        trayIcon = new TrayIcon(
                iconImage,
                "CarryAssistant",
                popupMenu
        );

        trayIcon.setImageAutoSize(true);

        /*
         * En Windows, la acción principal normalmente se produce
         * al hacer doble clic en el icono.
         */
        trayIcon.addActionListener(
                event -> openWindowAction.run()
        );

        systemTray = SystemTray.getSystemTray();

        try {
            systemTray.add(trayIcon);
            return true;

        } catch (AWTException exception) {
            trayIcon = null;
            systemTray = null;

            throw new IllegalStateException(
                    "No fue posible agregar el icono "
                            + "a la bandeja del sistema.",
                    exception
            );
        }
    }

    public boolean isInstalled() {
        return trayIcon != null;
    }

    public void updateMonitoringState(
            boolean monitoringStarted,
            boolean paused
    ) {
        if (!isInstalled()) {
            return;
        }

        EventQueue.invokeLater(() -> {
            pauseResumeItem.setEnabled(
                    monitoringStarted
            );

            stopMonitoringItem.setEnabled(
                    monitoringStarted
            );

            pauseResumeItem.setLabel(
                    paused
                            ? "Reanudar vigilancia"
                            : "Pausar vigilancia"
            );
        });
    }

    public void showInformation(
            String title,
            String message
    ) {
        if (!isInstalled()) {
            return;
        }

        EventQueue.invokeLater(() ->
                trayIcon.displayMessage(
                        title,
                        message,
                        TrayIcon.MessageType.INFO
                )
        );
    }

    public void showWarning(
            String title,
            String message
    ) {
        if (!isInstalled()) {
            return;
        }

        EventQueue.invokeLater(() ->
                trayIcon.displayMessage(
                        title,
                        message,
                        TrayIcon.MessageType.WARNING
                )
        );
    }

    private Image loadIcon() {
        try (InputStream input =
                     DesktopTrayManager.class.getResourceAsStream(
                             "/images/icon.png"
                     )) {

            if (input == null) {
                throw new IllegalStateException(
                        "No se encontró /images/icon.png"
                );
            }

            Image image = ImageIO.read(input);

            if (image == null) {
                throw new IllegalStateException(
                        "El archivo icon.png no contiene "
                                + "una imagen válida."
                );
            }

            return image;

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "No fue posible cargar el icono "
                            + "de la bandeja.",
                    exception
            );
        }
    }

    @Override
    public void close() {
        if (systemTray != null && trayIcon != null) {
            systemTray.remove(trayIcon);
        }

        trayIcon = null;
        systemTray = null;
    }
}