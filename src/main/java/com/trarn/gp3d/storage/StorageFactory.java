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
