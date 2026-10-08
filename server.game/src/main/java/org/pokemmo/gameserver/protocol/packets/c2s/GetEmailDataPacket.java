package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.character.UpdateCharacterSelector;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendGameMailPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendEmailResultPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendInventoryPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendPokemonContainerPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePlayerInfo;
import org.pokemmo.gameserver.services.GameServerService;
import org.pokemmo.gameserver.services.mail.MailService;
import org.pokemmo.gameserver.game.pool.GameSessionPool;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the client request used to send a mail with attachments.
 *
 * <p>The item attachment carries an owned-item Object ID. The item index ID
 * (for example 1476) is stored in {@code owned_item.item_index_id} and is not
 * present in this packet.</p>
 */
@Slf4j
public final class GetEmailDataPacket extends IncomingPacket {
    private String recipientName;
    private String title;
    private String body;
    private final List<ItemAttachment> itemAttachments = new ArrayList<>();
    private final List<Long> pokemonObjectIds = new ArrayList<>();
    private int moneyAmount;
    private boolean moneyAttachmentSeen;

    @Inject
    private GameServerService gameServerService;

    @Override
    public void decode(ByteBufEx buffer) {
        recipientName = buffer.readUtf16LE();
        title = buffer.readUtf16LE();
        body = buffer.readUtf16LE();

        int attachmentCount = buffer.readUnsignedByte();
        if (attachmentCount > 64) {
            throw new IllegalArgumentException("邮件附件数量不能超过 64: " + attachmentCount);
        }
        for (int i = 0; i < attachmentCount; i++) {
            int type = buffer.readUnsignedByte();
            switch (type) {
                case 0 -> {
                    long itemObjectId = buffer.readLongLE();
                    short amount = buffer.readShortLE();
                    if (itemObjectId <= 0 || amount <= 0) {
                        throw new IllegalArgumentException(
                                "道具附件 Object ID 和数量必须为正数: objectId="
                                        + itemObjectId + ", amount=" + amount);
                    }
                    itemAttachments.add(new ItemAttachment(itemObjectId, amount));
                }
                case 1 -> {
                    long pokemonObjectId = buffer.readLongLE();
                    if (pokemonObjectId <= 0) {
                        throw new IllegalArgumentException(
                                "精灵附件 Object ID 必须为正数: " + pokemonObjectId);
                    }
                    pokemonObjectIds.add(pokemonObjectId);
                }
                case 2 -> {
                    if (moneyAttachmentSeen) {
                        throw new IllegalArgumentException("邮件只能包含一个金钱附件");
                    }
                    moneyAttachmentSeen = true;
                    moneyAmount = buffer.readIntLE();
                    if (moneyAmount <= 0) {
                        throw new IllegalArgumentException(
                                "邮件金钱数量必须为正数: " + moneyAmount);
                    }
                }
                default -> throw new IllegalArgumentException(
                        "未知邮件附件类型: " + type);
            }
        }
    }

    @Override
    public void handle(Session session) {
        CharacterManager characterManager =
                session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager == null || characterManager.getCharacterData() == null
                || characterManager.getCharacterData().getPlayerEntity() == null) {
            log.warn("收到邮件发送请求，但当前 Session 没有已加载角色");
            session.send(new SendEmailResultPacket(SendEmailResultPacket.REJECTED));
            return;
        }

        if (!characterManager.getInteractManager().isMailWidgetOpen()) {
            log.warn("拒绝发送邮件：当前会话未通过 PC 打开邮箱");
            session.send(new SendEmailResultPacket(SendEmailResultPacket.REJECTED));
            return;
        }

        long senderCharacterId =
                characterManager.getCharacterData().getPlayerEntity().getEntityGameId();
        log.info("邮件发送请求: senderCharacterId={}, recipient={}, title={}, body={}, "
                        + "itemAttachments={}, pokemonObjectIds={}, moneyAmount={}",
                senderCharacterId, recipientName, title, body, itemAttachments,
                pokemonObjectIds, moneyAmount);

        for (ItemAttachment attachment : itemAttachments) {
            if (TradeManager.isItemOffered(characterManager, attachment.itemObjectId())) {
                log.warn("拒绝发送交易中的道具: itemObjectId={}", attachment.itemObjectId());
                session.send(new SendEmailResultPacket(SendEmailResultPacket.REJECTED));
                return;
            }
        }
        for (Long pokemonObjectId : pokemonObjectIds) {
            if (TradeManager.isPokemonOffered(characterManager, pokemonObjectId)) {
                log.warn("拒绝发送交易中的精灵: pokemonObjectId={}", pokemonObjectId);
                session.send(new SendEmailResultPacket(SendEmailResultPacket.REJECTED));
                return;
            }
        }

        List<MailService.ItemAttachment> requestedItems = itemAttachments.stream()
                .map(attachment -> new MailService.ItemAttachment(
                        attachment.itemObjectId(), attachment.amount()))
                .toList();
        MailService.SendMailResult result = gameServerService.sendMail(
                senderCharacterId,
                recipientName,
                title,
                body,
                requestedItems,
                List.copyOf(pokemonObjectIds),
                moneyAmount,
                characterManager.getSnowflakeIdGenerator());
        if (!result.success()) {
            log.warn("邮件发送失败: senderCharacterId={}, recipient={}",
                    senderCharacterId, recipientName);
            session.send(new SendEmailResultPacket(SendEmailResultPacket.REJECTED));
            return;
        }
        log.info("邮件发送成功: mailId={}, senderCharacterId={}, recipientCharacterId={}, "
                        + "remainingMoney={}, itemCount={}, pokemonCount={}",
                result.mailId(), senderCharacterId, result.recipientId(),
                result.remainingMoney(), itemAttachments.size(), pokemonObjectIds.size());

        // The client keeps the compose form disabled until it receives S2C 0x96.
        session.send(new SendEmailResultPacket(SendEmailResultPacket.SUCCESS));

        characterManager.getCharacterData().setMoney(result.remainingMoney());
        UpdateCharacterSelector moneyUpdate = new UpdateCharacterSelector.Builder()
                .setRefreshMoney(true)
                .setCharacterData(characterManager.getCharacterData())
                .build();

        for (int index = 0; index < characterManager.getPartyPokemons().length; index++) {
            if (characterManager.getPartyPokemons()[index] != null
                    && pokemonObjectIds.contains(
                    characterManager.getPartyPokemons()[index].getPokemonId())) {
                characterManager.getPartyPokemons()[index] = null;
            }
        }

        var inventory = gameServerService.getInventory();
        if (inventory == null) {
            log.error("邮件发送成功但主背包容器不存在: senderCharacterId={}", senderCharacterId);
            session.send(new SendUpdatePlayerInfo(moneyUpdate));
        } else {
            session.send(new SendUpdatePlayerInfo(moneyUpdate),
                    new SendInventoryPacket(inventory,
                            gameServerService.getItemsByContainerAndCharacter(
                                    senderCharacterId, inventory)));
        }

        PokemonContainerType partyType = PokemonContainerType.PARTY;
        var party = gameServerService.getContainerByType(partyType);
        if (party != null) {
            session.send(new SendPokemonContainerPacket(
                    party, gameServerService.getCharacterContainerPokemons(senderCharacterId, party)));
        }
        PokemonContainerType pcType = PokemonContainerType.PC;
        var pc = gameServerService.getContainerByType(pcType);
        if (pc != null) {
            session.send(new SendPokemonContainerPacket(
                    pc, gameServerService.getCharacterContainerPokemons(senderCharacterId, pc)));
        }

        MailService.MailCounts senderCounts = gameServerService.getMailCounts(senderCharacterId);
        session.send(new SendGameMailPacket(senderCounts.received(), senderCounts.unread(),
                senderCounts.sent()));

        Session recipientSession = GameSessionPool.getPlayerSessionInPool(result.recipientId());
        if (recipientSession != null && recipientSession != session && recipientSession.isActive()) {
            MailService.MailCounts recipientCounts = gameServerService.getMailCounts(result.recipientId());
            recipientSession.send(new SendGameMailPacket(recipientCounts.received(),
                    recipientCounts.unread(), recipientCounts.sent()));
        }
    }

    private record ItemAttachment(long itemObjectId, short amount) {
    }
}
