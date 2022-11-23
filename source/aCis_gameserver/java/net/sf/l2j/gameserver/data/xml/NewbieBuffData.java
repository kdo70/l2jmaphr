package net.sf.l2j.gameserver.data.xml;

import net.sf.l2j.Config;
import net.sf.l2j.commons.data.StatSet;
import net.sf.l2j.commons.data.xml.IXmlReader;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.gameserver.enums.actors.ClassId;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.holder.NewbieBuffHolder;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.MagicSkillUse;
import net.sf.l2j.gameserver.skills.L2Skill;
import net.sf.l2j.gameserver.taskmanager.GameTimeTaskManager;
import org.w3c.dom.Document;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class loads and store {@link NewbieBuffHolder} into a {@link List}.
 */
public class NewbieBuffData implements IXmlReader {
    private final List<NewbieBuffHolder> _buffs = new ArrayList<>();

    private int _magicLowestLevel = 100;
    private int _physicLowestLevel = 100;
    private int _magicUpperLevel = 100;
    private int _physicUpperLevel = 100;
    private int _priceId;
    private int _priceCount;

    protected NewbieBuffData() {
        load();
    }

    @Override
    public void load() {
        parseFile("./data/xml/newbieBuffs.xml");
        LOGGER.info("Loaded {} newbie buffs.", _buffs.size());
    }

    @Override
    public void parseDocument(Document doc, Path path) {
        forEach(doc, "list", listNode -> forEach(listNode, "buff", buffNode ->
        {
            final StatSet set = parseAttributes(buffNode);
            final int lowerLevel = set.getInteger("lowerLevel");
            final int upperLevel = set.getInteger("upperLevel");
            _priceId = set.getInteger("priceId");
            _priceCount += set.getInteger("priceCount");
            if (set.getBool("isMagicClass")) {
                if (lowerLevel < _magicLowestLevel)
                    _magicLowestLevel = lowerLevel;
                if (upperLevel > _magicUpperLevel)
                    _magicUpperLevel = upperLevel;
            } else {
                if (lowerLevel < _physicLowestLevel)
                    _physicLowestLevel = lowerLevel;
                if (upperLevel < _physicUpperLevel)
                    _physicUpperLevel = upperLevel;
            }
            _buffs.add(new NewbieBuffHolder(set));
        }));
    }

    /**
     * @param isMage : If true, return buffs list associated to mage classes.
     * @param level  : Filter the list by the given level.
     * @return The {@link List} of valid {@link NewbieBuffHolder}s for the given class type and level.
     */
    public List<NewbieBuffHolder> getValidBuffs(boolean isMage, int level) {
        return _buffs.stream().filter(b -> b.isMagicClassBuff() == isMage && level >= b.getLowerLevel() && level <= b.getUpperLevel()).collect(Collectors.toList());
    }

    public int getLowestBuffLevel(boolean isMage) {
        return (isMage) ? _magicLowestLevel : _physicLowestLevel;
    }

    public int getUpperBuffLevel(boolean isMage) {
        return (isMage) ? _magicUpperLevel : _physicUpperLevel;
    }

    public String supportMagic(Npc npc, Player player) {
        // Prevent a cursed weapon wielder of being buffed.
        if (player.isCursedWeaponEquipped())
            return null;

        // Orc Mage and Orc Shaman should receive fighter buffs since IL, although they are mage classes.
        final boolean isMage = player.isMageClass()
                && player.getClassId() != ClassId.ORC_MYSTIC
                && player.getClassId() != ClassId.ORC_SHAMAN;

        final int playerLevel = player.getStatus().getLevel();

        final boolean lowerLevel = player.getStatus().getLevel() < getLowestBuffLevel(isMage) || player.isSubClassActive();
        final boolean upperLevel = player.getStatus().getLevel() > getUpperBuffLevel(isMage) || player.isSubClassActive();

        // If the player is too low level, display a message and return.
        if (lowerLevel) {
            return "guide_for_newbie002.htm";
        }
        // If the player is too high level, display a message and return.
        if (upperLevel) {
            return "guide_for_newbie003.htm";
        }
        boolean needPayment = !Config.FREE_BUFFER && player.getStatus().getLevel() > Config.FREE_BUFFER_LVL;

        if (needPayment && !player.destroyItemByItemId("NewbieBuff", _priceId, calculatePrice(player), npc, true)) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return null;
        }
        player.broadcastPacket(new MagicSkillUse(npc, player, 1036, 1, 1000, 0));
        ThreadPool.schedule(() -> setNewbieEffects(npc, player, isMage, playerLevel), 1000);
        NewbieCommonBuffData.getInstance().getList(npc, player);
        return null;
    }

    public int calculatePrice(Player player) {
        int calculatedPrice = _priceCount;
        calculatedPrice *= player.getStatus().getLevel();
        float currentHour = GameTimeTaskManager.getInstance().getGameHour();
        currentHour = 1 + currentHour / 100;
        calculatedPrice *= currentHour;
        return calculatedPrice;
    }

    public void setNewbieEffects(Npc npc, Player player, boolean isMage, int playerLevel) {

        for (NewbieBuffHolder buff : NewbieBuffData.getInstance().getValidBuffs(isMage, playerLevel)) {
            final L2Skill skill = buff.getSkill();
            skill.getEffects(npc, player);
        }
    }

    public static NewbieBuffData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final NewbieBuffData INSTANCE = new NewbieBuffData();
    }
}