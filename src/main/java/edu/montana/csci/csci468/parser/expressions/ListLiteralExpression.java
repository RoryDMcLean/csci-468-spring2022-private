package edu.montana.csci.csci468.parser.expressions;

import edu.montana.csci.csci468.bytecode.ByteCodeGenerator;
import edu.montana.csci.csci468.eval.CatscriptRuntime;
import edu.montana.csci.csci468.parser.CatscriptType;
import edu.montana.csci.csci468.parser.SymbolTable;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ListLiteralExpression extends Expression {
    List<Expression> values;
    private CatscriptType type;

    public ListLiteralExpression(List<Expression> values) {
        this.values = new LinkedList<>();
        for (Expression value : values) {
            this.values.add(addChild(value));
        }
    }

    public List<Expression> getValues() {
        return values;
    }

    @Override
    public void validate(SymbolTable symbolTable) {
        for (Expression value : values) {
            value.validate(symbolTable);
        }
        if (values.size() > 0) {
            Expression lastExpression = values.get(0);
            for (Expression value : values) {
                if (lastExpression.getType() != value.getType()) {
                    type = CatscriptType.getListType(CatscriptType.OBJECT);
                    break;
                }
                lastExpression = value;
            }
            type = CatscriptType.getListType(lastExpression.getType());
        } else {
            type = CatscriptType.getListType(CatscriptType.OBJECT);
        }
    }

    @Override
    public CatscriptType getType() {
        return type;
    }

    //==============================================================
    // Implementation
    //==============================================================

    @Override
    public Object evaluate(CatscriptRuntime runtime) {
        List<Object> vals = new ArrayList<>();
        for (Expression value : values) {
            vals.add(value.evaluate(runtime));
        }
        return vals;
    }

    @Override
    public void transpile(StringBuilder javascript) {
        super.transpile(javascript);
    }

    @Override
    public void compile(ByteCodeGenerator code) {
        code.addTypeInstruction(Opcodes.NEW, ByteCodeGenerator.internalNameFor(LinkedList.class));
        code.addInstruction(Opcodes.DUP);
        code.addMethodInstruction(Opcodes.INVOKESPECIAL, ByteCodeGenerator.internalNameFor(LinkedList.class),
                "<init>", "()V");
        for(Expression value : values) {
            code.addInstruction(Opcodes.DUP);
            value.compile(code);
            box(code, value.getType());
            code.addMethodInstruction(Opcodes.INVOKEVIRTUAL, ByteCodeGenerator.internalNameFor(LinkedList.class),
                    "add", "(Ljava/Lang/Object;)Z");
            code.addInstruction(Opcodes.POP);
        }
    }


}
