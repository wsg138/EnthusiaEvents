package org.enthusia.events.setup;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class SetupListener implements Listener {

    private final SetupWizard setupWizard;

    public SetupListener(SetupWizard setupWizard) {
        this.setupWizard = setupWizard;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_AIR)
                    && setupWizard.session(event.getPlayer()).isPresent()
                    && setupWizard.isAirSelectionTool(event.getPlayer())) {
                event.setCancelled(true);
                setupWizard.handleAirClick(event.getPlayer());
            }
            return;
        }
        if (setupWizard.session(event.getPlayer()).isEmpty()) {
            return;
        }
        if (!setupWizard.hasHeldSetupTool(event.getPlayer())) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        event.setCancelled(true);
        if (setupWizard.usesRelativePlacementTarget(event.getPlayer())) {
            clicked = clicked.getRelative(event.getBlockFace());
        } else if (event.getPlayer().isSneaking()) {
            clicked = clicked.getRelative(BlockFace.UP);
        }
        setupWizard.handleClick(event.getPlayer(), clicked);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        setupWizard.handleBlockPlacement(event);
    }
}
