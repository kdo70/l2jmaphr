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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The type Drop list npc.
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

        if (_npc.isChampion()) {
            champion();
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

    public void champion() {
        if (_page > 1) {
            return;
        }

        Monster monster = new Monster(_npc.getObjectId(), _npc.getTemplate());
        final int levelModifier = monster.calculateLevelModifierForDrop(_player);
        int chance = Config.CHAMP_ITEM_DROP_CHANCE * 10000;
        if (Config.DEEPBLUE_DROP_RULES) {
            int deepBlueDrop = (levelModifier > 0) ? 3 : 1;
            chance = ((chance - ((chance * levelModifier) / 100)) / deepBlueDrop);
        }

        _html.append("<br><center><font color=B09878>&nbsp;Category ")
                .append("#Champion")
                .append("&nbsp;Type: ")
                .append("Drop")
                .append("&nbsp;Chance: ")
                .append(DropData.getChanceHtml(chance))
                .append("%</font></center><img src=L2UI.SquareGray width=280 height=1>");

        Item item = ItemData.getInstance().getTemplate(Config.CHAMP_ITEM_DROP_ID);
        String name = item.getName();

        if (name.length() >= 40) name = name.substring(0, 37) + "...";

        _html.append("<table width=280 bgcolor=000000><tr>");
        _html.append("<td width=44 height=41 align=center>");
        _html.append("<table bgcolor=ff0000 cellpadding=6 cellspacing=-5>");
        _html.append("<tr><td><button width=32 height=32 back=");
        _html.append(IconData.getIcon(Config.CHAMP_ITEM_DROP_ID)).append(" fore=");
        _html.append(IconData.getIcon(Config.CHAMP_ITEM_DROP_ID));
        _html.append("></td></tr></table></td>");
        _html.append("<td width=260>");
        _html.append(name);
        _html.append("<br1>");
        _html.append("<font color=B09878>Chance: ");
        _html.append(DropData.getChanceHtml(chance));
        _html.append("% Count: ");
        _html.append(Config.CHAMP_ITEM_DROP_COUNT);
        _html.append("</font></td>");
        _html.append("</tr></table>");
        _html.append("<img src=L2UI.SquareGray width=280 height=1>");
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
                .append(DropData.getChanceHtml(category.calculateCategoryChance(_player, _npc)))
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

            buildDropHtml(category, drop);

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


    public void buildDropHtml(DropCategory category, DropData drop) {
        int chance = drop.calculateDropChance(_player, _npc, category);
        String chanceHtml = DropData.getChanceHtml(chance);

        double min = drop.modifyCount(drop.getMinDrop(), chance);
        double max = drop.modifyCount(drop.getMaxDrop(), chance);

        String roundMin = DropData.getPercent(Math.floor(min));
        String roundMax = DropData.getPercent(Math.ceil(max));

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
        _html.append(chanceHtml);
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
     * Drop category list.
     *
     * @return the list
     */
    public List<DropCategory> dropCategory() {
        final List<DropCategory> categories = new ArrayList<>(_template.getDropData());
        categories.sort(Comparator.comparing(DropCategory::getDropChance));
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
}