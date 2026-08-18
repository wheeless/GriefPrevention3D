package com.avernix.gp3d.region;

import com.avernix.gp3d.storage.RegionStorage;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Authoritative in-memory index of every {@link Region}, backed by {@link RegionStorage}.
 *
 * <p>Lookups happen on the permission-check hot path (every block break, every container open),
 * so reads never touch disk. Mutations write through to storage asynchronously.
 */
public final class RegionManager
{
    private final RegionStorage storage;
    private final Map<Long, List<Region>> byClaim = new HashMap<>();
    private final Map<Long, Region> byId = new HashMap<>();

    public RegionManager(RegionStorage storage)
    {
        this.storage = storage;
    }

    public synchronized void loadAll() throws Exception
    {
        byClaim.clear();
        byId.clear();
        for (Region region : storage.loadRegions())
        {
            index(region);
        }
    }

    private void index(Region region)
    {
        byId.put(region.getId(), region);
        byClaim.computeIfAbsent(region.getClaimId(), k -> new ArrayList<>()).add(region);
    }

    public synchronized int size() { return byId.size(); }

    public synchronized Region byId(long id) { return byId.get(id); }

    public synchronized List<Region> forClaim(long claimId)
    {
        List<Region> list = byClaim.get(claimId);
        return list == null ? Collections.emptyList() : new ArrayList<>(list);
    }

    public synchronized Collection<Region> all() { return new ArrayList<>(byId.values()); }

    /**
     * Fast rejection for the permission hot path: true only if this claim, or any claim above it,
     * carries at least one region.
     */
    public synchronized boolean hasAnyRegion(Claim claim)
    {
        for (Claim c = claim; c != null; c = c.parent)
        {
            Long id = c.getID();
            if (id != null && byClaim.containsKey(id)) return true;
        }
        return false;
    }

    /**
     * The region that should govern {@code location}, searching the claim and its ancestors.
     * Innermost wins: highest priority first, then smallest volume.
     */
    public synchronized Region findGoverning(Claim claim, Location location)
    {
        Region best = null;
        for (Claim c = claim; c != null; c = c.parent)
        {
            Long id = c.getID();
            if (id == null) continue;
            List<Region> candidates = byClaim.get(id);
            if (candidates == null) continue;
            for (Region region : candidates)
            {
                if (!region.contains(location)) continue;
                if (best == null || beats(region, best)) best = region;
            }
        }
        return best;
    }

    /**
     * Innermost first: highest priority, then smallest volume, then id as a stable tiebreak.
     * Written as a real total order because a sort comparator that only knows "beats" can throw
     * "Comparison method violates its general contract" once more than two regions overlap.
     */
    private static final java.util.Comparator<Region> INNERMOST_FIRST =
            java.util.Comparator.comparingInt(Region::getPriority).reversed()
                    .thenComparingLong(Region::getVolume)
                    .thenComparingLong(Region::getId);

    private static boolean beats(Region candidate, Region incumbent)
    {
        if (candidate.getPriority() != incumbent.getPriority())
        {
            return candidate.getPriority() > incumbent.getPriority();
        }
        return candidate.getVolume() < incumbent.getVolume();
    }

    /** Every region containing this location, innermost first. */
    public synchronized List<Region> findAllAt(Claim claim, Location location)
    {
        List<Region> found = new ArrayList<>();
        for (Claim c = claim; c != null; c = c.parent)
        {
            Long id = c.getID();
            if (id == null) continue;
            List<Region> candidates = byClaim.get(id);
            if (candidates == null) continue;
            for (Region region : candidates)
            {
                if (region.contains(location)) found.add(region);
            }
        }
        found.sort(INNERMOST_FIRST);
        return found;
    }

    public synchronized int countOwnedBy(UUID owner)
    {
        int count = 0;
        for (Region region : byId.values())
        {
            if (owner.equals(region.getOwner())) count++;
        }
        return count;
    }

    /**
     * Next free region id, derived from the in-memory index so claim creation never blocks on
     * disk I/O on the main thread.
     */
    public synchronized long nextId()
    {
        long max = 0L;
        for (Long id : byId.keySet())
        {
            if (id > max) max = id;
        }
        return max + 1L;
    }

    public synchronized void add(Region region)
    {
        index(region);
        storage.saveRegionAsync(region);
    }

    public synchronized boolean remove(long regionId)
    {
        Region region = byId.remove(regionId);
        if (region == null) return false;
        List<Region> list = byClaim.get(region.getClaimId());
        if (list != null)
        {
            list.remove(region);
            if (list.isEmpty()) byClaim.remove(region.getClaimId());
        }
        storage.deleteRegionAsync(regionId);
        return true;
    }

    /** Drops every region attached to a claim, used when GriefPrevention deletes one. */
    public synchronized int removeForClaim(long claimId)
    {
        List<Region> list = byClaim.remove(claimId);
        if (list == null || list.isEmpty()) return 0;
        for (Region region : list)
        {
            byId.remove(region.getId());
        }
        storage.deleteRegionsForClaimAsync(claimId);
        return list.size();
    }

    public synchronized void persistTrust(Region region)
    {
        storage.saveTrustAsync(region);
    }

    /** Removes regions whose backing claim no longer exists. */
    public synchronized int pruneOrphans(java.util.function.LongPredicate claimExists)
    {
        List<Long> dead = new ArrayList<>();
        for (Long claimId : byClaim.keySet())
        {
            if (!claimExists.test(claimId)) dead.add(claimId);
        }
        int removed = 0;
        for (Long claimId : dead)
        {
            removed += removeForClaim(claimId);
        }
        return removed;
    }
}
