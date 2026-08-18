package com.avernix.gp3d.session;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * One player's in-progress 3D claim selection.
 *
 * <p>The footprint is picked exactly like a normal claim (two corners), then the vertical band is
 * seeded from the Y of the blocks that were clicked and adjusted from there.
 */
public final class SelectionSession
{
    private World world;
    private Integer x1, z1, y1;
    private Integer x2, z2, y2;
    private Integer bottom, top;
    private Long resizingRegionId;

    /** Non-null when this selection edits an existing region rather than creating a new one. */
    public Long getResizingRegionId() { return resizingRegionId; }
    public boolean isResizing() { return resizingRegionId != null; }
    public void setResizing(long regionId) { this.resizingRegionId = regionId; }

    /** Pre-loads the band so a resize starts from the region's current height, not the clicked blocks. */
    public void seedBand(int bottom, int top)
    {
        this.bottom = Math.min(bottom, top);
        this.top = Math.max(bottom, top);
    }

    public boolean hasFirst() { return x1 != null; }
    public boolean hasSecond() { return x2 != null; }
    public boolean isComplete() { return hasFirst() && hasSecond(); }

    public World getWorld() { return world; }

    public void setFirst(Location location)
    {
        if (world != null && !world.equals(location.getWorld())) reset();
        world = location.getWorld();
        x1 = location.getBlockX();
        y1 = location.getBlockY();
        z1 = location.getBlockZ();
        seedBand();
    }

    public void setSecond(Location location)
    {
        if (world != null && !world.equals(location.getWorld())) reset();
        world = location.getWorld();
        x2 = location.getBlockX();
        y2 = location.getBlockY();
        z2 = location.getBlockZ();
        seedBand();
    }

    /** Defaults the band to the span of the two clicked blocks, which is usually close enough. */
    private void seedBand()
    {
        if (bottom != null || top != null) return;
        if (y1 == null || y2 == null) return;
        bottom = Math.min(y1, y2);
        top = Math.max(y1, y2);
    }

    public void setBand(int bottom, int top)
    {
        this.bottom = Math.min(bottom, top);
        this.top = Math.max(bottom, top);
    }

    public boolean hasBand() { return bottom != null && top != null; }

    public int getBottom() { return bottom; }
    public int getTop() { return top; }

    public int getMinX() { return Math.min(x1, x2); }
    public int getMaxX() { return Math.max(x1, x2); }
    public int getMinZ() { return Math.min(z1, z2); }
    public int getMaxZ() { return Math.max(z1, z2); }

    public Location firstCorner() { return new Location(world, x1, y1, z1); }
    public Location secondCorner() { return new Location(world, x2, y2, z2); }

    public int footprintArea()
    {
        return (getMaxX() - getMinX() + 1) * (getMaxZ() - getMinZ() + 1);
    }

    public void reset()
    {
        world = null;
        x1 = z1 = y1 = null;
        x2 = z2 = y2 = null;
        bottom = top = null;
        resizingRegionId = null;
    }
}
