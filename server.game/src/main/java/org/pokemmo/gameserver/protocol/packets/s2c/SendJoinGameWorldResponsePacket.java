package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

public class SendJoinGameWorldResponsePacket extends OutgoingPacket {
  private final boolean success;
  private final int donorTimeStamp;
  private final int rewardPoints;
  private final int specRewardPoint;
  private final int gameDateBeginTimeStamp;
  private final int timeStamp; // current timestamp

  public SendJoinGameWorldResponsePacket(boolean success) {
    this.success = success;
    this.donorTimeStamp =  (int) (System.currentTimeMillis() / 1000)+7*24*3600;// 7 days donor
    this.rewardPoints = 0;
    this.specRewardPoint = 0;
    this.gameDateBeginTimeStamp = 1640995200; // 2022-01-01 08:00:00
    this.timeStamp = (int) (System.currentTimeMillis() / 1000);
  }


  @Override
  public void encode(ByteBufEx buffer) throws Exception {
    buffer.writeBoolean(success);
    if (!success) return;
    buffer.writeUtf16LE(""); // unused since rev. 22215
    buffer.writeByte(0);//unused
    buffer.writeIntLE(donorTimeStamp);
    buffer.writeIntLE(rewardPoints);
    buffer.writeIntLE(specRewardPoint);
    buffer.writeIntLE(gameDateBeginTimeStamp);
    buffer.writeIntLE(timeStamp);
  }
}
