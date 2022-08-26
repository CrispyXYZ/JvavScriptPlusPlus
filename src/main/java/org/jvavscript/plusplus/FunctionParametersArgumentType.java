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

public class FunctionParametersArgumentType implements ArgumentType<List<String>> {

    public static FunctionParametersArgumentType functionParameters() {
        return new FunctionParametersArgumentType();
    }

    public static List<String> getFunctionParameters(final CommandContext<?> context, final String name) {
        return context.getArgument(name, List.class);
    }

    @Override
    public List<String> parse(final StringReader reader) throws CommandSyntaxException {
        char begin = reader.read();
        if(begin!='(') throw new SimpleCommandExceptionType(new LiteralMessage("expected (")).createWithContext(reader);
        String parameters = reader.readStringUntil(')');
        String[] parameter = parameters.split(",");
        for(int i=0;i<parameter.length;i++) {
            parameter[i] = parameter[i].trim();
        }
        return new ArrayList<>(Arrays.asList(parameter));
    }

    @Override
    public String toString() {
        return "functionParameters()";
    }

    @Override
    public Collection<String> getExamples() {
        return Arrays.asList("()","(a, b, c)");
    }

}
