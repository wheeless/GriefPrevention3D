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
package com.trarn.gp3d.storage;

import com.trarn.gp3d.region.Region;
import com.trarn.gp3d.region.RegionManager;

import java.util.List;
import java.util.function.LongPredicate;

/**
 * Moves regions from one backend into the active one.
 *
 * <p>Kept separate from the command so the interesting decisions — id collisions and regions whose
 * claim has since been deleted — can be tested without a running server.
 */
public final class RegionMigrator
{
    private RegionMigrator() {}

    public record Result(int imported, int renumbered, int skipped, int conflicted) {}

    /**
     * @param source      regions read out of the other backend
     * @param target      the live manager, which writes through to the active storage
     * @param claimExists whether GriefPrevention still knows about a claim id
     */
    public static Result importInto(List<Region> source, RegionManager target,
                                    LongPredicate claimExists)
    {
        int imported = 0, renumbered = 0, skipped = 0, conflicted = 0;

        for (Region region : source)
        {
            // A region whose claim is gone would otherwise lie in wait for GriefPrevention to hand
            // that id to an unrelated new claim.
            if (!claimExists.test(region.getClaimId()))
            {
                skipped++;
                continue;
            }

            // Importing an overlap would reintroduce exactly the ambiguity creation forbids.
            if (target.findOverlap(region) != null)
            {
                conflicted++;
                continue;
            }

            if (target.byId(region.getId()) != null)
            {
                region.setId(target.nextId());
                renumbered++;
            }

            target.add(region);
            imported++;
        }

        return new Result(imported, renumbered, skipped, conflicted);
    }
}
