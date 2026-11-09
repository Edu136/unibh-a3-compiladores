package br.unibh.macs.ast;

import java.util.List;

/** {@code func nome(p: tipo, ...): tipo { ... }} */
public record FuncDecl(String nome, List<Param> params, Type retorno, Stmt.Block corpo, int linha, int coluna)
        implements Item {

    /** Parâmetro de função: {@code nome: tipo}. */
    public record Param(String nome, Type tipo, int linha, int coluna) {
    }
}
