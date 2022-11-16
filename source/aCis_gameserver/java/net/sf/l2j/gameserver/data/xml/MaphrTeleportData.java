package net.sf.l2j.gameserver.data.xml;

import net.sf.l2j.Config;
import net.sf.l2j.commons.data.xml.IXmlReader;
import net.sf.l2j.commons.lang.StringUtil;
import net.sf.l2j.gameserver.enums.TeleportType;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Teleport;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * @param type   : The {@link TeleportType} to filter.
     */
    public void showTeleportList(Player player, Npc npc, TeleportType type, int id) {
        System.out.println("showTeleportList");
        final NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());

        final StringBuilder sb = new StringBuilder();
        StringUtil.append(sb, "<table width=280 bgcolor=000000><tr><td width=150>Location</td><td width=60>Cost</td><td width=80>Item</td></tr></table><img src=L2UI.SquareGray width=280 height=1>\n");


        final List<Teleport> teleports = _teleports.get(id);
        if (teleports != null) {
            for (int index = 0; index < teleports.size(); index++) {
                final Teleport teleport = teleports.get(index);
                if (teleport == null || type != teleport.getType()) continue;

                int priceCount = 0;
                if (!Config.FREE_TELEPORT) {
                    priceCount = teleport.getCalculatedPriceCount(player);
                }
                StringUtil.append(sb, "<table width=280 bgcolor=000000><tr><td width=150><a action=\"bypass -h npc_%objectId%_teleport ", index, "\" msg=\"811;", teleport.getDesc(), "\">", teleport.getDesc(), "</a></td><td width=60><font color=A3A0A3>", priceCount, "</font></td><td width=80><font color=B09878>", ItemData.getInstance().getTemplate(teleport.getPriceId()).getName(), "</font></td></tr></table><img src=L2UI.SquareGray width=280 height=1>");
            }

            if (teleports.size() < 15) {
                StringUtil.append(sb, "<img height=", 20 * (15 - teleports.size()), ">");
            }
        }

        html.setFile("data/html/gatekeeper/list.htm");
        html.replace("%list%", sb.toString());
        html.replace("%objectId%", npc.getObjectId());
        html.replace("%npcname%", npc.getName());

        player.sendPacket(html);
    }

    public static MaphrTeleportData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final MaphrTeleportData INSTANCE = new MaphrTeleportData();
    }
}