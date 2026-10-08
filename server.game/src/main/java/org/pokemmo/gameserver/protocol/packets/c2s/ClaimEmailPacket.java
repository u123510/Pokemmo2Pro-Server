package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.character.UpdateCharacterSelector;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendEmailDetailPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendEmailClaimResultPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendEmailListPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendGameMailPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendInventoryPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendPokemonContainerPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePlayerInfo;
import org.pokemmo.gameserver.services.GameServerService;
import org.pokemmo.gameserver.services.mail.MailService;
import org.server.IncomingPacket;
import org.server.Packet;
import org.server.Session;
import org.server.bytes.ByteBufEx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** C2S 0x98: claims one attachment from a received mail. */
@Slf4j
public final class ClaimEmailPacket extends IncomingPacket {
    private static final int PAYLOAD_SIZE = Long.BYTES + 1 + 1 + Short.BYTES + 1;

    private long mailId;
    private int slot;
    private short pageIndex;
    private int claimMode;

    @Inject
    private GameServerService gameServerService;

    @Override
    public void decode(ByteBufEx buffer) {
        if (buffer.readableBytes() != PAYLOAD_SIZE) {
            throw new IllegalArgumentException(
                    "邮件附件领取请求长度错误: " + buffer.readableBytes());
        }
        mailId = buffer.readLongLE();
        slot = buffer.readUnsignedByte();
        int hasOptionalData = buffer.readUnsignedByte();
        if (hasOptionalData != 0) {
            throw new IllegalArgumentException("暂不支持带扩展数据的邮件附件领取请求");
        }
        pageIndex = buffer.readShortLE();
        // The client calls this byte an operation mode. It is 0 for the
        // default claim action and 1/2 for the Pokemon destination actions;
        // the attachment kind is determined by the selected slot in the
        // detail response, not by this byte.
        claimMode = buffer.readUnsignedByte();
        if (claimMode > 2) {
            throw new IllegalArgumentException("邮件领取操作模式必须为 0、1 或 2: " + claimMode);
        }
    }

    @Override
    public void handle(Session session) {
        CharacterManager characterManager =
                session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager == null || characterManager.getCharacterData() == null
                || characterManager.getCharacterData().getPlayerEntity() == null) {
            log.warn("收到邮件附件领取请求，但当前 Session 没有已加载角色");
            return;
        }
        if (mailId <= 0 || pageIndex < 0) {
            log.warn("忽略非法邮件附件领取请求: mailId={}, slot={}, pageIndex={}, mode={}",
                    mailId, slot, pageIndex, claimMode);
            return;
        }

        long characterId = characterManager.getCharacterData()
                .getPlayerEntity().getEntityGameId();
        MailService.MailClaimResult result = gameServerService.claimMailAttachment(
                characterId, mailId, slot, (byte) claimMode);
        if (result.success()) {
            characterManager.getCharacterData().setMoney(result.remainingMoney());
            refreshOnlineContainers(characterManager, characterId);
        } else {
            log.debug("邮件附件领取未更新记录: characterId={}, mailId={}, slot={}, mode={}",
                    characterId, mailId, slot, claimMode);
        }

        MailService.MailCounts counts = gameServerService.getMailCounts(characterId);
        MailService.MailListPage page = gameServerService.getMailList(characterId, false, pageIndex);
        MailService.MailDetail detail = gameServerService.getMailDetail(characterId, mailId);

        List<Packet> packets = new ArrayList<>();
        if (result.success()) {
            // The client handles S2C 0x9A immediately and disables the
            // claimed slot before the refreshed detail arrives.
            packets.add(new SendEmailClaimResultPacket(mailId, slot));
            UpdateCharacterSelector moneyUpdate = new UpdateCharacterSelector.Builder()
                    .setRefreshMoney(true)
                    .setCharacterData(characterManager.getCharacterData())
                    .build();
            packets.add(new SendUpdatePlayerInfo(moneyUpdate));
            var inventory = gameServerService.getInventory();
            if (inventory != null) {
                packets.add(new SendInventoryPacket(inventory,
                        gameServerService.getItemsByContainerAndCharacter(characterId, inventory)));
            }
            addPokemonContainerRefresh(packets, characterId, PokemonContainerType.PARTY);
            addPokemonContainerRefresh(packets, characterId, PokemonContainerType.PC);
        }
        packets.add(new SendGameMailPacket(counts.received(), counts.unread(), counts.sent()));
        packets.add(new SendEmailListPacket(pageIndex, false, page.entries()));
        packets.add(new SendEmailDetailPacket(detail));
        session.send(packets.toArray(Packet[]::new));
    }

    private void refreshOnlineContainers(CharacterManager characterManager, long characterId) {
        PokemonData[] partyPokemons = characterManager.getPartyPokemons();
        Arrays.fill(partyPokemons, null);
        var partyContainer = gameServerService.getContainerByType(PokemonContainerType.PARTY);
        if (partyContainer == null) {
            return;
        }
        List<PokemonData> party = gameServerService.getCharacterContainerPokemons(
                characterId, partyContainer);
        for (PokemonData pokemon : party) {
            short position = pokemon.getContainerPosition();
            if (position >= 0 && position < partyPokemons.length) {
                partyPokemons[position] = pokemon;
            }
        }
    }

    private void addPokemonContainerRefresh(List<Packet> packets, long characterId,
                                             PokemonContainerType containerType) {
        var container = gameServerService.getContainerByType(containerType);
        if (container != null) {
            packets.add(new SendPokemonContainerPacket(container,
                    gameServerService.getCharacterContainerPokemons(characterId, container)));
        }
    }
}
