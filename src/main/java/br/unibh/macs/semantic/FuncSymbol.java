package br.unibh.macs.semantic;

import br.unibh.macs.ast.FuncDecl;
import br.unibh.macs.ast.Type;
import java.util.List;

/** Assinatura de uma função declarada. */
public record FuncSymbol(String nome, List<Type> params, Type retorno, FuncDecl decl) {
}
