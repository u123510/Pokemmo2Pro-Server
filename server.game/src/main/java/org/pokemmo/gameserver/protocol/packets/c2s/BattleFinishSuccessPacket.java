package org.pokemmo.gameserver.protocol.packets.c2s;
import org.pokemmo.gameserver.game.battle.FactionResultType;
import org.pokemmo.gameserver.game.battle.BattleSpectatingService;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.story.StoryService;
import org.pokemmo.gameserver.game.character.UpdateCharacterSelector;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonStatusType;
import org.pokemmo.gameserver.game.pokemon.UpdatePokemonData;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePlayerInfo;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePokemonDataPacket;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;


public class BattleFinishSuccessPacket extends IncomingPacket {
    private CharacterManager characterManager;
    @Override
    public void decode(ByteBufEx buffer) {

    }

    @Override
    public void handle(Session session) throws Exception {
        characterManager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager == null || characterManager.getBattleManager() == null) {
            return;
        }
        if (characterManager.getBattleManager().isSpectator(session)) {
            BattleSpectatingService.leave(characterManager);
            return;
        }
        byte factionIndex = characterManager.getBattleManager().getFactionIndexBySession(session);
        if (StoryService.onFinishReply(characterManager)) return;
        FactionResultType factionResultType = characterManager.getBattleManager().debutFactions.get(factionIndex).getFactionStatType();
        //说明战斗胜负已分
        UpdateCharacterSelector updateCharacterSelector = new UpdateCharacterSelector.Builder()
                .setCharacterData(characterManager.getCharacterData())
                .setRefreshMoney(true)
                .build();
        session.send(new SendUpdatePlayerInfo(updateCharacterSelector));
            //我方胜利
        if(factionResultType == FactionResultType.VICTORY){
            //TODO 结算胜利与经验获取
        }
        //说明我方失败
        if(factionResultType == FactionResultType.DEFEAT){
            //TODO 结算战败
            PokemonData[] partyPokemons =  characterManager.getPartyPokemons();
            for(PokemonData partyPokemon:partyPokemons){
                if(partyPokemon !=null){
                    //重新设置宝可梦的技能pp
                    for(int i = 0;i < 4;i++){
                        partyPokemon.setMoveRemainPpByPos(i,partyPokemon.getPokemonMoveMaxPp(i));
                    }
                    //重新设置宝可梦的当前生命值为最大生命值
                    partyPokemon.setCurrentHp(partyPokemon.getMaxHp());
                    partyPokemon.setPokemonStatus(PokemonStatusType.NORMAL);
                    UpdatePokemonData updatePokemonData = new UpdatePokemonData.Builder()
                            .setUpdatePokemon(partyPokemon)
                            .setIsReloadPokemonMove(true)
                            .setIsReloadPokemonCurrentHp(true)
                            .setIsReloadPokemonStatus(true)
                            .build();
                    session.send(new SendUpdatePokemonDataPacket(updatePokemonData));
                }
            }
        }
        characterManager.setBattleManager(null);
    }



        /*List<BattleString> battleStringList_0 = new ArrayList<>(0);
        battleStringList_0.add(new BattleString(BattleStringType.NULL_TYPE));
        List<BattleString> battleStringList_1 = new ArrayList<>(0);
        battleStringList_0.add(new BattleString(BattleStringType.NULL_TYPE));
        List<BattleString> battleStringList_2 = new ArrayList<>(0);
        //结算玩家金钱

        //关闭对战
        if(isEnemyFactionAllDied) {
            debutFactions[userFaction].setFactionStatType(FactionResultType.VICTORY);
            debutFactions[targetFaction].setFactionStatType(FactionResultType.DEFEAT);
            session.send(new SendBattleFinishPacket(userFaction,battleStringList_0,battleStringList_1,0,0,0,battleStringList_2));
        }
        if(isSelfFactionAllDied) {
            debutFactions[userFaction].setFactionStatType(FactionResultType.DEFEAT);
            debutFactions[targetFaction].setFactionStatType(FactionResultType.VICTORY);
            session.send(new SendBattleFinishPacket(targetFaction,battleStringList_0,battleStringList_1,0,0,0,battleStringList_2));
        }*/

}
