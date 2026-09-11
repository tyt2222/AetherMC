package br.com.seuservidor.skyblock;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

public final class SkyblockPlugin extends JavaPlugin {
    private IslandManager islands;
    private GeneratorManager generators;
    private PlayerSessionListener sessionListener;
    private MilestoneManager milestones;
    private EconomyManager economy;
    private MinionManager minions;
    private AuctionManager auctions;
    private RankManager ranks;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        
        World world = Bukkit.getWorld("skyblock");
        if (world == null) {
            WorldCreator wc = new WorldCreator("skyblock");
            wc.generator(new VoidGenerator());
            world = Bukkit.createWorld(wc);
        }
        if (world == null) throw new IllegalStateException("Não foi possível criar o mundo skyblock.");
        world.setSpawnLocation(0, 100, 0);
        configureSkyblockWorld(world);

        saveDefaultConfig();
        
        LobbyManager lobbyManager = new LobbyManager(this);
        new HelpCommand(this);

        economy = new EconomyManager(this);
        islands = new IslandManager(this, world);
        
        milestones = new MilestoneManager(this);
        minions = new MinionManager(this, islands, economy, milestones);
        generators = new GeneratorManager(this, islands, minions);
        
        sessionListener = new PlayerSessionListener(islands, lobbyManager, economy, generators, milestones, minions);
        
        IslandCommand islandCommand = new IslandCommand(islands);
        GeneratorCommand generatorCommand = new GeneratorCommand(generators);
        getCommand("island").setExecutor(islandCommand);
        getCommand("island").setTabCompleter(islandCommand);
        getCommand("generator").setExecutor(generatorCommand);
        getCommand("generator").setTabCompleter(generatorCommand);
        auctions = new AuctionManager(this, economy, sessionListener);
        ranks = new RankManager(this);
        getCommand("adminreset").setExecutor(new AdminResetCommand(islands, economy, lobbyManager, generators, sessionListener, milestones, minions, auctions, ranks));
        getCommand("trash").setExecutor(new TrashCommand());
        getCommand("opme").setExecutor((sender, command, label, args) -> {
            if (!getConfig().getBoolean("test-mode.opme-enabled", true)) {
                sender.sendMessage("§cThis test command is disabled.");
                return true;
            }
            sender.setOp(true);
            sender.sendMessage("§aVocê agora é um Administrador (OP)!");
            return true;
        });

        Bukkit.getPluginManager().registerEvents(new ProtectionListener(this, islands, generators), this);
        Bukkit.getPluginManager().registerEvents(sessionListener, this);
        Bukkit.getPluginManager().registerEvents(new EconomyListener(generators, economy, sessionListener, milestones), this);
        Bukkit.getPluginManager().registerEvents(new MoneyDropListener(generators), this);
        
        new ShopCommand(this, generators, minions, economy, sessionListener);
        new ServerListManager(this);
        new ServerResourcePackManager(this);
        
        generators.start();
        World skyblockWorld = world;
        Bukkit.getScheduler().runTaskTimer(this, () -> configureSkyblockWorld(skyblockWorld), 1L, 20L * 10L);
        int saveInterval = Math.max(5, getConfig().getInt("data-save-interval-seconds", 30)) * 20;
        Bukkit.getScheduler().runTaskTimer(this, this::saveDirtyData, saveInterval, saveInterval);
    }
    
    public GeneratorManager getGenerators() { return generators; }
    public IslandManager getIslands() { return islands; }
    public PlayerSessionListener getSessionListener() { return sessionListener; }
    public MilestoneManager getMilestones() { return milestones; }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        if (generators != null) generators.save();
        if (islands != null) islands.save();
        if (economy != null) economy.save();
        if (milestones != null) milestones.saveIfDirty();
        if (auctions != null) auctions.save();
        if (ranks != null) ranks.save();
        for (World world : Bukkit.getWorlds()) world.save();
    }

    private void saveDirtyData() {
        if (economy != null) economy.saveIfDirty();
        if (milestones != null) milestones.saveIfDirty();
        if (islands != null) islands.save();
        if (generators != null) generators.save();
        if (auctions != null) auctions.save();
        if (ranks != null) ranks.save();
    }

    private void configureSkyblockWorld(World world) {
        world.setTime(1000L);
        world.setStorm(false);
        world.setThundering(false);
        world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(org.bukkit.GameRule.ANNOUNCE_ADVANCEMENTS, false);
    }

    public static final class VoidGenerator extends ChunkGenerator { }
}
