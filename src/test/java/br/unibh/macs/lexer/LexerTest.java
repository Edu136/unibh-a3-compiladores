package br.unibh.macs.lexer;

import static br.unibh.macs.lexer.TokenType.*;
import static org.junit.jupiter.api.Assertions.*;

import br.unibh.macs.erro.CompileError;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LexerTest {

    // ---------- utilitários ----------

    private static List<Token> tokens(String fonte) {
        return Lexer.tokenizar(fonte);
    }

    private static List<TokenType> tipos(String fonte) {
        return tokens(fonte).stream().map(Token::type).toList();
    }

    private static void assertToken(Token t, TokenType tipo, String lexema, int linha, int coluna) {
        assertAll(
                () -> assertEquals(tipo, t.type(), "tipo"),
                () -> assertEquals(lexema, t.lexema(), "lexema"),
                () -> assertEquals(linha, t.linha(), "linha de " + t),
                () -> assertEquals(coluna, t.coluna(), "coluna de " + t));
    }

    private static void assertErro(String fonte, int linha, int coluna, String mensagem) {
        CompileError e = assertThrows(CompileError.class, () -> tokens(fonte));
        assertAll(
                () -> assertEquals(CompileError.Fase.LEXICO, e.getFase()),
                () -> assertEquals(mensagem, e.getMessage()),
                () -> assertEquals(linha, e.getLinha(), "linha"),
                () -> assertEquals(coluna, e.getColuna(), "coluna"));
    }

    // ---------- declarações, palavras-chave e identificadores ----------

    @Test
    @DisplayName("declaração simples gera tokens com posição")
    void declaracaoSimples() {
        List<Token> t = tokens("var x: int = 42;");
        assertEquals(8, t.size());
        assertToken(t.get(0), VAR, "var", 1, 1);
        assertToken(t.get(1), ID, "x", 1, 5);
        assertToken(t.get(2), COLON, ":", 1, 6);
        assertToken(t.get(3), INT, "int", 1, 8);
        assertToken(t.get(4), ASSIGN, "=", 1, 12);
        assertToken(t.get(5), INT_LIT, "42", 1, 14);
        assertToken(t.get(6), SEMICOLON, ";", 1, 16);
        assertToken(t.get(7), EOF, "", 1, 17);
        assertEquals(42, t.get(5).valor());
    }

    @Test
    @DisplayName("todas as palavras-chave têm tipo próprio")
    void palavrasChave() {
        assertEquals(
                List.of(VAR, FUNC, IF, ELSE, WHILE, FOR, RETURN, PRINT, INPUT, TRUE, FALSE,
                        INT, FLOAT, CHAR, BOOL, STRING, EOF),
                tipos("var func if else while for return print input true false int float char bool string"));
    }

    @Test
    @DisplayName("identificadores, inclusive parecidos com palavras-chave")
    void identificadores() {
        List<Token> t = tokens("x _tmp contador2 ifx Var");
        assertEquals(List.of(ID, ID, ID, ID, ID, EOF), t.stream().map(Token::type).toList());
        assertEquals(List.of("x", "_tmp", "contador2", "ifx", "Var"),
                t.subList(0, 5).stream().map(Token::lexema).toList());
    }

    // ---------- posições ----------

    @Test
    @DisplayName("CRLF conta como uma linha e tab como uma coluna")
    void posicoesComCrlfETab() {
        List<Token> t = tokens("a\r\n\tb");
        assertToken(t.get(0), ID, "a", 1, 1);
        assertToken(t.get(1), ID, "b", 2, 2);
    }

    @Test
    @DisplayName("entrada vazia ou só com comentário gera apenas EOF")
    void entradaVazia() {
        assertToken(tokens("").get(0), EOF, "", 1, 1);
        assertEquals(1, tokens("   \n\t ").size());
    }
}
