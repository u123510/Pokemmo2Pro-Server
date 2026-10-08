package org.pokemmo.gameserver.game.player;

import lombok.Getter;
import lombok.Setter;
import org.server.Session;
import org.pokemmo.gameserver.protocol.packets.s2c.SendShortHeartBeatPacket;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
@Getter @Setter
public class ServerHeartBeatThread {
    private final Session playerSession;
    private boolean isClientTimeAccelerated = false;
    private long clinetHeartbeatTimeStamp = 0;
    public void run() {
        //确保线程首次运行时，客户端心跳时间戳正常的
        clinetHeartbeatTimeStamp = System.currentTimeMillis();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        // 设置为每分钟发送一次心跳（初始延迟1秒，然后每60秒执行一次）
        executor.scheduleAtFixedRate(() -> {
            try {
                if (playerSession.isActive() && !isClientTimeAccelerated)
                {
                    playerSession.send(new SendShortHeartBeatPacket(true));
                    // 检查心跳超时（超过2分钟没收到心跳就断开连接）
                    if (System.currentTimeMillis() - clinetHeartbeatTimeStamp > 2 * 60 * 1000) {
                        executor.shutdown();
                        playerSession.close();
                    }
                }
                else
                {
                   if(isClientTimeAccelerated) {
                       //加入封号名单
                   }
                    executor.shutdown();
                }
            } catch (Exception e) {
                executor.shutdown();
            }
        }, 60, 60, TimeUnit.SECONDS); // 初始延迟60秒，然后每60秒执行一次
    }
    public ServerHeartBeatThread(Session playerSession) {
        this.playerSession = playerSession;
    }
    public void start() {
        this.run();
    }
}
