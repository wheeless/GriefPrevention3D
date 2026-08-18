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

import com.trarn.gp3d.GP3DPlugin;
import com.trarn.gp3d.region.Region;
import com.trarn.gp3d.region.RegionManager;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Stops physics from carrying a player's reach across a region boundary.
 *
 * <p>{@code ClaimPermissionCheckEvent} only fires where GriefPrevention has a player to check. A
 * piston has none: GP treats it as a claim-boundary question in {@code onPistonEvent}, comparing the
 * piston's claim against the moved blocks' claim, and never asks about trust. That leaves a real
 * escalation — someone trusted only inside a band can place a piston in it and push blocks out into
 * the parent claim, which is exactly the kind of indirect grief a vertical claim invites.
 *
 * <p>The rule here mirrors GP's, one level down: <em>a mechanism may not move a block across a
 * region boundary in either direction</em>. Both directions matter — outward is the escalation,
 * inward would let anyone with parent-claim trust reach into a rented band.
 *
 * <p>Blocking rather than permission-checking is the only sound option, because at the time these
 * events fire there is no player to attribute them to.
 */
public final class BoundaryListener implements Listener
{
    private final GP3DPlugin plugin;
    private final RegionManager manager;

    public BoundaryListener(GP3DPlugin plugin, RegionManager manager)
    {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event)
    {
        if (crossesBoundary(event.getBlock(), event.getBlocks(), event.getDirection()))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event)
    {
        if (crossesBoundary(event.getBlock(), event.getBlocks(), event.getDirection()))
        {
            event.setCancelled(true);
        }
    }

    /** Water and lava spilling over a boundary. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFromTo(BlockFromToEvent event)
    {
        if (differentRegions(event.getBlock().getLocation(), event.getToBlock().getLocation()))
        {
            event.setCancelled(true);
        }
    }

    /** Fire and similar spreading over a boundary. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event)
    {
        if (differentRegions(event.getSource().getLocation(), event.getBlock().getLocation()))
        {
            event.setCancelled(true);
        }
    }

    /** A sapling inside a band must not grow its canopy out into the claim, or vice versa. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event)
    {
        Location origin = event.getLocation();
        if (!isRelevant(origin.getWorld(), origin.getBlockX(), origin.getBlockZ())
                && event.getBlocks().stream().noneMatch(
                        state -> isRelevant(state.getWorld(), state.getX(), state.getZ())))
        {
            return;
        }

        Region source = regionAt(origin);
        // Drop only the blocks that would land on the far side of a boundary, so the rest still grows.
        event.getBlocks().removeIf(state -> regionAt(state.getLocation()) != source);
    }

    // ---- shared logic --------------------------------------------------------------------

    private boolean crossesBoundary(Block piston, List<Block> moved, BlockFace direction)
    {
        if (manager.isEmpty() || !plugin.protectBoundaries()) return false;

        // Fast path: if nothing involved is even in a chunk containing a region, stop here.
        boolean relevant = isRelevant(piston.getWorld(), piston.getX(), piston.getZ());
        if (!relevant)
        {
            for (Block block : moved)
            {
                if (isRelevant(block.getWorld(), block.getX(), block.getZ())
                        || isRelevant(block.getWorld(), block.getX() + direction.getModX(),
                                block.getZ() + direction.getModZ()))
                {
                    relevant = true;
                    break;
                }
            }
        }
        if (!relevant) return false;

        Region reference = regionAt(piston.getLocation());

        List<Location> involved = new ArrayList<>(moved.size() * 2);
        for (Block block : moved)
        {
            involved.add(block.getLocation());
            // Where the block ends up matters as much as where it started.
            involved.add(block.getRelative(direction).getLocation());
        }

        for (Location location : involved)
        {
            if (regionAt(location) != reference) return true;
        }
        return false;
    }

    private boolean differentRegions(Location from, Location to)
    {
        if (manager.isEmpty() || !plugin.protectBoundaries()) return false;
        if (!isRelevant(from.getWorld(), from.getBlockX(), from.getBlockZ())
                && !isRelevant(to.getWorld(), to.getBlockX(), to.getBlockZ()))
        {
            return false;
        }
        return regionAt(from) != regionAt(to);
    }

    private boolean isRelevant(World world, int blockX, int blockZ)
    {
        return world != null && manager.mayHaveRegionAt(world.getName(), blockX, blockZ);
    }

    /**
     * The region governing a position, or null. Identity comparison is safe because the manager
     * hands out the same instance for a given region.
     */
    private Region regionAt(Location location)
    {
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, true, null);
        if (claim == null) return null;
        return manager.findGoverning(claim, location);
    }
}
