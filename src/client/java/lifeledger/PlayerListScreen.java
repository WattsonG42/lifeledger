package lifeledger;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class PlayerListScreen extends Screen {

    private static final Identifier PARCHMENT    = Identifier.fromNamespaceAndPath(Lifeledger.MOD_ID, "parchment_background");
    private static final Identifier OFFLINE_ICON = Identifier.fromNamespaceAndPath(Lifeledger.MOD_ID, "offline_icon");

    private static final int PANEL_W         = 280;
    private static final int PANEL_H         = 260;
    private static final int PADDING         = 6;
    private static final int TITLE_H         = 12;
    private static final int SEARCH_H        = 14;
    private static final int BOX_W           = 240;
    private static final int ROW_H           = 32;
    private static final int HEAD_SIZE       = 16;
    private static final int HEART_SIZE      = 9;
    private static final int HEART_STEP      = 7;
    private static final int MAX_HEARTS      = 10;
    private static final int ROW_BG_OFFLINE  = 0x55000000;
    private static final int ROW_BG_ONLINE   = 0x1800BB44;
    private static final int ROW_BG_SELF     = 0x4400BB44;
    private static final int SEPARATOR_COLOR = 0xBB445544;
    private static final int SEP_EXTRA       = 8;

    private final Screen parent;
    private EditBox searchBox;
    private List<Map.Entry<UUID, Integer>> filtered = List.of();
    private int onlineCount = 0;
    private int scrollOffset = 0;

    private int panelX, panelY;
    private int listY, visibleRows;

    public PlayerListScreen(Screen parent) {
        super(Component.literal("Player Stocks"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelX = (width  - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;

        int searchY = panelY + PADDING + TITLE_H + 2;
        listY = searchY + SEARCH_H + 4;
        int listBottom = panelY + PANEL_H - PADDING;
        visibleRows = (listBottom - listY) / ROW_H;

        int boxX = panelX + (PANEL_W - BOX_W) / 2;
        searchBox = new EditBox(font, boxX, searchY, BOX_W, SEARCH_H, Component.empty());
        searchBox.setHint(Component.literal("Search player..."));
        searchBox.setResponder(s -> { scrollOffset = 0; rebuildFiltered(); });
        addRenderableWidget(searchBox);

        rebuildFiltered();
    }

    private void rebuildFiltered() {
        String query = searchBox == null ? "" : searchBox.getValue().toLowerCase();
        Map<UUID, Integer> stocks = StockCache.getAllStocks();
        Map<UUID, String> names   = StockCache.getAllNames();

        Set<UUID> onlineUuids = new HashSet<>();
        if (minecraft != null && minecraft.getConnection() != null) {
            for (UUID uuid : stocks.keySet()) {
                if (minecraft.getConnection().getPlayerInfo(uuid) != null) onlineUuids.add(uuid);
            }
        }

        Comparator<Map.Entry<UUID, Integer>> byStocksDesc =
            Map.Entry.<UUID, Integer>comparingByValue(Comparator.reverseOrder())
                .thenComparing(e -> names.getOrDefault(e.getKey(), "").toLowerCase());

        List<Map.Entry<UUID, Integer>> matching = stocks.entrySet().stream()
            .filter(e -> names.getOrDefault(e.getKey(), "").toLowerCase().contains(query))
            .collect(Collectors.toList());

        List<Map.Entry<UUID, Integer>> online = matching.stream()
            .filter(e -> onlineUuids.contains(e.getKey()))
            .sorted(byStocksDesc)
            .collect(Collectors.toList());

        List<Map.Entry<UUID, Integer>> offline = matching.stream()
            .filter(e -> !onlineUuids.contains(e.getKey()))
            .sorted(byStocksDesc)
            .limit(100)
            .collect(Collectors.toList());

        onlineCount = online.size();
        List<Map.Entry<UUID, Integer>> combined = new ArrayList<>(online);
        combined.addAll(offline);
        filtered = combined;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        int maxScroll = Math.max(0, filtered.size() - visibleRows);
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - scrollY));
        return true;
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;

        graphics.blitSprite(pipeline, PARCHMENT, panelX, panelY, PANEL_W, PANEL_H);

        drawRows(graphics, pipeline);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawRows(GuiGraphicsExtractor graphics, RenderPipeline pipeline) {
        Map<UUID, String> names   = StockCache.getAllNames();
        int maxStocks = StockCache.getMaxStocks();
        int boxX = panelX + (PANEL_W - BOX_W) / 2;

        boolean hasSep = onlineCount > 0 && onlineCount < filtered.size();

        for (int i = 0; i < visibleRows; i++) {
            int idx = i + scrollOffset;
            if (idx >= filtered.size()) break;

            int extraY = (hasSep && idx >= onlineCount) ? SEP_EXTRA : 0;
            int rowY = listY + i * ROW_H + extraY;

            if (hasSep && idx == onlineCount) {
                int sepY = rowY - SEP_EXTRA / 2;
                graphics.fill(boxX + 4, sepY, boxX + BOX_W - 4, sepY + 1, SEPARATOR_COLOR);
            }

            Map.Entry<UUID, Integer> entry = filtered.get(idx);
            UUID uuid   = entry.getKey();
            int  stocks = entry.getValue();
            String name = names.getOrDefault(uuid, "Unknown");

            PlayerInfo info = minecraft.getConnection() == null ? null
                : minecraft.getConnection().getPlayerInfo(uuid);
            boolean online = info != null;
            boolean isSelf = minecraft.player != null && uuid.equals(minecraft.player.getUUID());
            int rowBg = online ? (isSelf ? ROW_BG_SELF : ROW_BG_ONLINE) : ROW_BG_OFFLINE;
            graphics.fill(boxX, rowY, boxX + BOX_W, rowY + ROW_H - 2, rowBg);

            int headX = boxX + 5;
            int headY = rowY + (ROW_H - HEAD_SIZE) / 2;
            if (online) {
                PlayerFaceExtractor.extractRenderState(graphics, info.getSkin(), headX, headY, HEAD_SIZE);
            } else {
                graphics.blitSprite(pipeline, OFFLINE_ICON, headX, headY, HEAD_SIZE, HEAD_SIZE);
            }

            int contentX = headX + HEAD_SIZE + 5;
            int mid = rowY + ROW_H / 2;

            graphics.text(font, name, contentX, rowY + 5, 0xFFFFFFFF);

            int displayMax  = Math.min(Math.max(maxStocks, stocks), MAX_HEARTS);
            int displayFull = Math.min(stocks, displayMax);
            for (int h = 0; h < displayMax; h++) {
                Identifier tex = h < displayFull ? LifeledgerClient.FULL_HEART : LifeledgerClient.EXPENDED_HEART;
                graphics.blitSprite(pipeline, tex, contentX + h * HEART_STEP, mid + 1, HEART_SIZE, HEART_SIZE);
            }
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
