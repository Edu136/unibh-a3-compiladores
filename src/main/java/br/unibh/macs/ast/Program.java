package br.unibh.macs.ast;

import java.util.List;

/** Raiz da árvore: funções e comandos do nível global, na ordem do arquivo. */
public record Program(List<Item> itens) {
}
