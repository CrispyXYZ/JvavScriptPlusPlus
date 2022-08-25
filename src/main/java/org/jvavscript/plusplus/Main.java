package org.jvavscript.plusplus;

import java.io.*;
import java.util.*;
import com.mojang.brigadier.*;
import com.mojang.brigadier.exceptions.*;
import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static com.mojang.brigadier.arguments.DoubleArgumentType.*;
import static com.mojang.brigadier.arguments.BoolArgumentType.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;

public class Main {

	private static final Object obj = new Object();
	private static final CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
	public static boolean sugar = true;
	public static boolean s2d = false;
	public static String VERSION = "0.3.3";
	private static final String VOID = "";
	private static Object lastCommandResult = VOID;
	private static final Map<String,Object> map = new HashMap<>();
	private static final Map<String,String> mapDefinition = new HashMap<>();
	private static final Map<String,Integer> mapLabel = new HashMap<>();
	private static String labelName = "";
	private static boolean shouldGoTo = false;


	private static ArrayList<String> readFile(String filename) throws IOException {
		FileInputStream fis = new FileInputStream(filename);
		ArrayList<String> lines = new ArrayList<>();
		//Construct BufferedReader from InputStreamReader
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));

		String line;
		while ((line = br.readLine()) != null) {
			if(line.startsWith("#include ")){
				line = line.substring(9);
				if(line.charAt(0)=='"') line = line.substring(1, line.length()-1);
				lines.addAll(readFile(line));
				continue;
			}
			lines.add(line);
		}
		br.close();
        fis.close();
		return lines;
	}
	
	private static String replaceVar(String cmd,Map<String, Object> map){
		int vsIndex, veIndex;
		vsIndex=cmd.indexOf("${");
		veIndex=cmd.indexOf("}");
		String varName = "";
		try{
			varName = cmd.substring(vsIndex+2,veIndex);
		} catch (StringIndexOutOfBoundsException ignored) {}
		if(vsIndex + veIndex != -2 && map.containsKey(varName)){
			cmd = cmd.replaceFirst("\\$\\{.+?}",map.get(varName).toString());
			cmd = replaceVar(cmd,map);
		}
		return cmd;
	}
	
	private static String replaceDef(String cmd){
		for(Map.Entry<String, String> e: mapDefinition.entrySet())
			cmd = cmd.replace(e.getKey(), e.getValue());
		return cmd;
	}

	private static String desugar(String cmd){
		if((cmd.contains(" + ")
		||cmd.contains(" * ")
		||cmd.contains(" / ")
		)&&!cmd.contains("let "))
			cmd = "let "+cmd;
		if(cmd.contains(" =")&&!cmd.contains("==")&&!cmd.contains("set "))
			cmd = "set "+cmd.replace(" =", "");
		if((cmd.contains(" < ")||cmd.contains(" > ")||cmd.contains(" == "))&&!cmd.contains("cmp "))
			cmd = "cmp "+cmd;
		return cmd;
	}
	
	public synchronized static void main(String[] args) {
		registerCommands();
		if(args.length != 0){
			parseScript(args[0]);
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
		cmd = replaceVar(cmd,map);
		cmd = replaceDef(cmd);
		if(sugar)
			cmd = desugar(cmd);
		if(s2d)
			cmd = cmd.replaceFirst("set","define");
		try {
			if(cmd.isEmpty()){
				System.out.print(">>> ");
				return;
			}
			dispatcher.execute(cmd.replace("\\n","\n"), obj);
			if(shouldGoTo){
				System.out.println("W: goto is not allowed to use in interactive mode, do nothing.");
				shouldGoTo=false;
			}
			System.out.println(" <  "+(lastCommandResult == VOID ? "(void)" : lastCommandResult));
			System.out.print(">>> ");
		} catch (CommandSyntaxException e) {
			System.out.println(e.getMessage());
			System.out.print(">>> ");
		}
	}

	private static void parseScript(String arg) {
		try{
			ArrayList<String> a = readFile(arg);
			for(int i =0; i < a.size(); i++){
				String s = a.get(i);
				if(s.startsWith(";"))
					mapLabel.put(s.substring(1),i);
			}
			for(int i=0; i < a.size(); i++){
				String c = a.get(i);
				c = replaceVar(c,map);
				c = replaceDef(c);
				if(sugar)
					c = desugar(c);
				if(s2d)
					c = c.replaceFirst("set","define");
				try {
					if(c.isEmpty()||c.startsWith(";"))
						continue;
					dispatcher.execute(c, obj);
				} catch (CommandSyntaxException e) {
					System.out.println("Error:"+e.getMessage());
				}
				if(shouldGoTo){
					if(mapLabel.containsKey(labelName))
						i = mapLabel.get(labelName);
					else
						System.out.println("E:label \""+labelName+"\" not found.");
					shouldGoTo=false;
				}
			}
		} catch (IOException e){
			e.printStackTrace();
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
						try{
							lastCommandResult = getString(c, "String").charAt(getInteger(c, "index"));
							return 1;
						} catch(StringIndexOutOfBoundsException e) {
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
							lastCommandResult = getDouble(c, "num1")+getDouble(c, "num2");
							return 1;
						})
					)
				)
				.then(
					literal("*")
					.then(
						argument("num2", doubleArg())
						.executes(c -> {
							lastCommandResult = getDouble(c, "num1")*getDouble(c, "num2");
							return 1;
						})
					)
				)
				.then(
					literal("/")
					.then(
						argument("num2", doubleArg())
						.executes(c -> {
							lastCommandResult = getDouble(c, "num1")/getDouble(c, "num2");
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
						/*Object r = */map.put(getString(c, "name"),getString(c, "value"));
						//System.out.print( r == null ? "" : "W: "+getString(c, "name")+" now is "+getString(c, "value")+" instead of "+r+"\n");
						lastCommandResult = getString(c, "value");
						return 1;
					})
				)
				.executes(c -> {
					/*Object r = */map.put(getString(c, "name"), lastCommandResult);
					//System.out.print( r == null ? "" : "W: "+getString(c, "name")+" now is "+lstCmdRslt+" instead of "+r+"\n");
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
						/*String r = */
						mapDefinition.put(getString(c, "name"),getString(c, "value"));
						//System.out.print( r == null ? "" : "W: "+getString(c, "name")+" now is "+getString(c, "value")+" instead of "+r+"\n");
						lastCommandResult = getString(c, "value");
						return 1;
					})
				)
				.executes(c -> {
					/*String r = */
					mapDefinition.put(getString(c, "name"), lastCommandResult.toString());
					//System.out.print( r == null ? "" : "W: "+getString(c, "name")+" now is "+lstCmdRslt+" instead of "+r+"\n");
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
					System.out.print(getString(c,"String"));
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
						argument("Number2",doubleArg())
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
				argument("Number",doubleArg())
				.executes(c -> {
					lastCommandResult = (int)getDouble(c, "Number");
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
						if(getBool(c, "bool"))
							dispatcher.execute("goto "+getString(c,"label"), obj);
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
	}
}