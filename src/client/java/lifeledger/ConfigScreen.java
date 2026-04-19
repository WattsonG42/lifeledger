package lifeledger;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.function.Consumer;

public class ConfigScreen extends Screen {
    private final Screen parent;
    private final TabManager tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
    private TabNavigationBar tabNavigationBar;
    private int selectedTab = 0;

    public ConfigScreen(Screen parent) {
        super(Component.literal("LifeLedger Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ClientPlayNetworking.send(new ConfigRequestPayload());

        this.tabNavigationBar = TabNavigationBar.builder(this.tabManager, this.width)
            .addTabs(new ClientSettingsTab(), new ServerSettingsTab())
            .build();
        this.addRenderableWidget(this.tabNavigationBar);
        this.tabNavigationBar.arrangeElements();

        int tabBottom = this.tabNavigationBar.getRectangle().bottom();
        this.tabManager.setTabArea(new ScreenRectangle(0, tabBottom, this.width, this.height - tabBottom));
        this.tabNavigationBar.selectTab(selectedTab, false);

        this.addRenderableWidget(
            Button.builder(Component.literal("Player Stocks"), btn -> this.minecraft.setScreen(new PlayerListScreen(this)))
                .bounds(this.width / 2 - 50, this.height - 55, 100, 20)
                .build()
        );
        this.addRenderableWidget(
            Button.builder(Component.literal("Close"), btn -> this.minecraft.setScreen(parent))
                .bounds(this.width / 2 - 50, this.height - 30, 100, 20)
                .build()
        );
    }

    @Override
    public void tick() {
        super.tick();
        if (ConfigSnapshotCache.consumeDirty()) {
            rebuildWidgets();
        }
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }


    private static class ClientSettingsTab extends GridLayoutTab {
        ClientSettingsTab() {
            super(Component.literal("Client Settings"));
            GridLayout.RowHelper rows = this.layout.spacing(4).createRowHelper(1);
            rows.addChild(toggle("Show Stocks in Tab List",
                ClientConfig.get().showStocksInTabList,
                v -> { ClientConfig.get().showStocksInTabList = v; ClientConfig.save(); }
            ));
        }

        private static CycleButton<Boolean> toggle(String label, boolean initial, Consumer<Boolean> onChange) {
            return CycleButton.onOffBuilder(initial)
                .create(0, 0, 200, 20, Component.literal(label), (b, v) -> onChange.accept(v));
        }
    }

    private class ServerSettingsTab extends GridLayoutTab {
        ServerSettingsTab() {
            super(Component.literal("Server Settings"));
            GridLayout.RowHelper rows = this.layout.spacing(4).createRowHelper(2);
            ConfigSnapshotPayload snap = ConfigSnapshotCache.get();
            if (snap == null) {
                rows.addChild(new StringWidget(200, 20, Component.literal("Loading..."), font));
            } else {
                boolean canEdit = snap.isOp();
                rows.addChild(serverToggle("Count Mob Deaths",            snap.countMobDeaths(),           canEdit, "mobs"));
                rows.addChild(serverToggle("Count PvP Deaths",            snap.countPvpDeaths(),           canEdit, "pvp"));
                rows.addChild(serverToggle("Count Fall Damage",           snap.countFallDamage(),          canEdit, "fall"));
                rows.addChild(serverToggle("Count Void Deaths",           snap.countVoidDeaths(),          canEdit, "void"));
                rows.addChild(serverToggle("Count Ender Dragon Deaths",   snap.countEnderDragonDeaths(),   canEdit, "dragon"));
                rows.addChild(serverToggle("Count Wither Deaths",         snap.countWitherDeaths(),        canEdit, "wither"));
                rows.addChild(serverToggle("Count Elder Guardian Deaths", snap.countElderGuardianDeaths(), canEdit, "elderguardian"));
                rows.addChild(serverToggle("Count Explosion Deaths",      snap.countExplosionDeaths(),     canEdit, "explosion"));
                rows.addChild(serverToggle("Count Anvil Deaths",          snap.countAnvilDeaths(),         canEdit, "anvil"));
                rows.addChild(serverToggle("Count Fire/Lava Deaths",      snap.countFireDeaths(),          canEdit, "fire"));
                rows.addChild(serverToggle("Count Drowning Deaths",       snap.countDrownDeaths(),         canEdit, "drown"));
                rows.addChild(serverToggle("Count Freeze Deaths",         snap.countFreezeDeaths(),        canEdit, "freeze"));
                rows.addChild(serverToggle("Count Magic Deaths",          snap.countMagicDeaths(),         canEdit, "magic"));
                rows.addChild(serverToggle("Count Suffocation Deaths",    snap.countSuffocationDeaths(),   canEdit, "suffocation"));
                rows.addChild(serverToggle("Death Sentence",              snap.deathSentenceEnabled(),     canEdit, "deathsentence"));
            }
        }

        private CycleButton<Boolean> serverToggle(String label, boolean initial, boolean canEdit, String configKey) {
            CycleButton<Boolean> btn = CycleButton.onOffBuilder(initial)
                .create(0, 0, 200, 20, Component.literal(label), (b, v) -> {
                    ConfigScreen.this.selectedTab = 1;
                    minecraft.player.connection.sendCommand("lifeledger config " + configKey + " " + v);
                    ClientPlayNetworking.send(new ConfigRequestPayload());
                });
            btn.active = canEdit;
            return btn;
        }
    }
}
