package br.unibh.macs.ast;

/** Item do nível global do programa: declaração de função ou comando. */
public sealed interface Item permits FuncDecl, Stmt {

    int linha();

    int coluna();
}
