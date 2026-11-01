package net.conduits_prevent_drowned;

import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.SpawnData;

public class ConduitGameTests {
    @GameTest
    public void minecraftVersionRange(GameTestHelper h) throws Exception {
        var dependency = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer(ConduitsPreventDrowned.ID).orElseThrow().getMetadata().getDependencies()
                .stream().filter(entry -> entry.getModId().equals("minecraft")).findFirst().orElseThrow();
        for (String raw : List.of("26.4-snapshot-6", "26.4-snapshot-7", "26.4-snapshot-10",
                "26.4-pre-1", "26.4-rc-1", "26.4")) {
            var normalized = net.fabricmc.loader.impl.game.minecraft.McVersionLookup.normalizeVersion(raw,
                    net.fabricmc.loader.impl.game.minecraft.McVersionLookup.getRelease(raw));
            h.assertTrue(dependency.matches(net.fabricmc.loader.api.Version.parse(normalized)),
                    "Metadata must accept " + raw + " (" + normalized + ")");
        }
        for (String raw : List.of("26.3", "26.4-snapshot-1", "26.4-snapshot-5",
                "26.4.1-pre-1", "26.4.1", "26.5-snapshot-1", "26.5", "27.1")) {
            var normalized = net.fabricmc.loader.impl.game.minecraft.McVersionLookup.normalizeVersion(raw,
                    net.fabricmc.loader.impl.game.minecraft.McVersionLookup.getRelease(raw));
            h.assertFalse(dependency.matches(net.fabricmc.loader.api.Version.parse(normalized)),
                    "Metadata must reject " + raw + " (" + normalized + ")");
        }
        h.succeed();
    }

    @GameTest
    public void reloadChangesProtectionAndPreservesInvalidConfig(GameTestHelper h) throws Exception {
        var path = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve(ConduitsPreventDrowned.ID + ".json");
        String original = Files.readString(path);
        var messages = new java.util.ArrayList<String>();
        var receiver = new net.minecraft.commands.CommandSource() {
            @Override public void sendSystemMessage(net.minecraft.network.chat.Component message) {
                messages.add(message.getString());
            }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        var server = h.getLevel().getServer();
        var source = server.createCommandSourceStack().withSource(receiver);
        var dispatcher = server.getCommands().getDispatcher();
        var p = player(h);
        var mob = EntityTypes.DROWNED.create(h.getLevel(), EntitySpawnReason.NATURAL);
        var pos = h.absolutePos(new BlockPos(1, 2, 1));
        mob.setPos(pos.getX(), pos.getY(), pos.getZ());
        p.setPos(pos.getX() + 10, pos.getY(), pos.getZ());
        p.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 100));
        h.getLevel().players().add(p);
        try {
            for (int range : new int[] {32, 0, 5, 400}) {
                Files.writeString(path, "{\"preventDrownedInRange\":" + range + "}");
                h.assertTrue(dispatcher.execute("conduits_prevent_drowned reload", source) == 1,
                        "Reload should succeed for range " + range);
                h.assertTrue(SpawnProtection.blocks(h.getLevel(), mob) == (range > 10),
                        "Actual spawn policy must immediately use reloaded range " + range);
            }
            Files.writeString(path, "{broken config");
            h.assertTrue(dispatcher.execute("conduits_prevent_drowned reload", source) == 0,
                    "Invalid reload must report failure");
            h.assertTrue(SpawnProtection.blocks(h.getLevel(), mob), "Failed reload keeps previous protection");
            h.assertTrue(Files.readString(path).equals("{broken config"), "Failed command preserves the file");
            h.assertTrue(messages.size() == 5, "Every reload sends exactly one response");
            h.assertTrue(messages.stream().allMatch(message -> message.startsWith(ConduitsPreventDrowned.PREFIX)),
                    "Both success and failure responses must have the mod prefix");
        } finally {
            h.getLevel().players().remove(p);
            Files.writeString(path, original);
            ConduitsPreventDrowned.CONFIG.reload();
        }
        h.succeed();
    }

    @GameTest
    public void dimensionIsolationAndExplicitSpawns(GameTestHelper h) {
        var p = player(h);
        var pos = h.absolutePos(new BlockPos(1, 2, 1));
        p.setPos(pos.getX(), pos.getY(), pos.getZ());
        p.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 100));
        var mob = EntityTypes.DROWNED.create(h.getLevel(), EntitySpawnReason.NATURAL);
        mob.setPos(pos.getX(), pos.getY(), pos.getZ());
        var otherLevel = h.getLevel().getServer().getLevel(Level.NETHER);
        otherLevel.players().add(p);
        try {
            h.assertFalse(SpawnProtection.blocks(h.getLevel(), mob), "Players in another dimension do not protect");
        } finally { otherLevel.players().remove(p); }
        h.getLevel().players().add(p);
        try {
            h.assertTrue(SpawnProtection.blocks(h.getLevel(), mob), "Same-dimension affected player protects");
            for (var reason : List.of(EntitySpawnReason.COMMAND, EntitySpawnReason.SPAWN_ITEM_USE,
                    EntitySpawnReason.CONVERSION, EntitySpawnReason.TRIAL_SPAWNER)) {
                var explicit = EntityTypes.DROWNED.create(h.getLevel(), reason);
                explicit.setPos(pos.getX(), pos.getY(), pos.getZ());
                h.assertTrue(h.getLevel().addFreshEntity(explicit), "Explicit entity addition stays allowed: " + reason);
                h.assertTrue(explicit.isAlive() && !explicit.isRemoved(), "Existing drowned stays alive: " + reason);
                explicit.discard();
            }
        } finally { h.getLevel().players().remove(p); }
        h.succeed();
    }

    @GameTest
    public void reloadCommandPermissions(GameTestHelper h) throws Exception {
        var dispatcher = h.getLevel().getServer().getCommands().getDispatcher();
        var source = h.getLevel().getServer().createCommandSourceStack().withSuppressedOutput();
        var node = dispatcher.getRoot().getChild(ConduitsPreventDrowned.ID);
        h.assertTrue(node != null, "Reload command registered");
        h.assertFalse(node.canUse(source.withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.ALL)),
                "Ordinary players cannot reload");
        var operator = source.withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER);
        h.assertTrue(node.canUse(operator), "Permission level 2 can reload");
        h.assertTrue(dispatcher.execute("conduits_prevent_drowned reload", operator) == 1,
                "Registered reload command executes successfully");
        h.succeed();
    }

    private ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
        p.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new Connection(PacketFlow.SERVERBOUND), p,
                CommonListenerCookie.createInitial(p.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {}
            @Override public boolean hasClientLoaded() { return true; }
        };
        return p;
    }

    @GameTest
    public void horizontalRangeAndEffects(GameTestHelper h) {
        var p = player(h);
        var origin = new BlockPos(0, 0, 0);
        p.setPos(3.9, 200, 4.9);
        h.assertFalse(SpawnProtection.protectedAt(List.of(p), origin, 6), "No Conduit Power means no protection");
        p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 100));
        h.assertFalse(SpawnProtection.protectedAt(List.of(p), origin, 6), "Other effects do not protect");
        p.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 100));
        h.assertTrue(SpawnProtection.protectedAt(List.of(p), origin, 6), "Height is ignored; block coordinates are used");
        h.assertFalse(SpawnProtection.protectedAt(List.of(p), origin, 5), "Exact radius is excluded");
        h.assertFalse(SpawnProtection.protectedAt(List.of(p), origin, 0), "Zero disables prevention");
        p.setPos(-0.1, -50, -0.1);
        h.assertFalse(SpawnProtection.protectedAt(List.of(p), origin, 1), "Negative coordinates use floor");
        h.assertTrue(SpawnProtection.protectedAt(List.of(p), origin, 2), "Negative position within radius");
        p.setPos(399, 100, 0);
        h.assertTrue(SpawnProtection.protectedAt(List.of(p), origin, 400), "Default range includes 399");
        p.setPos(400, 100, 0);
        h.assertFalse(SpawnProtection.protectedAt(List.of(p), origin, 400), "400 is a radius, not a global toggle");
        p.removeEffect(MobEffects.CONDUIT_POWER);
        h.assertFalse(SpawnProtection.protectedAt(List.of(p), p.blockPosition(), 400), "Effect expiration stops protection");
        h.succeed();
    }

    @GameTest
    public void multiplayerAndMobScope(GameTestHelper h) {
        var first = player(h);
        var second = player(h);
        first.setPos(0, 0, 0);
        second.setPos(2, 0, 2);
        second.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 100));
        h.assertTrue(SpawnProtection.protectedAt(List.of(first, second), BlockPos.ZERO, 10), "Any nearby affected player protects");
        h.assertFalse(SpawnProtection.protectedAt(List.of(), BlockPos.ZERO, 400), "No players in dimension means no protection");
        h.getLevel().players().add(second);
        try {
            var zombie = EntityTypes.ZOMBIE.create(h.getLevel(), EntitySpawnReason.NATURAL);
            zombie.setPos(0, 0, 0);
            h.assertFalse(SpawnProtection.blocks(h.getLevel(), zombie), "Other mobs remain untouched");
        } finally { h.getLevel().players().remove(second); }
        h.succeed();
    }

    @GameTest
    public void naturalSpawnMixin(GameTestHelper h) throws Exception {
        var p = player(h);
        var pos = h.absolutePos(new BlockPos(1, 2, 1));
        p.setPos(pos.getX() + 2, pos.getY(), pos.getZ());
        // Stable vanilla checks isolate whether the actual injected spawning method cancels.
        var mob = new Drowned(EntityTypes.DROWNED, h.getLevel()) {
            @Override public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason reason) { return true; }
            @Override public boolean checkSpawnObstruction(LevelReader level) { return true; }
        };
        mob.setPos(pos.getX(), pos.getY(), pos.getZ());
        var method = NaturalSpawner.class.getDeclaredMethod("isValidPositionForMob",
                net.minecraft.server.level.ServerLevel.class, Mob.class, double.class);
        method.setAccessible(true);
        h.getLevel().players().add(p);
        try {
            h.assertTrue((boolean) method.invoke(null, h.getLevel(), mob, 100), "Unprotected vanilla spawn allowed");
            p.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 100));
            h.assertFalse((boolean) method.invoke(null, h.getLevel(), mob, 100), "Actual natural spawn hook blocks drowned");
            p.removeEffect(MobEffects.CONDUIT_POWER);
            h.assertTrue((boolean) method.invoke(null, h.getLevel(), mob, 100), "Spawning resumes after effect ends");
        } finally { h.getLevel().players().remove(p); }
        h.succeed();
    }

    private void set(BaseSpawner spawner, String name, Object value) throws Exception {
        var field = BaseSpawner.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(spawner, value);
    }

    @GameTest
    public void realSpawnerTick(GameTestHelper h) throws Exception {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        var p = player(h);
        p.setPos(pos.getX() + 2, pos.getY(), pos.getZ());
        BaseSpawner spawner = new BaseSpawner() {
            @Override public void broadcastEvent(Level level, BlockPos at, int event) {}
        };
        var tag = new CompoundTag();
        tag.putString("id", "minecraft:drowned");
        var lights = new InclusiveRange<Integer>(0, 15);
        set(spawner, "nextSpawnData", new SpawnData(tag,
                Optional.of(new SpawnData.CustomSpawnRules(lights, lights)), Optional.empty()));
        set(spawner, "spawnDelay", 0);
        set(spawner, "spawnRange", 0);
        set(spawner, "spawnCount", 1);
        h.getLevel().players().add(p);
        try {
            var box = new net.minecraft.world.phys.AABB(pos).inflate(2);
            p.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 100));
            spawner.serverTick(h.getLevel(), pos);
            h.assertTrue(h.getLevel().getEntitiesOfClass(Drowned.class, box).isEmpty(), "Protected spawner creates no drowned");
            var delay = BaseSpawner.class.getDeclaredField("spawnDelay");
            delay.setAccessible(true);
            h.assertTrue(delay.getInt(spawner) >= 200, "Blocked spawner resets its delay");
            p.removeEffect(MobEffects.CONDUIT_POWER);
            set(spawner, "spawnDelay", 0);
            // Air is valid for drowned obstruction checks; custom light rules avoid random spawn checks.
            spawner.serverTick(h.getLevel(), pos);
            var spawned = h.getLevel().getEntitiesOfClass(Drowned.class, box);
            h.assertTrue(spawned.size() == 1, "Unprotected spawner still creates drowned");
            spawned.forEach(net.minecraft.world.entity.Entity::discard);
        } finally { h.getLevel().players().remove(p); }
        h.succeed();
    }

    @GameTest
    public void configCreationReloadAndPreservation(GameTestHelper h) throws Exception {
        var dir = Files.createTempDirectory("conduits-config-test-");
        var path = dir.resolve("config.json");
        try {
            var config = new ConfigStore(path);
            config.reload();
            h.assertTrue(config.range() == 400, "Fresh config defaults to 400");
            Files.writeString(path, "{\"preventDrownedInRange\":32,\"customNote\":\"keep\"}");
            config.reload();
            h.assertTrue(config.range() == 32, "Reload updates range");
            h.assertTrue(Files.readString(path).contains("customNote"), "Valid existing file is not rewritten");
            for (String json : List.of("{", "null", "{}", "{\"preventDrownedInRange\":-1}",
                    "{\"preventDrownedInRange\":401}", "{\"preventDrownedInRange\":1.5}",
                    "{\"preventDrownedInRange\":\"32\"}")) {
                Files.writeString(path, json);
                boolean failed = false;
                try { config.reload(); } catch (RuntimeException expected) { failed = true; }
                h.assertTrue(failed, "Invalid config rejected: " + json);
                h.assertTrue(config.range() == 32, "Failed reload keeps previous range");
                h.assertTrue(Files.readString(path).equals(json), "Invalid file is preserved");
            }
            h.assertTrue(ConfigStore.parse("{\"preventDrownedInRange\":0}") == 0, "Zero is valid");
        } finally { Files.deleteIfExists(path); Files.deleteIfExists(dir); }
        h.succeed();
    }
}
