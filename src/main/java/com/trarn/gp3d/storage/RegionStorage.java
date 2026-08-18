package com.trarn.gp3d.storage;

import com.trarn.gp3d.region.Region;

import java.util.List;

public interface RegionStorage
{
    void initialise() throws Exception;

    List<Region> loadRegions() throws Exception;

    /** Reserves the next region id. */
    long nextId() throws Exception;

    void saveRegionAsync(Region region);

    void saveTrustAsync(Region region);

    void deleteRegionAsync(long regionId);

    void deleteRegionsForClaimAsync(long claimId);

    /** Human-readable description of where the data lives, for logs and the migrate command. */
    String describe();

    /** Flushes pending writes and releases resources. */
    void shutdown();
}
