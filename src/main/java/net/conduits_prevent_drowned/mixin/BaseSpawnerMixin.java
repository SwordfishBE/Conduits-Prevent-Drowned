package net.conduits_prevent_drowned.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.conduits_prevent_drowned.SpawnProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BaseSpawner.class)
abstract class BaseSpawnerMixin {
    @Shadow private void delay(Level level, BlockPos pos) { throw new AssertionError(); }

    @Inject(method = "serverTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;checkSpawnObstruction(Lnet/minecraft/world/level/LevelReader;)Z"),
            cancellable = true)
    private void preventDrowned(ServerLevel level, BlockPos pos, CallbackInfo callback, @Local Mob mob) {
        if (SpawnProtection.blocks(level, mob)) {
            delay(level, pos);
            callback.cancel();
        }
    }
}
