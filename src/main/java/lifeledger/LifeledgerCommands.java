package lifeledger;

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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.function.BiConsumer;

public class LifeledgerCommands {

    public static void register(StockStore stockStore) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                Commands.literal("lifeledger")
                    .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .then(Commands.literal("default")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                            .executes(ctx -> cmdDefault(ctx, stockStore))))
                    .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(ctx -> cmdSet(ctx, stockStore)))))
                    .then(Commands.literal("give")
                        .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> cmdGive(ctx, stockStore)))))
                    .then(Commands.literal("take")
                        .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> cmdTake(ctx, stockStore)))))
                    .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(ctx -> cmdGet(ctx, stockStore))))
                    .then(Commands.literal("config")
                        .then(configToggle("mobs",         (cfg, v) -> cfg.countMobDeaths = v))
                        .then(configToggle("pvp",          (cfg, v) -> cfg.countPvpDeaths = v))
                        .then(configToggle("fall",         (cfg, v) -> cfg.countFallDamage = v))
                        .then(configToggle("void",         (cfg, v) -> cfg.countVoidDeaths = v))
                        .then(configToggle("dragon",       (cfg, v) -> cfg.countEnderDragonDeaths = v))
                        .then(configToggle("wither",       (cfg, v) -> cfg.countWitherDeaths = v))
                        .then(configToggle("elderguardian",(cfg, v) -> cfg.countElderGuardianDeaths = v))
                        .then(configToggle("deathsentence",(cfg, v) -> cfg.deathSentenceEnabled = v))
                        .then(configToggle("explosion",    (cfg, v) -> cfg.countExplosionDeaths = v))
                        .then(configToggle("anvil",        (cfg, v) -> cfg.countAnvilDeaths = v))
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

    private static int cmdSet(CommandContext<CommandSourceStack> ctx, StockStore stockStore) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        stockStore.setStocks(player.getUUID(), amount);
        stockStore.save();
        broadcast(ctx, stockStore);
        ctx.getSource().sendSuccess(() -> Component.literal(
            player.getName().getString() + "'s stocks set to " + amount), true);
        return amount;
    }

    private static int cmdGive(CommandContext<CommandSourceStack> ctx, StockStore stockStore) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        int current = Math.max(0, stockStore.getStocks(player.getUUID()));
        int total = current + amount;
        stockStore.setStocks(player.getUUID(), total);
        stockStore.save();
        broadcast(ctx, stockStore);
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Gave " + amount + " stock(s) to " + player.getName().getString() + " — now at " + total), true);
        return total;
    }

    private static int cmdTake(CommandContext<CommandSourceStack> ctx, StockStore stockStore) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        int current = Math.max(0, stockStore.getStocks(player.getUUID()));
        int total = Math.max(0, current - amount);
        stockStore.setStocks(player.getUUID(), total);
        stockStore.save();
        broadcast(ctx, stockStore);
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Took " + (current - total) + " stock(s) from " + player.getName().getString() + " — now at " + total), true);
        return total;
    }

    private static int cmdGet(CommandContext<CommandSourceStack> ctx, StockStore stockStore) throws CommandSyntaxException {
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

    private static void broadcast(CommandContext<CommandSourceStack> ctx, StockStore stockStore) {
        StockListPayload payload = new StockListPayload(
            stockStore.getAllStocks(), Lifeledger.CONFIG.getConfig().defaultStocks);
        for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, payload);
        }
    }
}