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
