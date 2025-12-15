package dev.baraus.bettersmithing;

import org.bstats.bukkit.Metrics;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class BetterSmithing extends JavaPlugin {

    private final List<String> toolTypes = Arrays.asList("SWORD", "SPEAR", "SHOVEL", "PICKAXE", "AXE", "HOE", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");

    private final Material[] leatherTools = Arrays.stream(Material.values()).filter(
            material -> material.name().startsWith("LEATHER_") && toolTypes.contains(material.name().replace("LEATHER_", ""))
    ).toArray(Material[]::new);

    private final Material[] copperTools = Arrays.stream(Material.values()).filter(
            material -> material.name().startsWith("COPPER_") && toolTypes.contains(material.name().replace("COPPER_", ""))
    ).toArray(Material[]::new);

    private final Material[] woodenTools = Arrays.stream(Material.values()).filter(
            material -> material.name().startsWith("WOODEN_") && toolTypes.contains(material.name().replace("WOODEN_", ""))
    ).toArray(Material[]::new);

    private final Material[] stoneTools = Arrays.stream(Material.values()).filter(
            material -> material.name().startsWith("STONE_") && toolTypes.contains(material.name().replace("STONE_", ""))
    ).toArray(Material[]::new);

    private final Material[] ironTools = Arrays.stream(Material.values()).filter(
            material -> material.name().startsWith("IRON_") && toolTypes.contains(material.name().replace("IRON_", ""))
    ).toArray(Material[]::new);

    private final Material[] goldenTools = Arrays.stream(Material.values()).filter(
            material -> material.name().startsWith("GOLDEN_") && toolTypes.contains(material.name().replace("GOLDEN_", ""))
    ).toArray(Material[]::new);

    private final Material[] diamondTools = Arrays.stream(Material.values()).filter(
            material -> material.name().startsWith("DIAMOND_") && toolTypes.contains(material.name().replace("DIAMOND_", ""))
    ).toArray(Material[]::new);

    public void addToolsRecipe(Material template, Material[] baseTools, Material[] resultTools,
                               Material additionMaterial, String basePrefix, String resultPrefix) {
        for (Material tool : baseTools) {
            Material equivalent = Arrays.stream(resultTools)
                    .filter(material -> tool.name().replace(basePrefix, "").equals(material.name().replace(resultPrefix, "")))
                    .findFirst()
                    .orElse(null);

            if (equivalent == null) {
                continue;
            }

            NamespacedKey key = new NamespacedKey(this, tool.name().toLowerCase() + "_to_" + equivalent.name().toLowerCase());
            RecipeChoice.MaterialChoice baseChoice = new RecipeChoice.MaterialChoice(tool);
            RecipeChoice.MaterialChoice additionChoice = new RecipeChoice.MaterialChoice(additionMaterial);

            RecipeChoice.MaterialChoice templateChoice = null;
            if (template != null && template != Material.AIR) {
                templateChoice = new RecipeChoice.MaterialChoice(template);
            }

            Recipe recipe = supportsTransformRecipes()
                    ? createTransformRecipe(key, new ItemStack(equivalent), templateChoice, baseChoice, additionChoice)
                    : createLegacyRecipe(key, new ItemStack(equivalent), baseChoice, additionChoice);

            if (recipe != null) {
                getServer().addRecipe(recipe);
            }
        }
    }

    @Override
    public void onEnable() {
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();

        String templateName = getConfig().getString("template", "AIR");
        Material templateMaterial = Material.matchMaterial(templateName);
        if (templateMaterial == null) {
            getLogger().warning("Template material '" + templateName + "' is not valid for this server version. Using AIR instead.");
            templateMaterial = Material.AIR;
        }

        Map<String, Material[]> tiersMap = new HashMap<>();
        tiersMap.put("leather", leatherTools);
        tiersMap.put("wooden", woodenTools);
        tiersMap.put("copper", copperTools);
        tiersMap.put("stone", stoneTools);
        tiersMap.put("iron", ironTools);
        tiersMap.put("golden", goldenTools);
        tiersMap.put("diamond", diamondTools);

        for (Map.Entry<String, Material[]> entry : tiersMap.entrySet()) {
            String tier = entry.getKey();
            Material[] tools = entry.getValue();

            if (tools.length == 0) {
                getLogger().warning("Tier '" + tier + "' has no compatible tools for this server version; skipping.");
                continue;
            }

            if (getConfig().contains("tiers." + tier)) {
                String upgradeTo = getConfig().getString("tiers." + tier + ".upgrade_to");
                String upgradeItem = getConfig().getString("tiers." + tier + ".upgrade_item");

                if (upgradeTo != null && upgradeItem != null) {
                    Material[] upgradeToTools = tiersMap.get(upgradeTo);
                    Material additionMaterial = Material.matchMaterial(upgradeItem.toUpperCase());

                    if (upgradeToTools != null && upgradeToTools.length > 0 && additionMaterial != null) {
                        addToolsRecipe(templateMaterial, tools, upgradeToTools, additionMaterial, tier.toUpperCase() + "_", upgradeTo.toUpperCase() + "_");
                    } else {
                        if (upgradeToTools == null) {
                            getLogger().warning("Unknown upgrade target tier '" + upgradeTo + "' in config; skipping tier '" + tier + "'.");
                        } else if (upgradeToTools.length == 0) {
                            getLogger().warning("Upgrade target tier '" + upgradeTo + "' has no compatible tools for this server version; skipping tier '" + tier + "'.");
                        }
                        if (additionMaterial == null) {
                            getLogger().warning("Unknown upgrade item '" + upgradeItem + "' in config; skipping tier '" + tier + "'.");
                        }
                    }
                }
            }
        }

        int pluginId = 17593; // Replace with your plugin ID
        Metrics metrics = new Metrics(this, pluginId);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    private boolean supportsTransformRecipes() {
        return isClassPresent("org.bukkit.inventory.SmithingTransformRecipe");
    }

    private Recipe createTransformRecipe(NamespacedKey key, ItemStack result, RecipeChoice template,
                                         RecipeChoice base, RecipeChoice addition) {
        if (template == null) {
            getLogger().warning("Current server requires smithing templates. Please set a valid 'template' material in config.yml.");
            return null;
        }

        try {
            Class<?> clazz = Class.forName("org.bukkit.inventory.SmithingTransformRecipe");
            Constructor<?> constructor = clazz.getConstructor(NamespacedKey.class, ItemStack.class, RecipeChoice.class, RecipeChoice.class, RecipeChoice.class);
            Object recipe = constructor.newInstance(key, result, template, base, addition);
            return (Recipe) recipe;
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Failed to create smithing transform recipe " + key.getKey(), ex);
            return null;
        }
    }

    private Recipe createLegacyRecipe(NamespacedKey key, ItemStack result, RecipeChoice base, RecipeChoice addition) {
        try {
            Class<?> clazz = Class.forName("org.bukkit.inventory.SmithingRecipe");
            Constructor<?> constructor = clazz.getConstructor(NamespacedKey.class, ItemStack.class, RecipeChoice.class, RecipeChoice.class);
            Object recipe = constructor.newInstance(key, result, base, addition);
            return (Recipe) recipe;
        } catch (ClassNotFoundException ignored) {
            getLogger().warning("SmithingRecipe is not available on this server version.");
            return null;
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Failed to create smithing recipe " + key.getKey(), ex);
            return null;
        }
    }

    private boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}
