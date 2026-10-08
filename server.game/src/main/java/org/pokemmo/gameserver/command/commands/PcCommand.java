package org.pokemmo.gameserver.command.commands;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.protocol.packets.s2c.SendBoxInfoPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendPcStatePacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendPokemonContainerPacket;
import org.pokemmo.gameserver.services.GameServerService;

@Slf4j
public class PcCommand implements Command {
    @Inject
    public PcCommand() {
    }

    @Override
    public String getName() {
        return "pc";
    }

    @Override
    public String getUsage() {
        return "//pc";
    }

    @Override
    public void execute(CommandContext context, String[] arguments) {
        if (arguments.length != 0) {
            context.reply("Usage: " + getUsage());
            return;
        }

        if (context.getCharacterManager().getCharacterData() == null
                || context.getCharacterManager().getCharacterData().getPlayerEntity() == null) {
            context.reply("The character is not ready for the PC.");
            return;
        }

        if (!openPc(context)) {
            context.reply("Unable to open the PC.");
            return;
        }
        context.reply("PC opened.");
    }

    private boolean openPc(CommandContext context) {
        long characterId = context.getCharacterManager().getCharacterData()
                .getPlayerEntity().getEntityGameId();
        try {
            context.getCharacterManager().getInteractManager().setMailWidgetOpen(false);
            GameServerService.PcData pcData = context.getGameServerService().getPcData(characterId);
            if (pcData == null) {
                log.warn("Unable to open the PC because the PC container is unavailable");
                return false;
            }

            context.getSession().send(
                    new SendPcStatePacket(false),
                    new SendPokemonContainerPacket(pcData.container(), pcData.pokemons()),
                    new SendBoxInfoPacket(context.getCharacterManager().getCharacterData()
                            .getPcBoxExpansionNumber()),
                    new SendPcStatePacket(true)
            );
            return true;
        } catch (RuntimeException exception) {
            log.error("Unable to load the PC container for character {}", characterId, exception);
            return false;
        }
    }
}
