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
 * otherwise leave regions protecting nothing. GriefPrevention's id counter is monotonic and
 * persisted, so ids are not normally reissued; the cleanup also covers the case where that counter
 * is reset or restored from an older backup while claim data survives, which would reissue ids and
 * attach a stale region to an unrelated new claim.
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
