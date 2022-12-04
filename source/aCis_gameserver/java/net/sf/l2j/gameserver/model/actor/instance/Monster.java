package net.sf.l2j.gameserver.model.actor.instance;

import net.sf.l2j.Config;
import net.sf.l2j.commons.math.MathUtil;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.gameserver.data.manager.CursedWeaponManager;
import net.sf.l2j.gameserver.data.xml.HerbDropData;
import net.sf.l2j.gameserver.enums.BossInfoType;
import net.sf.l2j.gameserver.enums.skills.Stats;
import net.sf.l2j.gameserver.geoengine.GeoEngine;
import net.sf.l2j.gameserver.model.actor.*;
import net.sf.l2j.gameserver.model.actor.container.monster.OverhitState;
import net.sf.l2j.gameserver.model.actor.container.monster.SeedState;
import net.sf.l2j.gameserver.model.actor.container.monster.SpoilState;
import net.sf.l2j.gameserver.model.actor.container.npc.AbsorbInfo;
import net.sf.l2j.gameserver.model.actor.container.npc.AggroInfo;
import net.sf.l2j.gameserver.model.actor.container.npc.MinionList;
import net.sf.l2j.gameserver.model.actor.container.npc.RewardInfo;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.group.CommandChannel;
import net.sf.l2j.gameserver.model.group.Party;
import net.sf.l2j.gameserver.model.holder.IntIntHolder;
import net.sf.l2j.gameserver.model.item.DropCategory;
import net.sf.l2j.gameserver.model.item.DropData;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.skills.L2Skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * A monster extends {@link Attackable} class.<br>
 * <br>
 * It is an attackable {@link Creature}, with the capability to hold minions/master.
 */
public class Monster extends Attackable {
    private final Map<Integer, AbsorbInfo> _absorbersList = new ConcurrentHashMap<>();

    private final OverhitState _overhitState = new OverhitState(this);
    private final SpoilState _spoilState = new SpoilState();
    private final SeedState _seedState = new SeedState(this);

    private Monster _master;
    private MinionList _minionList;

    private ScheduledFuture<?> _ccTask;

    private CommandChannel _firstCcAttacker;

    private long _lastCcAttack;

    private boolean _isRaid;
    private boolean _isMinion;

    /**
     * Instantiates a new Monster.
     *
     * @param objectId the object id
     * @param template the template
     */
    public Monster(int objectId, NpcTemplate template) {
        super(objectId, template);
    }

    @Override
    protected void calculateRewards(Creature creature) {
        if (getAggroList().isEmpty())
            return;

        // Creates an empty list of rewards.
        final Map<Creature, RewardInfo> rewards = new ConcurrentHashMap<>();

        Player maxDealer = null;
        int maxDamage = 0;
        long totalDamage = 0;

        // Go through the aggro list.
        for (AggroInfo info : getAggroList().values()) {
            if (!(info.getAttacker() instanceof Playable))
                continue;

            // Get the Playable corresponding to this attacker.
            final Playable attacker = (Playable) info.getAttacker();

            // Get damages done by this attacker.
            final int damage = info.getDamage();
            if (damage <= 1)
                continue;

            // Check if attacker isn't too far from this.
            if (!MathUtil.checkIfInRange(Config.PARTY_RANGE, this, attacker, true))
                continue;

            final Player attackerPlayer = attacker.getActingPlayer();

            totalDamage += damage;

            // Calculate real damages (Summoners should get own damage plus summon's damage).
            RewardInfo reward = rewards.get(attacker);
            if (reward == null) {
                reward = new RewardInfo(attacker);
                rewards.put(attacker, reward);
            }
            reward.addDamage(damage);

            if (attacker instanceof Summon) {
                reward = rewards.get(attackerPlayer);
                if (reward == null) {
                    reward = new RewardInfo(attackerPlayer);
                    rewards.put(attackerPlayer, reward);
                }
                reward.addDamage(damage);
            }

            if (reward.getDamage() > maxDamage) {
                maxDealer = attackerPlayer;
                maxDamage = reward.getDamage();
            }
        }

        // Command channel restriction ; if a CC is registered, the main contributor is the channel leader, no matter the participation of the channel, and no matter the damage done by other participants.
        if (_firstCcAttacker != null)
            maxDealer = _firstCcAttacker.getLeader();

        // Manage Base, Quests and Sweep drops.
        doItemDrop(getTemplate(), (maxDealer != null && maxDealer.isOnline()) ? maxDealer : creature);

        for (RewardInfo reward : rewards.values()) {
            if (reward.getAttacker() instanceof Summon)
                continue;

            // Attacker to be rewarded.
            final Player attacker = reward.getAttacker().getActingPlayer();

            // Total amount of damage done.
            final int damage = reward.getDamage();

            // Get party.
            final Party attackerParty = attacker.getParty();
            if (attackerParty == null) {
                // Calculate Exp and SP rewards.
                if (!attacker.isDead() && attacker.knows(this)) {
                    final int levelDiff = attacker.getStatus().getLevel() - getStatus().getLevel();
                    final float penalty = (attacker.hasServitor()) ? ((Servitor) attacker.getSummon()).getExpPenalty() : 0;
                    final int[] expSp = calculateExpAndSp(levelDiff, damage, totalDamage);

                    long exp = expSp[0];
                    int sp = expSp[1];

                    if (isChampion()) {
                        exp *= Config.CHAMP_MUL_EXP;
                        sp *= Config.CHAMP_MUL_SP;
                    }

                    exp *= 1 - penalty;


                    // Test over-hit.
                    if (_overhitState.isValidOverhit(attacker)) {
                        attacker.sendPacket(SystemMessageId.OVER_HIT);
                        exp += _overhitState.calculateOverhitExp(exp);
                    }

                    exp *= attacker.getStatus().calcStat(Stats.PERSONAL_RATE_XP, 1, null, null);
                    sp *= attacker.getStatus().calcStat(Stats.PERSONAL_RATE_SP, 1, null, null);

                    // Set new karma.
                    attacker.updateKarmaLoss(exp);

                    // Distribute the Exp and SP.
                    attacker.addExpAndSp(exp, sp, rewards);
                }
            }
            // Share with party members.
            else {
                int partyDmg = 0;
                float partyMul = 1;
                int partyLvl = 0;

                final List<Player> rewardedMembers = new ArrayList<>();
                final Map<Creature, RewardInfo> playersWithPets = new HashMap<>();

                // Iterate every Party member.
                for (Player partyPlayer : (attackerParty.isInCommandChannel()) ? attackerParty.getCommandChannel().getMembers() : attackerParty.getMembers()) {
                    if (partyPlayer == null || partyPlayer.isDead())
                        continue;

                    // Add Player of the Party (that have attacked or not) to members that can be rewarded and in range of the monster.
                    final boolean isInRange = MathUtil.checkIfInRange(Config.PARTY_RANGE, this, partyPlayer, true);
                    if (isInRange) {
                        rewardedMembers.add(partyPlayer);

                        if (partyPlayer.getStatus().getLevel() > partyLvl)
                            partyLvl = (attackerParty.isInCommandChannel()) ? attackerParty.getCommandChannel().getLevel() : partyPlayer.getStatus().getLevel();
                    }

                    // Retrieve the associated RewardInfo, if any.
                    final RewardInfo reward2 = rewards.get(partyPlayer);
                    if (reward2 != null) {
                        // Add Player damages to Party damages.
                        if (isInRange)
                            partyDmg += reward2.getDamage();

                        // Remove the Player from the rewards.
                        rewards.remove(partyPlayer);

                        playersWithPets.put(partyPlayer, reward2);
                        if (partyPlayer.hasPet() && rewards.containsKey(partyPlayer.getSummon()))
                            playersWithPets.put(partyPlayer.getSummon(), rewards.get(partyPlayer.getSummon()));
                    }
                }

                // If the Party didn't kill this Monster alone, calculate their part.
                if (partyDmg < totalDamage)
                    partyMul = ((float) partyDmg / totalDamage);

                // Calculate the level difference between Party and this Monster.
                final int levelDiff = partyLvl - getStatus().getLevel();

                // Calculate Exp and SP rewards.
                final int[] expSp = calculateExpAndSp(levelDiff, partyDmg, totalDamage);
                long exp = expSp[0];
                int sp = expSp[1];

                if (isChampion()) {
                    exp *= Config.CHAMP_MUL_EXP;
                    sp *= Config.CHAMP_MUL_SP;
                }

                exp *= partyMul;
                sp *= partyMul;

                // Test over-hit.
                if (_overhitState.isValidOverhit(attacker)) {
                    attacker.sendPacket(SystemMessageId.OVER_HIT);
                    exp += _overhitState.calculateOverhitExp(exp);
                }

                // Distribute Experience and SP rewards to Player Party members in the known area of the last attacker.
                if (partyDmg > 0)
                    attackerParty.distributeXpAndSp(exp, sp, rewardedMembers, partyLvl, playersWithPets);
            }
        }
    }

    @Override
    public boolean isAggressive() {
        return getTemplate().getAggroRange() > 0;
    }

    @Override
    public void onSpawn() {
        // Generate minions and spawn them (initial call and regular minions respawn are handled in the same method).
        if (!getTemplate().getMinionData().isEmpty())
            getMinionList().spawnMinions();

        super.onSpawn();

        // Clear over-hit state.
        _overhitState.clear();

        // Clear spoil state.
        _spoilState.clear();

        // Clear seed state.
        _seedState.clear();

        _absorbersList.clear();
    }

    @Override
    public void onTeleported() {
        super.onTeleported();

        if (hasMinions())
            getMinionList().onMasterTeleported();
    }

    @Override
    public void deleteMe() {
        if (hasMinions())
            getMinionList().onMasterDeletion();
        else if (_master != null)
            _master.getMinionList().onMinionDeletion(this);

        super.deleteMe();
    }

    @Override
    public Monster getMaster() {
        return _master;
    }

    @Override
    public boolean isRaidBoss() {
        return _isRaid && !_isMinion;
    }

    @Override
    public void reduceCurrentHp(double damage, Creature attacker, boolean awake, boolean isDOT, L2Skill skill) {
        if (attacker != null && isRaidBoss()) {
            final Party party = attacker.getParty();
            if (party != null) {
                final CommandChannel cc = party.getCommandChannel();
                if (BossInfoType.isCcMeetCondition(cc, getNpcId())) {
                    if (_ccTask == null) {
                        _ccTask = ThreadPool.scheduleAtFixedRate(this::checkCcLastAttack, 1000, 1000);
                        _lastCcAttack = System.currentTimeMillis();
                        _firstCcAttacker = cc;

                        // Broadcast message.
                        broadcastOnScreen(10000, BossInfoType.getBossInfo(getNpcId()).getCcRightsMsg(), cc.getLeader().getName());
                    } else if (_firstCcAttacker.equals(cc))
                        _lastCcAttack = System.currentTimeMillis();
                }
            }
        }
        super.reduceCurrentHp(damage, attacker, awake, isDOT, skill);
    }

    /**
     * Set this object as part of raid (it can be either a boss or a minion).<br>
     * <br>
     * This state affects behaviors such as auto loot configs, Command Channel acquisition, or even Config related to raid bosses.<br>
     * <br>
     * A raid boss can't be lethal-ed, and a raid curse occurs if the level difference is too high.
     *
     * @param isRaid : if true, this object will be set as a raid.
     */
    public void setRaid(boolean isRaid) {
        _isRaid = isRaid;
    }

    @Override
    public boolean isRaidRelated() {
        return _isRaid;
    }

    @Override
    public boolean isMinion() {
        return _isMinion;
    }

    /**
     * Set this {@link Monster} as a minion instance.
     *
     * @param isRaidMinion : If true, this instance is considered a raid minion.
     */
    public void setMinion(boolean isRaidMinion) {
        _isRaid = isRaidMinion;
        _isMinion = true;
    }

    /**
     * Gets overhit state.
     *
     * @return the overhit state
     */
    public OverhitState getOverhitState() {
        return _overhitState;
    }

    /**
     * Gets spoil state.
     *
     * @return the spoil state
     */
    public SpoilState getSpoilState() {
        return _spoilState;
    }

    /**
     * Gets seed state.
     *
     * @return the seed state
     */
    public SeedState getSeedState() {
        return _seedState;
    }

    /**
     * Add a {@link Player} that successfully absorbed the soul of this {@link Monster} into the _absorbersList.
     *
     * @param player  : The {@link Player} to test.
     * @param crystal : The {@link ItemInstance} which was used to register.
     */
    public void addAbsorber(Player player, ItemInstance crystal) {
        // If the Player isn't already in the _absorbersList, add it.
        AbsorbInfo ai = _absorbersList.get(player.getObjectId());
        if (ai == null) {
            // Create absorb info.
            _absorbersList.put(player.getObjectId(), new AbsorbInfo(crystal.getObjectId()));
        } else {
            // Add absorb info, unless already registered.
            if (!ai.isRegistered())
                ai.setItemId(crystal.getObjectId());
        }
    }

    /**
     * Register a {@link Player} into this instance _absorbersList, setting the HP ratio. The {@link AbsorbInfo} must already exist.
     *
     * @param player : The {@link Player} to test.
     */
    public void registerAbsorber(Player player) {
        // Get AbsorbInfo for user.
        AbsorbInfo ai = _absorbersList.get(player.getObjectId());
        if (ai == null)
            return;

        // Check item being used and register player to mob's absorber list.
        if (player.getInventory().getItemByObjectId(ai.getItemId()) == null)
            return;

        // Register AbsorbInfo.
        if (!ai.isRegistered()) {
            ai.setAbsorbedHpPercent((int) getStatus().getHpRatio() * 100);
            ai.setRegistered(true);
        }
    }

    /**
     * Gets absorb info.
     *
     * @param npcObjectId the npc object id
     * @return the absorb info
     */
    public AbsorbInfo getAbsorbInfo(int npcObjectId) {
        return _absorbersList.get(npcObjectId);
    }

    /**
     * Calculate the XP and SP to distribute to the attacker of the {@link Monster}.
     *
     * @param diff        : The difference of level between the attacker and the {@link Monster}.
     * @param damage      : The damages done by the attacker.
     * @param totalDamage : The total damage done.
     * @return an array consisting of xp and sp values.
     */
    private int[] calculateExpAndSp(int diff, int damage, long totalDamage) {
        // Calculate damage ratio.
        double xp = (double) getExpReward() * damage / totalDamage;
        double sp = (double) getSpReward() * damage / totalDamage;

        // Calculate level ratio.
        if (diff > 5) {
            double pow = Math.pow((double) 5 / 6, diff - 5);
            xp = xp * pow;
            sp = sp * pow;
        }

        // If the XP is inferior or equals 0, don't reward any SP. Both XP and SP can't be inferior to 0.
        if (xp <= 0) {
            xp = 0;
            sp = 0;
        } else if (sp <= 0)
            sp = 0;

        return new int[]
                {
                        (int) xp,
                        (int) sp
                };
    }

    /**
     * Sets master.
     *
     * @param master the master
     */
    public void setMaster(Monster master) {
        _master = master;
    }

    /**
     * Has minions boolean.
     *
     * @return the boolean
     */
    public boolean hasMinions() {
        return _minionList != null;
    }

    /**
     * Gets minion list.
     *
     * @return the minion list
     */
    public MinionList getMinionList() {
        if (_minionList == null)
            _minionList = new MinionList(this);

        return _minionList;
    }

    /**
     * Teleport this {@link Monster} to its master.
     */
    public void teleportToMaster() {
        if (_master == null)
            return;

        final int minOffset = (int) (_master.getCollisionRadius() + 30);
        final int maxOffset = (int) (100 + getCollisionRadius() + _master.getCollisionRadius());

        final Location spawnLoc = _master.getPosition().clone();
        spawnLoc.addRandomOffsetBetweenTwoValues(minOffset, maxOffset);
        spawnLoc.set(GeoEngine.getInstance().getValidLocation(_master, spawnLoc));

        teleportTo(spawnLoc, 0);
    }

    private IntIntHolder calculateRewardItem(Creature creature, DropCategory cat) {
        final DropData drop = cat.dropOne(creature.getActingPlayer(), this);
        if (drop == null) {
            return null;
        }
        int dropChance = drop.calculateDropChance(creature.getActingPlayer(), this, cat);
        int itemCount = drop.calculateDropCount(dropChance);
        if (itemCount > 0) {
            return new IntIntHolder(drop.getItemId(), itemCount);
        }
        return null;
    }

    private IntIntHolder calculateCategorizedRewardItem(Creature creature, DropCategory cat) {
        int categoryDropChance = cat.calculateCategoryChance(creature.getActingPlayer(), this);
        if (Rnd.get(DropData.MAX_CHANCE) < categoryDropChance) {
            return calculateRewardItem(creature, cat);
        }
        return null;
    }


    /**
     * Calculate level modifier for drop int.
     *
     * @param player : The {@link Player} to test.
     * @return The level modifier for drop purpose, based on this instance and the {@link Player} set as parameter.
     */
    public int calculateLevelModifierForDrop(Player player) {
        if (Config.DEEPBLUE_DROP_RULES) {
            int highestLevel = player.getStatus().getLevel();

            // Check to prevent very high level player to nearly kill mob and let low level player do the last hit.
            for (Creature creature : getAttackByList()) {
                if (creature.getStatus().getLevel() > highestLevel)
                    highestLevel = creature.getStatus().getLevel();
            }

            // According to official data (Prima), deep blue mobs are 9 or more levels below players
            if (highestLevel - 9 >= getStatus().getLevel())
                return ((highestLevel - (getStatus().getLevel() + 8)) * 9);
        }
        return 0;
    }

    /**
     * Manage Base & Quests drops of this {@link Monster} using an associated {@link NpcTemplate}.<br>
     * <br>
     * This method is called by {@link #calculateRewards}.
     *
     * @param template : The {@link NpcTemplate} used to retrieve drops.
     * @param creature : The {@link Creature} that made the most damage.
     */
    public void doItemDrop(NpcTemplate template, Creature creature) {
        if (creature == null)
            return;

        // Don't drop anything if the last attacker or owner isn't a Player.
        final Player player = creature.getActingPlayer();
        if (player == null)
            return;

        // Calculate level modifier.
        final int levelModifier = calculateLevelModifierForDrop(player);

        // Check Cursed Weapons drop.
        CursedWeaponManager.getInstance().checkDrop(this, player);

        // now throw all categorized drops and handle spoil.
        for (DropCategory cat : template.getDropData()) {
            IntIntHolder holder;
            if (cat.isSweep()) {
                if (getSpoilState().isSpoiled()) {
                    holder = calculateRewardItem(creature, cat);
                    if (holder == null)
                        continue;

                    getSpoilState().add(holder);
                }
            } else {
                if (getSeedState().isSeeded()) {
                    final DropData drop = cat.dropSeedAllowedDropsOnly();
                    if (drop == null)
                        continue;

                    holder = calculateRewardItem(creature, cat);
                } else
                    holder = calculateCategorizedRewardItem(creature, cat);

                if (holder == null)
                    continue;

                dropOrAutoLootItem(player, holder, true);
            }
        }

        // Apply special item drop for champions.
        if (isChampion() && Config.CHAMP_ITEM_DROP_CHANCE > 0) {
            int dropChance = Config.CHAMP_ITEM_DROP_CHANCE;

            // Apply level modifier, if any/wanted.
            if (Config.DEEPBLUE_DROP_RULES) {
                int deepBlueDrop = (levelModifier > 0) ? 3 : 1;

                // Check if we should apply our maths so deep blue mobs will not drop that easy.
                dropChance = ((Config.CHAMP_ITEM_DROP_CHANCE - ((Config.CHAMP_ITEM_DROP_CHANCE * levelModifier) / 100)) / deepBlueDrop);
            }

            if (Rnd.get(100) < dropChance) {
                final IntIntHolder item = new IntIntHolder(Config.CHAMP_ITEM_DROP_ID, Math.max(1, Rnd.get(1, Config.CHAMP_ITEM_DROP_COUNT)));
                if (Config.AUTO_LOOT)
                    player.addItem("ChampionLoot", item.getId(), item.getValue(), this, true);
                else
                    dropItem(player, item);
            }
        }
    }

    /**
     * Drop on ground or auto loot a reward item, depending about activated {@link Config}s.
     *
     * @param player        : The {@link Player} who made the highest damage contribution.
     * @param holder        : The {@link IntIntHolder} used for reward (item id / amount).
     * @param isRegularItem : If True, regular item scenario occurs ; if False, herb scenario occurs.
     */
    public void dropOrAutoLootItem(Player player, IntIntHolder holder, boolean isRegularItem) {
        if (isRegularItem) {
            // Check Config.
            if (((isRaidBoss() && Config.AUTO_LOOT_RAID) || (!isRaidBoss() && Config.AUTO_LOOT)) && player.getInventory().validateCapacityByItemId(holder)) {
                if (player.isInParty())
                    player.getParty().distributeItem(player, holder, false, this);
                else if (holder.getId() == 57)
                    player.addAdena("Loot", holder.getValue(), this, true);
                else
                    player.addItem("Loot", holder.getId(), holder.getValue(), this, true);
            } else
                dropItem(player, holder);

            // Broadcast message if RaidBoss was defeated.
            if (isRaidBoss())
                broadcastPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_DIED_DROPPED_S3_S2).addCharName(this).addItemName(holder.getId()).addNumber(holder.getValue()));
        } else {
            // Check Config.
            if (Config.AUTO_LOOT_HERBS)
                player.addItem("Loot", holder.getId(), 1, this, true);
            else {
                // If multiple similar herbs drop, split them and make a unique drop per item.
                final int count = holder.getValue();
                if (count > 1) {
                    holder.setValue(1);
                    for (int i = 0; i < count; i++)
                        dropItem(player, holder);
                } else
                    dropItem(player, holder);
            }
        }
    }

    /**
     * Drop a reward on the ground, to this {@link Monster} feet. It is item protected to the {@link Player} set as parameter.
     *
     * @param player : The {@link Player} used as item protection.
     * @param holder : The {@link IntIntHolder} used for reward (item id / amount).
     */
    public void dropItem(Player player, IntIntHolder holder) {
        for (int i = 0; i < holder.getValue(); i++) {
            // Create the ItemInstance and add it in the world as a visible object.
            final ItemInstance item = ItemInstance.create(holder.getId(), holder.getValue(), player, this);
            item.setDropProtection(player.getObjectId(), isRaidBoss());
            item.dropMe(this, 70);

            // If stackable, end loop as entire count is included in 1 instance of item.
            if (item.isStackable() || !Config.MULTIPLE_ITEM_DROP)
                break;
        }
    }

    /**
     * Check CommandChannel loot priority every second. After 5min, the loot priority dissapears.
     */
    private void checkCcLastAttack() {
        // We're still on time, do nothing.
        if (System.currentTimeMillis() - _lastCcAttack <= 300000)
            return;

        // Reset variables.
        _firstCcAttacker = null;
        _lastCcAttack = 0;

        // Set task to null.
        if (_ccTask != null) {
            _ccTask.cancel(false);
            _ccTask = null;
        }

        // Broadcast message.
        broadcastOnScreen(10000, BossInfoType.getBossInfo(getNpcId()).getCcNoRightsMsg());
    }

    @Override
    public boolean isAttackableWithoutForceBy(Playable attacker) {
        return isAttackableBy(attacker);
    }
}