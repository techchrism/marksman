package com.darkender.plugins.marksman.guns;

import com.darkender.plugins.marksman.Marksman;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.function.Predicate;

public class HuntingRifle extends Gun
{
    private BukkitTask reloadTask;
    private long lastShot = 0;
    private final long initialized;
    
    public HuntingRifle(GunSettings gunSettings, Player player, ItemStack item)
    {
        super(gunSettings, player, item);
        initialized = System.nanoTime();
    }
    
    @Override
    public void fire()
    {
        if(getCurrentAmmo() == 0)
        {
            reload();
            return;
        }
    
        // Cancel reloading if reloading while the gun is firing
        if(reloadTask != null)
        {
            reloadTask.cancel();
            reloadTask = null;
        }
        
        long nanoTime = System.nanoTime();
        if((nanoTime - lastShot) < Gun.ticksToNanoseconds(gunSettings.getShootDelay()) ||
                (nanoTime - initialized)  < Gun.ticksToNanoseconds(gunSettings.getShootDelay()))
        {
            return;
        }
        lastShot = nanoTime;
        
        gunSettings.getFireSound().play(player);
        setCurrentAmmo(getCurrentAmmo() - 1);
    
        Location rayStart = player.getEyeLocation();
        RayTraceResult rayTraceResult = rayStart.getWorld().rayTrace(rayStart, player.getEyeLocation().getDirection(),
                100.0, FluidCollisionMode.NEVER, true, 0.0, new Predicate<Entity>()
                {
                    @Override
                    public boolean test(Entity entity)
                    {
                        // Ensure the raytrace doesn't collide with the player
                        return (entity.getEntityId() != player.getEntityId()) && (entity instanceof LivingEntity);
                    }
                });
        
        if(rayTraceResult.getHitBlock() != null)
        {
            if(gunSettings.isTerrainParticles())
            {
                rayTraceResult.getHitBlock().getWorld().playEffect(rayTraceResult.getHitBlock().getLocation(),
                        Effect.STEP_SOUND, rayTraceResult.getHitBlock().getType());
            }
        }
        else if(rayTraceResult.getHitEntity() != null)
        {
            LivingEntity e = (LivingEntity) rayTraceResult.getHitEntity();
            double damage = gunSettings.getDamage();
            boolean headshot = false;
            if(gunSettings.isHeadshotsEnabled())
            {
                Vector relativeHit = rayTraceResult.getHitPosition().subtract(e.getLocation().toVector());
                if((e.getHeight() - relativeHit.getY()) < 0.60)
                {
                    damage += gunSettings.getHeadshotDamage();
                    headshot = true;
                }
            }
    
            EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(player, e, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage);
            Bukkit.getPluginManager().callEvent(event);
            if(!event.isCancelled())
            {
                if(headshot)
                {
                    gunSettings.getHeadshotSound().play(player);
                    if(gunSettings.isHeadshotFirework())
                    {
                        Firework fw = e.getWorld().spawn(e.getLocation(), Firework.class);
                        FireworkMeta meta = fw.getFireworkMeta();
                        meta.addEffect(FireworkEffect.builder().withColor(Color.RED).withFlicker().withTrail().build());
                        fw.setFireworkMeta(meta);
                    }
                }
                e.setVelocity(e.getVelocity().add(player.getEyeLocation().getDirection().multiply(gunSettings.getKnockback())));
                e.damage(damage, player);
            }
        }
        
        if(getCurrentAmmo() == 0)
        {
            reload();
        }
    }
    
    @Override
    public void reload()
    {
        if(getCurrentAmmo() == gunSettings.getReloadAmount())
        {
            return;
        }
        
        if(reloadTask == null)
        {
            scheduleReloadSingle();
        }
    }
    
    @Override
    public void close()
    {
        if(reloadTask != null)
        {
            reloadTask.cancel();
            reloadTask = null;
        }
    }
    
    private void scheduleReloadSingle()
    {
        BukkitRunnable runnable = new BukkitRunnable()
        {
            @Override
            public void run()
            {
                reloadSingle();
            }
        };
        reloadTask = runnable.runTaskLater(Marksman.instance, gunSettings.getReloadDelay());
    }
    
    private void reloadSingle()
    {
        setCurrentAmmo(getCurrentAmmo() + 1);
        gunSettings.getReloadSound().play(player);
        lastShot += Gun.ticksToNanoseconds(gunSettings.getReloadDelay());
        
        if(getCurrentAmmo() < gunSettings.getReloadAmount())
        {
            scheduleReloadSingle();
        }
        else
        {
            reloadTask = null;
        }
    }
}