package lifeledger;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record DeathSentenceClearedPayload() implements CustomPacketPayload {

    public static final Type<DeathSentenceClearedPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(Lifeledger.MOD_ID, "death_sentence_cleared")
    );

    public static final StreamCodec<FriendlyByteBuf, DeathSentenceClearedPayload> CODEC = StreamCodec.of(
        (buf, p) -> {},
        buf -> new DeathSentenceClearedPayload()
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}