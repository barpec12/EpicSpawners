package com.songoda.epicspawners.gui;

import com.songoda.core.compatibility.CompatibleMaterial;
import com.songoda.core.gui.Gui;
import com.songoda.core.hooks.EconomyManager;
import com.songoda.core.utils.NumberUtils;
import com.songoda.core.utils.TextUtils;
import com.songoda.epicspawners.EpicSpawners;
import com.songoda.epicspawners.settings.Settings;
import com.songoda.epicspawners.spawners.condition.SpawnCondition;
import com.songoda.epicspawners.spawners.spawner.PlacedSpawner;
import com.songoda.epicspawners.spawners.spawner.SpawnerData;
import com.songoda.epicspawners.spawners.spawner.SpawnerStack;
import com.songoda.epicspawners.spawners.spawner.SpawnerTier;
import com.songoda.epicspawners.utils.CostType;
import com.songoda.epicspawners.utils.GuiUtils;
import com.songoda.epicspawners.utils.HeadUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpawnerOverviewGui extends Gui {

    private static final Pattern REGEX = Pattern.compile("(.{1,28}(?:\\s|$))|(.{0,28})", Pattern.DOTALL);

    private final PlacedSpawner spawner;
    private final SpawnerStack stack;
    private final SpawnerData data;
    private final SpawnerTier tier;
    private final SpawnerTier nextTier;
    private final boolean onlyOneTier;
    private final Player player;
    private final EpicSpawners plugin;

    private int infoPage = 1;

    private int task;

    public SpawnerOverviewGui(EpicSpawners plugin, SpawnerStack stack, Player player) {
        super(3);
        this.stack = stack;
        spawner = stack.getSpawner();
        tier = stack.getCurrentTier();
        data = tier.getSpawnerData();
        nextTier = data.getNextTier(tier);
        onlyOneTier = tier.getSpawnerData().getTiers().size() == 1;
        this.player = player;
        this.plugin = plugin;

        setTitle(tier.getCompiledDisplayName(false, stack.getStackSize()));
        runTask();

        setOnClose(event -> Bukkit.getScheduler().cancelTask(task));

        paint();
    }

    public void paint() {
        reset();

        ItemStack glass1 = GuiUtils.getBorderItem(Settings.GLASS_TYPE_1.getMaterial());
        ItemStack glass2 = GuiUtils.getBorderItem(Settings.GLASS_TYPE_2.getMaterial());
        ItemStack glass3 = GuiUtils.getBorderItem(Settings.GLASS_TYPE_3.getMaterial());

        setDefaultItem(glass1);

        mirrorFill(0, 0, true, true, glass2);
        mirrorFill(0, 1, true, true, glass2);
        mirrorFill(0, 2, true, true, glass3);
        mirrorFill(1, 0, false, true, glass2);
        mirrorFill(1, 1, false, true, glass3);

        if (spawner.getSpawnerStacks().size() != 1)
            setButton(0, com.songoda.core.gui.GuiUtils.createButtonItem(CompatibleMaterial.OAK_DOOR,
                    plugin.getLocale().getMessage("general.nametag.back").getMessage()),
                    (event) -> guiManager.showGUI(event.player, new SpawnerTiersGui(plugin, player, spawner)));

        int showAmt = stack.getStackSize();
        if (showAmt > 64)
            showAmt = 1;
        else if (showAmt == 0)
            showAmt = 1;

        ItemStack item;

        CompatibleMaterial displayItem = tier.getDisplayItem();
        if (displayItem != null && !displayItem.isAir()) {
            item = displayItem.getItem();
        } else {
            try {
                item = HeadUtils.getTexturedSkull(data);
            } catch (Exception e) {
                item = CompatibleMaterial.SPAWNER.getItem();
                item.setAmount(showAmt);
            }
        }

        ItemMeta itemmeta = item.getItemMeta();
        itemmeta.setDisplayName(plugin.getLocale().getMessage("interface.spawner.statstitle").getMessage());
        ArrayList<String> lore = new ArrayList<>();

        List<CompatibleMaterial> blocks = tier.getSpawnBlocksList();

        if (blocks.isEmpty() || blocks.get(0) == null) blocks = Collections.singletonList(CompatibleMaterial.AIR);

        StringBuilder only = new StringBuilder(blocks.get(0).name());

        int num = 1;
        for (CompatibleMaterial block : blocks) {
            if (num != 1)
                only.append("&8, &a").append(block.name());
            num++;
        }

        String onlyStr = plugin.getLocale().getMessage("interface.spawner.onlyspawnson")
                .processPlaceholder("block", only.toString()).getMessage();

        lore.addAll(TextUtils.wrap("7", onlyStr));

        boolean met = true;
        for (SpawnCondition condition : tier.getConditions()) {
            if (!condition.isMet(spawner)) {
                if (met) {
                    met = false;
                    lore.add("");
                    lore.add(plugin.getLocale().getMessage("interface.spawner.paused").getMessage());
                }
                lore.addAll(TextUtils.wrap("7", " » " + condition.getDescription()));
            }
        }

        if (spawner.getSpawnerStacks().size() == 1) {
            lore.add("");
            lore.add(plugin.getLocale().getMessage("interface.spawner.stats")
                    .processPlaceholder("amount", NumberUtils.formatNumber(spawner.getSpawnCount())).getMessage());
        }
        
        itemmeta.setLore(lore);
        item.setItemMeta(itemmeta);

        double levelsCost = tier.getUpgradeCost(CostType.LEVELS);
        double economyCost = tier.getUpgradeCost(CostType.ECONOMY);

        ItemStack itemXP = Settings.XP_ICON.getMaterial().getItem();
        ItemMeta itemmetaXP = itemXP.getItemMeta();
        itemmetaXP.setDisplayName(plugin.getLocale().getMessage("interface.spawner.upgradewithlevels").getMessage());
        ArrayList<String> loreXP = new ArrayList<>();
        if (nextTier != null)
            loreXP.add(plugin.getLocale().getMessage("interface.spawner.upgradewithlevelslore")
                    .processPlaceholder("cost", Double.toString(levelsCost)).getMessage());
        else
            loreXP.add(plugin.getLocale().getMessage("event.upgrade.maxed").getMessage());
        itemmetaXP.setLore(loreXP);
        itemXP.setItemMeta(itemmetaXP);

        ItemStack itemECO = Settings.ECO_ICON.getMaterial().getItem();
        ItemMeta itemmetaECO = itemECO.getItemMeta();
        itemmetaECO.setDisplayName(plugin.getLocale().getMessage("interface.spawner.upgradewitheconomy").getMessage());
        ArrayList<String> loreECO = new ArrayList<>();
        if (nextTier != null)
            loreECO.add(plugin.getLocale().getMessage("interface.spawner.upgradewitheconomylore")
                    .processPlaceholder("cost", EconomyManager.formatEconomy(economyCost)).getMessage());
        else
            loreECO.add(plugin.getLocale().getMessage("event.upgrade.maxed").getMessage());
        itemmetaECO.setLore(loreECO);
        itemECO.setItemMeta(itemmetaECO);

        setItem(13, item);

        if (player.hasPermission("epicspawners.convert")) {
            setButton(4, GuiUtils.createButtonItem(Settings.CONVERT_ICON.getMaterial(),
                    plugin.getLocale().getMessage("interface.spawner.convert").getMessage()),
                    (event) -> guiManager.showGUI(player, new SpawnerConvertGui(plugin, stack, player)));
        }

        if (spawner.getSpawnerStacks().size() == 1)
            GuiUtils.applyBoosted(22, this, plugin, player, spawner);

        if (Settings.DISPLAY_HELP_BUTTON.getBoolean()) {
            ItemStack itemO = new ItemStack(Material.PAPER, 1);
            ItemMeta itemmetaO = itemO.getItemMeta();
            itemmetaO.setDisplayName(plugin.getLocale().getMessage("interface.spawner.tutorialtitle").getMessage());
            ArrayList<String> loreO = new ArrayList<>();
            String text = plugin.getLocale().getMessage("interface.spawner.tutorial").getMessage();

            int start = (14 * infoPage) - 14;
            int li = 1; // 12
            int added = 0;
            boolean max = false;

            String[] parts = text.split("\\|");
            for (String line : parts) {
                line = compileHow(player, line);
                if (line.equals(".") || line.isEmpty()) continue;

                Matcher m = REGEX.matcher(line);
                while (m.find()) {
                    if (li > start) {
                        if (li < start + 15) {
                            loreO.add(TextUtils.formatText("&7" + m.group()));
                            added++;
                        } else {
                            max = true;
                        }
                    }
                    li++;
                }
            }

            if (added == 0) {
                this.infoPage = 1;
                this.addInfo();
                return;
            }

            if (max) {
                loreO.add(plugin.getLocale().getMessage("interface.spawner.howtonext").getMessage());
            } else {
                loreO.add(plugin.getLocale().getMessage("interface.spawner.howtoback").getMessage());
            }

            itemmetaO.setLore(loreO);
            itemO.setItemMeta(itemmetaO);
            setButton(8, itemO,
                    event -> {
                        this.infoPage++;
                        addInfo();
                    });
        }
        if (!onlyOneTier && data.isUpgradeable()) {
            if (Settings.UPGRADE_WITH_LEVELS_ENABLED.getBoolean())
                setButton(11, itemXP, event -> {
                    stack.upgrade(player, CostType.LEVELS);
                    spawner.overview(player);
                });
            if (Settings.UPGRADE_WITH_ECONOMY_ENABLED.getBoolean())
                setButton(15, itemECO, event -> {
                    stack.upgrade(player, CostType.ECONOMY);
                    spawner.overview(player);
                });
        }
    }

    private void runTask() {
        task = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (inventory != null && inventory.getViewers().size() != 0)
                paint();
        }, 5L, 5L);
    }

    private void addInfo() {
        ItemStack itemO = new ItemStack(Material.PAPER, 1);
        ItemMeta itemmetaO = itemO.getItemMeta();
        itemmetaO.setDisplayName(plugin.getLocale().getMessage("interface.spawner.tutorialtitle").getMessage());
        List<String> loreO = new ArrayList<>();
        String text = plugin.getLocale().getMessage("interface.spawner.tutorial").getMessage();

        int start = (14 * infoPage) - 14;
        int li = 1; // 12
        int added = 0;
        boolean max = false;

        String[] parts = text.split("\\|");
        for (String line : parts) {
            line = compileHow(player, line);
            if (line.equals(".") || line.isEmpty()) continue;

            Matcher m = REGEX.matcher(line);
            while (m.find()) {
                if (li > start) {
                    if (li < start + 15) {
                        loreO.add(TextUtils.formatText("&7" + m.group()));
                        added++;
                    } else {
                        max = true;
                    }
                }
                li++;
            }
        }

        if (added == 0) {
            this.infoPage = 1;
            this.addInfo();
            return;
        }

        if (max) {
            loreO.add(plugin.getLocale().getMessage("interface.spawner.howtonext").getMessage());
        } else {
            loreO.add(plugin.getLocale().getMessage("interface.spawner.howtoback").getMessage());
        }

        itemmetaO.setLore(loreO);
        itemO.setItemMeta(itemmetaO);
        setItem(8, itemO);
    }

    private String compileHow(Player p, String text) {
        Matcher m = Pattern.compile("\\{(.*?)}").matcher(text);
        while (m.find()) {
            Matcher mi = Pattern.compile("\\[(.*?)]").matcher(text);
            int nu = 0;
            int a = 0;
            String type = "";
            while (mi.find()) {
                if (nu == 0) {
                    type = mi.group().replace("[", "").replace("]", "");
                    text = text.replace(mi.group(), "");
                } else {
                    switch (type) {
                        case "LEVELUP":
                            if (nu == 1) {
                                if (!p.hasPermission("epicspawners.combine." + data.getIdentifyingName()) && !p.hasPermission("epicspawners.combine." + data.getIdentifyingName())) {
                                    text = text.replace(mi.group(), "");
                                } else {
                                    text = text.replace(mi.group(), a(a, mi.group()));
                                    a++;
                                }
                            } else if (nu == 2) {
                                if (!Settings.UPGRADE_WITH_LEVELS_ENABLED.getBoolean()) {
                                    text = text.replace(mi.group(), "");
                                } else {
                                    text = text.replace(mi.group(), a(a, mi.group()));
                                    a++;
                                }
                            } else if (nu == 3) {
                                if (!Settings.UPGRADE_WITH_ECONOMY_ENABLED.getBoolean()) {
                                    text = text.replace(mi.group(), "");
                                } else {
                                    text = text.replace(mi.group(), a(a, mi.group()));
                                    a++;
                                }
                            }
                            break;
                        case "WATER":
                            if (nu == 1) {
                                if (!Settings.LIQUID_REPEL_RADIUS.getBoolean()) {
                                    text = text.replace(mi.group(), "");
                                } else {
                                    text = text.replace(mi.group(), a(a, mi.group()));
                                }
                            }
                            break;
                        case "REDSTONE":
                            if (nu == 1) {
                                if (!Settings.REDSTONE_ACTIVATE.getBoolean()) {
                                    text = text.replace(mi.group(), "");
                                } else {
                                    text = text.replace(mi.group(), a(a, mi.group()));
                                }
                            }
                            break;
                        case "OMNI":
                            if (nu == 1) {
                                if (!Settings.OMNI_SPAWNERS.getBoolean()) {
                                    text = text.replace(mi.group(), "");
                                } else {
                                    text = text.replace(mi.group(), a(a, mi.group()));
                                }
                            }
                            break;
                        case "DROP":
                            if (!Settings.MOB_KILLING_COUNT.getBoolean() || !p.hasPermission("epicspawners.Killcounter")) {
                                text = "";
                            } else {
                                text = text.replace("<TYPE>", tier.getDisplayName().toLowerCase());
                                stack.getSpawnerData().getKillGoal();
                                if (stack.getSpawnerData().getKillGoal() != 0)
                                    text = text.replace("<AMT>", Integer.toString(stack.getSpawnerData().getKillGoal()));
                                else
                                    text = text.replace("<AMT>", Integer.toString(Settings.KILL_GOAL.getInt()));
                            }
                            if (nu == 1) {
                                if (Settings.COUNT_UNNATURAL_KILLS.getBoolean()) {
                                    text = text.replace(mi.group(), "");
                                } else {
                                    text = text.replace(mi.group(), a(a, mi.group()));
                                }
                            }
                            break;
                    }
                }
                nu++;
            }
        }
        text = text.replaceAll("[\\[\\]{}]", ""); // [, ], { or }
        return text;
    }

    private String a(int a, String text) {
        return (a != 0 ? ", " : "") + text;
    }

}