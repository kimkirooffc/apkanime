package com.aniflow.util;

import java.awt.AWTException;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;

public class DesktopNotifier {
    private TrayIcon trayIcon;

    public DesktopNotifier() {
        if (SystemTray.isSupported()) {
            try {
                SystemTray tray = SystemTray.getSystemTray();
                BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                trayIcon = new TrayIcon(image, "AniFlow");
                trayIcon.setImageAutoSize(true);
                tray.add(trayIcon);
            } catch (AWTException ignored) {
                trayIcon = null;
            }
        }
    }

    public void notify(String title, String message) {
        if (trayIcon != null) {
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
            return;
        }

        System.out.println("[AniFlow Notification] " + title + " - " + message);
    }
}
