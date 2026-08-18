package com.trarn.gp3d.listener;

import com.trarn.gp3d.region.RegionManager;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.events.ClaimDeletedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.logging.Logger;

/**
 * Keeps region storage in step with GriefPrevention's claims.
 *
 * <p>Regions reference claims by id across two separate datastores, so a deleted claim would
 * otherwise leave regions pointing at nothing — and GP reuses claim ids from its own counter,
 * which would eventually attach a stale region to an unrelated new claim.
 */
public final class ClaimLifecycleListener implements Listener
{
    private final RegionManager manager;
    private final Logger logger;

    public ClaimLifecycleListener(RegionManager manager, Logger logger)
    {
        this.manager = manager;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClaimDeleted(ClaimDeletedEvent event)
    {
        Claim claim = event.getClaim();
        if (claim == null) return;

        int removed = removeRecursively(claim);
        if (removed > 0)
        {
            logger.info("Removed " + removed + " 3D region(s) belonging to deleted claim "
                    + claim.getID() + ".");
        }
    }

    /** GP deletes a parent's subdivisions along with it, so their regions must go too. */
    private int removeRecursively(Claim claim)
    {
        int removed = 0;
        Long id = claim.getID();
        if (id != null) removed += manager.removeForClaim(id);

        if (claim.children != null)
        {
            for (Claim child : claim.children)
            {
                removed += removeRecursively(child);
            }
        }
        return removed;
    }
}
