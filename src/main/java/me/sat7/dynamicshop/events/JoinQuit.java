package me.sat7.dynamicshop.events;

import me.sat7.dynamicshop.DynamicShop;
import me.sat7.dynamicshop.UpdateCheck;

import me.sat7.dynamicshop.files.CustomConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

public class JoinQuit implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e)
    {
        Player player = e.getPlayer();
        CustomConfig ccUser = DynamicShop.ccUser;

        BukkitTask task = Bukkit.getScheduler().runTaskAsynchronously(DynamicShop.plugin, new Runnable() {
            final String uuid = player.getUniqueId().toString();
            @Override
            public void run() {
                ccUser.get().set(uuid + ".tmpString","");
                ccUser.get().set(uuid +".interactItem","");
                ccUser.get().set(uuid + ".lastJoin", System.currentTimeMillis());
                ccUser.get().addDefault(uuid + ".cmdHelp",true);
                ccUser.save();
            }
        });

        if(DynamicShop.updateAvailable)
        {
            if(e.getPlayer().hasPermission("dshop.admin.shopedit") ||
                    e.getPlayer().hasPermission("dshop.admin.reload"))
            {
                e.getPlayer().sendMessage(DynamicShop.dsPrefix+"New update available!");
                e.getPlayer().sendMessage(UpdateCheck.getResourceUrl());
            }
        }
    }
}
