package lifeledger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PvpTracker {
    // victim -> (attacker -> mark timestamp): each attacker tracks independently
    private final Map<UUID, Map<UUID, Long>> marks = new HashMap<>();
    private record CrystalEntry(UUID attacker, long timestamp) {}
    // crystal entity ID -> entry: bridging the gap between hit and explosion
    private final Map<Integer, CrystalEntry> crystalAttackers = new HashMap<>();

    public void mark(UUID target, UUID attacker) {
        marks.computeIfAbsent(target, k -> new HashMap<>())
                .put(attacker, System.currentTimeMillis());
    }

    public boolean refreshIfMarked(UUID target, UUID attacker) {
        Map<UUID, Long> attackerMarks = marks.get(target);
        if (attackerMarks != null && attackerMarks.containsKey(attacker)) {
            attackerMarks.put(attacker, System.currentTimeMillis());
            return true;
        }
        return false;
    }

    public boolean hasAnyMark(UUID target) {
        Map<UUID, Long> attackerMarks = marks.get(target);
        return attackerMarks != null && !attackerMarks.isEmpty();
    }

    public boolean isMarked(UUID target, int windowSeconds) {
        Map<UUID, Long> attackerMarks = marks.get(target);
        if (attackerMarks == null || attackerMarks.isEmpty()) return false;
        long cutoff = System.currentTimeMillis() - windowSeconds * 1000L;
        return attackerMarks.values().stream().anyMatch(ts -> ts >= cutoff);
    }

    public void clearMark(UUID target) {
        marks.remove(target);
    }

    public void trackCrystal(int entityId, UUID attackerUUID) {
        long now = System.currentTimeMillis();
        // sweep old entries
        crystalAttackers.values().removeIf(e -> now - e.timestamp() > 5000);
        crystalAttackers.put(entityId, new CrystalEntry(attackerUUID, now));
    }

    public UUID consumeCrystalAttacker(int entityId) {
        CrystalEntry entry = crystalAttackers.remove(entityId);
        return entry != null ? entry.attacker() : null;
    }

    public void clearCrystalsByAttacker(UUID attackerUUID) {
        crystalAttackers.values().removeIf(e -> e.attacker().equals(attackerUUID));
    }
}