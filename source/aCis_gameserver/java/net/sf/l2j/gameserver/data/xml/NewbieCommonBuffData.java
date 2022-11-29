package net.sf.l2j.gameserver.data.xml;

import net.sf.l2j.commons.data.StatSet;
import net.sf.l2j.commons.data.xml.IXmlReader;
import net.sf.l2j.gameserver.model.holder.NewbieBuffHolder;
import org.w3c.dom.Document;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The type Newbie common buff data.
 */
public class NewbieCommonBuffData implements IXmlReader {
    private final List<NewbieBuffHolder> _buffs = new ArrayList<>();

    /**
     * Instantiates a new Newbie common buff data.
     */
    protected NewbieCommonBuffData() {
        load();
    }

    @Override
    public void load() {
        parseFile("./data/xml/buffer/common.xml");
        LOGGER.info("Loaded {} newbie common buffs.", _buffs.size());
    }

    @Override
    public void parseDocument(Document doc, Path path) {
        forEach(doc, "list", listNode -> forEach(listNode, "buff", buffNode -> {
            final StatSet set = parseAttributes(buffNode);
            _buffs.add(new NewbieBuffHolder(set));
        }));
    }

    /**
     * @return the buffs
     */
    public List<NewbieBuffHolder> getBuffs() {
        return _buffs;
    }

    /**
     * @return the instance
     */
    public static NewbieCommonBuffData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final NewbieCommonBuffData INSTANCE = new NewbieCommonBuffData();
    }
}