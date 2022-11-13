/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package net.sf.l2j.gameserver.handler.admincommandhandlers;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;

import net.sf.l2j.Config;
import net.sf.l2j.commons.lang.StringUtil;

import net.sf.l2j.gameserver.data.manager.BuyListManager;
import net.sf.l2j.gameserver.data.xml.IconData;
import net.sf.l2j.gameserver.data.xml.ItemData;
import net.sf.l2j.gameserver.data.xml.NpcData;
import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.item.kind.Item;
import net.sf.l2j.gameserver.skills.L2Skill;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.instance.Merchant;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.buylist.NpcBuyList;
import net.sf.l2j.gameserver.model.buylist.Product;
import net.sf.l2j.gameserver.model.item.DropCategory;
import net.sf.l2j.gameserver.model.item.DropData;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.enums.ScriptEventType;
import net.sf.l2j.gameserver.scripting.Quest;
import net.sf.l2j.gameserver.enums.skills.SkillType;

/**
 * @author terry
 */
public class AdminEditNpc implements IAdminCommandHandler
{
    private static final int PAGE_LIMIT = 20;

    private static final String[] ADMIN_COMMANDS =
            {
                    "admin_show_droplist",
                    "admin_show_scripts",
                    "admin_show_shop",
                    "admin_show_shoplist",
                    "admin_show_skilllist"
            };

    @Override
    public void useAdminCommand(String command, Player player)
    {
        final StringTokenizer st = new StringTokenizer(command, " ");
        st.nextToken();

        if (command.startsWith("admin_show_shoplist"))
        {
            try
            {
                showShopList(player, Integer.parseInt(st.nextToken()));
            }
            catch (Exception e)
            {
                player.sendMessage("Usage: //show_shoplist <list_id>");
            }
        }
        else if (command.startsWith("admin_show_shop"))
        {
            try
            {
                showShop(player, Integer.parseInt(st.nextToken()));
            }
            catch (Exception e)
            {
                player.sendMessage("Usage: //show_shop <npc_id>");
            }
        }
        else if (command.startsWith("admin_show_droplist"))
        {
            try
            {
                int npcId = Integer.parseInt(st.nextToken());
                int page = (st.hasMoreTokens()) ? Integer.parseInt(st.nextToken()) : 1;

                showNpcDropList(player, npcId, page);
            }
            catch (Exception e)
            {
                player.sendMessage("Usage: //show_droplist <npc_id> [<page>]");
            }
        }
        else if (command.startsWith("admin_show_skilllist"))
        {
            try
            {
                showNpcSkillList(player, Integer.parseInt(st.nextToken()));
            }
            catch (Exception e)
            {
                player.sendMessage("Usage: //show_skilllist <npc_id>");
            }
        }
        else if (command.startsWith("admin_show_scripts"))
        {
            try
            {
                showScriptsList(player, Integer.parseInt(st.nextToken()));
            }
            catch (Exception e)
            {
                player.sendMessage("Usage: //show_scripts <npc_id>");
            }
        }
    }

    private static void showShopList(Player player, int listId)
    {
        final NpcBuyList buyList = BuyListManager.getInstance().getBuyList(listId);
        if (buyList == null)
        {
            player.sendMessage("BuyList template is unknown for id: " + listId + ".");
            return;
        }

        final StringBuilder sb = new StringBuilder(500);
        StringUtil.append(sb, "<html><body><center><font color=\"LEVEL\">", NpcData.getInstance().getTemplate(buyList.getNpcId()).getName(), " (", buyList.getNpcId(), ") buylist id: ", buyList.getListId(), "</font></center><br><table width=\"100%\"><tr><td width=200>Item</td><td width=80>Price</td></tr>");

        for (Product product : buyList.getProducts())
            StringUtil.append(sb, "<tr><td>", product.getItem().getName(), "</td><td>", product.getPrice(), "</td></tr>");

        sb.append("</table></body></html>");

        final NpcHtmlMessage html = new NpcHtmlMessage(0);
        html.setHtml(sb.toString());
        player.sendPacket(html);
    }

    private static void showShop(Player player, int npcId)
    {
        final List<NpcBuyList> buyLists = BuyListManager.getInstance().getBuyListsByNpcId(npcId);
        if (buyLists.isEmpty())
        {
            player.sendMessage("No buyLists found for id: " + npcId + ".");
            return;
        }

        final StringBuilder sb = new StringBuilder(500);
        StringUtil.append(sb, "<html><title>Merchant Shop Lists</title><body>");

        if (player.getTarget() instanceof Merchant)
        {
            Npc merchant = (Npc) player.getTarget();
            int taxRate = merchant.getCastle().getTaxPercent();

            StringUtil.append(sb, "<center><font color=\"LEVEL\">", merchant.getName(), " (", npcId, ")</font></center><br>Tax rate: ", taxRate, "%");
        }

        StringUtil.append(sb, "<table width=\"100%\">");

        for (NpcBuyList buyList : buyLists)
            StringUtil.append(sb, "<tr><td><a action=\"bypass -h admin_show_shoplist ", buyList.getListId(), " 1\">Buylist id: ", buyList.getListId(), "</a></td></tr>");

        StringUtil.append(sb, "</table></body></html>");

        final NpcHtmlMessage html = new NpcHtmlMessage(0);
        html.setHtml(sb.toString());
        player.sendPacket(html);
    }

    private static void showNpcDropList(Player player, int npcId, int page)
    {
        final int ITEMS_PER_LIST = 8;

        final NpcTemplate npc = NpcData.getInstance().getTemplate(npcId);
        if (npc == null) return;
        if (npc.getDropData().isEmpty()) return;


        final List<DropCategory> list = new ArrayList<>(npc.getDropData());
        Collections.reverse(list);

        int i = 0;
        int itemsInPage = 0;
        int currentPage = 1;

        boolean hasMore = false;

        final StringBuilder sb = new StringBuilder();

        for (DropCategory cat : list) {
            sb.append("1");
            if (itemsInPage == ITEMS_PER_LIST) {
                hasMore = true;
                break;
            }

            for (DropData drop : cat.getAllDrops()) {
                double chance = (
                        drop.getItemId() == 57
                                ? drop.getChance() * Config.RATE_DROP_ADENA
                                : drop.getChance() * Config.RATE_DROP_ITEMS
                ) / 10000;

                chance = chance > 100 ? 100 : chance;

                String percent = null;
                if (chance <= 0.001) {
                    DecimalFormat df = new DecimalFormat("#.####");
                    percent = df.format(chance);
                } else if (chance <= 0.01) {
                    DecimalFormat df = new DecimalFormat("#.###");
                    percent = df.format(chance);
                } else {
                    DecimalFormat df = new DecimalFormat("##.##");
                    percent = df.format(chance);
                }

                Item item = ItemData.getInstance().getTemplate(drop.getItemId());
                String name = item.getName();

                if (name.length() >= 40) name = name.substring(0, 37) + "...";

                if (currentPage != page) {
                    i++;
                    if (i == ITEMS_PER_LIST) {
                        currentPage++;
                        i = 0;
                    }
                    continue;
                }

                if (itemsInPage == ITEMS_PER_LIST) {
                    hasMore = true;
                    break;
                }

                sb.append("<table width=280 bgcolor=000000><tr>");
                sb.append("<td width=44 height=41 align=center>");
                sb.append("<table bgcolor=FFFFFF cellpadding=6 cellspacing=\"-5\">");
                sb.append("<tr><td><button width=32 height=32 back="
                        + IconData.getIcon(item.getItemId())
                        + " fore=" + IconData.getIcon(item.getItemId())
                        + "></td></tr></table></td>");
                sb.append("<td width=260>"
                        + name
                        + "<br1><font color=B09878>"
                        + (cat.isSweep() ? "Spoil" : "Drop")
                        + " Chance: " + percent
                        + "% Count: " + drop.getMinDrop()
                        + " - "
                        + drop.getMaxDrop()
                        + "</font></td>");
                sb.append("</tr></table>");
                sb.append("<img src=L2UI.SquareGray width=280 height=1>");
                itemsInPage++;
            }
        }

        sb.append("<img height=" + (335 - (itemsInPage * 42)) + ">");
        sb.append("<img src=L2UI.SquareGray width=280 height=1>");

        sb.append("<table width=280 bgcolor=000000><tr>");
        sb.append("<td width=100></td>");
        sb.append("<td align=center width=30>");
        sb.append("<table width=1 height=2 bgcolor=000000></table>");
        if (page > 1) {
            sb.append("<button action=\"bypass droplist "
                    + npcId + " "
                    + (page - 1)
                    + "\" width=16 height=16 back=L2UI_ch3.prev1_over fore=L2UI_ch3.prev1>");
        }
        sb.append("</td>");

        sb.append("<td align=center width=100>Page " + page + "</td>");

        sb.append("<td align=center width=30>");
        sb.append("<table width=1 height=2 bgcolor=000000></table>");
        if (hasMore) {
            sb.append("<button action=\"bypass droplist "
                    + npcId
                    + " "
                    + (page + 1)
                    + "\" width=16 height=16 back=L2UI_ch3.next1_over fore=L2UI_ch3.next1>");
        }
        sb.append("</td>");
        sb.append("<td width=100></td>");

        sb.append("</tr></table>");

        sb.append("<img src=L2UI.SquareGray width=280 height=1>");

        NpcHtmlMessage html = new NpcHtmlMessage(npcId);

        html.setFile("data/html/droplist.htm");
        html.replace("%list%", sb.toString());
        html.replace("%name%", npc.getName());

        player.sendPacket(html);
    }

    private static void showNpcSkillList(Player player, int npcId)
    {
        final NpcTemplate npcData = NpcData.getInstance().getTemplate(npcId);
        if (npcData == null)
        {
            player.sendMessage("Npc template is unknown for id: " + npcId + ".");
            return;
        }
        return;

	/*	final Collection<L2Skill> skills = npcData.getSkills().values();

		final StringBuilder sb = new StringBuilder(500);
		StringUtil.append(sb, "<html><body><center><font color=\"LEVEL\">", npcData.getName(), " (", npcId, "): ", skills.size(), " skills</font></center><table width=\"100%\">");

		for (L2Skill skill : skills)
			StringUtil.append(sb, "<tr><td>", ((skill.getSkillType() == SkillType.NOTDONE) ? ("<font color=\"777777\">" + skill.getName() + "</font>") : skill.getName()), " [", skill.getId(), "-", skill.getLevel(), "]</td></tr>");

		sb.append("</table></body></html>");

		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setHtml(sb.toString());
		player.sendPacket(html);*/
    }

    private static void showScriptsList(Player player, int npcId)
    {
        final NpcTemplate npcData = NpcData.getInstance().getTemplate(npcId);
        if (npcData == null)
        {
            player.sendMessage("Npc template is unknown for id: " + npcId + ".");
            return;
        }

        final StringBuilder sb = new StringBuilder(500);
        StringUtil.append(sb, "<html><body><center><font color=\"LEVEL\">", npcData.getName(), " (", npcId, ")</font></center><br>");

        if (!npcData.getEventQuests().isEmpty())
        {
            ScriptEventType type = null; // Used to see if we moved of type.

            // For any type of QuestScriptEventType
            for (Map.Entry<ScriptEventType, List<Quest>> entry : npcData.getEventQuests().entrySet())
            {
                if (type != entry.getKey())
                {
                    type = entry.getKey();
                    StringUtil.append(sb, "<br><font color=\"LEVEL\">", type.name(), "</font><br1>");
                }

                for (Quest quest : entry.getValue())
                    StringUtil.append(sb, quest.getName(), "<br1>");
            }
        }
        else
            sb.append("This NPC isn't affected by scripts.");

        sb.append("</body></html>");

        final NpcHtmlMessage html = new NpcHtmlMessage(0);
        html.setHtml(sb.toString());
        player.sendPacket(html);
    }

    @Override
    public String[] getAdminCommandList()
    {
        return ADMIN_COMMANDS;
    }
}