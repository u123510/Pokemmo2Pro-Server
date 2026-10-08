package org.pokemmo.gameserver.game.pokemon;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.db.jooq.tables.records.PokemonRecord;
import org.pokemmo.gameserver.game.item.ItemData;
import org.pokemmo.gameserver.game.item.ItemManager;
import org.pokemmo.gameserver.game.particleEffectType.ParticleEffectType;
import org.pokemmo.gameserver.game.move.MoveManager;
import org.pokemmo.gameserver.util.ArrayUtil;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

@Getter @Setter @AllArgsConstructor @Slf4j
public class PokemonData {
    private static final short NO_HELD_ITEM = -1;
    private static final short RANDOM_PARTICLE_EFFECT = -3;
    private static final short UNSIGNED_RANDOM_PARTICLE_EFFECT = 253;
    private static final short INVALID_PARTICLE_EFFECT = -1;
    static final short SHINY_DEBUT_PARTICLE_EFFECT = 0;
    private static final short FIRST_RENDERABLE_PARTICLE_EFFECT = 4;

    private PokemonDexData pokemonDexData;
    private long pokemonId;
    private long trainerId;
    private short widgetType = 0;
    private int containerId;
    private short containerPosition;
    private short pokemonIndexId;
    private int personalityValue;
    private long originalTrainerId;
    private String otName = "";
    private String name = "";
    private short markStatus;
    private PokemonStatusType pokemonStatus;
    private short level;
    private short currentHp;
    private short maxHp;
    private PokemonNatureType natureType;
    private short item = -1;
    private int exp;
    private byte ppUpTimes;
    private short friendValue;
    private short[] moves;
    private short[] movesPp;
    private short[] canRememberMoves;
    private short[] pokemonEvs;
    private short[] pokemonContestsCategoryValues;
    private short catchAddress;
    private short catchLevel;
    private short catchRegion;
    private short ballType;
    private short formType;
    private short[] pokemonIvs;
    private short pokemonAbilityIndex;
    private short[] contestRibbon;
    private boolean[] normalRibbon;
    private boolean hasHiddenAbility;
    private boolean isShiny;
    private boolean isAlpha;
    private boolean isSecret;
    private LocalDateTime catchTime;
    private short eggValue;
    private short hiddenPowerType;
    private short currentSelectParticleEffectType = -1;
    private short[] particleEffects;

    public byte getMovePos(short moveIndexId) {
        for (int i = 0; i < 4; i++) {
            if (moves[i] == moveIndexId) {
                return (byte) i;
            }
        }
        return -1;
    }

    /**
     * Returns the particle value that ordinary Pokemon/container packets can display.
     * Random mode remains persisted as -3 and is only projected to one owned effect
     * while encoding a client-facing view.
     */
    public short getCurrentSelectParticleEffectTypeForDisplay() {
        return getCurrentSelectParticleEffectTypeForClient();
    }

    /**
     * Returns the concrete particle ID required by battle packets.
     * The client does not render the -3 random sentinel in battle; it skips the
     * effect instead, so random mode must be resolved to an owned renderable ID.
     * A shiny or secret-shiny Pokemon without a manually selected particle uses
     * the client's reserved value 0, which selects the normal or secret debut
     * effect from the rarity bits in the same battle packet.
     */
    public short getCurrentSelectParticleEffectTypeForBattle() {
        short selectedParticle = getCurrentSelectParticleEffectTypeForClient();
        if (selectedParticle < 0 && (isShiny || isSecret)) {
            return SHINY_DEBUT_PARTICLE_EFFECT;
        }
        return selectedParticle;
    }

    /**
     * Returns the owned particle IDs in the format expected by the client.
     * The backing array is kept in its persisted form so legacy item indexes
     * can still be converted after all resource managers have loaded.
     */
    public short[] getParticleEffectsForClient() {
        return normalizeParticleEffects(particleEffects);
    }

    private short getCurrentSelectParticleEffectTypeForClient() {
        if (!isRandomParticleEffect(currentSelectParticleEffectType)) {
            return currentSelectParticleEffectType;
        }
        if (particleEffects == null) {
            log.debug("随机质子无法解析: pokemonId={}, configured={}, owned=null, selected=-1",
                    pokemonId, currentSelectParticleEffectType);
            return -1;
        }

        short[] normalizedEffects = getParticleEffectsForClient();
        int validEffectCount = 0;
        for (short effect : normalizedEffects) {
            if (isRenderableParticleEffect(effect)) {
                validEffectCount++;
            }
        }
        if (validEffectCount == 0) {
            log.debug("随机质子无法解析: pokemonId={}, configured={}, owned={}, selected=-1",
                    pokemonId, currentSelectParticleEffectType, Arrays.toString(particleEffects));
            return -1;
        }

        int selectedIndex = ThreadLocalRandom.current().nextInt(validEffectCount);
        short selectedEffect = INVALID_PARTICLE_EFFECT;
        for (short effect : normalizedEffects) {
            if (isRenderableParticleEffect(effect) && selectedIndex-- == 0) {
                selectedEffect = effect;
                break;
            }
        }
        log.debug("随机质子解析成功: pokemonId={}, configured={}, owned={}, normalized={}, selected={}",
                pokemonId, currentSelectParticleEffectType,
                Arrays.toString(particleEffects), Arrays.toString(normalizedEffects), selectedEffect);
        return selectedEffect;
    }

    /**
     * Converts legacy particle item indexes to the concrete client particle IDs.
     * New records already store IDs in the 0..38 range and pass through unchanged.
     */
    public static short[] normalizeParticleEffects(short[] effects) {
        if (effects == null || effects.length == 0) {
            return new short[0];
        }
        short[] normalized = new short[effects.length];
        int size = 0;
        for (short effect : effects) {
            short particleType = normalizeParticleEffect(effect);
            if (particleType == INVALID_PARTICLE_EFFECT) {
                continue;
            }
            boolean duplicate = false;
            for (int index = 0; index < size; index++) {
                if (normalized[index] == particleType) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                normalized[size++] = particleType;
            }
        }
        return Arrays.copyOf(normalized, size);
    }

    private static short normalizeParticleEffect(short effect) {
        if (ParticleEffectType.getByType(effect) != null) {
            return effect;
        }
        ItemData itemData = ItemManager.getItemData(effect);
        if (itemData != null && itemData.getItemParticleEffectType() != null) {
            return itemData.getItemParticleEffectType().getType();
        }
        return INVALID_PARTICLE_EFFECT;
    }

    public static boolean isRenderableParticleEffect(short particleEffect) {
        return particleEffect >= FIRST_RENDERABLE_PARTICLE_EFFECT
                && ParticleEffectType.getByType(particleEffect) != null;
    }

    private static boolean isRandomParticleEffect(short particleEffect) {
        return particleEffect == RANDOM_PARTICLE_EFFECT
                || particleEffect == UNSIGNED_RANDOM_PARTICLE_EFFECT;
    }

    public byte getPokemonMoveMaxPp(int moveIndex) {
        if(moveIndex < 0 || moveIndex > 3){
            return 0;
        }
        short moveIndexId = moves[moveIndex];
        byte moveBasePp = MoveManager.getPokemonMove(moveIndexId).getMoveBasePp();
        if (moveBasePp== 0 || moveBasePp == 1) {
            return  moveBasePp;
        }
        return (byte) (java.lang.Math.floor(0.2d * (double) moveBasePp * (byte) (3 & (this.ppUpTimes >> (moveIndex * 2)))) + moveBasePp);
    }

    public short getMoveRemainPpByMoveIndexId(short moveIndexId){
        byte movePos = getMovePos(moveIndexId);
        if(movePos<0 || movePos>=4)
            return -1;
        return movesPp[movePos];
    }
    public void setMoveRemainPpByPressureAbilityComputer(short moveIndexId,byte pressureAbilityAmount){
        byte movePos = getMovePos(moveIndexId);
        if(movePos<0 || movePos>=4)
            return;
        if(movesPp[movePos]>0){
            if(movesPp[movePos] <pressureAbilityAmount){
                movesPp[movePos] = 0;
            }
            else{
                movesPp[movePos] -=pressureAbilityAmount;
            }
        }
    }
    public void setMoveRemainPpByPos(int movePos,byte remainPp){
        if(movePos<0 || movePos>=4)
            return;
        movesPp[movePos] = remainPp;
    }
    public PokemonType getPokemonFirstType(){
        if (pokemonDexData == null || pokemonDexData.getPokemonTypes() == null || pokemonDexData.getPokemonTypes().isEmpty()) {
            return PokemonType.NORMAL;
        }
        return pokemonDexData.getPokemonTypes().get(0);
    }
    public PokemonType getPokemonSecondType(){
        if (pokemonDexData == null || pokemonDexData.getPokemonTypes() == null || pokemonDexData.getPokemonTypes().size() < 2) {
            return getPokemonFirstType();
        }
        return pokemonDexData.getPokemonTypes().get(1);
    }
    public byte getPokemonSex(){
        return (byte) ((personalityValue & 0xFF) >= pokemonDexData.getGenderRatio() ? 0 : 1);
    }

    public PokemonRecord toPokemonRecord() {
        return new PokemonRecord(
                pokemonId,
                trainerId,
                widgetType,
                containerId,
                containerPosition,
                pokemonIndexId,
                personalityValue,
                originalTrainerId,
                otName,
                name,
                markStatus,
                (short) (pokemonStatus == null ? 0 : pokemonStatus.getType()),
                level,
                currentHp,
                item,
                exp,
                (short) ppUpTimes,
                friendValue,
                ArrayUtil.toShortObject(moves),
                ArrayUtil.toShortObject(movesPp),
                ArrayUtil.toShortObject(canRememberMoves),
                ArrayUtil.toShortObject(pokemonEvs),
                ArrayUtil.toShortObject(pokemonContestsCategoryValues),
                catchAddress,
                catchLevel,
                catchRegion,
                ballType,
                formType,
                ArrayUtil.toShortObject(pokemonIvs),
                pokemonAbilityIndex,
                ArrayUtil.toShortObject(contestRibbon),
                ArrayUtil.toBooleanObject(normalRibbon),
                hasHiddenAbility,
                isShiny,
                isAlpha,
                isSecret,
                catchTime,
                eggValue,
                hiddenPowerType,
                currentSelectParticleEffectType,
                ArrayUtil.toShortObject(particleEffects)
        );
    }

    public short getPokemonAbilityIndexId(){
        return pokemonDexData.getPokemonAbilities().get(this.pokemonAbilityIndex).getAbilityIndexId();
    }
    public short choiceRandomMove(){
        Random random = new Random();
        int moveSize = 0;
        for(int i = 0;i<4;i++){
            if(moves[i] == 0){
                break;
            }
            moveSize++;
        }
        return moves[random.nextInt(moveSize)];
    }
    public void addContestRibbon(int index,int level){
        this.contestRibbon[index] = (short) level;
    }
    public void addNormalRibbon(int index){
        this.normalRibbon[index] = true;
    }
    public static class Builder{
        private PokemonDexData pokemonDexData;
        private long pokemonId;
        private long trainerId;
        private short widgetType = 0;
        private int containerId;
        private short containerPosition;
        private short pokemonIndexId;
        private int personalityValue;
        private long originalTrainerId;
        private String otName  = "";
        private String name = "";
        private short markStatus;
        private short status;
        private short level;
        private short currentHp;
        private short maxHp;
        private PokemonNatureType natureType;
        private short item = -1;
        private int exp;
        private byte ppUpTimes;
        private short friendValue;
        private short[] moves = new short[4];
        private short[] movesPp = new short[4];
        private short[] canRememberMoves = new short[4];
        private short[] pokemonEvs = new short[6];
        private short[] pokemonContestsCategoryValues = new short[5];
        private short catchAddress;
        private short catchLevel;
        private short catchRegion;
        private short ballType = 3;
        private short formType;
        private short[] pokemonIvs = new short[6];
        private short pokemonAbilityIndex;
        private short[] contestRibbon = new short[6];
        private boolean[] normalRibbon = new boolean[16];
        private boolean hasHiddenAbility;
        private boolean isShiny;
        private boolean isAlpha;
        private boolean isSecret;
        private LocalDateTime catchTime;
        private short eggValue;
        private short hiddenPowerType;
        private short currentSelectParticleEffectType = -1;
        private short[] particleEffects = new short[0];
        public Builder setByRecord(PokemonRecord pokemonRecord) {
            this.pokemonDexData = PokemonManager.getPokemonoexData(pokemonRecord.getDexId());
            this.pokemonId = pokemonRecord.getId();
            this.trainerId = pokemonRecord.getTrainerId();
            this.widgetType = pokemonRecord.getWidgetType();
            this.containerId = pokemonRecord.getContainerId();
            this.containerPosition = pokemonRecord.getContainerPosition();
            this.pokemonIndexId = pokemonRecord.getDexId();
            this.personalityValue = pokemonRecord.getPersonalityValue();
            this.originalTrainerId = pokemonRecord.getOriginalTrainerId();
            this.otName = pokemonRecord.getOtName();
            this.name = pokemonRecord.getName();
            this.markStatus = pokemonRecord.getMarkStatus();
            this.status = pokemonRecord.getStatus();
            this.level = pokemonRecord.getLevelValue();
            this.currentHp = pokemonRecord.getCurrentHp();
            this.natureType = PokemonNatureType.getByPersonalityValue(this.personalityValue);
            // The schema historically used 0 for an empty held-item slot, while
            // the client uses a negative value as the canonical no-item sentinel.
            // Normalize both null and legacy non-positive values at the boundary
            // so every outgoing Pokemon packet has the same representation.
            Short storedItem = pokemonRecord.getItem();
            this.item = storedItem == null || storedItem <= 0 ? NO_HELD_ITEM : storedItem;
            this.exp = pokemonRecord.getExp();
            this.ppUpTimes = pokemonRecord.getPpUpTimes().byteValue();
            this.friendValue = pokemonRecord.getFriendValue();
            this.moves = ArrayUtil.toShortPrimitive(pokemonRecord.getMoves());
            this.movesPp = ArrayUtil.toShortPrimitive(pokemonRecord.getMovesPp());
            this.canRememberMoves = ArrayUtil.toShortPrimitive(pokemonRecord.getCanRememberMoves());
            this.pokemonEvs = ArrayUtil.toShortPrimitive(pokemonRecord.getEvValues());
            this.pokemonContestsCategoryValues = ArrayUtil.toShortPrimitive(pokemonRecord.getContestsCategoryValues());
            this.catchAddress = pokemonRecord.getCatchAddress();
            this.catchLevel = pokemonRecord.getCatchLevel();
            this.catchRegion = pokemonRecord.getCatchRegion();
            this.ballType = pokemonRecord.getBallType();
            this.formType = pokemonRecord.getFormType();
            this.pokemonIvs = ArrayUtil.toShortPrimitive(pokemonRecord.getIvValues());
            this.maxHp = pokemonDexData.getPokemonAbilityValue(
                    PokemonStatType.HP,
                    this.pokemonIvs[PokemonStatType.HP.getType()],
                    this.pokemonEvs[PokemonStatType.HP.getType()],
                    this.level,
                    natureType);
            this.pokemonAbilityIndex = pokemonRecord.getAbility();
            this.contestRibbon = ArrayUtil.toShortPrimitive(pokemonRecord.getContestRibbon());
            this.normalRibbon = ArrayUtil.toBooleanPrimitive(pokemonRecord.getNormalRibbon());
            this.hasHiddenAbility = pokemonRecord.getHasHiddenAbility();
            this.isShiny = pokemonRecord.getIsShiny();
            this.isAlpha = pokemonRecord.getIsAlpha();
            this.isSecret = pokemonRecord.getIsSecret();
            this.catchTime = pokemonRecord.getCatchTime();
            this.eggValue = pokemonRecord.getEggValue();
            this.hiddenPowerType = pokemonRecord.getHiddenPowerType();
            this.currentSelectParticleEffectType = pokemonRecord.getCurrentSelectParticleEffectType();
            this.particleEffects = ArrayUtil.toShortPrimitive(pokemonRecord.getParticleEffects());
            return this;
        }
        public Builder setPokemonDexData(PokemonDexData pokemonDexData) {
            this.pokemonDexData = pokemonDexData;
            return this;
        }
        public Builder setPokemonId(long pokemonId) {
            this.pokemonId = pokemonId;
            return this;
        }
        public Builder setTrainerId(long trainerId) {
            this.trainerId = trainerId;
            return this;
        }
        public Builder setPokemonIndexId(short pokemonIndexId) {
            this.pokemonIndexId = pokemonIndexId;
            return this;
        }
        public Builder setContainerPos(short containerPos) {
            this.containerPosition = containerPos;
            return this;
        }
        public Builder setPersonalityValue(int personalityValue) {
            this.personalityValue = personalityValue;
            return this;
        }
        public Builder setPokemonCurrentHp(short currentHp) {
            this.currentHp = currentHp;
            return this;
        }
        public Builder setPokemonMaxHp(short maxHp) {
            this.maxHp = maxHp;
            return this;
        }
        public Builder setPokemonNatureType(PokemonNatureType natureType) {
            this.natureType = natureType;
            return this;
        }
        public Builder setLevel(short level) {
            this.level = level;
            return this;
        }
        public Builder setPokemonIvs(short[] pokemonIvs) {
            this.pokemonIvs = pokemonIvs;
            return this;
        }
        public Builder setPokemonEvs(short[] pokemonEvs) {
            this.pokemonEvs = pokemonEvs;
            return this;
        }
        public Builder setAbilityIndex(short abilityIndex) {
            this.pokemonAbilityIndex = abilityIndex;
            return this;
        }
        public Builder setMoves(short[] moves) {
            this.moves = moves;
            return this;
        }
        public Builder setMovesPp(short[] movesPp) {
            this.movesPp = movesPp;
            return this;
        }
        public Builder setItem(short item) {
            this.item = item > 0 ? item : NO_HELD_ITEM;
            return this;
        }
        public Builder setBallType(short ballType) {
            this.ballType = ballType;
            return this;
        }
        public PokemonData build() {
            return new PokemonData(pokemonDexData,pokemonId, trainerId, widgetType, containerId, containerPosition, pokemonIndexId, personalityValue, originalTrainerId, otName, name, markStatus, PokemonStatusType.getByType(status), level, currentHp, maxHp, natureType, item, exp, ppUpTimes, friendValue, moves, movesPp, canRememberMoves, pokemonEvs, pokemonContestsCategoryValues, catchAddress, catchLevel, catchRegion, ballType, formType, pokemonIvs, pokemonAbilityIndex, contestRibbon, normalRibbon, hasHiddenAbility, isShiny, isAlpha, isSecret, catchTime, eggValue, hiddenPowerType, currentSelectParticleEffectType, particleEffects);
        }
    }

}
