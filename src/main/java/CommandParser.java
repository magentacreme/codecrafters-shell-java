import java.io.File;
import java.util.ArrayList;
import java.util.Set;


public final class CommandParser {
    private CommandParser() {}

    public static String longestCommonPrefix(ArrayList<String> strings) {

    if (strings.isEmpty()) {
        return "";
    }

    String prefix = strings.get(0);

    for (int i = 1; i < strings.size(); i++) {

        String current = strings.get(i);

        int j = 0;

        while (j < prefix.length()
                && j < current.length()
                && prefix.charAt(j) == current.charAt(j)) {

            j++;
        }

        prefix = prefix.substring(0, j);

        if (prefix.isEmpty()) {
            break;
        }
    }

    return prefix;
}

    public static String readCommand(Set<String> builtins) throws Exception {

    StringBuilder input = new StringBuilder();

    // boolean multipleMatches = false;

    while (true) {
        int c = System.in.read();
        //boolean found = false;
        // Enter
        if (c == '\n') {
            System.out.println();
            return input.toString();
        }

        if (c == '\r') {
            continue;
        }

if (c == '\t') {
    String current = input.toString();
    ArrayList<String> matches = new ArrayList<>();

    for (String builtin : builtins) {

        if (builtin.startsWith(current)) {
            matches.add(builtin);
        }
    }

    String path = System.getenv("PATH");

    if (path != null) {

        String[] directories = path.split(":");

        for (String directory : directories) {

            File dir = new File(directory);
            File[] files = dir.listFiles();

            if (files == null) {
                continue;
            }

            for (File file : files) {

                if (file.isFile()
                        && file.canExecute()
                        && file.getName().startsWith(current)
                        && !matches.contains(file.getName())) {

                    matches.add(file.getName());
                }
            }
        }
    }

    // Alphabetical order
    matches.sort(String::compareTo);

    if (matches.isEmpty()) {

        System.out.print("\007");

    }

    else if (matches.size() == 1) {

        String completion = matches.get(0);

        String remaining =
                completion.substring(current.length());

        System.out.print(remaining);
        System.out.print(" ");

        input.append(remaining);
        input.append(" ");

    }

    else {

        String commonPrefix =
                longestCommonPrefix(matches);

        // There is more text that can be completed
        if (commonPrefix.length() > current.length()) {

            String remaining =
                    commonPrefix.substring(current.length());

            System.out.print(remaining);

            input.append(remaining);

        }

        // Nothing more can be completed
        else {

            System.out.print("\007");
        }
    }

    continue;
}

// Backspace
        if (c == 127 || c == 8) {

            if (input.length() > 0) {

                input.deleteCharAt(input.length() - 1);

                System.out.print("\b \b");
            }

            continue;
        }

        // Normal character
        input.append((char) c);
        System.out.print((char) c);
    }
}
    public static String[] parse(String input) {

        ArrayList<String> arguments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean argumentStarted = false;
        //cd java/src/main/java
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
