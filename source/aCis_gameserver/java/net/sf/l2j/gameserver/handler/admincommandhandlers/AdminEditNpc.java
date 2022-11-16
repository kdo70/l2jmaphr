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