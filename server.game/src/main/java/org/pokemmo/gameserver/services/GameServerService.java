package org.pokemmo.gameserver.services;

import org.jooq.postgres.extensions.types.Inet;
import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.AccountContextRecord;
import org.pokemmo.db.jooq.tables.records.CharacterRecord;
import org.pokemmo.db.jooq.tables.records.ContainerRecord;
import org.pokemmo.db.jooq.tables.records.InventoryRecord;
import org.pokemmo.db.jooq.tables.records.OwnedItemRecord;
import org.pokemmo.db.jooq.tables.records.PokemonRecord;
import org.pokemmo.gameserver.game.character.CharacterData;
import org.pokemmo.gameserver.game.events.EventRegionType;
import org.pokemmo.gameserver.game.events.GameEvent;
import org.pokemmo.gameserver.game.events.GameEventType;
import org.pokemmo.gameserver.game.events.ServerEventType;
import org.pokemmo.gameserver.game.gtl.GtlActionResult;
import org.pokemmo.gameserver.game.gtl.GtlFilterType;
import org.pokemmo.gameserver.game.gtl.GtlGenderFilterType;
import org.pokemmo.gameserver.game.gtl.GtlListingPage;
import org.pokemmo.gameserver.game.gtl.GtlPurchaseHistoryEntry;
import org.pokemmo.gameserver.game.gtl.GtlShinyFilterType;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.instance.GameInstance;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonEggGroupType;
import org.pokemmo.gameserver.game.pokemon.PokemonNatureType;
import org.pokemmo.gameserver.game.skin.SkinType;
import org.pokemmo.gameserver.services.character.CharacterService;
import org.pokemmo.gameserver.services.inventory.InventoryService;
import org.pokemmo.gameserver.services.item.ItemUseService;
import org.pokemmo.gameserver.services.pokemon.PokemonService;
import org.pokemmo.gameserver.services.pokemon.PokemonCaptureService;
import org.pokemmo.gameserver.services.pokemon.PokemonHealingService;
import org.pokemmo.gameserver.services.story.PalletStoryStore;
import org.pokemmo.gameserver.services.story.OakParcelStore;
import org.pokemmo.gameserver.services.story.ViridianCatchStore;
import org.pokemmo.gameserver.services.story.StoryProgressStore;
import org.pokemmo.gameserver.services.story.StoryEventFlagStore;
import org.pokemmo.gameserver.services.story.StoryActionStore;
import org.pokemmo.gameserver.services.world.WorldService;
import org.pokemmo.gameserver.services.trade.TradeService;
import org.pokemmo.gameserver.services.mail.MailService;
import org.pokemmo.gameserver.services.friend.FriendService;
import org.pokemmo.gameserver.game.trade.TradeItemOffer;
import org.pokemmo.gameserver.util.SnowflakeIdGenerator;
import org.pokemmo.gameserver.game.item.ItemUseManager;
import org.server.node.JoinableSeverData;

import java.util.BitSet;
import java.util.List;
import java.util.Optional;

public final class GameServerService {
    private final CharacterService characterService;
    private final PokemonService pokemonService;
    private final PokemonHealingService pokemonHealingService;
    private final PalletStoryStore palletStoryStore;
    private final OakParcelStore oakParcelStore;
    private final ViridianCatchStore viridianCatchStore;
    private final StoryProgressStore storyProgressStore;
    private final StoryEventFlagStore storyEventFlagStore;
    private final StoryActionStore storyActionStore;
    private final InventoryService inventoryService;
    private final WorldService worldService;
    private final TradeService tradeService;
    private final MailService mailService;
    private final FriendService friendService;
    private final Database database;
    private ItemUseService itemUseService;

    public GameServerService(Database database) {
        this.database = database;
        this.worldService = new WorldService(database);
        this.characterService = new CharacterService(database);
        this.pokemonService = new PokemonService(database, worldService, characterService);
        this.pokemonHealingService = new PokemonHealingService(database);
        this.palletStoryStore = new PalletStoryStore(database);
        this.oakParcelStore = new OakParcelStore(database);
        this.viridianCatchStore = new ViridianCatchStore(database);
        this.storyProgressStore = new StoryProgressStore(database);
        this.storyEventFlagStore = new StoryEventFlagStore(database);
        this.storyActionStore = new StoryActionStore(database);
        this.inventoryService = new InventoryService(database);
        this.tradeService = new TradeService(database);
        this.mailService = new MailService(database);
        this.friendService = new FriendService(database);
        this.itemUseService = new ItemUseService(database, null);
    }

    /** Connects Item.bin-adjacent overrides after ScriptManager has loaded resources. */
    public void configureItemUseManager(ItemUseManager itemUseManager) {
        this.itemUseService = new ItemUseService(database, itemUseManager);
    }

    public ItemUseService.Result useItem(
            long characterId,
            short itemIndexId,
            long targetPokemonId,
            short usedItemAmount,
            byte targetMovePosition,
            byte unuse) {
        return itemUseService.useItem(characterId, itemIndexId, targetPokemonId,
                usedItemAmount, targetMovePosition, unuse);
    }

    public record PokemonPositionChange(
            int oldContainerId,
            short oldPosition,
            int newContainerId,
            short newPosition) {
    }

    /**
     * Result of a committed player trade. The two lists contain the Pokemon
     * received by the first and second character respectively, with their
     * persisted owner/container/slot values already applied.
     */
    public record TradeCompletionResult(
            boolean success,
            List<PokemonData> firstReceived,
            List<PokemonData> secondReceived) {
        public static TradeCompletionResult rejected() {
            return new TradeCompletionResult(false, List.of(), List.of());
        }
    }

    public List<CharacterData> getCharacters(int accountId) {
        return characterService.getCharacters(accountId);
    }

    public String getServerNodeName(int nodeId) {
        return worldService.getServerNodeName(nodeId);
    }

    public ContainerRecord getContainerByType(PokemonContainerType containerType) {
        return worldService.getContainerByType(containerType);
    }

    public PcData getPcData(long characterId) {
        if (characterId <= 0) {
            return null;
        }
        ContainerRecord pcContainer = getContainerByType(PokemonContainerType.PC);
        if (pcContainer == null) {
            return null;
        }
        return new PcData(pcContainer, getCharacterContainerPokemons(characterId, pcContainer));
    }

    public AccountContextRecord getAccountContext(int accountId) {
        return worldService.getAccountContext(accountId);
    }

    public AccountContextRecord getAccountContextByCharacterId(long characterId) {
        return worldService.getAccountContextByCharacterId(characterId);
    }

    public int getGameServerIdByIpv4(Inet ipAddress) {
        return worldService.getGameServerIdByIpv4(ipAddress);
    }

    public void deleteAccountContext(int accountId) {
        worldService.deleteAccountContext(accountId);
    }

    public void addPokemon(PokemonRecord pokemon) {
        pokemonService.addPokemon(pokemon);
    }

    public PokemonCaptureService.CaptureResult captureWildPokemon(
            long characterId, PokemonData wildPokemon, short ballItemIndexId,
            boolean caught) {
        return pokemonService.captureWildPokemon(characterId, wildPokemon, ballItemIndexId, caught);
    }

    public PokemonCaptureService.CaptureResult captureSafariPokemon(
            long characterId, PokemonData wildPokemon, short ballItemIndexId,
            boolean caught) {
        return pokemonService.captureSafariPokemon(characterId, wildPokemon, ballItemIndexId, caught);
    }


    public boolean updatePokemonAlpha(long characterId, long pokemonId, boolean isAlpha) {
        return pokemonService.updatePokemonAlpha(characterId, pokemonId, isAlpha);
    }

    public PokemonHealingService.Result healParty(long characterId, PokemonData[] expectedParty) {
        return pokemonHealingService.healParty(characterId, expectedParty);
    }

    public PokemonHealingService.Result healPartyFully(long characterId, PokemonData[] expectedParty) {
        return pokemonHealingService.healParty(characterId, expectedParty, true);
    }

    public PalletStoryStore getPalletStoryStore() {
        return palletStoryStore;
    }

    public OakParcelStore getOakParcelStore() {
        return oakParcelStore;
    }

    public ViridianCatchStore getViridianCatchStore() {
        return viridianCatchStore;
    }

    public StoryProgressStore getStoryProgressStore() {
        return storyProgressStore;
    }

    public StoryEventFlagStore getStoryEventFlagStore() {
        return storyEventFlagStore;
    }

    public StoryActionStore getStoryActionStore() {
        return storyActionStore;
    }

    public boolean updatePokemonShiny(long characterId, long pokemonId, boolean isShiny) {
        return pokemonService.updatePokemonShiny(characterId, pokemonId, isShiny);
    }

    public boolean updatePokemonSecret(long characterId, long pokemonId, boolean isSecret) {
        return pokemonService.updatePokemonSecret(characterId, pokemonId, isSecret);
    }

    public boolean updatePokemonPersonalityValue(long characterId, long pokemonId, int personalityValue) {
        return pokemonService.updatePokemonPersonalityValue(characterId, pokemonId, personalityValue);
    }

    public boolean updatePokemonOtName(long characterId, long pokemonId, String otName) {
        return pokemonService.updatePokemonOtName(characterId, pokemonId, otName);
    }

    public boolean updatePokemonName(long characterId, long pokemonId, String name) {
        return pokemonService.updatePokemonName(characterId, pokemonId, name);
    }

    public boolean updatePokemonNormalRibbons(long characterId, long pokemonId, boolean[] normalRibbons) {
        return pokemonService.updatePokemonNormalRibbons(characterId, pokemonId, normalRibbons);
    }

    public boolean updatePokemonRibbons(long characterId, long pokemonId, short[] contestRibbons, boolean[] normalRibbons) {
        return pokemonService.updatePokemonRibbons(characterId, pokemonId, contestRibbons, normalRibbons);
    }

    public boolean updatePokemonBallType(long characterId, long pokemonId, short ballType) {
        return pokemonService.updatePokemonBallType(characterId, pokemonId, ballType);
    }

    public boolean updatePokemonParticleEffects(long characterId, long pokemonId, short[] particleEffects) {
        return pokemonService.updatePokemonParticleEffects(characterId, pokemonId, particleEffects);
    }

    public boolean updatePokemonCurrentSelectParticleEffect(long characterId, long pokemonId, short particleEffectId) {
        return pokemonService.updatePokemonCurrentSelectParticleEffect(characterId, pokemonId, particleEffectId);
    }

    public boolean updatePokemonIvs(long characterId, long pokemonId, short[] ivValues) {
        return pokemonService.updatePokemonIvs(characterId, pokemonId, ivValues);
    }

    public boolean updatePokemonHiddenAbility(long characterId, long pokemonId, boolean hasHiddenAbility) {
        return pokemonService.updatePokemonHiddenAbility(characterId, pokemonId, hasHiddenAbility);
    }

    public boolean updatePokemonAbilityIndex(long characterId, long pokemonId, short abilityIndex) {
        return pokemonService.updatePokemonAbilityIndex(characterId, pokemonId, abilityIndex);
    }

    public boolean updatePokemonMoves(long characterId, long pokemonId, short[] moves, short[] movesPp, byte ppUpTimes) {
        return pokemonService.updatePokemonMoves(characterId, pokemonId, moves, movesPp, ppUpTimes);
    }

    public boolean updatePokemonEvs(long characterId, long pokemonId, short[] evValues) {
        return pokemonService.updatePokemonEvs(characterId, pokemonId, evValues);
    }

    public boolean updatePokemonFriendValue(long characterId, long pokemonId, short friendValue) {
        return pokemonService.updatePokemonFriendValue(characterId, pokemonId, friendValue);
    }

    public boolean updatePokemonFormType(long characterId, long pokemonId, short formType) {
        return pokemonService.updatePokemonFormType(characterId, pokemonId, formType);
    }

    public boolean updatePokemonGrowth(long characterId, long pokemonId, int exp, short level, short[] evValues) {
        return pokemonService.updatePokemonGrowth(characterId, pokemonId, exp, level, evValues);
    }

    public boolean updatePokemonItem(long characterId, long pokemonId, int containerId,
                                     short itemIndexId, SnowflakeIdGenerator idGenerator) {
        return pokemonService.updatePokemonItem(characterId, pokemonId, containerId, itemIndexId, idGenerator);
    }

    public List<PokemonData> getCharacterContainerPokemons(long characterId, ContainerRecord container) {
        return pokemonService.getCharacterContainerPokemons(characterId, container);
    }

    public Optional<List<PokemonData>> changePokemonPositions(
            long characterId, List<PokemonPositionChange> changes) {
        return pokemonService.changePokemonPositions(characterId, changes);
    }

    public Optional<List<PokemonData>> sortPokemon(
            long characterId, int containerType, int[] containerIndexes, int sortFlags) {
        return pokemonService.sortPokemon(characterId, containerType, containerIndexes, sortFlags);
    }

    public short findNextFreePartyPosition(long characterId) {
        return pokemonService.findNextFreePartyPosition(characterId);
    }

    public CharacterData getCharacter(long characterId) {
        return characterService.getCharacter(characterId);
    }

    public CharacterData getCharacterByName(String characterName) {
        return characterService.getCharacterByName(characterName);
    }

    public void updateCharacter(CharacterRecord character) {
        characterService.updateCharacter(character);
    }

    public boolean addOnlineMinutes(long characterId, int minutes) {
        return characterService.addOnlineMinutes(characterId, minutes);
    }

    public CharacterService.SafariStart startSafari(long characterId, int fee, short steps, short balls) {
        return characterService.startSafari(characterId, fee, steps, balls);
    }

    public boolean updateSafariState(long characterId, short steps, short balls) {
        return characterService.updateSafariState(characterId, steps, balls);
    }

    public OwnedItemRecord getOwnedItem(long characterId, long ownedItemId) {
        return inventoryService.getOwnedItem(characterId, ownedItemId);
    }

    public OwnedItemRecord getOwnedItemByIndex(long characterId, short itemIndexId) {
        return inventoryService.getOwnedItemByIndex(characterId, itemIndexId);
    }

    public MailService.SendMailResult sendMail(
            long senderId,
            String recipientName,
            String title,
            String body,
            List<MailService.ItemAttachment> itemAttachments,
            List<Long> pokemonObjectIds,
            int moneyAmount,
            SnowflakeIdGenerator idGenerator) {
        return mailService.sendMail(senderId, recipientName, title, body,
                itemAttachments, pokemonObjectIds, moneyAmount, idGenerator);
    }

    public MailService.MailCounts getMailCounts(long characterId) {
        return mailService.getMailCounts(characterId);
    }

    public MailService.MailListPage getMailList(long characterId, boolean sent, int pageIndex) {
        return mailService.getMailList(characterId, sent, pageIndex);
    }

    public MailService.MailDetail getMailDetail(long characterId, long mailId) {
        return mailService.getMailDetail(characterId, mailId);
    }

    public MailService.MailClaimResult claimMail(long characterId, long mailId) {
        return mailService.claimMail(characterId, mailId);
    }

    public MailService.MailClaimResult claimMailAttachment(long characterId, long mailId,
                                                            int slot, byte claimMode) {
        return mailService.claimMailAttachment(characterId, mailId, slot, claimMode);
    }

    public boolean markMailRead(long characterId, long mailId) {
        return mailService.markMailRead(characterId, mailId);
    }

    public MailService.MailDeleteResult deleteMail(long characterId, long mailId) {
        return mailService.deleteMail(characterId, mailId);
    }

    public FriendService.FriendActionResult toggleFriend(
            long characterId, String targetPlayerName) {
        return friendService.toggleFriend(characterId, targetPlayerName);
    }

    public long findFriendCharacterIdByName(String playerName) {
        return friendService.findCharacterIdByName(playerName);
    }

    public boolean isFriend(long characterId, long friendId) {
        return friendService.isFriend(characterId, friendId);
    }

    public boolean addFriendPair(long firstCharacterId, long secondCharacterId) {
        return friendService.addFriendPair(firstCharacterId, secondCharacterId);
    }

    public boolean removeFriend(long characterId, long friendId) {
        return friendService.removeFriend(characterId, friendId);
    }

    public List<FriendService.FriendEntry> getFriendList(long characterId) {
        return friendService.getFriendList(characterId);
    }

    public boolean updateCharacterFollower(long characterId, short pokemonIndexId, short rarity) {
        return characterService.updateCharacterFollower(characterId, pokemonIndexId, rarity);
    }

    public boolean updateCharacterSkin(
            long characterId, SkinType skinType, short skin, short color) {
        return characterService.updateCharacterSkin(
                characterId, skinType, skin, color);
    }

    public List<ContainerRecord> getPokemonContainer() {
        return worldService.getPokemonContainer();
    }

    public List<GameEvent> getGameSideActiveEventFlags(
            long characterId, EventRegionType regionType) {
        return worldService.getGameSideActiveEventFlags(characterId, regionType);
    }

    public List<InventoryRecord> getInventories() {
        return inventoryService.getInventories();
    }

    public List<org.server.node.JoinableSeverData> getOnlineChatNodeServers(int serverNodeId) {
        return worldService.getOnlineChatNodeServers(serverNodeId);
    }

    public List<OwnedItemRecord> getItemsByContainerAndCharacter(
            long characterId, InventoryRecord inventory) {
        return inventoryService.getItemsByContainerAndCharacter(characterId, inventory);
    }

    public OwnedItemRecord addInventoryItem(
            long characterId, short itemIndexId, short amount, long itemId) {
        return inventoryService.addInventoryItem(characterId, itemIndexId, amount, itemId);
    }

    public boolean removeInventoryItem(long characterId, long itemId, short amount) {
        return inventoryService.removeInventoryItem(characterId, itemId, amount);
    }

    public boolean removeStoryItem(long characterId, long itemId, short amount) {
        return inventoryService.removeStoryItem(characterId, itemId, amount);
    }

    public InventoryRecord getInventory() {
        return inventoryService.getInventory();
    }

    public void setServerSideEventStatus(
            long characterId, GameEventType gameEventType, short flag) {
        worldService.setServerSideEventStatus(characterId, gameEventType, flag);
    }

    public void setGameSideEventStatus(
            long characterId, ServerEventType serverEventType, short eventStatus) {
        worldService.setGameSideEventStatus(characterId, serverEventType, eventStatus);
    }

    public BitSet[] getPokemonDexUnlockDataById(long characterId) {
        return worldService.getPokemonDexUnlockDataById(characterId);
    }

    public BitSet[] unlockAllPokemonDex(long characterId) {
        return worldService.unlockAllPokemonDex(characterId);
    }

    public List<GameInstance> getInstaceInfo(long characterId) {
        return worldService.getInstaceInfo(characterId);
    }

    public short findNextFreePcBoxPosition(long characterId) {
        return pokemonService.findNextFreePcBoxPosition(characterId);
    }

    public boolean releasePokemonFromPc(long characterId, long pokemonId) {
        return pokemonService.releasePokemonFromPc(characterId, pokemonId);
    }

    public TradeCompletionResult completeTradeResult(
            long firstId, long secondId, int firstMoney, int secondMoney,
            List<Long> firstPokemonIds, List<Long> secondPokemonIds,
            List<TradeItemOffer> firstItems,
            List<TradeItemOffer> secondItems,
            SnowflakeIdGenerator idGenerator) {
        return tradeService.completeTradeResult(firstId, secondId, firstMoney, secondMoney,
                firstPokemonIds, secondPokemonIds, firstItems, secondItems, idGenerator);
    }

    public boolean completeTrade(long firstId, long secondId, int firstMoney, int secondMoney,
                                 List<Long> firstPokemonIds, List<Long> secondPokemonIds,
                                 List<TradeItemOffer> firstItems,
                                 List<TradeItemOffer> secondItems,
                                 SnowflakeIdGenerator idGenerator) {
        return tradeService.completeTrade(firstId, secondId, firstMoney, secondMoney,
                firstPokemonIds, secondPokemonIds, firstItems, secondItems, idGenerator);
    }

    public boolean completeTrade(long firstId, long secondId, int firstMoney, int secondMoney,
                                 List<Long> firstPokemonIds, List<Long> secondPokemonIds,
                                 List<TradeItemOffer> firstItems,
                                 List<TradeItemOffer> secondItems) {
        return tradeService.completeTrade(firstId, secondId, firstMoney, secondMoney,
                firstPokemonIds, secondPokemonIds, firstItems, secondItems);
    }

    public record PcData(ContainerRecord container, List<PokemonData> pokemons) {
        public PcData {
            pokemons = pokemons == null ? List.of() : List.copyOf(pokemons);
        }
    }
}
