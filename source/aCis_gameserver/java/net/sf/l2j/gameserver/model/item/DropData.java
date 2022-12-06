package net.sf.l2j.gameserver.model.item;

import net.sf.l2j.Config;
import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.gameserver.enums.skills.Stats;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.Monster;

import java.text.DecimalFormat;

/**
 * A container used by monster drops.<br>
 * <br>
 * The chance is exprimed as 1.000.000 to handle 4 point accuracy digits (100.0000%).
 */
public class DropData {
    public static final int MAX_CHANCE = 1000000;

    private final int _itemId;
    private final int _minDrop;
    private final int _maxDrop;
    private final int _chance;

    public DropData(int itemId, int minDrop, int maxDrop, int chance) {
        _itemId = itemId;
        _minDrop = minDrop;
        _maxDrop = maxDrop;
        _chance = chance;
    }

    @Override
    public String toString() {
        return "DropData =[ItemID: " + _itemId + " Min: " + _minDrop + " Max: " + _maxDrop + " Chance: " + (_chance / 10000.0) + "%]";
    }

    /**
     * @return the id of the dropped item.
     */
    public int getItemId() {
        return _itemId;
    }

    /**
     * @return the minimum quantity of dropped items.
     */
    public int getMinDrop() {
        return _minDrop;
    }

    /**
     * @return the maximum quantity of dropped items.
     */
    public int getMaxDrop() {
        return _maxDrop;
    }

    /**
     * @return the chance to have a drop, under a 1.000.000 chance.
     */
    public int getChance() {
        return _chance;
    }

    public int calculateDropChance(Player player, Npc npc, DropCategory cat) {
        return calculateChance(player, npc, cat, getChance());
    }

    public static int calculateChance(Player player, Npc npc, DropCategory cat, int chance) {
        Monster monster = new Monster(npc.getObjectId(), npc.getTemplate());
        final int levelModifier = monster.calculateLevelModifierForDrop(player);

        if (Config.DEEPBLUE_DROP_RULES) {
            int deepBlueDrop = (levelModifier > 0) ? 3 : 1;
            chance = ((chance - ((chance * levelModifier) / 100)) / deepBlueDrop);
        }

        if (cat.getCategoryType() == 0) {
            chance *= Config.RATE_DROP_ADENA;
        } else if (cat.getCategoryType() == -1) {
            chance *= Config.RATE_DROP_SPOIL;
        } else if (npc.isRaidBoss()) {
            chance *= Config.RATE_DROP_ITEMS_BY_RAID;
        } else {
            chance *= Config.RATE_DROP_ITEMS;
        }

        if (cat.getCategoryType() == 0 && npc.isChampion()) {
            chance *= Config.CHAMP_MUL_ADENA;
        } else if (cat.getCategoryType() == -1 && npc.isChampion()) {
            chance *= Config.CHAMP_MUL_SPOIL;
        } else if (npc.isChampion()) {
            chance *= Config.CHAMP_MUL_ITEMS;
        }

        if (!npc.isRaidBoss() && cat.getCategoryType() == 0) {
            chance *= player.getStatus().calcStat(Stats.PERSONAL_DROP_ADENA, 1, null, null);
        } else if (!npc.isRaidBoss() && !(cat.getCategoryType() == -1)) {
            chance *= player.getStatus().calcStat(Stats.PERSONAL_DROP_ITEMS, 1, null, null);
        } else if (!npc.isRaidBoss() && cat.getCategoryType() == -1) {
            chance *= player.getStatus().calcStat(Stats.PERSONAL_SPOIL_ITEMS, 1, null, null);
        }

        return chance;
    }

    public int modifyCount(int count, int dropChance) {
        if (dropChance > 1000000) {
            double mul;
            mul = (double) dropChance / 1000000;
            count *= mul;
        }
        return count;
    }

    public int calculateDropCount(int dropChance) {
        int min = modifyCount(getMinDrop(), dropChance);
        int max = modifyCount(getMaxDrop(), dropChance);
        return Rnd.get(min, max);
    }

    public static String getChanceHtml(int chance) {
        double modifyChance = Math.min((double) chance / 10000, 100);
        if (modifyChance <= 0) {
            return "0";
        }
        return getPercent(modifyChance);
    }

    public static String getPercent(double chance) {
        String percent;
        if (chance <= 0.001) {
            DecimalFormat string = new DecimalFormat("#.####");
            percent = string.format(chance);
        } else if (chance <= 0.01) {
            DecimalFormat string = new DecimalFormat("#.###");
            percent = string.format(chance);
        } else {
            DecimalFormat string = new DecimalFormat("##.##");
            percent = string.format(chance);
        }
        return percent;
    }
}