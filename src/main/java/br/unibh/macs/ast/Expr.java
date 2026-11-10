package br.unibh.macs.ast;

import java.util.List;

/**
 * Expressões da MACSLang. São classes (não records) porque o analisador semântico
 * preenche {@link #getType()} depois que a árvore é construída.
 */
public abstract sealed class Expr {

    private final int linha;
    private final int coluna;
    private Type type; // null até a análise semântica

    protected Expr(int linha, int coluna) {
        this.linha = linha;
        this.coluna = coluna;
    }

    public int linha() {
        return linha;
    }

    public int coluna() {
        return coluna;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    // ---------- literais ----------

    public static final class IntLit extends Expr {
        private final int valor;

        public IntLit(int valor, int linha, int coluna) {
            super(linha, coluna);
            this.valor = valor;
        }

        public int valor() {
            return valor;
        }
    }

    public static final class FloatLit extends Expr {
        private final double valor;

        public FloatLit(double valor, int linha, int coluna) {
            super(linha, coluna);
            this.valor = valor;
        }

        public double valor() {
            return valor;
        }
    }

    public static final class CharLit extends Expr {
        private final char valor;

        public CharLit(char valor, int linha, int coluna) {
            super(linha, coluna);
            this.valor = valor;
        }

        public char valor() {
            return valor;
        }
    }

    public static final class StringLit extends Expr {
        private final String valor;

        public StringLit(String valor, int linha, int coluna) {
            super(linha, coluna);
            this.valor = valor;
        }

        public String valor() {
            return valor;
        }
    }

    public static final class BoolLit extends Expr {
        private final boolean valor;

        public BoolLit(boolean valor, int linha, int coluna) {
            super(linha, coluna);
            this.valor = valor;
        }

        public boolean valor() {
            return valor;
        }
    }

    // ---------- nomes, operadores e chamadas ----------

    /** Uso de variável. */
    public static final class Var extends Expr {
        private final String nome;

        public Var(String nome, int linha, int coluna) {
            super(linha, coluna);
            this.nome = nome;
        }

        public String nome() {
            return nome;
        }
    }

    /** {@code !x} ou {@code -x}. Posição: a do operador. */
    public static final class Unary extends Expr {
        private final UnaryOp op;
        private final Expr operando;

        public Unary(UnaryOp op, Expr operando, int linha, int coluna) {
            super(linha, coluna);
            this.op = op;
            this.operando = operando;
        }

        public UnaryOp op() {
            return op;
        }

        public Expr operando() {
            return operando;
        }
    }

    /** {@code esq op dir}. Posição: a do operador. */
    public static final class Binary extends Expr {
        private final BinaryOp op;
        private final Expr esq;
        private final Expr dir;

        public Binary(BinaryOp op, Expr esq, Expr dir, int linha, int coluna) {
            super(linha, coluna);
            this.op = op;
            this.esq = esq;
            this.dir = dir;
        }

        public BinaryOp op() {
            return op;
        }

        public Expr esq() {
            return esq;
        }

        public Expr dir() {
            return dir;
        }
    }

    /**
     * Conversão implícita inserida pelo analisador semântico (na v1, só int → float).
     * Não existe no código-fonte: fica na posição da expressão convertida.
     */
    public static final class Convert extends Expr {
        private final Expr expr;
        private final Type alvo;

        public Convert(Expr expr, Type alvo) {
            super(expr.linha(), expr.coluna());
            this.expr = expr;
            this.alvo = alvo;
            setType(alvo);
        }

        public Expr expr() {
            return expr;
        }

        public Type alvo() {
            return alvo;
        }
    }

    /** Chamada de função {@code nome(args)}. Posição: a do nome. */
    public static final class Call extends Expr {
        private final String nome;
        private final List<Expr> args;

        public Call(String nome, List<Expr> args, int linha, int coluna) {
            super(linha, coluna);
            this.nome = nome;
            this.args = List.copyOf(args);
        }

        public String nome() {
            return nome;
        }

        public List<Expr> args() {
            return args;
        }
    }
}
