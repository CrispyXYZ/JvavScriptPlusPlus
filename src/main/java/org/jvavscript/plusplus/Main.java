package org.jvavscript.plusplus;

import  java.util.*;
import  com.mojang.brigadier.*;
import  com.mojang.brigadier.exceptions.*;
import  static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import  static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;
import  static com.mojang.brigadier.arguments.StringArgumentType.*;

public class Main {
	public static void main(String[] args) {
		Object obj = new Object();
		Scanner scanner = new Scanner(System.in);
		String cmd = "noEmpty";
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
		System.out.print(">>>");
		while (!(cmd = scanner.nextLine()).isEmpty()) {
			try {
				dispatcher.execute(cmd, obj);
				System.out.print(">>>");
			} catch (CommandSyntaxException e) {
				System.out.println(e.getMessage());
				System.out.print(">>>");
			}
		}
	}
}