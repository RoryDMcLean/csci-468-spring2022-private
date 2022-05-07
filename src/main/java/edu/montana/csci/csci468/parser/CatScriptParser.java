package edu.montana.csci.csci468.parser;

import edu.montana.csci.csci468.parser.expressions.*;
import edu.montana.csci.csci468.parser.statements.*;
import edu.montana.csci.csci468.tokenizer.CatScriptTokenizer;
import edu.montana.csci.csci468.tokenizer.Token;
import edu.montana.csci.csci468.tokenizer.TokenList;
import edu.montana.csci.csci468.tokenizer.TokenType;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import static edu.montana.csci.csci468.tokenizer.TokenType.*;

public class CatScriptParser {

    private TokenList tokens;
    private FunctionDefinitionStatement currentFunctionDefinition;

    public CatScriptProgram parse(String source) {
        tokens = new CatScriptTokenizer(source).getTokens();

        // first parse an expression
        CatScriptProgram program = new CatScriptProgram();
        program.setStart(tokens.getCurrentToken());
        Expression expression = null;
        try {
            expression = parseExpression();
        } catch(RuntimeException re) {
            // ignore :)
        }
        if (expression == null || tokens.hasMoreTokens()) {
            tokens.reset();
            while (tokens.hasMoreTokens()) {
                program.addStatement(parseProgramStatement());
            }
        } else {
            program.setExpression(expression);
        }

        program.setEnd(tokens.getCurrentToken());
        return program;
    }

    public CatScriptProgram parseAsExpression(String source) {
        tokens = new CatScriptTokenizer(source).getTokens();
        CatScriptProgram program = new CatScriptProgram();
        program.setStart(tokens.getCurrentToken());
        Expression expression = parseExpression();
        program.setExpression(expression);
        program.setEnd(tokens.getCurrentToken());
        return program;
    }

    //============================================================
    //  Statements
    //============================================================

    private Statement parseProgramStatement() {
        Statement printStmt = parsePrintStatement();
        if (printStmt != null) {
            return printStmt;
        }
        return new SyntaxErrorStatement(tokens.consumeToken());
    }

    private Statement parsePrintStatement() {
        if (tokens.match(PRINT)) {

            PrintStatement printStatement = new PrintStatement();
            printStatement.setStart(tokens.consumeToken());

            require(LEFT_PAREN, printStatement);
            printStatement.setExpression(parseExpression());
            printStatement.setEnd(require(RIGHT_PAREN, printStatement));

            return printStatement;
        } else {
            return parseForStatement();
        }
    }

    private Statement parseForStatement() {
        if (tokens.match(FOR)) {
            ForStatement forStatement = new ForStatement();
            forStatement.setStart(tokens.consumeToken());

            require(LEFT_PAREN, forStatement);
            forStatement.setVariableName(require(IDENTIFIER, forStatement).getStringValue());
            require(IN, forStatement);
            forStatement.setExpression(parseExpression());
            require(RIGHT_PAREN, forStatement);

            require(LEFT_BRACE, forStatement);
            List<Statement> body = new LinkedList<>();
            while(tokens.hasMoreTokens() && !tokens.match(RIGHT_BRACE)) {
                body.add(parseProgramStatement());
            }
            forStatement.setBody(body);
            forStatement.setEnd(require(RIGHT_BRACE, forStatement));

            return forStatement;
        } else {
            return parseIfStatement();
        }
    }

    private Statement parseIfStatement() {
        if (tokens.match(IF)) {
            IfStatement ifStatement = new IfStatement();
            ifStatement.setStart(tokens.consumeToken());

            require(LEFT_PAREN, ifStatement);
            ifStatement.setExpression(parseExpression());
            require(RIGHT_PAREN, ifStatement);

            require(LEFT_BRACE, ifStatement);
            List<Statement> body = new LinkedList<>();
            while(tokens.hasMoreTokens() && !tokens.match(RIGHT_BRACE)) {
                body.add(parseProgramStatement());
            }
            ifStatement.setTrueStatements(body);
            Token ifBrace = require(RIGHT_BRACE, ifStatement);

            if (tokens.match(ELSE)) {
                require(ELSE, ifStatement);
                require(LEFT_BRACE, ifStatement);
                body = new LinkedList<>();
                while(tokens.hasMoreTokens() && !tokens.match(RIGHT_BRACE)) {
                    body.add(parseProgramStatement());
                }
                ifStatement.setElseStatements(body);
                ifStatement.setEnd(require(RIGHT_BRACE, ifStatement));
            } else {
                ifStatement.setEnd(ifBrace);
            }

            return ifStatement;
        } else {
            return parseVariableStatement();
        }
    }

    private Statement parseVariableStatement() {
        if(tokens.match(VAR)) {
            VariableStatement varStatement = new VariableStatement();
            varStatement.setStart(tokens.consumeToken());
            varStatement.setVariableName(require(IDENTIFIER, varStatement).getStringValue());

            if (tokens.match(EQUAL)) {
                tokens.consumeToken();
            }
            else {
                require(COLON, varStatement);
                String type = require(IDENTIFIER, varStatement).getStringValue();
                switch (type) {
                    case "int":
                        varStatement.setExplicitType(CatscriptType.INT);
                        break;
                    case "bool":
                        varStatement.setExplicitType(CatscriptType.BOOLEAN);
                        break;
                    case "string":
                        varStatement.setExplicitType(CatscriptType.STRING);
                        break;
                    case "object":
                        varStatement.setExplicitType(CatscriptType.OBJECT);
                        break;
                    case "list":
                        require(LESS, varStatement);
                        switch (require(IDENTIFIER, varStatement).getStringValue()) {
                            case "int":
                                varStatement.setExplicitType(CatscriptType.getListType(CatscriptType.INT));
                                break;
                            case "bool":
                                varStatement.setExplicitType(CatscriptType.getListType(CatscriptType.BOOLEAN));
                                break;
                            case "string":
                                varStatement.setExplicitType(CatscriptType.getListType(CatscriptType.STRING));
                                break;
                            case "object":
                                varStatement.setExplicitType(CatscriptType.getListType(CatscriptType.OBJECT));
                                break;
                        }
                        require(GREATER, varStatement);
                        break;
                }
                require(EQUAL, varStatement);
            }

            varStatement.setExpression(parseExpression());
            varStatement.setEnd(tokens.lastToken());

            return varStatement;
        }
        else {
            return parseFunctionCallStatement();
        }
    }

    private Statement parseFunctionCallStatement() {
        if(tokens.match(IDENTIFIER)) {
            Expression parsedExpression = parseExpression();
            if (parsedExpression instanceof FunctionCallExpression) {
                return new FunctionCallStatement((FunctionCallExpression) parsedExpression);
            }
            else {
                AssignmentStatement assignmentStatement = new AssignmentStatement();
                assignmentStatement.setVariableName(parsedExpression.getStart().getStringValue());
                assignmentStatement.setStart(parsedExpression.getStart());
                require(EQUAL, assignmentStatement);
                assignmentStatement.setExpression(parseExpression());

                return assignmentStatement;
            }
        }
        else {
            return parseFunctionDefinitionStatement();
        }
    }

    private Statement parseFunctionDefinitionStatement() {
        if(tokens.match(FUNCTION)) {
            FunctionDefinitionStatement functionDefinitionStatement = new FunctionDefinitionStatement();
            functionDefinitionStatement.setStart(tokens.consumeToken());
            functionDefinitionStatement.setName(require(IDENTIFIER, functionDefinitionStatement).getStringValue());
            require(LEFT_PAREN, functionDefinitionStatement);
            while(!tokens.match(RIGHT_PAREN)) {
                String paramName = require(IDENTIFIER, functionDefinitionStatement).getStringValue();
                TypeLiteral type = new TypeLiteral();

                if(tokens.match(COMMA) || tokens.match(RIGHT_PAREN)) {
                    type.setType(CatscriptType.OBJECT);
                } else {
                    require(COLON, functionDefinitionStatement);
                    switch (require(IDENTIFIER, functionDefinitionStatement).getStringValue()) {
                        case "int":
                            type.setType(CatscriptType.INT);
                            break;
                        case "bool":
                            type.setType(CatscriptType.BOOLEAN);
                            break;
                        case "string":
                            type.setType(CatscriptType.STRING);
                            break;
                        case "object":
                            type.setType(CatscriptType.OBJECT);
                            break;
                        case "list":
                            if (tokens.match(LESS)) {
                                tokens.consumeToken();
                                switch (require(IDENTIFIER, functionDefinitionStatement).getStringValue()) {
                                    case "int":
                                        type.setType(CatscriptType.getListType(CatscriptType.INT));
                                        break;
                                    case "bool":
                                        type.setType(CatscriptType.getListType(CatscriptType.BOOLEAN));
                                        break;
                                    case "string":
                                        type.setType(CatscriptType.getListType(CatscriptType.STRING));
                                        break;
                                    case "object":
                                        type.setType(CatscriptType.getListType(CatscriptType.OBJECT));
                                        break;
                                }
                                require(GREATER, functionDefinitionStatement);
                            } else {
                                type.setType(CatscriptType.getListType(CatscriptType.OBJECT));
                            }
                            break;
                    }
                }
                functionDefinitionStatement.addParameter(paramName, type);
                if(!tokens.match(RIGHT_PAREN)) {
                    require(COMMA, functionDefinitionStatement);
                }
            }
            tokens.consumeToken();

            TypeLiteral returnType = new TypeLiteral();
            if (tokens.match(COLON)) {
                tokens.consumeToken();
                switch (require(IDENTIFIER, functionDefinitionStatement).getStringValue()) {
                    case "int":
                        returnType.setType(CatscriptType.INT);
                        break;
                    case "bool":
                        returnType.setType(CatscriptType.BOOLEAN);
                        break;
                    case "string":
                        returnType.setType(CatscriptType.STRING);
                        break;
                    case "object":
                        returnType.setType(CatscriptType.OBJECT);
                        break;
                    case "list":
                        if (tokens.match(LESS)) {
                            tokens.consumeToken();
                            switch (require(IDENTIFIER, functionDefinitionStatement).getStringValue()) {
                                case "int":
                                    returnType.setType(CatscriptType.getListType(CatscriptType.INT));
                                    break;
                                case "bool":
                                    returnType.setType(CatscriptType.getListType(CatscriptType.BOOLEAN));
                                    break;
                                case "string":
                                    returnType.setType(CatscriptType.getListType(CatscriptType.STRING));
                                    break;
                                case "object":
                                    returnType.setType(CatscriptType.getListType(CatscriptType.OBJECT));
                                    break;
                            }
                            require(GREATER, functionDefinitionStatement);
                        } else {
                            returnType.setType(CatscriptType.getListType(CatscriptType.OBJECT));
                        }
                        break;
                    default:
                        returnType = null;
                }
            } else {
                returnType = null;
            }
            functionDefinitionStatement.setType(returnType);

            require(LEFT_BRACE, functionDefinitionStatement);
            List<Statement> body = new LinkedList<>();
            while(tokens.hasMoreTokens() && !tokens.match(RIGHT_BRACE)) {
                if(tokens.match(RETURN)) {
                    tokens.consumeToken();
                    body.add(parseReturnStatement(functionDefinitionStatement));
                } else {
                    body.add(parseProgramStatement());
                }
            }
            functionDefinitionStatement.setBody(body);
            functionDefinitionStatement.setEnd(require(RIGHT_BRACE, functionDefinitionStatement));

            return functionDefinitionStatement;
        }
        else {
            return null;
        }
    }

    private Statement parseReturnStatement(FunctionDefinitionStatement functionDefinitionStatement) {
        ReturnStatement returnStatement = new ReturnStatement();
        returnStatement.setFunctionDefinition(functionDefinitionStatement);
        if (!tokens.match(RIGHT_BRACE)) {
            returnStatement.setExpression(parseExpression());
        }

        return returnStatement;
    }

    //============================================================
    //  Expressions
    //============================================================

    private Expression parseExpression() {
        return parseEqualityExpression();
    }

    private Expression parseEqualityExpression() {
        Expression expression = parseComparisonExpression();
        while (tokens.match(EQUAL_EQUAL, BANG_EQUAL)) {
            Token operator = tokens.consumeToken();
            final Expression rightHandSide = parseComparisonExpression();
            EqualityExpression equalityExpression = new EqualityExpression(operator, expression, rightHandSide);
            equalityExpression.setStart(expression.getStart());
            equalityExpression.setEnd(rightHandSide.getEnd());
            expression = equalityExpression;
        }
        return expression;
    }

    private Expression parseComparisonExpression() {
        Expression expression = parseAdditiveExpression();
        while (tokens.match(LESS, GREATER, LESS_EQUAL, GREATER_EQUAL)) {
            Token operator = tokens.consumeToken();
            final Expression rightHandSide = parseAdditiveExpression();
            ComparisonExpression comparisonExpression = new ComparisonExpression(operator, expression, rightHandSide);
            comparisonExpression.setStart(expression.getStart());
            comparisonExpression.setEnd(rightHandSide.getEnd());
            expression = comparisonExpression;
        }
        return expression;
    }

    private Expression parseAdditiveExpression() {
        Expression expression = parseMultiplyExpression();
        while (tokens.match(PLUS, MINUS)) {
            Token operator = tokens.consumeToken();
            final Expression rightHandSide = parseMultiplyExpression();
            AdditiveExpression additiveExpression = new AdditiveExpression(operator, expression, rightHandSide);
            additiveExpression.setStart(expression.getStart());
            additiveExpression.setEnd(rightHandSide.getEnd());
            expression = additiveExpression;
        }
        return expression;
    }

    private Expression parseMultiplyExpression() {
        Expression expression = parseUnaryExpression();
        while (tokens.match(STAR, SLASH)) {
            Token operator = tokens.consumeToken();
            final Expression rightHandSide = parseUnaryExpression();
            FactorExpression factorExpression = new FactorExpression(operator, expression, rightHandSide);
            factorExpression.setStart(expression.getStart());
            factorExpression.setEnd(rightHandSide.getEnd());
            expression = factorExpression;
        }
        return expression;
    }

    private Expression parseUnaryExpression() {
        if (tokens.match(MINUS, NOT)) {
            Token token = tokens.consumeToken();
            Expression rhs = parseUnaryExpression();
            UnaryExpression unaryExpression = new UnaryExpression(token, rhs);
            unaryExpression.setStart(token);
            unaryExpression.setEnd(rhs.getEnd());
            return unaryExpression;
        } else {
            return parsePrimaryExpression();
        }
    }

    private Expression parsePrimaryExpression() {
        if (tokens.match(LEFT_PAREN)) {
            tokens.consumeToken();
            Expression containedExpression = parseExpression();
            if (!tokens.matchAndConsume(RIGHT_PAREN)) {
                return new SyntaxErrorExpression(tokens.consumeToken());
            } else {
                return new ParenthesizedExpression(containedExpression);
            }
        } else if (tokens.match(LEFT_BRACKET)) {
            tokens.consumeToken();
            List<Expression> tokenList = new LinkedList<>();
            while (!tokens.match(RIGHT_BRACKET)) {
                tokenList.add(parsePrimaryExpression());
                if (tokens.match(EOF)) {
                    ListLiteralExpression listExpression = new ListLiteralExpression(tokenList);
                    require(RIGHT_BRACKET, listExpression, ErrorType.UNTERMINATED_LIST);
                    return listExpression;
                } else if (!tokens.match(COMMA) && !tokens.match(RIGHT_BRACKET)) {
                    return new SyntaxErrorExpression(tokens.consumeToken());
                } else if (tokens.match(COMMA)) {
                    tokens.consumeToken();
                }
            }
            tokens.consumeToken();

            return new ListLiteralExpression(tokenList);

        } else if (tokens.match(INTEGER)) {
            Token integerToken = tokens.consumeToken();
            IntegerLiteralExpression integerExpression = new IntegerLiteralExpression(integerToken.getStringValue());
            integerExpression.setToken(integerToken);
            return integerExpression;
        } else if (tokens.match(STRING)) {
            Token stringToken = tokens.consumeToken();
            StringLiteralExpression stringExpression = new StringLiteralExpression(stringToken.getStringValue());
            stringExpression.setToken(stringToken);
            return stringExpression;
        } else if (tokens.match(TRUE)) {
            Token booleanToken = tokens.consumeToken();
            BooleanLiteralExpression booleanExpression = new BooleanLiteralExpression(true);
            booleanExpression.setToken(booleanToken);
            return booleanExpression;
        } else if (tokens.match(FALSE)) {
            Token booleanToken = tokens.consumeToken();
            BooleanLiteralExpression booleanExpression = new BooleanLiteralExpression(false);
            booleanExpression.setToken(booleanToken);
            return booleanExpression;
        } else if (tokens.match(NULL)) {
            Token nullToken = tokens.consumeToken();
            NullLiteralExpression nullExpression = new NullLiteralExpression();
            nullExpression.setToken(nullToken);
            return nullExpression;
        } else if (tokens.match(IDENTIFIER)) {
            Token identifierToken = tokens.consumeToken();
            if (!tokens.match(LEFT_PAREN)) {
                IdentifierExpression identifierExpression = new IdentifierExpression(identifierToken.getStringValue());
                identifierExpression.setToken(identifierToken);
                return identifierExpression;
            } else {
                tokens.consumeToken();
                List<Expression> tokenArgumentList = new LinkedList<>();
                while (!tokens.match(RIGHT_PAREN)) {
                    tokenArgumentList.add(parseExpression());
                    if (tokens.match(EOF)) {
                        FunctionCallExpression functionExpression = new
                                FunctionCallExpression(identifierToken.getStringValue(), tokenArgumentList);
                        require(RIGHT_PAREN, functionExpression, ErrorType.UNTERMINATED_ARG_LIST);
                        return functionExpression;
                    } else if (!tokens.match(COMMA) && !tokens.match(RIGHT_PAREN)) {
                        return new SyntaxErrorExpression(tokens.consumeToken());
                    } else if (tokens.match(COMMA)) {
                        tokens.consumeToken();
                    }
                }
                tokens.consumeToken();

                return new FunctionCallExpression(identifierToken.getStringValue(), tokenArgumentList);
            }
        } else {
            return new SyntaxErrorExpression(tokens.consumeToken());
        }
    }

    //============================================================
    //  Parse Helpers
    //============================================================
    private Token require(TokenType type, ParseElement elt) {
        return require(type, elt, ErrorType.UNEXPECTED_TOKEN);
    }

    private Token require(TokenType type, ParseElement elt, ErrorType msg) {
        if(tokens.match(type)){
            return tokens.consumeToken();
        } else {
            elt.addError(msg, tokens.getCurrentToken());
            return tokens.getCurrentToken();
        }
    }

}
