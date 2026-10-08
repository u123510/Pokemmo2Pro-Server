package org.pokemmo.gameserver.protocol.packets.c2s;
import com.google.inject.Inject;
import org.pokemmo.gameserver.game.interact.SceneInteractionService;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;
import mmo.Util;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StartTalkPacket extends IncomingPacket {
    private long npcGameId;
    private long hash;
    private final SceneInteractionService sceneInteractionService;

    @Inject
    public StartTalkPacket(SceneInteractionService sceneInteractionService) {
        this.sceneInteractionService = sceneInteractionService;
    }

    @Override
    public boolean isLongRunning() {
        return true;
    }

    @Override
    public void decode(ByteBufEx buffer) {
        if (buffer.readableBytes() != 16) {
            throw new IllegalArgumentException("NPC 对话请求必须携带 16 字节数据");
        }
        npcGameId =buffer.readLongLE();
        hash = buffer.readLongLE();
    }
    @Override
    public void handle(Session session) throws Exception {
        if(hash != Util.sigHash( java.lang.Long.hashCode(npcGameId) & 0xFFFFFFFF)){
            log.warn("NPC 对话请求哈希校验失败: npcId={}", npcGameId);
            session.close();
            //TODO: 说明哈希校验失败，标记封号
            return;
        }
        sceneInteractionService.handleNpcInteraction(session, npcGameId);
    }
}
