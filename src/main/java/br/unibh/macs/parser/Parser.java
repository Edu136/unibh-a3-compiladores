package br.unibh.macs.parser;

import static br.unibh.macs.lexer.TokenType.*;

import br.unibh.macs.ast.BinaryOp;
import br.unibh.macs.ast.Expr;
import br.unibh.macs.ast.FuncDecl;
import br.unibh.macs.ast.Item;
import br.unibh.macs.ast.Program;
import br.unibh.macs.ast.Stmt;
import br.unibh.macs.ast.Type;
import br.unibh.macs.ast.UnaryOp;
import br.unibh.macs.erro.CompileError;
import br.unibh.macs.lexer.Token;
import br.unibh.macs.lexer.TokenType;
import java.util.ArrayList;
import java.util.List;

/**
 * Analisador sintático por descida recursiva: um método por regra da gramática.
 * Para no primeiro erro, lançando {@link CompileError}.
 */
public final class Parser {

    private final List<Token> tokens;
    private int pos = 0;

    private Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public static Program analisar(List<Token> tokens) {
        return new Parser(tokens).programa();
    }

    // programa = { declFuncao | comando } EOF ;
    private Program programa() {
        List<Item> itens = new ArrayList<>();
        while (!verificar(EOF)) {
            itens.add(comando());
        }
        return new Program(itens);
    }

    // ---------- comandos ----------

    private Stmt comando() {
        return switch (atual().type()) {
            case VAR -> {
                Stmt.VarDecl d = declVar();
                consumirPontoEVirgula();
                yield d;
            }
            case ID -> {
                Stmt s = atribuicaoOuChamada();
                consumirPontoEVirgula();
                yield s;
            }
            case PRINT -> cmdPrint();
            case INPUT -> cmdInput();
            case LBRACE -> bloco();
            default -> throw esperado("comando");
        };
    }

    // declVar = "var" ID ":" tipo [ "=" expr ] ;
    private Stmt.VarDecl declVar() {
        Token var = consumir(VAR, "'var'");
        Token nome = consumir(ID, "identificador");
        consumir(COLON, "':'");
        Type tipo = tipo();
        Expr init = aceitar(ASSIGN) ? expr() : null;
        return new Stmt.VarDecl(nome.lexema(), tipo, init, var.linha(), var.coluna());
    }

    /** Comando que começa com identificador: chamada se vier "(", atribuição se vier "=". */
    private Stmt atribuicaoOuChamada() {
        Token nome = consumir(ID, "identificador");
        if (verificar(LPAREN)) {
            Expr.Call call = chamada(nome);
            return new Stmt.CallStmt(call, nome.linha(), nome.coluna());
        }
        consumir(ASSIGN, "'='");
        return new Stmt.Assign(nome.lexema(), expr(), nome.linha(), nome.coluna());
    }

    // cmdPrint = "print" "(" expr ")" ";" ;
    private Stmt.Print cmdPrint() {
        Token print = consumir(PRINT, "'print'");
        consumir(LPAREN, "'('");
        Expr e = expr();
        consumir(RPAREN, "')'");
        consumirPontoEVirgula();
        return new Stmt.Print(e, print.linha(), print.coluna());
    }

    // cmdInput = "input" "(" ID ")" ";" ;
    private Stmt.Input cmdInput() {
        Token input = consumir(INPUT, "'input'");
        consumir(LPAREN, "'('");
        Token nome = consumir(ID, "identificador");
        consumir(RPAREN, "')'");
        consumirPontoEVirgula();
        return new Stmt.Input(nome.lexema(), input.linha(), input.coluna());
    }

    // bloco = "{" { comando } "}" ;
    private Stmt.Block bloco() {
        Token abre = consumir(LBRACE, "'{'");
        List<Stmt> stmts = new ArrayList<>();
        while (!verificar(RBRACE) && !verificar(EOF)) {
            stmts.add(comando());
        }
        consumir(RBRACE, "'}'");
        return new Stmt.Block(stmts, abre.linha(), abre.coluna());
    }

    // tipo = "int" | "float" | "char" | "bool" | "string" ;
    private Type tipo() {
        Type tipo = switch (atual().type()) {
            case INT -> Type.INT;
            case FLOAT -> Type.FLOAT;
            case CHAR -> Type.CHAR;
            case BOOL -> Type.BOOL;
            case STRING -> Type.STRING;
            default -> throw esperado("tipo");
        };
        avancar();
        return tipo;
    }

    // ---------- expressões ----------

    // expr = ou ;
    private Expr expr() {
        return ou();
    }

    // ou = e { "||" e } ;
    private Expr ou() {
        Expr esq = e();
        while (verificar(OR)) {
            Token op = avancar();
            esq = new Expr.Binary(BinaryOp.OR, esq, e(), op.linha(), op.coluna());
        }
        return esq;
    }

    // e = igualdade { "&&" igualdade } ;
    private Expr e() {
        Expr esq = igualdade();
        while (verificar(AND)) {
            Token op = avancar();
            esq = new Expr.Binary(BinaryOp.AND, esq, igualdade(), op.linha(), op.coluna());
        }
        return esq;
    }

    // igualdade = relacional { ( "==" | "!=" ) relacional } ;
    private Expr igualdade() {
        Expr esq = relacional();
        while (verificar(EQ) || verificar(NE)) {
            Token op = avancar();
            BinaryOp bop = op.type() == EQ ? BinaryOp.EQ : BinaryOp.NE;
            esq = new Expr.Binary(bop, esq, relacional(), op.linha(), op.coluna());
        }
        return esq;
    }

    // relacional = aditiva [ ( "<" | "<=" | ">" | ">=" ) aditiva ] ;   (não encadeia)
    private Expr relacional() {
        Expr esq = aditiva();
        BinaryOp bop = operadorRelacional(atual().type());
        if (bop == null) {
            return esq;
        }
        Token op = avancar();
        return new Expr.Binary(bop, esq, aditiva(), op.linha(), op.coluna());
    }

    private static BinaryOp operadorRelacional(TokenType tipo) {
        return switch (tipo) {
            case LT -> BinaryOp.LT;
            case LE -> BinaryOp.LE;
            case GT -> BinaryOp.GT;
            case GE -> BinaryOp.GE;
            default -> null;
        };
    }

    // aditiva = mult { ( "+" | "-" ) mult } ;
    private Expr aditiva() {
        Expr esq = mult();
        while (verificar(PLUS) || verificar(MINUS)) {
            Token op = avancar();
            BinaryOp bop = op.type() == PLUS ? BinaryOp.ADD : BinaryOp.SUB;
            esq = new Expr.Binary(bop, esq, mult(), op.linha(), op.coluna());
        }
        return esq;
    }

    // mult = unaria { ( "*" | "/" | "%" ) unaria } ;
    private Expr mult() {
        Expr esq = unaria();
        while (verificar(STAR) || verificar(SLASH) || verificar(PERCENT)) {
            Token op = avancar();
            BinaryOp bop = switch (op.type()) {
                case STAR -> BinaryOp.MUL;
                case SLASH -> BinaryOp.DIV;
                default -> BinaryOp.MOD;
            };
            esq = new Expr.Binary(bop, esq, unaria(), op.linha(), op.coluna());
        }
        return esq;
    }

    // unaria = ( "!" | "-" ) unaria | primaria ;
    private Expr unaria() {
        if (verificar(NOT) || verificar(MINUS)) {
            Token op = avancar();
            UnaryOp uop = op.type() == NOT ? UnaryOp.NOT : UnaryOp.NEG;
            return new Expr.Unary(uop, unaria(), op.linha(), op.coluna());
        }
        return primaria();
    }

    // primaria = literal | "true" | "false" | ID | chamada | "(" expr ")" ;
    private Expr primaria() {
        Token t = atual();
        return switch (t.type()) {
            case INT_LIT -> {
                avancar();
                yield new Expr.IntLit((Integer) t.valor(), t.linha(), t.coluna());
            }
            case FLOAT_LIT -> {
                avancar();
                yield new Expr.FloatLit((Double) t.valor(), t.linha(), t.coluna());
            }
            case CHAR_LIT -> {
                avancar();
                yield new Expr.CharLit((Character) t.valor(), t.linha(), t.coluna());
            }
            case STRING_LIT -> {
                avancar();
                yield new Expr.StringLit((String) t.valor(), t.linha(), t.coluna());
            }
            case TRUE, FALSE -> {
                avancar();
                yield new Expr.BoolLit(t.type() == TRUE, t.linha(), t.coluna());
            }
            case ID -> {
                avancar();
                yield verificar(LPAREN) ? chamada(t) : new Expr.Var(t.lexema(), t.linha(), t.coluna());
            }
            case LPAREN -> {
                avancar();
                Expr dentro = expr();
                consumir(RPAREN, "')'");
                yield dentro;
            }
            default -> throw esperado("expressão");
        };
    }

    // chamada = ID "(" [ expr { "," expr } ] ")" ;   (o ID já foi consumido)
    private Expr.Call chamada(Token nome) {
        consumir(LPAREN, "'('");
        List<Expr> args = new ArrayList<>();
        if (!verificar(RPAREN)) {
            do {
                args.add(expr());
            } while (aceitar(COMMA));
        }
        consumir(RPAREN, "')'");
        return new Expr.Call(nome.lexema(), args, nome.linha(), nome.coluna());
    }

    // ---------- navegação e erros ----------

    private Token atual() {
        return tokens.get(pos);
    }

    private Token avancar() {
        Token t = tokens.get(pos);
        if (t.type() != EOF) {
            pos++;
        }
        return t;
    }

    private boolean verificar(TokenType tipo) {
        return atual().type() == tipo;
    }

    /** Consome o token se for do tipo dado. */
    private boolean aceitar(TokenType tipo) {
        if (verificar(tipo)) {
            avancar();
            return true;
        }
        return false;
    }

    /** Exige um token do tipo dado; senão, erro "esperado <descricao>". */
    private Token consumir(TokenType tipo, String descricao) {
        if (!verificar(tipo)) {
            throw esperado(descricao);
        }
        return avancar();
    }

    /** Falta de ";" aponta para logo depois do token anterior (fim da linha onde ele faltou). */
    private void consumirPontoEVirgula() {
        if (aceitar(SEMICOLON)) {
            return;
        }
        Token anterior = tokens.get(pos - 1);
        throw new CompileError(CompileError.Fase.SINTATICO,
                anterior.linha(), anterior.coluna() + anterior.lexema().length(),
                "esperado ';', encontrado " + descrever(atual()));
    }

    private CompileError esperado(String oque) {
        return erroNoAtual("esperado " + oque + ", encontrado " + descrever(atual()));
    }

    private CompileError erroNoAtual(String mensagem) {
        Token t = atual();
        return new CompileError(CompileError.Fase.SINTATICO, t.linha(), t.coluna(), mensagem);
    }

    /** Como o token aparece nas mensagens: 'print', identificador 'x', literal 42, fim do arquivo. */
    private static String descrever(Token t) {
        return switch (t.type()) {
            case EOF -> "fim do arquivo";
            case ID -> "identificador '" + t.lexema() + "'";
            case INT_LIT, FLOAT_LIT, CHAR_LIT, STRING_LIT -> "literal " + t.lexema();
            default -> "'" + t.lexema() + "'";
        };
    }
}
