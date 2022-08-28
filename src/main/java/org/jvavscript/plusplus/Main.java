package org.jvavscript.plusplus;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static com.mojang.brigadier.arguments.BoolArgumentType.bool;
import static com.mojang.brigadier.arguments.BoolArgumentType.getBool;
import static com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg;
import static com.mojang.brigadier.arguments.DoubleArgumentType.getDouble;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
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
    private static final Map<Integer, Object> mapObjects = new HashMap<>();
    private static final Map<String, Map<String, Integer>> mapLabelFunction = new HashMap<>();
    public static boolean sugar = true;
    public static boolean s2d = false;
    public static String VERSION = "0.5.0";
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
        InputStream is;
        List<String> lines;
        BufferedReader br;
        if(filename.startsWith("__jar_res_")) {
            filename = filename.substring(10);
            is = Main.class.getClassLoader().getResourceAsStream(filename);
        } else {
            is = new FileInputStream(filename);
        }
        if(is == null) throw new IOException("Input stream is null.");
        lines = new ArrayList<>();
        //Construct BufferedReader from InputStreamReader
        br = new BufferedReader(new InputStreamReader(is));

        String line;
        while ((line = br.readLine()) != null) {
            line = line.replaceAll("\\*(?=[0-9a-zA-Z_.])", "__ptr_");
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
                lines.addAll(readFile("__jar_res_includes/"+line));
                continue;
            }
            lines.add(line);
        }
        br.close();
        is.close();
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
        if (cmd.startsWith("function")) return cmd.replaceAll("(?<=\\S)\\(", " (").replace(")[", ") [");
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
        cmd = cmd.replaceAll("\\*(?=[0-9a-zA-Z_.])", "__ptr_");
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
                    System.out.println("WARNING: This command is only for debugging.");
                    System.out.println("variables: " + map);
                    System.out.println("defines: " + mapDefinition);
                    System.out.println("labels: " + mapLabel);
                    System.out.println("functionLabels: " + mapLabelFunction);
                    System.out.println("objects: " + mapObjects);
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
                                    String name = getString(c, "name");
                                    String value = getString(c, "value");
                                    if (name.startsWith("__ptr_")) {
                                        name = name.substring(6);
                                        map.put(name, value.hashCode());
                                        mapObjects.put(value.hashCode(), value);
                                        return 1;
                                    }
                                    map.put(name, value);
                                    lastCommandResult = value;
                                    return 1;
                                })
                        )
                        .executes(c -> {
                            String name = getString(c, "name");
                            if (name.startsWith("__ptr_")) {
                                name = name.substring(6);
                                map.put(name, lastCommandResult.hashCode());
                                mapObjects.put(lastCommandResult.hashCode(), lastCommandResult);
                                return 1;
                            }
                            map.put(name, lastCommandResult);
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
        dispatcher.register(
            literal("construct")
                .then(
                    argument("className", string())
                        .then(
                            argument("args", string())
                                .executes(c -> {
                                    String className = getString(c, "className");
                                    Class<?> clazz;
                                    try {
                                        clazz = Class.forName(className);
                                    } catch (ClassNotFoundException e) {
                                        e.printStackTrace();
                                        return 0;
                                    }

                                    String args = getString(c, "args");
                                    String[] argsArray = "".equals(args) ? new String[0] : args.split(",");
                                    Class<?>[] argsType = new Class[argsArray.length];
                                    Object[] argsValue = new Object[argsArray.length];
                                    try {
                                        for (int i = 0; i < argsArray.length; i++) {
                                            String[] argsEach = argsArray[i].split(" ");
                                            argsType[i] = getClazz(argsEach[0]);
                                            argsValue[i] = getValue(argsEach[1], argsEach[0]);
                                        }
                                    } catch (ClassNotFoundException | NullPointerException |
                                             ClassCastException | NumberFormatException e) {
                                        e.printStackTrace();
                                        return 0;
                                    }

                                    try {
                                        lastCommandResult = clazz.getConstructor(argsType).newInstance(argsValue);
                                    } catch (NoSuchMethodException | InstantiationException |
                                             IllegalAccessException | InvocationTargetException e) {
                                        e.printStackTrace();
                                        return 0;
                                    }
                                    return 1;
                                })
                        )
                )
        );
        dispatcher.register(
            literal("invoke")
                .then(
                    argument("className", string())
                        .then(
                            argument("*object", integer())
                                .then(
                                    argument("methodName", string())
                                        .then(
                                            argument("args", string())
                                                .executes(c -> {
                                                    String className = getString(c, "className");
                                                    Class<?> clazz;
                                                    try {
                                                        clazz = Class.forName(className);
                                                    } catch (ClassNotFoundException e) {
                                                        e.printStackTrace();
                                                        return 0;
                                                    }

                                                    int hash = getInteger(c, "*object");
                                                    Object object = hash == 0 ? null : mapObjects.get(hash);

                                                    String args = getString(c, "args");
                                                    String[] argsArray = "".equals(args) ? new String[0] : args.split(",");
                                                    Class<?>[] argsType = new Class[argsArray.length];
                                                    Object[] argsValue = new Object[argsArray.length];
                                                    try {
                                                        for (int i = 0; i < argsArray.length; i++) {
                                                            String[] argsEach = argsArray[i].split(" ");
                                                            argsType[i] = getClazz(argsEach[0]);
                                                            argsValue[i] = getValue(argsEach[1], argsEach[0]);
                                                        }
                                                    } catch (ClassNotFoundException | NullPointerException |
                                                             ClassCastException | NumberFormatException e) {
                                                        e.printStackTrace();
                                                        return 0;
                                                    }

                                                    Method method;
                                                    Object result;
                                                    try {
                                                        method = clazz.getMethod(getString(c, "methodName"), argsType);
                                                        result = method.invoke(object,argsValue);
                                                    } catch (NoSuchMethodException | IllegalAccessException |
                                                             InvocationTargetException e) {
                                                        e.printStackTrace();
                                                        return 0;
                                                    }
                                                    lastCommandResult = result;
                                                    return 1;
                                                })
                                        )
                                )
                        )
                )
        );
        dispatcher.register(
            literal("getfield")
                .then(
                    argument("className", string())
                        .then(
                            argument("*object", integer())
                                .then(
                                    argument("fieldName", string())
                                        .executes( c -> {
                                            String className = getString(c, "className");
                                            Class<?> clazz;
                                            try {
                                                clazz = Class.forName(className);
                                            } catch (ClassNotFoundException e) {
                                                e.printStackTrace();
                                                return 0;
                                            }

                                            int hash = getInteger(c, "*object");
                                            Object object = hash == 0 ? null : mapObjects.get(hash);

                                            String fieldName = getString(c, "fieldName");
                                            Field field;
                                            Object result;
                                            try {
                                                field = clazz.getField(fieldName);
                                                result = field.get(object);
                                            } catch (NoSuchFieldException | IllegalAccessException e) {
                                                e.printStackTrace();
                                                return 0;
                                            }
                                            lastCommandResult = result;
                                            return 1;
                                        })
                                )
                        )
                )
        );
    }

    private static Object getValue(String value, String className) throws ClassNotFoundException, NullPointerException, ClassCastException, NumberFormatException {
        return switch (className) {
            case "boolean" -> Boolean.parseBoolean(value);
            case "byte" -> Byte.parseByte(value);
            case "short" -> Short.parseShort(value);
            case "char" -> value.charAt(0);
            case "int" -> Integer.parseInt(value);
            case "long" -> Long.parseLong(value);
            case "float" -> Float.parseFloat(value);
            case "double" -> Double.parseDouble(value);
            case "java.lang.String" -> value;
            default -> {
                Object obj = mapObjects.get(Integer.valueOf(value));
                if (obj == null) throw new NullPointerException();
                yield Class.forName(className).cast(obj);
            }
        };
    }

    private static Class<?> getClazz(String name) throws ClassNotFoundException {
        return switch (name) {
            case "boolean" -> boolean.class;
            case "byte" -> byte.class;
            case "short" -> short.class;
            case "char" -> char.class;
            case "int" -> int.class;
            case "long" -> long.class;
            case "float" -> float.class;
            case "double" -> double.class;
            case "void" -> void.class;
            default -> Class.forName(name);
        };
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
        if (parameters.isEmpty()) {
            dispatcher.register(literal(name)
                .executes(getObjectCommand(name, statements, copy))
            );
            return;
        }
        //noinspection unchecked
        dispatcher.register((LiteralArgumentBuilder<Object>) forLiteral(
            argument(lastParam, string())
                .executes(getObjectCommand(name, statements, copy)), parameters, name));
    }

    private static Command<Object> getObjectCommand(String name, List<String> statements, List<String> copy) {
        return c -> {
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
        };
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