package com.trarn.gp3d.command;

import com.trarn.gp3d.GP3DPlugin;
import com.trarn.gp3d.region.Region;
import com.trarn.gp3d.session.SelectionSession;
import com.trarn.gp3d.util.BandPrompt;
import com.trarn.gp3d.util.Messages;
import com.trarn.gp3d.storage.RegionMigrator;
import com.trarn.gp3d.storage.RegionStorage;
import com.trarn.gp3d.storage.StorageFactory;
import com.trarn.gp3d.util.Permissions;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** {@code /3dclaim} — every chat button in {@link BandPrompt} maps onto one of these subcommands. */
public final class ClaimCommand implements CommandExecutor, TabCompleter
{
    private static final List<String> SUBCOMMANDS = List.of(
            "wand", "height", "confirm", "cancel", "info", "list", "delete",
            "trust", "untrust", "show", "migrate", "resize");

    private final GP3DPlugin plugin;

    public ClaimCommand(GP3DPlugin plugin)
    {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        // Migrating backends is an operations task, and the natural place to run it is the server
        // console — which is exactly where you are after editing config.yml and restarting.
        if (args.length > 0 && args[0].equalsIgnoreCase("migrate"))
        {
            migrate(sender, args);
            return true;
        }

        if (!(sender instanceof Player player))
        {
            sender.sendMessage("Only '3dclaim migrate' works from the console. "
                    + "Everything else needs a player, because it acts on where you are standing.");
            return true;
        }

        if (!player.hasPermission(Permissions.USE) && !player.hasPermission(Permissions.ADMIN))
        {
            player.sendMessage(plugin.messages().get("no-permission"));
            return true;
        }

        if (args.length == 0)
        {
            toggleMode(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT))
        {
            case "wand" -> giveWand(player);
            case "height" -> setHeight(player, args);
            case "confirm" -> confirm(player, args);
            case "cancel" -> cancel(player);
            case "info" -> info(player);
            case "list" -> list(player);
            case "delete", "remove" -> delete(player, args);
            case "trust" -> trust(player, args);
            case "untrust" -> untrust(player, args);
            case "show" -> show(player, args);
            case "resize" -> resize(player, args);
            case "migrate" -> migrate(player, args);
            default -> help(player);
        }
        return true;
    }

    private void toggleMode(Player player)
    {
        if (plugin.sessions().isActive(player))
        {
            plugin.sessions().stop(player);
            plugin.visualizer().clear(player);
            player.sendMessage(plugin.messages().get("mode-off"));
            return;
        }

        plugin.sessions().start(player);
        player.sendMessage(plugin.messages().get("mode-on"));
        if (!player.getInventory().contains(plugin.wandMaterial())) giveWand(player);
    }

    private void giveWand(Player player)
    {
        // The wand is an ordinary item, so this is a convenience rather than a capability. Refusing
        // it leaves the player perfectly able to select with one they crafted themselves.
        if (!player.hasPermission(Permissions.WAND))
        {
            player.sendMessage(plugin.messages().get("wand-hint",
                    "item", friendlyName(plugin.wandMaterial())));
            return;
        }

        ItemStack wand = new ItemStack(plugin.wandMaterial());
        wand.editMeta(meta -> meta.displayName(
                Component.text("3D Claim Wand", NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false)));
        player.getInventory().addItem(wand);
        player.sendMessage(plugin.messages().get("wand-given"));
    }

    private void setHeight(Player player, String[] args)
    {
        SelectionSession session = requireSelection(player);
        if (session == null) return;

        if (args.length < 3)
        {
            player.sendMessage(plugin.messages().get("band-invalid"));
            return;
        }

        Integer bottom = parseInt(args[1]);
        Integer top = parseInt(args[2]);
        World world = session.getWorld();
        if (bottom == null || top == null || world == null
                || Math.min(bottom, top) < world.getMinHeight()
                || Math.max(bottom, top) >= world.getMaxHeight())
        {
            player.sendMessage(plugin.messages().get("band-invalid"));
            return;
        }

        session.setBand(bottom, top);
        player.sendMessage(plugin.messages().get("band-set",
                "bottom", String.valueOf(session.getBottom()),
                "top", String.valueOf(session.getTop())));

        BandPrompt.send(player, session, session.footprintArea());
        plugin.visualizer().show(player, world,
                session.getMinX(), session.getBottom(), session.getMinZ(),
                session.getMaxX(), session.getTop(), session.getMaxZ());
    }

    private void confirm(Player player, String[] args)
    {
        SelectionSession session = requireSelection(player);
        if (session == null) return;

        if (!session.hasBand())
        {
            player.sendMessage(plugin.messages().get("band-invalid"));
            return;
        }

        if (session.isResizing())
        {
            Region target = plugin.regions().byId(session.getResizingRegionId());
            if (target == null)
            {
                player.sendMessage(plugin.messages().get("region-not-found"));
                plugin.sessions().stop(player);
                return;
            }
            applyResize(player, target, session.getMinX(), session.getMinZ(),
                    session.getMaxX(), session.getMaxZ(), session.getBottom(), session.getTop());
            return;
        }

        World world = session.getWorld();
        Location cornerA = new Location(world, session.getMinX(), session.getBottom(), session.getMinZ());
        Location cornerB = new Location(world, session.getMaxX(), session.getTop(), session.getMaxZ());

        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(cornerA, true, null);
        if (claim == null || claim.getID() == null || !claim.contains(cornerB, true, false))
        {
            player.sendMessage(plugin.messages().get("not-in-claim"));
            return;
        }

        if (!mayManageClaim(player, claim))
        {
            player.sendMessage(plugin.messages().get("not-your-claim"));
            return;
        }

        Region clash = plugin.regions().findOverlap(world.getName(),
                session.getMinX(), session.getBottom(), session.getMinZ(),
                session.getMaxX(), session.getTop(), session.getMaxZ(), -1L);
        if (clash != null)
        {
            player.sendMessage(plugin.messages().get("overlaps",
                    "id", String.valueOf(clash.getId()),
                    "name", clash.getName() == null ? ("#" + clash.getId()) : clash.getName(),
                    "bounds", clash.describeBounds()));
            plugin.visualizer().show(player, clash);
            return;
        }

        int limit = regionLimit(player);
        if (limit >= 0 && plugin.regions().countOwnedBy(player.getUniqueId()) >= limit)
        {
            player.sendMessage(plugin.messages().get("limit-reached", "limit", String.valueOf(limit)));
            return;
        }

        String name = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : null;

        Region region = new Region(
                plugin.regions().nextId(), claim.getID(), world.getName(),
                player.getUniqueId(), name, 0,
                session.getMinX(), session.getBottom(), session.getMinZ(),
                session.getMaxX(), session.getTop(), session.getMaxZ());

        plugin.regions().add(region);
        plugin.sessions().stop(player);

        player.sendMessage(plugin.messages().get("region-created",
                "id", String.valueOf(region.getId()),
                "bounds", region.describeBounds()));
        plugin.visualizer().show(player, region);
    }

    /**
     * Two ways in, because the two axes want different tools.
     *
     * <p>{@code /3dclaim resize height <bottom> <top>} edits only the band, where exact numbers are
     * the point and clicking would be a worse way to say "65 to 100". Plain {@code /3dclaim resize}
     * starts a wand selection for the footprint, where clicking corners is obviously better than
     * typing coordinates. Both land in the same validation.
     */
    private void resize(Player player, String[] args)
    {
        Region region = regionAt(player);
        if (region == null)
        {
            player.sendMessage(plugin.messages().get("region-not-found"));
            return;
        }
        if (!mayManageRegion(player, region))
        {
            player.sendMessage(plugin.messages().get("no-permission"));
            return;
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("height"))
        {
            if (args.length < 4)
            {
                player.sendMessage(Component.text("Usage: /3dclaim resize height <bottom> <top>",
                        NamedTextColor.RED));
                return;
            }

            Integer bottom = parseInt(args[2]);
            Integer top = parseInt(args[3]);
            World world = player.getWorld();
            if (bottom == null || top == null
                    || Math.min(bottom, top) < world.getMinHeight()
                    || Math.max(bottom, top) >= world.getMaxHeight())
            {
                player.sendMessage(plugin.messages().get("band-invalid"));
                return;
            }

            applyResize(player, region, region.getMinX(), region.getMinZ(),
                    region.getMaxX(), region.getMaxZ(), bottom, top);
            return;
        }

        // Footprint resize: reuse the ordinary selection flow, bound to this region.
        SelectionSession session = plugin.sessions().start(player);
        session.setResizing(region.getId());
        session.seedBand(region.getMinY(), region.getMaxY());

        player.sendMessage(plugin.messages().get("resize-mode",
                "id", String.valueOf(region.getId()),
                "name", region.getName() == null ? ("#" + region.getId()) : region.getName()));
        if (!player.getInventory().contains(plugin.wandMaterial())) giveWand(player);
        plugin.visualizer().show(player, region);
    }

    /** Shared by both resize forms and the wand confirm path. */
    private void applyResize(Player player, Region region, int minX, int minZ, int maxX, int maxZ,
                             int bottom, int top)
    {
        World world = player.getServer().getWorld(region.getWorld());
        if (world == null)
        {
            player.sendMessage(plugin.messages().get("region-not-found"));
            return;
        }

        // The region must still fit inside the claim it belongs to.
        Claim claim = GriefPrevention.instance.dataStore.getClaim(region.getClaimId());
        Location cornerA = new Location(world, minX, bottom, minZ);
        Location cornerB = new Location(world, maxX, top, maxZ);
        if (claim == null || !claim.contains(cornerA, true, false) || !claim.contains(cornerB, true, false))
        {
            player.sendMessage(plugin.messages().get("not-in-claim"));
            return;
        }

        // Ignore itself, or a region would always collide with its own current box.
        Region clash = plugin.regions().findOverlap(region.getWorld(), Math.min(minX, maxX),
                Math.min(bottom, top), Math.min(minZ, maxZ), Math.max(minX, maxX),
                Math.max(bottom, top), Math.max(minZ, maxZ), region.getId());
        if (clash != null)
        {
            player.sendMessage(plugin.messages().get("overlaps",
                    "id", String.valueOf(clash.getId()),
                    "name", clash.getName() == null ? ("#" + clash.getId()) : clash.getName(),
                    "bounds", clash.describeBounds()));
            plugin.visualizer().show(player, clash);
            return;
        }

        region.setBounds(minX, bottom, minZ, maxX, top, maxZ);
        plugin.regions().persist(region);
        plugin.sessions().stop(player);

        player.sendMessage(plugin.messages().get("resized",
                "id", String.valueOf(region.getId()),
                "bounds", region.describeBounds()));
        plugin.visualizer().show(player, region);
    }

    private void cancel(Player player)
    {
        plugin.sessions().stop(player);
        plugin.visualizer().clear(player);
        player.sendMessage(plugin.messages().get("cancelled"));
    }

    private void info(Player player)
    {
        Region region = regionAt(player);
        if (region == null)
        {
            player.sendMessage(plugin.messages().get("region-not-found"));
            return;
        }

        player.sendMessage(Component.text("3D Region #" + region.getId()
                + (region.getName() == null ? "" : " (" + region.getName() + ")"),
                NamedTextColor.AQUA, TextDecoration.BOLD));
        player.sendMessage(Component.text("  Bounds: ", NamedTextColor.GRAY)
                .append(Component.text(region.describeBounds(), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  Owner: ", NamedTextColor.GRAY)
                .append(Component.text(nameOf(region.getOwner()), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  Claim: ", NamedTextColor.GRAY)
                .append(Component.text("#" + region.getClaimId(), NamedTextColor.WHITE)));

        if (region.getTrust().isEmpty())
        {
            player.sendMessage(Component.text("  Trust: ", NamedTextColor.GRAY)
                    .append(Component.text("nobody", NamedTextColor.DARK_GRAY)));
        }
        else
        {
            for (Map.Entry<UUID, ClaimPermission> entry : region.getTrust().entrySet())
            {
                player.sendMessage(Component.text("  " + Messages.friendly(entry.getValue()) + ": ",
                        NamedTextColor.GRAY)
                        .append(Component.text(nameOf(entry.getKey()), NamedTextColor.WHITE)));
            }
        }

        plugin.visualizer().show(player, region);
    }

    private void list(Player player)
    {
        List<Region> owned = new ArrayList<>();
        for (Region region : plugin.regions().all())
        {
            if (player.getUniqueId().equals(region.getOwner())) owned.add(region);
        }

        if (owned.isEmpty())
        {
            player.sendMessage(Component.text("You have no 3D regions.", NamedTextColor.GRAY));
            return;
        }

        player.sendMessage(Component.text("Your 3D regions (" + owned.size() + ")",
                NamedTextColor.AQUA, TextDecoration.BOLD));
        for (Region region : owned)
        {
            player.sendMessage(Component.text("  #" + region.getId() + " ", NamedTextColor.WHITE)
                    .append(Component.text(region.getWorld() + " " + region.describeBounds(),
                            NamedTextColor.GRAY)));
        }
    }

    private void delete(Player player, String[] args)
    {
        Region region = args.length > 1 ? byId(args[1]) : regionAt(player);
        if (region == null)
        {
            player.sendMessage(plugin.messages().get("region-not-found"));
            return;
        }
        if (!mayManageRegion(player, region))
        {
            player.sendMessage(plugin.messages().get("no-permission"));
            return;
        }

        plugin.regions().remove(region.getId());
        player.sendMessage(plugin.messages().get("region-deleted", "id", String.valueOf(region.getId())));
    }

    private void trust(Player player, String[] args)
    {
        if (args.length < 2)
        {
            player.sendMessage(Component.text("Usage: /3dclaim trust <player> [build|container|access|permission]",
                    NamedTextColor.RED));
            return;
        }

        Region region = regionAt(player);
        if (region == null)
        {
            player.sendMessage(plugin.messages().get("region-not-found"));
            return;
        }
        if (!mayManageRegion(player, region))
        {
            player.sendMessage(plugin.messages().get("no-permission"));
            return;
        }

        UUID target = resolvePlayer(args[1]);
        if (target == null)
        {
            player.sendMessage(plugin.messages().get("player-unknown", "player", args[1]));
            return;
        }

        ClaimPermission level = args.length > 2 ? parseLevel(args[2]) : ClaimPermission.Build;
        if (level == null)
        {
            player.sendMessage(Component.text("Trust level must be build, container, access or permission.",
                    NamedTextColor.RED));
            return;
        }

        region.setTrust(target, level);
        plugin.regions().persistTrust(region);
        player.sendMessage(plugin.messages().get("trust-granted",
                "player", args[1], "level", Messages.friendly(level),
                "id", String.valueOf(region.getId())));
    }

    private void untrust(Player player, String[] args)
    {
        if (args.length < 2)
        {
            player.sendMessage(Component.text("Usage: /3dclaim untrust <player>", NamedTextColor.RED));
            return;
        }

        Region region = regionAt(player);
        if (region == null)
        {
            player.sendMessage(plugin.messages().get("region-not-found"));
            return;
        }
        if (!mayManageRegion(player, region))
        {
            player.sendMessage(plugin.messages().get("no-permission"));
            return;
        }

        UUID target = resolvePlayer(args[1]);
        if (target == null)
        {
            player.sendMessage(plugin.messages().get("player-unknown", "player", args[1]));
            return;
        }

        if (!region.dropTrust(target))
        {
            player.sendMessage(plugin.messages().get("trust-none"));
            return;
        }

        plugin.regions().persistTrust(region);
        player.sendMessage(plugin.messages().get("trust-revoked",
                "player", args[1], "id", String.valueOf(region.getId())));
    }

    private void show(Player player, String[] args)
    {
        Region region = args.length > 1 ? byId(args[1]) : regionAt(player);
        if (region == null)
        {
            player.sendMessage(plugin.messages().get("region-not-found"));
            return;
        }
        plugin.visualizer().show(player, region);
    }

    /**
     * Imports every region from another backend into the active one.
     *
     * <p>Worth having because the alternative failure mode is silent: point the config at a fresh
     * MySQL database, restart, and every existing region simply stops protecting anything. Nobody
     * notices until a player reports grief.
     */
    private void migrate(CommandSender sender, String[] args)
    {
        if (!sender.hasPermission(Permissions.ADMIN))
        {
            sender.sendMessage(plugin.messages().get("no-permission"));
            return;
        }

        if (args.length < 2)
        {
            sender.sendMessage(Component.text("Usage: /3dclaim migrate <sqlite|mysql>",
                    NamedTextColor.RED));
            sender.sendMessage(Component.text("Imports regions FROM that backend INTO the active one ("
                    + plugin.storageType() + ").", NamedTextColor.GRAY));
            return;
        }

        String source = StorageFactory.normalise(args[1]);
        if (source == null)
        {
            sender.sendMessage(Component.text("Unknown storage type: " + args[1], NamedTextColor.RED));
            return;
        }
        if (source.equals(plugin.storageType()))
        {
            sender.sendMessage(Component.text(
                    "That is already the active backend; there is nothing to import.",
                    NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("Reading regions from " + source + "...",
                NamedTextColor.GRAY));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
        {
            List<Region> imported;
            RegionStorage from = StorageFactory.create(
                    source, plugin.getConfig(), plugin.getDataFolder(), plugin.getLogger());
            try
            {
                from.initialise();
                imported = from.loadRegions();
            }
            catch (Exception e)
            {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Migration source could not be opened", e);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(Component.text(
                        "Could not read from " + source + "; see console for details.",
                        NamedTextColor.RED)));
                return;
            }
            finally
            {
                from.shutdown();
            }

            // Applied on the main thread: RegionManager is what the permission check reads.
            Bukkit.getScheduler().runTask(plugin, () -> applyMigration(sender, source, imported));
        });
    }

    private void applyMigration(CommandSender sender, String source, List<Region> imported)
    {
        RegionMigrator.Result result = RegionMigrator.importInto(
                imported, plugin.regions(),
                claimId -> GriefPrevention.instance.dataStore.getClaim(claimId) != null);

        sender.sendMessage(Component.text("Imported " + result.imported() + " region(s) from "
                + source + " into " + plugin.storageType() + ".", NamedTextColor.GREEN));
        if (result.renumbered() > 0)
        {
            sender.sendMessage(Component.text("  " + result.renumbered()
                    + " were renumbered to avoid colliding with existing ids.", NamedTextColor.GRAY));
        }
        if (result.skipped() > 0)
        {
            sender.sendMessage(Component.text("  " + result.skipped()
                    + " skipped: their claim no longer exists.", NamedTextColor.GRAY));
        }
        if (result.conflicted() > 0)
        {
            sender.sendMessage(Component.text("  " + result.conflicted()
                    + " skipped: they would overlap a region already here.", NamedTextColor.GRAY));
        }
        plugin.getLogger().info("Migration from " + source + ": " + result.imported()
                + " imported, " + result.renumbered() + " renumbered, " + result.skipped()
                + " skipped, " + result.conflicted() + " conflicted.");
    }

    private void help(Player player)
    {
        player.sendMessage(Component.text("3D Claims", NamedTextColor.AQUA, TextDecoration.BOLD));
        for (String line : new String[] {
                "/3dclaim — toggle 3D claim mode",
                "/3dclaim wand — get the selection wand",
                "/3dclaim height <bottom> <top> — set the vertical band",
                "/3dclaim confirm [name] — create the region",
                "/3dclaim cancel — discard the selection",
                "/3dclaim info — describe the region you're standing in",
                "/3dclaim list — list your regions",
                "/3dclaim delete [id] — delete a region",
                "/3dclaim resize — reselect the footprint with the wand",
                "/3dclaim resize height <bottom> <top> — change just the height",
                "/3dclaim trust <player> [level] — grant trust inside the region",
                "/3dclaim untrust <player> — revoke trust",
                "/3dclaim show [id] — outline a region",
                "/3dclaim migrate <sqlite|mysql> — import regions from another backend" })
        {
            player.sendMessage(Component.text("  " + line, NamedTextColor.GRAY));
        }
    }

    // ---- helpers -------------------------------------------------------------------------

    private SelectionSession requireSelection(Player player)
    {
        SelectionSession session = plugin.sessions().get(player);
        if (session == null)
        {
            player.sendMessage(plugin.messages().get("mode-off"));
            return null;
        }
        if (!session.isComplete())
        {
            player.sendMessage(plugin.messages().get("selection-incomplete"));
            return null;
        }
        return session;
    }

    private Region regionAt(Player player)
    {
        Location location = player.getLocation();
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, true, null);
        if (claim == null) return null;
        return plugin.regions().findGoverning(claim, location);
    }

    private Region byId(String raw)
    {
        Integer id = parseInt(raw);
        return id == null ? null : plugin.regions().byId(id);
    }

    /** Region owner, claim owner/manager, or an admin. */
    private boolean mayManageRegion(Player player, Region region)
    {
        if (player.hasPermission(Permissions.ADMIN)) return true;
        if (player.getUniqueId().equals(region.getOwner())) return true;

        // "permission" trust inside a region lets that player manage the region's own trust list.
        // It deliberately stops there and never reaches GriefPrevention's claim-level Manage.
        if (region.getTrust().get(player.getUniqueId()) == ClaimPermission.Manage) return true;

        Claim claim = GriefPrevention.instance.dataStore.getClaim(region.getClaimId());
        return claim != null && mayManageClaim(player, claim);
    }

    /**
     * Whether the player may carve regions out of this claim. Reads GP's fields directly rather
     * than calling {@code allowGrantPermission}, which would fire another permission check.
     */
    private boolean mayManageClaim(Player player, Claim claim)
    {
        if (player.hasPermission(Permissions.ADMIN)) return true;

        UUID uuid = player.getUniqueId();
        String asString = uuid.toString();
        for (Claim current = claim; current != null; current = current.parent)
        {
            if (uuid.equals(current.ownerID)) return true;
            if (current.managers != null && current.managers.contains(asString)) return true;
        }
        return false;
    }

    private int regionLimit(Player player)
    {
        if (player.hasPermission(Permissions.UNLIMITED) || player.hasPermission(Permissions.ADMIN))
        {
            return -1;
        }

        int best = -1;
        for (var info : player.getEffectivePermissions())
        {
            String node = info.getPermission();
            if (!info.getValue() || !node.startsWith(Permissions.LIMIT_PREFIX)) continue;
            Integer value = parseInt(node.substring(Permissions.LIMIT_PREFIX.length()));
            if (value != null && value > best) best = value;
        }
        return best >= 0 ? best : plugin.defaultRegionLimit();
    }

    private static UUID resolvePlayer(String name)
    {
        if (name.equalsIgnoreCase("public") || name.equals("*")) return Region.PUBLIC;

        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();

        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached == null ? null : cached.getUniqueId();
    }

    private static String nameOf(UUID uuid)
    {
        if (Region.PUBLIC.equals(uuid)) return "everyone";
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null ? uuid.toString() : name;
    }

    private static ClaimPermission parseLevel(String raw)
    {
        return switch (raw.toLowerCase(Locale.ROOT))
        {
            case "build", "trust" -> ClaimPermission.Build;
            case "container", "containers" -> ClaimPermission.Container;
            case "access" -> ClaimPermission.Access;
            case "permission", "manage" -> ClaimPermission.Manage;
            default -> null;
        };
    }

    private static String friendlyName(org.bukkit.Material material)
    {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static Integer parseInt(String raw)
    {
        try { return Integer.parseInt(raw.trim()); }
        catch (NumberFormatException e) { return null; }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        if (args.length == 1)
        {
            List<String> available = new ArrayList<>(SUBCOMMANDS);
            if (!sender.hasPermission(Permissions.WAND)) available.remove("wand");
            if (!sender.hasPermission(Permissions.ADMIN)) available.remove("migrate");
            return partial(args[0], available);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("trust") || sub.equals("untrust")))
        {
            List<String> names = new ArrayList<>();
            names.add("public");
            for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
            return partial(args[1], names);
        }
        if (args.length == 2 && sub.equals("resize"))
        {
            return partial(args[1], List.of("height"));
        }
        // Gated at both levels: hiding it from the subcommand list is pointless if typing
        // "migrate " by hand still suggests the backends to someone who cannot run it.
        if (args.length == 2 && sub.equals("migrate") && sender.hasPermission(Permissions.ADMIN))
        {
            return partial(args[1], List.of(StorageFactory.SQLITE, StorageFactory.MYSQL));
        }
        if (args.length == 3 && sub.equals("trust"))
        {
            return partial(args[2], List.of("build", "container", "access", "permission"));
        }
        return List.of();
    }

    private static List<String> partial(String prefix, List<String> options)
    {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options)
        {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) matches.add(option);
        }
        return matches;
    }
}
