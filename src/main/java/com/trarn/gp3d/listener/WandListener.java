package com.trarn.gp3d.listener;

import com.trarn.gp3d.GP3DPlugin;
import com.trarn.gp3d.region.Region;
import com.trarn.gp3d.session.SelectionSession;
import com.trarn.gp3d.util.BandPrompt;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Turns wand clicks into corner selections while the player is in 3D claim mode. */
public final class WandListener implements Listener
{
    private final GP3DPlugin plugin;

    public WandListener(GP3DPlugin plugin)
    {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event)
    {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        SelectionSession session = plugin.sessions().get(player);
        if (session == null) return;

        if (event.getItem() == null || event.getItem().getType() != plugin.wandMaterial()) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Action action = event.getAction();
        boolean first;
        if (action == Action.LEFT_CLICK_BLOCK) first = true;
        else if (action == Action.RIGHT_CLICK_BLOCK) first = false;
        else return;

        // Consume the click entirely: the wand selects, it never builds or breaks.
        event.setCancelled(true);

        Location location = block.getLocation();
        if (first)
        {
            session.setFirst(location);
            player.sendMessage(plugin.messages().get("corner-first",
                    "x", String.valueOf(location.getBlockX()),
                    "y", String.valueOf(location.getBlockY()),
                    "z", String.valueOf(location.getBlockZ())));
        }
        else
        {
            session.setSecond(location);
            player.sendMessage(plugin.messages().get("corner-second",
                    "x", String.valueOf(location.getBlockX()),
                    "y", String.valueOf(location.getBlockY()),
                    "z", String.valueOf(location.getBlockZ())));
        }

        if (session.isComplete())
        {
            BandPrompt.send(player, session, session.footprintArea());
            plugin.visualizer().show(player, session.getWorld(),
                    session.getMinX(), session.getBottom(), session.getMinZ(),
                    session.getMaxX(), session.getTop(), session.getMaxZ());
        }
    }

    /**
     * Outlines the region you are standing in when you switch to the wand, the way GriefPrevention's
     * golden shovel outlines the claim you are standing in.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event)
    {
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItem(event.getNewSlot());

        if (held == null || held.getType() != plugin.wandMaterial())
        {
            // Switching away from the wand drops the outline rather than leaving it to time out.
            plugin.visualizer().clear(player);
            return;
        }

        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(player.getLocation(), true, null);
        if (claim == null) return;

        Region region = plugin.regions().findGoverning(claim, player.getLocation());
        if (region == null) return;

        plugin.visualizer().show(player, region);
        player.sendMessage(plugin.messages().get("standing-in",
                "id", String.valueOf(region.getId()),
                "name", region.getName() == null ? ("#" + region.getId()) : region.getName(),
                "bottom", String.valueOf(region.getMinY()),
                "top", String.valueOf(region.getMaxY())));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event)
    {
        plugin.sessions().clear(event.getPlayer().getUniqueId());
    }
}
