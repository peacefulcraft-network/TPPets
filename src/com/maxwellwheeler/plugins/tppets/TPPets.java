package com.maxwellwheeler.plugins.tppets;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.maxwellwheeler.plugins.tppets.commands.CommandCreateCats;
import com.maxwellwheeler.plugins.tppets.commands.CommandTPP;
import com.maxwellwheeler.plugins.tppets.commands.CommandTpForward;
import com.maxwellwheeler.plugins.tppets.listeners.TPPetsChunkListener;
import com.maxwellwheeler.plugins.tppets.listeners.TPPetsEntityListener;
import com.maxwellwheeler.plugins.tppets.listeners.TPPetsPlayerListener;
import com.maxwellwheeler.plugins.tppets.region.LostAndFoundRegion;
import com.maxwellwheeler.plugins.tppets.region.ProtectedRegion;
import com.maxwellwheeler.plugins.tppets.storage.PlayerPetIndex;
import com.maxwellwheeler.plugins.tppets.storage.SQLite;

import net.milkbowl.vault.permission.Permission;

public class TPPets extends JavaPlugin implements Listener {
    // Configuration-based data types
    private ArrayList<ProtectedRegion> restrictedRegions = new ArrayList<ProtectedRegion>();
    private Hashtable<String, LostAndFoundRegion> lostRegions = new Hashtable<String, LostAndFoundRegion>();
    private Hashtable<String, List<String>> commandAliases = new Hashtable<String, List<String>>();

    // Database
    private SQLite dbc;
    
    private boolean preventPlayerDamage;
    private boolean preventEnvironmentalDamage;
    private boolean preventMobDamage;
    
    // Vault stuff
    private Permission perms;
    private boolean vaultEnabled;
    
    private boolean allowTpBetweenWorlds;
    private boolean allowUntamingPets;
    
    private PlayerPetIndex petIndex;
    
    
    /*
     * VARIABLE INITIALIZERS
     * 
     */
    
    private void initializePetIndex() {
        petIndex = new PlayerPetIndex(this, getConfig().getInt("total_pet_limit"), getConfig().getInt("dog_limit"), getConfig().getInt("cat_limit"), getConfig().getInt("bird_limit"));
    }
    
    private void initializeDBC() {
        dbc = new SQLite(this, getDataFolder().getPath(), "tppets");
        dbc.createDatabase();
        dbc.createTables();
    }
    
    private void initializeAllowTP() {
        allowTpBetweenWorlds = getConfig().getBoolean("tp_pets_between_worlds");
    }
    
    private void initializeAllowUntamingPets() {
        allowUntamingPets = getConfig().getBoolean("allow_untaming_pets");
    }
    
    private void initializeDamageConfigs() {
        List<String> configList = getConfig().getStringList("protect_pets_from");
        if (configList.contains("PlayerDamage")) {
            preventPlayerDamage = true;
            getLogger().info("Preventing player damage...");
        }
        if (configList.contains("EnvironmentalDamage")) {
            preventEnvironmentalDamage = true;
            getLogger().info("Preventing environmental damage...");
        }
        if (configList.contains("MobDamage")) {
            preventMobDamage = true;
            getLogger().info("Preventing mob damage...");
        }
    }
    
    private void initializeCommandAliases() {
        Set<String> configKeyList = getConfig().getConfigurationSection("command_aliases").getKeys(false);
        for (String key : configKeyList) {
            List<String> tempAliasList = getConfig().getStringList("command_aliases." + key);
            tempAliasList.add(key);
            commandAliases.put(key, tempAliasList);
        }
    }
    
    private void initializeRestrictedRegions() {
        restrictedRegions = dbc.getProtectedRegions();
    }
    
    private void initializeLostRegions() {
        lostRegions = dbc.getLostRegions();
    }
    
    private void initializeVault() {
        if (vaultEnabled = getServer().getPluginManager().isPluginEnabled("Vault")) {
            initializePermissions();
            getLogger().info("Vault detected. Permission tppets.tpanywhere will work with online and offline players.");
        } else {
            getLogger().info("Vault not detected on this server. Permission tppets.tpanywhere will only work with online players.");
        }
    }
    
    private boolean initializePermissions() {
        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        perms = rsp.getProvider();
        return perms != null;
    }


    @Override
    public void onEnable() {
        // Config setup and pulling
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();
        initializeCommandAliases();
        initializeAllowTP();
        initializeAllowUntamingPets();
        
        // Database setup
        getLogger().info("Setting up database.");
        initializeDBC();
        
        // Database pulling
        getLogger().info("Getting data from database.");
        initializeLostRegions();
        initializeRestrictedRegions();
        initializePetIndex();
        
        // Register events + commands
        getLogger().info("Registering commands and events.");
        getServer().getPluginManager().registerEvents(new TPPetsChunkListener(this), this);
        getServer().getPluginManager().registerEvents(new TPPetsEntityListener(this), this);
        getServer().getPluginManager().registerEvents(new TPPetsPlayerListener(this), this);
        initializeCommandAliases();
        this.getCommand("tpp").setExecutor(new CommandTPP(commandAliases));
        this.getCommand("generate-tamed-cats").setExecutor(new CommandCreateCats());
        this.getCommand("tp-forward").setExecutor(new CommandTpForward());

        initializeDamageConfigs();
        initializeLostRegions();
        initializeVault();
    }
    
    /*
     * PROTECTED REGIONS
     * 
     */
    
    public void addProtectedRegion (ProtectedRegion pr) {
        restrictedRegions.add(pr);
    }
    
    public ProtectedRegion inProtectedRegion(Location lc) {
        for (ProtectedRegion pr : restrictedRegions) {
            if (pr.isInZone(lc)) {
                return pr;
            }
        }
        return null;
    }
    
    public ProtectedRegion inProtectedRegion(Player pl) {
        return inProtectedRegion(pl.getLocation());
    }
    
    public boolean isInProtectedRegion(Location lc) {
        for (ProtectedRegion pr : restrictedRegions) {
            if (pr.isInZone(lc)) {
                return true;
            }
        }
        return false;
    }
    
    public boolean isInProtectedRegion(Player pl) {
        return isInProtectedRegion(pl.getLocation());
    }
    
    public ProtectedRegion getProtectedRegion(String name) {
        for (ProtectedRegion pr : restrictedRegions) {
            if (pr.getZoneName().equals(name)) {
                return pr;
            }
        }
        return null;
    }

    public void removeProtectedRegion(String name) {
        for (int i = 0; i < restrictedRegions.size(); i++) {
            if (restrictedRegions.get(i).getZoneName().equals(name)) {
                restrictedRegions.remove(i);
                break;
            }
        }
    }
    
    public void updateLFReference(String lfRegionName) {
        for (ProtectedRegion pr : restrictedRegions) {
            if (pr.getLfName().equals(lfRegionName)) {
                pr.updateLFReference();
            }
        }
    }
    
    public void removeLFReference(String lfRegionName) {
        for (ProtectedRegion pr : restrictedRegions) {
            if (pr.getLfName().equals(lfRegionName)) {
                pr.setLfReference(null);
            }
        }
    }
    
    /*
     * LOST REGIONS
     * 
     */
    
    public boolean isInLostRegion(Location lc) {
        for (String lfKey : lostRegions.keySet()) {
            if (lostRegions.get(lfKey).isInZone(lc)) {
                return true;
            }
        }
        return false;
    }
    
    public void addLostRegion(LostAndFoundRegion lfr) {
        lostRegions.put(lfr.getZoneName(), lfr);
    }
    
    public void removeLostRegion(LostAndFoundRegion lfr) {
        lostRegions.remove(lfr.getZoneName());
    }
    
    /*
     * GETTERS/SETTERS
     * 
     */
    
    public SQLite getSQLite() {
        return dbc;
    }
    
    public ArrayList<ProtectedRegion> getProtectedRegions() {
        return restrictedRegions;
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
    
    public LostAndFoundRegion getLostRegion(String name) {
        return lostRegions.get(name);
    }
    
    public Permission getPerms() {
        return perms;
    }
    
    public boolean getVaultEnabled() {
        return vaultEnabled;
    }
    
    public boolean getAllowTp() {
        return allowTpBetweenWorlds;
    }
    
    public boolean getAllowUntamingPets() {
        return allowUntamingPets;
    }
    
    public PlayerPetIndex getPetIndex() {
        return petIndex;
    }
}
