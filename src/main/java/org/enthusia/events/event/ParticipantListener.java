package org.enthusia.events.event;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.enthusia.events.gui.EventVoteGui;
import org.enthusia.events.gui.RestoreConfirmGui;
import org.enthusia.events.skin.SkinCache;

public final class ParticipantListener implements Listener {

    private final EventManager eventManager;
    private final EventVoteGui voteGui;
    private final RestoreConfirmGui restoreConfirmGui;
    private final SkinCache skinCache;

    public ParticipantListener(EventManager eventManager, EventVoteGui voteGui, RestoreConfirmGui restoreConfirmGui, SkinCache skinCache) {
        this.eventManager = eventManager;
        this.voteGui = voteGui;
        this.restoreConfirmGui = restoreConfirmGui;
        this.skinCache = skinCache;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        skinCache.cacheFromOnline(event.getPlayer());
        eventManager.handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        eventManager.handleQuit(event.getPlayer());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        voteGui.handleClick(event);
        restoreConfirmGui.handleClick(event);
    }
}
