package vt.icl.permission;

import net.minecraft.server.command.ServerCommandSource;

public interface PermissionHandler {
    boolean hasPermission(ServerCommandSource source, String permission);
}
