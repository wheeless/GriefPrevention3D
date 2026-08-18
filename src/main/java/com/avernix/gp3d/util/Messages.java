package com.avernix.gp3d.util;

import com.avernix.gp3d.region.Region;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

/** Config-backed, colour-coded messages. Keys live under {@code messages:} in config.yml. */
public final class Messages
{
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    static
    {
        DEFAULTS.put("prefix", "&8[&b3D&8] &r");
        DEFAULTS.put("mode-on", "&b3D claim mode enabled. &7Left-click a block for corner 1, right-click for corner 2.");
        DEFAULTS.put("mode-off", "&73D claim mode disabled.");
        DEFAULTS.put("no-permission", "&cYou don't have permission to do that.");
        DEFAULTS.put("corner-first", "&aCorner 1 set &7at {x}, {y}, {z}&a. Now right-click the opposite corner.");
        DEFAULTS.put("corner-second", "&aCorner 2 set &7at {x}, {y}, {z}&a.");
        DEFAULTS.put("wand-given", "&aHere's your 3D claim wand.");
        DEFAULTS.put("selection-incomplete", "&cSelect both corners first.");
        DEFAULTS.put("not-in-claim", "&cBoth corners must be inside the same claim.");
        DEFAULTS.put("not-your-claim", "&cYou can only create 3D regions inside claims you own or manage.");
        DEFAULTS.put("band-invalid", "&cThat isn't a valid height range for this world.");
        DEFAULTS.put("band-set", "&aHeight set to &fBottom: {bottom} &7/ &fTop: {top}&a.");
        DEFAULTS.put("region-created", "&aCreated 3D region &f#{id} &a— &7{bounds}");
        DEFAULTS.put("region-deleted", "&aDeleted 3D region &f#{id}&a.");
        DEFAULTS.put("region-not-found", "&cNo 3D region here.");
        DEFAULTS.put("limit-reached", "&cYou've reached your limit of &f{limit} &c3D regions.");
        DEFAULTS.put("standing-in", "&7You are in 3D region &b{name} &7(Y {bottom}-{top}).");
        DEFAULTS.put("resize-mode", "&bResizing 3D region {name}&b. &7Left-click a block for corner 1, right-click for corner 2. Height is kept unless you change it.");
        DEFAULTS.put("resized", "&aResized 3D region &f#{id} &a— &7{bounds}");
        DEFAULTS.put("cancelled", "&7Selection cancelled.");
        DEFAULTS.put("overlaps", "&cThat would overlap 3D region &f{name} &7({bounds})&c. Regions can sit above or below each other, but never share blocks.");
        DEFAULTS.put("trust-granted", "&aGranted &f{player} &a{level} trust in region &f#{id}&a.");
        DEFAULTS.put("trust-revoked", "&aRevoked &f{player}&a's trust in region &f#{id}&a.");
        DEFAULTS.put("trust-none", "&cThat player has no trust in this region.");
        DEFAULTS.put("player-unknown", "&cNever seen a player called &f{player}&c.");
        DEFAULTS.put("denial", "&cThat belongs to a 3D region (Y {bottom}-{top}) you don't have {level} trust in.");
    }

    private final Map<String, String> values = new LinkedHashMap<>(DEFAULTS);
    private String prefix = DEFAULTS.get("prefix");

    public void load(FileConfiguration config)
    {
        values.clear();
        values.putAll(DEFAULTS);
        ConfigurationSection section = config.getConfigurationSection("messages");
        if (section != null)
        {
            for (String key : section.getKeys(false))
            {
                String value = section.getString(key);
                if (value != null) values.put(key, value);
            }
        }
        prefix = values.getOrDefault("prefix", "");
    }

    private String raw(String key, String... replacements)
    {
        String value = values.getOrDefault(key, key);
        for (int i = 0; i + 1 < replacements.length; i += 2)
        {
            value = value.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return value;
    }

    /** A prefixed, coloured component ready to send. */
    public Component get(String key, String... replacements)
    {
        return LEGACY.deserialize(prefix + raw(key, replacements));
    }

    /** Colour-coded but unprefixed, for inline use inside larger components. */
    public Component bare(String key, String... replacements)
    {
        return LEGACY.deserialize(raw(key, replacements));
    }

    /** Plain legacy string, used where GriefPrevention wants a {@code String}. */
    public String legacy(String key, String... replacements)
    {
        return raw(key, replacements).replace('&', '§');
    }

    /** The denial text GriefPrevention shows when a region blocks an action. */
    public String regionDenial(Region region, ClaimPermission required)
    {
        return legacy("denial",
                "bottom", String.valueOf(region.getMinY()),
                "top", String.valueOf(region.getMaxY()),
                "level", friendly(required),
                "id", String.valueOf(region.getId()),
                "name", region.getName() == null ? ("#" + region.getId()) : region.getName());
    }

    public static String friendly(ClaimPermission permission)
    {
        if (permission == null) return "access";
        return switch (permission)
        {
            case Edit -> "edit";
            case Build -> "build";
            case Container, Inventory -> "container";
            case Access -> "access";
            case Manage -> "permission";
        };
    }
}
