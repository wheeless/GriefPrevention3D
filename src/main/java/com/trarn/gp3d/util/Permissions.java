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
package com.trarn.gp3d.util;

public final class Permissions
{
    private Permissions() {}

    /** Required to enter 3D claim mode and create regions. */
    public static final String USE = "gp3d.use";
    /**
     * Receive a wand from {@code /3dclaim wand}. Purely a convenience: the wand is an ordinary
     * item, so anyone can craft one and select with it. This node only gates the free handout.
     */
    public static final String WAND = "gp3d.wand";
    /** Manage any region regardless of ownership. */
    public static final String ADMIN = "gp3d.admin";
    /** Prefix for per-rank region limits, e.g. gp3d.limit.25. */
    public static final String LIMIT_PREFIX = "gp3d.limit.";
    /** Exempt from the region limit entirely. */
    public static final String UNLIMITED = "gp3d.unlimited";

    /** GriefPrevention's own bypass nodes, honoured so admins are never blocked by a region. */
    public static final String GP_IGNORE_CLAIMS = "griefprevention.ignoreclaims";
    public static final String GP_DELETE_CLAIMS = "griefprevention.deleteclaims";
    public static final String GP_ADMIN_CLAIMS = "griefprevention.adminclaims";
}
