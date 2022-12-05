package net.sf.l2j.gameserver.model.actor.instance;

import net.sf.l2j.Config;
import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.commons.lang.StringUtil;
import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.gameserver.skills.L2Skill;

import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.idfactory.IdFactory;
import net.sf.l2j.gameserver.data.SkillTable.FrequentSkill;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.MagicSkillUse;
import net.sf.l2j.gameserver.network.serverpackets.SpecialCamera;

/**
 * Event: "Tree of life"
 */
public class TreeOfLife extends Folk {
	/**
	 * Log
	 */
	private static final CLogger LOGGER = new CLogger(TreeOfLife.class.getName());

	/**
	 * Visual effect skill identity
	 */
	public static final int SKILL_ID = Config.TREE_SKILL_ID;

	/**
	 * Visual effect skill hit time
	 */
	public static final int SKILL_HIT_TIME = Config.TREE_HIT_TIME;

	/**
	 * Tree of life task delay
	 */
	public static final int TASK_DELAY = Config.TREE_TASK_DELAY;

	/**
	 * AI task
	 */
	private ScheduledFuture<?> _aiTask;

	/**
	 * Instantiates a new Tree of life.
	 *
	 * @param objectId the object id
	 * @param template the template
	 */
	public TreeOfLife(int objectId, NpcTemplate template) {
		super(objectId, template);
		_aiTask = ThreadPool.scheduleAtFixedRate(() -> {
			this.broadcastPacket(new MagicSkillUse(this, this,
					SKILL_ID, 1, SKILL_HIT_TIME, 0));

			for (Player player : getKnownTypeInRadius(Player.class, Config.TREE_RADIUS)) {
				boolean isReward = false;

				if (isNeedEffectReward(player)) {
					final L2Skill effect = FrequentSkill.TREE_EFFECT.getSkill();
					effect.getEffects(player, player);

					player.broadcastPacket(new MagicSkillUse(player, player,
							2242, 1, SKILL_HIT_TIME, 0));
				}

				if (isNeedXpSpReward(player)) {
					int xp = Rnd.get(Config.TREE_XP_MIN, Config.TREE_XP_MAX);
					int sp = Rnd.get(Config.TREE_SP_MIN, Config.TREE_SP_MAX);

					player.addExpAndSp(getRewardCount(xp, player), getRewardCount(sp, player));
					isReward = true;
				}

				if (isNeedItemReward(player)) {
					final ItemInstance item = new ItemInstance(
							IdFactory.getInstance().getNextId(),
							Config.TREE_ITEM_ID);

					int itemCount = Rnd.get(Config.TREE_ITEM_COUNT_MIN, Config.TREE_ITEM_COUNT_MAX);
					item.setCount(getRewardCount(itemCount, player));
					player.addItem("TreeOfLife", item, null, true);
					isReward = true;
				}

				if (isReward) {
					player.broadcastPacket(new MagicSkillUse(player, player,
							SKILL_ID, 1, SKILL_HIT_TIME, 0));
				}
			}
		}, TASK_DELAY, TASK_DELAY);
	}

	/**
	 * Gets reward count.
	 *
	 * @param value  the value
	 * @param player the player
	 * @return the reward count
	 */
	public int getRewardCount(int value, Player player) {
		if (!Config.TREE_REWARD_MUL_LVL) {
			return value;
		}
		if (Rnd.chance(1)) {
			value = value * 2;

			player.sendMessage("Вы ощущаете внимание древа жизни, " +
					"оно заботится о вас, и дарует в двое больше поддержки");

			player.sendPacket(new SpecialCamera(this.getObjectId(), 800, 180,
					-1, 15000, 15000, 0, 0, 1, 0));
		}

		int rewardCount = value * player.getStatus().getLevel();

		if (Config.TREE_DEBUG) {
			StringUtil.printSection("The tree of life - getRewardCount");
			LOGGER.info("getRewardCount: " + rewardCount);
		}
		return rewardCount;
	}

	/**
	 * Is need effect reward boolean.
	 *
	 * @param player the player
	 * @return the boolean
	 */
	public boolean isNeedEffectReward(Player player) {
		boolean isNeedEffectReward = Config.TREE_EFFECT_REWARD_ENABLED
				&& player.getFirstEffect(Config.TREE_EFFECT_ID) == null
				&& Rnd.chance(Config.TREE_EFFECT_REWARD_CHANCE)
				&& player.getStatus().getLevel() >= Config.TREE_EFFECT_LVL_MIN
				&& player.getStatus().getLevel() <= Config.TREE_EFFECT_LVL_MAX;

		if (isNeedEffectReward && Config.TREE_EFFECT_NEED_SITTING) {
			isNeedEffectReward = player.isSitting();
		}

		if (Config.TREE_DEBUG) {
			StringUtil.printSection("The tree of life - isNeedEffectReward");
			LOGGER.info("isNeedEffectReward: " + isNeedEffectReward);
			LOGGER.info("Condition 1: " + Config.TREE_EFFECT_REWARD_ENABLED);
			LOGGER.info("Condition 2: " + (player.getFirstEffect(Config.TREE_EFFECT_ID) == null));
			LOGGER.info("Condition 3: " + Rnd.chance(Config.TREE_EFFECT_REWARD_CHANCE));
			LOGGER.info("Condition 4: " + (player.getStatus().getLevel() >= Config.TREE_EFFECT_LVL_MIN));
			LOGGER.info("Condition 5: " + (player.getStatus().getLevel() <= Config.TREE_EFFECT_LVL_MAX));
			LOGGER.info("Condition 6: " + player.isSitting());
		}
		return isNeedEffectReward;
	}

	/**
	 * Is need xp sp reward boolean.
	 *
	 * @param player the player
	 * @return the boolean
	 */
	public boolean isNeedXpSpReward(Player player) {
		boolean isNeedXpSpReward = Config.TREE_XP_SP_REWARD_ENABLED
				&& Rnd.chance(Config.TREE_XP_SP_REWARD_CHANCE)
				&& player.getStatus().getLevel() >= Config.TREE_XP_SP_LVL_MIN
				&& player.getStatus().getLevel() <= Config.TREE_XP_SP_LVL_MAX;

		if (isNeedXpSpReward && Config.TREE_XP_SP_NEED_SITTING) {
			isNeedXpSpReward = player.isSitting();
		}

		if (Config.TREE_DEBUG) {
			StringUtil.printSection("The tree of life - isNeedXpSpReward");
			LOGGER.info("isNeedXpSpReward: " + isNeedXpSpReward);
			LOGGER.info("Condition 1: " + Config.TREE_XP_SP_REWARD_ENABLED);
			LOGGER.info("Condition 2: " + Rnd.chance(Config.TREE_XP_SP_REWARD_CHANCE));
			LOGGER.info("Condition 3: " + (player.getStatus().getLevel() >= Config.TREE_XP_SP_LVL_MIN));
			LOGGER.info("Condition 4: " + (player.getStatus().getLevel() <= Config.TREE_XP_SP_LVL_MAX));
			LOGGER.info("Condition 5: " + player.isSitting());
		}
		return isNeedXpSpReward;
	}

	/**
	 * Is need item reward boolean.
	 *
	 * @param player the player
	 * @return the boolean
	 */
	public boolean isNeedItemReward(Player player) {
		boolean isNeedItemReward = Config.TREE_ITEM_REWARD_ENABLED
				&& Rnd.chance(Config.TREE_ITEM_REWARD_CHANCE)
				&& player.getStatus().getLevel() >= Config.TREE_ITEM_LVL_MIN
				&& player.getStatus().getLevel() <= Config.TREE_ITEM_LVL_MAX;

		if (isNeedItemReward && Config.TREE_ITEM_NEED_SITTING) {
			isNeedItemReward = player.isSitting();
		}

		if (Config.TREE_DEBUG) {
			StringUtil.printSection("The tree of life - isNeedItemReward");
			LOGGER.info("isNeedItemReward: " + isNeedItemReward);
			LOGGER.info("Condition 1: " + Config.TREE_ITEM_REWARD_ENABLED);
			LOGGER.info("Condition 2: " + Rnd.chance(Config.TREE_ITEM_REWARD_CHANCE));
			LOGGER.info("Condition 3: " + (player.getStatus().getLevel() >= Config.TREE_ITEM_LVL_MIN));
			LOGGER.info("Condition 4: " + (player.getStatus().getLevel() <= Config.TREE_ITEM_LVL_MAX));
			LOGGER.info("Condition 5: " + player.isSitting());
		}
		return isNeedItemReward;
	}

	@Override
	public void deleteMe() {
		if (_aiTask != null) {
			_aiTask.cancel(true);
			_aiTask = null;
		}
		super.deleteMe();
	}

	@Override
	public void onAction(Player player, boolean isCtrlPressed, boolean isShiftPressed) {
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
}