package com.darkender.plugins.marksman;

import com.darkender.plugins.marksman.commands.MarksmanCommand;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.function.Predicate;

public class Marksman extends JavaPlugin implements Listener
{
    private NamespacedKey gunFlag = new NamespacedKey(this, "gun");
    
    @Override
    public void onEnable()
    {
        MarksmanCommand marksmanCommand = new MarksmanCommand(this);
        getCommand("marksman").setExecutor(marksmanCommand);
        getCommand("marksman").setTabCompleter(marksmanCommand);
        
        getServer().getPluginManager().registerEvents(this, this);
        
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable()
        {
            @Override
            public void run()
            {
                for(Player p : Bukkit.getOnlinePlayers())
                {
                    gunTick(p, p.getInventory().getItemInMainHand(), false);
                    gunTick(p, p.getInventory().getItemInOffHand(), true);
                }
            }
        }, 1L, 4L);
    }
    
    private Location getHandScreenLocation(Location loc, boolean offhand)
    {
        Location spawnFrom = loc.clone();
        Vector normal2D = spawnFrom.getDirection().clone().setY(0).normalize()
                .rotateAroundY((offhand ? 1 : -1) * (Math.PI / 2))
                .multiply(0.40).setY(-0.35);
        spawnFrom.add(normal2D);
        spawnFrom.add(loc.getDirection().clone().multiply(-0.3));
        return spawnFrom;
    }
    
    private void gunTick(Player player, ItemStack item, boolean offhand)
    {
        if(item == null)
        {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if(meta == null)
        {
            return;
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        if(data.has(gunFlag, PersistentDataType.STRING))
        {
            String gunName = data.get(gunFlag, PersistentDataType.STRING);
            
            if(gunName.equals("debug"))
            {
                // Fire debug particles
                
                // Raytrace to get nearest collision
                //Location debugStart = getHandScreenLocation(player.getEyeLocation(), offhand);
                Location debugStart = player.getEyeLocation();
                RayTraceResult rayTraceResult = debugStart.getWorld().rayTrace(debugStart, player.getEyeLocation().getDirection(),
                        50.0, FluidCollisionMode.NEVER, true, 0.0, new Predicate<Entity>()
                        {
                            @Override
                            public boolean test(Entity entity)
                            {
                                // Ensure the raytrace doesn't collide with the player
                                return (entity.getEntityId() != player.getEntityId());
                            }
                        });
                
                Location modelLoc = getHandScreenLocation(player.getEyeLocation(), offhand);
                Vector modelDirection;
                double distance;
                if(rayTraceResult == null)
                {
                    distance = 50.0;
                    Vector to = player.getEyeLocation().toVector().add(player.getEyeLocation().getDirection().multiply(distance));
                    Vector from = modelLoc.toVector();
                    modelDirection = to.subtract(from).normalize();
                }
                else
                {
                    Location hitLoc = new Location(debugStart.getWorld(), rayTraceResult.getHitPosition().getX(),
                            rayTraceResult.getHitPosition().getY(), rayTraceResult.getHitPosition().getZ());
                    distance = modelLoc.distance(hitLoc);
                    hitLoc.getWorld().spawnParticle(Particle.FLAME, hitLoc, 0);
    
                    Vector to = hitLoc.toVector();
                    Vector from = modelLoc.toVector();
                    modelDirection = to.subtract(from).normalize();
                }
                
                // Draw particles extending until the raytrace collides or expires
                double distanceProgress = 0.0;
                double stepDistance = 0.15;
                Vector step = modelDirection.clone().multiply(stepDistance);
                Location current = modelLoc.clone();
                while(distanceProgress <= distance)
                {
                    if((distance - distanceProgress) < 5)
                    {
                        if(distance != 50)
                        {
                            if(stepDistance > 0.15)
                            {
                                stepDistance -= 0.3;
                                step = modelDirection.clone().multiply(stepDistance);
                            }
                            else if(stepDistance < 0.15)
                            {
                                stepDistance = 0.15;
                                step = modelDirection.clone().multiply(stepDistance);
                            }
                        }
                    }
                    else if(stepDistance < 2.0)
                    {
                        stepDistance += 0.15;
                        step = modelDirection.clone().multiply(stepDistance);
                    }
                    
                    current.getWorld().spawnParticle(Particle.REDSTONE, current, 0, new Particle.DustOptions(
                            (distance - distanceProgress) < 3 ? Color.RED : Color.AQUA, 0.5F));
                    current = current.add(step);
                    distanceProgress += stepDistance;
                }
            }
        }
    }
    
    public ItemStack getDebugGun()
    {
        ItemStack debugGun = new ItemStack(Material.DIAMOND_HOE);
        debugGun.addUnsafeEnchantment(Enchantment.ARROW_KNOCKBACK, 1);
        ItemMeta meta = debugGun.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.setDisplayName(ChatColor.AQUA + "Debug Gun");
        meta.getPersistentDataContainer().set(gunFlag, PersistentDataType.STRING, "debug");
        debugGun.setItemMeta(meta);
        return debugGun;
    }
    
    @EventHandler
    public void onInteract(PlayerInteractEvent event)
    {
        if(event.getItem() == null)
        {
            return;
        }
        
        ItemMeta meta = event.getItem().getItemMeta();
        if(meta == null)
        {
            return;
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        if(data.has(gunFlag, PersistentDataType.STRING))
        {
            String gunName = data.get(gunFlag, PersistentDataType.STRING);
            event.getPlayer().sendMessage(ChatColor.GOLD + "Interacted with " + ChatColor.DARK_AQUA + gunName +
                    ChatColor.BLUE + " (" + event.getAction().name().toLowerCase() + ")");
            event.setCancelled(true);
        }
    }
}
