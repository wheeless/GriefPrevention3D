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
package com.trarn.gp3d;

import com.trarn.gp3d.command.ClaimCommand;
import com.trarn.gp3d.listener.BoundaryListener;
import com.trarn.gp3d.listener.ClaimLifecycleListener;
import com.trarn.gp3d.listener.PermissionListener;
import com.trarn.gp3d.listener.WandListener;
import com.trarn.gp3d.region.RegionManager;
import com.trarn.gp3d.session.SessionManager;
import com.trarn.gp3d.storage.RegionStorage;
import com.trarn.gp3d.storage.StorageFactory;
import com.trarn.gp3d.util.GriefPreventionCompat;
import com.trarn.gp3d.util.Messages;
import com.trarn.gp3d.util.Visualizer;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.logging.Level;

/**
 * Adds vertically bounded ("3D") regions to GriefPrevention claims.
 *
 * <p>The plugin never modifies GriefPrevention's data. Regions live in their own SQLite file keyed
 * by GP claim id, and are enforced by overriding the verdict GP publishes through
 * {@code ClaimPermissionCheckEvent}.
 */
public final class GP3DPlugin extends JavaPlugin
{
    private final Messages messages = new Messages();
    private RegionStorage storage;
    private RegionManager regions;
    private SessionManager sessions;
    private Visualizer visualizer;

    private String storageType = StorageFactory.SQLITE;
    private Material wandMaterial = Material.GOLDEN_HOE;
    private int defaultRegionLimit = -1;
    private boolean protectBoundaries = true;

    @Override
    public void onEnable()
    {
        if (getServer().getPluginManager().getPlugin("GriefPrevention") == null)
        {
            getLogger().severe("GriefPrevention is not installed. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        List<String> incompatibilities = GriefPreventionCompat.findIncompatibilities();
        if (!incompatibilities.isEmpty())
        {
            getLogger().severe("This GriefPrevention build is missing API this plugin needs:");
            for (String problem : incompatibilities) getLogger().severe("  - " + problem);
            getLogger().severe("Disabling rather than half-protecting your claims.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        readConfig();

        String configured = getConfig().getString("storage.type", StorageFactory.SQLITE);
        storageType = StorageFactory.normalise(configured);
        if (storageType == null)
        {
            getLogger().warning("Unknown storage.type '" + configured + "'; using sqlite instead.");
            storageType = StorageFactory.SQLITE;
        }

        storage = StorageFactory.create(storageType, getConfig(), getDataFolder(), getLogger());
        regions = new RegionManager(storage);
        sessions = new SessionManager();

        try
        {
            storage.initialise();
            regions.loadAll();
            getLogger().info("Loaded " + regions.size() + " 3D region(s) from " + storage.describe() + ".");
        }
        catch (ClassNotFoundException e)
        {
            getLogger().severe("The JDBC driver for " + storageType + " is missing. Drivers are "
                    + "declared under `libraries:` in plugin.yml, so this usually means the server "
                    + "could not download it on first start. Give the server outbound network "
                    + "access once, or install the driver manually. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        catch (Exception e)
        {
            getLogger().log(Level.SEVERE, "Could not open the region database. Disabling.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new PermissionListener(this, regions), this);
        getServer().getPluginManager().registerEvents(new WandListener(this), this);
        getServer().getPluginManager().registerEvents(new BoundaryListener(this, regions), this);
        getServer().getPluginManager().registerEvents(
                new ClaimLifecycleListener(regions, getLogger()), this);

        PluginCommand command = getCommand("3dclaim");
        if (command != null)
        {
            ClaimCommand executor = new ClaimCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        // GriefPrevention finishes loading claims during its own enable, but claims can also be
        // deleted while this plugin is offline, so reconcile once the server is fully up.
        getServer().getScheduler().runTask(this, this::pruneOrphanedRegions);
    }

    private void readConfig()
    {
        messages.load(getConfig());

        Material wand = Material.matchMaterial(getConfig().getString("wand", "GOLDEN_HOE"));
        if (wand == null || !wand.isItem())
        {
            getLogger().warning("Configured wand is not a valid item; falling back to GOLDEN_HOE.");
            wand = Material.GOLDEN_HOE;
        }
        wandMaterial = wand;

        defaultRegionLimit = getConfig().getInt("default-region-limit", -1);
        protectBoundaries = getConfig().getBoolean("protect-boundaries", true);

        visualizer = new Visualizer(this);
        visualizer.configure(
                Material.matchMaterial(getConfig().getString("visualization.corner", "GLOWSTONE")),
                Material.matchMaterial(getConfig().getString("visualization.bottom", "LIME_STAINED_GLASS")),
                Material.matchMaterial(getConfig().getString("visualization.top", "LIGHT_BLUE_STAINED_GLASS")),
                getConfig().getInt("visualization.seconds", 15));
    }

    private void pruneOrphanedRegions()
    {
        GriefPrevention gp = GriefPrevention.instance;
        if (gp == null || gp.dataStore == null) return;

        int removed = regions.pruneOrphans(claimId -> gp.dataStore.getClaim(claimId) != null);
        if (removed > 0)
        {
            getLogger().info("Pruned " + removed + " 3D region(s) whose claim no longer exists.");
        }
    }

    @Override
    public void onDisable()
    {
        if (visualizer != null) visualizer.clearAll();
        if (storage != null) storage.shutdown();
    }

    public Messages messages() { return messages; }
    public RegionStorage storage() { return storage; }
    public String storageType() { return storageType; }
    public RegionManager regions() { return regions; }
    public SessionManager sessions() { return sessions; }
    public Visualizer visualizer() { return visualizer; }
    public Material wandMaterial() { return wandMaterial; }
    public int defaultRegionLimit() { return defaultRegionLimit; }
    public boolean protectBoundaries() { return protectBoundaries; }
}
