package edu.montana.csci.csci468.parser;

import edu.montana.csci.csci468.parser.expressions.*;
import edu.montana.csci.csci468.parser.statements.*;
import edu.montana.csci.csci468.tokenizer.CatScriptTokenizer;
import edu.montana.csci.csci468.tokenizer.Token;
import edu.montana.csci.csci468.tokenizer.TokenList;
import edu.montana.csci.csci468.tokenizer.TokenType;

import java.util.LinkedList;
import java.util.List;

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
            return null;
        }
    }

    //============================================================
    //  Expressions
    //============================================================

    private Expression parseExpression() {
        return parseAdditiveExpression();
    }

    private Expression parseAdditiveExpression() {
        Expression expression = parseUnaryExpression();
        while (tokens.match(PLUS, MINUS)) {
            Token operator = tokens.consumeToken();
            final Expression rightHandSide = parseUnaryExpression();
            AdditiveExpression additiveExpression = new AdditiveExpression(operator, expression, rightHandSide);
            additiveExpression.setStart(expression.getStart());
            additiveExpression.setEnd(rightHandSide.getEnd());
            expression = additiveExpression;
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
            Expression containedExpression = parseAdditiveExpression();
            if (!tokens.match(RIGHT_PAREN)) {
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
                    tokenArgumentList.add(parsePrimaryExpression());
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
