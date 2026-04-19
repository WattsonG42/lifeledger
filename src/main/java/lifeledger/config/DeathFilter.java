package lifeledger.config;

import lifeledger.PvpTracker;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public class DeathFilter {

    public static boolean shouldCount(DamageSource source, @NonNull ServerConfig config, UUID playerUUID, PvpTracker pvpTracker) {
        if (!config.countVoidDeaths      && source.is(DamageTypes.FELL_OUT_OF_WORLD))  return false;
        if (!config.countFallDamage      && source.is(DamageTypes.FALL))               return false;
        if (!config.countExplosionDeaths && source.is(DamageTypeTags.IS_EXPLOSION))    return false;
        if (!config.countAnvilDeaths     && source.is(DamageTypes.FALLING_ANVIL))      return false;
        if (!config.countFireDeaths      && source.is(DamageTypeTags.IS_FIRE))         return false;
        if (!config.countDrownDeaths     && source.is(DamageTypes.DROWN))              return false;
        if (!config.countFreezeDeaths    && source.is(DamageTypes.FREEZE))             return false;
        if (!config.countMagicDeaths     && (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC))) return false;
        if (!config.countSuffocationDeaths && source.is(DamageTypes.IN_WALL))          return false;

        // Boss toggles, not affected by the mob filter below
        if (source.getEntity() instanceof EnderDragon) return config.countEnderDragonDeaths;
        if (isWitherSource(source))                    return config.countWitherDeaths;
        if (source.getEntity() instanceof ElderGuardian) return config.countElderGuardianDeaths;

        // Generalized mob filter
        if (!config.countMobDeaths
                && source.getEntity() instanceof LivingEntity e
                && !(e instanceof Player)) return false;

        // Death Sentence: any marked player dies regardless of other settings
        if (config.deathSentenceEnabled && source.getEntity() instanceof Player
                && pvpTracker.isMarked(playerUUID, config.deathSentenceWindowSeconds)) return true;
        if (!config.countPvpDeaths && source.getEntity() instanceof Player) return false;
        return true;
    }

    private static boolean isWitherSource(@NonNull DamageSource source) {
        return source.getEntity() instanceof WitherBoss
            || source.getDirectEntity() instanceof WitherSkull;
    }
}
