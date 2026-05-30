package com.nova.novablock;

import com.nova.novablock.antiafk.AntiAfkManager;
import com.nova.novablock.gui.MainMenuConfig;
import com.nova.novablock.island.IslandFlagsManager;
import com.nova.novablock.island.IslandStorageManager;
import com.nova.novablock.boss.BossManager;
import com.nova.novablock.command.AdminCommand;
import com.nova.novablock.command.HelpCommand;
import com.nova.novablock.command.MinionCommand;
import com.nova.novablock.command.OneBlockCommand;
import com.nova.novablock.command.ScoreboardCommand;
import com.nova.novablock.config.ConfigManager;
import com.nova.novablock.economy.EconomyManager;
import com.nova.novablock.event.EventManager;
import com.nova.novablock.gui.GuiManager;
import com.nova.novablock.help.HelpRegistrar;
import com.nova.novablock.hotbar.HotbarMenuManager;
import com.nova.novablock.island.InviteManager;
import com.nova.novablock.island.IslandManager;
import com.nova.novablock.island.IslandWorldManager;
import com.nova.novablock.island.OneBlockRepairService;
import com.nova.novablock.island.PreviewHologramManager;
import com.nova.novablock.listener.BlockListener;
import com.nova.novablock.listener.PlayerListener;
import com.nova.novablock.lootroom.LootRoomManager;
import com.nova.novablock.minion.MinionManager;
import com.nova.novablock.paxel.PaxelManager;
import com.nova.novablock.phase.PhaseManager;
import com.nova.novablock.progression.LoginStreakManager;
import com.nova.novablock.progression.PrestigeManager;
import com.nova.novablock.progression.ProgressionManager;
import com.nova.novablock.prophecy.ProphecyManager;
import com.nova.novablock.quest.QuestManager;
import com.nova.novablock.scoreboard.ActionBarNudger;
import com.nova.novablock.scoreboard.RankNameplateManager;
import com.nova.novablock.scoreboard.ScoreboardManager;
import com.nova.novablock.season.SeasonManager;
import com.nova.novablock.season.SeasonalPathManager;
import com.nova.novablock.spawn.SpawnManager;
import com.nova.novablock.spawn.PlayerSpawnManager;
import com.nova.novablock.social.FriendManager;
import com.nova.novablock.sprint.WeeklySprintManager;
import com.nova.novablock.command.SpawnCommand;
import com.nova.novablock.storage.DataStorage;
import com.nova.novablock.storage.YamlStorage;
import org.bukkit.plugin.java.JavaPlugin;

public final class NovaBlock extends JavaPlugin {

    private static NovaBlock instance;

    private ConfigManager configManager;
    private DataStorage storage;
    private IslandWorldManager worldManager;
    private IslandManager islandManager;
    private InviteManager inviteManager;
    private PhaseManager phaseManager;
    private ProphecyManager prophecyManager;
    private BossManager bossManager;
    private LootRoomManager lootRoomManager;
    private ProgressionManager progressionManager;
    private PrestigeManager prestigeManager;
    private LoginStreakManager loginStreakManager;
    private QuestManager questManager;
    private PaxelManager paxelManager;
    private HotbarMenuManager hotbarManager;
    private EconomyManager economyManager;
    private GuiManager guiManager;
    private EventManager eventManager;
    private SeasonManager seasonManager;
    private SeasonalPathManager seasonalPathManager;
    private ScoreboardManager scoreboardManager;
    private RankNameplateManager rankNameplateManager;
    private AntiAfkManager antiAfkManager;
    private IslandFlagsManager islandFlagsManager;
    private IslandStorageManager islandStorageManager;
    private MainMenuConfig menuConfig;
    private OneBlockRepairService oneBlockRepairService;
    private PreviewHologramManager previewHologramManager;
    private SpawnManager spawnManager;
    private PlayerSpawnManager playerSpawnManager;
    private FriendManager friendManager;
    private WeeklySprintManager sprintManager;
    private MinionManager minionManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.configManager = new ConfigManager(this);
        this.configManager.loadAll();

        this.storage = new YamlStorage(this);
        this.storage.init();

        this.worldManager = new IslandWorldManager(this);
        this.worldManager.init();

        this.economyManager = new EconomyManager(this);
        this.progressionManager = new ProgressionManager(this);
        this.phaseManager = new PhaseManager(this);
        this.phaseManager.loadPhases();
        this.phaseManager.loadNetherPhases();
        this.prestigeManager = new PrestigeManager(this);
        this.loginStreakManager = new LoginStreakManager(this);

        this.prophecyManager = new ProphecyManager(this);
        this.bossManager = new BossManager(this);
        this.bossManager.registerDefaultBosses();
        this.lootRoomManager = new LootRoomManager(this);
        this.lootRoomManager.registerDefaultRooms();

        this.islandManager = new IslandManager(this);
        this.islandManager.loadAll();
        this.inviteManager = new InviteManager(this);

        this.questManager = new QuestManager(this);
        this.questManager.loadDailyQuests();
        this.paxelManager = new PaxelManager(this);
        this.hotbarManager = new HotbarMenuManager(this);
        this.guiManager = new GuiManager(this);
        this.eventManager = new EventManager(this);
        this.seasonManager = new SeasonManager(this);
        this.seasonalPathManager = new SeasonalPathManager(this);
        this.seasonalPathManager.load();
        this.scoreboardManager = new ScoreboardManager(this);
        this.rankNameplateManager = new RankNameplateManager(this);
        this.antiAfkManager = new AntiAfkManager(this);
        this.islandFlagsManager = new IslandFlagsManager(this);
        this.islandStorageManager = new IslandStorageManager(this);
        this.menuConfig = new MainMenuConfig(this);
        this.oneBlockRepairService = new OneBlockRepairService(this);
        this.previewHologramManager = new PreviewHologramManager(this);
        this.spawnManager = new SpawnManager(this);
        this.playerSpawnManager = new PlayerSpawnManager(this);
        this.friendManager = new FriendManager(this);
        this.sprintManager = new WeeklySprintManager(this);
        this.minionManager = new MinionManager(this);

        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new ActionBarNudger(this), this);
        getServer().getPluginManager().registerEvents(guiManager, this);

        OneBlockCommand obCmd = new OneBlockCommand(this);
        getCommand("oneblock").setExecutor(obCmd);
        getCommand("oneblock").setTabCompleter(obCmd);
        AdminCommand adminCmd = new AdminCommand(this);
        getCommand("obadmin").setExecutor(adminCmd);
        getCommand("obadmin").setTabCompleter(adminCmd);
        getCommand("sb").setExecutor(new ScoreboardCommand(this));
        getCommand("novahelp").setExecutor(new HelpCommand(this));
        getCommand("spawn").setExecutor(new SpawnCommand(this));
        MinionCommand minionCmd = new MinionCommand(this);
        getCommand("obminions").setExecutor(minionCmd);
        getCommand("obminions").setTabCompleter(minionCmd);

        eventManager.startTimers();
        seasonManager.startSeasonTicker();
        sprintManager.startTicker();
        seasonalPathManager.ensureTags();
        scoreboardManager.startTicker();
        rankNameplateManager.startTicker();
        oneBlockRepairService.start();
        previewHologramManager.start();
        minionManager.start();

        // Crash-recovery: clean up orphan bosses and leftover loot-room worlds.
        bossManager.cleanupOrphans();
        lootRoomManager.cleanupOrphanWorlds();

        // Plug NovaBlock into the /help index so players can discover commands without leaving chat.
        HelpRegistrar.register(this);

        // Optional PlaceholderAPI hook — guarded so the plugin works without PAPI installed.
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new com.nova.novablock.compat.PlaceholderHook(this).register();
                getLogger().info("PlaceholderAPI expansion registered.");
            } catch (Throwable t) {
                getLogger().warning("Failed to register PlaceholderAPI expansion: " + t.getMessage());
            }
        }

        getLogger().info("NovaBlock enabled — " + phaseManager.phaseCount() + " phases, "
                + bossManager.bossCount() + " bosses, " + lootRoomManager.roomCount() + " loot rooms.");
    }

    @Override
    public void onDisable() {
        // Stop tickers first so they don't fire against objects we're about to tear down.
        if (antiAfkManager != null) antiAfkManager.shutdown();
        if (islandFlagsManager != null) islandFlagsManager.shutdown();
        if (islandStorageManager != null) islandStorageManager.shutdown();
        if (eventManager != null) eventManager.shutdown();
        if (seasonManager != null) seasonManager.shutdown();
        if (seasonalPathManager != null) seasonalPathManager.save();
        if (rankNameplateManager != null) rankNameplateManager.shutdown();
        if (scoreboardManager != null) scoreboardManager.shutdown();
        if (oneBlockRepairService != null) oneBlockRepairService.shutdown();
        if (lootRoomManager != null) lootRoomManager.shutdown();
        if (bossManager != null) bossManager.shutdown();
        if (paxelManager != null) paxelManager.shutdown();
        if (previewHologramManager != null) previewHologramManager.shutdown();
        if (minionManager != null) minionManager.shutdown();

        // Then persist.
        if (islandManager != null) islandManager.saveAll();
        if (progressionManager != null) progressionManager.saveAll();
        if (playerSpawnManager != null) playerSpawnManager.saveNow();
        if (friendManager != null) friendManager.saveNow();
        if (sprintManager != null) sprintManager.shutdown();
        if (storage != null) storage.shutdown();
        instance = null;
    }

    public static NovaBlock get() { return instance; }

    public ConfigManager configs() { return configManager; }
    public DataStorage storage() { return storage; }
    public IslandWorldManager worlds() { return worldManager; }
    public IslandManager islands() { return islandManager; }
    public InviteManager invites() { return inviteManager; }
    public PhaseManager phases() { return phaseManager; }
    public ProphecyManager prophecies() { return prophecyManager; }
    public BossManager bosses() { return bossManager; }
    public LootRoomManager lootRooms() { return lootRoomManager; }
    public ProgressionManager progression() { return progressionManager; }
    public PrestigeManager prestige() { return prestigeManager; }
    public LoginStreakManager loginStreaks() { return loginStreakManager; }
    public QuestManager quests() { return questManager; }
    public PaxelManager paxels() { return paxelManager; }
    public HotbarMenuManager hotbar() { return hotbarManager; }
    public EconomyManager economy() { return economyManager; }
    public GuiManager guis() { return guiManager; }
    public EventManager events() { return eventManager; }
    public SeasonManager seasons() { return seasonManager; }
    public SeasonalPathManager seasonalPaths() { return seasonalPathManager; }
    public ScoreboardManager scoreboards() { return scoreboardManager; }
    public RankNameplateManager rankNameplates() { return rankNameplateManager; }
    public AntiAfkManager antiAfk() { return antiAfkManager; }
    public IslandFlagsManager islandFlags() { return islandFlagsManager; }
    public IslandStorageManager islandStorage() { return islandStorageManager; }
    public MainMenuConfig menuConfig() { return menuConfig; }
    public OneBlockRepairService repairs() { return oneBlockRepairService; }
    public SpawnManager spawn() { return spawnManager; }
    public PlayerSpawnManager playerSpawns() { return playerSpawnManager; }
    public FriendManager friends() { return friendManager; }
    public WeeklySprintManager sprint() { return sprintManager; }
    public MinionManager minions() { return minionManager; }
}
