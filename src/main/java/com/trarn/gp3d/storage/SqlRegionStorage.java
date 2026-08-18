package com.trarn.gp3d.storage;

import com.trarn.gp3d.region.Region;
import me.ryanhamshire.GriefPrevention.ClaimPermission;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Everything the SQL backends share. Subclasses supply only the parts that actually differ between
 * dialects: how to open a connection, the DDL, and the upsert conflict clause.
 *
 * <p>Reads never happen here on the hot path — {@code RegionManager} keeps the authoritative copy in
 * memory — so this class only has to be correct and durable, not fast. Writes are serialised onto a
 * single background thread so the main thread never blocks on I/O.
 */
public abstract class SqlRegionStorage implements RegionStorage
{
    protected final Logger logger;
    protected final String regionsTable;
    protected final String trustTable;

    private final ExecutorService writer =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "GP3D-Storage"));
    private Connection connection;

    protected SqlRegionStorage(String tablePrefix, Logger logger)
    {
        this.logger = logger;
        this.regionsTable = tablePrefix + "regions";
        this.trustTable = tablePrefix + "trust";
    }

    // ---- dialect hooks -------------------------------------------------------------------

    protected abstract Connection openConnection() throws SQLException;

    /** DDL run at startup; must be idempotent. */
    protected abstract List<String> schemaStatements();

    /** Dialect-specific tail of the upsert, e.g. {@code ON CONFLICT(id) DO UPDATE SET ...}. */
    protected abstract String upsertSuffix();

    // ---- shared implementation -----------------------------------------------------------

    protected synchronized Connection connection() throws SQLException
    {
        // A MySQL connection can be silently dropped by wait_timeout while still reporting open,
        // so validity is checked rather than assumed.
        if (connection != null)
        {
            boolean usable;
            try { usable = !connection.isClosed() && connection.isValid(3); }
            catch (SQLException e) { usable = false; }

            if (!usable)
            {
                try { connection.close(); } catch (SQLException ignored) { }
                connection = null;
            }
        }

        if (connection == null) connection = openConnection();
        return connection;
    }

    @Override
    public void initialise() throws Exception
    {
        try (Statement statement = connection().createStatement())
        {
            for (String sql : schemaStatements())
            {
                statement.executeUpdate(sql);
            }
        }
    }

    @Override
    public List<Region> loadRegions() throws Exception
    {
        List<Region> regions = new ArrayList<>();
        Map<Long, Region> index = new HashMap<>();

        try (Statement statement = connection().createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM " + regionsTable))
        {
            while (rs.next())
            {
                Region region = new Region(
                        rs.getLong("id"),
                        rs.getLong("claim_id"),
                        rs.getString("world"),
                        UUID.fromString(rs.getString("owner")),
                        rs.getString("name"),
                        rs.getInt("priority"),
                        rs.getInt("min_x"), rs.getInt("min_y"), rs.getInt("min_z"),
                        rs.getInt("max_x"), rs.getInt("max_y"), rs.getInt("max_z"));
                regions.add(region);
                index.put(region.getId(), region);
            }
        }

        try (Statement statement = connection().createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM " + trustTable))
        {
            while (rs.next())
            {
                Region region = index.get(rs.getLong("region_id"));
                if (region == null) continue;
                try
                {
                    region.loadTrust(UUID.fromString(rs.getString("player")),
                            ClaimPermission.valueOf(rs.getString("permission")));
                }
                catch (IllegalArgumentException ignored)
                {
                    // Unknown UUID or permission name: drop the entry rather than fail the load.
                }
            }
        }

        return regions;
    }

    @Override
    public long nextId() throws Exception
    {
        try (Statement statement = connection().createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT COALESCE(MAX(id), 0) + 1 FROM " + regionsTable))
        {
            return rs.next() ? rs.getLong(1) : 1L;
        }
    }

    @Override
    public void saveRegionAsync(Region region)
    {
        submit("save region " + region.getId(), () ->
        {
            String sql = "INSERT INTO " + regionsTable + " (id, claim_id, world, owner, name,"
                    + " priority, min_x, min_y, min_z, max_x, max_y, max_z, created)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " + upsertSuffix();

            try (PreparedStatement ps = connection().prepareStatement(sql))
            {
                ps.setLong(1, region.getId());
                ps.setLong(2, region.getClaimId());
                ps.setString(3, region.getWorld());
                ps.setString(4, region.getOwner().toString());
                ps.setString(5, region.getName());
                ps.setInt(6, region.getPriority());
                ps.setInt(7, region.getMinX());
                ps.setInt(8, region.getMinY());
                ps.setInt(9, region.getMinZ());
                ps.setInt(10, region.getMaxX());
                ps.setInt(11, region.getMaxY());
                ps.setInt(12, region.getMaxZ());
                ps.setLong(13, System.currentTimeMillis());
                ps.executeUpdate();
            }
            writeTrust(region);
        });
    }

    @Override
    public void saveTrustAsync(Region region)
    {
        submit("save trust for region " + region.getId(), () -> writeTrust(region));
    }

    private void writeTrust(Region region) throws SQLException
    {
        try (PreparedStatement delete = connection()
                .prepareStatement("DELETE FROM " + trustTable + " WHERE region_id = ?"))
        {
            delete.setLong(1, region.getId());
            delete.executeUpdate();
        }

        Map<UUID, ClaimPermission> trust = region.getTrust();
        if (trust.isEmpty()) return;

        try (PreparedStatement insert = connection().prepareStatement(
                "INSERT INTO " + trustTable + " (region_id, player, permission) VALUES (?, ?, ?)"))
        {
            for (Map.Entry<UUID, ClaimPermission> entry : trust.entrySet())
            {
                insert.setLong(1, region.getId());
                insert.setString(2, entry.getKey().toString());
                insert.setString(3, entry.getValue().name());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    @Override
    public void deleteRegionAsync(long regionId)
    {
        submit("delete region " + regionId, () ->
        {
            try (PreparedStatement ps = connection()
                    .prepareStatement("DELETE FROM " + regionsTable + " WHERE id = ?"))
            {
                ps.setLong(1, regionId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection()
                    .prepareStatement("DELETE FROM " + trustTable + " WHERE region_id = ?"))
            {
                ps.setLong(1, regionId);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void deleteRegionsForClaimAsync(long claimId)
    {
        submit("delete regions for claim " + claimId, () ->
        {
            try (PreparedStatement ps = connection().prepareStatement(
                    "DELETE FROM " + trustTable + " WHERE region_id IN "
                            + "(SELECT id FROM (SELECT id FROM " + regionsTable
                            + " WHERE claim_id = ?) AS doomed)"))
            {
                ps.setLong(1, claimId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection()
                    .prepareStatement("DELETE FROM " + regionsTable + " WHERE claim_id = ?"))
            {
                ps.setLong(1, claimId);
                ps.executeUpdate();
            }
        });
    }

    protected interface SqlTask { void run() throws SQLException; }

    protected void submit(String description, SqlTask task)
    {
        writer.execute(() ->
        {
            try
            {
                task.run();
            }
            catch (SQLException e)
            {
                logger.log(Level.SEVERE, "GP3D storage failed to " + description, e);
            }
        });
    }

    @Override
    public void shutdown()
    {
        writer.shutdown();
        try
        {
            if (!writer.awaitTermination(10, TimeUnit.SECONDS))
            {
                logger.warning("GP3D storage did not flush within 10s; some writes may be lost.");
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }

        synchronized (this)
        {
            try
            {
                if (connection != null && !connection.isClosed()) connection.close();
            }
            catch (SQLException e)
            {
                logger.log(Level.WARNING, "GP3D failed to close its database cleanly", e);
            }
            connection = null;
        }
    }
}
