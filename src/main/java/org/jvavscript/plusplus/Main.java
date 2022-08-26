package org.jvavscript.plusplus;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

import static com.mojang.brigadier.arguments.BoolArgumentType.bool;
import static com.mojang.brigadier.arguments.BoolArgumentType.getBool;
import static com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg;
import static com.mojang.brigadier.arguments.DoubleArgumentType.getDouble;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;
import static org.jvavscript.plusplus.FunctionParametersArgumentType.functionParameters;
import static org.jvavscript.plusplus.FunctionParametersArgumentType.getFunctionParameters;
import static org.jvavscript.plusplus.FunctionStatementArgumentType.functionStatement;
import static org.jvavscript.plusplus.FunctionStatementArgumentType.getFunctionStatement;

public class Main {

    private static final Object obj = new Object();
    private static final CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
    private static final String VOID = "";
    private static final Map<String, Object> map = new HashMap<>();
    private static final Map<String, String> mapDefinition = new HashMap<>();
    private static final Map<String, Integer> mapLabel = new HashMap<>();
    private static final Map<String, Map<String, Integer>> mapLabelFunction = new HashMap<>();
    public static boolean sugar = true;
    public static boolean s2d = false;
    public static String VERSION = "0.4.0";
    private static Object lastCommandResult = VOID;
    private static String labelName = "";
    private static boolean shouldGoTo = false;

    private static String readUntilCharAndReplaceLn(BufferedReader br, char c) throws IOException {
        StringBuilder sb = new StringBuilder();
        char read;
        while ((read = (char) br.read()) != c) {
            if (read == '\n') read = ',';
            sb.append(read);
        }
        return sb.toString();
    }

    private static List<String> readFile(String filename) throws IOException {
        FileInputStream fis = new FileInputStream(filename);
        List<String> lines = new ArrayList<>();
        //Construct BufferedReader from InputStreamReader
        BufferedReader br = new BufferedReader(new InputStreamReader(fis));

        String line;
        while ((line = br.readLine()) != null) {
            if (line.startsWith("function")) {
                if (!line.contains("]")) {
                    if (line.endsWith("[")) {
                        line = String.format("%s%s]", line, readUntilCharAndReplaceLn(br, ']'));
                    }
                    if (!line.contains("[")) {
                        String newline = br.readLine();
                        line = String.format("%s %s,%s]", line, newline, readUntilCharAndReplaceLn(br, ']'));
                    }
                }
                line = line.replaceAll(",+", ",");
            }
            if (line.startsWith("#include ")) {
                line = line.substring(9);
                if (line.charAt(0) == '"') line = line.substring(1, line.length() - 1);
                lines.addAll(readFile(line));
                continue;
            }
            lines.add(line);
        }
        br.close();
        fis.close();
        return lines;
    }

    private static String replaceVar(String cmd) {
        if (cmd.startsWith("function")) return cmd;
        int vsIndex, veIndex;
        vsIndex = cmd.indexOf("${");
        veIndex = cmd.indexOf("}");
        String varName = "";
        try {
            varName = cmd.substring(vsIndex + 2, veIndex);
        } catch (StringIndexOutOfBoundsException ignored) {}
        if (vsIndex + veIndex != -2 && map.containsKey(varName)) {
            cmd = cmd.replaceFirst("\\$\\{.+?}", map.get(varName).toString());
            cmd = replaceVar(cmd);
        }
        return cmd;
    }

    private static String replaceDef(String cmd) {
        if (cmd.startsWith("function")) return cmd;
        for (Map.Entry<String, String> e : mapDefinition.entrySet())
            cmd = cmd.replace(e.getKey(), e.getValue());
        return cmd;
    }

    private static String desugar(String cmd) {
        if (cmd.startsWith("function")) return cmd.replaceAll("(?<=\\S)\\(", " (").replace(")[", ") [");;
        if ((cmd.contains(" + ")
            || cmd.contains(" * ")
            || cmd.contains(" / ")
            || cmd.contains(" - ")
        ) && !cmd.contains("let "))
            cmd = "let " + cmd;
        if (cmd.contains(" =") && !cmd.contains("==") && !cmd.contains("set "))
            cmd = "set " + cmd.replace(" =", "");
        if ((cmd.contains(" < ") || cmd.contains(" > ") || cmd.contains(" == ")) && !cmd.contains("cmp "))
            cmd = "cmp " + cmd;
        return cmd;
    }

    public synchronized static void main(String[] args) {
        registerCommands();
        if (args.length != 0) {
            executeScript(args[0]);
            return;
        }

        System.out.println("JvavScript++ v" + VERSION);
        System.out.print(">>> ");
        //noinspection InfiniteLoopStatement
        while (true) {
            interpret();
        }
    }

    private static void interpret() {
        Scanner scanner = new Scanner(System.in);
        String cmd;
        cmd = scanner.nextLine();
        cmd = replaceVar(cmd);
        cmd = replaceDef(cmd);
        if (sugar)
            cmd = desugar(cmd);
        if (s2d)
            cmd = cmd.replaceFirst("set", "define");
        try {
            if (cmd.isEmpty()) {
                System.out.print(">>> ");
                return;
            }
            dispatcher.execute(cmd.replace("\\n", "\n"), obj);
            if (shouldGoTo) {
                System.out.println("W: goto is not allowed to use in interactive mode, do nothing.");
                shouldGoTo = false;
            }
            System.out.println(" <  " + (lastCommandResult == VOID ? "(void)" : lastCommandResult));
            System.out.print(">>> ");
        } catch (CommandSyntaxException e) {
            System.out.println(e.getMessage());
            System.out.print(">>> ");
        }
    }

    private static void executeScript(String arg) {
        try {
            List<String> list = readFile(arg);
            addLabelsToMap(list, mapLabel);
            executeCommandList(list, mapLabel);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void executeCommandList(List<String> list, Map<String, Integer> mapLabel) {
        for (int i = 0; i < list.size(); i++) {
            String c = list.get(i);
            c = replaceVar(c);
            c = replaceDef(c);
            if (sugar)
                c = desugar(c);
            if (s2d)
                c = c.replaceFirst("set", "define");
            try {
                if (c.isEmpty() || c.startsWith(";"))
                    continue;
                dispatcher.execute(c, obj);
            } catch (CommandSyntaxException e) {
                System.out.println("Error:" + e.getMessage());
            }
            if (shouldGoTo) {
                if (mapLabel.containsKey(labelName))
                    i = mapLabel.get(labelName);
                else
                    System.out.println("E:label \"" + labelName + "\" not found.");
                shouldGoTo = false;
            }
        }
    }

    private static void addLabelsToMap(List<String> list, Map<String, Integer> mapLabel) {
        for (int i = 0; i < list.size(); i++) {
            String s = list.get(i);
            if (s.startsWith(";"))
                mapLabel.put(s.substring(1), i);
        }
    }

    private static void registerCommands() {
        dispatcher.register(
            literal("print")
                .then(
                    argument("String", string())
                        .executes(c -> {
                            System.out.print(getString(c, "String"));
                            lastCommandResult = VOID;
                            return 1;
                        })
                )
                .executes(c -> {
                    System.out.print(lastCommandResult);
                    lastCommandResult = VOID;
                    return 1;
                })
        );
        dispatcher.register(
            literal("println")
                .then(
                    argument("String", string())
                        .executes(c -> {
                            System.out.println(getString(c, "String"));
                            lastCommandResult = VOID;
                            return 1;
                        })
                )
                .executes(c -> {
                    System.out.println(lastCommandResult);
                    lastCommandResult = VOID;
                    return 1;
                })
        );
        dispatcher.register(
            literal("charat")
                .then(
                    argument("String", string())
                        .then(
                            argument("index", integer())
                                .executes(c -> {
                                    try {
                                        lastCommandResult = getString(c, "String").charAt(getInteger(c, "index"));
                                        return 1;
                                    } catch (StringIndexOutOfBoundsException e) {
                                        e.printStackTrace();
                                        lastCommandResult = VOID;
                                        return 0;
                                    }
                                })
                        )
                )
        );
        dispatcher.register(
            literal("indexof")
                .then(
                    argument("String", string())
                        .then(
                            argument("anotherString", string())
                                .executes(c -> {
                                    lastCommandResult = getString(c, "String").indexOf(getString(c, "anotherString"));
                                    return 1;
                                })
                        )
                )
        );
        dispatcher.register(
            literal("exit")
                .executes(c -> {
                    System.exit(0);
                    lastCommandResult = "E: Could not exit program.";
                    return 0;
                })
        );
        dispatcher.register(
            literal("version")
                .executes(c -> {
                    dispatcher.execute("print " + VERSION, obj);
                    lastCommandResult = VOID;
                    return 1;
                })
        );
        dispatcher.register(
            literal("help")
                .then(
                    argument("Command", string())
                        .executes(c -> {
                            String command = getString(c, "Command");
                            for (String x : dispatcher.getAllUsage(dispatcher.getRoot().getChild(command), obj, true))
                                System.out.printf("%s %s%n", command, x);
                            lastCommandResult = VOID;
                            return 1;
                        })
                )
                .executes(c -> {
                    for (String x : dispatcher.getSmartUsage(dispatcher.getRoot(), obj).values())
                        System.out.println(x);
                    lastCommandResult = VOID;
                    return 1;
                })
        );
        dispatcher.register(
            literal("let")
                .then(
                    argument("num1", doubleArg())
                        .then(
                            literal("+")
                                .then(
                                    argument("num2", doubleArg())
                                        .executes(c -> {
                                            lastCommandResult = getDouble(c, "num1") + getDouble(c, "num2");
                                            return 1;
                                        })
                                )
                        )
                        .then(
                            literal("-")
                                .then(
                                    argument("num2", doubleArg())
                                        .executes(c -> {
                                            lastCommandResult = getDouble(c, "num1") - getDouble(c, "num2");
                                            return 1;
                                        })
                                )
                        )
                        .then(
                            literal("*")
                                .then(
                                    argument("num2", doubleArg())
                                        .executes(c -> {
                                            lastCommandResult = getDouble(c, "num1") * getDouble(c, "num2");
                                            return 1;
                                        })
                                )
                        )
                        .then(
                            literal("/")
                                .then(
                                    argument("num2", doubleArg())
                                        .executes(c -> {
                                            lastCommandResult = getDouble(c, "num1") / getDouble(c, "num2");
                                            return 1;
                                        })
                                )
                        )
                )
        );
        dispatcher.register(
            literal("list")
                .executes(c -> {
                    System.out.println("variables: " + map);
                    System.out.println("defines:   " + mapDefinition);
                    lastCommandResult = VOID;
                    return 1;
                })
        );
        dispatcher.register(
            literal("set")
                .then(
                    argument("name", string())
                        .then(
                            argument("value", string())
                                .executes(c -> {
                                    map.put(getString(c, "name"), getString(c, "value"));
                                    lastCommandResult = getString(c, "value");
                                    return 1;
                                })
                        )
                        .executes(c -> {
                            map.put(getString(c, "name"), lastCommandResult);
                            return 1;
                        })
                )
        );
        dispatcher.register(
            literal("define")
                .then(
                    argument("name", string())
                        .then(
                            argument("value", string())
                                .executes(c -> {
                                    mapDefinition.put(getString(c, "name"), getString(c, "value"));
                                    lastCommandResult = getString(c, "value");
                                    return 1;
                                })
                        )
                        .executes(c -> {
                            mapDefinition.put(getString(c, "name"), lastCommandResult.toString());
                            return 1;
                        })
                )
        );
        dispatcher.register(
            literal("enable")
                .then(
                    literal("sugar")
                        .executes(c -> {
                            sugar = true;
                            lastCommandResult = VOID;
                            return 1;
                        })
                )
                .then(
                    literal("s2d")
                        .executes(c -> {
                            s2d = true;
                            lastCommandResult = VOID;
                            return 1;
                        })
                )
        );
        dispatcher.register(
            literal("disable")
                .then(
                    literal("sugar")
                        .executes(c -> {
                            sugar = false;
                            lastCommandResult = VOID;
                            return 1;
                        })
                )
                .then(
                    literal("s2d")
                        .executes(c -> {
                            s2d = false;
                            lastCommandResult = VOID;
                            return 1;
                        })
                )
        );
        dispatcher.register(
            literal("input")
                .then(
                    argument("String", string())
                        .executes(c -> {
                            System.out.print(getString(c, "String"));
                            lastCommandResult = new Scanner(System.in).nextLine();
                            return 1;
                        })
                )
                .executes(c -> {
                    lastCommandResult = new Scanner(System.in).nextLine();
                    return 1;
                })
        );
        dispatcher.register(
            literal("cmp")
                .then(
                    argument("String1", string())
                        .then(
                            literal("==")
                                .then(
                                    argument("String2", string())
                                        .executes(c -> {
                                            lastCommandResult = getString(c, "String1").equals(getString(c, "String2"));
                                            return 1;
                                        })
                                )
                        )
                )
                .then(
                    argument("Number1", doubleArg())
                        .then(
                            literal(">")
                                .then(
                                    argument("Number2", doubleArg())
                                        .executes(c -> {
                                            lastCommandResult = getDouble(c, "Number1") > getDouble(c, "Number2");
                                            return 1;
                                        })
                                )
                        )
                        .then(
                            literal("==")
                                .then(
                                    argument("Number2", doubleArg())
                                        .executes(c -> {
                                            lastCommandResult = getDouble(c, "Number1") == getDouble(c, "Number2");
                                            return 1;
                                        })
                                )
                        )
                        .then(
                            literal("<")
                                .then(
                                    argument("Number2", doubleArg())
                                        .executes(c -> {
                                            lastCommandResult = getDouble(c, "Number1") < getDouble(c, "Number2");
                                            return 1;
                                        })
                                )
                        )
                )
        );
        dispatcher.register(
            literal("not")
                .then(
                    argument("bool", bool())
                        .executes(c -> {
                            lastCommandResult = !getBool(c, "bool");
                            return 1;
                        })
                )
        );
        dispatcher.register(
            literal("random")
                .executes(c -> {
                    lastCommandResult = Math.random();
                    return 1;
                })
        );
        dispatcher.register(
            literal("floor")
                .then(
                    argument("Number", doubleArg())
                        .executes(c -> {
                            lastCommandResult = (int) getDouble(c, "Number");
                            return 1;
                        })
                )
                .executes(c -> {
                    lastCommandResult = ((Double) lastCommandResult).intValue();
                    return 1;
                })
        );
        dispatcher.register(
            literal("if")
                .then(
                    argument("bool", bool())
                        .then(
                            argument("label", string())
                                .executes(c -> {
                                    if (getBool(c, "bool"))
                                        dispatcher.execute("goto " + getString(c, "label"), obj);
                                    return 1;
                                })
                        )
                )
        );
        dispatcher.register(
            literal("goto")
                .then(
                    argument("label", string())
                        .executes(c -> {
                            labelName = getString(c, "label");
                            shouldGoTo = true;
                            return 1;
                        })
                )
        );
        dispatcher.register(
            literal("length")
                .then(
                    argument("String", string())
                        .executes(c -> {
                            String str = getString(c, "String");
                            lastCommandResult = str.length();
                            return 1;
                        })
                )
        );
        dispatcher.register(
            literal("return")
                .then(
                    argument("value", string())
                        .executes(c -> {
                            lastCommandResult = getString(c, "value");
                            return 1;
                        })
                )
        );
        dispatcher.register(
            literal("function")
                .then(
                    argument("name", string())
                        .then(
                            argument("parameters", functionParameters())
                                .then(
                                    argument("statements", functionStatement())
                                        .executes(c -> {
                                            String functionName = getString(c, "name");
                                            List<String> parameters = getFunctionParameters(c, "parameters");
                                            List<String> statements = getFunctionStatement(c, "statements");
                                            registerFunction(functionName, parameters, statements);
                                            return 1;
                                        })
                                )
                        )
                )
        );
    }

    private static String replaceParam(CommandContext<Object> c, String cmd, List<String> params) {
        int vsIndex, veIndex;
        vsIndex = cmd.indexOf("%");
        veIndex = cmd.indexOf("%", vsIndex + 1);
        String varName = "";
        try {
            varName = cmd.substring(vsIndex + 1, veIndex);
        } catch (StringIndexOutOfBoundsException ignored) {}
        if (vsIndex + veIndex != -2 && params.contains(varName)) {
            cmd = cmd.replaceFirst("%.+?%", getString(c, varName));
            cmd = replaceParam(c, cmd, params);
        }
        return cmd;
    }

    private static Map<String, Integer> getFunctionLabelMap(String name) {
        mapLabelFunction.putIfAbsent(name, new HashMap<>());
        return mapLabelFunction.get(name);
    }
    private static void registerFunction(String name, List<String> parameters, List<String> statements) {
        List<String> copy = new ArrayList<>(parameters);
        String lastParam = parameters.get(parameters.size() - 1);
        parameters.remove(parameters.size() - 1);
        //noinspection unchecked
        dispatcher.register((LiteralArgumentBuilder<Object>) forLiteral(
            argument(lastParam, string())
                .executes(c -> {
                    List<String> copiedStatements = new ArrayList<>(statements);
                    for (int i = 0; i < copiedStatements.size(); i++) {
                        var each = copiedStatements.get(i);
                        if (each.contains("%")) {
                            copiedStatements.set(i, replaceParam(c, each, copy));
                        }
                    }
                    addLabelsToMap(copiedStatements, getFunctionLabelMap(name));
                    executeCommandList(copiedStatements, getFunctionLabelMap(name));
                    return 1;
                }), parameters, name));
    }

    private static ArgumentBuilder<Object, ?> forLiteral
        (ArgumentBuilder<Object, ?> builder, List<String> parameters, String literalName) {
        if (parameters.size() == 0) return literal(literalName).then(builder);
        ArgumentBuilder<Object, RequiredArgumentBuilder<Object, String>> arg = argument(parameters.get(parameters.size() - 1), string());
        parameters.remove(parameters.size() - 1);
        arg.then(builder);
        return forLiteral(arg, parameters, literalName);
    }
}