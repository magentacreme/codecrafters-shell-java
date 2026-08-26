import java.util.ArrayList;

public final class CommandParser {
    private CommandParser() {
    }

    public static String[] parse(String input) {
        ArrayList<String> arguments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean argumentStarted = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (inSingleQuote) {
                argumentStarted = true;
                if (c == '\'') {
                    inSingleQuote = false;
                } else {
                    current.append(c);
                }
            } else if (inDoubleQuote) {
                argumentStarted = true;
                switch (c) {
                    case '\\' -> {
                        if (i + 1 < input.length()) {
                            char next = input.charAt(i + 1);
                            if (next == '"' || next == '\\') {
                                current.append(next);
                                i++;
                            } else {
                                current.append('\\');
                            }
                        } else {
                            current.append('\\');
                        }
                    }
                    case '"' -> inDoubleQuote = false;
                    default -> current.append(c);
                }
            } else if (c == '\\') {
                argumentStarted = true;
                if (i + 1 < input.length()) {
                    i++;
                    current.append(input.charAt(i));
                }
            } else if (c == '\'') {
                argumentStarted = true;
                inSingleQuote = true;
            } else if (c == '"') {
                argumentStarted = true;
                inDoubleQuote = true;
            } else if (Character.isWhitespace(c)) {
                if (argumentStarted) {
                    arguments.add(current.toString());
                    current.setLength(0);
                    argumentStarted = false;
                }
            } else {
                argumentStarted = true;
                current.append(c);
            }
        }

        if (argumentStarted) {
            arguments.add(current.toString());
        }

        return arguments.toArray(String[]::new);
    }
}
