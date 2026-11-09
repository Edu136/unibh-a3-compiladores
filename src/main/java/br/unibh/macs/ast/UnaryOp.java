package br.unibh.macs.ast;

/** Operadores unários: negação lógica e menos unário. */
public enum UnaryOp {
    NOT("!"),
    NEG("-");

    private final String simbolo;

    UnaryOp(String simbolo) {
        this.simbolo = simbolo;
    }

    public String simbolo() {
        return simbolo;
    }
}
