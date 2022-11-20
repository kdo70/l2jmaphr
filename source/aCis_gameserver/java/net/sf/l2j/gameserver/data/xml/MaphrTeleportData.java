package net.sf.l2j.gameserver.data.xml;

import net.sf.l2j.Config;
import net.sf.l2j.commons.data.xml.IXmlReader;
import net.sf.l2j.commons.lang.StringUtil;
import net.sf.l2j.gameserver.enums.TeleportType;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.model.location.Teleport;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.taskmanager.GameTimeTaskManager;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;

import java.nio.file.Path;
import java.util.*;

/**
 * This class loads and stores {@link Teleport}s used as regular teleport positions.
 */
public class MaphrTeleportData implements IXmlReader {
    private final Map<Integer, List<Teleport>> _teleports = new HashMap<>();

    protected MaphrTeleportData() {
        load();
    }

    @Override
    public void load() {
        parseFile("./data/xml/maphr-teleports.xml");
        LOGGER.info("Loaded {} teleport positions.", _teleports.size());
    }

    @Override
    public void parseDocument(Document doc, Path path) {
        forEach(doc, "list", listNode -> forEach(listNode, "telPosList", telPosListNode -> {
            final NamedNodeMap telPosListAttrs = telPosListNode.getAttributes();
            final int id = Integer.parseInt(telPosListAttrs.getNamedItem("id").getNodeValue());

            final List<Teleport> teleports = new ArrayList<>();
            forEach(telPosListNode, "loc", locNode -> teleports.add(new Teleport(parseAttributes(locNode))));

            _teleports.put(id, teleports);
        }));
    }

    public void reload() {
        _teleports.clear();

        load();
    }

    public List<Teleport> getTeleports(int npcId) {
        return _teleports.get(npcId);
    }

    /**
     * Build and send an HTM to a {@link Player}, based on {@link Npc}'s {@link Teleport}s and {@link TeleportType}.
     *
     * @param player : The {@link Player} to test.
     * @param npc    : The {@link Npc} to test.
     */
    public void showTeleportList(Player player, Npc npc, int id, int chatId, String page) {
        final NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());

        final StringBuilder sb = new StringBuilder();
        StringUtil.append(sb, "<table width=280 bgcolor=000000><tr><td width=170>Location</td><td width=100>Point</td><td width=55>Cost</td><td width=50>Item</td></tr></table><img src=L2UI.SquareGray width=280 height=1>");


        final List<Teleport> teleports = _teleports.get(id);
        if (teleports != null) {
            int countTeleports = 0;
            for (int index = 0; index < teleports.size(); index++) {
                final Teleport teleport = teleports.get(index);

                if (teleport.getType() == TeleportType.NOBLE && !player.isNoble()) {
                    continue;
                }

                int priceCount = 0;
                if (!Config.FREE_TELEPORT) {
                    priceCount = calculatedPriceCount(player, teleport);
                }
                countTeleports++;

                StringTokenizer tokenizer = new StringTokenizer(ItemData.getInstance().getTemplate(teleport.getPriceId()).getName());
                String itemName = tokenizer.hasMoreTokens() ? tokenizer.nextToken() : "";
                StringUtil.append(sb, "<table width=280 bgcolor=000000><tr><td width=170><a action=\"bypass -h npc_%objectId%_global_teleport ", id, " ", index, "\" msg=\"811;", teleport.getDesc(), "\">", teleport.getDesc(), "</a></td><td width=100><font color=A3A0A3>", teleport.getPoint(), "</td><td width=55>", priceCount, "</font></td><td width=50><font color=B09878>", itemName, "</font></td></tr></table><img src=L2UI.SquareGray width=280 height=1>");
            }

            if (countTeleports < 13) {
                StringUtil.append(sb, "<img height=", 20 * (13 - countTeleports), ">");
            }
        }

        html.setFile("data/html/gatekeeper/list.htm");
        html.replace("%list%", sb.toString());
        html.replace("%objectId%", npc.getObjectId());
        html.replace("%chatId%", chatId);
        html.replace("%npcname%", npc.getName());
        html.replace("%" + page + "%", "color=B09878");
        System.out.println("page " + page);

        player.sendPacket(html);
    }

    public int calculatedPriceCount(Player player, Teleport teleport) {
        if (Config.FREE_TELEPORT_LVL > 0
                && !player.isSubClassActive()
                && Config.FREE_TELEPORT_LVL >= player.getStatus().getLevel()
                && teleport.getPriceId() == 57) {
            return 0;
        }

        int calculatedPrice = teleport.getPriceCount();

        //1000 distance * price
        if (teleport.getPriceId() == 57) {
            double distant = player.distance2D(new Location(teleport.getX(), teleport.getY(), teleport.getZ()));
            int distantMul = (int) distant / 1000;
            if (distantMul < 1) {
                distantMul = 1;
            }
            calculatedPrice *= distantMul;
        }

        //max 80%
        float levelMul = player.isSubClassActive() ? 80 : player.getStatus().getLevel();
        levelMul = 1 + levelMul / 100;
        calculatedPrice *= levelMul;

        //max 24%
        float currentHour = GameTimeTaskManager.getInstance().getGameHour();
        currentHour = 1 + currentHour / 100;
        calculatedPrice *= currentHour;

        return calculatedPrice;
    }

    public static MaphrTeleportData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final MaphrTeleportData INSTANCE = new MaphrTeleportData();
    }
}