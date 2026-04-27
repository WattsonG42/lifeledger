package lifeledger;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lifeledger.config.ServerConfig;
import lifeledger.stocks.StockStore;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.Collection;
import java.util.UUID;
import java.util.function.BiConsumer;

public class LifeledgerCommands {

    private static boolean isOp(CommandSourceStack src) {
        return src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    public static void register(StockStore stockStore, PvpTracker pvpTracker) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                Commands.literal("lifeledger")
                    .then(Commands.literal("testimpact")
                        .requires(LifeledgerCommands::isOp)
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayer();
                            if (player != null && ServerPlayNetworking.canSend(player, DeathSentencePayload.TYPE)) {
                                ServerPlayNetworking.send(player, new DeathSentencePayload());
                            }
                            return 1;
                        }))
                    .then(Commands.literal("stocks")
                        .executes(ctx -> cmdStocksSelf(ctx, stockStore))
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(ctx -> cmdStocksOther(ctx, stockStore))))
                    .then(Commands.literal("default")
                        .requires(LifeledgerCommands::isOp)
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                            .executes(ctx -> cmdDefault(ctx, stockStore))))
                    .then(Commands.literal("set")
                        .requires(LifeledgerCommands::isOp)
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                            .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(ctx -> cmdSet(ctx, stockStore)))))
                    .then(Commands.literal("give")
                        .requires(LifeledgerCommands::isOp)
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                            .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> cmdGive(ctx, stockStore)))))
                    .then(Commands.literal("take")
                        .requires(LifeledgerCommands::isOp)
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                            .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> cmdTake(ctx, stockStore)))))
                    .then(Commands.literal("get")
                        .requires(LifeledgerCommands::isOp)
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                            .executes(ctx -> cmdGet(ctx, stockStore))))
                    .then(Commands.literal("config")
                        .requires(LifeledgerCommands::isOp)
                        .then(configToggle("mobs",          (cfg, v) -> cfg.countMobDeaths = v))
                        .then(configToggle("pvp",           (cfg, v) -> cfg.countPvpDeaths = v))
                        .then(configToggle("fall",          (cfg, v) -> cfg.countFallDamage = v))
                        .then(configToggle("void",          (cfg, v) -> cfg.countVoidDeaths = v))
                        .then(configToggle("dragon",        (cfg, v) -> cfg.countEnderDragonDeaths = v))
                        .then(configToggle("wither",        (cfg, v) -> cfg.countWitherDeaths = v))
                        .then(configToggle("elderguardian", (cfg, v) -> cfg.countElderGuardianDeaths = v))
                        .then(Commands.literal("deathsentence")
                            .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean value = BoolArgumentType.getBool(ctx, "value");
                                    Lifeledger.CONFIG.getConfig().deathSentenceEnabled = value;
                                    Lifeledger.CONFIG.save();
                                    if (!value) pvpTracker.clearAllMarks();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                        "[LifeLedger] deathsentence set to " + value), true);
                                    return 1;
                                })))
                        .then(configToggle("explosion",     (cfg, v) -> cfg.countExplosionDeaths = v))
                        .then(configToggle("anvil",         (cfg, v) -> cfg.countAnvilDeaths = v))
                        .then(configToggle("fire",          (cfg, v) -> cfg.countFireDeaths = v))
                        .then(configToggle("drown",         (cfg, v) -> cfg.countDrownDeaths = v))
                        .then(configToggle("freeze",        (cfg, v) -> cfg.countFreezeDeaths = v))
                        .then(configToggle("magic",         (cfg, v) -> cfg.countMagicDeaths = v))
                        .then(configToggle("suffocation",   (cfg, v) -> cfg.countSuffocationDeaths = v))
                        .then(Commands.literal("deathsentencewindow")
                            .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                .executes(LifeledgerCommands::cmdDeathSentenceWindow))))
            )
        );
    }

    private static int cmdDefault(CommandContext<CommandSourceStack> ctx, StockStore stockStore) {
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        Lifeledger.CONFIG.getConfig().defaultStocks = amount;
        Lifeledger.CONFIG.save();
        broadcast(ctx, stockStore);
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Default stocks set to " + amount + " (applies to new players only)"), true);
        return amount;
    }

    private static boolean hasJoined(UUID uuid, CommandContext<CommandSourceStack> ctx, StockStore stockStore) {
        return stockStore.hasPlayer(uuid) || ctx.getSource().getServer().getPlayerList().getPlayer(uuid) != null;
    }

    private static int cmdSet(CommandContext<CommandSourceStack> ctx, StockStore stockStore) throws CommandSyntaxException {
        NameAndId profile = singleProfile(ctx);
        UUID uuid = profile.id();
        String name = profile.name();
        if (!hasJoined(uuid, ctx, stockStore)) {
            ctx.getSource().sendFailure(Component.literal(name + " has not joined this server."));
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        if (amount <= 0) {
            ctx.getSource().sendSuccess(() -> Component.literal(name + " eliminated (0 stocks)"), true);
            Lifeledger.eliminate(uuid, name, stockStore, ctx.getSource().getServer(), Lifeledger.CONFIG.getConfig().banMessage);
            return 0;
        }
        stockStore.setStocks(uuid, amount);
        stockStore.save();
        broadcast(ctx, stockStore);
        ctx.getSource().sendSuccess(() -> Component.literal(name + "'s stocks set to " + amount), true);
        return amount;
    }

    private static int cmdGive(CommandContext<CommandSourceStack> ctx, StockStore stockStore) throws CommandSyntaxException {
        NameAndId profile = singleProfile(ctx);
        UUID uuid = profile.id();
        String name = profile.name();
        if (!hasJoined(uuid, ctx, stockStore)) {
            ctx.getSource().sendFailure(Component.literal(name + " has not joined this server."));
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        int current = Math.max(0, stockStore.getStocks(uuid));
        int total = current + amount;
        stockStore.setStocks(uuid, total);
        stockStore.save();
        broadcast(ctx, stockStore);
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Gave " + amount + " stock(s) to " + name + " — now at " + total), true);
        return total;
    }

    private static int cmdTake(CommandContext<CommandSourceStack> ctx, StockStore stockStore) throws CommandSyntaxException {
        NameAndId profile = singleProfile(ctx);
        UUID uuid = profile.id();
        String name = profile.name();
        if (!hasJoined(uuid, ctx, stockStore)) {
            ctx.getSource().sendFailure(Component.literal(name + " has not joined this server."));
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        int current = Math.max(0, stockStore.getStocks(uuid));
        int total = Math.max(0, current - amount);
        if (total <= 0) {
            ctx.getSource().sendSuccess(() -> Component.literal(name + " eliminated (stocks depleted)"), true);
            Lifeledger.eliminate(uuid, name, stockStore, ctx.getSource().getServer(), Lifeledger.CONFIG.getConfig().banMessage);
            return 0;
        }
        stockStore.setStocks(uuid, total);
        stockStore.save();
        broadcast(ctx, stockStore);
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Took " + (current - total) + " stock(s) from " + name + " — now at " + total), true);
        return total;
    }

    private static int cmdGet(CommandContext<CommandSourceStack> ctx, StockStore stockStore) throws CommandSyntaxException {
        NameAndId profile = singleProfile(ctx);
        int stocks = stockStore.getStocks(profile.id());
        ctx.getSource().sendSuccess(() -> Component.literal(
            profile.name() + " has " + stocks + " stock(s)"), false);
        return stocks;
    }

    private static int cmdStocksSelf(CommandContext<CommandSourceStack> ctx, StockStore stockStore) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int stocks = stockStore.getStocks(player.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal("You have " + stocks + " stock(s)"), false);
        return stocks;
    }

    private static int cmdStocksOther(CommandContext<CommandSourceStack> ctx, StockStore stockStore) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        int stocks = stockStore.getStocks(player.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal(
            player.getName().getString() + " has " + stocks + " stock(s)"), false);
        return stocks;
    }

    private static int cmdDeathSentenceWindow(CommandContext<CommandSourceStack> ctx) {
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        Lifeledger.CONFIG.getConfig().deathSentenceWindowSeconds = seconds;
        Lifeledger.CONFIG.save();
        ctx.getSource().sendSuccess(() -> Component.literal(
            "[LifeLedger] Death Sentence window set to " + seconds + "s"), true);
        return seconds;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> configToggle(String name, BiConsumer<ServerConfig, Boolean> setter) {
        return Commands.literal(name)
            .then(Commands.argument("value", BoolArgumentType.bool())
                .executes(ctx -> {
                    boolean value = BoolArgumentType.getBool(ctx, "value");
                    setter.accept(Lifeledger.CONFIG.getConfig(), value);
                    Lifeledger.CONFIG.save();
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "[LifeLedger] " + name + " set to " + value), true);
                    return 1;
                }));
    }

    private static NameAndId singleProfile(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<NameAndId> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        return profiles.iterator().next();
    }

    private static void broadcast(CommandContext<CommandSourceStack> ctx, StockStore stockStore) {
        MinecraftServer server = ctx.getSource().getServer();
        StockListPayload payload = new StockListPayload(
            stockStore.getAllStocks(), Lifeledger.resolveNames(stockStore, server), Lifeledger.CONFIG.getConfig().defaultStocks);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, payload);
        }
    }
}
