package vt.icl.permission;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;
import vt.icl.ICL;

import java.util.List;

public class ForgePermissions implements PermissionHandler {

    public static final PermissionNode<Boolean> FORCECLEAN = new PermissionNode<>(
            new Identifier(ICL.MODID, "forceclean"),
            PermissionTypes.BOOLEAN,
            (player, playerUUID, context) -> false
    );
    public static final PermissionNode<Boolean> RELOAD = new PermissionNode<>(
            new Identifier(ICL.MODID, "reload"),
            PermissionTypes.BOOLEAN,
            (player, playerUUID, context) -> false
    );
    public static final PermissionNode<Boolean> CONFIG = new PermissionNode<>(
            new Identifier(ICL.MODID, "config"),
            PermissionTypes.BOOLEAN,
            (player, playerUUID, context) -> false
    );
    public static final PermissionNode<Boolean> CANCEL = new PermissionNode<>(
            new Identifier(ICL.MODID, "cancel"),
            PermissionTypes.BOOLEAN,
            (player, playerUUID, context) -> false
    );

    public static List<PermissionNode<?>> permissionNodesList = List.of(
           FORCECLEAN, RELOAD, CONFIG, CANCEL
    );

    @Override
    public boolean hasPermission(ServerCommandSource source, String permission) {
        PermissionNode<Boolean> permissionNode = (PermissionNode<Boolean>) PermissionAPI.getRegisteredNodes().stream()
                .filter(p -> p.getNodeName().equals(permission))
                .findFirst()
                .orElse(null);
        if (source.hasPermissionLevel(4)) {
            return true;
        }
        if (source.getPlayer() == null) {
            return false;
        }
        if (permissionNode == null) {
            ICL.LOGGER.warn("Permission node {} not found", permission);
            return false;
        }
        return PermissionAPI.getPermission(source.getPlayer(), permissionNode);
    }

    public static void init() {
        FORCECLEAN.setInformation(Text.of("Force Clean"), Text.of("Allows the player to force clean the world"));
        RELOAD.setInformation(Text.of("Reload"), Text.of("Allows the player to reload the config"));
        CONFIG.setInformation(Text.of("Config"), Text.of("Allows the player to view the config"));
        CANCEL.setInformation(Text.of("Cancel"), Text.of("Allows the player to cancel the current operation"));
    }
}
