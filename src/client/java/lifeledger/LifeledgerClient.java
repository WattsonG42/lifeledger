package lifeledger;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class LifeledgerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientConfig.load();

        ClientPlayNetworking.registerGlobalReceiver(StockListPayload.TYPE, (payload, context) ->
            context.client().execute(() -> StockCache.update(payload.stocks(), payload.maxStocks()))
        );

        ClientPlayNetworking.registerGlobalReceiver(StockDeltaPayload.TYPE, (payload, context) ->
            context.client().execute(() -> StockCache.applyDelta(payload.uuid(), payload.stocks(), payload.eliminated()))
        );

        ClientPlayNetworking.registerGlobalReceiver(ConfigSnapshotPayload.TYPE, (payload, context) ->
            context.client().execute(() -> ConfigSnapshotCache.update(payload))
        );

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            StockCache.clear();
            ConfigSnapshotCache.clear();
        });
    }
}