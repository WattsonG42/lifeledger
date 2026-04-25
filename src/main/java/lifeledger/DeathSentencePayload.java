package lifeledger;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record DeathSentencePayload() implements CustomPacketPayload {

    public static final Type<DeathSentencePayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(Lifeledger.MOD_ID, "death_sentence")
    );

    public static final StreamCodec<FriendlyByteBuf, DeathSentencePayload> CODEC = StreamCodec.of(
        (buf, p) -> {},
        buf -> new DeathSentencePayload()
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}