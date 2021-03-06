package edu.montana.csci.csci468.parser.expressions;

import edu.montana.csci.csci468.bytecode.ByteCodeGenerator;
import edu.montana.csci.csci468.eval.CatscriptRuntime;
import edu.montana.csci.csci468.parser.CatscriptType;
import edu.montana.csci.csci468.parser.ErrorType;
import edu.montana.csci.csci468.parser.ParseError;
import edu.montana.csci.csci468.parser.SymbolTable;
import edu.montana.csci.csci468.parser.statements.FunctionDefinitionStatement;
import edu.montana.csci.csci468.parser.statements.Statement;
import org.objectweb.asm.Opcodes;

import java.util.LinkedList;
import java.util.List;

public class FunctionCallExpression extends Expression {
    private final String name;
    List<Expression> arguments;
    private CatscriptType type;

    public FunctionCallExpression(String functionName, List<Expression> arguments) {
        this.arguments = new LinkedList<>();
        for (Expression value : arguments) {
            this.arguments.add(addChild(value));
        }
        this.name = functionName;
    }

    public List<Expression> getArguments() {
        return arguments;
    }

    public String getName() {
        return name;
    }

    @Override
    public CatscriptType getType() {
        return type;
    }

    @Override
    public void validate(SymbolTable symbolTable) {
        FunctionDefinitionStatement function = symbolTable.getFunction(getName());
        if (function == null) {
            addError(ErrorType.UNKNOWN_NAME);
            type = CatscriptType.OBJECT;
        } else {
            type = function.getType();
            if (arguments.size() != function.getParameterCount()) {
                addError(ErrorType.ARG_MISMATCH);
            } else {
                for (int i = 0; i < arguments.size(); i++) {
                    Expression argument = arguments.get(i);
                    argument.validate(symbolTable);
                    CatscriptType parameterType = function.getParameterType(i);
                    if (!parameterType.isAssignableFrom(argument.getType())) {
                        argument.addError(ErrorType.INCOMPATIBLE_TYPES);
                    }
                }
            }
        }
    }

    //==============================================================
    // Implementation
    //==============================================================

    @Override
    public Object evaluate(CatscriptRuntime runtime) {
        FunctionDefinitionStatement functionStatement = getProgram().getFunction(name);
        runtime.pushScope();
        for (int i = 0; i < functionStatement.getParameterCount(); i++) {
            runtime.setValue(functionStatement.getParameterName(i), arguments.get(i).evaluate(runtime));
        }
        List<Statement> body = functionStatement.getBody();
        for (Statement statement : body) {
            statement.execute(runtime);
        }
        runtime.popScope();
        return runtime.getValue(name);
    }

    @Override
    public void transpile(StringBuilder javascript) {
        super.transpile(javascript);
    }

    @Override
    public void compile(ByteCodeGenerator code) {
        code.addVarInstruction(Opcodes.ALOAD, 0);
        FunctionDefinitionStatement function = getProgram().getFunction(name);
        for (int i = 0; i < arguments.size(); i++) {
            Expression expression = arguments.get(i);
            expression.compile(code);
            CatscriptType expressionType = expression.getType();
            CatscriptType parameterType = function.getParameterType(i);
            if(parameterType.equals(CatscriptType.OBJECT) &&
                    (expressionType.equals(CatscriptType.INT) || expressionType.equals(CatscriptType.BOOLEAN))) {
                box(code, expressionType);
            }
        }
        code.addMethodInstruction(Opcodes.INVOKEVIRTUAL, code.getProgramInternalName(), name, function.getDescriptor());
    }


}
