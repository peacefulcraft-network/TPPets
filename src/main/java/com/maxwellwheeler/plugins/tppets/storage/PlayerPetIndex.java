package com.maxwellwheeler.plugins.tppets.storage;

import java.util.Hashtable;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Tameable;

import com.maxwellwheeler.plugins.tppets.TPPets;
import com.maxwellwheeler.plugins.tppets.helpers.PermissionChecker;
import com.maxwellwheeler.plugins.tppets.helpers.UUIDUtils;

/**
 * An index of how many pets each player owns. Used for the limiting of tamed pets.
 * @author GatheringExp
 *
 */
public class PlayerPetIndex {
    /**
     * Enum representing the results of when a pet is attempting to be tamed.
     * @author GatheringExp
     *
     */
    public enum RuleRestriction {
        ALLOWED, TOTAL, DOG, CAT, PARROT, UNKNOWN
    }
    
    /**
     * Links the enum {@link RuleRestriction} with the enum {@link PetType.Pets}
     * @param pt The {@link PetType.Pets} enum value
     * @return The rule restriction represented by pt
     */
    private RuleRestriction enumLink(PetType.Pets pt) {
        switch (pt) {
        case DOG:
            return RuleRestriction.DOG;
        case CAT:
            return RuleRestriction.CAT;
        case PARROT:
            return RuleRestriction.PARROT;
        case UNKNOWN:
        default:
            return RuleRestriction.UNKNOWN;
        }
    }
    
    private Hashtable<String, AllPetsList> playerIndex = new Hashtable<String, AllPetsList>();
    private TPPets thisPlugin;
    private int totalLimit;
    private int dogLimit;
    private int catLimit;
    private int birdLimit;
    
    /**
     * General constructor, referencing the plugin instance, and pet limits from the config file.
     * @param thisPlugin The TPPets instance.
     * @param totalLimit The limit of all pets owned.
     * @param dogLimit The limit of all dogs owned.
     * @param catLimit The limit of all cats owned.
     * @param birdLimit The limit of all birds owned.
     */
    public PlayerPetIndex(TPPets thisPlugin, int totalLimit, int dogLimit, int catLimit, int birdLimit) {
        this.thisPlugin = thisPlugin;
        this.totalLimit = totalLimit;
        this.dogLimit = dogLimit;
        this.catLimit = catLimit;
        this.birdLimit = birdLimit;
        initializePetIndex();
    }
    
    /**
     * Generates the pet index from data stored in the database and in loaded chunks.
     */
    private void initializePetIndex() {
        for (World wld : Bukkit.getServer().getWorlds()) {
            for (Entity ent : wld.getEntitiesByClasses(org.bukkit.entity.Sittable.class)) {
                Tameable tameableTemp = (Tameable) ent;
                if (tameableTemp.isTamed()) {
                    String trimmedOwnerUUID = UUIDUtils.trimUUID(tameableTemp.getOwner().getUniqueId());
                    String trimmedEntityUUID = UUIDUtils.trimUUID(ent.getUniqueId());
                    if (!playerIndex.containsKey(trimmedOwnerUUID)) {
                        playerIndex.put(trimmedOwnerUUID, new AllPetsList());
                    }
                    playerIndex.get(trimmedOwnerUUID).addPet(trimmedEntityUUID, PetType.getEnumByEntity(ent));
                }
            }
            if (thisPlugin.getDatabase() != null) {
                List<PetStorage> psList = thisPlugin.getDatabase().getPetsFromWorld(wld.getName());
                if (psList != null) {
                    for (PetStorage ps : thisPlugin.getDatabase().getPetsFromWorld(wld.getName())) {
                        if (!playerIndex.containsKey(ps.ownerId)) {
                            playerIndex.put(ps.ownerId, new AllPetsList());
                        } else {
                            playerIndex.get(ps.ownerId).addPet(ps.petId, ps.petType);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Gets the limit specific to a pet of type pt.
     * @param pt The pet type limit to get
     * @return An integer represneting the specific taming limit of pet type pt
     */
    public int getSpecificLimit(PetType.Pets pt) {
        switch (pt) {
            case DOG:
                return dogLimit;
            case CAT:
                return catLimit;
            case PARROT:
                return birdLimit;
            default:
                return -1;
        }
    }
    
    /**
     * Adds a new pet tamed to a player's count with
     * @param playerUUID A trimmed version of a player's UUID string.
     * @param entityUUID A trimmed version of the entity's UUID string.
     * @param pt The type of the pet.
     */
    public void newPetTamed(String playerUUID, String entityUUID, PetType.Pets pt) {
        String trimmedPlayerUUID = UUIDUtils.trimUUID(playerUUID);
        String trimmedEntityUUID = UUIDUtils.trimUUID(entityUUID);
        if (!playerIndex.containsKey(trimmedPlayerUUID)) {
            playerIndex.put(trimmedPlayerUUID, new AllPetsList());
        }
        playerIndex.get(trimmedPlayerUUID).addPet(trimmedEntityUUID, pt);
    }
    
    /**
     * Removes a pet from the player's count with
     * @param playerUUID A trimmed version of a player's UUID string.
     * @param entityUUID A trimmed version of an entity's UUID string.
     * @param pt The type of the pet.
     */
    public void removePetTamed(String playerUUID, String entityUUID, PetType.Pets pt) {
        String trimmedPlayerUUID = UUIDUtils.trimUUID(playerUUID);
        String trimmedEntityUUID = UUIDUtils.trimUUID(entityUUID);
        if (playerIndex.containsKey(trimmedPlayerUUID)) {
            playerIndex.get(trimmedPlayerUUID).removePet(trimmedEntityUUID, pt);
        }
    }
    
    /**
     * Helper function that determines if the integer within is under (not inclusive) the limit
     * @param limit The upper bound of the test, not inclusive.
     * @param within The integer being tested that should be under the limit.
     * @return True if within is below limit, false if otherwise
     */
    private boolean isWithinLimit(int limit, int within) {
        return limit < 0 || within < limit;
    }
    
    /**
     * Core functionality of this object. Determines if a player should be able to tame a given pet.
     * @param at The animal tamer that is attempting to tame a pet
     * @param loc The location of the tamer, used in Vault's permission api
     * @param pt The type of the the pet being tamed.
     * @return The ruling. It can be allowed, disallowed because of X, or unknown
     */
    public RuleRestriction allowTame(AnimalTamer at, Location loc, PetType.Pets pt) {
        if (PermissionChecker.offlineHasPerms(at, "tppets.bypasslimit", loc.getWorld(), thisPlugin) || PermissionChecker.onlineHasPerms(at, "tppets.bypasslimit")) {
            return RuleRestriction.ALLOWED;
        }
        String trimmedUUID = UUIDUtils.trimUUID(at.getUniqueId());
        if (playerIndex.containsKey(trimmedUUID)) {
            if (!isWithinLimit(totalLimit, playerIndex.get(trimmedUUID).getTotalLength())) {
                return RuleRestriction.TOTAL;
            } else if (!isWithinLimit(getSpecificLimit(pt), playerIndex.get(trimmedUUID).getPetsLength(pt))) {
                return enumLink(pt);
            }
        }
        return RuleRestriction.ALLOWED;
    }
}