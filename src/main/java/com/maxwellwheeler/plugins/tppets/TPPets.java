package com.maxwellwheeler.plugins.tppets;

import com.maxwellwheeler.plugins.tppets.commands.CommandTPP;
import com.maxwellwheeler.plugins.tppets.helpers.ConfigUpdater;
import com.maxwellwheeler.plugins.tppets.helpers.DBUpdater;
import com.maxwellwheeler.plugins.tppets.helpers.LogWrapper;
import com.maxwellwheeler.plugins.tppets.helpers.UUIDUtils;
import com.maxwellwheeler.plugins.tppets.listeners.TPPetsChunkListener;
import com.maxwellwheeler.plugins.tppets.listeners.TPPetsEntityListener;
import com.maxwellwheeler.plugins.tppets.listeners.TPPetsInventoryListener;
import com.maxwellwheeler.plugins.tppets.listeners.TPPetsPlayerListener;
import com.maxwellwheeler.plugins.tppets.regions.LostAndFoundRegion;
import com.maxwellwheeler.plugins.tppets.regions.ProtectedRegion;
import com.maxwellwheeler.plugins.tppets.storage.DBWrapper;
import com.maxwellwheeler.plugins.tppets.storage.PetLimitChecker;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

/**
 * The plugin's main class.
 * @author GatheringExp
 *
 */
public class TPPets extends JavaPlugin {
    private Hashtable<String, ProtectedRegion> protectedRegions = new Hashtable<>();
    private Hashtable<String, LostAndFoundRegion> lostRegions = new Hashtable<>();
    private Hashtable<String, List<String>> commandAliases = new Hashtable<>();
    private Hashtable<String, List<Material>> customTools = new Hashtable<>();
    private Hashtable<String, List<String>> allowedPlayers = new Hashtable<>();

    // Database
    private DBWrapper database;
    private DBUpdater databaseUpdater;

    // Config
    private ConfigUpdater configUpdater;

    private LogWrapper logWrapper;

    private boolean preventPlayerDamage = false;
    private boolean preventEnvironmentalDamage = false;
    private boolean preventMobDamage = false;
    private boolean preventOwnerDamage = false;
    
    // Vault stuff
    private Permission perms;

    private boolean vaultEnabled;
    
    private boolean allowTpBetweenWorlds;
    private boolean allowUntamingPets;
    
    private PetLimitChecker petIndex;
    private int storageLimit;

    
    
    /*
     * VARIABLE INITIALIZERS
     *
     */

    private void initializeLogWrapper() {
        logWrapper = new LogWrapper(this, getConfig().getBoolean("logging.updated_pets", true), getConfig().getBoolean("logging.successful_actions", true), getConfig().getBoolean("logging.unsuccessful_actions", true), getConfig().getBoolean("logging.prevented_damage", true), getConfig().getBoolean("logging.errors", true));
    }

    private void initializeStorageLimit() {
        storageLimit = getConfig().getInt("storage_limit", 0);
    }

    /**
     * Initializes the customTools Hashtable, which is later used to allow servers to configure which tools can be applied to which tasks.
     */
    private void initializeCustomTools() {
        ConfigurationSection toolsSection = getConfig().getConfigurationSection("tools");
        for (String key : toolsSection.getKeys(false)) {
            List<Material> rMat = new ArrayList<>();
            for (String materialName : toolsSection.getStringList(key)) {
                rMat.add(Material.getMaterial(materialName));
            }
            customTools.put(key, rMat);
        }
    }

    /**
     * Initializes the {@link PetLimitChecker} based on total_pet_limit, dog_limit, cat_limit, and bird_limit integers in the config.
     */
    private void initializePetIndex() {
        petIndex = new PetLimitChecker(this, getConfig().getInt("total_pet_limit"), getConfig().getInt("dog_limit"), getConfig().getInt("cat_limit"), getConfig().getInt("bird_limit"), getConfig().getInt("horse_limit"), getConfig().getInt("mule_limit"), getConfig().getInt("llama_limit"), getConfig().getInt("donkey_limit"));
    }
    
    /**
     * Initializes the {@link DBWrapper} based on config options mysql.enable, mysql.host, mysql.port, mysql.database, mysql.username, and mysql.password.
     * If DBWrapper is false, it will use the SQLite connection rather than a MySQL one.
     */
    private void initializeDBC() {
        if (!getConfig().getBoolean("mysql.enable")) {
            // Use SQLite connection
            database = new DBWrapper(getDataFolder().getPath(), "tppets", this);
        } else {
            // Use MySQL connection
            database = new DBWrapper(getConfig().getString("mysql.host"), getConfig().getInt("mysql.port"), getConfig().getString("mysql.database"), getConfig().getString("mysql.username"), getConfig().getString("mysql.password"), this);
            if (database.getRealDatabase().getConnection() == null) {
                // Unless MySQL connection fails
                database = new DBWrapper(getDataFolder().getPath(), "tppets", this);
            }
        }
    }

    /**
     * Updates the database if it can, otherwise turns database = null, negatively impacting virtually every aspect of this plugin
     */
    private void updateDBC() {
        databaseUpdater = new DBUpdater(this);
        databaseUpdater.update(this.getDatabase());
        if (!databaseUpdater.isUpToDate()) {
            getLogWrapper().logErrors("Database is unable to be updated");
            database = null;
        }
    }

    /**
     * Creates the tables if they don't exist, using the DBWrapper
     */
    private void createTables() {
        if (database != null && !database.initializeTables()) {
            database = null;
        }
    }

    /**
     * Updates the spigot/bukkit config
     */
    private void updateConfig() {
        configUpdater = new ConfigUpdater(this);
        configUpdater.update();
    }
    
    /**
     * Loads configuration option tp_pets_between_worlds into memory.
     */
    private void initializeAllowTP() {
        allowTpBetweenWorlds = getConfig().getBoolean("tp_pets_between_worlds");
    }
    
    /**
     * Loads configuration option allow_untaming_pets into memory.
     */
    private void initializeAllowUntamingPets() {
        allowUntamingPets = getConfig().getBoolean("allow_untaming_pets");
    }
    
    /**
     * Initializes local variables tracking what damage to prevent.
     */
    private void initializeDamageConfigs() {
        List<String> configList = getConfig().getStringList("protect_pets_from");
        if (configList.contains("PlayerDamage")) {
            preventPlayerDamage = true;
            getLogWrapper().logSuccessfulAction("Preventing player damage...");
        }
        if (configList.contains("EnvironmentalDamage")) {
            preventEnvironmentalDamage = true;
            getLogWrapper().logSuccessfulAction("Preventing environmental damage...");
        }
        if (configList.contains("MobDamage")) {
            preventMobDamage = true;
            getLogWrapper().logSuccessfulAction("Preventing mob damage...");
        }
        if (configList.contains("OwnerDamage")) {
            preventOwnerDamage = true;
            getLogWrapper().logSuccessfulAction("Preventing owner damage...");
        }
    }
    
    /**
     * Initializes local variables of command aliases.
     */
    private void initializeCommandAliases() {
        Set<String> configKeyList = getConfig().getConfigurationSection("command_aliases").getKeys(false);
        for (String key : configKeyList) {
            List<String> tempAliasList = getConfig().getStringList("command_aliases." + key);
            List<String> lowercaseAliasList = new ArrayList<>();
            for (String alias : tempAliasList) {
                lowercaseAliasList.add(alias.toLowerCase());
            }
            lowercaseAliasList.add(key.toLowerCase());
            commandAliases.put(key.toLowerCase(), lowercaseAliasList);
        }
    }
    
    /**
     * Initializes protected regions in a list
     */
    private void initializeProtectedRegions() {
        if (database != null) {
            protectedRegions = database.getProtectedRegions();
        }
    }
    
    /**
     * Initializes protected regions in a list
     */
    private void initializeLostRegions() {
        if (database != null) {
            lostRegions = database.getLostRegions();
        }
    }
    
    /**
     * Checks if vault (soft dependency) is enabled
     */
    private void initializeVault() {
        if (vaultEnabled = getServer().getPluginManager().isPluginEnabled("Vault")) {
            initializePermissions();
            getLogWrapper().logSuccessfulAction("Vault detected. Permission tppets.tpanywhere will work with online and offline players.");
        } else {
            getLogWrapper().logSuccessfulAction("Vault not detected on this server. Permission tppets.tpanywhere will only work with online players.");
        }
    }
    
    /**
     * Initializes vault permissions object
     * @return if the operation was successful
     */
    private boolean initializePermissions() {
        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        perms = rsp.getProvider();
        return perms != null;
    }

    /**
     * Initializes allowed players {@link Hashtable}
     */
    private void initializeAllowedPlayers() {
        if (database != null) {
            allowedPlayers = database.getAllAllowedPlayers();
        }
    }
    
    @Override
    public void onEnable() {
        // Config setup and pulling
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();
        updateConfig();
        initializeLogWrapper();
        initializeStorageLimit();
        initializeCommandAliases();
        initializeAllowTP();
        initializeAllowUntamingPets();
        initializeCustomTools();
        
        // Database setup
        getLogWrapper().logSuccessfulAction("Setting up database.");
        initializeDBC();
        updateDBC();
        createTables();
        initializeAllowedPlayers();

        // Database pulling
        getLogWrapper().logSuccessfulAction("Getting data from database.");
        initializeLostRegions();
        initializeProtectedRegions();
        initializePetIndex();
        
        // Register events + commands
        getLogWrapper().logSuccessfulAction("Registering commands and events.");
        getServer().getPluginManager().registerEvents(new TPPetsChunkListener(this), this);
        getServer().getPluginManager().registerEvents(new TPPetsEntityListener(this), this);
        getServer().getPluginManager().registerEvents(new TPPetsInventoryListener(this), this);
        getServer().getPluginManager().registerEvents(new TPPetsPlayerListener(this, customTools), this);
        initializeCommandAliases();
        this.getCommand("tpp").setExecutor(new CommandTPP(commandAliases, this));

        initializeDamageConfigs();
        initializeLostRegions();
        initializeVault();
    }
    
    /*
     * PROTECTED REGIONS
     * 
     */
    
    /**
     * Adds protected region to list in memory of protected regions actively being protected
     * @param pr The {@link ProtectedRegion} to be added.
     */
    public void addProtectedRegion (ProtectedRegion pr) {
        protectedRegions.put(pr.getZoneName(), pr);
    }
    
    /**
     * 
     * @param lc The location to be checked
     * @return {@link ProtectedRegion} that the location is in, null otherwise
     */
    public ProtectedRegion getProtectedRegionWithin(Location lc) {
        for (String key : protectedRegions.keySet()) { 
            if (protectedRegions.get(key).isInZone(lc)) {
                return protectedRegions.get(key);
            }
        }
        return null;
    }
    
    /**
     * 
     * @param lc The location to be checked
     * @return if location is in a {@link ProtectedRegion}
     */
    public boolean isInProtectedRegion(Location lc) {
        return getProtectedRegionWithin(lc) != null;
    }

    /**
     * Returns a protected region with a given name
     * @param name Name of {@link ProtectedRegion}
     * @return the referenced {@link ProtectedRegion}, null otherwise.
     */
    public ProtectedRegion getProtectedRegion(String name) {
        return protectedRegions.get(name);
    }

    /**
     * Removes a protected region from memory, but not from disk.
     * @param name Name of the protected region
     */
    public void removeProtectedRegion(String name) {
        protectedRegions.remove(name);
    }
    
    /**
     * Updates the lfReference property of {@link ProtectedRegion}s that have {@link LostAndFoundRegion} of name lfRegionName
     * @param lfRegionName {@link LostAndFoundRegion}'s name that should be refreshed within all {@link ProtectedRegion}s.
     */
    public void updateLFReference(String lfRegionName) {
        for (String key : protectedRegions.keySet()) {
            ProtectedRegion pr = protectedRegions.get(key);
            if (pr != null && pr.getLfName().equals(lfRegionName)) {
                pr.updateLFReference();
            }
        }
    }
    
    /**
     * Removes all lfRefernece properties of {@link ProtectedRegion}s that have name lfRegionName
     * @param lfRegionName The name of the {@link LostAndFoundRegion} that is being removed
     */
    public void removeLFReference(String lfRegionName) {
        for (String key : protectedRegions.keySet()) {
            ProtectedRegion pr = protectedRegions.get(key);
            if (pr != null && pr.getLfName().equals(lfRegionName)) {
                pr.setLfReference(null);
            }
        }
    }
    
    /*
     * LOST REGIONS
     * 
     */
    
    /**
     * Tests if a location is in a lost region
     * @param lc Location to be tested.
     * @return Boolean representing if the location is in a lost region.
     */
    public boolean isInLostRegion(Location lc) {
        for (String lfKey : lostRegions.keySet()) {
            if (lostRegions.get(lfKey).isInZone(lc)) {
                return true;
            }
        }
        return false;
    }

    public boolean isAllowedToPet(String petUUID, String playerUUID) {
        String trimmedPetUUID = UUIDUtils.trimUUID(petUUID);
        String trimmedPlayerUUID = UUIDUtils.trimUUID(playerUUID);
        return this.getAllowedPlayers().containsKey(trimmedPetUUID) && this.getAllowedPlayers().get(trimmedPetUUID).contains(trimmedPlayerUUID);
    }
    
    /**
     * Adds {@link LostAndFoundRegion} to active {@link LostAndFoundRegion} list
     * @param lfr {@link LostAndFoundRegion} to add.
     */
    public void addLostRegion(LostAndFoundRegion lfr) {
        lostRegions.put(lfr.getZoneName(), lfr);
    }
    
    /**
     * Removes {@link LostAndFoundRegion} from active {@link LostAndFoundRegion} list
     * @param lfr {@link LostAndFoundRegion} to remove.
     */
    public void removeLostRegion(LostAndFoundRegion lfr) {
        lostRegions.remove(lfr.getZoneName());
    }

    public boolean canTpThere(Player pl) {
        ProtectedRegion tempPr = getProtectedRegionWithin(pl.getLocation());
        boolean ret = pl.hasPermission("tppets.tpanywhere") || tempPr == null;
        if (!ret) {
            pl.sendMessage(tempPr.getEnterMessage());
        }
        return ret;
    }

    /*
     * GETTERS/SETTERS
     * 
     */
    
    public DBWrapper getDatabase() {
        return database;
    }
    
    public Hashtable<String, ProtectedRegion> getProtectedRegions() {
        return protectedRegions;
    }
    
    public Hashtable<String, LostAndFoundRegion> getLostRegions() {
        return lostRegions;
    }
    
    public boolean getPreventPlayerDamage() {
        return preventPlayerDamage;
    }
    
    public boolean getPreventEnvironmentalDamage() {
        return preventEnvironmentalDamage;
    }
    
    public boolean getPreventMobDamage() {
        return preventMobDamage;
    }

    public boolean getPreventOwnerDamage() {
        return preventOwnerDamage;
    }
    
    public LostAndFoundRegion getLostRegion(String name) {
        return lostRegions.get(name);
    }
    
    public Permission getPerms() {
        return perms;
    }
    
    public boolean getVaultEnabled() {
        return vaultEnabled;
    }
    
    public boolean getAllowTpBetweenWorlds() {
        return allowTpBetweenWorlds;
    }
    
    public boolean getAllowUntamingPets() {
        return allowUntamingPets;
    }
    
    public PetLimitChecker getPetIndex() {
        return petIndex;
    }

    public DBUpdater getDatabaseUpdater() {
        return databaseUpdater;
    }

    public ConfigUpdater getConfigUpdater() {
        return configUpdater;
    }

    public Hashtable<String, List<String>> getAllowedPlayers() {
        return allowedPlayers;
    }

    public int getStorageLimit() {
        return storageLimit;
    }

    public LogWrapper getLogWrapper() {
        return logWrapper;
    }
}
