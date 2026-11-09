package br.unibh.macs.ast;

/** Operadores binários, da menor para a maior precedência. */
public enum BinaryOp {
    OR("||"),
    AND("&&"),
    EQ("=="), NE("!="),
    LT("<"), LE("<="), GT(">"), GE(">="),
    ADD("+"), SUB("-"),
    MUL("*"), DIV("/"), MOD("%");

    private final String simbolo;

    BinaryOp(String simbolo) {
        this.simbolo = simbolo;
    }

    public String simbolo() {
        return simbolo;
    }
}
