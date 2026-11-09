package br.unibh.macs.parser;

import static org.junit.jupiter.api.Assertions.*;

import br.unibh.macs.ast.AstPrinter;
import br.unibh.macs.ast.Program;
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

    @Test
    @DisplayName("programa vazio ou só com comentário")
    void programaVazio() {
        assertArvore("", "Program");
        assertArvore("// só comentário", "Program");
    }
}
