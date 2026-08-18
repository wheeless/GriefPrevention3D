package com.trarn.gp3d.util;

import me.ryanhamshire.GriefPrevention.ClaimPermission;

/**
 * Mirrors GriefPrevention's own claim-bypass rule, so a region is never more permissive than the
 * claim it sits inside.
 *
 * <p>GP's {@code getDefaultDenial} bypasses only when the player has <em>toggled</em>
 * {@code /ignoreclaims} on <em>and</em> holds the matching permission:
 *
 * <pre>{@code
 * if (isAdminClaim() && player.hasPermission("griefprevention.adminclaims")) return null;
 * if (permission == Edit && player.hasPermission("griefprevention.deleteclaims")) return null;
 * if (uuid.equals(getOwnerID())
 *     || (playerData.ignoreClaims && hasBypassPermission(player, permission))) return null;
 * }</pre>
 *
 * <p>Holding the permission alone is not enough. That distinction matters because
 * {@code griefprevention.ignoreclaims} is granted to every operator by default — treating the
 * permission as the bypass would mean regions silently stopped applying to admins the moment they
 * opped themselves, which looks exactly like the plugin being broken.
 */
public final class BypassRule
{
    private BypassRule() {}

    public static boolean bypasses(boolean adminClaim, boolean hasAdminClaimsPermission,
                                   boolean ignoreClaimsToggled, boolean hasIgnoreClaimsPermission,
                                   boolean hasDeleteClaimsPermission, ClaimPermission required)
    {
        if (adminClaim && hasAdminClaimsPermission) return true;
        if (required == ClaimPermission.Edit && hasDeleteClaimsPermission) return true;

        // The toggle is the gate; the permission only says whether the toggle is allowed to work.
        if (!ignoreClaimsToggled) return false;

        return required == ClaimPermission.Edit
                ? hasDeleteClaimsPermission
                : hasIgnoreClaimsPermission;
    }
}
