package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.battle.BattleRequestManager;
import org.pokemmo.gameserver.game.friend.FriendManager;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.pokemmo.gameserver.game.interact.PcInteractionService;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.game.story.StoryService;

@Slf4j
public class InteractPacket extends IncomingPacket {
    private byte interactTimes;
    private byte type;

    @Inject
    private PcInteractionService pcInteractionService;

    @Override
    public boolean isLongRunning() {
        return true;
    }

    @Override
    public void decode(ByteBufEx buffer) {
        if (buffer.readableBytes() != 2) throw new IllegalArgumentException("交互回执必须为两个字节");
        interactTimes = buffer.readByte();
        type = buffer.readByte();
    }
    @Override
    public void handle(Session session) throws Exception {
        CharacterManager characterManager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager == null || characterManager.getCharacterData() == null
                || characterManager.getCharacterData().getPlayerEntity() == null) {
            return;
        }
        if (characterManager.getInteractManager().getInteractType() == InteractType.STORY) {
            StoryService.onReply(characterManager, session, interactTimes, type);
            return;
        }
        //增加交互次数
        characterManager.getInteractManager().addInteractTimes();
        if (characterManager.getInteractManager().getInteractType() == InteractType.FRIEND_REQUEST) {
            FriendManager.handleRequestDecision(
                    characterManager, type, characterManager.getCharacterService());
            return;
        }
        if (characterManager.getInteractManager().getInteractType() == InteractType.TRADE_REQUEST) {
            TradeManager.handleRequestDecision(characterManager, type);
            return;
        }
        if (characterManager.getInteractManager().getInteractType() == InteractType.BATTLE_REQUEST) {
            BattleRequestManager.handleRequestDecision(characterManager, type);
            return;
        }
        if (characterManager.getInteractManager().getInteractType() == InteractType.PC_MENU) {
            handlePcMenu(session, characterManager);
            return;
        }
        // No fallback to the removed legacy story graph.
    }

    private void handlePcMenu(Session session, CharacterManager characterManager) {
        int selection = Byte.toUnsignedInt(type);
        if (selection < 1 || selection > 4) {
            clearInteractData(session);
            return;
        }
        characterManager.getInteractManager().setMailWidgetOpen(false);
        try {
            switch (selection) {
                case 1 -> pcInteractionService.openPc(session, characterManager);
                case 2 -> pcInteractionService.openGtl(session);
                case 3 -> pcInteractionService.openEmail(session, characterManager);
                case 4 -> {
                    // Cancel only closes the menu.
                }
                default -> throw new AssertionError(selection);
            }
        } catch (RuntimeException exception) {
            log.error("处理电脑菜单选项失败: selection={}", selection, exception);
        } finally {
            clearInteractData(session);
        }
    }
    private void clearInteractData(Session session){
        //清空当前交互脚本
        session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getInteractManager().setCurrentInteractScript(null);
        //清空当前脚本节点
        session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getInteractManager().setCurrentScript(null);
        //清空当前对话交互实体id
        session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getInteractManager().clearLastInteractorEntityId();
        //设置当前交互类型为非对话
        session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getInteractManager().setInteractType(InteractType.NONE);
    }
}
