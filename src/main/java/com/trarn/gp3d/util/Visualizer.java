package com.trarn.gp3d.util;

import com.trarn.gp3d.region.Region;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Draws a region's outline with client-side fake blocks.
 *
 * <p>GriefPrevention's own {@code BoundaryVisualization} only ever renders a flat footprint, so a
 * vertical band needs its own rendering: both the floor and ceiling rectangles plus corner pillars,
 * which is what makes the height of the claim readable in-world.
 */
public final class Visualizer
{
    /** Hard ceiling on fake blocks per visualisation, so a huge region can't flood the client. */
    private static final int MAX_BLOCKS = 1200;

    private final Plugin plugin;
    private final Map<UUID, Active> active = new HashMap<>();

    private Material cornerMaterial = Material.GLOWSTONE;
    private Material bottomMaterial = Material.LIME_STAINED_GLASS;
    private Material topMaterial = Material.LIGHT_BLUE_STAINED_GLASS;
    private int seconds = 15;

    public Visualizer(Plugin plugin)
    {
        this.plugin = plugin;
    }

    public void configure(Material corner, Material bottom, Material top, int seconds)
    {
        if (corner != null) this.cornerMaterial = corner;
        if (bottom != null) this.bottomMaterial = bottom;
        if (top != null) this.topMaterial = top;
        if (seconds > 0) this.seconds = seconds;
    }

    public void show(Player player, Region region)
    {
        World world = player.getWorld();
        if (!world.getName().equals(region.getWorld())) return;
        show(player, world, region.getMinX(), region.getMinY(), region.getMinZ(),
                region.getMaxX(), region.getMaxY(), region.getMaxZ());
    }

    public void show(Player player, World world, int minX, int minY, int minZ,
                     int maxX, int maxY, int maxZ)
    {
        clear(player);

        List<Location> corners = new ArrayList<>();
        List<Location> bottomEdge = new ArrayList<>();
        List<Location> topEdge = new ArrayList<>();

        int stepX = step(maxX - minX);
        int stepZ = step(maxZ - minZ);

        for (int x = minX; x <= maxX; x += stepX)
        {
            addEdge(bottomEdge, world, x, minY, minZ);
            addEdge(bottomEdge, world, x, minY, maxZ);
            addEdge(topEdge, world, x, maxY, minZ);
            addEdge(topEdge, world, x, maxY, maxZ);
        }
        for (int z = minZ; z <= maxZ; z += stepZ)
        {
            addEdge(bottomEdge, world, minX, minY, z);
            addEdge(bottomEdge, world, maxX, minY, z);
            addEdge(topEdge, world, minX, maxY, z);
            addEdge(topEdge, world, maxX, maxY, z);
        }

        // Vertical pillars at the four corners so the band's height reads at a glance.
        int stepY = step(maxY - minY);
        for (int y = minY; y <= maxY; y += stepY)
        {
            addEdge(corners, world, minX, y, minZ);
            addEdge(corners, world, maxX, y, minZ);
            addEdge(corners, world, minX, y, maxZ);
            addEdge(corners, world, maxX, y, maxZ);
        }

        Active session = new Active();
        paint(player, session, bottomEdge, bottomMaterial);
        paint(player, session, topEdge, topMaterial);
        paint(player, session, corners, cornerMaterial);

        if (session.shown.isEmpty()) return;

        session.revertTask = plugin.getServer().getScheduler().runTaskLater(
                plugin, () -> clear(player), seconds * 20L);
        active.put(player.getUniqueId(), session);
    }

    private static int step(int span)
    {
        // Keep total blocks bounded on very large regions by sampling the edges.
        return Math.max(1, (span + 1) / 64 + 1);
    }

    private static void addEdge(List<Location> target, World world, int x, int y, int z)
    {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) return;
        target.add(new Location(world, x, y, z));
    }

    private void paint(Player player, Active session, List<Location> locations, Material material)
    {
        BlockData data = material.createBlockData();
        for (Location location : locations)
        {
            if (session.shown.size() >= MAX_BLOCKS) return;
            session.shown.add(location);
            player.sendBlockChange(location, data);
        }
    }

    public void clear(Player player)
    {
        Active session = active.remove(player.getUniqueId());
        if (session == null) return;
        if (session.revertTask != null) session.revertTask.cancel();
        if (!player.isOnline()) return;
        for (Location location : session.shown)
        {
            // Re-send the world's real block so the client drops the illusion.
            player.sendBlockChange(location, location.getBlock().getBlockData());
        }
    }

    public void clearAll()
    {
        for (UUID uuid : new ArrayList<>(active.keySet()))
        {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) clear(player);
            else active.remove(uuid);
        }
    }

    private static final class Active
    {
        private final List<Location> shown = new ArrayList<>();
        private BukkitTask revertTask;
    }
}
