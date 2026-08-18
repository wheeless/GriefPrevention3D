package com.avernix.gp3d.listener;

import com.avernix.gp3d.GP3DPlugin;
import com.avernix.gp3d.region.Region;
import com.avernix.gp3d.region.RegionManager;
import com.avernix.gp3d.util.EventLocations;
import com.avernix.gp3d.util.Permissions;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.events.ClaimPermissionCheckEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

/**
 * Applies vertical regions on top of GriefPrevention's own trust decisions.
 *
 * <p>Every one of GP's {@code allowBuild} / {@code allowBreak} / {@code allowAccess} /
 * {@code allowContainers} / {@code allowEdit} / {@code allowGrantPermission} calls funnels through
 * {@code Claim.checkPermission}, which fires {@code ClaimPermissionCheckEvent} with its own verdict
 * pre-loaded and then returns whatever the listener leaves behind. That makes this single handler
 * a complete, bidirectional override point: a null denial reason grants, a non-null one denies.
 *
 * <p>The rule is deliberately narrow. If the action is not inside a region we touch nothing at all,
 * so ordinary claim behaviour outside the band is bit-for-bit unchanged.
 */
public final class PermissionListener implements Listener
{
    private final GP3DPlugin plugin;
    private final RegionManager manager;

    public PermissionListener(GP3DPlugin plugin, RegionManager manager)
    {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onPermissionCheck(ClaimPermissionCheckEvent event)
    {
        Claim claim = event.getClaim();
        if (claim == null) return;

        // Hot path: the overwhelming majority of checks are on claims with no regions at all.
        if (!manager.hasAnyRegion(claim)) return;

        Player player = event.getCheckedPlayer();
        UUID uuid = event.getCheckedUUID();

        // Admins bypassing claims are already handled by GP; never tighten on top of that.
        if (player != null && isBypassing(player)) return;

        Location location = EventLocations.resolve(event.getTriggeringEvent());
        if (location == null && player != null) location = player.getLocation();
        if (location == null) return;

        Region region = manager.findGoverning(claim, location);
        if (region == null) return;   // outside every band: GP's verdict stands untouched

        // The claim's owner and managers never lose control of their own claim.
        if (uuid != null && isClaimStaff(claim, uuid)) return;

        ClaimPermission required = event.getRequiredPermission();

        // Edit and Manage administer the *claim* (resize, delete, /trust), not the band. A region
        // must never confer them: GriefPrevention already denies non-staff here, so leaving its
        // verdict untouched is both the safe and the correct answer.
        if (required == ClaimPermission.Edit || required == ClaimPermission.Manage) return;

        if (region.grants(uuid, required))
        {
            // A null reason is how GriefPrevention represents "allowed"; setCancelled(false) is
            // deprecated but does exactly this internally.
            event.setDenialReason(null);
        }
        else
        {
            final Region denied = region;
            event.setDenialReason(() -> plugin.messages().regionDenial(denied, required));
        }
    }

    private boolean isBypassing(Player player)
    {
        if (player.hasPermission(Permissions.GP_IGNORE_CLAIMS)
                || player.hasPermission(Permissions.GP_DELETE_CLAIMS))
        {
            return true;
        }

        GriefPrevention gp = GriefPrevention.instance;
        if (gp == null || gp.dataStore == null) return false;
        PlayerData data = gp.dataStore.getPlayerData(player.getUniqueId());
        return data != null && data.ignoreClaims;
    }

    /**
     * Walks the claim chain looking for ownership or manager status.
     *
     * <p>Deliberately reads GP's fields directly rather than calling {@code allowGrantPermission},
     * because that would re-enter {@code checkPermission} and fire this event recursively.
     */
    private static boolean isClaimStaff(Claim claim, UUID uuid)
    {
        String asString = uuid.toString();
        for (Claim current = claim; current != null; current = current.parent)
        {
            if (uuid.equals(current.ownerID)) return true;
            if (current.managers != null && current.managers.contains(asString)) return true;
        }
        return false;
    }
}
