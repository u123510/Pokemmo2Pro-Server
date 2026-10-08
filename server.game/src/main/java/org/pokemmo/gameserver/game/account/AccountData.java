package org.pokemmo.gameserver.game.account;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.server.union.language.LanguageType;
import org.pokemmo.gameserver.game.platform.CpuArchitectureType;
import org.pokemmo.gameserver.game.platform.CpuBitType;
import org.pokemmo.gameserver.game.platform.PlatformType;
import org.pokemmo.gameserver.game.rom.RomInfo;

import java.util.List;
import java.util.Map;

@Getter @Setter
@RequiredArgsConstructor
public class AccountData {
    //账户的id
    private final int accountId;
    //登录的节点id
    private final short loginNodeId;
    //登录的mac
    private final byte[] mac;
    //登录的客户端版本
    private final int clientRevision;
    // revision.txt 中的游戏版本
    private final int installationRevision;
    // 账户国家类型
    private final LanguageType countryType;
    // 账户登录客户端的rom信息
    private List<RomInfo> roms;
    // 账户登录的客户端信息
    private final Map<Byte, String> clientInfo;
    // 账户登录的客户端平台类型
    private final PlatformType platformType;
    // 账户登录的客户端指令集类型
    private final CpuArchitectureType cpuArchitectureType;
    // 账户登录的客户端系统位数
    private final CpuBitType cpuBitType;
}
