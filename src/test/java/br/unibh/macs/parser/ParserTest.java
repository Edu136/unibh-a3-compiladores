package br.unibh.macs.parser;

import static org.junit.jupiter.api.Assertions.*;

import br.unibh.macs.ast.AstPrinter;
import br.unibh.macs.ast.Expr;
import br.unibh.macs.ast.Program;
import br.unibh.macs.ast.Stmt;
import br.unibh.macs.erro.CompileError;
import br.unibh.macs.lexer.Lexer;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParserTest {

    // ---------- utilitários ----------

    private static Program analisar(String fonte) {
        return Parser.analisar(Lexer.tokenizar(fonte));
    }

    private static String arvore(String fonte) {
        return AstPrinter.imprimir(analisar(fonte));
    }

    /** Compara a árvore impressa com as linhas esperadas. */
    private static void assertArvore(String fonte, String... linhas) {
        assertEquals(String.join("\n", linhas) + "\n", arvore(fonte));
    }

    private static void assertErro(String fonte, int linha, int coluna, String mensagem) {
        CompileError e = assertThrows(CompileError.class, () -> analisar(fonte));
        assertAll(
                () -> assertEquals(CompileError.Fase.SINTATICO, e.getFase()),
                () -> assertEquals(mensagem, e.getMessage()),
                () -> assertEquals(linha, e.getLinha(), "linha"),
                () -> assertEquals(coluna, e.getColuna(), "coluna"));
    }

    // ---------- comandos simples ----------

    @Test
    @DisplayName("declaração com inicializador")
    void declaracaoComInicializador() {
        assertArvore("var x: int = 42;",
                "Program",
                "  VarDecl x: int",
                "    IntLit 42");
    }

    @Test
    @DisplayName("declaração sem inicializador")
    void declaracaoSemInicializador() {
        assertArvore("var nome: string;",
                "Program",
                "  VarDecl nome: string");
    }

    @Test
    @DisplayName("atribuição e chamada como comando")
    void atribuicaoEChamada() {
        assertArvore("x = 1; f(a, 2);",
                "Program",
                "  Assign x",
                "    IntLit 1",
                "  CallStmt",
                "    Call f",
                "      Var a",
                "      IntLit 2");
    }

    @Test
    @DisplayName("print e input")
    void printEInput() {
        assertArvore("print(\"oi\"); input(n);",
                "Program",
                "  Print",
                "    StringLit \"oi\"",
                "  Input n");
    }

    @Test
    @DisplayName("blocos aninhados e vazios")
    void blocos() {
        assertArvore("{ { } var x: char = 'a'; }",
                "Program",
                "  Block",
                "    Block",
                "    VarDecl x: char",
                "      CharLit 'a'");
    }

    // ---------- controle de fluxo e funções ----------

    @Test
    @DisplayName("if / else if / else")
    void ifElseIfElse() {
        assertArvore("if (a) { x = 1; } else if (b) { x = 2; } else { x = 3; }",
                "Program",
                "  If",
                "    Var a",
                "    Block",
                "      Assign x",
                "        IntLit 1",
                "    Else",
                "      If",
                "        Var b",
                "        Block",
                "          Assign x",
                "            IntLit 2",
                "        Else",
                "          Block",
                "            Assign x",
                "              IntLit 3");
    }

    @Test
    void whileSimples() {
        assertArvore("while (i < 10) { i = i + 1; }",
                "Program",
                "  While",
                "    Binary <",
                "      Var i",
                "      IntLit 10",
                "    Block",
                "      Assign i",
                "        Binary +",
                "          Var i",
                "          IntLit 1");
    }

    @Test
    @DisplayName("for com as três partes")
    void forCompleto() {
        assertArvore("for (var i: int = 0; i < 3; i = i + 1) { print(i); }",
                "Program",
                "  For",
                "    Init",
                "      VarDecl i: int",
                "        IntLit 0",
                "    Cond",
                "      Binary <",
                "        Var i",
                "        IntLit 3",
                "    Passo",
                "      Assign i",
                "        Binary +",
                "          Var i",
                "          IntLit 1",
                "    Block",
                "      Print",
                "        Var i");
    }

    @Test
    @DisplayName("for com partes vazias")
    void forComPartesVazias() {
        assertArvore("for (;;) {}",
                "Program",
                "  For",
                "    Block");
        assertArvore("for (i = 0; ; ) {}",
                "Program",
                "  For",
                "    Init",
                "      Assign i",
                "        IntLit 0",
                "    Block");
    }

    @Test
    @DisplayName("função com parâmetros, return e chamada recursiva")
    void funcaoComParametros() {
        assertArvore("func f(n: int, x: float): int { return f(n - 1, x); }",
                "Program",
                "  FuncDecl f(n: int, x: float): int",
                "    Block",
                "      Return",
                "        Call f",
                "          Binary -",
                "            Var n",
                "            IntLit 1",
                "          Var x");
    }

    @Test
    @DisplayName("função sem parâmetros e itens intercalados no nível global")
    void itensIntercalados() {
        assertArvore("print(1); func g(): bool { return true; } print(g());",
                "Program",
                "  Print",
                "    IntLit 1",
                "  FuncDecl g(): bool",
                "    Block",
                "      Return",
                "        BoolLit true",
                "  Print",
                "    Call g");
    }

    @Test
    @DisplayName("return no nível global é aceito pelo sintático")
    void returnGlobal() {
        assertArvore("return 0;",
                "Program",
                "  Return",
                "    IntLit 0");
    }

    // ---------- expressões ----------

    @Test
    @DisplayName("multiplicação tem precedência sobre soma")
    void precedenciaMultiplicacao() {
        assertArvore("print(1 + 2 * 3);",
                "Program",
                "  Print",
                "    Binary +",
                "      IntLit 1",
                "      Binary *",
                "        IntLit 2",
                "        IntLit 3");
    }

    @Test
    @DisplayName("operadores binários associam à esquerda")
    void associatividadeEsquerda() {
        assertArvore("print(a - b - c);",
                "Program",
                "  Print",
                "    Binary -",
                "      Binary -",
                "        Var a",
                "        Var b",
                "      Var c");
    }

    @Test
    @DisplayName("cadeia completa de precedência")
    void cadeiaDePrecedencia() {
        assertArvore("print(a || b && c == d < e + f * -g);",
                "Program",
                "  Print",
                "    Binary ||",
                "      Var a",
                "      Binary &&",
                "        Var b",
                "        Binary ==",
                "          Var c",
                "          Binary <",
                "            Var d",
                "            Binary +",
                "              Var e",
                "              Binary *",
                "                Var f",
                "                Unary -",
                "                  Var g");
    }

    @Test
    @DisplayName("parênteses mudam a precedência")
    void parenteses() {
        assertArvore("print((1 + 2) * 3);",
                "Program",
                "  Print",
                "    Binary *",
                "      Binary +",
                "        IntLit 1",
                "        IntLit 2",
                "      IntLit 3");
    }

    @Test
    @DisplayName("unários podem se repetir")
    void unariosRepetidos() {
        assertArvore("print(!!x); print(- -y);",
                "Program",
                "  Print",
                "    Unary !",
                "      Unary !",
                "        Var x",
                "  Print",
                "    Unary -",
                "      Unary -",
                "        Var y");
    }

    @Test
    @DisplayName("chamadas dentro de expressões, com e sem argumentos")
    void chamadasEmExpressoes() {
        assertArvore("print(f() + g(h(1), 2 + 3));",
                "Program",
                "  Print",
                "    Binary +",
                "      Call f",
                "      Call g",
                "        Call h",
                "          IntLit 1",
                "        Binary +",
                "          IntLit 2",
                "          IntLit 3");
    }

    @Test
    @DisplayName("todos os tipos de literal")
    void literais() {
        assertArvore("print(3.14); print('\\n'); print(\"a\\tb\"); print(false);",
                "Program",
                "  Print",
                "    FloatLit 3.14",
                "  Print",
                "    CharLit '\\n'",
                "  Print",
                "    StringLit \"a\\tb\"",
                "  Print",
                "    BoolLit false");
    }

    @Test
    @DisplayName("nós guardam a posição: Binary fica no operador")
    void posicoes() {
        Stmt.VarDecl d = (Stmt.VarDecl) analisar("var x: int = a + 1;").itens().get(0);
        Expr.Binary soma = (Expr.Binary) d.init();
        assertAll(
                () -> assertEquals(1, d.coluna()),
                () -> assertEquals(16, soma.coluna()),
                () -> assertEquals(14, soma.esq().coluna()),
                () -> assertNull(soma.getType(), "tipo só é preenchido pelo semântico"));
    }

    @Test
    @DisplayName("concatenação associa à esquerda")
    void concatenacao() {
        assertArvore("print(\"a\" + 1 + 2);",
                "Program",
                "  Print",
                "    Binary +",
                "      Binary +",
                "        StringLit \"a\"",
                "        IntLit 1",
                "      IntLit 2");
    }

    // ---------- atalhos de atribuição (STORY-005) ----------

    @Test
    @DisplayName("x++ vira x = x + 1")
    void incremento() {
        assertArvore("x++;",
                "Program",
                "  Assign x",
                "    Binary +",
                "      Var x",
                "      IntLit 1");
    }

    @Test
    @DisplayName("x-- vira x = x - 1")
    void decremento() {
        assertArvore("x--;",
                "Program",
                "  Assign x",
                "    Binary -",
                "      Var x",
                "      IntLit 1");
    }

    @Test
    @DisplayName("x += e vira x = x + (e)")
    void maisIgualComExpressao() {
        assertArvore("x += y * 2;",
                "Program",
                "  Assign x",
                "    Binary +",
                "      Var x",
                "      Binary *",
                "        Var y",
                "        IntLit 2");
    }

    @Test
    @DisplayName("o lado direito do atalho fica inteiro (\"entre parênteses\")")
    void ladoDireitoDoAtalhoFicaAgrupado() {
        assertArvore("x -= 3 - 1;",
                "Program",
                "  Assign x",
                "    Binary -",
                "      Var x",
                "      Binary -",
                "        IntLit 3",
                "        IntLit 1");
    }

    @Test
    @DisplayName("*=, /= e %=")
    void vezesDivididoERestoIgual() {
        assertArvore("a *= 2; b /= 3; c %= 4;",
                "Program",
                "  Assign a",
                "    Binary *",
                "      Var a",
                "      IntLit 2",
                "  Assign b",
                "    Binary /",
                "      Var b",
                "      IntLit 3",
                "  Assign c",
                "    Binary %",
                "      Var c",
                "      IntLit 4");
    }

    @Test
    @DisplayName("atalho no passo do for")
    void atalhoNoPassoDoFor() {
        assertArvore("for (var i: int = 0; i < 3; i++) { print(i); }",
                "Program",
                "  For",
                "    Init",
                "      VarDecl i: int",
                "        IntLit 0",
                "    Cond",
                "      Binary <",
                "        Var i",
                "        IntLit 3",
                "    Passo",
                "      Assign i",
                "        Binary +",
                "          Var i",
                "          IntLit 1",
                "    Block",
                "      Print",
                "        Var i");
    }

    @Test
    @DisplayName("atalho no início e no passo do for")
    void atalhoNoInicioDoFor() {
        assertArvore("for (i += 2; i < 9; i--) { }",
                "Program",
                "  For",
                "    Init",
                "      Assign i",
                "        Binary +",
                "          Var i",
                "          IntLit 2",
                "    Cond",
                "      Binary <",
                "        Var i",
                "        IntLit 9",
                "    Passo",
                "      Assign i",
                "        Binary -",
                "          Var i",
                "          IntLit 1",
                "    Block");
    }

    @Test
    @DisplayName("posições da árvore traduzida: Assign e Var no nome, Binary e IntLit no operador")
    void posicoesDoAtalho() {
        Stmt.Assign a = (Stmt.Assign) analisar("  conta++;").itens().get(0);
        Expr.Binary soma = (Expr.Binary) a.valor();
        assertAll(
                () -> assertEquals(1, a.linha()),
                () -> assertEquals(3, a.coluna()),
                () -> assertEquals(1, soma.linha()),
                () -> assertEquals(8, soma.coluna()),
                () -> assertEquals(1, soma.esq().linha()),
                () -> assertEquals(3, soma.esq().coluna()),
                () -> assertEquals(1, soma.dir().linha()),
                () -> assertEquals(8, soma.dir().coluna()));
    }

    @Test
    @DisplayName("++ pré-fixado não existe")
    void incrementoPrefixado() {
        assertErro("++x;", 1, 1, "esperado comando, encontrado '++'");
    }

    @Test
    @DisplayName("atalho não é expressão")
    void atalhoDentroDeExpressao() {
        assertErro("y = x++;", 1, 6, "esperado ';', encontrado '++'");
        assertErro("print(x++);", 1, 8, "esperado ')', encontrado '++'");
    }

    @Test
    @DisplayName("atalho sem lado direito, e '--' colado entre números")
    void atalhoSemLadoDireito() {
        assertErro("x += ;", 1, 6, "esperado expressão, encontrado ';'");
        assertErro("print(5--3);", 1, 8, "esperado ')', encontrado '--'");
    }

    // ---------- programa completo ----------

    @Test
    @DisplayName("programa do fatorial gera a árvore esperada")
    void programaFatorial() throws Exception {
        String esperado = recurso("fatorial.ast").replace("\r\n", "\n");
        assertEquals(esperado, arvore(recurso("fatorial.macs")));
    }

    private static String recurso(String nome) throws Exception {
        try (InputStream in = ParserTest.class.getResourceAsStream("/" + nome)) {
            assertNotNull(in, "recurso de teste não encontrado: " + nome);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ---------- erros sintáticos ----------

    @Test
    @DisplayName("falta de ';' aponta para o fim da linha onde ele faltou")
    void faltaPontoEVirgula() {
        assertErro("var x: int = 1\nprint(x);", 1, 15, "esperado ';', encontrado 'print'");
    }

    @Test
    void faltaFechaParenteses() {
        assertErro("print(x;", 1, 8, "esperado ')', encontrado ';'");
    }

    @Test
    void relacionalEncadeado() {
        assertErro("var b: bool = a < b < c;", 1, 21,
                "operador relacional não pode ser encadeado (use && para combinar comparações)");
    }

    @Test
    void expressaoSoltaComoComando() {
        assertErro("x + 1;", 1, 3, "esperado '=' ou '(' após identificador, encontrado '+'");
    }

    @Test
    void funcaoDentroDeBloco() {
        assertErro("{ func f(): int { return 1; } }", 1, 3, "função só pode ser declarada no nível global");
    }

    @Test
    void tipoVoid() {
        assertErro("func f(): void {}", 1, 11, "esperado tipo, encontrado identificador 'void'");
    }

    @Test
    void cast() {
        assertErro("var x: int = (int) y;", 1, 15, "esperado expressão, encontrado 'int'");
    }

    @Test
    void blocoNaoFechado() {
        assertErro("if (x) {", 1, 9, "esperado '}', encontrado fim do arquivo");
    }

    @Test
    void elseSemBlocoNemIf() {
        assertErro("if (x) {} else print(1);", 1, 16, "esperado '{' ou 'if' após 'else', encontrado 'print'");
    }

    @Test
    void returnSemExpressao() {
        assertErro("return;", 1, 7, "esperado expressão, encontrado ';'");
    }

    @Test
    void inputComExpressao() {
        assertErro("input(x + 1);", 1, 9, "esperado ')', encontrado '+'");
    }

    @Test
    void whileSemParenteses() {
        assertErro("while x {}", 1, 7, "esperado '(', encontrado identificador 'x'");
    }

    @Test
    void comandoInvalido() {
        assertErro(") ;", 1, 1, "esperado comando, encontrado ')'");
    }

    @Test
    void nomeDeVariavelInvalido() {
        assertErro("var 1x: int;", 1, 5, "esperado identificador, encontrado literal 1");
    }

    @Test
    void elseSemIf() {
        assertErro("else { }", 1, 1, "esperado comando, encontrado 'else'");
    }

    @Test
    @DisplayName("programa vazio ou só com comentário")
    void programaVazio() {
        assertArvore("", "Program");
        assertArvore("// só comentário", "Program");
    }
}
