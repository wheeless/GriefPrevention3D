package com.avernix.gp3d.util;

public final class Permissions
{
    private Permissions() {}

    /** Required to enter 3D claim mode and create regions. */
    public static final String USE = "gp3d.use";
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
