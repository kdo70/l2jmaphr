package net.sf.l2j.gameserver.model.item;

import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.Monster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DropCategory {
    private final List<DropData> _drops;
    private int _categoryChance;
    private final int _categoryType;

    public DropCategory(int categoryType) {
        _categoryType = categoryType;
        _drops = new ArrayList<>(0);
        _categoryChance = 0;
    }

    public void addDropData(DropData drop, boolean raid) {
        _drops.add(drop);
        _categoryChance += drop.getChance();
    }

    public List<DropData> getAllDrops() {
        return _drops;
    }

    public boolean isSweep() {
        return (getCategoryType() == -1);
    }

    public int getCategoryChance() {
        if (getCategoryType() >= 0)
            return _categoryChance;

        return DropData.MAX_CHANCE;
    }

    public int getCategoryType() {
        return _categoryType;
    }

    /**
     * useful for seeded conditions...the category will attempt to drop only among items that are allowed to be dropped when a mob is seeded. Previously, this only included adena. According to sh1ny, sealstones are also acceptable drops. if no acceptable drops are in the category, nothing will be
     * dropped. otherwise, it will check for the item's chance to drop and either drop it or drop nothing.
     *
     * @return acceptable drop when mob is seeded, if it exists. Null otherwise.
     */
    public synchronized DropData dropSeedAllowedDropsOnly() {
        List<DropData> drops = new ArrayList<>();
        int subCatChance = 0;
        for (DropData drop : getAllDrops()) {
            if ((drop.getItemId() == 57) || (drop.getItemId() == 6360) || (drop.getItemId() == 6361) || (drop.getItemId() == 6362)) {
                drops.add(drop);
                subCatChance += drop.getChance();
            }
        }

        if (subCatChance == 0)
            return null;

        // among the results choose one.
        final int randomIndex = Rnd.get(subCatChance);

        int sum = 0;
        for (DropData drop : drops) {
            sum += drop.getChance();

            if (sum > randomIndex) // drop this item and exit the function
                return drop;
        }
        // since it is still within category, only drop one of the acceptable drops from the results.
        return null;
    }

    public synchronized DropData dropOne(Player player, Monster monster) {
        List<DropData> drops = getAllDrops();
        Collections.shuffle(drops);
        for (DropData drop : getAllDrops()) {
            int chance = drop.calculateDropChance(player, monster, this);
            if (Rnd.get(DropData.MAX_CHANCE) < chance) {
                return drop;
            }
        }
        return null;
    }

    public int getDropChance() {
        if (getCategoryType() >= 0) {
            return _categoryChance;
        }
        return 0;
    }

    public int calculateCategoryChance(Player player, Npc npc) {
        return DropData.calculateChance(player, npc, this, getCategoryChance());
    }
}