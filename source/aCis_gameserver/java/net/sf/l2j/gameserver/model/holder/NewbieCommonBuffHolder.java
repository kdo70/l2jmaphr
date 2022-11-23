package net.sf.l2j.gameserver.model.holder;

import net.sf.l2j.commons.data.StatSet;

/**
 * The type Newbie common buff holder.
 */
public class NewbieCommonBuffHolder extends IntIntHolder {

    private final int _lowerLevel;
    private final int _upperLevel;
    private final String _icon;
    private final String _desc;
    private final int _priceId;
    private final int _priceCount;
    private final boolean _onlyNight;

    /**
     * Instantiates a new Newbie common buff holder.
     * @param set the set
     */
    public NewbieCommonBuffHolder(StatSet set) {
        super(set.getInteger("skillId"), set.getInteger("skillLevel"));

        _lowerLevel = set.getInteger("lowerLevel");
        _upperLevel = set.getInteger("upperLevel");
        _icon = set.getString("icon");
        _desc = set.getString("desc");
        _priceId = set.getInteger("priceId");
        _priceCount = set.getInteger("priceCount");
        _onlyNight = set.getBool("onlyNight");
    }

    /**
     * Gets lower level.
     * @return the lower level
     */
    public int getLowerLevel() {
        return _lowerLevel;
    }

    /**
     * Gets upper level.
     * @return the upper level
     */
    public int getUpperLevel() {
        return _upperLevel;
    }

    /**
     * Gets icon.
     * @return the icon
     */
    public String getIcon() {
        return _icon;
    }

    /**
     * Gets desc.
     * @return the desc
     */
    public String getDesc() {
        return _desc;
    }

    /**
     * Gets price id.
     * @return the price id
     */
    public Integer getPriceId() {
        return _priceId;
    }

    /**
     * Gets price count.
     * @return the price count
     */
    public int getPriceCount() {
        return _priceCount;
    }

    /**
     * Is only night boolean.
     * @return the boolean
     */
    public boolean isOnlyNight() {
        return _onlyNight;
    }
}