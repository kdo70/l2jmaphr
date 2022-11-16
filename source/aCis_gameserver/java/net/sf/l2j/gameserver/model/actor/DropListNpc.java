package net.sf.l2j.gameserver.model.actor;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.xml.IconData;
import net.sf.l2j.gameserver.data.xml.ItemData;
import net.sf.l2j.gameserver.data.xml.NpcData;
import net.sf.l2j.gameserver.model.actor.instance.Monster;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.item.DropCategory;
import net.sf.l2j.gameserver.model.item.DropData;
import net.sf.l2j.gameserver.model.item.kind.Item;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

import java.text.DecimalFormat;
import java.util.*;

/**
 * The type Drop list npc.
 * TODO: проверить дроп камней и их рейтовку
 */
public class DropListNpc {

    /**
     * The Player.
     */
    private final Player _player;

    /**
     * The Npc.
     */
    private final Npc _npc;

    /**
     * The objectId.
     */
    private final int _objectId;

    /**
     * The page.
     */
    private final int _page;

    /**
     * The NpcTemplate.
     */
    private final NpcTemplate _template;

    /**
     * The Monster.
     */
    private final Monster _monster;

    /**
     * The Items per list.
     */
    public int ITEMS_PER_LIST = 8;

    /**
     * The Iteration.
     */
    public int ITERATION = 0;

    /**
     * The Category iteration.
     */
    public int CATEGORY_ITERATION = 0;

    /**
     * The Current page.
     */
    public int CURRENT_PAGE = 1;

    /**
     * The Items in page.
     */
    public int ITEMS_IN_PAGE = 0;

    /**
     * The Has more.
     */
    public boolean HAS_MORE = false;

    /**
     * The Html.
     */
    public StringBuilder _html;

    /**
     * The Message.
     */
    public NpcHtmlMessage _message;

    /**
     * Instantiates a new Drop list npc.
     *
     * @param player   the player
     * @param npc      the npc
     * @param objectId the object id
     * @param page     the page
     */
    public DropListNpc(Player player, Npc npc, Integer objectId, Integer page) {
        _player = player;
        _npc = npc;
        _objectId = objectId;
        _page = page;
        _template = NpcData.getInstance().getTemplate(_npc.getNpcId());
        _monster = new Monster(_objectId, _template);
        _html = new StringBuilder();
        _message = new NpcHtmlMessage(_npc.getNpcId());
    }

    /**
     * Send.
     */
    public void send() {
        if (this.checkAccess()) {
            return;
        }

        categories();
        buildPaginationHtml();
        buildMessage();

        _player.sendPacket(_message);
    }

    /**
     * Categories.
     */
    public void categories() {
        final List<DropCategory> categories = dropCategory();
        for (DropCategory category : categories) {
            CATEGORY_ITERATION++;

            if (ITEMS_IN_PAGE == ITEMS_PER_LIST) {
                HAS_MORE = true;
                break;
            }

            if (ITERATION == 0 && CURRENT_PAGE == _page) {
                buildCategoryHtml(category);
            }
            dropList(category);
        }
    }

    /**
     * Build category html.
     *
     * @param category the category
     */
    public void buildCategoryHtml(DropCategory category) {
        _html.append("<br><center><font color=B09878>&nbsp;Category ")
                .append("#")
                .append(CATEGORY_ITERATION)
                .append("&nbsp;Type: ")
                .append(category.isSweep() ? "Spoil " : "Drop ")
                .append("&nbsp;Chance: ")
                .append(category.getChance(_player, _monster, _npc.isChampion()))
                .append("%</font></center><img src=L2UI.SquareGray width=280 height=1>");
    }

    /**
     * Drop list.
     *
     * @param category the category
     */
    public void dropList(DropCategory category) {
        final List<DropData> dropList = dropData(category);
        for (DropData drop : dropList) {

            if (CURRENT_PAGE != _page) {
                iteration();
                continue;
            }

            if (ITEMS_IN_PAGE == ITEMS_PER_LIST) {
                HAS_MORE = true;
                break;
            }

            double chance = calculateChance(category, drop);
            buildDropHtml(drop, chance);
            ITEMS_IN_PAGE++;
        }
    }

    /**
     * Iteration.
     */
    public void iteration() {
        ITERATION++;

        if (ITERATION != ITEMS_PER_LIST) {
            return;
        }

        CURRENT_PAGE++;
        ITERATION = 0;
    }

    /**
     * Build drop html.
     *
     * @param drop   the drop
     * @param chance the chance
     */
    public void buildDropHtml(DropData drop, double chance) {

        double min = drop.getMinDrop();
        double max = drop.getMaxDrop();

        if ((int) chance > 100) {
            min = (min * chance) / 100;
            max = ((max * chance) / 100) + 1;
        }

        String roundMin = percent(Math.floor(min));
        String roundMax = percent(Math.ceil(max));

        chance = chance > 100 ? 99 : chance;

        Item item = ItemData.getInstance().getTemplate(drop.getItemId());
        String name = item.getName();

        if (name.length() >= 40) name = name.substring(0, 37) + "...";

        _html.append("<table width=280 bgcolor=000000><tr>");
        _html.append("<td width=44 height=41 align=center>");
        _html.append("<table bgcolor=ffffff cellpadding=6 cellspacing=-5>");
        _html.append("<tr><td><button width=32 height=32 back=");
        _html.append(IconData.getIcon(item.getItemId())).append(" fore=");
        _html.append(IconData.getIcon(item.getItemId()));
        _html.append("></td></tr></table></td>");
        _html.append("<td width=260>");
        _html.append(name);
        _html.append("<br1>");
        _html.append("<font color=B09878>Chance: ");
        _html.append(percent(chance));
        _html.append("% Count: ");
        _html.append(roundMin);
        _html.append(min == max ? "" : " - " + roundMax);
        _html.append("</font></td>");
        _html.append("</tr></table>");
        _html.append("<img src=L2UI.SquareGray width=280 height=1>");
    }

    /**
     * Check access boolean.
     *
     * @return the boolean
     */
    public Boolean checkAccess() {
        return _npc == null || _template.getDropData().isEmpty();
    }

    /**
     * Build pagination html.
     */
    public void buildPaginationHtml() {
        _html.append("<img height=").append(335 - (ITEMS_IN_PAGE * 42)).append(">");
        _html.append("<img src=L2UI.SquareGray width=280 height=1>");
        _html.append("<table width=280 bgcolor=000000><tr>");
        _html.append("<td width=100></td>");
        _html.append("<td align=center width=30>");
        _html.append("<table width=1 height=2 bgcolor=000000></table>");
        _html.append("<button action=\"bypass droplist ");
        _html.append(_objectId);
        _html.append(" ");
        _html.append(_page - (_page > 1 ? 1 : 0));
        _html.append("\" width=16 height=16 back=L2UI_ch3.prev1_over fore=L2UI_ch3.prev1>");
        _html.append("</td>");
        _html.append("<td align=center width=100>Page ");
        _html.append(_page);
        _html.append("</td>");
        _html.append("<td align=center width=30>");
        _html.append("<table width=1 height=2 bgcolor=000000></table>");
        _html.append("<button action=\"bypass droplist ");
        _html.append(_objectId);
        _html.append(" ");
        _html.append(_page + (HAS_MORE ? 1 : 0));
        _html.append("\" width=16 height=16 back=L2UI_ch3.next1_over fore=L2UI_ch3.next1>");
        _html.append("</td>");
        _html.append("<td width=100></td>");
        _html.append("</tr></table>");
        _html.append("<img src=L2UI.SquareGray width=280 height=1>");
    }

    /**
     * Build message.
     */
    public void buildMessage() {
        _message.setFile("data/html/droplist.htm");
        _message.replace("%list%", _html.toString());
        _message.replace("%name%", _template.getName());
    }

    /**
     * Calculate chance double.
     *
     * @param category the category
     * @param drop     the drop
     * @return the double
     */
    public Double calculateChance(DropCategory category, DropData drop) {
        double chance = drop.getChance();
        if (drop.getItemId() == 57) {
            chance = calculateAdena(chance);
        } else if (category.isSweep()) {
            chance = calculateSweep(chance);
        } else {
            chance = calculateItems(chance);
        }
        return chance / 10000;
    }

    /**
     * Calculate adena double.
     *
     * @param chance the chance
     * @return the double
     */
    public Double calculateAdena(double chance) {
        chance *= Config.RATE_DROP_ADENA;
        if (_npc.isChampion()) {
            chance *= Config.CHAMP_MUL_ADENA;
        }
        return chance;
    }

    /**
     * Calculate sweep double.
     *
     * @param chance the chance
     * @return the double
     */
    public Double calculateSweep(double chance) {
        chance *= Config.RATE_DROP_SPOIL;
        if (_npc.isChampion()) {
            chance *= Config.CHAMP_MUL_SPOIL;
        }
        return chance;
    }

    /**
     * Calculate items double.
     *
     * @param chance the chance
     * @return the double
     */
    public Double calculateItems(double chance) {
        if (Objects.equals(_template.getType(), "RaidBoss")) {
            chance *= Config.RATE_DROP_ITEMS_BY_RAID;
        } else {
            chance *= Config.RATE_DROP_ITEMS;
            if (_npc.isChampion()) {
                chance *= Config.CHAMP_MUL_ITEMS;
            }
        }
        return chance;
    }

    /**
     * Drop category list.
     *
     * @return the list
     */
    public List<DropCategory> dropCategory() {
        final List<DropCategory> categories = new ArrayList<>(_template.getDropData());
        categories.sort(Comparator.comparing(DropCategory::getCategoryChance));
        Collections.reverse(categories);
        return categories;
    }

    /**
     * Drop data list.
     *
     * @param category the category
     * @return the list
     */
    public List<DropData> dropData(DropCategory category) {
        final List<DropData> dropList = new ArrayList<>(category.getAllDrops());
        dropList.sort(Comparator.comparing(DropData::getChance));
        Collections.reverse(dropList);
        return dropList;
    }

    /**
     * Percent string.
     *
     * @param chance the chance
     * @return the string
     */
    public static String percent(Double chance) {
        String percent;
        if (chance <= 0.001) {
            percent = (new DecimalFormat("#.####")).format(chance);
        } else if (chance <= 0.01) {
            percent = (new DecimalFormat("#.###")).format(chance);
        } else {
            percent = (new DecimalFormat("##.##")).format(chance);
        }
        return percent;
    }
}