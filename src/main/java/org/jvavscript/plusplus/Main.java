package org.jvavscript.plusplus;

import java.io.*;
import java.util.*;
import com.mojang.brigadier.*;
import com.mojang.brigadier.exceptions.*;
import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static com.mojang.brigadier.arguments.BoolArgumentType.*;

public class Main {

	public static boolean sugar = false;
	public static boolean s2d = false;
	public static String VERSION = "0.3.0";
	private static final String VOID = "";
	private static Object lstCmdRslt = VOID;
	private static Map<String,Object> map = new HashMap<>();
	private static Map<String,String> mapD = new HashMap<>();
	private static Map<String,Integer> mapL = new HashMap<>();
	private static String labelName = "";
	private static boolean shouldGoTo = false;

	
	private static ArrayList<String> readFile(String fname) throws IOException {
		FileInputStream fis = new FileInputStream(fname);
		ArrayList<String> lines = new ArrayList<String>();
		//Construct BufferedReader from InputStreamReader
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));

		String line = null;
		while ((line = br.readLine()) != null) {
			lines.add(line);
		}
		br.close();
        fis.close();
		return lines;
	}
	
	private static String replaceVar(String cmd,Map map){
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
	
	private static String replaceDef(String cmd,Map<String,String> map){
		for(Map.Entry e: map.entrySet())
			cmd = cmd.replace(e.getKey().toString(),e.getValue().toString());
		return cmd;
	}

	private static String desugar(String cmd){
		if((cmd.contains(" + ")
		||cmd.contains(" * ")
		||cmd.contains(" / ")
		)&&!cmd.contains("let "))
			cmd = "let "+cmd;
		if(cmd.contains(" ="))
			cmd = "set "+cmd.replace(" =", "");
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
		dispatcher.register(
			literal("enable")
			.then(
				literal("sugar")
				.executes(c -> {
					sugar = true;
					lstCmdRslt = VOID;
					return 1;
				})
			)
			.then(
				literal("s2d")
				.executes(c -> {
					s2d = true;
					lstCmdRslt = VOID;
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
					lstCmdRslt = VOID;
					return 1;
				})
			)
			.then(
				literal("s2d")
				.executes(c -> {
					s2d = false;
					lstCmdRslt = VOID;
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
					lstCmdRslt = new Scanner(System.in).nextLine();
					return 1;
				})
			)
			.executes(c -> {
				lstCmdRslt = new Scanner(System.in).nextLine();
				return 1;
			})
		);
		dispatcher.register(
			literal("eq")
			.then(
				argument("A", string())
				.then(
					argument("B", string())
					.executes(c -> {
						lstCmdRslt = getString(c, "A").equals(getString(c, "B"));
						return 1;
					})
				)
			)
		);
		dispatcher.register(
			literal("if")
			.then(
				argument("bool", bool())
				.then(
					argument("label", string())
					.executes(c -> {
						if(getBool(c, "bool"))
							dispatcher.execute("goto "+getString(c,"label"),obj);
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
		if(args.length != 0){
			try{
				ArrayList<String> a = readFile(args[0]);
				for(int i =0; i < a.size(); i++){
					String s = a.get(i);
					if(s.startsWith(";"))
						mapL.put(s.substring(1),i);
				}
				f1:
				for(int i=0; i < a.size(); i++){
					String c = a.get(i);
					c = replaceVar(c,map);
					c = replaceDef(c,mapD);
					if(sugar)
						c = desugar(c);
					if(s2d)
						c = c.replaceFirst("set","define");
					try {
						if(c.isEmpty()||c.startsWith(";"))
							continue f1;
						dispatcher.execute(c, obj);
					} catch (CommandSyntaxException e) {
						System.out.println("Error:"+e.getMessage());
					}
					if(shouldGoTo){
						if(mapL.containsKey(labelName))
							i = mapL.get(labelName);
						else
							System.out.println("E:label \""+labelName+"\" not found.");
						shouldGoTo=false;
					}
				}
			} catch (IOException e){
				System.out.println("Error:"+e.getMessage());
			}
			return;
		}
		System.out.println("JvavScript++ v" + VERSION);
		System.out.print(">>> ");
		w1:
		while (true) {
			cmd = scanner.nextLine();
			cmd = replaceVar(cmd,map);
			cmd = replaceDef(cmd,mapD);
			if(sugar)
				cmd = desugar(cmd);
			if(s2d)
				cmd = cmd.replaceFirst("set","define");
			try {
				if(cmd.isEmpty()){
					System.out.print(">>> ");
					continue w1;
				}
				dispatcher.execute(cmd, obj);
				if(shouldGoTo){
					System.out.println("W: goto is not allowed to use in interactive mode, do nothing.");
					shouldGoTo=false;
				}
				System.out.println(" <  "+(lstCmdRslt == VOID ? "(void)" : lstCmdRslt));
				System.out.print(">>> ");
			} catch (CommandSyntaxException e) {
				System.out.println(e.getMessage());
				System.out.print(">>> ");
			}
		}
	}
}