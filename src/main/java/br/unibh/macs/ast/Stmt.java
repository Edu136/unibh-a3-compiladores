package br.unibh.macs.ast;

import java.util.List;

/** Comandos da MACSLang. Cada um guarda a posição do seu primeiro token. */
public sealed interface Stmt extends Item {

    /** {@code var nome: tipo [= init];} — init é null quando não há inicializador. */
    record VarDecl(String nome, Type tipo, Expr init, int linha, int coluna) implements Stmt {
    }

    /** {@code nome = valor;} */
    record Assign(String nome, Expr valor, int linha, int coluna) implements Stmt {
    }

    /** Chamada de função usada como comando: {@code f(x);} */
    record CallStmt(Expr.Call call, int linha, int coluna) implements Stmt {
    }

    /** {@code print(expr);} */
    record Print(Expr expr, int linha, int coluna) implements Stmt {
    }

    /** {@code input(nome);} */
    record Input(String nome, int linha, int coluna) implements Stmt {
    }

    /** {@code if (cond) {...} [else ...]} — senao é Block, If (else if) ou null. */
    record If(Expr cond, Block entao, Stmt senao, int linha, int coluna) implements Stmt {
    }

    /** {@code while (cond) {...}} */
    record While(Expr cond, Block corpo, int linha, int coluna) implements Stmt {
    }

    /**
     * {@code for (init; cond; passo) {...}} — cada parte pode ser null.
     * init é VarDecl ou Assign.
     */
    record For(Stmt init, Expr cond, Assign passo, Block corpo, int linha, int coluna) implements Stmt {
    }

    /** {@code return expr;} */
    record Return(Expr expr, int linha, int coluna) implements Stmt {
    }

    /** {@code { comandos }} */
    record Block(List<Stmt> stmts, int linha, int coluna) implements Stmt {
    }
}
