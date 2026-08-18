/*
 * GriefPrevention3D - vertically bounded regions inside GriefPrevention claims.
 * Copyright (C) 2026 Trarn
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package me.ryanhamshire.GriefPrevention;

import com.trarn.gp3d.region.Region;
import com.trarn.gp3d.region.RegionManager;
import com.trarn.gp3d.util.BypassRule;
import com.trarn.gp3d.storage.MySqlRegionStorage;
import com.trarn.gp3d.storage.RegionMigrator;
import com.trarn.gp3d.storage.RegionStorage;
import com.trarn.gp3d.storage.SqliteRegionStorage;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Exercises the logic that decides who may act where. Lives in GriefPrevention's package so it can
 * build real Claim objects (the no-arg constructor and `id` field are package-private).
 */
public class Gp3dTest
{
    private static int passed = 0;
    private static int failed = 0;
    private static World world;

    public static void main(String[] args) throws Exception
    {
        world = fakeWorld("world");

        trustHierarchy();
        containment();
        stackedRegions();
        nestedRegions();
        claimChainLookup();
        overlapRule();
        resizing();
        chunkIndex();
        bypassRule();
        storageRoundTrip();
        migration();
        mysqlRoundTrip();

        System.out.println();
        System.out.println(failed == 0
                ? "ALL PASSED (" + passed + " assertions)"
                : failed + " FAILED, " + passed + " passed");
        if (failed > 0) System.exit(1);
    }

    // ---- tests ---------------------------------------------------------------------------

    /** Region trust must behave exactly like GP trust: Build implies Container implies Access. */
    private static void trustHierarchy()
    {
        section("trust hierarchy");
        UUID bob = UUID.randomUUID();
        UUID eve = UUID.randomUUID();
        Region region = region(1, 100, 0, 0, 64, 10, 10, 80);
        region.setTrust(bob, ClaimPermission.Build);

        check("build trust grants build", region.grants(bob, ClaimPermission.Build));
        check("build trust grants container", region.grants(bob, ClaimPermission.Container));
        check("build trust grants access", region.grants(bob, ClaimPermission.Access));
        check("build trust does NOT grant manage", !region.grants(bob, ClaimPermission.Manage));
        check("build trust does NOT grant edit", !region.grants(bob, ClaimPermission.Edit));
        check("untrusted player is denied", !region.grants(eve, ClaimPermission.Access));

        Region containerOnly = region(2, 100, 0, 0, 64, 10, 10, 80);
        containerOnly.setTrust(bob, ClaimPermission.Container);
        check("container trust grants container", containerOnly.grants(bob, ClaimPermission.Container));
        check("container trust grants access", containerOnly.grants(bob, ClaimPermission.Access));
        check("container trust does NOT grant build", !containerOnly.grants(bob, ClaimPermission.Build));

        Region publicRegion = region(3, 100, 0, 0, 64, 10, 10, 80);
        publicRegion.setTrust(Region.PUBLIC, ClaimPermission.Access);
        check("public access applies to anyone", publicRegion.grants(eve, ClaimPermission.Access));
        check("public access does not grant build", !publicRegion.grants(eve, ClaimPermission.Build));

        Region managed = region(4, 100, 0, 0, 64, 10, 10, 80);
        managed.setTrust(bob, ClaimPermission.Manage);
        check("manage trust grants manage", managed.grants(bob, ClaimPermission.Manage));
        check("manage trust does NOT grant build", !managed.grants(bob, ClaimPermission.Build));
        check("manage trust does NOT grant edit", !managed.grants(bob, ClaimPermission.Edit));
        check("manage trust does NOT grant container", !managed.grants(bob, ClaimPermission.Container));

        check("region owner always allowed", region.grants(region.getOwner(), ClaimPermission.Edit));
    }

    private static void containment()
    {
        section("containment boundaries");
        Region region = region(1, 100, 0, 0, 64, 10, 10, 80);

        check("min corner inclusive", region.contains(0, 64, 0));
        check("max corner inclusive", region.contains(10, 80, 10));
        check("one below band excluded", !region.contains(5, 63, 5));
        check("one above band excluded", !region.contains(5, 81, 5));
        check("outside footprint excluded", !region.contains(11, 70, 5));
        check("inside band included", region.contains(5, 72, 5));
        check("volume is inclusive", region.getVolume() == 11L * 17L * 11L);

        check("location in other world excluded",
                !region.contains(new Location(fakeWorld("nether"), 5, 72, 5)));
        check("location in same world included",
                region.contains(new Location(world, 5, 72, 5)));
    }

    /** The whole point of the design: two regions sharing a footprint at different heights. */
    private static void stackedRegions() throws Exception
    {
        section("stacked regions");
        RegionManager manager = manager();
        Claim claim = claim(100, UUID.randomUUID(), null);

        Region ground = region(1, 100, 0, 0, 64, 15, 15, 79);
        Region upper = region(2, 100, 0, 0, 80, 15, 15, 95);
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        ground.setTrust(alice, ClaimPermission.Build);
        upper.setTrust(bob, ClaimPermission.Build);
        manager.add(ground);
        manager.add(upper);

        Region atY70 = manager.findGoverning(claim, new Location(world, 5, 70, 5));
        Region atY85 = manager.findGoverning(claim, new Location(world, 5, 85, 5));
        Region atY120 = manager.findGoverning(claim, new Location(world, 5, 120, 5));

        check("y=70 resolves to ground floor", atY70 != null && atY70.getId() == 1);
        check("y=85 resolves to upper floor", atY85 != null && atY85.getId() == 2);
        check("y=120 resolves to no region", atY120 == null);

        check("alice may build downstairs", atY70.grants(alice, ClaimPermission.Build));
        check("bob may NOT build downstairs", !atY70.grants(bob, ClaimPermission.Build));
        check("bob may build upstairs", atY85.grants(bob, ClaimPermission.Build));
        check("alice may NOT build upstairs", !atY85.grants(alice, ClaimPermission.Build));
    }

    /** When regions overlap, the smallest (innermost) one governs. */
    private static void nestedRegions() throws Exception
    {
        section("nested regions");
        RegionManager manager = manager();
        Claim claim = claim(100, UUID.randomUUID(), null);

        Region big = region(1, 100, 0, 0, 64, 50, 50, 120);
        Region small = region(2, 100, 10, 10, 70, 20, 20, 80);
        manager.add(big);
        manager.add(small);

        Region inner = manager.findGoverning(claim, new Location(world, 15, 75, 15));
        Region outer = manager.findGoverning(claim, new Location(world, 40, 75, 40));
        check("innermost region wins on overlap", inner != null && inner.getId() == 2);
        check("outer region governs elsewhere", outer != null && outer.getId() == 1);

        small.setPriority(-5);
        Region afterPriority = manager.findGoverning(claim, new Location(world, 15, 75, 15));
        check("priority beats volume", afterPriority != null && afterPriority.getId() == 1);

        check("findAllAt returns both", manager.findAllAt(claim, new Location(world, 15, 75, 15)).size() == 2);
    }

    /** A region on a parent claim must still apply when GP resolves an inner subdivision. */
    private static void claimChainLookup() throws Exception
    {
        section("claim chain lookup");
        RegionManager manager = manager();
        Claim parent = claim(100, UUID.randomUUID(), null);
        Claim subdivision = claim(101, parent.ownerID, parent);

        Region onParent = region(1, 100, 0, 0, 64, 50, 50, 80);
        manager.add(onParent);

        check("parent claim has regions", manager.hasAnyRegion(parent));
        check("subdivision sees parent's regions", manager.hasAnyRegion(subdivision));
        check("region resolves through subdivision",
                manager.findGoverning(subdivision, new Location(world, 5, 70, 5)) != null);

        Claim unrelated = claim(999, UUID.randomUUID(), null);
        check("unrelated claim has no regions", !manager.hasAnyRegion(unrelated));

        check("removeForClaim drops them", manager.removeForClaim(100) == 1);
        check("gone after removal", !manager.hasAnyRegion(parent));
    }

    private static void storageRoundTrip() throws Exception
    {
        section("sqlite round trip");
        File file = Files.createTempFile("gp3d-test", ".db").toFile();
        file.delete();
        Logger logger = Logger.getLogger("gp3d-test");

        UUID owner = UUID.randomUUID();
        UUID friend = UUID.randomUUID();

        SqliteRegionStorage storage = new SqliteRegionStorage(file, "gp3d_", logger);
        storage.initialise();
        RegionManager manager = new RegionManager(storage);
        manager.loadAll();

        check("starts empty", manager.size() == 0);
        check("first id is 1", manager.nextId() == 1L);

        Region region = new Region(manager.nextId(), 42, "world", owner, "Shop", 3,
                0, 64, 0, 10, 80, 10);
        region.setTrust(friend, ClaimPermission.Container);
        region.setTrust(Region.PUBLIC, ClaimPermission.Access);
        manager.add(region);
        check("second id is 2", manager.nextId() == 2L);
        storage.shutdown();

        // Reopen from scratch to prove everything survived the write.
        SqliteRegionStorage reopened = new SqliteRegionStorage(file, "gp3d_", logger);
        reopened.initialise();
        RegionManager reloaded = new RegionManager(reopened);
        reloaded.loadAll();

        check("one region persisted", reloaded.size() == 1);
        Region back = reloaded.byId(1);
        check("region survived", back != null);
        check("claim id survived", back.getClaimId() == 42);
        check("name survived", "Shop".equals(back.getName()));
        check("priority survived", back.getPriority() == 3);
        check("owner survived", owner.equals(back.getOwner()));
        check("bounds survived",
                back.getMinY() == 64 && back.getMaxY() == 80 && back.getMaxX() == 10);
        check("explicit trust survived", back.grants(friend, ClaimPermission.Container));
        check("public trust survived", back.grants(UUID.randomUUID(), ClaimPermission.Access));
        check("untrusted still denied", !back.grants(UUID.randomUUID(), ClaimPermission.Build));

        reloaded.remove(1);
        reopened.shutdown();

        SqliteRegionStorage third = new SqliteRegionStorage(file, "gp3d_", logger);
        third.initialise();
        RegionManager empty = new RegionManager(third);
        empty.loadAll();
        check("deletion persisted", empty.size() == 0);
        third.shutdown();
        file.delete();
    }

    /** Import must renumber id collisions and drop regions whose claim is gone. */
    private static void migration() throws Exception
    {
        section("migration between backends");
        RegionManager target = manager();

        Region existing = region(1, 100, 0, 0, 64, 10, 10, 80);
        target.add(existing);

        UUID friend = UUID.randomUUID();
        Region collides = region(1, 100, 20, 20, 64, 30, 30, 80);   // same id as existing
        collides.setTrust(friend, ClaimPermission.Build);
        Region clean = region(7, 100, 40, 40, 64, 50, 50, 80);
        Region orphan = region(8, 555, 0, 0, 64, 10, 10, 80);       // claim 555 does not exist

        RegionMigrator.Result result = RegionMigrator.importInto(
                List.of(collides, clean, orphan), target, claimId -> claimId == 100);

        check("imported the two live regions", result.imported() == 2);
        check("renumbered the id collision", result.renumbered() == 1);
        check("skipped the orphan", result.skipped() == 1);
        check("target now holds three", target.size() == 3);
        check("original survived untouched", target.byId(1) == existing);
        check("collision got a fresh id", collides.getId() != 1);
        check("renumbered region kept its trust", collides.grants(friend, ClaimPermission.Build));
        check("clean region kept its id", target.byId(7) == clean);
        check("orphan was not imported", target.byId(8) == null);
        check("nothing conflicted in this batch", result.conflicted() == 0);

        // An import that would land on top of an existing region is refused, not merged.
        Region wouldOverlap = region(9, 100, 2, 2, 66, 8, 8, 70);
        RegionMigrator.Result second = RegionMigrator.importInto(
                List.of(wouldOverlap), target, claimId -> claimId == 100);
        check("overlapping import refused", second.conflicted() == 1 && second.imported() == 0);
    }

    /**
     * Exercises the MySQL dialect against a real server when one is configured, since the DDL and
     * the upsert clause differ from SQLite and neither is checked by compiling.
     */
    private static void mysqlRoundTrip() throws Exception
    {
        section("mysql round trip");
        String host = System.getenv("GP3D_MYSQL_HOST");
        if (host == null || host.isBlank())
        {
            System.out.println("  skipped (set GP3D_MYSQL_HOST to run)");
            return;
        }

        int port = Integer.parseInt(System.getenv().getOrDefault("GP3D_MYSQL_PORT", "3306"));
        String db = System.getenv().getOrDefault("GP3D_MYSQL_DB", "gp3d");
        String user = System.getenv().getOrDefault("GP3D_MYSQL_USER", "root");
        String pass = System.getenv().getOrDefault("GP3D_MYSQL_PASS", "");
        String prefix = "gp3dtest_";
        Logger logger = Logger.getLogger("gp3d-mysql-test");

        UUID owner = UUID.randomUUID();
        UUID friend = UUID.randomUUID();

        RegionStorage storage = new MySqlRegionStorage(host, port, db, user, pass,
                "useSSL=false&allowPublicKeyRetrieval=true", prefix, logger);
        storage.initialise();

        RegionManager manager = new RegionManager(storage);
        manager.loadAll();
        int baseline = manager.size();

        Region region = new Region(manager.nextId(), 42, "world", owner, "Shop", 3,
                0, 64, 0, 10, 80, 10);
        region.setTrust(friend, ClaimPermission.Container);
        region.setTrust(Region.PUBLIC, ClaimPermission.Access);
        manager.add(region);
        long id = region.getId();

        // Save again to prove the upsert path updates rather than throwing on duplicate key.
        region.setName("Renamed");
        manager.add(region);
        storage.shutdown();

        RegionStorage reopened = new MySqlRegionStorage(host, port, db, user, pass,
                "useSSL=false&allowPublicKeyRetrieval=true", prefix, logger);
        reopened.initialise();
        RegionManager reloaded = new RegionManager(reopened);
        reloaded.loadAll();

        Region back = reloaded.byId(id);
        check("region persisted to mysql", back != null);
        check("upsert updated rather than duplicated", reloaded.size() == baseline + 1);
        check("name update survived", back != null && "Renamed".equals(back.getName()));
        check("bounds survived", back != null && back.getMinY() == 64 && back.getMaxY() == 80);
        check("owner survived", back != null && owner.equals(back.getOwner()));
        check("explicit trust survived", back != null && back.grants(friend, ClaimPermission.Container));
        check("public trust survived", back != null && back.grants(UUID.randomUUID(), ClaimPermission.Access));

        reloaded.remove(id);
        reopened.shutdown();

        RegionStorage third = new MySqlRegionStorage(host, port, db, user, pass,
                "useSSL=false&allowPublicKeyRetrieval=true", prefix, logger);
        third.initialise();
        RegionManager empty = new RegionManager(third);
        empty.loadAll();
        check("deletion persisted", empty.byId(id) == null);
        third.shutdown();
    }

    /**
     * A region must never be more permissive than the claim around it, and must not quietly stop
     * applying the moment someone is opped.
     */
    private static void bypassRule()
    {
        section("admin bypass mirrors GriefPrevention");
        ClaimPermission build = ClaimPermission.Build;

        // The trap: every op holds griefprevention.ignoreclaims by default.
        check("op WITHOUT /ignoreclaims does not bypass",
                !BypassRule.bypasses(false, false, false, true, true, build));
        check("op WITH /ignoreclaims does bypass",
                BypassRule.bypasses(false, false, true, true, true, build));
        check("toggle without the permission does not bypass",
                !BypassRule.bypasses(false, false, true, false, false, build));
        check("ordinary player never bypasses",
                !BypassRule.bypasses(false, false, false, false, false, build));

        check("adminclaims permission bypasses an admin claim",
                BypassRule.bypasses(true, true, false, false, false, build));
        check("adminclaims permission is irrelevant on a normal claim",
                !BypassRule.bypasses(false, true, false, false, false, build));

        check("deleteclaims bypasses Edit outright",
                BypassRule.bypasses(false, false, false, false, true, ClaimPermission.Edit));
        check("ignoreclaims alone does not bypass Edit",
                !BypassRule.bypasses(false, false, true, true, false, ClaimPermission.Edit));
    }

    /**
     * Regions must never share a block, so that exactly one region governs any position. The
     * important half of this is what stays *allowed*: bands stacked at different heights.
     */
    private static void overlapRule() throws Exception
    {
        section("overlap rule");
        RegionManager manager = manager();

        Region ground = region(1, 100, 0, 0, 64, 15, 15, 79);
        manager.add(ground);

        // Stacking is the whole point, so it must not read as a collision.
        Region upstairs = region(2, 100, 0, 0, 80, 15, 15, 95);
        check("band directly above does not overlap", manager.findOverlap(upstairs) == null);

        Region touching = region(3, 100, 0, 0, 79, 15, 15, 95);
        check("band sharing one Y level does overlap", manager.findOverlap(touching) != null);

        Region nested = region(4, 100, 5, 5, 70, 10, 10, 75);
        check("region nested inside another overlaps", manager.findOverlap(nested) != null);

        Region enclosing = region(5, 100, -10, -10, 0, 40, 40, 200);
        check("region enclosing another overlaps", manager.findOverlap(enclosing) != null);

        Region corner = region(6, 100, 15, 15, 70, 25, 25, 75);
        check("partial corner overlap detected", manager.findOverlap(corner) != null);

        Region beside = region(7, 100, 16, 0, 64, 30, 15, 79);
        check("region beside another does not overlap", manager.findOverlap(beside) == null);

        Region elsewhere = new Region(8, 100, "nether", UUID.randomUUID(), null, 0,
                0, 64, 0, 15, 79, 15);
        check("same box in another world does not overlap", manager.findOverlap(elsewhere) == null);

        check("a region does not overlap itself", manager.findOverlap(ground) == null);

        // Once stacked, both must still resolve correctly.
        manager.add(upstairs);
        Claim claim = claim(100, UUID.randomUUID(), null);
        Region atY70 = manager.findGoverning(claim, new Location(world, 5, 70, 5));
        Region atY85 = manager.findGoverning(claim, new Location(world, 5, 85, 5));
        check("stacked regions still resolve independently",
                atY70 != null && atY70.getId() == 1 && atY85 != null && atY85.getId() == 2);
    }

    /** A resize must keep the region's identity, and must not be allowed to eat a neighbour. */
    private static void resizing() throws Exception
    {
        section("resizing");
        RegionManager manager = manager();

        UUID friend = UUID.randomUUID();
        Region shop = region(1, 100, 0, 0, 64, 15, 15, 79);
        shop.setTrust(friend, ClaimPermission.Container);
        UUID owner = shop.getOwner();
        shop.setName("Shop");
        manager.add(shop);

        Region upstairs = region(2, 100, 0, 0, 90, 15, 15, 99);
        manager.add(upstairs);

        // Growing into free space below is fine.
        shop.setBounds(0, 60, 0, 15, 85, 15);
        check("bounds updated", shop.getMinY() == 60 && shop.getMaxY() == 85);
        check("id preserved across resize", shop.getId() == 1);
        check("owner preserved across resize", owner.equals(shop.getOwner()));
        check("name preserved across resize", "Shop".equals(shop.getName()));
        check("trust preserved across resize", shop.grants(friend, ClaimPermission.Container));
        check("still indexed under the same id", manager.byId(1) == shop);

        // Self-exclusion: a region must not collide with its own current box.
        check("region does not clash with itself", manager.findOverlap(shop) == null);

        // Growing up into the neighbour must be caught.
        check("resize into a neighbour is detected",
                manager.findOverlap(shop.getWorld(), 0, 60, 0, 15, 95, 15, shop.getId()) != null);

        // ignoreId excludes exactly one region and nothing else: upstairs' own box reads as
        // occupied normally, and free only when upstairs itself is the one being ignored.
        check("neighbour's box is occupied by default",
                manager.findOverlap(shop.getWorld(), 0, 90, 0, 15, 99, 15, -1L) != null);
        check("ignoreId excludes only the named region",
                manager.findOverlap(shop.getWorld(), 0, 90, 0, 15, 99, 15, 2L) == null);

        Claim claim = claim(100, UUID.randomUUID(), null);
        check("resized region governs its new lower band",
                manager.findGoverning(claim, new Location(world, 5, 62, 5)) == shop);
    }

    /**
     * The chunk index is the fast rejection for boundary protection, running on every flowing-water
     * tick. A false negative there would silently disable the protection, so it is checked directly.
     */
    private static void chunkIndex() throws Exception
    {
        section("boundary chunk index");
        RegionManager manager = manager();
        check("empty manager reports empty", manager.isEmpty());
        check("nothing is relevant when there are no regions",
                !manager.mayHaveRegionAt("world", 0, 0));

        // Spans chunks (0,0) and (1,1): blocks 8..20 on both axes.
        Region region = region(1, 100, 8, 8, 64, 20, 20, 79);
        manager.add(region);

        check("manager no longer empty", !manager.isEmpty());
        check("origin chunk flagged", manager.mayHaveRegionAt("world", 8, 8));
        check("far corner chunk flagged", manager.mayHaveRegionAt("world", 20, 20));
        check("chunk 0,1 flagged", manager.mayHaveRegionAt("world", 8, 20));
        check("chunk 1,0 flagged", manager.mayHaveRegionAt("world", 20, 8));
        check("a block in a flagged chunk but outside the box still counts",
                manager.mayHaveRegionAt("world", 0, 0));
        check("distant chunk not flagged", manager.mayHaveRegionAt("world", 500, 500) == false);
        check("other world not flagged", !manager.mayHaveRegionAt("nether", 8, 8));

        // Negative coordinates use arithmetic shift, which floors — the common off-by-one here.
        Region negative = region(2, 100, -40, -40, 64, -35, -35, 79);
        manager.add(negative);
        check("negative-coordinate chunk flagged", manager.mayHaveRegionAt("world", -40, -40));
        check("negative chunk neighbour not flagged", manager.mayHaveRegionAt("world", -200, -200) == false);

        // A single-block region must still register its own chunk.
        Region tiny = region(3, 100, 1000, 1000, 70, 1000, 1000, 70);
        manager.add(tiny);
        check("single-block region flags its chunk", manager.mayHaveRegionAt("world", 1000, 1000));

        manager.remove(3);
        check("index updated after removal", !manager.mayHaveRegionAt("world", 1000, 1000));

        // Resizing moves the footprint, so the index has to follow.
        negative.setBounds(-40, 64, -40, -35, 79, -35);
        manager.persist(negative);
        check("index still correct after resize", manager.mayHaveRegionAt("world", -40, -40));
    }

    // ---- helpers -------------------------------------------------------------------------

    private static RegionManager manager() throws Exception
    {
        RegionManager manager = new RegionManager(new NoopStorage());
        manager.loadAll();
        return manager;
    }

    /** Footprint (minX,minZ)-(maxX,maxZ) with a vertical band of minY..maxY. */
    private static Region region(long id, long claimId,
                                 int minX, int minZ, int minY,
                                 int maxX, int maxZ, int maxY)
    {
        return new Region(id, claimId, "world", UUID.randomUUID(), null, 0,
                minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Claim claim(long id, UUID owner, Claim parent)
    {
        Claim claim = new Claim();
        claim.id = id;
        claim.ownerID = owner;
        claim.parent = parent;
        claim.managers = new ArrayList<>();
        return claim;
    }

    private static World fakeWorld(String name)
    {
        return (World) Proxy.newProxyInstance(
                Gp3dTest.class.getClassLoader(),
                new Class<?>[] { World.class },
                (proxy, method, methodArgs) -> switch (method.getName())
                {
                    case "getName" -> name;
                    case "getMinHeight" -> -64;
                    case "getMaxHeight" -> 320;
                    case "equals" -> proxy == methodArgs[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "World(" + name + ")";
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type)
    {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0d;
        if (type == float.class) return 0.0f;
        return null;
    }

    private static final class NoopStorage implements com.trarn.gp3d.storage.RegionStorage
    {
        public void initialise() {}
        public List<Region> loadRegions() { return new ArrayList<>(); }
        public long nextId() { return 1L; }
        public void saveRegionAsync(Region region) {}
        public void saveTrustAsync(Region region) {}
        public void deleteRegionAsync(long regionId) {}
        public void deleteRegionsForClaimAsync(long claimId) {}
        public String describe() { return "in-memory (test)"; }
        public void shutdown() {}
    }

    private static void section(String name)
    {
        System.out.println();
        System.out.println("== " + name);
    }

    private static void check(String label, boolean condition)
    {
        if (condition) { passed++; System.out.println("  ok   " + label); }
        else { failed++; System.out.println("  FAIL " + label); }
    }
}
