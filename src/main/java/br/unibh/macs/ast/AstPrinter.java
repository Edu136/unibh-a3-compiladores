package br.unibh.macs.ast;

import java.util.stream.Collectors;

/** Imprime a árvore sintática indentada (2 espaços por nível), um nó por linha. */
public final class AstPrinter {

    private final StringBuilder sb = new StringBuilder();

    private AstPrinter() {
    }

    public static String imprimir(Program programa) {
        AstPrinter p = new AstPrinter();
        p.linha(0, "Program");
        for (Item item : programa.itens()) {
            p.item(1, item);
        }
        return p.sb.toString();
    }

    private void item(int nivel, Item item) {
        switch (item) {
            case FuncDecl f -> {
                String params = f.params().stream()
                        .map(p -> p.nome() + ": " + p.tipo().nome())
                        .collect(Collectors.joining(", "));
                linha(nivel, "FuncDecl " + f.nome() + "(" + params + "): " + f.retorno().nome());
                stmt(nivel + 1, f.corpo());
            }
            case Stmt s -> stmt(nivel, s);
        }
    }

    private void stmt(int nivel, Stmt stmt) {
        switch (stmt) {
            case Stmt.VarDecl d -> {
                linha(nivel, "VarDecl " + d.nome() + ": " + d.tipo().nome());
                if (d.init() != null) {
                    expr(nivel + 1, d.init());
                }
            }
            case Stmt.Assign a -> {
                linha(nivel, "Assign " + a.nome());
                expr(nivel + 1, a.valor());
            }
            case Stmt.CallStmt c -> {
                linha(nivel, "CallStmt");
                expr(nivel + 1, c.call());
            }
            case Stmt.Print p -> {
                linha(nivel, "Print");
                expr(nivel + 1, p.expr());
            }
            case Stmt.Input i -> linha(nivel, "Input " + i.nome());
            case Stmt.If i -> {
                linha(nivel, "If");
                expr(nivel + 1, i.cond());
                stmt(nivel + 1, i.entao());
                if (i.senao() != null) {
                    linha(nivel + 1, "Else");
                    stmt(nivel + 2, i.senao());
                }
            }
            case Stmt.While w -> {
                linha(nivel, "While");
                expr(nivel + 1, w.cond());
                stmt(nivel + 1, w.corpo());
            }
            case Stmt.For f -> {
                linha(nivel, "For");
                if (f.init() != null) {
                    linha(nivel + 1, "Init");
                    stmt(nivel + 2, f.init());
                }
                if (f.cond() != null) {
                    linha(nivel + 1, "Cond");
                    expr(nivel + 2, f.cond());
                }
                if (f.passo() != null) {
                    linha(nivel + 1, "Passo");
                    stmt(nivel + 2, f.passo());
                }
                stmt(nivel + 1, f.corpo());
            }
            case Stmt.Return r -> {
                linha(nivel, "Return");
                expr(nivel + 1, r.expr());
            }
            case Stmt.Block b -> {
                linha(nivel, "Block");
                for (Stmt s : b.stmts()) {
                    stmt(nivel + 1, s);
                }
            }
        }
    }

    private void expr(int nivel, Expr expr) {
        switch (expr) {
            case Expr.IntLit i -> linha(nivel, "IntLit " + i.valor());
            case Expr.FloatLit f -> linha(nivel, "FloatLit " + f.valor());
            case Expr.CharLit c -> linha(nivel, "CharLit '" + escapar(String.valueOf(c.valor()), '\'') + "'");
            case Expr.StringLit s -> linha(nivel, "StringLit \"" + escapar(s.valor(), '"') + "\"");
            case Expr.BoolLit b -> linha(nivel, "BoolLit " + b.valor());
            case Expr.Var v -> linha(nivel, "Var " + v.nome());
            case Expr.Unary u -> {
                linha(nivel, "Unary " + u.op().simbolo());
                expr(nivel + 1, u.operando());
            }
            case Expr.Binary b -> {
                linha(nivel, "Binary " + b.op().simbolo());
                expr(nivel + 1, b.esq());
                expr(nivel + 1, b.dir());
            }
            case Expr.Call c -> {
                linha(nivel, "Call " + c.nome());
                for (Expr arg : c.args()) {
                    expr(nivel + 1, arg);
                }
            }
        }
    }

    /** Reescreve caracteres especiais como no código-fonte (\n, \t, \\, \0 e a aspa delimitadora). */
    private static String escapar(String texto, char aspa) {
        StringBuilder r = new StringBuilder();
        for (char c : texto.toCharArray()) {
            switch (c) {
                case '\n' -> r.append("\\n");
                case '\t' -> r.append("\\t");
                case '\\' -> r.append("\\\\");
                case '\0' -> r.append("\\0");
                default -> {
                    if (c == aspa) {
                        r.append('\\');
                    }
                    r.append(c);
                }
            }
        }
        return r.toString();
    }

    private void linha(int nivel, String texto) {
        sb.append("  ".repeat(nivel)).append(texto).append('\n');
    }
}
