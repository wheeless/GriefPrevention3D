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
package com.trarn.gp3d.session;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Tracks who is currently in 3D claim mode, and their pending selection. */
public final class SessionManager
{
    private final Map<UUID, SelectionSession> sessions = new HashMap<>();

    public boolean isActive(Player player)
    {
        return sessions.containsKey(player.getUniqueId());
    }

    public SelectionSession get(Player player)
    {
        return sessions.get(player.getUniqueId());
    }

    /** Enters 3D claim mode, returning the fresh session. */
    public SelectionSession start(Player player)
    {
        SelectionSession session = new SelectionSession();
        sessions.put(player.getUniqueId(), session);
        return session;
    }

    public void stop(Player player)
    {
        sessions.remove(player.getUniqueId());
    }

    public void clear(UUID uuid)
    {
        sessions.remove(uuid);
    }
}
