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
