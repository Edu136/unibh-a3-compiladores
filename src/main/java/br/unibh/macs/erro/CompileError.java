package br.unibh.macs.erro;

/** Erro de compilação com a fase e a posição no código-fonte. */
public class CompileError extends RuntimeException {

    public enum Fase {
        LEXICO("léxico"),
        SINTATICO("sintático"),
        SEMANTICO("semântico");

        private final String descricao;

        Fase(String descricao) {
            this.descricao = descricao;
        }

        public String descricao() {
            return descricao;
        }
    }

    private final Fase fase;
    private final int linha;
    private final int coluna;

    public CompileError(Fase fase, int linha, int coluna, String mensagem) {
        super(mensagem);
        this.fase = fase;
        this.linha = linha;
        this.coluna = coluna;
    }

    public Fase getFase() {
        return fase;
    }

    public int getLinha() {
        return linha;
    }

    public int getColuna() {
        return coluna;
    }

    /** Formata como {@code arquivo:linha:coluna: erro <fase>: mensagem}. */
    public String formatar(String arquivo) {
        return arquivo + ":" + linha + ":" + coluna + ": erro " + fase.descricao() + ": " + getMessage();
    }
}
