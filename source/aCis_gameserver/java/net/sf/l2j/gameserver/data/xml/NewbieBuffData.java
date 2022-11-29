package net.sf.l2j.gameserver.data.xml;

import net.sf.l2j.commons.data.StatSet;
import net.sf.l2j.commons.data.xml.IXmlReader;
import net.sf.l2j.gameserver.model.holder.NewbieBuffHolder;
import org.w3c.dom.Document;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The type Newbie buff data.
 */
public class NewbieBuffData implements IXmlReader {

    private final List<NewbieBuffHolder> _buffs = new ArrayList<>();

    private int _magicLowestLevel = 100;

    private int _physicLowestLevel = 100;

    private int _magicUpperLevel = 100;

    private int _physicUpperLevel = 100;

    /**
     * Instantiates a new Newbie buff data.
     */
    protected NewbieBuffData() {
        load();
    }

    @Override
    public void load() {
        parseFile("./data/xml/buffer/main.xml");
        LOGGER.info("Loaded {} newbie buffs.", _buffs.size());
    }

    @Override
    public void parseDocument(Document doc, Path path) {
        forEach(doc, "list", listNode -> forEach(listNode, "buff", buffNode ->
        {
            final StatSet set = parseAttributes(buffNode);
            final int lowerLevel = set.getInteger("lowerLevel");
            final int upperLevel = set.getInteger("upperLevel");

            if (set.getBool("isMagicClass")) {

                if (lowerLevel < _magicLowestLevel) {
                    _magicLowestLevel = lowerLevel;
                }

                if (upperLevel > _magicUpperLevel) {
                    _magicUpperLevel = upperLevel;
                }

            } else {

                if (lowerLevel < _physicLowestLevel) {
                    _physicLowestLevel = lowerLevel;
                }

                if (upperLevel < _physicUpperLevel) {
                    _physicUpperLevel = upperLevel;
                }

            }
            _buffs.add(new NewbieBuffHolder(set));
        }));
    }

    /**
     * @return the valid buffs
     */
    public List<NewbieBuffHolder> getValidBuffs(boolean isMage, int level) {
        return _buffs.stream().filter(
                b -> b.isMagicClassBuff() == isMage
                        && level >= b.getLowerLevel()
                        && level <= b.getUpperLevel()
        ).collect(Collectors.toList());
    }

    /**
     * @return the valid buffs
     */
    public List<NewbieBuffHolder> getValidBuffs(boolean isMage) {
        return _buffs.stream().filter(b -> b.isMagicClassBuff() == isMage).collect(Collectors.toList());
    }

    /**
     * @return the lowest buff level
     */
    public int getLowestBuffLevel(boolean isMage) {
        return (isMage) ? _magicLowestLevel : _physicLowestLevel;
    }

    /**
     * @return the upper buff level
     */
    public int getUpperBuffLevel(boolean isMage) {
        return (isMage) ? _magicUpperLevel : _physicUpperLevel;
    }

    /**
     * @return the instance
     */
    public static NewbieBuffData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final NewbieBuffData INSTANCE = new NewbieBuffData();
    }
}