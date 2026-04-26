package lifeledger;

import lifeledger.config.ServerConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record ConfigSnapshotPayload(
    boolean countMobDeaths,
    boolean countPvpDeaths,
    boolean countFallDamage,
    boolean countVoidDeaths,
    boolean countEnderDragonDeaths,
    boolean countWitherDeaths,
    boolean countElderGuardianDeaths,
    boolean countExplosionDeaths,
    boolean countAnvilDeaths,
    boolean countFireDeaths,
    boolean countDrownDeaths,
    boolean countFreezeDeaths,
    boolean countMagicDeaths,
    boolean countSuffocationDeaths,
    boolean deathSentenceEnabled,
    boolean isOp,
    int defaultStocks,
    int deathSentenceWindowSeconds
) implements CustomPacketPayload {

    public static final Type<ConfigSnapshotPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(Lifeledger.MOD_ID, "config_snapshot")
    );

    public static final StreamCodec<FriendlyByteBuf, ConfigSnapshotPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeBoolean(p.countMobDeaths());
            buf.writeBoolean(p.countPvpDeaths());
            buf.writeBoolean(p.countFallDamage());
            buf.writeBoolean(p.countVoidDeaths());
            buf.writeBoolean(p.countEnderDragonDeaths());
            buf.writeBoolean(p.countWitherDeaths());
            buf.writeBoolean(p.countElderGuardianDeaths());
            buf.writeBoolean(p.countExplosionDeaths());
            buf.writeBoolean(p.countAnvilDeaths());
            buf.writeBoolean(p.countFireDeaths());
            buf.writeBoolean(p.countDrownDeaths());
            buf.writeBoolean(p.countFreezeDeaths());
            buf.writeBoolean(p.countMagicDeaths());
            buf.writeBoolean(p.countSuffocationDeaths());
            buf.writeBoolean(p.deathSentenceEnabled());
            buf.writeBoolean(p.isOp());
            buf.writeVarInt(p.defaultStocks());
            buf.writeVarInt(p.deathSentenceWindowSeconds());
        },
        buf -> new ConfigSnapshotPayload(
            buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
            buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
            buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
            buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
            buf.readVarInt(), buf.readVarInt()
        )
    );

    public static ConfigSnapshotPayload from(ServerConfig cfg, ServerPlayer player) {
        return new ConfigSnapshotPayload(
            cfg.countMobDeaths, cfg.countPvpDeaths, cfg.countFallDamage, cfg.countVoidDeaths,
            cfg.countEnderDragonDeaths, cfg.countWitherDeaths, cfg.countElderGuardianDeaths,
            cfg.countExplosionDeaths, cfg.countAnvilDeaths,
            cfg.countFireDeaths, cfg.countDrownDeaths, cfg.countFreezeDeaths,
            cfg.countMagicDeaths, cfg.countSuffocationDeaths,
            cfg.deathSentenceEnabled,
            player.createCommandSourceStack().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER),
            cfg.defaultStocks,
            cfg.deathSentenceWindowSeconds
        );
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}