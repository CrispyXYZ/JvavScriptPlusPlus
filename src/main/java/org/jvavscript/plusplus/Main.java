package org.jvavscript.plusplus;

import java.util.*;
import com.mojang.brigadier.*;
import com.mojang.brigadier.exceptions.*;
import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;

public class Main {

	public static String VERSION = "0.2.0";
	private static final String VOID = "";
	public static Object lstCmdRslt = VOID;
	public static Map<String,Object> map = new HashMap<>();
	public static Map<String,String> mapD = new HashMap<>();

	public static String replaceVar(String cmd,Map map){
		int vsIndex, veIndex;
		vsIndex=cmd.indexOf("${");
		veIndex=cmd.indexOf("}");
		String sq = "";
		try{
			sq = cmd.subSequence(vsIndex+2,veIndex).toString();
		} catch (StringIndexOutOfBoundsException e) {}
		//System.out.println(sq);
		if(vsIndex + veIndex != -2 && map.containsKey(sq)){
			cmd = cmd.replaceFirst("\\$\\{.+?\\}",map.get(sq).toString());
			cmd = replaceVar(cmd,map);
		}
		//System.out.println(cmd);
		return cmd;
	}
	
	public static String replaceDef(String cmd,Map<String,String> map){
		for(Map.Entry e: map.entrySet())
			cmd = cmd.replace(e.getKey().toString(),e.getValue().toString());
		return cmd;
	}

	public static void main(String[] args) {
		Object obj = new Object();
		Scanner scanner = new Scanner(System.in);
		String cmd;
		CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
		dispatcher.register(
			literal("print")
			.then(
				argument("String", string())
				.executes(c -> {
					System.out.println(getString(c, "String"));
					lstCmdRslt = VOID;
					return 1;
				})
			)
			.executes(c -> {
				System.out.println(lstCmdRslt);
				lstCmdRslt = VOID;
				return 1;
			})
		);
		dispatcher.register(
			literal("exit")
			.executes(c -> {
				System.exit(0);
				lstCmdRslt = "E: Could not exit program.";
				return 0;
			})
		);
		dispatcher.register(
			literal("version")
			.executes(c -> {
				dispatcher.execute("print " + VERSION, obj);
				lstCmdRslt = VOID;
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
					lstCmdRslt = VOID;
					return 1;
				})
			)
			.executes(c -> {
				for (String x : dispatcher.getSmartUsage(dispatcher.getRoot(), obj).values())
					System.out.println(x);
				lstCmdRslt = VOID;
				return 1;
			})
		);
		dispatcher.register(
			literal("let")
			.then(
				argument("int1", integer())
				.then(
					literal("+")
					.then(
						argument("int2", integer())
						.executes(c -> {
							lstCmdRslt = getInteger(c, "int1")+getInteger(c, "int2");
							return 1;
						})
					)
				)
				.then(
					literal("*")
					.then(
						argument("int2", integer())
						.executes(c -> {
							lstCmdRslt = getInteger(c, "int1")*getInteger(c, "int2");
							return 1;
						})
					)
				)
				.then(
					literal("/")
					.then(
						argument("int2", integer())
						.executes(c -> {
							lstCmdRslt = getInteger(c, "int1")/getInteger(c, "int2");
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
				System.out.println("defines:   " + mapD);
				lstCmdRslt = VOID;
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
						Object r = map.put(getString(c, "name"),getString(c, "value"));
						System.out.print( r == null ? "" : "W: "+getString(c, "name")+" now is "+getString(c, "value")+" instead of "+r+"\n");
						lstCmdRslt = VOID;
						return 1;
					})
				)
				.executes(c -> {
					Object r = map.put(getString(c, "name"),lstCmdRslt);
					System.out.print( r == null ? "" : "W: "+getString(c, "name")+" now is "+lstCmdRslt+" instead of "+r+"\n");
					lstCmdRslt = VOID;
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
						String r = mapD.put(getString(c, "name"),getString(c, "value"));
						System.out.print( r == null ? "" : "W: "+getString(c, "name")+" now is "+getString(c, "value")+" instead of "+r+"\n");
						lstCmdRslt = VOID;
						return 1;
					})
				)
				.executes(c -> {
					String r = mapD.put(getString(c, "name"),lstCmdRslt.toString());
					System.out.print( r == null ? "" : "W: "+getString(c, "name")+" now is "+lstCmdRslt+" instead of "+r+"\n");
					lstCmdRslt = VOID;
					return 1;
				})
			)
		);
		System.out.println("JvavScript++ v" + VERSION);
		System.out.print(">>> ");
		while (true) {
			cmd = scanner.nextLine();
			cmd = replaceVar(cmd,map);
			cmd = replaceDef(cmd,mapD);
			try {
				dispatcher.execute(cmd, obj);
				System.out.println(" <  "+(lstCmdRslt == VOID ? "(void)" : lstCmdRslt));
				System.out.print(">>> ");
			} catch (CommandSyntaxException e) {
				System.out.println(e.getMessage());
				System.out.print(">>> ");
			}
		}
	}
}