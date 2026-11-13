package br.unibh.macs.lexer;

/** Categorias de token da MACSLang. */
public enum TokenType {
    // palavras-chave
    VAR, FUNC, IF, ELSE, WHILE, FOR, RETURN, PRINT, INPUT, TRUE, FALSE,
    INT, FLOAT, CHAR, BOOL, STRING,

    // literais e nomes
    INT_LIT, FLOAT_LIT, CHAR_LIT, STRING_LIT, ID,

    // operadores
    PLUS, MINUS, STAR, SLASH, PERCENT,
    INC, DEC, PLUS_ASSIGN, MINUS_ASSIGN, STAR_ASSIGN, SLASH_ASSIGN, PERCENT_ASSIGN,
    ASSIGN, EQ, NE, LT, LE, GT, GE,
    AND, OR, NOT,

    // pontuação
    LPAREN, RPAREN, LBRACE, RBRACE, COMMA, SEMICOLON, COLON,

    EOF
}
