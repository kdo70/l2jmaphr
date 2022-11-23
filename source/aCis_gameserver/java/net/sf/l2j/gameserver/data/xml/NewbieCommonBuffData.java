package net.sf.l2j.gameserver.data.xml;

import net.sf.l2j.Config;
import net.sf.l2j.commons.data.StatSet;
import net.sf.l2j.commons.data.xml.IXmlReader;
import net.sf.l2j.commons.lang.StringUtil;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.gameserver.enums.actors.ClassId;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.holder.NewbieCommonBuffHolder;
import net.sf.l2j.gameserver.model.item.kind.Item;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.MagicSkillUse;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.skills.L2Skill;
import net.sf.l2j.gameserver.taskmanager.GameTimeTaskManager;
import org.w3c.dom.Document;

import java.nio.file.Path;
import java.text.DecimalFormat;
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
                    StringUtil.append(sb, "<table width=280 bgcolor=000000>" +
                            "<tr>" +
                            "<td width=35><img src=\"", buff.getIcon(), "\" width=32 height=16><img height=7></td>" +
                            "<td width=100>", buff.getSkill().getName(), "</td>" +
                            "<td width=40><font color=A3A0A3>", String.format(Locale.US, "%,d", calculatePrice(buff, player)), "</font></td>" +
                            "<td width=40><font color=B09878>", itemName, "</font></td>" +
                            "<td width=50><a action=\"bypass -h Quest NewbieHelper GetBuff ", index, "\">Get Magic</a></td>" +
                            "</tr>" +
                            "</table>" +
                            "<img src=L2UI.SquareGray width=280 height=1>");


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
            if (!Config.FREE_BUFFER){
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

    public static NewbieCommonBuffData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final NewbieCommonBuffData INSTANCE = new NewbieCommonBuffData();
    }
}