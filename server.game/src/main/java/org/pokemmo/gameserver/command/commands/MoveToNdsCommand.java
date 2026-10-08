package org.pokemmo.gameserver.command.commands;

import com.google.inject.Inject;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.region.RegionType;

/**
 * Client-compatible NDS teleport command.
 *
 * The client writes bank/map as one unsigned 16-bit value:
 * (bank << 8) | mapId. The final flag is accepted for compatibility.
 */
public final class MoveToNdsCommand implements Command {
    @Inject
    public MoveToNdsCommand() {
    }

    @Override
    public String getName() {
        return "moveto2";
    }

    @Override
    public String getUsage() {
        return "//moveto2 <region> <bank/map> <x> <y> <z> <ng|true|false>";
    }

    @Override
    public void execute(CommandContext context, String[] arguments) {
        if (arguments.length != 6) {
            context.reply("Usage: " + getUsage());
            return;
        }
        try {
            int region = Integer.parseInt(arguments[0]);
            int packedMap = Integer.parseInt(arguments[1]);
            int x = Integer.parseInt(arguments[2]);
            int y = Integer.parseInt(arguments[3]);
            Integer.parseInt(arguments[4]);
            parseFlag(arguments[5]);
            if (region != RegionType.SINNOH.getType()) {
                context.reply("moveto2 only supports Sinnoh region 3.");
                return;
            }
            if (packedMap < 0 || packedMap > 0xFFFF) {
                context.reply("moveto2 bank/map must be an unsigned 16-bit value.");
                return;
            }
            int bank = (packedMap >>> 8) & 0xFF;
            int mapId = packedMap & 0xFF;
            String failure = context.getCharacterManager().moveTo(region, bank, mapId, x, y);
            context.reply(failure == null
                    ? "Moved to the requested NDS map position."
                    : failure);
        } catch (NumberFormatException exception) {
            context.reply("moveto2 arguments must be integers except the final flag.");
        } catch (IllegalArgumentException exception) {
            context.reply(exception.getMessage());
        }
    }

    private boolean parseFlag(String value) {
        if ("NG".equalsIgnoreCase(value)
                || "true".equalsIgnoreCase(value)
                || "1".equals(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value) || "0".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException("moveto2 final flag must be NG, true, or false.");
    }
}
