package net.sf.l2j.gameserver.scripting.script.feature;

import net.sf.l2j.Config;
import net.sf.l2j.commons.lang.StringUtil;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.gameserver.data.xml.ItemData;
import net.sf.l2j.gameserver.data.xml.NewbieBuffData;
import net.sf.l2j.gameserver.data.xml.NewbieCommonBuffData;
import net.sf.l2j.gameserver.enums.actors.ClassId;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.holder.NewbieBuffHolder;
import net.sf.l2j.gameserver.model.item.kind.Item;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.MagicSkillUse;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.network.serverpackets.PlaySound;
import net.sf.l2j.gameserver.scripting.Quest;
import net.sf.l2j.gameserver.scripting.QuestState;
import net.sf.l2j.gameserver.skills.L2Skill;
import net.sf.l2j.gameserver.taskmanager.GameTimeTaskManager;

import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

public class NewbieHelper extends Quest {
    private static final String QUEST_NAME = "NewbieHelper";

    public NewbieHelper() {
        super(-1, "feature");

        addTalkId(30009, 30019, 30131, 30400, 30530,
                30575, 30008, 30017, 30129, 30370, 30528,
                30573, 30598, 30599, 30600, 30601, 30602,
                31076, 31077
        );

        addFirstTalkId(30009, 30019, 30131, 30400,
                30530, 30575, 30008, 30017, 30129, 30370,
                30528, 30573, 30598, 30599, 30600, 30601,
                30602, 31076, 31077
        );
    }

    @Override
    public String onAdvEvent(String event, Npc npc, Player player) {
        if (player.getTarget() != npc) {
            return actionFailed(player);
        }

        QuestState questState = player.getQuestList().getQuestState(QUEST_NAME);

        if (questState == null) {
            return actionFailed(player);
        }

        if (event.startsWith("Index")) {
            return onFirstTalk(npc, player);
        }

        if (event.startsWith("SupportMagic")) {
            return supportMagic(player, npc);
        }

        if (event.startsWith("GetMagic")) {
            return getBuff(npc, player, getIndex(event));
        }

        if (event.startsWith("AboutMagic")) {
            return getDescriptionList(npc, player, getIndex(event));
        }

        return actionFailed(player);
    }

    public String onFirstTalk(Npc npc, Player player) {
        final NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
        html.setFile("data/html/script/feature/NewbieHelper/" + npc.getNpcId() + ".htm");
        html.replace("%npcName%", npc.getName());
        html.replace("%price%", String.format(Locale.US, "%,d", calculatePrice(player)));
        player.sendPacket(html);
        return null;
    }

    private String supportMagic(Player player, Npc npc) {
        final boolean playerIsMage = playerIsMage(player);
        final int playerLevel = player.getStatus().getLevel();

        if (playerIsLowerLevel(player, playerIsMage) || playerIsUpperLevel(player, playerIsMage)) {
            return playerIsLowerLevel(player, playerIsMage) ? "guide_for_newbie002.htm" : "guide_for_newbie003.htm";
        }

        boolean needPayment = !Config.FREE_BUFFER && player.getStatus().getLevel() > Config.FREE_BUFFER_LVL;

        if (needPayment && isDestroyPlayerItem(player, npc, 57, calculatePrice(player))) {
            return actionFailed(player);
        }

        player.broadcastPacket(new MagicSkillUse(npc, player, 1036, 1, 1000, 0));
        ThreadPool.schedule(() -> setEffects(npc, player, playerIsMage, playerLevel), 1000);

        return getList(npc, player);
    }

    public String getList(Npc npc, Player player) {
        final boolean playerIsMage = playerIsMage(player);

        if (playerIsLowerLevel(player, playerIsMage) || playerIsUpperLevel(player, playerIsMage)) {
            return playerIsLowerLevel(player, playerIsMage) ? "guide_for_newbie002.htm" : "guide_for_newbie003.htm";
        }

        final NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
        final StringBuilder sb = new StringBuilder();

        List<NewbieBuffHolder> buffs = NewbieCommonBuffData.getInstance().getBuffs();

        int visibleCount = 0;
        for (int index = 0; index < buffs.size(); index++) {
            final NewbieBuffHolder buff = buffs.get(index);

            if (player.getStatus().getLevel() < buff.getLowerLevel()
                    || player.getStatus().getLevel() > buff.getUpperLevel()
                    || buff.isOnlyNight() && !GameTimeTaskManager.getInstance().isNight()) {
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
                    "<td width=60><a action=\"bypass -h Quest NewbieHelper GetMagic ", index, "\">Применить</a></td>" +
                    "</tr>" +
                    "</table>" +
                    "<img src=L2UI.SquareGray width=280 height=1>");
            visibleCount++;
        }

        StringUtil.append(sb, "<table width=285 bgcolor=000000>" +
                "<tr>" +
                "<td width=285 align=\"center\"><img height=2><a action=\"bypass -h Quest NewbieHelper AboutMagic\">Узнать больше о магической поддержке</a><img height=2> </td>" +
                "</tr>" +
                "</table><table width=285 bgcolor=000000><tr><td width=285 align=\"center\"><img height=2><a action=\"bypass -h Quest NewbieHelper Index\">Вернуться назад</a><img height=2></td></tr>" +
                "</table>");

        if (visibleCount < 13) {
            StringUtil.append(sb, "<img height=", 26 * (13 - visibleCount), ">");
        }

        html.setFile("data/html/script/feature/NewbieHelper/guide_for_newbie005.htm");
        html.replace("%list%", sb.toString());
        html.replace("%npcName%", npc.getName());
        html.replace("%objectId%", npc.getObjectId());
        player.sendPacket(html);

        return null;
    }

    public String getBuff(Npc npc, Player player, int index) {
        final boolean playerIsMage = playerIsMage(player);

        if (playerIsLowerLevel(player, playerIsMage) || playerIsUpperLevel(player, playerIsMage)) {
            return actionFailed(player);
        }

        List<NewbieBuffHolder> buffs = NewbieCommonBuffData.getInstance().getBuffs();

        final NewbieBuffHolder buff = buffs.get(index);
        int skillLvl = buff.getSkill().getLevel();

        int visualSkillId = buff.getSkill().getId();
        if (visualSkillId == 4699 || visualSkillId == 4702) {
            visualSkillId = visualSkillId == 4699 ? 4700 : 4703;
        }

        boolean needPayment = !Config.FREE_BUFFER && player.getStatus().getLevel() > Config.FREE_BUFFER_LVL;

        if (needPayment && isDestroyPlayerItem(player, npc, buff.getPriceId(), calculatePrice(buff, player))) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return null;
        }

        if (visualSkillId == 1323) {
            player.sendPacket(new PlaySound(2, "newbie_blessing", player));
        }

        MagicSkillUse packet = new MagicSkillUse(npc, player, visualSkillId, skillLvl, 1000, 0);
        player.broadcastPacket(packet);

        ThreadPool.schedule(() -> setEffect(npc, player, buff.getSkill()), 1000);

        this.getList(npc, player);
        return null;
    }

    public String getDescriptionList(Npc npc, Player player, int page) {
        final NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
        final StringBuilder sb = new StringBuilder();

        int currentPage = 1;
        int iteration = 0;
        int item_per_list = 8;
        int item_in_page = 0;
        boolean has_more = false;

        final boolean isMage = playerIsMage(player);
        final List<NewbieBuffHolder> _buffs = NewbieBuffData.getInstance().getValidBuffs(isMage);
        _buffs.addAll(NewbieCommonBuffData.getInstance().getBuffs());

        for (NewbieBuffHolder buff : _buffs) {
            final L2Skill skill = buff.getSkill();

            if (currentPage != page) {
                iteration++;

                if (iteration != item_per_list) {
                    continue;
                }

                currentPage++;
                iteration = 0;
                continue;
            }

            if (item_in_page == item_per_list) {
                has_more = true;
                break;
            }

            sb.append("<table width=280 bgcolor=000000>");
            sb.append("<tr><td width=35><img src=\"");
            sb.append(buff.getIcon());
            sb.append("\" width=32 height=32><img height=5></td><td width=170>");
            sb.append(skill.getName());
            sb.append("<font color=A3A0A3> Lv.</font> <font color=B09878>");
            sb.append(skill.getLevel());
            sb.append("</font><br1><font color=B09878>");
            if (buff.isOnlyNight()) {
                sb.append("Доступно только ночью");
            } else {
                sb.append("Доступно с ");
                sb.append(buff.getLowerLevel());
                sb.append(" уровня");
            }
            sb.append("</font></td><td width=70></td></tr></table><table width=280 bgcolor=000000><tr><td width=224><font color=A3A0A3>");
            sb.append(buff.getDesc());
            sb.append("</font></td><td width=1></td></tr></table><img src=L2UI.SquareGray width=280 height=1>");
            item_in_page++;
        }

        sb.append("<table width=280 bgcolor=000000><tr>");
        sb.append("<td width=100></td>");
        sb.append("<td align=center width=30>");
        sb.append("<table width=1 height=2 bgcolor=000000></table>");
        sb.append("<button action=\"bypass -h Quest NewbieHelper AboutMagic ");
        sb.append(page - (page > 1 ? 1 : 0));
        sb.append("\" width=16 height=16 back=L2UI_ch3.prev1_over fore=L2UI_ch3.prev1>");
        sb.append("</td>");
        sb.append("<td align=center width=100>Page ");
        sb.append(page);
        sb.append("</td>");
        sb.append("<td align=center width=30>");
        sb.append("<table width=1 height=2 bgcolor=000000></table>");
        sb.append("<button action=\"bypass -h Quest NewbieHelper AboutMagic ");
        sb.append(page + (has_more ? 1 : 0));
        sb.append("\" width=16 height=16 back=L2UI_ch3.next1_over fore=L2UI_ch3.next1>");
        sb.append("</td>");
        sb.append("<td width=100></td>");
        sb.append("</tr></table>");
        sb.append("<img src=L2UI.SquareGray width=280 height=1>");

        html.setFile("data/html/script/feature/NewbieHelper/guide_for_newbie006.htm");
        html.replace("%list%", sb.toString());
        html.replace("%npcName%", npc.getName());
        html.replace("%objectId%", npc.getObjectId());
        player.sendPacket(html);
        return null;
    }

    protected boolean isDestroyPlayerItem(Player player, Npc npc, int itemId, int count) {
        return !player.destroyItemByItemId("NewbieBuff", itemId, count, npc, true);
    }

    public static void setEffect(Npc npc, Player player, L2Skill skill) {
        skill.getEffects(npc, player);
    }

    private void setEffects(Npc npc, Player player, boolean isMage, int playerLevel) {
        for (NewbieBuffHolder buff : NewbieBuffData.getInstance().getValidBuffs(isMage, playerLevel)) {
            setEffect(npc, player, buff.getSkill());
        }
    }

    private int calculatePrice(Player player) {
        int calculatedPrice = Config.BUFF_PRICE_PER_UNIT;
        calculatedPrice *= player.getStatus().getLevel();
        float currentHour = GameTimeTaskManager.getInstance().getGameHour();
        currentHour = 1 + currentHour / 100;
        calculatedPrice *= currentHour;
        return calculatedPrice;
    }

    public int calculatePrice(NewbieBuffHolder buff, Player player) {
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

    private boolean playerIsLowerLevel(Player player, boolean playerIsMage) {
        return player.getStatus().getLevel() < NewbieBuffData.getInstance().getLowestBuffLevel(playerIsMage)
                || player.isSubClassActive();
    }

    private boolean playerIsUpperLevel(Player player, boolean playerIsMage) {
        return player.getStatus().getLevel() > NewbieBuffData.getInstance().getUpperBuffLevel(playerIsMage)
                || player.isSubClassActive();
    }

    private boolean playerIsMage(Player player) {
        return player.isMageClass()
                && player.getClassId() != ClassId.ORC_MYSTIC
                && player.getClassId() != ClassId.ORC_SHAMAN;
    }

    public int getIndex(String command) {
        final StringTokenizer string = new StringTokenizer(command, " ");
        string.nextToken();
        return string.hasMoreTokens() ? Integer.parseInt(string.nextToken()) : 1;
    }

    private String actionFailed(Player player) {
        player.sendPacket(ActionFailed.STATIC_PACKET);
        return null;
    }
}
