# GriefPrevention3D

Vertically bounded ("3D") regions inside GriefPrevention claims.

A region carves a height band out of an existing claim. Inside the band, only players trusted
*to that region* may act. Outside it, the claim behaves exactly as it always has. That makes
things like "the shop on floor 1 belongs to Alice, floor 2 belongs to Bob" expressible for the
first time, without either of them being able to touch the other's floor.

---

## Compatibility

**One jar covers the 1.21.x line and the 26.x line.** Requires **Paper** (or a Paper fork such as
Purpur or Pufferfish) — not Spigot or CraftBukkit, because the chat UI uses Paper's Adventure
integration (`CommandSender#sendMessage(Component)`), which Spigot does not provide.

| | Supported |
| --- | --- |
| Server | Paper 1.21.x → 26.1.2, 26.2, and newer |
| Java | 21 (1.21.x servers) and 25 (26.x servers) |
| GriefPrevention | 16.18.x (verified 16.18.7); API surface unchanged back to GP 17/18 builds |

### Why one jar is enough

The jar is compiled against the **oldest** supported Paper API and emits **Java 21 bytecode**. A
Java 25 JVM runs Java 21 bytecode, so the 26.x line is covered without a second build.

That only holds if every API member used still exists in the newer lines, so it is checked rather
than assumed. Compiling the same sources against `1.21.11`, `26.1.2` and `26.2` and extracting every
external method and field reference from the resulting bytecode yields **136 references, identical
across all three** — same owner, name and descriptor. Nothing this plugin touches moved or changed
shape, so the symbols resolve on any of them.

The reason the surface is so stable is that this plugin is mostly *bouncing off GriefPrevention*, as
opposed to the server internals. Its Bukkit usage is confined to long-settled API — events,
`Material`, `Location`, `World` height bounds, `sendBlockChange`, and Adventure components.

### Re-checking a future version

When a new line ships (26.3 and beyond), confirm the surface still exists:

```bash
gradle crossCheck -PpaperApi=26.3.build.1-stable -PpaperJdk=25
```

A clean compile means the contract is intact and the existing jar is fine as-is. Note that Paper
26.x artifacts require JDK 25 to resolve, which is why `-PpaperJdk` exists; the shipped jar is still
built with the default Java 21 target.

### GriefPrevention drift

GriefPrevention is the real dependency, and it is stable here: the `ClaimPermissionCheckEvent`
contract this plugin is built on is byte-for-byte the same in GP 16.18.7, 17.0.0 and 18.0.0, and the
`BoundingBox(Claim)` ceiling clamp that motivates the whole design is present in all of them too.

As a backstop, every GriefPrevention member this plugin binds to is verified reflectively at enable
time. If a future GP build removes or renames any of it, the plugin logs exactly what is missing and
disables itself rather than silently half-protecting claims.

---

## Why it's built this way

Three findings from GriefPrevention's own bytecode shaped the entire design.

**1. GP publishes every trust decision as an overridable event.** All six of `allowBuild`,
`allowBreak`, `allowAccess`, `allowContainers`, `allowEdit` and `allowGrantPermission` funnel into
`Claim.checkPermission`, which does this:

```java
event.setDenialReason(defaultDenial);        // GP's own verdict
Bukkit.getPluginManager().callEvent(event);
return event.getDenialReason();              // whatever a listener leaves is final
```

A listener on `ClaimPermissionCheckEvent` therefore has complete, bidirectional control over every
permission decision GP makes — a null reason grants, a non-null one denies. No fork, no reflection,
no NMS, and nothing breaks when GriefPrevention updates.

**2. GP claims have a floor but structurally cannot have a ceiling.** `Claim.contains()` does a
genuine 3D check, and the database persists the Y of both corners. But `BoundingBox(Claim)` ends
with `this.maxY = world.getMaxHeight()`, discarding the stored top every time a box is built.

**3. GP subdivisions cannot stack.** `overlaps()` is `BoundingBox(a).intersects(BoundingBox(b))`.
Because both boxes have `maxY` forced to world height, two subdivisions sharing a footprint
**always** intersect no matter what Y values they hold, so GP rejects the second one.

Finding 3 is why regions are not GP subdivisions. They are this plugin's own objects, stored
separately and keyed by GP claim id. GP never sees them, so there is no overlap check to fail and
stacking works. It also makes the semantics fall out for free: if an action isn't inside a region
we simply don't touch GP's verdict, so the parent claim's trust applies outside the band with no
special-casing at all.

---

## Installation

Every push to `main` publishes a rolling `dev` build, and every `v*` tag publishes a release, so a
test server can pull the jar directly:

```bash
# latest main build
wget -O plugins/GriefPrevention3D.jar \
  https://github.com/wheeless/GriefPrevention3D/releases/download/dev/GriefPrevention3D.jar

# latest tagged release
wget -O plugins/GriefPrevention3D.jar \
  https://github.com/wheeless/GriefPrevention3D/releases/latest/download/GriefPrevention3D.jar
```

Restart, and the plugin creates `plugins/GriefPrevention3D/config.yml` plus its database.

JDBC drivers are declared under `libraries:` in `plugin.yml`, so the server downloads them on first
start. If the server has no outbound network access, install `org.xerial:sqlite-jdbc` (and
`com.mysql:mysql-connector-j` if using MySQL) into its library folder manually — the plugin will say
exactly which driver is missing rather than failing obscurely.

## Usage

Enter 3D claim mode with `/3dclaim`, then left-click a block for corner 1 and right-click for corner
2, exactly like picking a normal claim. Selection uses a wand — a golden hoe by default, configurable.

The wand is an **ordinary item**, so `gp3d.wand` gates only the free handout, not the ability to
select: anyone can craft a golden hoe and use it. It defaults to `op` so the server decides who gets
one for free, and players without it are simply told which item to hold. Once both corners
are set you get the height prompt:

```
  3D Claim — set the height of your claim
  Footprint: 176 blocks  (10, 30) → (20, 45)
  Bottom: 65   -16   -1   +1   +16
  Top:    80   -16   -1   +1   +16
     Confirm     Cancel
  or type /3dclaim height <bottom> <top>
```

The buttons are clickable and each just runs the equivalent command, so the chat UI and the command
interface can never drift apart. Confirm, and the region is created and outlined in-world.

Switching to the wand outlines the region you are standing in, the same way GriefPrevention's golden
shovel outlines the claim you are standing in. Switching away clears it.

### Resizing

Both axes are editable, and each uses the tool that suits it:

```
/3dclaim resize height 60 100    # change just the band
/3dclaim resize                  # reselect the footprint with the wand
```

Typing exact Y values beats clicking for a height, and clicking corners beats typing coordinates for
a footprint, so neither form is a wrapper around the other. The wand form keeps the current height
unless you change it in the prompt, and both preserve the region's id, owner, name and **trust list** —
that is the point of resizing rather than deleting and recreating.

Both run the same validation as creation: the region must stay inside its claim, and must not overlap
another. A resize is checked against every region *except itself*, so growing a region a few blocks
is not treated as colliding with where it currently is.

Regions may not overlap. If a selection would share any block with an existing region the creation is
refused and the region in the way is outlined so you can see what you hit. Bands stacked at different
heights are fine — that is the point — but a region may not nest inside, enclose, or clip another.

### Commands

| Command | What it does |
| --- | --- |
| `/3dclaim` | Toggle 3D claim mode (and hand out the wand) |
| `/3dclaim wand` | Get the selection wand (needs `gp3d.wand`) |
| `/3dclaim height <bottom> <top>` | Set the vertical band |
| `/3dclaim confirm [name]` | Create the region |
| `/3dclaim cancel` | Discard the selection |
| `/3dclaim info` | Describe the region you're standing in |
| `/3dclaim list` | List your regions |
| `/3dclaim delete [id]` | Delete a region |
| `/3dclaim resize` | Reselect the footprint with the wand |
| `/3dclaim resize height <bottom> <top>` | Change only the height |
| `/3dclaim trust <player> [build\|container\|access\|permission]` | Grant trust inside the region |
| `/3dclaim untrust <player>` | Revoke trust |
| `/3dclaim show [id]` | Outline a region |
| `/3dclaim migrate <sqlite\|mysql>` | Import regions from another backend (admin) |

`trust` defaults to `build`, and accepts `public` to grant everyone. Trust levels use
GriefPrevention's own hierarchy, so build implies container implies access.

### Permissions

| Node | Default | Grants |
| --- | --- | --- |
| `gp3d.use` | everyone | Enter 3D claim mode; create regions in claims you own or manage |
| `gp3d.wand` | op | Receive a wand from `/3dclaim wand` |
| `gp3d.admin` | op | Manage any region, bypass limits |
| `gp3d.unlimited` | op | Exempt from the region limit |
| `gp3d.limit.<n>` | — | Per-rank region cap, e.g. `gp3d.limit.25`; highest matching node wins |

GriefPrevention's bypass rule is mirrored exactly, so a region is never more permissive than the
claim around it. Bypass requires `/ignoreclaims` to be toggled **on** *and* the matching permission —
holding the permission alone is not enough.

> **Testing note:** `griefprevention.ignoreclaims` is granted to every operator by default, so if
> bypass keyed off the permission alone, regions would appear not to work the moment you opped
> yourself. They do apply to ops; run `/ignoreclaims` to deliberately bypass them, and again to stop.

## Trust model: sealed box

Inside a region, only these may act:

- players with region trust at the required level
- the region's owner
- the claim's owner and managers (a landlord never loses access to their own claim)
- admins bypassing claims

Everyone else is denied, **including players with trust on the parent claim**. That's what makes a
region usable for rented or delegated space. Outside the band nothing changes.

`permission` trust in a region lets that player manage *the region's* trust list via
`/3dclaim trust`. It deliberately stops there: a region never confers GriefPrevention's claim-level
`Manage` or `Edit`, so it can't be used to escalate into resizing, deleting, or running `/trust` on
the whole claim.

## Storage

Regions live separately from GriefPrevention's own data, which is never modified. GP may be running
on flat files or MySQL depending on server config and performs its own schema migrations, so writing
into its tables would couple this plugin to that choice and risk collisions.

Two backends are supported, chosen in `config.yml`:

```yaml
storage:
  type: sqlite          # or: mysql (MariaDB works too)
  table-prefix: gp3d_
  sqlite:
    file: regions.db
  mysql:
    host: localhost
    port: 3306
    database: minecraft
    username: root
    password: ''
    properties: 'useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8'
```

```sql
gp3d_regions(id, claim_id, world, owner, name, priority,
             min_x, min_y, min_z, max_x, max_y, max_z, created)
gp3d_trust(region_id, player, permission)      -- player 0-0 == everyone
```

Everything is loaded into memory at startup and reads never touch disk, because the permission check
runs on every block break. Writes go through a single background thread. The MySQL connection is
revalidated before use, since `wait_timeout` can drop it while it still reports as open.

> **Two servers sharing one MySQL database must use different `table-prefix` values.** Region ids and
> GriefPrevention claim ids are both per-server counters, so a shared prefix would collide.

### Switching backends

Changing `storage.type` points the plugin at an empty database — the old regions are still in the old
one, but nothing is protecting anything any more, and the first sign of trouble is usually a player
reporting grief. So after switching, import the old data:

```
/3dclaim migrate sqlite      # run this after switching TO mysql
/3dclaim migrate mysql       # run this after switching TO sqlite
```

This is the one subcommand that also works from the **server console** (without the leading slash),
which is where you already are after editing `config.yml` and restarting. Everything else needs a
player, because it acts on where you are standing.

It reads from the named backend and writes into whichever one is currently active, using the same
connection settings from `config.yml`. Regions whose id collides with an existing one are renumbered
rather than overwriting anything, regions whose claim no longer exists are skipped instead of being
left to attach themselves to whatever claim GriefPrevention next gives that id to, and regions that
would overlap something already present are refused rather than merged — importing one would
reintroduce exactly the ambiguity creation forbids. The command reports all four counts, and reading
happens off the main thread.

### Referential integrity

Because regions reference claims by id across two datastores, integrity is maintained in two places:
`ClaimDeletedEvent` drops a deleted claim's regions (and its subdivisions' recursively), and a
startup sweep prunes regions whose claim vanished while the server was down.

GriefPrevention's claim id counter is monotonic and persisted (`nextClaimID` in flat-file mode, a
table row in database mode), so a deleted claim's id is not normally handed out again. The cleanup
still matters: it stops the sidecar accumulating rows that protect nothing, and it covers the case
where GP's counter is reset or restored from an older backup while claim data survives — at which
point ids *would* be reissued and a stale region would attach itself to an unrelated new claim.

## Configuration

```yaml
storage: { ... }                # see Storage above
wand: GOLDEN_HOE
default-region-limit: -1        # -1 = unlimited
visualization:
  corner: GLOWSTONE
  bottom: LIME_STAINED_GLASS
  top: LIGHT_BLUE_STAINED_GLASS
  seconds: 15
messages: { ... }               # every player-facing string, with & colour codes
```

## Boundary protection

Trust checks only happen where GriefPrevention has a player to check. A piston has none — GP treats
it as a claim-boundary question and never asks about trust — which left a real escalation: a player
trusted only inside a band could place a piston in it and push blocks out into the parent claim.

So physics is stopped at the region boundary the same way GP stops it at the claim boundary:

| Vector | Behaviour |
| --- | --- |
| Pistons (extend and retract) | Cancelled if any moved block, or its destination, sits across a boundary |
| Liquid flow | Cancelled if water or lava would spill across a boundary |
| Fire and block spread | Cancelled across a boundary |
| Tree growth | Only the blocks landing on the far side are dropped, so the rest still grows |

It applies in **both directions**. Outward is the escalation you would expect; inward matters too,
otherwise anyone with parent-claim trust could reach into a rented band with a piston.

These are blocked outright rather than permission-checked, because at the time the events fire there
is no player to attribute them to — which is exactly why the trust listener never saw them.

Because these events are hot (every flowing-water tick, every piston pulse), the check starts with a
chunk-level index lookup, so anything not near a region costs one hash probe. Set
`protect-boundaries: false` if it conflicts with an existing farm.

## Limits worth knowing

- **Claim-wide toggles stay claim-wide.** Explosions, mob griefing and PvP are properties of the
  claim rather than of a trust check, so they ignore region bounds.
- **GP's own visualisation is still flat.** The golden shovel and `/claim` inspect draw the claim's
  footprint with no height. Use `/3dclaim show` or `/3dclaim info` to see a band.
- **Regions are free.** They don't consume claim blocks, matching how GP treats subdivisions.
- **Regions never overlap.** Creation is refused if the new box would share a block with an
  existing one, so exactly one region governs any position. Nesting is refused for the same reason,
  matching GriefPrevention's own refusal to nest subdivisions. Stacking is unaffected — bands at
  different heights share no blocks.

## Building

```bash
./gradlew build          # compiles, runs the logic tests, produces build/libs/
./gradlew verify         # tests only
```

GriefPrevention is resolved from JitPack, so no binary lives in this repo. Drop a jar at
`libs/GriefPrevention.jar` to build against a specific server build instead — `build.gradle` prefers
it when present.

`./gradlew verify` runs 65 assertions covering the trust hierarchy against GP's own
`ClaimPermission.isGrantedBy`, containment boundaries, stacked and nested region resolution,
claim-chain lookup through subdivisions, migration (id collisions and orphaned claims), and a real
SQLite round trip. Point it at a MySQL server to add 8 more covering that dialect end to end:

```bash
GP3D_MYSQL_HOST=127.0.0.1 GP3D_MYSQL_PORT=3306 GP3D_MYSQL_DB=gp3d \
GP3D_MYSQL_USER=root GP3D_MYSQL_PASS=secret ./gradlew verify
```

CI does exactly that against a MariaDB service container, and additionally cross-compiles against
Paper 26.1.2 and 26.2 on every push, so the single-jar compatibility claim above stays enforced
rather than assumed.

---

By Trarn.
