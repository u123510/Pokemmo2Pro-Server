package org.pokemmo.loginserver.protocol.packets.s2c;

import org.pokemmo.loginserver.service.LoginService;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.server.node.ServerNode;

import java.util.List;
//发送游戏节点列表，只发送节点的名称，负载占比，是否可以加入游戏 这种基本数据
public class SendGameNodeListPacket extends OutgoingPacket {
  private final List<ServerNode> gameNodes;

  public SendGameNodeListPacket(List<ServerNode> gameNodes) {
    this.gameNodes = gameNodes;
  }

  @Override
  public void encode(ByteBufEx buffer){
    assert gameNodes.size() <= Byte.MAX_VALUE;
    buffer.writeByte(gameNodes.size());
    if (gameNodes.isEmpty()) {
      buffer.writeByte(0);
      buffer.writeByte(0);
      return;
    }
    ServerNode first = gameNodes.get(0);
    buffer.writeByte(first.getId());
    for (ServerNode gameNode : gameNodes) {
      buffer.writeByte(gameNode.getId());
      buffer.writeUtf16LE(gameNode.getName());
      buffer.writeShortLE(1); // current Load
      buffer.writeShortLE(100); // max Load
      buffer.writeBoolean(gameNode.isJoinAble());
    }
  }
}