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

    // ---------- literais ----------

    @Test
    @DisplayName("literais int, float, char e string com seus valores")
    void literais() {
        List<Token> t = tokens("0 2147483647 3.14 0.5 'a' \"olá, mundo\"");
        assertEquals(List.of(INT_LIT, INT_LIT, FLOAT_LIT, FLOAT_LIT, CHAR_LIT, STRING_LIT, EOF),
                t.stream().map(Token::type).toList());
        assertEquals(0, t.get(0).valor());
        assertEquals(2147483647, t.get(1).valor());
        assertEquals(3.14, t.get(2).valor());
        assertEquals(0.5, t.get(3).valor());
        assertEquals('a', t.get(4).valor());
        assertEquals("olá, mundo", t.get(5).valor());
        assertEquals("\"olá, mundo\"", t.get(5).lexema());
    }

    @Test
    @DisplayName("escapes em string e char")
    void escapes() {
        List<Token> t = tokens("\"a\\nb\\t\\\\\\\"\" '\\'' '\\0'");
        assertEquals("a\nb\t\\\"", t.get(0).valor());
        assertEquals('\'', t.get(1).valor());
        assertEquals('\0', t.get(2).valor());
    }

    @Test
    @DisplayName("número colado em letras gera dois tokens")
    void numeroSeguidoDeLetras() {
        assertEquals(List.of(INT_LIT, ID, EOF), tipos("1abc"));
    }

    @Test
    @DisplayName("letra acentuada dentro de string conta uma coluna")
    void acentoEmString() {
        assertToken(tokens("\"ção\" x").get(1), ID, "x", 1, 7);
    }

    @Test
    void stringNaoTerminada() {
        assertErro("\"abc", 1, 1, "string não terminada");
    }

    @Test
    void stringNaoAtravessaLinha() {
        assertErro("\"ab\nc\"", 1, 1, "string não terminada");
    }

    @Test
    void charNaoTerminado() {
        assertErro("'a", 1, 1, "char não terminado");
    }

    @Test
    void charVazio() {
        assertErro("''", 1, 1, "char vazio");
    }

    @Test
    void charComMaisDeUmCaractere() {
        assertErro("'ab'", 1, 1, "char com mais de um caractere");
    }

    @Test
    void escapeDesconhecido() {
        assertErro("\"a\\qb\"", 1, 3, "escape desconhecido '\\q'");
    }

    @Test
    void inteiroForaDoIntervalo() {
        assertErro("2147483648", 1, 1, "inteiro fora do intervalo: 2147483648 (máximo 2147483647)");
    }

    @Test
    void floatSemDigitoAposPonto() {
        assertErro("3.", 1, 1, "número malformado: esperado dígito após '.'");
    }

    @Test
    void floatComecandoComPonto() {
        assertErro(".5", 1, 1, "caractere inesperado '.'");
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
