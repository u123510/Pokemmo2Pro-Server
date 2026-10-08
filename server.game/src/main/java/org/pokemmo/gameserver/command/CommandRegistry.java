package org.pokemmo.gameserver.command;

import jakarta.inject.Inject;
import com.google.inject.Singleton;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Singleton
public class CommandRegistry {
    private final Map<String, Command> commands = new HashMap<>();

    @Inject
    public CommandRegistry(Set<Command> registeredCommands) {
        for (Command command : registeredCommands) {
            register(command.getName(), command);
            for (String alias : command.getAliases()) {
                register(alias, command);
            }
        }
    }

    private void register(String name, Command command) {
        String normalizedName = name.trim().toLowerCase();
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("Command name cannot be empty");
        }
        Command previous = commands.putIfAbsent(normalizedName, command);
        if (previous != null && previous != command) {
            throw new IllegalStateException("Duplicate command name: " + normalizedName);
        }
    }

    public Optional<Command> find(String name) {
        return Optional.ofNullable(commands.get(name.toLowerCase()));
    }
}
