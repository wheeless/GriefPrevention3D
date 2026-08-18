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

import com.trarn.gp3d.session.SelectionSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

/**
 * The "Bottom: 65, Top: 100" prompt shown once both corners are picked.
 *
 * <p>Every button just runs the equivalent command, so the chat UI and the command interface stay
 * exactly in step and neither can drift from the other.
 */
public final class BandPrompt
{
    private BandPrompt() {}

    public static void send(Player player, SelectionSession session, int area)
    {
        int bottom = session.getBottom();
        int top = session.getTop();

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("  3D Claim ", NamedTextColor.AQUA, TextDecoration.BOLD)
                .append(Component.text("— set the height of your claim", NamedTextColor.GRAY)
                        .decoration(TextDecoration.BOLD, false)));

        player.sendMessage(Component.text("  Footprint: ", NamedTextColor.DARK_GRAY)
                .append(Component.text(area + " blocks  ", NamedTextColor.GRAY))
                .append(Component.text("(" + session.getMinX() + ", " + session.getMinZ()
                        + ") → (" + session.getMaxX() + ", " + session.getMaxZ() + ")",
                        NamedTextColor.DARK_GRAY)));

        player.sendMessage(row("Bottom", bottom, true, bottom, top));
        player.sendMessage(row("Top", top, false, bottom, top));

        player.sendMessage(Component.text("  ")
                .append(button(" Confirm ", NamedTextColor.GREEN, "/3dclaim confirm",
                        "Create the 3D region"))
                .append(Component.text("  "))
                .append(button(" Cancel ", NamedTextColor.RED, "/3dclaim cancel",
                        "Discard this selection")));

        player.sendMessage(Component.text("  or type ", NamedTextColor.DARK_GRAY)
                .append(Component.text("/3dclaim height <bottom> <top>", NamedTextColor.GRAY)));
        player.sendMessage(Component.empty());
    }

    private static Component row(String label, int value, boolean isBottom, int bottom, int top)
    {
        Component line = Component.text("  " + label + ": ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(value), NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text("  "));

        for (int delta : new int[] { -16, -1, 1, 16 })
        {
            int newBottom = isBottom ? bottom + delta : bottom;
            int newTop = isBottom ? top : top + delta;
            String command = "/3dclaim height " + newBottom + " " + newTop;
            String text = delta > 0 ? " +" + delta + " " : " " + delta + " ";
            line = line.append(button(text, delta > 0 ? NamedTextColor.GREEN : NamedTextColor.RED,
                    command, "Set " + label.toLowerCase() + " to " + (isBottom ? newBottom : newTop)))
                    .append(Component.text(" "));
        }
        return line;
    }

    private static Component button(String text, NamedTextColor colour, String command, String hover)
    {
        return Component.text(text, colour)
                .decoration(TextDecoration.BOLD, true)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }
}
