package com.avernix.gp3d.region;

import me.ryanhamshire.GriefPrevention.ClaimPermission;
import org.bukkit.Location;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A vertical slice of a GriefPrevention claim.
 *
 * <p>Regions are not GriefPrevention subdivisions. GP's {@code BoundingBox(Claim)} constructor
 * forces {@code maxY} to the world height, so two GP subdivisions sharing a footprint always
 * report as overlapping regardless of their Y values &mdash; which makes stacked subdivisions
 * impossible to create. Regions therefore live entirely in this plugin's storage and are applied
 * on top of GP's decisions via {@code ClaimPermissionCheckEvent}.
 */
public final class Region
{
    /** Sentinel key for trust granted to everybody. */
    public static final UUID PUBLIC = new UUID(0L, 0L);

    private long id;
    private final long claimId;
    private final String world;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;
    private final UUID owner;
    private String name;
    private int priority;
    private final Map<UUID, ClaimPermission> trust = new HashMap<>();

    public Region(long id, long claimId, String world, UUID owner, String name, int priority,
                  int x1, int y1, int z1, int x2, int y2, int z2)
    {
        this.id = id;
        this.claimId = claimId;
        this.world = world;
        this.owner = owner;
        this.name = name;
        this.priority = priority;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getClaimId() { return claimId; }
    public String getWorld() { return world; }
    public UUID getOwner() { return owner; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }

    public Map<UUID, ClaimPermission> getTrust() { return Collections.unmodifiableMap(trust); }

    public void setTrust(UUID player, ClaimPermission permission) { trust.put(player, permission); }
    public boolean dropTrust(UUID player) { return trust.remove(player) != null; }
    public void loadTrust(UUID player, ClaimPermission permission) { trust.put(player, permission); }

    public boolean contains(Location location)
    {
        if (location.getWorld() == null || !location.getWorld().getName().equals(world)) return false;
        return contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public boolean contains(int x, int y, int z)
    {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** Used to pick the innermost region when several overlap. */
    public long getVolume()
    {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    /**
     * Whether {@code player} may perform an action needing {@code required} inside this region.
     * Defers to GP's own {@link ClaimPermission#isGrantedBy} so the trust hierarchy stays
     * identical to the rest of the server (Edit > Build > Container > Access, Manage separate).
     */
    public boolean grants(UUID player, ClaimPermission required)
    {
        if (required == null) return false;
        if (player != null && player.equals(owner)) return true;
        if (player != null && required.isGrantedBy(trust.get(player))) return true;
        return required.isGrantedBy(trust.get(PUBLIC));
    }

    public String describeBounds()
    {
        return "(" + minX + ", " + minZ + ") to (" + maxX + ", " + maxZ + ")  Y " + minY + "-" + maxY;
    }
}
