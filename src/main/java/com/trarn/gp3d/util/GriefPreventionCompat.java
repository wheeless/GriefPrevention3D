package com.trarn.gp3d.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies at startup that the GriefPrevention on this server still exposes everything this plugin
 * binds to.
 *
 * <p>The plugin compiles against one GriefPrevention build but is expected to run against any of
 * them, and the members below are the entire contract between the two. Checking them once at enable
 * turns a would-be {@code NoSuchMethodError} in the middle of a block break — which surfaces as
 * random protection failures — into a single, precise line in the startup log.
 */
public final class GriefPreventionCompat
{
    private GriefPreventionCompat() {}

    private static final String CLAIM = "me.ryanhamshire.GriefPrevention.Claim";
    private static final String DATASTORE = "me.ryanhamshire.GriefPrevention.DataStore";
    private static final String PLAYER_DATA = "me.ryanhamshire.GriefPrevention.PlayerData";
    private static final String GP = "me.ryanhamshire.GriefPrevention.GriefPrevention";
    private static final String PERMISSION = "me.ryanhamshire.GriefPrevention.ClaimPermission";
    private static final String CHECK_EVENT =
            "me.ryanhamshire.GriefPrevention.events.ClaimPermissionCheckEvent";
    private static final String DELETED_EVENT =
            "me.ryanhamshire.GriefPrevention.events.ClaimDeletedEvent";

    /** Returns the list of missing members; empty means this GriefPrevention build is supported. */
    public static List<String> findIncompatibilities()
    {
        List<String> problems = new ArrayList<>();

        // The override point the whole plugin is built on.
        method(problems, CHECK_EVENT, "getDenialReason");
        method(problems, CHECK_EVENT, "setDenialReason", java.util.function.Supplier.class);
        method(problems, CHECK_EVENT, "getRequiredPermission");
        method(problems, CHECK_EVENT, "getTriggeringEvent");
        method(problems, CHECK_EVENT, "getCheckedPlayer");
        method(problems, CHECK_EVENT, "getCheckedUUID");
        method(problems, CHECK_EVENT, "getClaim");

        method(problems, DELETED_EVENT, "getClaim");

        // Claim geometry and ownership, read directly to avoid re-entering permission checks.
        method(problems, CLAIM, "getID");
        field(problems, CLAIM, "parent");
        field(problems, CLAIM, "children");
        field(problems, CLAIM, "managers");
        field(problems, CLAIM, "ownerID");

        method(problems, PERMISSION, "isGrantedBy", type(PERMISSION));
        for (String constant : new String[] { "Edit", "Build", "Container", "Access", "Manage" })
        {
            field(problems, PERMISSION, constant);
        }

        method(problems, DATASTORE, "getClaim", long.class);
        method(problems, DATASTORE, "getPlayerData", java.util.UUID.class);
        field(problems, PLAYER_DATA, "ignoreClaims");
        field(problems, GP, "instance");
        field(problems, GP, "dataStore");

        return problems;
    }

    private static Class<?> type(String name)
    {
        try { return Class.forName(name); }
        catch (ClassNotFoundException e) { return null; }
    }

    private static void method(List<String> problems, String owner, String name, Class<?>... params)
    {
        Class<?> type = type(owner);
        if (type == null) { problems.add("class " + owner); return; }
        for (Class<?> param : params)
        {
            if (param == null) { problems.add(owner + "#" + name + " (parameter type missing)"); return; }
        }
        try { type.getMethod(name, params); }
        catch (NoSuchMethodException e) { problems.add(owner + "#" + name + "(...)"); }
    }

    private static void field(List<String> problems, String owner, String name)
    {
        Class<?> type = type(owner);
        if (type == null) { problems.add("class " + owner); return; }
        try { type.getField(name); }
        catch (NoSuchFieldException e) { problems.add(owner + "#" + name); }
    }
}
