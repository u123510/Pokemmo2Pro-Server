package org.pokemmo.gameserver.command.commands;

import com.google.inject.Inject;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.character.CharacterData;
import org.pokemmo.gameserver.game.item.ItemManager;
import org.pokemmo.gameserver.game.pool.GameSessionPool;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendInventoryPacket;
import org.server.Session;

public class CreateItemCommand implements Command {
    @Inject
    public CreateItemCommand() {
    }

    @Override
    public String getName() {
        return "createitem";
    }

    @Override
    public String getUsage() {
        return "//createitem <character name> <item id> <amount>";
    }

    @Override
    public void execute(CommandContext context, String[] arguments) {
        if (arguments.length != 3) {
            context.reply("Usage: " + getUsage());
            return;
        }

        CharacterData targetCharacter = context.getGameServerService().getCharacterByName(arguments[0]);
        if (targetCharacter == null) {
            context.reply("Character not found: " + arguments[0]);
            return;
        }

        int itemIndexId;
        try {
            itemIndexId = Integer.parseInt(arguments[1]);
        } catch (NumberFormatException exception) {
            context.reply("Item id must be an integer.");
            return;
        }
        if (itemIndexId <= 0 || itemIndexId > Short.MAX_VALUE
                || ItemManager.getItemData((short) itemIndexId) == null) {
            context.reply("Unknown item id: " + arguments[1]);
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(arguments[2]);
        } catch (NumberFormatException exception) {
            context.reply("Item amount must be an integer from 1 to " + Short.MAX_VALUE + ".");
            return;
        }
        if (amount < 1 || amount > Short.MAX_VALUE) {
            context.reply("Item amount must be an integer from 1 to " + Short.MAX_VALUE + ".");
            return;
        }

        long targetCharacterId = targetCharacter.getPlayerEntity().getEntityGameId();
        long itemId = context.getCharacterManager().getSnowflakeIdGenerator().nextId();
        if (context.getGameServerService().addInventoryItem(
                targetCharacterId,
                (short) itemIndexId,
                (short) amount,
                itemId
        ) == null) {
            context.reply("The item amount could not be saved. The stack may exceed the database limit.");
            return;
        }

        Session targetSession = GameSessionPool.getPlayerSessionInPool(targetCharacterId);
        if (targetSession != null) {
            var targetManager = targetSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
            var inventory = context.getGameServerService().getInventory();
            if (targetManager != null && inventory != null) {
                targetSession.send(new SendInventoryPacket(
                        inventory,
                        context.getGameServerService().getItemsByContainerAndCharacter(targetCharacterId, inventory)
                ));
            }
        }

        context.reply("Added item " + itemIndexId + " x" + amount + " to "
                + targetCharacter.getPlayerEntity().getEntityName() + ".");
    }
}
