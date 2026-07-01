package dev.grimstats.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.grimstats.GrimStats;
import dev.grimstats.config.GrimStatsConfig;
import dev.grimstats.http.auth.PasswordHasher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.IOException;

/**
 * In-game admin commands. Requires operator permission (level 3+).
 *
 * <ul>
 *   <li>{@code /grimstats setup <username> <password>} — sets the dashboard admin credentials.</li>
 *   <li>{@code /grimstats info} — prints the dashboard URL.</li>
 * </ul>
 */
public final class GrimStatsCommand {

    private GrimStatsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, GrimStats mod) {
        // 1.21.11 replaced numeric op-level checks with the PermissionCheck system.
        // LEVEL_ADMINS corresponds to the old operator level 3.
        dispatcher.register(Commands.literal("grimstats")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.literal("setup")
                        .then(Commands.argument("username", StringArgumentType.word())
                                .then(Commands.argument("password", StringArgumentType.greedyString())
                                        .executes(ctx -> setup(ctx, mod)))))
                .then(Commands.literal("info")
                        .executes(ctx -> info(ctx, mod))));
    }

    private static int setup(CommandContext<CommandSourceStack> ctx, GrimStats mod) {
        String username = StringArgumentType.getString(ctx, "username");
        String password = StringArgumentType.getString(ctx, "password");
        GrimStatsConfig cfg = mod.getConfigManager().get();
        PasswordHasher.Hashed hashed = PasswordHasher.hash(password, cfg.auth.iterations);

        // This op-only command is the break-glass ROOT recovery path: create or reset a ROOT user.
        GrimStatsConfig.User user = cfg.auth.users.stream()
                .filter(u -> u.username.equalsIgnoreCase(username))
                .findFirst()
                .orElseGet(() -> {
                    GrimStatsConfig.User u = new GrimStatsConfig.User();
                    u.username = username;
                    cfg.auth.users.add(u);
                    return u;
                });
        user.role = "ROOT";
        user.passwordHash = hashed.hashBase64();
        user.passwordSalt = hashed.saltBase64();
        user.iterations = hashed.iterations();

        try {
            mod.getConfigManager().update(cfg);
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("GrimStats: failed to save credentials: " + e.getMessage()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "GrimStats: ROOT user '" + username + "' configured. Log in at the dashboard."), true);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx, GrimStats mod) {
        GrimStatsConfig cfg = mod.getConfigManager().get();
        // 0.0.0.0 binds all interfaces; show the server's address placeholder rather than 0.0.0.0.
        String host = "0.0.0.0".equals(cfg.http.host) ? "<server-ip>" : cfg.http.host;
        String url = "http://" + host + ":" + cfg.http.port;
        ctx.getSource().sendSuccess(() -> Component.literal("GrimStats dashboard: " + url), false);
        return 1;
    }
}
