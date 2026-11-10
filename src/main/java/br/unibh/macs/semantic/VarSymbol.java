package br.unibh.macs.semantic;

import br.unibh.macs.ast.Type;

/**
 * Variável declarada (ou parâmetro).
 *
 * @param global true se declarada fora de funções
 * @param slot   índice único dentro da unidade (cada função, ou o programa global), na ordem
 *               de declaração; parâmetros primeiro. O gerador de código decide como mapear
 *               o slot para memória.
 */
public record VarSymbol(String nome, Type tipo, boolean global, int slot) {
}
