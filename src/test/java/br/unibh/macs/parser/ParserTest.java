package br.unibh.macs.parser;

import static org.junit.jupiter.api.Assertions.*;

import br.unibh.macs.ast.AstPrinter;
import br.unibh.macs.ast.Expr;
import br.unibh.macs.ast.Program;
import br.unibh.macs.ast.Stmt;
import br.unibh.macs.erro.CompileError;
import br.unibh.macs.lexer.Lexer;
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

    @Test
    @DisplayName("programa vazio ou só com comentário")
    void programaVazio() {
        assertArvore("", "Program");
        assertArvore("// só comentário", "Program");
    }
}
