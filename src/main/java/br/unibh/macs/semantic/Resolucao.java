package br.unibh.macs.semantic;

import br.unibh.macs.ast.Expr;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Liga cada uso de nome na árvore anotada ao seu símbolo, para o gerador de código não
 * precisar refazer a análise de escopos. A chave é a identidade do nó (não o nome),
 * porque o mesmo nome pode se referir a variáveis diferentes (sombreamento).
 *
 * <p>Nós com variável: {@code Expr.Var}, {@code Stmt.VarDecl}, {@code Stmt.Assign},
 * {@code Stmt.Input} e {@code FuncDecl.Param}.
 */
public final class Resolucao {

    private final Map<Object, VarSymbol> variaveis = new IdentityHashMap<>();
    private final Map<Expr.Call, FuncSymbol> funcoes = new IdentityHashMap<>();

    void registrar(Object no, VarSymbol simbolo) {
        variaveis.put(no, simbolo);
    }

    void registrar(Expr.Call chamada, FuncSymbol funcao) {
        funcoes.put(chamada, funcao);
    }

    /** Símbolo da variável usada/declarada no nó, ou null se o nó não for de variável. */
    public VarSymbol variavel(Object no) {
        return variaveis.get(no);
    }

    /** Função chamada, ou null. */
    public FuncSymbol funcao(Expr.Call chamada) {
        return funcoes.get(chamada);
    }
}
