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

import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.Locale;
import java.util.logging.Logger;

/** Builds a {@link RegionStorage} from config. Also used by the migrate command to open a source. */
public final class StorageFactory
{
    private StorageFactory() {}

    public static final String SQLITE = "sqlite";
    public static final String MYSQL = "mysql";

    public static String normalise(String type)
    {
        if (type == null) return SQLITE;
        String lower = type.trim().toLowerCase(Locale.ROOT);
        return switch (lower)
        {
            case MYSQL, "mariadb" -> MYSQL;
            case SQLITE, "sqlite3", "file" -> SQLITE;
            default -> null;
        };
    }

    public static RegionStorage create(String type, FileConfiguration config, File dataFolder,
                                       Logger logger)
    {
        String prefix = config.getString("storage.table-prefix", "gp3d_");

        if (MYSQL.equals(type))
        {
            return new MySqlRegionStorage(
                    config.getString("storage.mysql.host", "localhost"),
                    config.getInt("storage.mysql.port", 3306),
                    config.getString("storage.mysql.database", "minecraft"),
                    config.getString("storage.mysql.username", "root"),
                    config.getString("storage.mysql.password", ""),
                    config.getString("storage.mysql.properties",
                            "useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8"),
                    prefix, logger);
        }

        String fileName = config.getString("storage.sqlite.file", "regions.db");
        return new SqliteRegionStorage(new File(dataFolder, fileName), prefix, logger);
    }
}
