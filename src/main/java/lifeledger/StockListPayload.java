package lifeledger;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record StockListPayload(Map<UUID, Integer> stocks, Map<UUID, String> names, int maxStocks) implements CustomPacketPayload {

    public static final Type<StockListPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(Lifeledger.MOD_ID, "stock_list")
    );

    public static final StreamCodec<FriendlyByteBuf, StockListPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeVarInt(p.maxStocks());
            buf.writeVarInt(p.stocks().size());
            for (Map.Entry<UUID, Integer> e : p.stocks().entrySet()) {
                buf.writeUUID(e.getKey());
                buf.writeVarInt(e.getValue());
                buf.writeUtf(p.names().getOrDefault(e.getKey(), ""));
            }
        },
        buf -> {
            int max = buf.readVarInt();
            int size = buf.readVarInt();
            Map<UUID, Integer> stocks = new HashMap<>(size);
            Map<UUID, String>  names  = new HashMap<>(size);
            for (int i = 0; i < size; i++) {
                UUID uuid = buf.readUUID();
                stocks.put(uuid, buf.readVarInt());
                names.put(uuid, buf.readUtf());
            }
            return new StockListPayload(stocks, names, max);
        }
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
