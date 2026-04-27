package lifeledger;

import lifeledger.config.DeathFilter;
import lifeledger.config.ServerConfigManager;
import lifeledger.stocks.StockStore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.component.DataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class Lifeledger implements ModInitializer {
    public static final String MOD_ID = "lifeledger";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final String[] DEATH_SENTENCE_MESSAGES = new String[]{
            "{attacker} wants to see you squirm.",
            "{attacker} has sentenced you to death.",
            "{attacker} is coming for your soul ╹◡╹",
            "Death follows you now. {attacker} made sure of it.",
            "{attacker} has marked you. Your time is running out.",
            "{attacker} has put a price on your head.",
            "Run. {attacker} is not done with you ╹◡╹",
            "{attacker} has chosen you. Choose your next moves carefully.",
            "The reaper answers to {attacker} now.",
            "Pray.",
            "L + ratio + Death Sentence - {attacker}",
            "Run.",
            "Tick tock",
            "{attacker} doesn't need luck.",
            "Time's up.",
            "{attacker} will be there when you respawn."
    };

    private static final Random RANDOM = new Random();

    public static final ServerConfigManager CONFIG = new ServerConfigManager();
    private final StockStore stockStore = new StockStore();
    private final PvpTracker pvpTracker = new PvpTracker();
    private final Map<UUID, Long> configRequestCooldowns = new HashMap<>();
    private static final long CONFIG_REQUEST_COOLDOWN_MS = 1000;
    private MinecraftServer server;

    @Override
    public void onInitialize() {
        LOGGER.info("[LifeLedger] Loaded");
        CONFIG.load();
        stockStore.load();
        LifeledgerCommands.register(stockStore, pvpTracker);

        PayloadTypeRegistry.clientboundPlay().register(StockListPayload.TYPE, StockListPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StockDeltaPayload.TYPE, StockDeltaPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ConfigSnapshotPayload.TYPE, ConfigSnapshotPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DeathSentencePayload.TYPE, DeathSentencePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DeathSentenceClearedPayload.TYPE, DeathSentenceClearedPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ConfigRequestPayload.TYPE, ConfigRequestPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ConfigRequestPayload.TYPE, (payload, context) -> {
            UUID uuid = context.player().getUUID();
            long now = System.currentTimeMillis();
            if (now - configRequestCooldowns.getOrDefault(uuid, 0L) < CONFIG_REQUEST_COOLDOWN_MS) return;
            configRequestCooldowns.put(uuid, now);
            ServerPlayNetworking.send(context.player(), ConfigSnapshotPayload.from(CONFIG.getConfig(), context.player()));
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer && entity instanceof EndCrystal crystal) {
                pvpTracker.trackCrystal(crystal.getId(), serverPlayer.getUUID());
            }
            return InteractionResult.PASS;
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer victim)) return true;
            ServerPlayer attacker = null;
            if (source.getDirectEntity() instanceof ServerPlayer p) attacker = p;
            else if (source.getEntity() instanceof ServerPlayer p) attacker = p;
            else if (source.getDirectEntity() instanceof PrimedTnt tnt
                    && tnt.getOwner() instanceof ServerPlayer p) attacker = p;
            else if (source.getDirectEntity() instanceof EndCrystal crystal) {
                UUID crystalAttackerUUID = pvpTracker.consumeCrystalAttacker(crystal.getId());
                if (crystalAttackerUUID != null)
                    attacker = server.getPlayerList().getPlayer(crystalAttackerUUID);
            }
            if (attacker == null) return true;

            UUID attackerUUID = attacker.getUUID();
            UUID victimUUID = victim.getUUID();
            ItemStack weapon = attacker.getMainHandItem();
            if (CONFIG.getConfig().deathSentenceEnabled
                    && weapon.has(DataComponents.CUSTOM_NAME)) {
                String name = weapon.get(DataComponents.CUSTOM_NAME).getString();
                if (name.equalsIgnoreCase(CONFIG.getConfig().deathSentenceItemName)) {
                    boolean alreadyMarked = pvpTracker.isMarked(victimUUID, CONFIG.getConfig().deathSentenceWindowSeconds);
                    pvpTracker.mark(victimUUID, attackerUUID);
                    if (!alreadyMarked) {
                        LOGGER.info("[LifeLedger] {} marked {} with Death Sentence",
                            attacker.getName().getString(), victim.getName().getString());
                        if (ServerPlayNetworking.canSend(victim, DeathSentencePayload.TYPE)) {
                            ServerPlayNetworking.send(victim, new DeathSentencePayload());
                        }
                        String msg = DEATH_SENTENCE_MESSAGES[RANDOM.nextInt(DEATH_SENTENCE_MESSAGES.length)]
                            .replace("{attacker}", attacker.getName().getString());
                        victim.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.DARK_RED));
                    }
                    return true;
                }
            }
            if (CONFIG.getConfig().deathSentenceEnabled && pvpTracker.refreshIfMarked(victimUUID, attackerUUID)) {
                LOGGER.info("[LifeLedger] Death Sentence window refreshed: {} still targeting {}",
                    attacker.getName().getString(), victim.getName().getString());
            }
            return true;
        });

        ServerLifecycleEvents.SERVER_STARTED.register(s -> this.server = s);

        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (!CONFIG.getConfig().deathSentenceEnabled) return;
            for (UUID uuid : pvpTracker.consumeExpiredVictims(CONFIG.getConfig().deathSentenceWindowSeconds)) {
                ServerPlayer p = s.getPlayerList().getPlayer(uuid);
                if (p != null && ServerPlayNetworking.canSend(p, DeathSentenceClearedPayload.TYPE)) {
                    ServerPlayNetworking.send(p, new DeathSentenceClearedPayload());
                }
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
            ServerPlayer joiningPlayer = handler.getPlayer();
            UUID uuid = joiningPlayer.getUUID();
            stockStore.setName(uuid, joiningPlayer.getScoreboardName());
            if (!stockStore.hasPlayer(uuid)) {
                stockStore.setStocks(uuid, CONFIG.getConfig().defaultStocks);
            }
            stockStore.save();
            StockListPayload payload = new StockListPayload(stockStore.getAllStocks(), resolveNames(s), CONFIG.getConfig().defaultStocks);
            ServerPlayNetworking.send(joiningPlayer, payload);
            broadcastStockList(s);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> {
            UUID uuid = handler.getPlayer().getUUID();
            configRequestCooldowns.remove(uuid);
            pvpTracker.clearMark(uuid);
            pvpTracker.clearCrystalsByAttacker(uuid);
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayer player)) return;
            UUID uuid = player.getUUID();
            boolean markedActive = pvpTracker.isMarked(uuid, CONFIG.getConfig().deathSentenceWindowSeconds);
            boolean counts = DeathFilter.shouldCount(source, CONFIG.getConfig(), uuid, pvpTracker);
            if (pvpTracker.hasAnyMark(uuid) && !markedActive) {
                LOGGER.info("[LifeLedger] Death Sentence mark on {} expired before death counted",
                    player.getName().getString());
            }
            pvpTracker.clearMark(uuid);
            if (!counts) return;
            int remaining = stockStore.getStocks(uuid) - 1;
            stockStore.setStocks(uuid, remaining);

            LOGGER.info("[LifeLedger] {} died. Stocks remaining: {}", player.getName().getString(), remaining);

            if (remaining <= 0) {
                eliminate(player.getUUID(), player.getScoreboardName(), stockStore, server, CONFIG.getConfig().banMessage);
            } else {
                player.sendSystemMessage(Component.literal(
                    "You lost a stock. Stocks remaining: " + remaining));
                stockStore.save();
                broadcastDelta(server, uuid, remaining, false);
            }
        });
    }

    private void broadcastStockList(MinecraftServer s) {
        StockListPayload payload = new StockListPayload(stockStore.getAllStocks(), resolveNames(s), CONFIG.getConfig().defaultStocks);
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    private Map<UUID, String> resolveNames(MinecraftServer s) {
        return resolveNames(stockStore, s);
    }

    static Map<UUID, String> resolveNames(StockStore stocks, MinecraftServer s) {
        Map<UUID, String> names = new HashMap<>(stocks.getAllNames());
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (stocks.hasPlayer(p.getUUID())) names.put(p.getUUID(), p.getScoreboardName());
        }
        return names;
    }

    static void eliminate(UUID uuid, String name, StockStore stockStore, MinecraftServer server, String reason) {
        stockStore.removePlayer(uuid);
        stockStore.save();
        broadcastDelta(server, uuid, 0, true);
        server.getPlayerList().broadcastSystemMessage(
            Component.literal(name + " was laid to rest.")
                .withStyle(ChatFormatting.DARK_RED),
            false
        );
        NameAndId nameAndId = new NameAndId(uuid, name);
        server.getPlayerList().getBans().add(new UserBanListEntry(nameAndId, null, null, null, reason));
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) online.connection.disconnect(Component.literal(reason));
    }

    private static void broadcastDelta(MinecraftServer s, UUID uuid, int stocks, boolean eliminated) {
        StockDeltaPayload payload = new StockDeltaPayload(uuid, stocks, eliminated);
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, payload);
        }
    }
}
