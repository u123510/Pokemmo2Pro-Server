package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.db.jooq.tables.records.OwnedItemRecord;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.character.PlayerVisibilityService;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.skin.SkinType;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendCharacterSkinPacket;
import org.pokemmo.gameserver.services.GameServerService;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

@Slf4j
public class ResetCharacterClothesPacket extends IncomingPacket {
    private byte skinTypeValue;
    private boolean usesDirectAddonId;
    private short addonId;
    private long ownedItemId;

    @Inject
    private GameServerService gameServerService;

    @Override
    public void decode(ByteBufEx buffer) {
        skinTypeValue = buffer.readByte();
        usesDirectAddonId = buffer.readBoolean();
        if (usesDirectAddonId) {
            addonId = buffer.readShortLE();
        } else {
            ownedItemId = buffer.readLongLE();
        }
    }

    @Override
    public void handle(Session session) throws Exception {
        CharacterManager characterManager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager == null || characterManager.getCharacterData() == null
                || characterManager.getCharacterData().getPlayerEntity() == null) {
            log.warn("忽略没有角色上下文的换装封包");
            return;
        }

        SkinType skinType = SkinType.getByType(skinTypeValue & 0xFF);
        if (skinType == null || skinType == SkinType.FISHING_ROD) {
            log.warn("忽略非法服装槽位: {}", skinTypeValue & 0xFF);
            return;
        }

        long characterId = characterManager.getCharacterData().getPlayerEntity().getEntityGameId();
        short skin;
        short color;
        if (usesDirectAddonId) {
            skin = addonId;
            color = -1;
        } else if (ownedItemId == 0) {
            // The client uses a zero owned-item ID to clear the selected slot.
            skin = -1;
            color = -1;
        } else {
            if (TradeManager.isItemOffered(characterManager, ownedItemId)) {
                log.warn("角色 {} 尝试装备已报价道具 ownedItemId={}", characterId, ownedItemId);
                return;
            }
            OwnedItemRecord ownedItem = gameServerService.getOwnedItem(characterId, ownedItemId);
            if (ownedItem == null || ownedItem.getItemIndexId() == null
                    || ownedItem.getItemAmount() == null || ownedItem.getItemAmount() < 1) {
                log.warn("角色 {} 尝试装备不存在或不属于自己的时装 ownedItemId={}", characterId, ownedItemId);
                return;
            }
            skin = skinType.getAddonIdFromItemIndex(ownedItem.getItemIndexId());
            color = skinType == SkinType.BIKE || ownedItem.getColorId() == null
                    ? -1
                    : ownedItem.getColorId();
            if (skin < 0) {
                log.warn("无法将时装 itemIndexId={} 映射到槽位 {}", ownedItem.getItemIndexId(), skinType);
                return;
            }
        }

        if (skin < -1 || skin > 1022 || color < -1 || color > 62
                || !gameServerService.updateCharacterSkin(characterId, skinType, skin, color)) {
            log.warn("角色 {} 更新服装失败: slot={}, skin={}, color={}", characterId, skinType, skin, color);
            return;
        }

        skinType.setSkin(characterManager.getCharacterData(), skin);
        skinType.setColor(characterManager.getCharacterData(), color);
        SendCharacterSkinPacket packet = new SendCharacterSkinPacket(characterId, skinType, skin, color);
        session.send(packet);
        MapData currentMap = characterManager.getCurrentMapDatas()[0];
        if (currentMap != null) {
            short channel = characterManager.getCharacterData().getChannel();
            for (Session targetSession : currentMap.getPlayerSessionPool().values()) {
                if (targetSession == session || !targetSession.isActive()) {
                    continue;
                }
                CharacterManager targetManager = targetSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
                if (targetManager == null || targetManager.getCharacterData() == null
                        || targetManager.getCharacterData().getChannel() != channel
                        || targetManager.getCurrentMapDatas()[0] == null
                        || !targetManager.getCurrentMapDatas()[0].equals(currentMap)) {
                    continue;
                }
                PlayerVisibilityService.sendIfVisible(characterManager, targetSession, packet);
            }
        }
    }
}
