package com.pallux.practicebot.managers;

import com.pallux.practicebot.PracticeBot;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

import java.util.*;

/**
 * Manages kits for practice bots
 */
public class KitManager {

    private final PracticeBot plugin;
    private final Map<String, Kit> kits;

    public KitManager(PracticeBot plugin) {
        this.plugin = plugin;
        this.kits = new HashMap<>();
        loadKits();
    }

    /**
     * Load all kits from kits.yml
     */
    public void loadKits() {
        kits.clear();
        FileConfiguration config = plugin.getConfigManager().getKitsConfig();
        ConfigurationSection kitsSection = config.getConfigurationSection("kits");

        if (kitsSection == null) {
            plugin.getLogger().warning("No kits found in kits.yml!");
            return;
        }

        for (String kitName : kitsSection.getKeys(false)) {
            try {
                Kit kit = loadKit(kitName);
                if (kit != null) {
                    kits.put(kitName, kit);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load kit: " + kitName);
                e.printStackTrace();
            }
        }

        plugin.getLogger().info("Loaded " + kits.size() + " kit(s)");
    }

    /**
     * Load a single kit
     */
    private Kit loadKit(String kitName) {
        FileConfiguration config = plugin.getConfigManager().getKitsConfig();
        String path = "kits." + kitName + ".";

        Kit kit = new Kit(kitName);

        // Load armor
        ConfigurationSection armorSection = config.getConfigurationSection(path + "armor");
        if (armorSection != null) {
            kit.setHelmet(loadItem(armorSection.getConfigurationSection("helmet")));
            kit.setChestplate(loadItem(armorSection.getConfigurationSection("chestplate")));
            kit.setLeggings(loadItem(armorSection.getConfigurationSection("leggings")));
            kit.setBoots(loadItem(armorSection.getConfigurationSection("boots")));
        }

        // Load inventory items
        ConfigurationSection inventorySection = config.getConfigurationSection(path + "inventory");
        if (inventorySection != null) {
            for (String slotStr : inventorySection.getKeys(false)) {
                int slot = Integer.parseInt(slotStr);
                ItemStack item = loadItem(inventorySection.getConfigurationSection(slotStr));
                if (item != null) {
                    kit.addInventoryItem(slot, item);
                }
            }
        }

        // Load offhand
        ConfigurationSection offhandSection = config.getConfigurationSection(path + "offhand");
        if (offhandSection != null) {
            kit.setOffhand(loadItem(offhandSection));
        }

        return kit;
    }

    /**
     * Load an item from configuration section
     */
    private ItemStack loadItem(ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        String materialName = section.getString("material");
        if (materialName == null) {
            return null;
        }

        Material material;
        try {
            material = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material: " + materialName);
            return null;
        }

        int amount = section.getInt("amount", 1);
        ItemStack item = new ItemStack(material, amount);

        // Apply enchantments
        List<String> enchantments = section.getStringList("enchantments");
        if (!enchantments.isEmpty()) {
            for (String enchantStr : enchantments) {
                String[] parts = enchantStr.split(":");
                if (parts.length == 2) {
                    try {
                        Enchantment enchant = Enchantment.getByName(parts[0]);
                        int level = Integer.parseInt(parts[1]);
                        if (enchant != null) {
                            item.addUnsafeEnchantment(enchant, level);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Invalid enchantment: " + enchantStr);
                    }
                }
            }
        }

        // Handle potion types
        String potionType = section.getString("potion-type");
        if (potionType != null && item.getItemMeta() instanceof PotionMeta) {
            PotionMeta meta = (PotionMeta) item.getItemMeta();
            try {
                // Parse potion type (e.g., "INSTANT_HEALTH_II")
                boolean extended = potionType.contains("LONG");
                boolean upgraded = potionType.contains("II") || potionType.contains("STRONG");

                String baseName = potionType.replace("_LONG", "")
                        .replace("_II", "")
                        .replace("_STRONG", "")
                        .replace("INSTANT_HEALTH", "INSTANT_HEAL");

                PotionType type = PotionType.valueOf(baseName);
                PotionData data = new PotionData(type, extended, upgraded);
                meta.setBasePotionData(data);
                item.setItemMeta(meta);
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid potion type: " + potionType);
            }
        }

        return item;
    }

    /**
     * Get a kit by name
     */
    public Kit getKit(String name) {
        return kits.get(name);
    }

    /**
     * Check if a kit exists
     */
    public boolean hasKit(String name) {
        return kits.containsKey(name);
    }

    /**
     * Get all kit names
     */
    public Set<String> getKitNames() {
        return kits.keySet();
    }

    /**
     * Get all kits
     */
    public Collection<Kit> getKits() {
        return kits.values();
    }

    /**
     * Apply a kit to a player
     */
    public void applyKit(Player player, String kitName) {
        Kit kit = getKit(kitName);
        if (kit == null) {
            return;
        }

        kit.apply(player);
    }

    /**
     * Reload all kits
     */
    public void reload() {
        loadKits();
    }

    /**
     * Inner class representing a kit
     */
    public static class Kit {
        private final String name;
        private ItemStack helmet;
        private ItemStack chestplate;
        private ItemStack leggings;
        private ItemStack boots;
        private ItemStack offhand;
        private final Map<Integer, ItemStack> inventoryItems;

        public Kit(String name) {
            this.name = name;
            this.inventoryItems = new HashMap<>();
        }

        public String getName() {
            return name;
        }

        public void setHelmet(ItemStack helmet) {
            this.helmet = helmet;
        }

        public void setChestplate(ItemStack chestplate) {
            this.chestplate = chestplate;
        }

        public void setLeggings(ItemStack leggings) {
            this.leggings = leggings;
        }

        public void setBoots(ItemStack boots) {
            this.boots = boots;
        }

        public void setOffhand(ItemStack offhand) {
            this.offhand = offhand;
        }

        public void addInventoryItem(int slot, ItemStack item) {
            inventoryItems.put(slot, item);
        }

        public ItemStack getHelmet() {
            return helmet;
        }

        public ItemStack getChestplate() {
            return chestplate;
        }

        public ItemStack getLeggings() {
            return leggings;
        }

        public ItemStack getBoots() {
            return boots;
        }

        public ItemStack getOffhand() {
            return offhand;
        }

        public Map<Integer, ItemStack> getInventoryItems() {
            return inventoryItems;
        }

        /**
         * Apply this kit to a player
         */
        public void apply(Player player) {
            // Clear inventory
            player.getInventory().clear();

            // Set armor
            if (helmet != null) player.getInventory().setHelmet(helmet.clone());
            if (chestplate != null) player.getInventory().setChestplate(chestplate.clone());
            if (leggings != null) player.getInventory().setLeggings(leggings.clone());
            if (boots != null) player.getInventory().setBoots(boots.clone());

            // Set offhand
            if (offhand != null) player.getInventory().setItemInOffHand(offhand.clone());

            // Set inventory items
            for (Map.Entry<Integer, ItemStack> entry : inventoryItems.entrySet()) {
                player.getInventory().setItem(entry.getKey(), entry.getValue().clone());
            }

            player.updateInventory();
        }
    }
}