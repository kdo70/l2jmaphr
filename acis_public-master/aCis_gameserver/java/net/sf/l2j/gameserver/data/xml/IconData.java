package net.sf.l2j.gameserver.data.xml;

import java.util.Map;
import java.nio.file.Path;

import org.w3c.dom.Node;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.data.xml.IXmlReader;

import java.util.concurrent.ConcurrentHashMap;

public class IconData implements IXmlReader {

    public static final CLogger LOGGER = new CLogger(IconData.class.getName());

    private static final Map<Integer, String> _icons = new ConcurrentHashMap<>();

    public static IconData getInstance() {
        return SingletonHolder._instance;
    }

    protected IconData() {
        load();
    }

    @Override
    public void load() {
        parseFile("./data/xml/icons.xml");
        LOGGER.info("Loaded {} icons.", _icons.size());
    }

    @Override
    public void parseDocument(Document doc, Path path) {
        Node n = doc.getFirstChild();

        for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling()) {
            if (d.getNodeName().equalsIgnoreCase("icon")) {

                NamedNodeMap attrs = d.getAttributes();
                int itemId = Integer.parseInt(attrs.getNamedItem("itemId").getNodeValue());
                String iconName = attrs.getNamedItem("iconName").getNodeValue();

                _icons.put(itemId, iconName);
            }
        }

    }

    /**
     * Get an item icon by ID
     *
     * @return sting
     */
    public static String getIcon(int id) {
        if (_icons.get(id) == null) return "icon.NOIMAGE";
        return _icons.get(id);
    }

    private static class SingletonHolder {
        protected static final IconData _instance = new IconData();
    }
}