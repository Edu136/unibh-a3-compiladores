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

    // ---------- comandos ----------

    static Stream<Arguments> comandosInvalidos() {
        return Stream.of(
                Arguments.of("if (1) { }", "1:5 condição do if deve ser bool, encontrado int"),
                Arguments.of("while (\"s\") { }", "1:8 condição do while deve ser bool, encontrado string"),
                Arguments.of("for (; 1; ) { }", "1:8 condição do for deve ser bool, encontrado int"),
                Arguments.of("var x: int = 2.5;", "1:1 inicialização incompatível: 'x' é int, valor é float"),
                Arguments.of("var x: int; x = \"a\";", "1:13 atribuição incompatível: 'x' é int, valor é string"),
                Arguments.of("var b: bool; input(b);", "1:14 input não aceita variável bool"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("comandosInvalidos")
    void comandoInvalido(String fonte, String erro) {
        assertErros(fonte, erro);
    }

    @Test
    @DisplayName("input aceita int, float, char e string")
    void inputValido() {
        assertSemErros("var a: int; var b: float; var c: char; var d: string; input(a); input(b); input(c); input(d);");
    }

    // ---------- funções ----------

    @Test
    @DisplayName("função chamada antes de declarada, com recursão")
    void funcaoAntesDaDeclaracao() {
        String a = arvore("print(f(3)); func f(n: int): int "
                + "{ if (n <= 1) { return 1; } else { return n * f(n - 1); } }");
        assertTrue(a.contains("Call f : int"), a);
    }

    @Test
    @DisplayName("argumento e return com promoção int→float")
    void promocaoEmArgumentoEReturn() {
        assertContemLinhas(arvore("func g(x: float): float { return 1; } print(g(2));"),
                "      Return",
                "        Convert float",
                "          IntLit 1 : int",
                "  Print",
                "    Call g : float",
                "      Convert float",
                "        IntLit 2 : int");
    }

    @Test
    @DisplayName("funções e variáveis têm espaços de nomes separados")
    void espacosDeNomes() {
        assertSemErros("var f: int = 1; func f(): int { return 2; } print(f + f()); "
                + "func n(n: int): int { return n; }");
    }

    @Test
    @DisplayName("slots: globais e locais numerados por unidade, parâmetros primeiro")
    void slots() {
        Resultado r = analisar("var a: int = 1; func f(p: int): int { var q: int = p; return q; }");
        assertEquals(List.of(), erros(r));

        Stmt.VarDecl a = (Stmt.VarDecl) r.programa().itens().get(0);
        var f = (br.unibh.macs.ast.FuncDecl) r.programa().itens().get(1);
        Stmt.VarDecl q = (Stmt.VarDecl) f.corpo().stmts().get(0);

        assertEquals(new VarSymbol("a", br.unibh.macs.ast.Type.INT, true, 0), r.resolucao().variavel(a));
        VarSymbol p = r.resolucao().variavel(f.params().get(0));
        assertEquals(new VarSymbol("p", br.unibh.macs.ast.Type.INT, false, 0), p);
        assertEquals(new VarSymbol("q", br.unibh.macs.ast.Type.INT, false, 1), r.resolucao().variavel(q));
        assertSame(p, r.resolucao().variavel(q.init()), "o uso de p resolve para o mesmo símbolo do parâmetro");
    }

    @Test
    @DisplayName("a chamada fica ligada ao símbolo da função")
    void resolucaoDeChamada() {
        Resultado r = analisar("func f(): int { return 1; } print(f());");
        Expr.Call call = (Expr.Call) ((Stmt.Print) r.programa().itens().get(1)).expr();
        assertEquals("f", r.resolucao().funcao(call).nome());
    }

    static Stream<Arguments> funcoesInvalidas() {
        return Stream.of(
                Arguments.of("var g: int = 1; func f(): int { return g; }",
                        "1:40 variável 'g' não declarada (funções não acessam variáveis globais)"),
                Arguments.of("func f(): int { return 1; } func f(): int { return 2; }",
                        "1:29 função 'f' já declarada"),
                Arguments.of("func f(a: int, a: int): int { return a; }", "1:16 parâmetro 'a' repetido"),
                Arguments.of("print(g(1));", "1:7 função 'g' não declarada"),
                Arguments.of("func f(a: int): int { return a; } print(f(1, 2));",
                        "1:41 função 'f' espera 1 argumento(s), recebeu 2"),
                Arguments.of("func f(a: int): int { return a; } print(f(\"x\"));",
                        "1:43 argumento 1 de 'f': esperado int, encontrado string"),
                Arguments.of("return 1;", "1:1 return fora de função"),
                Arguments.of("func f(): int { return \"a\"; }",
                        "1:17 return incompatível: função 'f' retorna int, valor é string"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("funcoesInvalidas")
    void funcaoInvalida(String fonte, String erro) {
        assertErros(fonte, erro);
    }

    @Test
    @DisplayName("argumentos de função inexistente também são verificados")
    void argumentosDeFuncaoInexistente() {
        assertErros("print(g(x));", "1:7 função 'g' não declarada", "1:9 variável 'x' não declarada");
    }

    // ---------- todo caminho retorna ----------

    @ParameterizedTest(name = "{0}")
    @DisplayName("funções que sempre retornam")
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "func a(n: int): int { if (n > 0) { return 1; } else { return 2; } }",
            "func b(): int { { return 1; } }",
            "func c(): int { while (true) { } return 0; }",
            "func d(n: int): int { if (n > 0) { return 1; } else if (n < 0) { return 2; } else { return 3; } }"})
    void sempreRetorna(String fonte) {
        assertSemErros(fonte);
    }

    @Test
    @DisplayName("if sem else não garante retorno")
    void ifSemElse() {
        assertErros("func f(n: int): int { if (n > 0) { return 1; } }", "1:1 função 'f' pode terminar sem return");
    }

    @Test
    @DisplayName("while não garante retorno")
    void returnSoDentroDoWhile() {
        assertErros("func f(): int { while (true) { return 1; } }", "1:1 função 'f' pode terminar sem return");
    }

    // ---------- acúmulo e ordem ----------

    @Test
    @DisplayName("vários erros são reportados, em ordem")
    void variosErros() {
        assertErros("print(a); print(b);", "1:7 variável 'a' não declarada", "1:17 variável 'b' não declarada");
    }

    @Test
    @DisplayName("erros de função e do código global ficam em ordem de posição")
    void ordemEntreFuncaoEGlobal() {
        assertErros("func f(): int { return b; } print(a);",
                "1:24 variável 'b' não declarada", "1:35 variável 'a' não declarada");
    }

    @Test
    @DisplayName("um erro não gera outros em cascata")
    void semCascata() {
        assertErros("print((x + 1) * 2);", "1:8 variável 'x' não declarada");
    }
}
