package com.trarn.gp3d.util;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.hanging.HangingEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.player.PlayerBucketEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleEvent;
import org.bukkit.inventory.Inventory;

/**
 * Resolves the block a permission check is really about.
 *
 * <p>{@code ClaimPermissionCheckEvent} carries the player, the claim and the required permission,
 * but not a location &mdash; GriefPrevention resolves the claim before firing and never passes the
 * coordinates along. For a 2D claim that is fine; for a vertical band it is exactly the missing
 * piece, so we recover it from the triggering Bukkit event.
 *
 * <p>Ordering matters: the more specific subtypes are checked before their parents, because e.g.
 * {@link PlayerInteractEvent} is a {@link PlayerEvent} but the clicked block, not the player's
 * feet, is the location being protected.
 */
public final class EventLocations
{
    private EventLocations() {}

    public static Location resolve(Event event)
    {
        if (event == null) return null;

        // Block-centric events: the block is the thing being protected.
        if (event instanceof BlockEvent blockEvent)
        {
            return blockEvent.getBlock().getLocation();
        }

        if (event instanceof PlayerInteractEvent interact)
        {
            Block clicked = interact.getClickedBlock();
            if (clicked != null) return clicked.getLocation();
            return interact.getPlayer().getLocation();
        }

        if (event instanceof PlayerBucketEvent bucket)
        {
            // The affected block, i.e. where the fluid lands or is taken from.
            return bucket.getBlock().getLocation();
        }

        if (event instanceof PlayerInteractEntityEvent interactEntity)
        {
            return interactEntity.getRightClicked().getLocation();
        }

        if (event instanceof HangingEvent hanging)
        {
            return hanging.getEntity().getLocation();
        }

        if (event instanceof VehicleEvent vehicle)
        {
            return vehicle.getVehicle().getLocation();
        }

        // For damage events getEntity() is the victim, which is the location being protected.
        if (event instanceof EntityEvent entityEvent)
        {
            Entity entity = entityEvent.getEntity();
            if (entity != null) return entity.getLocation();
        }

        if (event instanceof InventoryEvent inventoryEvent)
        {
            Inventory inventory = inventoryEvent.getInventory();
            Location location = inventory.getLocation();
            if (location != null) return location;
        }

        if (event instanceof PlayerTeleportEvent teleport)
        {
            return teleport.getTo();
        }

        if (event instanceof PlayerEvent playerEvent)
        {
            return playerEvent.getPlayer().getLocation();
        }

        return null;
    }
}
