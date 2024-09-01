package vt.icl;

import net.minecraft.command.CommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.network.packet.s2c.play.PlaySoundIdS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.command.ModIdArgument;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vt.icl.commands.IclCommand;
import vt.icl.config.ConfigManager;
import vt.icl.config.Configuration;
import vt.icl.config.lang.IclTranslationManager;
import vt.icl.mixin.ItemEntityAccessor;
import vt.icl.permission.ForgePermissions;
import vt.icl.permission.PermissionHandler;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static vt.icl.config.lang.IclTranslationManager.createDefaultTranslationFiles;

@Mod(ICL.MODID)
public class ICL {
    public static final String MODID = "icl";
    public static final String MOD_PREFIX = "[" + MODID.toUpperCase() + "] ";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID.toUpperCase());
    public static Configuration config = ConfigManager.getConfig();
    private static Timer TIMER = new Timer(MODID.toUpperCase());
    private static MinecraftServer server;

    public static Map<String, String> translations;
    private static Map<String, String> defaultTranslations;
    public static PermissionHandler permissionHandler;

    public ICL() {
        LOGGER.info("Initializing " + MODID.toUpperCase());
        createDefaultTranslationFiles();
        translations = IclTranslationManager.loadTranslation(config.NotificationLang);
        defaultTranslations = IclTranslationManager.loadTranslation("en_us");
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onPermissionNodesRegister(PermissionGatherEvent.Nodes event) {
        ForgePermissions.init();
        event.addNodes(ForgePermissions.permissionNodesList);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        if (config.UsePermissionsApi) {
            try {
                Class.forName("net.minecraftforge.server.permission.PermissionAPI");
                permissionHandler = new vt.icl.permission.ForgePermissions();
            } catch (ClassNotFoundException e) {
                LOGGER.error("PermissionAPI not found, falling back to default permission system");
            }
        } else {
            LOGGER.info("Using default permission system");
            permissionHandler = null;
        }
        server = event.getServer();
        if (config.Delay > 0) {
            doItemClean(server);
            if (config.doShowNotification) {
                setupNotificationTimers(server);
            }
            if (config.doNotificationCountdown) {
                setupCountdownTimer(server);
            }
        } else {
            LOGGER.info(MODID.toUpperCase() + " disabled, delay is less than 0");
        }
        LOGGER.info(MODID.toUpperCase() + " initialized");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        TIMER.cancel();
        LOGGER.info(MODID.toUpperCase() + " stopped");
    }

    @SubscribeEvent
    public void onCommandRegister(RegisterCommandsEvent event) {
        IclCommand.register(event.getDispatcher());
    }

    public static void doItemClean(MinecraftServer server) {
        long delay = config.Delay;
        if (delay < 0) {
            return;
        }
        TIMER.schedule(new TimerTask() {
            @Override
            public void run() {

                if (config.doShowNotification) {
                    setupNotificationTimers(server);
                }
                if (config.doNotificationCountdown) {
                    setupCountdownTimer(server);
                }

                clearItems(server);

                TIMER.purge();

                doItemClean(server);
            }
        }, delay * 1000);
    }

    private static void setupNotificationTimers(MinecraftServer server) {
        for (int i = 0; i < config.NotificationTimes; i++) {
            int finalI = i;
            long delay = config.Delay - config.NotificationStart + config.NotificationDelay * i;
            if (delay < 0  || delay > config.Delay) {
                continue;
            }
            TIMER.schedule(new TimerTask() {
                @Override
                public void run() {
                    LOGGER.info("{} seconds left", "Clearing items " + (config.NotificationStart - config.NotificationDelay * finalI));
                    for (var player : server.getPlayerManager().getPlayerList()) {
                        MutableText message = Text.literal(MOD_PREFIX  + IclTranslate("text.icl.notification", (config.NotificationStart - config.NotificationDelay * finalI)) + " ")
                                .formatted(Formatting.valueOf(config.NotificationColor));
                        IclMessage(player, message);
                        try {
                            if (config.doNotificationSound) {
                                IclPlaysound(player, false);
                            }
                        } catch (Exception e) {
                            player.sendMessage(Text.literal(e.getMessage()).formatted(Formatting.valueOf(config.NotificationColor)));
                            LOGGER.error("Failed to play sound: " + e.getMessage());
                        }
                    }
                }
            }, delay * 1000);
        }
    }

    private static void IclMessage(ServerPlayerEntity player, MutableText message) {
        if (permissionCheckforCancel(player.getCommandSource())) {
            message.append(Text.literal(IclTranslate("text.icl.cancel.button"))
                    .styled(style -> style.withClickEvent(IclCancelEvent()))
                    .formatted(Formatting.RED));
        }
        player.sendMessage(message);
    }

    private static void setupCountdownTimer(MinecraftServer server) {
        long countdownstart = config.CountdownStart;
        if (countdownstart > config.Delay) {
            countdownstart = config.Delay;
        }
        long delay = config.Delay - countdownstart;
        if (delay < 0 || delay > config.Delay) {
            return;
        }
        if (countdownstart < 0) {
            return;
        }


        long finalCountdownstart = countdownstart;
        TIMER.schedule(new TimerTask() {
            @Override
            public void run() {
                for (int i = 0; i < finalCountdownstart; i++) {
                    int finalI = i;
                    TIMER.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            LOGGER.info("{} seconds left", "Clearing items " + (finalCountdownstart - finalI));
                            for (var player : server.getPlayerManager().getPlayerList()) {
                                MutableText message = Text.literal(MOD_PREFIX  + IclTranslate("text.icl.countdown", (finalCountdownstart - finalI)) + " ")
                                        .formatted(Formatting.valueOf(config.NotificationColor));
                                IclMessage(player, message);
                            }
                        }
                    }, finalI * 1000L);
                }
            }
        }, (delay) * 1000);
    }

    public static void clearItems(MinecraftServer server) {
        LOGGER.info("Clearing items");
        for (var player : server.getPlayerManager().getPlayerList()) {
            if (config.doShowNotification) {
                player.sendMessage(Text.literal(MOD_PREFIX + IclTranslate("text.icl.clear")).formatted(Formatting.valueOf(config.NotificationColor)));
                try {
                    if (config.doLastNotificationSound) {
                        IclPlaysound(player, true);
                    }
                } catch (Exception e) {
                    player.sendMessage(Text.literal(e.getMessage()).formatted(Formatting.valueOf(config.NotificationColor)));
                    LOGGER.error("Failed to play sound: " + e.getMessage());
                }
            }
        }
        int count = 0;
        for (var world : server.getWorlds()) {
            for (var entity : world.getEntitiesByType(TypeFilter.instanceOf(ItemEntity.class), Entity::isAlive)) {
                if(config.preserveNoPickupItems) {
                    ItemEntityAccessor accessor = (ItemEntityAccessor) entity;
                    if (accessor.getPickupDelay() == Short.MAX_VALUE) {
                        continue;
                    }
                }
                if (config.preserveNoDespawnItems) {
                    if (entity.getItemAge() == Short.MIN_VALUE) {
                        continue;
                    }
                }
                count += entity.getStack().getCount();
                entity.remove(Entity.RemovalReason.DISCARDED);
            }
        }
        for (var player : server.getPlayerManager().getPlayerList()) {
            if (config.doShowNotification) {
                player.sendMessage(Text.literal(MOD_PREFIX  + IclTranslate("text.icl.clear.finish", count)).formatted(Formatting.valueOf(config.NotificationColor)));
            }
        }
        LOGGER.info("Items cleared: {}", count);
    }

    public static void reloadIcl() {
        TIMER.cancel();
        TIMER = new Timer(MODID.toUpperCase());
        config = ConfigManager.getConfig();
        if (config.Delay > 0) {
            doItemClean(server);
            if (config.doShowNotification) {
                setupNotificationTimers(server);
            }
            if (config.doNotificationCountdown) {
                setupCountdownTimer(server);
            }
        } else {
            LOGGER.info(MODID.toUpperCase() + " disabled, delay is less than 0");
        }
    }

    public static void CancelIcl(int tempDelay) {
        TIMER.cancel();
        TIMER = new Timer(MODID.toUpperCase());
        if (tempDelay > 0) {
            TIMER.schedule(new TimerTask() {
                @Override
                public void run() {
                    config = ConfigManager.getConfig();
                    if (config.Delay > 0) {
                        doItemClean(server);
                        if (config.doShowNotification) {
                            setupNotificationTimers(server);
                        }
                        if (config.doNotificationCountdown) {
                            setupCountdownTimer(server);
                        }
                    } else {
                        LOGGER.info(MODID.toUpperCase() + " disabled, delay is less than 0");
                    }
                }
            }, tempDelay * 1000L);
        } else {
            config = ConfigManager.getConfig();
            if (config.Delay > 0) {
                doItemClean(server);
                if (config.doShowNotification) {
                    setupNotificationTimers(server);
                }
                if (config.doNotificationCountdown) {
                    setupCountdownTimer(server);
                }
            } else {
                LOGGER.info(MODID.toUpperCase() + " disabled, delay is less than 0");
            }
        }
    }

    public static ClickEvent IclCancelEvent() {
        return new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/icl cancel");
    }

    public static String IclTranslate(String key, Object... args) {
        String translation = null;
        if (translations != null) {
            translation = translations.get(key);
        }
        if (translation == null && defaultTranslations != null) {
            translation = defaultTranslations.get(key);
        }
        if (translation != null) {
            if (args != null && args.length > 0) {
                return String.format(translation, args);
            } else {
                return translation;
            }
        } else {
            return key;
        }
    }

    public static void IclPlaysound(ServerPlayerEntity player, boolean isLastSound) {
        Vec3d vec3d;
        double e = player.getX();
        double f = player.getY();
        double g = player.getZ();
        double h = e * e + f * f + g * g;
        double k = Math.sqrt(h);
        vec3d = new Vec3d(player.getX() + e / k * 2.0, player.getY() + f / k * 2.0, player.getZ() + g / k * 2.0);
        Identifier sound;
        if (isLastSound) {
            sound = new Identifier(config.LastNotificationSound);
        } else {
            sound = new Identifier(config.NotificationSound);
        }
        player.networkHandler.sendPacket(new PlaySoundIdS2CPacket(sound, SoundCategory.PLAYERS, vec3d, 1, 1, 1));
    }

    private static boolean permissionCheckforCancel(ServerCommandSource source) {
        if (ICL.permissionHandler != null) {
            return ICL.permissionHandler.hasPermission(source, ICL.MODID + "." + "cancel");
        } else {
            return !config.RequireOpCancel || source.hasPermissionLevel(2);
        }
    }

}
