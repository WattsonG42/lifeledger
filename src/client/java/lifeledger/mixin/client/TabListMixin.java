package lifeledger.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import lifeledger.ClientConfig;
import lifeledger.LifeledgerClient;
import lifeledger.StockCache;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(PlayerTabOverlay.class)
public class TabListMixin {

    @Inject(method = "extractPingIcon", at = @At("HEAD"))
    private void drawStockHearts(
        GuiGraphicsExtractor graphics,
        int entryWidth, int entryX, int rowY,
        PlayerInfo playerInfo,
        CallbackInfo ci
    ) {
        if (!ClientConfig.get().showStocksInTabList) return;

        UUID uuid = playerInfo.getProfile().id();
        int stocks = StockCache.getStocks(uuid);
        int maxStocks = StockCache.getMaxStocks();

        if (stocks < 0 || maxStocks <= 0) return;

        // Cap display at 10 slots. Use the larger of maxStocks or actual stocks so players
        // with more stocks than the current default still render their full count.
        int displayMax = Math.min(Math.max(maxStocks, stocks), 10);
        int displayFull = Math.min(stocks, displayMax);

        int step = 7;
        int startX = entryWidth + entryX + 2;

        RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;
        for (int i = 0; i < displayMax; i++) {
            Identifier tex = i < displayFull ? LifeledgerClient.FULL_HEART : LifeledgerClient.EXPENDED_HEART;
            graphics.blitSprite(pipeline, tex, startX + i * step, rowY, 9, 9);
        }
    }
}
