package br.unibh.macs.semantic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tabela de símbolos de variáveis: pilha de escopos (o índice 0 é o global).
 *
 * <p>Ao entrar numa função é marcada uma <b>barreira</b>: a busca para nela, porque as funções
 * não acessam variáveis globais.
 */
final class SymbolTable {

    private final List<Map<String, VarSymbol>> escopos = new ArrayList<>();
    private int barreira = 0; // índice do escopo mais externo visível

    void abrir() {
        escopos.add(new HashMap<>());
    }

    void fechar() {
        escopos.remove(escopos.size() - 1);
    }

    /** Abre o escopo de uma função; os escopos de fora deixam de ser visíveis. */
    void entrarFuncao() {
        barreira = escopos.size();
        abrir();
    }

    void sairFuncao() {
        fechar();
        barreira = 0;
    }

    void declarar(VarSymbol simbolo) {
        escopos.get(escopos.size() - 1).put(simbolo.nome(), simbolo);
    }

    boolean declaradaNoEscopoAtual(String nome) {
        return escopos.get(escopos.size() - 1).containsKey(nome);
    }

    /** Procura do escopo mais interno até a barreira. */
    VarSymbol buscar(String nome) {
        for (int i = escopos.size() - 1; i >= barreira; i--) {
            VarSymbol s = escopos.get(i).get(nome);
            if (s != null) {
                return s;
            }
        }
        return null;
    }

    /** True se o nome existe atrás da barreira (usado só para enriquecer a mensagem de erro). */
    boolean existeForaDaFuncao(String nome) {
        for (int i = barreira - 1; i >= 0; i--) {
            if (escopos.get(i).containsKey(nome)) {
                return true;
            }
        }
        return false;
    }
}
