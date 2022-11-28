package net.sf.l2j.gameserver.data.xml;

import net.sf.l2j.Config;
import net.sf.l2j.commons.data.StatSet;
import net.sf.l2j.commons.data.xml.IXmlReader;
import net.sf.l2j.commons.lang.StringUtil;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.gameserver.enums.actors.ClassId;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.holder.NewbieBuffHolder;
import net.sf.l2j.gameserver.model.holder.NewbieCommonBuffHolder;
import net.sf.l2j.gameserver.model.item.kind.Item;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.MagicSkillUse;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.skills.L2Skill;
import net.sf.l2j.gameserver.taskmanager.GameTimeTaskManager;
import org.w3c.dom.Document;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

public class NewbieCommonBuffData implements IXmlReader {
    private final List<NewbieCommonBuffHolder> _buffs = new ArrayList<>();


    protected NewbieCommonBuffData() {
        load();
    }

    @Override
    public void load() {
        parseFile("./data/xml/newbieCommonBuffs.xml");
        LOGGER.info("Loaded {} newbie common buffs.", _buffs.size());
    }

    @Override
    public void parseDocument(Document doc, Path path) {
        forEach(doc, "list", listNode -> forEach(listNode, "buff", buffNode -> {
            final StatSet set = parseAttributes(buffNode);
            _buffs.add(new NewbieCommonBuffHolder(set));
        }));
    }

    public void getList(Npc npc, Player player) {
        try {
            final NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
            final boolean isMage = player.isMageClass() && player.getClassId() != ClassId.ORC_MYSTIC && player.getClassId() != ClassId.ORC_SHAMAN;
            int visibleCount = 0;
            if (player.getStatus().getLevel() < NewbieBuffData.getInstance().getLowestBuffLevel(isMage)) {
                html.setFile("data/html/script/feature/NewbieHelper/guide_for_newbie002.htm");
            } else if (player.isSubClassActive() || player.getStatus().getLevel() > NewbieBuffData.getInstance().getUpperBuffLevel(isMage)) {
                html.setFile("data/html/script/feature/NewbieHelper/guide_for_newbie003.htm");
            } else {

                final StringBuilder sb = new StringBuilder();
                for (int index = 0; index < _buffs.size(); index++) {
                    final NewbieCommonBuffHolder buff = _buffs.get(index);
                    if (player.getStatus().getLevel() < buff.getLowerLevel() || player.getStatus().getLevel() > buff.getUpperLevel()) {
                        continue;
                    }
                    if (buff.isOnlyNight() && !GameTimeTaskManager.getInstance().isNight()) {
                        continue;
                    }
                    Item item = ItemData.getInstance().getTemplate(buff.getPriceId());
                    StringTokenizer tokenizer = new StringTokenizer(item.getName());
                    String itemName = tokenizer.nextToken();
                    StringUtil.append(sb, "<table width=285 bgcolor=000000>" +
                            "<tr>" +
                            "<td width=35><img src=\"", buff.getIcon(), "\" width=32 height=16><img height=7></td>" +
                            "<td width=105>", buff.getSkill().getName(), "</td>" +
                            "<td width=45><font color=A3A0A3>", String.format(Locale.US, "%,d", calculatePrice(buff, player)), "</font></td>" +
                            "<td width=35><font color=B09878>", itemName, "</font></td>" +
                            "<td width=60><a action=\"bypass -h Quest NewbieHelper GetBuff ", index, "\">Применить</a></td>" +
                            "</tr>" +
                            "</table>" +
                            "<img src=L2UI.SquareGray width=280 height=1>");
                    visibleCount++;
                }

                StringUtil.append(sb, "<table width=285 bgcolor=000000><tr>" +
                        "<td width=285 align=\"center\"><img height=2>" +
                        "<a action=\"bypass -h Quest NewbieHelper AboutBuff\">" +
                        "Узнать больше о магической поддержке" +
                        "</a>" +
                        "<img height=2> </td>" +
                        "</tr></table>");

                if (visibleCount < 13) {
                    StringUtil.append(sb, "<img height=", 26 * (13 - visibleCount), ">");
                }
                html.setFile("data/html/script/feature/NewbieHelper/guide_for_newbie005.htm");
                html.replace("%list%", sb.toString());
            }
            html.replace("%npcName%", npc.getName());
            player.sendPacket(html);
        } catch (final Exception e) {
            System.out.println("Class: NewbieCommonBuffData Method: getList Message:" + e.getMessage());
            player.sendPacket(ActionFailed.STATIC_PACKET);
        }
    }

    public void getBuff(Npc npc, Player player, int index) {
        final NewbieCommonBuffHolder buff = _buffs.get(index);

        final boolean lowerLevel = player.getStatus().getLevel() < buff.getLowerLevel();
        final boolean upperLevel = player.getStatus().getLevel() > buff.getUpperLevel();

        if (lowerLevel || upperLevel || player.isSubClassActive()) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        int visualSkillId = buff.getSkill().getId();
        int skillLvl = buff.getSkill().getLevel();

        if (visualSkillId == 4699) visualSkillId = 4700;
        if (visualSkillId == 4702) visualSkillId = 4703;

        boolean needPayment = !Config.FREE_BUFFER && player.getStatus().getLevel() > Config.FREE_BUFFER_LVL;

        if (needPayment && !player.destroyItemByItemId("NewbieCommonBuff", buff.getPriceId(), calculatePrice(buff, player), npc, true)) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        MagicSkillUse packet = new MagicSkillUse(npc, player, visualSkillId, skillLvl, 1000, 0);
        player.broadcastPacket(packet);
        ThreadPool.schedule(() -> setEffect(npc, player, buff.getSkill()), 1000);
        this.getList(npc, player);
    }

    public int calculatePrice(NewbieCommonBuffHolder buff, Player player) {
        int calculatedPrice = buff.getPriceCount();

        if (buff.getPriceId() == 57) {
            calculatedPrice *= buff.getSkill().getMpConsume() == 0 ? 242 : buff.getSkill().getMpConsume();

            int mul = player.getStatus().getLevel();
            if (!Config.FREE_BUFFER) {
                mul -= Config.FREE_BUFFER_LVL;
            }
            calculatedPrice *= mul;
        }

        float currentHour = GameTimeTaskManager.getInstance().getGameHour();
        calculatedPrice *= 1 + currentHour / 100;

        return calculatedPrice;
    }

    public static void setEffect(Npc npc, Player player, L2Skill skill) {
        skill.getEffects(npc, player);
    }

    public void getDescriptionList(Npc npc, Player player, int page) {
        final NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
        final StringBuilder sb = new StringBuilder();
        int currentPage = 1;
        int iteration = 0;
        int item_per_list = 8;
        int item_in_page = 0;
        boolean has_more = false;

        // Orc Mage and Orc Shaman should receive fighter buffs since IL, although they are mage classes.
        final boolean isMage = player.isMageClass()
                && player.getClassId() != ClassId.ORC_MYSTIC
                && player.getClassId() != ClassId.ORC_SHAMAN;

        for (NewbieBuffHolder buff : NewbieBuffData.getInstance().getValidBuffs(isMage)) {
            final L2Skill skill = buff.getSkill();

            if (currentPage != page) {
                System.out.println("iteration " + currentPage);
                iteration++;

                if (iteration != item_per_list) {
                    continue;
                }

                currentPage++;
                iteration = 0;
                continue;
            }

            if (item_in_page == item_per_list) {
                System.out.println("break " + item_in_page);
                has_more = true;
                break;
            }

            StringUtil.append(sb, "<table width=280 bgcolor=000000>" +
                    "<tr>" +
                    "<td width=35>" +
                    "<img src=\"" + buff.getIcon() + "\" width=32 height=32><img height=5>" +
                    "</td>" +
                    "<td width=200>" + skill.getName() + "<font color=A3A0A3> Lv.</font> <font color=B09878>" + skill.getLevel() + "</font>" +
                    "<br1><font color=B09878>Доступен с " + buff.getLowerLevel() + " уровня</font>" +
                    "</td>" +
                    "<td width=120></td>" +
                    "</tr>" +
                    "</table>" +
                    "<table width=280 bgcolor=000000>" +
                    "<tr>" +
                    "<td width=115><font color=A3A0A3>" + buff.getDesc() + "</font></td><td width=1></td>" +
                    "</tr>" +
                    "</table>" +
                    "<img src=L2UI.SquareGray width=280 height=1>");
            item_in_page++;
        }

        sb.append("<img src=L2UI.SquareGray width=280 height=1>");
        sb.append("<table width=280 bgcolor=000000><tr>");
        sb.append("<td width=100></td>");
        sb.append("<td align=center width=30>");
        sb.append("<table width=1 height=2 bgcolor=000000></table>");
        sb.append("<button action=\"bypass -h Quest NewbieHelper AboutBuff");
        sb.append(" ");
        sb.append(page - (page > 1 ? 1 : 0));
        sb.append("\" width=16 height=16 back=L2UI_ch3.prev1_over fore=L2UI_ch3.prev1>");
        sb.append("</td>");
        sb.append("<td align=center width=100>Page ");
        sb.append(page);
        sb.append("</td>");
        sb.append("<td align=center width=30>");
        sb.append("<table width=1 height=2 bgcolor=000000></table>");
        sb.append("<button action=\"bypass -h Quest NewbieHelper AboutBuff");
        sb.append(" ");
        sb.append(page + (has_more ? 1 : 0));
        sb.append("\" width=16 height=16 back=L2UI_ch3.next1_over fore=L2UI_ch3.next1>");
        sb.append("</td>");
        sb.append("<td width=100></td>");
        sb.append("</tr></table>");
        sb.append("<img src=L2UI.SquareGray width=280 height=1>");

        html.setFile("data/html/script/feature/NewbieHelper/guide_for_newbie006.htm");
        html.replace("%list%", sb.toString());
        player.sendPacket(html);
    }

    public static NewbieCommonBuffData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final NewbieCommonBuffData INSTANCE = new NewbieCommonBuffData();
    }
}