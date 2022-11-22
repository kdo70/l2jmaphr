package net.sf.l2j.gameserver.data.xml;

import net.sf.l2j.commons.data.StatSet;
import net.sf.l2j.commons.data.xml.IXmlReader;
import net.sf.l2j.commons.lang.StringUtil;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.gameserver.enums.actors.ClassId;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.holder.NewbieCommonBuffHolder;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.MagicSkillUse;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.skills.L2Skill;
import org.w3c.dom.Document;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The type Newbie common buff data.
 */
public class NewbieCommonBuffData implements IXmlReader {
    private final List<NewbieCommonBuffHolder> _buffs = new ArrayList<>();

    /**
     * Instantiates a new Newbie common buff data.
     */
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

    /**
     * Gets list.
     *
     * @param npc    the npc
     * @param player the player
     */
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
                    if (player.getStatus().getLevel() < buff.getLowerLevel() || player.getStatus().getLevel() >= buff.getUpperLevel()) {
                        continue;
                    }
                    StringUtil.append(sb, "<table width=280 bgcolor=000000><tr><td width=60><img src=\"", buff.getIcon(), "\" width=32 height=16></td><td width=150><font color=B09878>", buff.getSkill().getName(), "</font></td><td width=60><font color=A3A0A3>", buff.getSkill().getLevel(), "</font></td><td width=60><a action=\"bypass -h Quest NewbieHelper GetBuff ", index, "\">Get Magic</a></td></tr></table><img src=L2UI.SquareGray width=280 height=1>");
                }

                String linkText;
                if (isMage) {
                    linkText = "Get magic for magician";
                } else {
                    linkText = "Get magic for warrior";
                }

                html.setFile("data/html/script/feature/NewbieHelper/guide_for_newbie005.htm");
                html.replace("%list%", sb.toString());
                html.replace("%linkText%", linkText);
            }
            html.replace("%npcName%", npc.getName());
            player.sendPacket(html);
        } catch (final Exception e) {
            System.out.println("Class: NewbieCommonBuffData Method: getList Message:" + e.getMessage());
            player.sendPacket(ActionFailed.STATIC_PACKET);
        }
    }

    /**
     * Gets buff.
     *
     * @param npc    the npc
     * @param player the player
     * @param index  the index
     */
    public void getBuff(Npc npc, Player player, int index) {
        final NewbieCommonBuffHolder buff = _buffs.get(index);

        final boolean lowerLevel = player.getStatus().getLevel() < buff.getLowerLevel();
        final boolean upperLevel = player.getStatus().getLevel() > buff.getUpperLevel();

        if (lowerLevel || upperLevel) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        int visualSkillId = buff.getSkill().getId();
        int skillLvl = buff.getSkill().getLevel();

        if (visualSkillId == 4699) {
            visualSkillId = 4700;
        }
        if (visualSkillId == 4702) {
            visualSkillId = 4703;
        }

        MagicSkillUse packet = new MagicSkillUse(npc, player, visualSkillId, skillLvl, 1000, 0);
        player.broadcastPacket(packet);
        ThreadPool.schedule(() -> setEffect(npc, player, buff.getSkill()), 1000);

        this.getList(npc, player);
    }

    /**
     * Sets effect.
     *
     * @param npc    the npc
     * @param player the player
     * @param skill  the skill
     */
    public static void setEffect(Npc npc, Player player, L2Skill skill) {
        skill.getEffects(npc, player);
    }

    /**
     * Gets instance.
     *
     * @return the instance
     */
    public static NewbieCommonBuffData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        /**
         * The constant INSTANCE.
         */
        protected static final NewbieCommonBuffData INSTANCE = new NewbieCommonBuffData();
    }
}