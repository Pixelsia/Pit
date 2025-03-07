package tokyo.peya.plugins.areapvp;

import com.zaxxer.hikari.*;
import net.milkbowl.vault.economy.*;
import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Material;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.*;
import org.bukkit.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.*;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.*;
import tokyo.peya.plugins.areapvp.command.*;
import tokyo.peya.plugins.areapvp.events.*;
import tokyo.peya.plugins.areapvp.perk.*;

import java.lang.reflect.*;
import java.util.*;

public class AreaPvP extends JavaPlugin
{
    public static boolean debugging = false;

    public static FileConfiguration config;
    public static AreaPvP plugin;
    public static HashMap<Location, Tuple<Integer, Material>> block;
    public static HashMap<UUID, Integer> arrows; //宣伝
    public static Timer timer;
    public static HikariDataSource data;
    public static HashMap<UUID, String> gui;
    public static Economy economy;
    public static int spawnloc;

    public static AreaPvP getPlugin()
    {
        return plugin;
    }

    public static void refreshScoreBoard(Player player)
    {
        Scoreboard scoreboard = player.getScoreboard();
        if (scoreboard == null)
            return;
        scoreboard.getTeams().forEach(Team::unregister);
    }

    private static Object getPrivateField(Object object, String field) throws SecurityException,
            NoSuchFieldException, IllegalArgumentException, IllegalAccessException
    {
        Class<?> clazz = object.getClass();
        Field objectField = clazz.getDeclaredField(field);
        objectField.setAccessible(true);
        Object result = objectField.get(object);
        objectField.setAccessible(false);
        return result;
    }

    public static void unRegisterBukkitCommand(PluginCommand cmd)
    {
        try
        {
            Object result = getPrivateField(Bukkit.getPluginManager(), "commandMap");
            SimpleCommandMap commandMap = (SimpleCommandMap) result;
            Object map = getPrivateField(commandMap, "knownCommands");
            @SuppressWarnings("unchecked")
            HashMap<String, Command> knownCommands = (HashMap<String, Command>) map;
            knownCommands.remove(cmd.getName());
            for (String alias : cmd.getAliases())
            {
                if (knownCommands.containsKey(alias) && knownCommands.get(alias).toString().contains("CmdSender"))
                {
                    knownCommands.remove(alias);
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void onEnable()
    {
        plugin = this;
        Bukkit.getPluginManager().registerEvents(new Events(), this);
        Bukkit.getPluginManager().registerEvents(new PerkProcess(), this);
        Bukkit.getPluginManager().registerEvents(new GUI(), this);
        Bukkit.getPluginManager().registerEvents(new DamageModifier(), this);
        if (Bukkit.getPluginManager().isPluginEnabled("EazyNick"))
            Bukkit.getPluginManager().registerEvents(new NickHandler(), this);
        Bukkit.getPluginManager().registerEvents(new DamageModifier(), this);
        getCommand("areapvp").setExecutor(new Main());
        getCommand("spawn").setExecutor(new Spawn());
        getCommand("oof").setExecutor(new Oof());
        getCommand("pvpd").setExecutor(new PitDebug());
        getCommand("view").setExecutor(new View());
        saveDefaultConfig();
        config = getConfig();
        spawnloc = config.getInt("spawnLoc");

        block = new HashMap<>();
        arrows = new HashMap<>();
        gui = new HashMap<>();
        if (getServer().getPluginManager().getPlugin("Vault") == null)
        {
            System.out.println("Please install 1 plugin(s) [Vault]");
            getServer().getPluginManager().disablePlugin(this);
        }

        Init.initShop();

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);

        economy = rsp.getProvider();

        HikariConfig config = new HikariConfig();
        config.setDriverClassName(getConfig().getString("sql.driver"));
        config.setJdbcUrl(getConfig().getString("sql.jdbc")
                .replace("%%data%%", getDataFolder().getAbsolutePath()));
        data = new HikariDataSource(config);
        Init.initDatabase();

        timer = new Timer();
        timer.runTaskTimer(this, 0L, 20L); //1秒に1回実行

        Init.schedulePlayerTimer();

        Init.scheduleSpawnTimer();

        Init.scheduleArrowTimer();

        Bukkit.getOnlinePlayers().forEach(player -> {
            refreshScoreBoard(player);
            new Events().onJoin(new PlayerJoinEvent(player, ""));
        });
    }

    @Override
    public void onDisable()
    {
        if (data != null)
            data.close();
        block.forEach((b, v) -> b.getWorld().getBlockAt(b).setType(v.b()));
    }
}
