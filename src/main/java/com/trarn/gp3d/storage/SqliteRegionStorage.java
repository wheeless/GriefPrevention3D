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
package com.trarn.gp3d.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

/** File-backed storage. The default, and the right choice for a single server. */
public final class SqliteRegionStorage extends SqlRegionStorage
{
    private final File file;

    public SqliteRegionStorage(File file, String tablePrefix, Logger logger)
    {
        super(tablePrefix, logger);
        this.file = file;
    }

    @Override
    public void initialise() throws Exception
    {
        // Force registration even when the classloader is picky about service discovery.
        Class.forName("org.sqlite.JDBC");

        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs())
        {
            throw new IllegalStateException("Could not create data directory " + parent);
        }
        super.initialise();
    }

    @Override
    protected Connection openConnection() throws SQLException
    {
        return DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
    }

    @Override
    protected List<String> schemaStatements()
    {
        return List.of(
                "PRAGMA foreign_keys = ON",
                """
                CREATE TABLE IF NOT EXISTS %s (
                    id        INTEGER PRIMARY KEY,
                    claim_id  INTEGER NOT NULL,
                    world     TEXT    NOT NULL,
                    owner     TEXT    NOT NULL,
                    name      TEXT,
                    priority  INTEGER NOT NULL DEFAULT 0,
                    min_x     INTEGER NOT NULL, min_y INTEGER NOT NULL, min_z INTEGER NOT NULL,
                    max_x     INTEGER NOT NULL, max_y INTEGER NOT NULL, max_z INTEGER NOT NULL,
                    created   INTEGER NOT NULL
                )""".formatted(regionsTable),
                """
                CREATE TABLE IF NOT EXISTS %s (
                    region_id  INTEGER NOT NULL,
                    player     TEXT    NOT NULL,
                    permission TEXT    NOT NULL,
                    PRIMARY KEY (region_id, player)
                )""".formatted(trustTable),
                "CREATE INDEX IF NOT EXISTS idx_%s_claim ON %s(claim_id)"
                        .formatted(regionsTable, regionsTable));
    }

    @Override
    protected String upsertSuffix()
    {
        return """
                ON CONFLICT(id) DO UPDATE SET
                    claim_id = excluded.claim_id, world = excluded.world,
                    owner = excluded.owner, name = excluded.name, priority = excluded.priority,
                    min_x = excluded.min_x, min_y = excluded.min_y, min_z = excluded.min_z,
                    max_x = excluded.max_x, max_y = excluded.max_y, max_z = excluded.max_z""";
    }

    @Override
    public String describe()
    {
        return "SQLite (" + file.getName() + ")";
    }
}
