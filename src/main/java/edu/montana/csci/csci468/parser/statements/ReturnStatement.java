package edu.montana.csci.csci468.parser.statements;

import edu.montana.csci.csci468.bytecode.ByteCodeGenerator;
import edu.montana.csci.csci468.eval.CatscriptRuntime;
import edu.montana.csci.csci468.parser.CatscriptType;
import edu.montana.csci.csci468.parser.ErrorType;
import edu.montana.csci.csci468.parser.ParseError;
import edu.montana.csci.csci468.parser.SymbolTable;
import edu.montana.csci.csci468.parser.expressions.Expression;
import org.objectweb.asm.Opcodes;

public class ReturnStatement extends Statement {
    private Expression expression;
    private FunctionDefinitionStatement function;

    public void setExpression(Expression parseExpression) {
        this.expression = addChild(parseExpression);
    }

    public void setFunctionDefinition(FunctionDefinitionStatement func) {
        this.function = func;
    }

    public Expression getExpression() {
        return expression;
    }

    @Override
    public void validate(SymbolTable symbolTable) {
        if (expression != null) {
            expression.validate(symbolTable);
            if (!function.getType().isAssignableFrom(expression.getType())) {
                expression.addError(ErrorType.INCOMPATIBLE_TYPES);
            }
        } else {
            if (!function.getType().equals(CatscriptType.VOID)) {
                addError(ErrorType.INCOMPATIBLE_TYPES);
            }
        }
    }

    //==============================================================
    // Implementation
    //==============================================================
    @Override
    public void execute(CatscriptRuntime runtime) {
        Object returnValue = expression.evaluate(runtime);
        runtime.setValue(function.getName(), returnValue);
    }

    @Override
    public void transpile(StringBuilder javascript) {
        super.transpile(javascript);
    }

    @Override
    public void compile(ByteCodeGenerator code) {
        if (expression == null) {
            code.addInstruction(Opcodes.RETURN);
        } else {
            expression.compile(code);
            CatscriptType returnType = function.getType();
            CatscriptType expressionType = expression.getType();

            if (returnType.equals(CatscriptType.OBJECT) &&
                    (expressionType.equals(CatscriptType.INT) || expressionType.equals(CatscriptType.BOOLEAN))) {
                box(code, expressionType);
            }

            if (returnType.equals(CatscriptType.INT) || returnType.equals(CatscriptType.BOOLEAN)) {
                code.addInstruction(Opcodes.IRETURN);
            } else {
                code.addInstruction(Opcodes.ARETURN);
            }
        }
    }

}