package br.unibh.macs.lexer;

import br.unibh.macs.erro.CompileError;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Analisador léxico da MACSLang: transforma o código-fonte em uma lista de tokens.
 * Para no primeiro erro, lançando {@link CompileError}.
 */
public final class Lexer {

    private static final Map<String, TokenType> PALAVRAS_CHAVE = Map.ofEntries(
            Map.entry("var", TokenType.VAR),
            Map.entry("func", TokenType.FUNC),
            Map.entry("if", TokenType.IF),
            Map.entry("else", TokenType.ELSE),
            Map.entry("while", TokenType.WHILE),
            Map.entry("for", TokenType.FOR),
            Map.entry("return", TokenType.RETURN),
            Map.entry("print", TokenType.PRINT),
            Map.entry("input", TokenType.INPUT),
            Map.entry("true", TokenType.TRUE),
            Map.entry("false", TokenType.FALSE),
            Map.entry("int", TokenType.INT),
            Map.entry("float", TokenType.FLOAT),
            Map.entry("char", TokenType.CHAR),
            Map.entry("bool", TokenType.BOOL),
            Map.entry("string", TokenType.STRING));

    private final String fonte;
    private final List<Token> tokens = new ArrayList<>();

    private int pos = 0;      // índice do próximo caractere a ler
    private int linha = 1;    // posição do próximo caractere
    private int coluna = 1;

    // início do token atual
    private int inicio;
    private int linhaInicio;
    private int colunaInicio;

    private Lexer(String fonte) {
        this.fonte = fonte;
    }

    public static List<Token> tokenizar(String fonte) {
        return new Lexer(fonte).executar();
    }

    private List<Token> executar() {
        while (true) {
            pularEspacos();
            if (fim()) {
                break;
            }
            marcarInicio();
            lerToken();
        }
        tokens.add(new Token(TokenType.EOF, "", null, linha, coluna));
        return tokens;
    }

    // ---------- leitura de tokens ----------

    private void lerToken() {
        char c = avancar();
        if (ehLetra(c)) {
            lerIdentificador();
        } else if (ehDigito(c)) {
            lerNumero();
        } else {
            switch (c) {
                case '"' -> lerString();
                case '\'' -> lerChar();
                case '+' -> adicionar(TokenType.PLUS);
                case '-' -> adicionar(TokenType.MINUS);
                case '*' -> adicionar(TokenType.STAR);
                case '/' -> adicionar(TokenType.SLASH); // comentários já foram pulados
                case '%' -> adicionar(TokenType.PERCENT);
                case '(' -> adicionar(TokenType.LPAREN);
                case ')' -> adicionar(TokenType.RPAREN);
                case '{' -> adicionar(TokenType.LBRACE);
                case '}' -> adicionar(TokenType.RBRACE);
                case ',' -> adicionar(TokenType.COMMA);
                case ';' -> adicionar(TokenType.SEMICOLON);
                case ':' -> adicionar(TokenType.COLON);
                case '=' -> adicionar(seguidoDe('=') ? TokenType.EQ : TokenType.ASSIGN);
                case '!' -> adicionar(seguidoDe('=') ? TokenType.NE : TokenType.NOT);
                case '<' -> adicionar(seguidoDe('=') ? TokenType.LE : TokenType.LT);
                case '>' -> adicionar(seguidoDe('=') ? TokenType.GE : TokenType.GT);
                case '&' -> {
                    if (!seguidoDe('&')) {
                        throw erro("'&' isolado: esperado '&&'");
                    }
                    adicionar(TokenType.AND);
                }
                case '|' -> {
                    if (!seguidoDe('|')) {
                        throw erro("'|' isolado: esperado '||'");
                    }
                    adicionar(TokenType.OR);
                }
                default -> throw erro("caractere inesperado " + descrever(c));
            }
        }
    }

    private void lerIdentificador() {
        while (!fim() && (ehLetra(atual()) || ehDigito(atual()))) {
            avancar();
        }
        String texto = lexemaAtual();
        adicionar(PALAVRAS_CHAVE.getOrDefault(texto, TokenType.ID));
    }

    /** INT_LIT = D+ ; FLOAT_LIT = D+ "." D+ (sem sinal: o "-" é operador). */
    private void lerNumero() {
        pularDigitos();
        if (!fim() && atual() == '.') {
            avancar();
            if (fim() || !ehDigito(atual())) {
                throw erro("número malformado: esperado dígito após '.'");
            }
            pularDigitos();
            adicionar(TokenType.FLOAT_LIT, Double.parseDouble(lexemaAtual()));
            return;
        }
        String texto = lexemaAtual();
        try {
            adicionar(TokenType.INT_LIT, Integer.parseInt(texto));
        } catch (NumberFormatException e) {
            throw erro("inteiro fora do intervalo: " + texto + " (máximo " + Integer.MAX_VALUE + ")");
        }
    }

    private void pularDigitos() {
        while (!fim() && ehDigito(atual())) {
            avancar();
        }
    }

    /** String entre aspas duplas, na mesma linha, com escapes. */
    private void lerString() {
        StringBuilder valor = new StringBuilder();
        while (true) {
            if (fim() || ehQuebraDeLinha(atual())) {
                throw erro("string não terminada");
            }
            char c = avancar();
            if (c == '"') {
                break;
            }
            valor.append(c == '\\' ? lerEscape() : c);
        }
        adicionar(TokenType.STRING_LIT, valor.toString());
    }

    /** Char entre aspas simples: exatamente um caractere (ou um escape). */
    private void lerChar() {
        if (fim() || ehQuebraDeLinha(atual())) {
            throw erro("char não terminado");
        }
        if (atual() == '\'') {
            throw erro("char vazio");
        }
        char c = avancar();
        char valor = (c == '\\') ? lerEscape() : c;

        if (!fim() && atual() == '\'') {
            avancar();
            adicionar(TokenType.CHAR_LIT, valor);
            return;
        }
        throw erro(existeAspaSimplesNaLinha() ? "char com mais de um caractere" : "char não terminado");
    }

    /** Lê o caractere após '\' (já consumida) e devolve o valor do escape. */
    private char lerEscape() {
        int linhaBarra = linha;
        int colunaBarra = coluna - 1;
        if (fim() || ehQuebraDeLinha(atual())) {
            throw erro(tokenAtualEhString() ? "string não terminada" : "char não terminado");
        }
        char c = avancar();
        return switch (c) {
            case 'n' -> '\n';
            case 't' -> '\t';
            case '0' -> '\0';
            case '\\' -> '\\';
            case '"' -> '"';
            case '\'' -> '\'';
            default -> throw new CompileError(CompileError.Fase.LEXICO, linhaBarra, colunaBarra,
                    "escape desconhecido '\\" + c + "'");
        };
    }

    private boolean tokenAtualEhString() {
        return fonte.charAt(inicio) == '"';
    }

    private boolean existeAspaSimplesNaLinha() {
        for (int i = pos; i < fonte.length() && !ehQuebraDeLinha(fonte.charAt(i)); i++) {
            if (fonte.charAt(i) == '\'') {
                return true;
            }
        }
        return false;
    }

    // ---------- espaços e comentários ----------

    private void pularEspacos() {
        while (!fim()) {
            char c = atual();
            if (c == ' ' || c == '\t' || ehQuebraDeLinha(c)) {
                avancar();
            } else if (c == '/' && proximo() == '/') {
                while (!fim() && !ehQuebraDeLinha(atual())) {
                    avancar();
                }
            } else if (c == '/' && proximo() == '*') {
                pularComentarioDeBloco();
            } else {
                return;
            }
        }
    }

    private void pularComentarioDeBloco() {
        marcarInicio(); // para o erro apontar o "/*"
        avancar();
        avancar();
        while (!fim()) {
            if (atual() == '*' && proximo() == '/') {
                avancar();
                avancar();
                return;
            }
            avancar();
        }
        throw erro("comentário de bloco não terminado");
    }

    // ---------- navegação ----------

    private boolean fim() {
        return pos >= fonte.length();
    }

    private char atual() {
        return fonte.charAt(pos);
    }

    /** Caractere depois do atual, ou '\0' no fim. */
    private char proximo() {
        return pos + 1 < fonte.length() ? fonte.charAt(pos + 1) : '\0';
    }

    /** Se o próximo caractere for o esperado, consome e devolve true. */
    private boolean seguidoDe(char esperado) {
        if (!fim() && atual() == esperado) {
            avancar();
            return true;
        }
        return false;
    }

    /** Consome um caractere atualizando linha e coluna. "\r\n" conta como uma única quebra. */
    private char avancar() {
        char c = fonte.charAt(pos++);
        if (c == '\n') {
            linha++;
            coluna = 1;
        } else if (c == '\r') {
            if (fim() || atual() != '\n') {
                linha++;
                coluna = 1;
            }
        } else {
            coluna++;
        }
        return c;
    }

    private void marcarInicio() {
        inicio = pos;
        linhaInicio = linha;
        colunaInicio = coluna;
    }

    private String lexemaAtual() {
        return fonte.substring(inicio, pos);
    }

    private void adicionar(TokenType tipo) {
        adicionar(tipo, null);
    }

    private void adicionar(TokenType tipo, Object valor) {
        tokens.add(new Token(tipo, lexemaAtual(), valor, linhaInicio, colunaInicio));
    }

    private CompileError erro(String mensagem) {
        return new CompileError(CompileError.Fase.LEXICO, linhaInicio, colunaInicio, mensagem);
    }

    private static boolean ehLetra(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    /**
     * Mostra o caractere entre aspas, ou pelo código Unicode quando ele não aparece na tela
     * (ex.: espaço não separável U+00A0, comum em código copiado de PDF ou Word).
     */
    private static String descrever(char c) {
        int tipo = Character.getType(c);
        boolean invisivel = Character.isWhitespace(c) || Character.isISOControl(c)
                || tipo == Character.SPACE_SEPARATOR || tipo == Character.FORMAT;
        return invisivel ? String.format("U+%04X (invisível)", (int) c) : "'" + c + "'";
    }

    private static boolean ehQuebraDeLinha(char c) {
        return c == '\n' || c == '\r';
    }

    private static boolean ehDigito(char c) {
        return c >= '0' && c <= '9';
    }
}
