/*
 * Copyright (c) 2026.
 */

package com.nsgwick.personalpvp.managers;

import com.nsgwick.personalpvp.PPVPPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class PapiManager extends PlaceholderExpansion {

    private final PPVPPlugin plugin;

    public PapiManager(PPVPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "personalpvp";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        // keeps expansion registered across /papi reload
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        // TODO: replace these with your real calls:
        boolean enabled = PPVPPlugin.inst().pvp().pvpPositive(player.getUniqueId());
        boolean locked  = PPVPPlugin.inst().pvp().pvpNegative(player.getUniqueId());

        switch (params.toLowerCase()) {
            case "enabled":
                return enabled ? "true" : "false";

            case "status":
                return enabled ? "enabled" : "disabled";
            case "status_colored":
                return enabled
                        ? (ChatColor.GREEN + "Enabled")
                        : (ChatColor.RED + "Disabled");
            case "locked":
                return locked ? "true" : "false";

            case "toggleable":
            case "can_toggle":
                return locked ? "false" : "true";

            default:
                return null; // unknown placeholder -> PAPI will leave it unhandled
        }
    }
}