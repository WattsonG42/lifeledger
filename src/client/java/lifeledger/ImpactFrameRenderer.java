package lifeledger;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public final class ImpactFrameRenderer {


    private static final long[] FRAME_END_MS = { 20, 40, 60, 80, 140 };
    private static final long   TOTAL_MS     = 140;

    private static final Identifier[] FRAMES = {
        Identifier.fromNamespaceAndPath(Lifeledger.MOD_ID, "ds_slash_frames/impact_1_slash"),
        Identifier.fromNamespaceAndPath(Lifeledger.MOD_ID, "ds_slash_frames/impact_2_slash"),
        Identifier.fromNamespaceAndPath(Lifeledger.MOD_ID, "ds_slash_frames/impact_3_slash"),
        Identifier.fromNamespaceAndPath(Lifeledger.MOD_ID, "ds_slash_frames/impact_4_slash"),
        Identifier.fromNamespaceAndPath(Lifeledger.MOD_ID, "ds_slash_frames/impact_5_slash"),
    };

    private static long triggerTime = -1;

    private ImpactFrameRenderer() {}

    public static void trigger() {
        triggerTime = System.currentTimeMillis();
    }

    public static void render(GuiGraphicsExtractor graphics) {
        if (!ClientConfig.get().impactFrameEnabled) return;
        if (triggerTime < 0) return;

        long elapsed = System.currentTimeMillis() - triggerTime;
        if (elapsed >= TOTAL_MS) {
            triggerTime = -1;
            return;
        }

        int w = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int h = Minecraft.getInstance().getWindow().getGuiScaledHeight();


        graphics.fill(0, 0, w, h, 0xFF000000);

        int frameIndex = 0;
        for (int i = 0; i < FRAME_END_MS.length; i++) {
            if (elapsed < FRAME_END_MS[i]) { frameIndex = i; break; }
            frameIndex = i;
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FRAMES[frameIndex], 0, 0, w, h);
    }
}