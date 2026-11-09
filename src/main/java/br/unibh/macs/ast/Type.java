package br.unibh.macs.ast;

/** Os cinco tipos da MACSLang. */
public enum Type {
    INT("int"),
    FLOAT("float"),
    CHAR("char"),
    BOOL("bool"),
    STRING("string");

    private final String nome;

    Type(String nome) {
        this.nome = nome;
    }

    /** Nome como escrito no código-fonte (ex.: "int"). */
    public String nome() {
        return nome;
    }
}
