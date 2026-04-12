package lifeledger;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record StockDeltaPayload(UUID uuid, int stocks, boolean eliminated) implements CustomPacketPayload {
    public static final Type<StockDeltaPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Lifeledger.MOD_ID, "stock_delta"));

    public static final StreamCodec<FriendlyByteBuf, StockDeltaPayload> CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeUUID(p.uuid()); buf.writeVarInt(p.stocks()); buf.writeBoolean(p.eliminated()); },
        buf -> new StockDeltaPayload(buf.readUUID(), buf.readVarInt(), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}