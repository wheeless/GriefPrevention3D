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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * MySQL/MariaDB-backed storage, for servers that already centralise plugin data.
 *
 * <p>Region ids are namespaced only by {@code table-prefix}, and claim ids come from each
 * GriefPrevention instance's own counter, so two different servers must not share one prefix.
 */
public final class MySqlRegionStorage extends SqlRegionStorage
{
    private final String url;
    private final Properties properties = new Properties();

    public MySqlRegionStorage(String host, int port, String database, String username,
                              String password, String extraProperties, String tablePrefix,
                              Logger logger)
    {
        super(tablePrefix, logger);

        String suffix = (extraProperties == null || extraProperties.isBlank())
                ? "" : "?" + extraProperties;
        this.url = "jdbc:mysql://" + host + ":" + port + "/" + database + suffix;

        properties.setProperty("user", username == null ? "" : username);
        properties.setProperty("password", password == null ? "" : password);
    }

    @Override
    public void initialise() throws Exception
    {
        Class.forName("com.mysql.cj.jdbc.Driver");
        super.initialise();
    }

    @Override
    protected Connection openConnection() throws SQLException
    {
        return DriverManager.getConnection(url, properties);
    }

    @Override
    protected List<String> schemaStatements()
    {
        return List.of(
                """
                CREATE TABLE IF NOT EXISTS %s (
                    id        BIGINT      NOT NULL PRIMARY KEY,
                    claim_id  BIGINT      NOT NULL,
                    world     VARCHAR(64) NOT NULL,
                    owner     CHAR(36)    NOT NULL,
                    name      VARCHAR(64),
                    priority  INT         NOT NULL DEFAULT 0,
                    min_x     INT NOT NULL, min_y INT NOT NULL, min_z INT NOT NULL,
                    max_x     INT NOT NULL, max_y INT NOT NULL, max_z INT NOT NULL,
                    created   BIGINT      NOT NULL,
                    INDEX idx_claim (claim_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""".formatted(regionsTable),
                """
                CREATE TABLE IF NOT EXISTS %s (
                    region_id  BIGINT      NOT NULL,
                    player     CHAR(36)    NOT NULL,
                    permission VARCHAR(16) NOT NULL,
                    PRIMARY KEY (region_id, player)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""".formatted(trustTable));
    }

    @Override
    protected String upsertSuffix()
    {
        return """
                ON DUPLICATE KEY UPDATE
                    claim_id = VALUES(claim_id), world = VALUES(world),
                    owner = VALUES(owner), name = VALUES(name), priority = VALUES(priority),
                    min_x = VALUES(min_x), min_y = VALUES(min_y), min_z = VALUES(min_z),
                    max_x = VALUES(max_x), max_y = VALUES(max_y), max_z = VALUES(max_z)""";
    }

    @Override
    public String describe()
    {
        return "MySQL (" + url.replaceAll("\\?.*$", "") + ")";
    }
}
