package lifeledger;

import lifeledger.config.DeathFilter;
import lifeledger.config.ServerConfigManager;
import lifeledger.stocks.StockStore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.component.DataComponents;
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
import java.util.UUID;

public class Lifeledger implements ModInitializer {
    public static final String MOD_ID = "lifeledger";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

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
        LifeledgerCommands.register(stockStore);

        PayloadTypeRegistry.clientboundPlay().register(StockListPayload.TYPE, StockListPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StockDeltaPayload.TYPE, StockDeltaPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ConfigSnapshotPayload.TYPE, ConfigSnapshotPayload.CODEC);
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
            // Resolve attacker: melee = getDirectEntity(), projectiles = getEntity(), crystals = tracked map
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
            // Marking triggers on any player-attributed hit (melee or bow) with the named item in main hand
            ItemStack weapon = attacker.getMainHandItem();
            if (CONFIG.getConfig().deathSentenceEnabled
                    && weapon.has(DataComponents.CUSTOM_NAME)) {
                String name = weapon.get(DataComponents.CUSTOM_NAME).getString();
                if (name.equalsIgnoreCase(CONFIG.getConfig().deathSentenceItemName)) {
                    pvpTracker.mark(victimUUID, attackerUUID);
                    LOGGER.info("[LifeLedger] {} marked {} with Death Sentence",
                        attacker.getName().getString(), victim.getName().getString());
                    return true;
                }
            }
            // Any player damage (melee or projectile) refreshes an existing mark
            if (pvpTracker.refreshIfMarked(victimUUID, attackerUUID)) {
                LOGGER.info("[LifeLedger] Death Sentence window refreshed: {} still targeting {}",
                    attacker.getName().getString(), victim.getName().getString());
            }
            return true;
        });

        ServerLifecycleEvents.SERVER_STARTED.register(s -> this.server = s);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
            ServerPlayer joiningPlayer = handler.getPlayer();
            UUID uuid = joiningPlayer.getUUID();
            if (!stockStore.hasPlayer(uuid)) {
                stockStore.setStocks(uuid, CONFIG.getConfig().defaultStocks);
                stockStore.save();
            }
            // Direct send to circumvent shaky getPlayers() timing
            StockListPayload payload = new StockListPayload(stockStore.getAllStocks(), CONFIG.getConfig().defaultStocks);
            ServerPlayNetworking.send(joiningPlayer, payload);
            broadcastStockList(s); // joiner may get it twice, harmless
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
                stockStore.removePlayer(uuid);
            }
            stockStore.save();

            broadcastDelta(server, uuid, remaining, remaining <= 0);

            if (remaining <= 0) {
                String reason = CONFIG.getConfig().banMessage;
                NameAndId nameAndId = new NameAndId(player.getUUID(), player.getScoreboardName());
                server.getPlayerList().getBans().add(
                    new UserBanListEntry(nameAndId, null, null, null, reason)
                );
                player.connection.disconnect(Component.literal(reason));
            }
        });
    }

    private void broadcastStockList(MinecraftServer s) {
        StockListPayload payload = new StockListPayload(stockStore.getAllStocks(), CONFIG.getConfig().defaultStocks);
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    private void broadcastDelta(MinecraftServer s, UUID uuid, int stocks, boolean eliminated) {
        StockDeltaPayload payload = new StockDeltaPayload(uuid, stocks, eliminated);
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, payload);
        }
    }
}