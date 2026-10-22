package net.conduits_prevent_drowned;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.player.Player;

public final class SpawnProtection {
    private SpawnProtection() {}

    public static boolean blocks(ServerLevel level, Mob mob) {
        if (!(mob instanceof Drowned)) return false;
        return protectedAt(level.players(), mob.blockPosition(), ConduitsPreventDrowned.CONFIG.range());
    }

    static boolean protectedAt(Iterable<? extends Player> players, BlockPos position, int range) {
        if (range == 0) return false;
        for (Player player : players) {
            if (!player.hasEffect(MobEffects.CONDUIT_POWER)) continue;
            // Match the original block-based horizontal circle, with a strict boundary.
            double dx = (double) player.blockPosition().getX() - position.getX();
            double dz = (double) player.blockPosition().getZ() - position.getZ();
            if (dx * dx + dz * dz < (double) range * range) return true;
        }
        return false;
    }
}
