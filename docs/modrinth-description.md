# GriefPrevention3D

**Vertically bounded claims for GriefPrevention.** Rent out a floor, not a building.

GriefPrevention claims are columns — they run from a floor height all the way to the sky. That makes
some very ordinary builds impossible to protect properly: a shopping arcade where each floor has a
different owner, an apartment block, a basement you want to lend out without handing over the house.

GriefPrevention3D adds **regions**: a height band carved out of a claim you already own. Inside the
band, only players you trust *to that band* may build, open containers or interact. Outside it, your
claim behaves exactly as it always has.

Bands can be stacked. Floor 1 can belong to one player and floor 2 to another, in the same footprint,
with neither able to touch the other's floor.

---

## How it works

1. Make a claim the normal way, with the golden shovel.
2. Run `/3dclaim` and pick two corners with the wand — same as claiming, just a different tool.
3. Set the height from the chat prompt, or type it:

```
  3D Claim — set the height of your claim
  Footprint: 176 blocks  (10, 30) → (20, 45)
  Bottom: 65   -16   -1   +1   +16
  Top:    80   -16   -1   +1   +16
     Confirm     Cancel
```

4. `/3dclaim trust <player>` to let someone in.

The buttons are clickable, and every one of them just runs the equivalent command — so you can drive
the whole thing from chat or from commands, whichever you prefer.

Switching to the wand outlines the region you're standing in, the way the golden shovel outlines the
claim you're standing in.

---

## Trust

Regions use GriefPrevention's own trust levels, so there's nothing new to learn:

`/3dclaim trust <player> [build | container | access | permission]`

Build implies container implies access, exactly as it does for claims. `public` grants to everyone.

Regions are **sealed**: people trusted on the surrounding claim are *not* automatically let into a
band. That's what makes a region usable for space you're renting or lending out. The claim's owner
and managers always keep access to their own claim, and admins bypassing claims are never blocked.

---

## Commands

| Command | Description |
| --- | --- |
| `/3dclaim` | Toggle 3D claim mode |
| `/3dclaim wand` | Get the selection wand |
| `/3dclaim height <bottom> <top>` | Set the vertical band |
| `/3dclaim confirm [name]` | Create the region |
| `/3dclaim info` | Describe the region you're standing in |
| `/3dclaim list` | List your regions |
| `/3dclaim resize` | Reselect the footprint with the wand |
| `/3dclaim resize height <bottom> <top>` | Change only the height |
| `/3dclaim trust <player> [level]` | Grant trust inside the region |
| `/3dclaim untrust <player>` | Revoke trust |
| `/3dclaim delete [id]` | Delete a region |
| `/3dclaim show [id]` | Outline a region |
| `/3dclaim migrate <sqlite\|mysql>` | Import regions from another backend |

Resizing keeps the region's trust list — no need to re-add everyone.

## Permissions

| Node | Default | Grants |
| --- | --- | --- |
| `gp3d.use` | everyone | Create regions in claims you own or manage |
| `gp3d.wand` | op | Receive a wand from `/3dclaim wand` |
| `gp3d.admin` | op | Manage any region, bypass limits |
| `gp3d.unlimited` | op | Exempt from the region limit |
| `gp3d.limit.<n>` | — | Per-rank cap, e.g. `gp3d.limit.25` |

The wand is an ordinary item (a golden hoe by default, configurable), so `gp3d.wand` only gates the
free handout — anyone can craft one.

---

## Anti-grief

A vertical claim invites indirect grief, so physics is stopped at the region boundary the same way
GriefPrevention stops it at the claim boundary:

- **Pistons** can't push or pull blocks across a boundary
- **Liquids** can't spill across one
- **Fire and block spread** can't cross one
- **Trees** won't grow past one

It works in both directions — outward, so band trust can't reach into the claim; and inward, so claim
trust can't reach into a rented band.

Regions also may not overlap or nest, so exactly one region governs any block. Stacked bands at
different heights are fine, since they share no blocks.

---

## Storage

SQLite by default — zero setup. MySQL and MariaDB are supported for servers that centralise plugin
data, and `/3dclaim migrate` moves your regions between backends without losing anything.

Regions live in their own database keyed by GriefPrevention's claim id. **GriefPrevention's own data
is never modified**, and it doesn't matter whether GP is running on flat files or a database.

---

## Compatibility

| | |
| --- | --- |
| **Server** | Paper 1.21.x through 26.x — one jar |
| **Not supported** | Spigot / CraftBukkit (the chat UI uses Paper's Adventure API) |
| **Requires** | GriefPrevention 16.18.x |
| **Java** | 21 or newer |

---

## Notes

- Regions don't cost claim blocks, the same way subdivisions don't.
- Claim-wide settings — explosions, mob griefing, PvP — are properties of the claim rather than of a
  trust check, so they stay claim-wide.
- GriefPrevention's own visualisation still draws a flat footprint. Use `/3dclaim show` to see a band.

Licensed GPL-3.0-or-later, like GriefPrevention itself.
