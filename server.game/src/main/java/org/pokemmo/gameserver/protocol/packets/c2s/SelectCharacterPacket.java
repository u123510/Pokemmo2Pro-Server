package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import org.jooq.postgres.extensions.types.Inet;
import org.pokemmo.db.jooq.tables.records.*;
import org.pokemmo.gameserver.game.account.AccountData;
import org.pokemmo.gameserver.game.character.CharacterData;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.events.EventRegionType;
import org.pokemmo.gameserver.game.kick.KickType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonDexData;
import org.pokemmo.gameserver.game.pokemon.PokemonManager;
import org.pokemmo.gameserver.game.pool.GameSessionPool;
import org.pokemmo.gameserver.protocol.packets.s2c.*;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.game.building.BulidingType;

import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.string.GameLocalFormatString;
import org.pokemmo.gameserver.game.string.GameMassageString;

import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.services.GameServerService;
import mmo.Util;
import lombok.extern.slf4j.Slf4j;
import org.server.services.ServerService;
import org.server.union.chat.ChatMessage;
import org.server.union.chat.ChatType;
import org.server.util.IpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class SelectCharacterPacket extends IncomingPacket {
    @Inject
    private GameServerService gameServerService;
    @Inject
    private ServerService serverService;
    private long characterId;
    private long characterIdHash;
    //当前登录的地图数据
    private MapData loginMapData;
    @Override
    public void decode(ByteBufEx buffer) {
        this.characterId = buffer.readLongLE();
        this.characterIdHash = buffer.readLongLE();
    }
    @Override
    public void handle(Session session) throws Exception {
        CharacterManager characterManager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        AccountData accountData = characterManager.getAccountData();
        //校验哈希值是否正确
        if(characterIdHash != Util.sigHash( java.lang.Long.hashCode(characterId) & 0xFFFFFFFF)){
            //玩家id被篡改了，拒绝登录
            session.close();
            return;
        }
        //将玩家会话添加到玩家会话池
        GameSessionPool.addPlayerSessionInPool(characterId,session);
        //根据玩家的id获取玩家对象
        CharacterData character = gameServerService.getCharacter(characterId);
        //更新玩家的上下文
        Inet ipv4 = IpUtil.getLocalIPv4Inet();
        short gameServerId = (short)gameServerService.getGameServerIdByIpv4(ipv4);
        //检测服务器的id是否正常
        if(gameServerId ==-1){
            //服务器id异常的，拒绝登录
            session.send(new SendKickInGamePacket(KickType.SERVER_SHUTDOWN));
            session.close();
            return;
        }
        if (character == null || character.getAccountId() != accountData.getAccountId()) {
            log.warn("用户 {} 尝试选择 {} 一个不属于他的角色", accountData.getAccountId(),characterId);
            session.send(new SendKickInGamePacket(KickType.OTHER_ERROR));
            session.close();
            return;
        }
        //更新账号的上下文
        AccountContextRecord accountContextRecord = new AccountContextRecord();
        accountContextRecord.setAccountId(accountData.getAccountId());
        accountContextRecord.setCharacterId(characterId);
        accountContextRecord.setServerId(gameServerId);
        serverService.updateAccountContext(accountContextRecord);
        //设置服务的角色对象
        characterManager.setCharacterData(character);
        characterManager.startOnlineSession();
        //对队伍宝可梦进行填充
        List<PokemonData> partyPokemons = gameServerService.getCharacterContainerPokemons(characterId, gameServerService.getContainerByType(PokemonContainerType.PARTY));
        for(PokemonData partyPokemon : partyPokemons){
            characterManager.getPartyPokemons()[partyPokemon.getContainerPosition()] = partyPokemon;
        }
        //设置交互次数为0
        characterManager.getInteractManager().setInteractTimes((byte)0);
        characterManager.handleLoadGameWorldContext();
    }
}
