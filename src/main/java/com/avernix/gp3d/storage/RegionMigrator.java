package com.avernix.gp3d.storage;

import com.avernix.gp3d.region.Region;
import com.avernix.gp3d.region.RegionManager;

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

    public record Result(int imported, int renumbered, int skipped) {}

    /**
     * @param source      regions read out of the other backend
     * @param target      the live manager, which writes through to the active storage
     * @param claimExists whether GriefPrevention still knows about a claim id
     */
    public static Result importInto(List<Region> source, RegionManager target,
                                    LongPredicate claimExists)
    {
        int imported = 0, renumbered = 0, skipped = 0;

        for (Region region : source)
        {
            // A region whose claim is gone would otherwise lie in wait for GriefPrevention to hand
            // that id to an unrelated new claim.
            if (!claimExists.test(region.getClaimId()))
            {
                skipped++;
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

        return new Result(imported, renumbered, skipped);
    }
}
