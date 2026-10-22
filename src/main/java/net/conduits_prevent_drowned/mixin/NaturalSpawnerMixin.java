package net.conduits_prevent_drowned.mixin;

import net.conduits_prevent_drowned.SpawnProtection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NaturalSpawner.class)
abstract class NaturalSpawnerMixin {
    @Inject(method = "isValidPositionForMob", at = @At("HEAD"), cancellable = true)
    private static void preventDrowned(ServerLevel level, Mob mob, double distanceSquared,
                                       CallbackInfoReturnable<Boolean> callback) {
        if (SpawnProtection.blocks(level, mob)) callback.setReturnValue(false);
    }
}
