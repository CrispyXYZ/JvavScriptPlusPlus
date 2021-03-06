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


//TODO 变量增加作用域
//TODO 多次调用同一函数
//TODO 函数不依赖label实现
public class Main {

	public static final String VERSION = "0.3.4";
	public static final String VOID = "";
	private static boolean sugar = true;
	private static boolean s2d = false;
	private static Object lstCmdRslt = VOID;
	private static Map<String,Object> map = new HashMap<>();
	private static Map<String,String> mapD = new HashMap<>();
	private static Map<String,Integer> mapL = new HashMap<>();
	private static Map<String,String[]> mapF = new HashMap<>();
	private static String labelName = "";
	private static boolean shouldGoTo = false;

	
	private static ArrayList<String> readFile(String fname) throws IOException {
		FileInputStream fis = new FileInputStream(fname);
		ArrayList<String> lines = new ArrayList<String>();
		//Construct BufferedReader from InputStreamReader
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));

		String line = null;
		boolean isInFun = false;
		String cfName = "";
		String[] cfParams = null;
		while ((line = br.readLine()) != null) {
			if(line.equals("#end")){
				isInFun = false;
				lines.add("goto "+cfName+"_end");
				continue;
			}
			if(isInFun){
				if(cfParams[0]!=""){
					for(String p : cfParams){
						line = line.replace(p, cfName+"_"+p);
					}
				}
			}
			if(line.startsWith("#include ")){
				line = line.substring(9);
				if(line.charAt(0)=='"') line = line.substring(1, line.length()-1);
				lines.addAll(readFile(line));
				continue;
			}
			if(line.startsWith("#fun ")){
				line = line.substring(5);
				cfName = line.substring(0,line.indexOf("("));
				cfParams = line.substring(line.indexOf("(")+1,line.indexOf(")")).split(",");
				mapF.put(cfName,cfParams);
				isInFun = true;
				line = ";"+cfName;
			}
			if(line.startsWith("#")
			&&line.indexOf("fun")!=1
			&&mapF.containsKey(line.substring(1,line.indexOf("(")))){
				String fun = line.substring(1,line.indexOf("("));
				String[] params = mapF.get(fun);
				String[] pValues = line.substring(line.indexOf("(")+1,line.indexOf(")")).split(",");
				if(params.length!=0){
					for(int i=0;i<params.length;i++){
						String param = params[i];
						String value = pValues[i];
						lines.add("set "+fun+"_"+param+" "+value);
					}
				}
				lines.add("goto "+fun);
				lines.add(";"+fun+"_end");
				continue;
			}
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
			sq = cmd.substring(vsIndex+2,veIndex);
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
		if(cmd.contains(" =")&&!cmd.contains("==")&&!cmd.contains("set "))
			cmd = "set "+cmd.replace(" =", "");
		if((cmd.contains(" < ")||cmd.contains(" > ")||cmd.contains(" == "))&&!cmd.contains("cmp "))
			cmd = "cmp "+cmd;
		return cmd;
	}
	
	public synchronized static void main(String[] args) {
		Object obj = new Object();
		Scanner scanner = new Scanner(System.in);
		String cmd;
		CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
		dispatcher.register(
			literal("print")
			.then(
				argument("String", string())
				.executes(c -> {
					System.out.print(getString(c, "String"));
					lstCmdRslt = VOID;
					return 1;
				})
			)
			.executes(c -> {
				System.out.print(lstCmdRslt);
				lstCmdRslt = VOID;
				return 1;
			})
		);
		dispatcher.register(
			literal("println")
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
			literal("charat")
			.then(
				argument("String", string())
				.then(
					argument("index", integer())
					.executes(c -> {
						try{
							lstCmdRslt = getString(c, "String").charAt(getInteger(c, "index"));
							return 1;
						} catch(StringIndexOutOfBoundsException e) {
							//System.out.println(e);
							lstCmdRslt = VOID;
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
						lstCmdRslt = getString(c, "String").indexOf(getString(c, "anotherString"));
						return 1;
					})
				)
			)
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
				argument("num1", doubleArg())
				.then(
					literal("+")
					.then(
						argument("num2", doubleArg())
						.executes(c -> {
							lstCmdRslt = getDouble(c, "num1")+getDouble(c, "num2");
							return 1;
						})
					)
				)
				.then(
					literal("*")
					.then(
						argument("num2", doubleArg())
						.executes(c -> {
							lstCmdRslt = getDouble(c, "num1")*getDouble(c, "num2");
							return 1;
						})
					)
				)
				.then(
					literal("/")
					.then(
						argument("num2", doubleArg())
						.executes(c -> {
							lstCmdRslt = getDouble(c, "num1")/getDouble(c, "num2");
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
						/*Object r = */map.put(getString(c, "name"),getString(c, "value"));
						//System.out.print( r == null ? "" : "W: "+getString(c, "name")+" now is "+getString(c, "value")+" instead of "+r+"\n");
						lstCmdRslt = getString(c, "value");
						return 1;
					})
				)
				.executes(c -> {
					/*Object r = */map.put(getString(c, "name"),lstCmdRslt);
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
						/*String r = */mapD.put(getString(c, "name"),getString(c, "value"));
						//System.out.print( r == null ? "" : "W: "+getString(c, "name")+" now is "+getString(c, "value")+" instead of "+r+"\n");
						lstCmdRslt = getString(c, "value");
						return 1;
					})
				)
				.executes(c -> {
					/*String r = */mapD.put(getString(c, "name"),lstCmdRslt.toString());
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
			literal("cmp")
			.then(
				argument("String1", string())
				.then(
					literal("==")
					.then(
						argument("String2", string())
						.executes(c -> {
							lstCmdRslt = getString(c, "String1").equals(getString(c, "String2"));
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
							lstCmdRslt = getDouble(c, "Number1") > getDouble(c, "Number2");
							return 1;
						})
					)
				)
				.then(
					literal("==")
					.then(
						argument("Number2",doubleArg())
						.executes(c -> {
							lstCmdRslt = getDouble(c, "Number1") == getDouble(c, "Number2");
							return 1;
						})
					)
				)
				.then(
					literal("<")
					.then(
						argument("Number2", doubleArg())
						.executes(c -> {
							lstCmdRslt = getDouble(c, "Number1") < getDouble(c, "Number2");
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
					lstCmdRslt = !getBool(c, "bool");
					return 1;
				})
			)
		);
		dispatcher.register(
			literal("random")
			.executes(c -> {
				lstCmdRslt = Math.random();
				return 1;
			})
		);
		dispatcher.register(
			literal("floor")
			.then(
				argument("Number",doubleArg())
				.executes(c -> {
					lstCmdRslt = (int)getDouble(c, "Number");
					return 1;
				})
			)
			.executes(c -> {
				lstCmdRslt = ((Double)lstCmdRslt).intValue();
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
		dispatcher.register(
			literal("return")
			.then(
				argument("value", string())
				.executes(c -> {
					lstCmdRslt = getString(c, "value");
					return 1;
				})
			)
		);
		if(args.length != 0){
			try{
				ArrayList<String> a = readFile(args[0]);
				//System.out.println(Arrays.toString(a.toArray()));
				for(int i =0; i < a.size(); i++){
					String s = a.get(i);
					if(s.startsWith(";"))
						mapL.put(s.substring(1),i);
				}
				for(int i=0; i < a.size(); i++){
					String c = a.get(i);
					c = replaceVar(c,map);
					c = replaceDef(c,mapD);
					if(sugar)
						c = desugar(c);
					if(s2d)
						c = c.replaceFirst("set","define");
					//System.out.println(c);
					try {
						if(c.isEmpty()||c.startsWith(";"))
							continue;
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
				System.out.println(e);
			}
			return;
		}
		System.out.println("JvavScript++ v" + VERSION);
		System.out.print(">>> ");
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
					continue;
				}
				dispatcher.execute(cmd.replace("\\n","\n"), obj);
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