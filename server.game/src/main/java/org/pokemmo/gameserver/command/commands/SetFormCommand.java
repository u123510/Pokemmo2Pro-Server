package org.pokemmo.gameserver.command.commands;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.db.jooq.tables.records.ContainerRecord;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.character.CharacterData;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.character.PlayerVisibilityService;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.entity.PlayerEntity;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonFormCatalog;
import org.pokemmo.gameserver.game.pokemon.UpdatePokemonData;
import org.pokemmo.gameserver.game.pool.GameSessionPool;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendPokemonContainerPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendSetFollowPokemonPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePokemonDataPacket;
import org.server.Session;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 设置与查看队伍宝可梦形态的管理命令。
 * <p>
 * 语法支持：
 * 1. 切换形态：
 *    - {@code //setform <槽位 0-5> <形态 0-255>} - 修改自身队伍槽位宝可梦形态；
 *    - {@code //setform <玩家名> <槽位 0-5> <形态 0-255>} - 修改指定玩家队伍槽位宝可梦形态。
 * 2. 形态查询（子命令 list）：
 *    - {@code //setform list} - 查看自身队伍 6 个槽位形态概览；
 *    - {@code //setform list <槽位 0-5>} - 查看自身指定槽位宝可梦形态及支持的全部形态变化列表；
 *    - {@code //setform list <玩家名>} - 查看指定玩家队伍 6 个槽位形态概览；
 *    - {@code //setform list <玩家名> <槽位 0-5>} - 查看指定玩家指定槽位形态变化列表。
 * <p>
 * 同步链路：
 * 1. 数据库持久化：带 trainerId 与 pokemonId 双重条件更新 {@code pokemon.form_type}；
 * 2. 内存更新：更新 {@link PokemonData#setFormType(short)} 与队伍槽位；
 * 3. 0x16 增量包：发送 {@link SendUpdatePokemonDataPacket}（重载 IndexId 与 FormType），触发客户端重新载入 3D/2D 模型；
 * 4. 0x14 容器包：发送 {@link SendPokemonContainerPacket} 刷新背包与队伍界面；
 * 5. 跟随外观联动：若修改的宝可梦当前正在跟随，重新计算外观稀有度（低 5 位为 formType），写库并广播视野同步。
 */
@Slf4j
public class SetFormCommand implements Command {
    private static final int MIN_FORM = 0;
    private static final int MAX_FORM = 255;
    private static final int FORM_MASK = 0x1F;
    private static final int FEMALE_FLAG = 0x20;
    private static final int SHINY_FLAG = 0x40;
    private static final int ALPHA_FLAG = 0x80;

    @Inject
    public SetFormCommand() {
    }

    @Override
    public String getName() {
        return "setform";
    }

    @Override
    public Set<String> getAliases() {
        return Set.of("form");
    }

    @Override
    public String getUsage() {
        return "//setform [玩家名称] <队伍槽位 0-5> <形态 0-255> | //setform list [玩家名称] [队伍槽位 0-5]";
    }

    @Override
    public void execute(CommandContext context, String[] arguments) {
        if (arguments.length == 0) {
            context.reply("用法:\n"
                    + "  //setform <槽位 0-5> <形态 0-255> (修改自己队伍)\n"
                    + "  //setform <玩家名> <槽位 0-5> <形态 0-255> (修改他人队伍)\n"
                    + "  //setform list [槽位 0-5] (查看自己队伍形态)\n"
                    + "  //setform list <玩家名> [槽位 0-5] (查看他人队伍形态)");
            return;
        }

        CharacterManager operatorManager = context.getCharacterManager();
        String operatorName = (operatorManager != null && operatorManager.getCharacterData() != null
                && operatorManager.getCharacterData().getPlayerEntity() != null)
                ? operatorManager.getCharacterData().getPlayerEntity().getEntityName()
                : "系统控制台";

        if (arguments[0].equalsIgnoreCase("list")) {
            executeList(context, arguments, operatorManager, operatorName);
            return;
        }

        if (arguments.length != 2 && arguments.length != 3) {
            context.reply("用法: " + getUsage());
            return;
        }

        String targetName = arguments.length == 3 ? arguments[0] : null;
        String slotArg = arguments.length == 3 ? arguments[1] : arguments[0];
        String formArg = arguments.length == 3 ? arguments[2] : arguments[1];

        TargetContext target = resolveTarget(context, operatorManager, targetName);
        if (target == null) {
            return;
        }

        int partyPosition;
        int formId;
        try {
            partyPosition = Integer.parseInt(slotArg);
            formId = Integer.parseInt(formArg);
        } catch (NumberFormatException exception) {
            log.warn("执行 setform 失败: 参数格式非法, operator={}, slot={}, form={}",
                    operatorName, slotArg, formArg);
            context.reply("错误: 队伍槽位和形态编号必须为有效整数。");
            return;
        }

        if (partyPosition < 0 || partyPosition >= PokemonContainerType.PARTY.getSize()) {
            context.reply("错误: 队伍槽位必须在 0 到 5 之间。");
            return;
        }
        if (formId < MIN_FORM || formId > MAX_FORM) {
            context.reply("错误: 形态编号必须在 0 到 255 之间。");
            return;
        }

        log.info("管理员 [{}] 请求修改宝可梦形态: 目标玩家=[{}], 槽位=[{}], 目标形态=[{}]",
                operatorName, target.name, partyPosition, formId);

        PokemonData targetPokemon = findTargetPokemon(
                context, target.id, partyPosition, target.manager);
        if (targetPokemon == null) {
            log.warn("执行 setform 失败: 玩家 [{}] 槽位 [{}] 没有宝可梦, operator={}",
                    target.name, partyPosition, operatorName);
            context.reply("错误: 玩家 [" + target.name + "] 的队伍槽位 " + partyPosition + " 中没有宝可梦。");
            return;
        }

        // 步骤 1: 写入数据库持久化
        if (!context.getGameServerService().updatePokemonFormType(
                target.id, targetPokemon.getPokemonId(), (short) formId)) {
            log.error("执行 setform 失败: 数据库保存失败, characterId={}, pokemonId={}, formId={}",
                    target.id, targetPokemon.getPokemonId(), formId);
            context.reply("错误: 数据库保存宝可梦形态失败。");
            return;
        }

        // 步骤 2: 更新在线内存状态
        short oldForm = targetPokemon.getFormType();
        targetPokemon.setFormType((short) formId);

        // 步骤 3: 在线客户端同步
        if (target.session != null && target.manager != null) {
            target.manager.getPartyPokemons()[partyPosition] = targetPokemon;

            target.session.send(new SendUpdatePokemonDataPacket(new UpdatePokemonData.Builder()
                    .setUpdatePokemon(targetPokemon)
                    .setIsReloadPokemonIndexId(true)
                    .build()));

            refreshParty(context, target.session, target.id);
            syncFollowerIfApplicable(context, target.manager, target.id, targetPokemon);
        }

        String formName = PokemonFormCatalog.getFormName(targetPokemon.getPokemonIndexId(), formId);
        log.info("修改宝可梦形态成功: 操作者=[{}], 目标玩家=[{}], 槽位=[{}], 宝可梦=[{}(ID:{})], 原形态=[{}], 新形态=[{}({})]",
                operatorName, target.name, partyPosition, targetPokemon.getName(),
                targetPokemon.getPokemonId(), oldForm, formId, formName);

        context.reply("成功: 已将玩家 [" + target.name + "] 槽位 " + partyPosition
                + " 的宝可梦 (" + targetPokemon.getName() + ") 形态设置为 " + formId + " (" + formName + ")。");
    }

    /**
     * 执行 list 子命令：显示队伍全部或指定槽位的宝可梦形态变化信息。
     */
    private void executeList(
            CommandContext context,
            String[] arguments,
            CharacterManager operatorManager,
            String operatorName) {
        if (arguments.length > 3) {
            context.reply("用法: //setform list [玩家名称] [队伍槽位 0-5]");
            return;
        }

        String targetName = null;
        Integer querySlot = null;

        if (arguments.length == 2) {
            Integer parsedSlot = tryParseSlot(arguments[1]);
            if (parsedSlot != null) {
                querySlot = parsedSlot;
            } else {
                targetName = arguments[1];
            }
        } else if (arguments.length == 3) {
            targetName = arguments[1];
            querySlot = tryParseSlot(arguments[2]);
            if (querySlot == null) {
                context.reply("错误: 队伍槽位必须是 0 到 5 之间的整数。");
                return;
            }
        }

        TargetContext target = resolveTarget(context, operatorManager, targetName);
        if (target == null) {
            return;
        }

        log.info("管理员 [{}] 查询宝可梦形态信息: 目标玩家=[{}], 指定槽位=[{}]",
                operatorName, target.name, querySlot == null ? "全队" : querySlot);

        if (querySlot != null) {
            showSlotFormDetail(context, target, querySlot);
        } else {
            showPartyFormSummary(context, target);
        }
    }

    /**
     * 展示队伍 6 个槽位的形态概览。
     */
    private void showPartyFormSummary(CommandContext context, TargetContext target) {
        PokemonData[] party = getPartyPokemons(context, target.id, target.manager);
        StringBuilder sb = new StringBuilder();
        sb.append("=== 玩家 [").append(target.name).append("] 队伍形态概览 ===\n");
        for (int slot = 0; slot < PokemonContainerType.PARTY.getSize(); slot++) {
            PokemonData pokemon = slot < party.length ? party[slot] : null;
            if (pokemon == null) {
                sb.append("  [槽位 ").append(slot).append("] (空)\n");
            } else {
                int speciesId = pokemon.getPokemonIndexId();
                int form = pokemon.getFormType();
                String formName = PokemonFormCatalog.getFormName(speciesId, form);
                sb.append("  [槽位 ").append(slot).append("] ")
                        .append(pokemon.getName())
                        .append(" (No.").append(speciesId).append(")")
                        .append(" - 当前形态: ").append(form).append(" [").append(formName).append("]\n");
            }
        }
        sb.append("提示: 输入 //setform list <槽位 0-5> 可查看该宝可梦支持的所有形态变化。");
        context.reply(sb.toString().trim());
    }

    /**
     * 展示指定槽位宝可梦的详细形态信息与支持的形态列表。
     */
    private void showSlotFormDetail(CommandContext context, TargetContext target, int slot) {
        PokemonData targetPokemon = findTargetPokemon(context, target.id, slot, target.manager);
        if (targetPokemon == null) {
            context.reply("玩家 [" + target.name + "] 的队伍槽位 " + slot + " 中没有宝可梦。");
            return;
        }

        int speciesId = targetPokemon.getPokemonIndexId();
        int currentForm = targetPokemon.getFormType();
        String currentFormName = PokemonFormCatalog.getFormName(speciesId, currentForm);
        Map<Integer, String> knownForms = PokemonFormCatalog.getKnownForms(speciesId);

        StringBuilder sb = new StringBuilder();
        sb.append("=== 玩家 [").append(target.name).append("] 槽位 ").append(slot).append(" 形态变化详情 ===\n");
        sb.append("宝可梦: ").append(targetPokemon.getName()).append(" (全国编号: ").append(speciesId).append(")\n");
        sb.append("当前形态: ").append(currentForm).append(" [").append(currentFormName).append("]\n");

        if (!knownForms.isEmpty()) {
            sb.append("支持的形态变化列表:\n");
            for (Map.Entry<Integer, String> entry : knownForms.entrySet()) {
                int formId = entry.getKey();
                String name = entry.getValue();
                sb.append("  [").append(formId).append("] ").append(name);
                if (formId == currentForm) {
                    sb.append(" <-- 当前");
                }
                sb.append("\n");
            }
        } else {
            sb.append("形态范围: 0 ~ 255 (可在该范围内自由切换形态)\n");
        }

        sb.append("切换形态: //setform ").append(slot).append(" <形态编号>");
        context.reply(sb.toString().trim());
    }

    /**
     * 解析目标角色上下文（自身或指定角色名）。
     */
    private TargetContext resolveTarget(
            CommandContext context,
            CharacterManager operatorManager,
            String targetName) {
        if (targetName == null) {
            if (operatorManager == null || operatorManager.getCharacterData() == null
                    || operatorManager.getCharacterData().getPlayerEntity() == null) {
                context.reply("错误: 无法获取当前操作角色上下文。");
                return null;
            }
            long cid = operatorManager.getCharacterData().getPlayerEntity().getEntityGameId();
            String name = operatorManager.getCharacterData().getPlayerEntity().getEntityName();
            return new TargetContext(name, cid, context.getSession(), operatorManager);
        }

        CharacterData character = context.getGameServerService().getCharacterByName(targetName);
        if (character == null || character.getPlayerEntity() == null) {
            context.reply("错误: 未找到指定玩家 [" + targetName + "]。");
            return null;
        }
        long cid = character.getPlayerEntity().getEntityGameId();
        Session session = GameSessionPool.getPlayerSessionInPool(cid);
        CharacterManager manager = session == null
                ? null
                : session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        return new TargetContext(character.getPlayerEntity().getEntityName(), cid, session, manager);
    }

    /**
     * 获取整队宝可梦数组（优先内存，回退数据库）。
     */
    private PokemonData[] getPartyPokemons(
            CommandContext context,
            long characterId,
            CharacterManager targetManager) {
        if (targetManager != null && targetManager.getPartyPokemons() != null) {
            return targetManager.getPartyPokemons();
        }
        PokemonData[] party = new PokemonData[PokemonContainerType.PARTY.getSize()];
        ContainerRecord partyContainer = context.getGameServerService()
                .getContainerByType(PokemonContainerType.PARTY);
        if (partyContainer != null) {
            List<PokemonData> list = context.getGameServerService()
                    .getCharacterContainerPokemons(characterId, partyContainer);
            for (PokemonData p : list) {
                if (p.getContainerPosition() >= 0 && p.getContainerPosition() < party.length) {
                    party[p.getContainerPosition()] = p;
                }
            }
        }
        return party;
    }

    private Integer tryParseSlot(String str) {
        try {
            int slot = Integer.parseInt(str);
            if (slot >= 0 && slot < PokemonContainerType.PARTY.getSize()) {
                return slot;
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private PokemonData findTargetPokemon(
            CommandContext context,
            long characterId,
            int partyPosition,
            CharacterManager targetManager) {
        if (targetManager != null && targetManager.getPartyPokemons() != null) {
            if (partyPosition < targetManager.getPartyPokemons().length) {
                PokemonData targetPokemon = targetManager.getPartyPokemons()[partyPosition];
                if (targetPokemon != null && targetPokemon.getContainerPosition() == partyPosition) {
                    return targetPokemon;
                }
            }
        }

        ContainerRecord partyContainer = context.getGameServerService()
                .getContainerByType(PokemonContainerType.PARTY);
        if (partyContainer == null) {
            return null;
        }
        return context.getGameServerService()
                .getCharacterContainerPokemons(characterId, partyContainer)
                .stream()
                .filter(pokemon -> pokemon.getContainerPosition() == partyPosition)
                .findFirst()
                .orElse(null);
    }

    private void refreshParty(CommandContext context, Session targetSession, long characterId) {
        ContainerRecord partyContainer = context.getGameServerService()
                .getContainerByType(PokemonContainerType.PARTY);
        if (partyContainer == null) {
            return;
        }
        List<PokemonData> partyPokemons = context.getGameServerService()
                .getCharacterContainerPokemons(characterId, partyContainer);
        targetSession.send(new SendPokemonContainerPacket(partyContainer, partyPokemons));
    }

    private void syncFollowerIfApplicable(
            CommandContext context,
            CharacterManager targetManager,
            long characterId,
            PokemonData targetPokemon) {
        if (targetManager.getCharacterData() == null
                || targetManager.getCharacterData().getPlayerEntity() == null) {
            return;
        }
        PlayerEntity entity = targetManager.getCharacterData().getPlayerEntity();
        if (entity.getFollowPokemonIndexId() != targetPokemon.getPokemonIndexId()) {
            return;
        }

        int rarity = targetPokemon.getFormType() & FORM_MASK;
        if (targetPokemon.getPokemonSex() == 1) {
            rarity |= FEMALE_FLAG;
        }
        if (targetPokemon.isShiny()) {
            rarity |= SHINY_FLAG;
        }
        if (targetPokemon.isAlpha()) {
            rarity |= ALPHA_FLAG;
        }
        short followerRarity = (short) rarity;

        if (context.getGameServerService().updateCharacterFollower(
                characterId, targetPokemon.getPokemonIndexId(), followerRarity)) {
            entity.setFollowPokemonRarity(followerRarity);
            SendSetFollowPokemonPacket packet = new SendSetFollowPokemonPacket(
                    characterId, targetPokemon.getPokemonIndexId(), followerRarity, false);
            PlayerVisibilityService.broadcast(targetManager, packet, true);
            log.info("已同步刷新跟随宝可梦外观与广播视野: 玩家=[{}], 宝可梦=[{}], 新外观稀有度=[{}]",
                    entity.getEntityName(), targetPokemon.getName(), followerRarity);
        } else {
            log.warn("更新跟随宝可梦外观数据库记录失败: characterId={}, pokemonIndexId={}, rarity={}",
                    characterId, targetPokemon.getPokemonIndexId(), followerRarity);
        }
    }

    private record TargetContext(String name, long id, Session session, CharacterManager manager) {
    }
}
