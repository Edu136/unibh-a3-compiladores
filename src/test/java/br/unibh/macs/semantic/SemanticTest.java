package br.unibh.macs.semantic;

import static org.junit.jupiter.api.Assertions.*;

import br.unibh.macs.ast.AstPrinter;
import br.unibh.macs.ast.Expr;
import br.unibh.macs.ast.Stmt;
import br.unibh.macs.erro.CompileError;
import br.unibh.macs.lexer.Lexer;
import br.unibh.macs.parser.Parser;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SemanticTest {

    // ---------- utilitários ----------

    private static Resultado analisar(String fonte) {
        return SemanticAnalyzer.analisar(Parser.analisar(Lexer.tokenizar(fonte)));
    }

    private static List<String> erros(Resultado r) {
        r.erros().forEach(e -> assertEquals(CompileError.Fase.SEMANTICO, e.getFase()));
        return r.erros().stream().map(e -> e.getLinha() + ":" + e.getColuna() + " " + e.getMessage()).toList();
    }

    /** Confere a lista completa de erros, na ordem ("linha:coluna mensagem"). */
    private static void assertErros(String fonte, String... esperados) {
        assertEquals(List.of(esperados), erros(analisar(fonte)));
    }

    private static void assertSemErros(String fonte) {
        assertErros(fonte);
    }

    /** Árvore anotada; falha se houver erro semântico. */
    private static String arvore(String fonte) {
        Resultado r = analisar(fonte);
        assertEquals(List.of(), erros(r));
        return AstPrinter.imprimir(r.programa());
    }

    private static void assertContemLinhas(String arvore, String... linhas) {
        String trecho = String.join("\n", linhas);
        assertTrue(arvore.contains(trecho), "esperado o trecho:\n" + trecho + "\n\nna árvore:\n" + arvore);
    }

    // ---------- declarações, nomes e tipos ----------

    @Test
    @DisplayName("declaração e uso de variável com tipos anotados")
    void declaracaoEUso() {
        assertEquals(String.join("\n",
                        "Program",
                        "  VarDecl x: int",
                        "    IntLit 1 : int",
                        "  Print",
                        "    Binary + : int",
                        "      Var x : int",
                        "      IntLit 2 : int",
                        ""),
                arvore("var x: int = 1; print(x + 2);"));
    }

    @Test
    @DisplayName("promoção int→float insere Convert")
    void promocao() {
        assertContemLinhas(arvore("var f: float = 1; print(1 + 2.5);"),
                "  VarDecl f: float",
                "    Convert float",
                "      IntLit 1 : int",
                "  Print",
                "    Binary + : float",
                "      Convert float",
                "        IntLit 1 : int",
                "      FloatLit 2.5 : float");
    }

    @Test
    @DisplayName("+ com string concatena qualquer tipo, sem Convert")
    void concatenacao() {
        String a = arvore("print(\"n = \" + 1 + 2.5 + true + 'c');");
        assertEquals(4, a.lines().filter(l -> l.contains("Binary + : string")).count(), a);
        assertFalse(a.contains("Convert"), a);
    }

    @Test
    @DisplayName("sombreamento: variável interna esconde a externa")
    void sombreamento() {
        Resultado r = analisar("var x: int = 1; { var x: string = \"a\"; print(x); } print(x);");
        assertEquals(List.of(), erros(r));
        Stmt.Block bloco = (Stmt.Block) r.programa().itens().get(1);
        Expr.Var interna = (Expr.Var) ((Stmt.Print) bloco.stmts().get(1)).expr();
        Expr.Var externa = (Expr.Var) ((Stmt.Print) r.programa().itens().get(2)).expr();

        assertEquals("string", interna.getType().nome());
        assertEquals("int", externa.getType().nome());
        VarSymbol si = r.resolucao().variavel(interna);
        VarSymbol se = r.resolucao().variavel(externa);
        assertNotEquals(si.slot(), se.slot());
        assertEquals(0, se.slot());
        assertEquals(1, si.slot());
    }

    @Test
    @DisplayName("variável do for é visível no cabeçalho e no corpo")
    void escopoDoFor() {
        assertSemErros("for (var i: int = 0; i < 3; i = i + 1) { print(i); }");
    }

    @Test
    @DisplayName("igualdade de strings, relacional de char e igualdade de bool")
    void comparacoes() {
        String a = arvore("print(\"a\" == \"b\"); print('a' < 'b'); print(true != false);");
        assertEquals(3, a.lines().filter(l -> l.contains("Binary") && l.endsWith(": bool")).count(), a);
    }

    // ---------- erros de nomes e de operadores ----------

    @Test
    void variavelNaoDeclarada() {
        assertErros("print(x);", "1:7 variável 'x' não declarada");
    }

    @Test
    @DisplayName("o inicializador não enxerga a própria variável")
    void autoReferencia() {
        assertErros("var x: int = x;", "1:14 variável 'x' não declarada");
    }

    @Test
    void declaracaoRepetida() {
        assertErros("var x: int; var x: int;", "1:13 variável 'x' já declarada neste escopo");
    }

    @Test
    void inputDeVariavelNaoDeclarada() {
        // posição do comando input (o nó Input não guarda a posição do nome)
        assertErros("input(z);", "1:1 variável 'z' não declarada");
    }

    @Test
    @DisplayName("variável do for não existe depois do for")
    void variavelDoForForaDoFor() {
        assertErros("for (var i: int = 0; i < 1; i = i + 1) { } print(i);", "1:50 variável 'i' não declarada");
    }

    @Test
    @DisplayName("variável de bloco não existe depois do bloco")
    void variavelDeBlocoForaDoBloco() {
        assertErros("{ var y: int = 1; } print(y);", "1:27 variável 'y' não declarada");
    }

    static Stream<Arguments> operadoresInvalidos() {
        return Stream.of(
                Arguments.of("print(true + 1);", "1:12 operador '+' não se aplica a bool e int"),
                Arguments.of("print('a' + 1);", "1:11 operador '+' não se aplica a char e int"),
                Arguments.of("print(5.0 % 2);", "1:11 operador '%' não se aplica a float e int"),
                Arguments.of("print(1 && true);", "1:9 operador '&&' não se aplica a int e bool"),
                Arguments.of("print(!1);", "1:7 operador '!' não se aplica a int"),
                Arguments.of("print(-\"a\");", "1:7 operador '-' não se aplica a string"),
                Arguments.of("print(\"a\" < \"b\");", "1:11 operador '<' não se aplica a string e string"),
                Arguments.of("print(1 == \"1\");", "1:9 operador '==' não se aplica a int e string"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("operadoresInvalidos")
    void operadorInvalido(String fonte, String erro) {
        assertErros(fonte, erro);
    }

    @Test
    @DisplayName("um erro não gera outros em cascata")
    void semCascata() {
        assertErros("print((x + 1) * 2);", "1:8 variável 'x' não declarada");
    }
}
