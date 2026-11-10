package br.unibh.macs.semantic;

import br.unibh.macs.ast.Program;
import br.unibh.macs.erro.CompileError;
import java.util.List;

/**
 * Saída do analisador semântico.
 *
 * @param programa  árvore anotada (tipos e conversões); só deve ir para o gerador de código
 *                  se não houver erros
 * @param resolucao ligação dos nomes aos símbolos
 * @param erros     todos os erros semânticos, ordenados por posição
 */
public record Resultado(Program programa, Resolucao resolucao, List<CompileError> erros) {

    public boolean temErros() {
        return !erros.isEmpty();
    }
}
