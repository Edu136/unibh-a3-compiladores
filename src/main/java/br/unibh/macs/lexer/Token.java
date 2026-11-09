package br.unibh.macs.lexer;

/**
 * Um token reconhecido pelo analisador léxico.
 *
 * @param type   categoria do token
 * @param lexema texto exatamente como aparece no código-fonte
 * @param valor  valor do literal (Integer, Double, Character ou String); null nos demais
 * @param linha  linha do primeiro caractere (a partir de 1)
 * @param coluna coluna do primeiro caractere (a partir de 1)
 */
public record Token(TokenType type, String lexema, Object valor, int linha, int coluna) {
}
