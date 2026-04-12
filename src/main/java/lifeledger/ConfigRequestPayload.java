package lifeledger;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record ConfigRequestPayload() implements CustomPacketPayload {

    public static final Type<ConfigRequestPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(Lifeledger.MOD_ID, "config_request")
    );

    public static final StreamCodec<FriendlyByteBuf, ConfigRequestPayload> CODEC = StreamCodec.of(
        (buf, p) -> {}, buf -> new ConfigRequestPayload()
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}