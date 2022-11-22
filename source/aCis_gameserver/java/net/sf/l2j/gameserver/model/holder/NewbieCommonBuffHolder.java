package net.sf.l2j.gameserver.model.holder;

import net.sf.l2j.commons.data.StatSet;

/**
 * The type Newbie common buff holder.
 */
public class NewbieCommonBuffHolder extends IntIntHolder {
    private int _lowerLevel;
    private int _upperLevel;
    private String _icon;
    private String _desc;

    /**
     * Instantiates a new Newbie common buff holder.
     *
     * @param set the set
     */
    public NewbieCommonBuffHolder(StatSet set) {
        super(set.getInteger("skillId"), set.getInteger("skillLevel"));

        _lowerLevel = set.getInteger("lowerLevel");
        _upperLevel = set.getInteger("upperLevel");
        _icon = set.getString("icon");
        _desc = set.getString("desc");
    }

    /**
     * Gets lower level.
     *
     * @return the lower level
     */
    public int getLowerLevel() {
        return _lowerLevel;
    }

    /**
     * Gets upper level.
     *
     * @return the upper level
     */
    public int getUpperLevel() {
        return _upperLevel;
    }

    public String getIcon() {
        return _icon;
    }

    public String getDesc() {
        return _desc;
    }
}