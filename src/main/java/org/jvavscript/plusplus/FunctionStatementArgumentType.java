package org.jvavscript.plusplus;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class FunctionStatementArgumentType implements ArgumentType<List<String>> {

    public static FunctionStatementArgumentType functionStatement() {
        return new FunctionStatementArgumentType();
    }

    public static List<String> getFunctionStatement(final CommandContext<?> context, final String name) {
        return context.getArgument(name, List.class);
    }

    @Override
    public List<String> parse(final StringReader reader) throws CommandSyntaxException {
        char begin = reader.read();
        if(begin!='[') throw new SimpleCommandExceptionType(new LiteralMessage("expected [")).createWithContext(reader);
        String statement = reader.getRemaining();
        statement = statement.substring(0,statement.length()-1);
        reader.setCursor(reader.getTotalLength());
        String[] split = statement.split(",");
        for(int i=0;i<split.length;i++) {
            split[i] = split[i].trim();
        }
        return new ArrayList<>(Arrays.asList(split));
    }

    @Override
    public String toString() {
        return "functionStatement()";
    }

    @Override
    public Collection<String> getExamples() {
        return Arrays.asList("{println \"Hello World\"}","{%arg1% + %arg2%, tmp =, ${tmp}/2,return}","{}");
    }

}
