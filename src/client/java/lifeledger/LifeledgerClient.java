package lifeledger;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class LifeledgerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientConfig.load();

        ClientPlayNetworking.registerGlobalReceiver(StockListPayload.TYPE, (payload, context) ->
            context.client().execute(() -> StockCache.update(payload.stocks(), payload.names(), payload.maxStocks()))
        );

        ClientPlayNetworking.registerGlobalReceiver(StockDeltaPayload.TYPE, (payload, context) ->
            context.client().execute(() -> StockCache.applyDelta(payload.uuid(), payload.stocks(), payload.eliminated()))
        );

        ClientPlayNetworking.registerGlobalReceiver(ConfigSnapshotPayload.TYPE, (payload, context) ->
            context.client().execute(() -> ConfigSnapshotCache.update(payload))
        );

        ClientPlayNetworking.registerGlobalReceiver(DeathSentencePayload.TYPE, (payload, context) ->
            context.client().execute(ImpactFrameRenderer::trigger)
        );

        ClientPlayNetworking.registerGlobalReceiver(DeathSentenceClearedPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if (context.client().player != null) {
                    context.client().player.sendSystemMessage(
                        Component.literal("You are safe... for now.").withStyle(ChatFormatting.DARK_GREEN)
                    );
                }
            })
        );

        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath(Lifeledger.MOD_ID, "impact_frame"),
            (graphics, deltaTracker) -> ImpactFrameRenderer.render(graphics)
        );

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            StockCache.clear();
            ConfigSnapshotCache.clear();
            ImpactFrameRenderer.reset();
        });
    }
}