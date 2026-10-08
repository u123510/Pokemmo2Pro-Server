package org.pokemmo.gameserver.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CommandParser {
    private CommandParser() {
    }

    public static Optional<ParsedCommand> parse(String message) {
        if (message == null) {
            return Optional.empty();
        }
        String commandText = message.trim();
        if (!commandText.startsWith("//")) {
            return Optional.empty();
        }

        commandText = commandText.substring(2).trim();
        if (commandText.isEmpty()) {
            return Optional.of(new ParsedCommand("", new String[0]));
        }

        List<String> tokens = tokenize(commandText);
        return Optional.of(new ParsedCommand(
                tokens.get(0).toLowerCase(),
                tokens.subList(1, tokens.size()).toArray(String[]::new)
        ));
    }

    private static List<String> tokenize(String commandText) {
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        char quote = 0;
        boolean escaping = false;
        for (char current : commandText.toCharArray()) {
            if (escaping) {
                token.append(current);
                escaping = false;
                continue;
            }
            if (current == '\\') {
                escaping = true;
                continue;
            }
            if (quote != 0) {
                if (current == quote) {
                    quote = 0;
                } else {
                    token.append(current);
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
            } else if (Character.isWhitespace(current)) {
                if (!token.isEmpty()) {
                    tokens.add(token.toString());
                    token.setLength(0);
                }
            } else {
                token.append(current);
            }
        }
        if (escaping) {
            token.append('\\');
        }
        if (!token.isEmpty()) {
            tokens.add(token.toString());
        }
        return tokens;
    }

    public record ParsedCommand(String name, String[] arguments) {
    }
}
