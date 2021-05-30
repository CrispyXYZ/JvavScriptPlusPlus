package org.jvavscript.plusplus;

import  java.util.*;
import  com.mojang.brigadier.*;
import  com.mojang.brigadier.exceptions.*;
import  static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import  static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;
import  static com.mojang.brigadier.arguments.StringArgumentType.*;

public class Main {
	
	public static String VERSION="0.1.1";
	
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
					return 0;
				})
			)
		);
		dispatcher.register(
			literal("exit")
			.executes(c -> {
				System.exit(0);
				return 0;
			})
		);
		dispatcher.register(
		    literal("version")
		    .executes( c -> {
		    	dispatcher.execute("print "+VERSION,obj);
		    	return 0;
		    })
		);
		dispatcher.register(
			literal("help")
			.then(
				argument("Command", string())
					.executes(c -> {
						String command = getString(c, "Command");
						for(String x: dispatcher.getSmartUsage(dispatcher.getRoot().getChild(command), obj).values())
							System.out.printf("%s %s%n",command,x);
						return  0;
					})
				)
			.executes(c -> {
				for(String x: dispatcher.getSmartUsage(dispatcher.getRoot(), obj).values())
					System.out.println(x);
				return  0;
			})
		);
		System.out.println("JvavScript++ v"+VERSION);
		System.out.print(">>> ");
		while (true) {
			cmd = scanner.nextLine();
			try {
				dispatcher.execute(cmd, obj);
				System.out.print(">>> ");
			} catch (CommandSyntaxException e) {
				System.out.println(e.getMessage());
				System.out.print(">>> ");
			}
		}
	}
}