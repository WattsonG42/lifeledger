package lifeledger;

public class ConfigSnapshotCache {
    private static volatile ConfigSnapshotPayload snapshot = null;
    private static volatile boolean dirty = false;

    public static void update(ConfigSnapshotPayload payload) {
        snapshot = payload;
        dirty = true;
    }

    public static ConfigSnapshotPayload get() { return snapshot; }

    public static boolean consumeDirty() {
        if (dirty) { dirty = false; return true; }
        return false;
    }

    public static void clear() {
        snapshot = null;
        dirty = false;
    }
}