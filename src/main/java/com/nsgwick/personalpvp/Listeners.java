/*
 * Updated for MC 1.21 by ChatGPT
 */

package com.nsgwick.personalpvp;

import com.nsgwick.personalpvp.managers.TaskManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;
import org.bukkit.projectiles.ProjectileSource;
import org.jetbrains.annotations.NotNull;
import com.nsgwick.personalpvp.config.GeneralConfig;

import java.util.*;
import java.util.stream.Collectors;

public class Listeners implements Listener {

    public Listeners(final PPVPPlugin pl) {

        if (pl.conf().get().getProperty(GeneralConfig.PREVENT_PLAYERDAMAGE)) {
            pl.getServer().getPluginManager().registerEvents(new DamageByEntityListener(), pl);
        }

        if (pl.conf().get().getProperty(GeneralConfig.PREVENT_RODS)) {
            pl.getServer().getPluginManager().registerEvents(new FishingListener(), pl);
        }

        if (pl.conf().get().getProperty(GeneralConfig.PREVENT_PROJECTILES)) {
            pl.getServer().getPluginManager().registerEvents(new ProjectileListener(), pl);
        }

        if (pl.conf().get().getProperty(GeneralConfig.PREVENT_POTS)) {
            pl.getServer().getPluginManager().registerEvents(new PotionListener(), pl);
        }

        if (pl.conf().get().getProperty(GeneralConfig.PREVENT_FIRE)) {
            pl.getServer().getPluginManager().registerEvents(new CombustionListener(), pl);
        }

        pl.getServer().getPluginManager().registerEvents(new DeathListener(), pl);
    }
}

/* ----------------------------- DEATH LISTENER ----------------------------- */

class DeathListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(final PlayerDeathEvent e) {

        if (e.getEntity().getKiller() == null) return;

        e.setKeepLevel(PPVPPlugin.inst().conf().get().getProperty(GeneralConfig.KEEPXP_ON_PVP_DEATH));

        if (!PPVPPlugin.inst().conf().get().getProperty(GeneralConfig.KEEPINV_ON_PVP_DEATH)) return;

        e.getDrops().clear();
        e.setKeepInventory(true);
    }
}

/* ----------------------------- DAMAGE LISTENER ----------------------------- */

class DamageByEntityListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(@NotNull EntityDamageByEntityEvent e) {

        Entity defender = e.getEntity();
        Entity attacker = e.getDamager();

        if (Utils.Tameables.shouldTameablesCancel(attacker, defender)) {
            e.setCancelled(true);
            return;
        }

        if (!(defender instanceof Player && attacker instanceof Player)) return;

        UUID entityUuid = defender.getUniqueId();
        UUID damagerUuid = attacker.getUniqueId();

        if (attacker instanceof AreaEffectCloud cloud) {
            boolean containsBad =
                    cloud.getCustomEffects().stream()
                            .map(PotionEffect::getType)
                            .anyMatch(Utils.BAD_EFFECTS::contains);

            if (containsBad) e.setCancelled(true);
            return;
        }

        if (PPVPPlugin.inst().pvp().isEitherNegative(entityUuid, damagerUuid)) {
            e.setCancelled(true);
            TaskManager.blockedAttack(entityUuid, damagerUuid);
        }
    }
}

/* ----------------------------- POTION LISTENER ----------------------------- */

class PotionListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCloud(final AreaEffectCloudApplyEvent e) {

        List<LivingEntity> players =
                e.getAffectedEntities().stream()
                        .filter(ent -> ent instanceof Player)
                        .collect(Collectors.toList());

        if (players.isEmpty()) return;

        // Check base potion type (1.21+ uses getBasePotionType())
        PotionType base = e.getEntity().getBasePotionType();
        if (base != null && base.getEffectType() != null && Utils.BAD_EFFECTS.contains(base.getEffectType())) {
            players.forEach(p -> {
                if (PPVPPlugin.inst().pvp().pvpNegative(p.getUniqueId())) {
                    e.getAffectedEntities().remove(p);
                }
            });
        }

        boolean hasBadEffects =
                e.getEntity().getCustomEffects().stream()
                        .map(PotionEffect::getType)
                        .anyMatch(Utils.BAD_EFFECTS::contains);

        if (!hasBadEffects) return;

        players.forEach(p -> {
            if (PPVPPlugin.inst().pvp().pvpNegative(p.getUniqueId())) {
                e.getAffectedEntities().remove(p);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSplash(final PotionSplashEvent e) {

        ProjectileSource shooter = e.getEntity().getShooter();

        if (!(shooter instanceof Player player)) return;

        boolean containsBad =
                e.getPotion().getEffects().stream()
                        .map(PotionEffect::getType)
                        .anyMatch(Utils.BAD_EFFECTS::contains);

        if (!containsBad) return;

        List<Player> affectedPlayers =
                e.getAffectedEntities().stream()
                        .filter(en -> en instanceof Player)
                        .map(en -> (Player) en)
                        .collect(Collectors.toList());

        boolean block = PPVPPlugin.inst().pvp().pvpNegative(player.getUniqueId())
                || affectedPlayers.stream()
                .map(Player::getUniqueId)
                .anyMatch(PPVPPlugin.inst().pvp()::pvpNegative);

        if (block) {
            e.setCancelled(true);
            player.getInventory().addItem(e.getEntity().getItem());
            TaskManager.blockedAttack(
                    affectedPlayers.stream().map(Player::getUniqueId).toArray(UUID[]::new)
            );
            return;
        }

        // Remove invalid recipients safely
        affectedPlayers.stream()
                .filter(p -> PPVPPlugin.inst().pvp().pvpNegative(p.getUniqueId()))
                .forEach(p -> e.getAffectedEntities().remove(p));
    }
}

/* ----------------------------- PROJECTILE LISTENER ----------------------------- */

class ProjectileListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(final ProjectileHitEvent e) {

        if (!(e.getEntity().getShooter() instanceof Player shooter)) return;
        if (!(e.getHitEntity() instanceof Player defender)) return;

        UUID shooterUuid = shooter.getUniqueId();
        UUID defenderUuid = defender.getUniqueId();

        // Snowballs and eggs allowed
        boolean harmless = (e.getEntity() instanceof Snowball) || (e.getEntity() instanceof Egg);

        if (!harmless && PPVPPlugin.inst().pvp().isEitherNegative(shooterUuid, defenderUuid)) {
            e.setCancelled(true);
            TaskManager.blockedAttack(shooterUuid, defenderUuid);
        }
    }
}

/* ----------------------------- FISHING LISTENER ----------------------------- */

class FishingListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(@NotNull PlayerFishEvent e) {

        if (!(e.getCaught() instanceof Player caught)) return;

        UUID caughtUuid = caught.getUniqueId();
        UUID playerUuid = e.getPlayer().getUniqueId();

        if (PPVPPlugin.inst().pvp().isEitherNegative(caughtUuid, playerUuid)) {
            e.setCancelled(true);
            TaskManager.blockedAttack(caughtUuid, playerUuid);
        }
    }
}

/* ----------------------------- COMBUST FIRE LISTENER ----------------------------- */

class CombustionListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombust(EntityCombustByEntityEvent e) {

        if (!(e.getCombuster() instanceof Player combuster)) return;
        if (!(e.getEntity() instanceof Player victim)) return;

        UUID cUUID = combuster.getUniqueId();
        UUID vUUID = victim.getUniqueId();

        if (PPVPPlugin.inst().pvp().isEitherNegative(cUUID, vUUID)) {
            e.setCancelled(true);
            TaskManager.blockedAttack(cUUID, vUUID);
        }
    }
}
