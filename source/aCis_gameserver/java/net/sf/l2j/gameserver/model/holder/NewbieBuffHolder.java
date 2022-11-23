package net.sf.l2j.gameserver.model.holder;

import net.sf.l2j.commons.data.StatSet;

/**
 * A container used by Newbie Buffers. Those are beneficial magic effects launched on newbie players in order to help them in their Lineage 2 adventures.<br>
 * <br>
 * Those buffs got level limitation, and are class based (fighter or mage type).
 */
public class NewbieBuffHolder extends IntIntHolder
{
	private final int _lowerLevel;
	private final int _upperLevel;
	private final boolean _isMagicClass;
	private final int _priceId;
	private final int _priceCount;

	public NewbieBuffHolder(StatSet set)
	{
		super(set.getInteger("skillId"), set.getInteger("skillLevel"));
		
		_lowerLevel = set.getInteger("lowerLevel");
		_upperLevel = set.getInteger("upperLevel");
		_isMagicClass = set.getBool("isMagicClass");
		_priceId = set.getInteger("priceId");
		_priceCount = set.getInteger("priceCount");
	}
	
	/**
	 * @return the lower level that the player must achieve in order to obtain this buff.
	 */
	public int getLowerLevel()
	{
		return _lowerLevel;
	}
	
	/**
	 * @return the upper level that the player mustn't exceed in order to obtain this buff.
	 */
	public int getUpperLevel()
	{
		return _upperLevel;
	}
	
	/**
	 * @return false if it's a fighter buff, true if it's a magic buff.
	 */
	public boolean isMagicClassBuff()
	{
		return _isMagicClass;
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
}