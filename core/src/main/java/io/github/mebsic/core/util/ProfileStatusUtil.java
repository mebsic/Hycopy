package io.github.mebsic.core.util;

import io.github.mebsic.core.model.ProfileStatus;
import org.bukkit.ChatColor;

public final class ProfileStatusUtil {
    private ProfileStatusUtil() {
    }

    public static boolean hasNotice(ProfileStatus status, boolean vanished) {
        return vanished || (status != null && status != ProfileStatus.ONLINE);
    }

    public static String formatNotice(ProfileStatus status, boolean vanished) {
        if (!hasNotice(status, vanished)) {
            return "";
        }
        String message = ChatColor.WHITE + "You are currently ";
        if (vanished) {
            message += ChatColor.RED + "VANISHED";
            if (status != null && status != ProfileStatus.ONLINE) {
                message += ChatColor.WHITE + ", " + ChatColor.RED + statusLabel(status);
            }
            return message;
        }
        return message + ChatColor.RED + statusLabel(status);
    }

    private static String statusLabel(ProfileStatus status) {
        if (status == ProfileStatus.APPEAR_OFFLINE) {
            return "APPEARING OFFLINE";
        }
        return status == null ? "" : status.name();
    }
}
