package org.enthusia.events.event;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collection;

public record PlayerSnapshot(
        Location location,
        ItemStack[] inventory,
        ItemStack[] armor,
        ItemStack offhand,
        double health,
        int foodLevel,
        float saturation,
        float exhaustion,
        int totalExperience,
        GameMode gameMode,
        Collection<PotionEffect> potionEffects,
        boolean allowFlight,
        boolean flying,
        float walkSpeed,
        float flySpeed
) {

    public static PlayerSnapshot capture(Player player) {
        return new PlayerSnapshot(
                player.getLocation().clone(),
                player.getInventory().getContents().clone(),
                player.getInventory().getArmorContents().clone(),
                player.getInventory().getItemInOffHand().clone(),
                Math.min(player.getHealth(), player.getMaxHealth()),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExhaustion(),
                player.getTotalExperience(),
                player.getGameMode(),
                new ArrayList<>(player.getActivePotionEffects()),
                player.getAllowFlight(),
                player.isFlying(),
                player.getWalkSpeed(),
                player.getFlySpeed()
        );
    }
}
