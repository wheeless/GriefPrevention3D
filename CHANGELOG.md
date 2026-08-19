# Changelog

## 0.0.2 — first public build

Adds vertically bounded ("3D") regions inside GriefPrevention claims. A region carves a height band
out of a claim: inside the band only region-trusted players may act, outside it the claim behaves
exactly as it always has. Stacked bands are the point — floor 1 can belong to one player and floor 2
to another.

**Features**
- `/3dclaim` enters claim mode; select two corners with a wand, then set the band from a clickable
  chat prompt or with `/3dclaim height <bottom> <top>`
- Per-region trust using GriefPrevention's own levels (build / container / access / permission)
- Sealed regions: parent-claim trustees are kept out, while the claim owner and managers keep access
- `/3dclaim resize` (wand) and `/3dclaim resize height <bottom> <top>`
- Regions may never overlap or nest, so exactly one region governs any block
- Boundary protection: pistons, liquids, fire spread and tree growth cannot cross a region edge
- SQLite (default) or MySQL/MariaDB storage, with `/3dclaim migrate` to move between them
- Wand outlines the region you are standing in, like the golden shovel does for claims

**Requirements**
- Paper 1.21.x through 26.x (single jar; not Spigot — the UI uses Paper's Adventure API)
- GriefPrevention 16.18.x
- Java 21+

**Notes**
- Regions live in their own database keyed by claim id; GriefPrevention's data is never modified.
  Works with GP on flat-file or MySQL storage.
- Permissions: `gp3d.use` (default all), `gp3d.wand`, `gp3d.admin`, `gp3d.unlimited`,
  `gp3d.limit.<n>`
