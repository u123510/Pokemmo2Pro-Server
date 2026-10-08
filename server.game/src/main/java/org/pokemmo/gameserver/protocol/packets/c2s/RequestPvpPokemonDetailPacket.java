package org.pokemmo.gameserver.protocol.packets.c2s;

import lombok.Getter;
import lombok.ToString;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/**
 * 客户端请求指定宝可梦在 PvP 中的详细配置与使用率分布 (C2S 0x4F, 对应客户端 f.Rr0)
 */
@Getter
@ToString
public class RequestPvpPokemonDetailPacket extends IncomingPacket {

    private byte month;
    private byte category;
    private byte tierId;
    private short pokemonId;

    @Override
    public void decode(ByteBufEx buffer) {
        this.month = buffer.readByte();
        this.category = buffer.readByte();
        this.tierId = buffer.readByte();
        this.pokemonId = buffer.readShortLE();
    }

    @Override
    public void handle(Session session) throws Exception {
        // 当前为 0 场次初始状态，暂无单只宝可梦详细配置
    }
}
