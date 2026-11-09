package br.unibh.macs;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {

    @TempDir
    Path dir;

    private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

    private int executar(String... args) {
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);
        return Main.run(args, out, err);
    }

    private String out() {
        return outBytes.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return errBytes.toString(StandardCharsets.UTF_8);
    }

    private String arquivo(String nome, String conteudo) throws IOException {
        return arquivo(nome, conteudo.getBytes(StandardCharsets.UTF_8));
    }

    private String arquivo(String nome, byte[] conteudo) throws IOException {
        Path p = dir.resolve(nome);
        Files.write(p, conteudo);
        return p.toString();
    }

    @Test
    @DisplayName("--tokens imprime um token por linha e sai com 0")
    void tokensComSucesso() throws IOException {
        int rc = executar(arquivo("ok.macs", "var x: int = 42;"), "--tokens");

        assertEquals(0, rc);
        String[] linhas = out().strip().split("\\R");
        assertEquals(8, linhas.length);
        assertEquals("1:1  VAR  var", linhas[0]);
        assertEquals("1:14  INT_LIT  42", linhas[5]);
        assertEquals("1:17  EOF", linhas[7]);
        assertEquals("", err());
    }

    @Test
    @DisplayName("erro léxico vai para o stderr com arquivo:linha:coluna e sai com 1")
    void erroLexico() throws IOException {
        int rc = executar(arquivo("ruim.macs", "x @"), "--tokens");

        assertEquals(1, rc);
        assertTrue(err().contains("ruim.macs:1:3: erro léxico: caractere inesperado '@'"), err());
        assertEquals("", out());
    }

    @Test
    @DisplayName("sem --tokens não imprime nada e sai com 0")
    void semTokens() throws IOException {
        assertEquals(0, executar(arquivo("ok.macs", "var x: int = 42;")));
        assertEquals("", out());
    }

    @Test
    void semArgumentos() {
        assertEquals(2, executar());
        assertTrue(err().contains("uso: macsc <arquivo.macs> [--tokens] [--ast]"), err());
    }

    // ---------- fase sintática ----------

    @Test
    @DisplayName("--ast imprime a árvore e sai com 0")
    void astComSucesso() throws IOException {
        int rc = executar(arquivo("ok.macs", "var x: int = 1 + 2;"), "--ast");

        assertEquals(0, rc);
        assertEquals(String.join("\n",
                        "Program",
                        "  VarDecl x: int",
                        "    Binary +",
                        "      IntLit 1",
                        "      IntLit 2"),
                out().strip().replace("\r\n", "\n"));
        assertEquals("", err());
    }

    @Test
    @DisplayName("erro sintático vai para o stderr e sai com 1")
    void erroSintatico() throws IOException {
        int rc = executar(arquivo("ruim.macs", "print(1"), "--ast");

        assertEquals(1, rc);
        assertTrue(err().contains("ruim.macs:1:8: erro sintático: esperado ')', encontrado fim do arquivo"), err());
        assertEquals("", out());
    }

    @Test
    @DisplayName("--tokens e --ast juntos: tokens primeiro, depois a árvore")
    void tokensEAst() throws IOException {
        assertEquals(0, executar(arquivo("ok.macs", "print(1);"), "--tokens", "--ast"));
        String saida = out();
        assertTrue(saida.startsWith("1:1  PRINT  print"), saida);
        assertTrue(saida.indexOf("1:10  EOF") < saida.indexOf("Program"), saida);
    }

    @Test
    @DisplayName("sem flags, a sintaxe também é verificada")
    void semFlagsVerificaSintaxe() throws IOException {
        assertEquals(1, executar(arquivo("ruim.macs", "print(1")));
        assertTrue(err().contains("erro sintático"), err());
    }

    @Test
    @DisplayName("vetor é rejeitado já no léxico")
    void vetorRejeitadoNoLexico() throws IOException {
        assertEquals(1, executar(arquivo("v.macs", "a[0] = 1;"), "--ast"));
        assertTrue(err().contains("v.macs:1:2: erro léxico: caractere inesperado '['"), err());
    }

    @Test
    void arquivoInexistente() {
        assertEquals(2, executar("naoexiste.macs"));
        assertTrue(err().contains("arquivo não encontrado: naoexiste.macs"), err());
    }

    @Test
    void opcaoDesconhecida() throws IOException {
        assertEquals(2, executar(arquivo("ok.macs", "x"), "--xyz"));
        assertTrue(err().contains("opção desconhecida: --xyz"), err());
    }

    @Test
    @DisplayName("caminho com caractere inválido é erro de uso, sem exceção")
    void caminhoInvalido() {
        assertEquals(2, executar("a?b.macs"));
        assertTrue(err().contains("caminho de arquivo inválido: a?b.macs"), err());
    }

    @Test
    @DisplayName("arquivo salvo em ANSI (Windows-1252) é lido corretamente")
    void arquivoAnsi() throws IOException {
        byte[] ansi = "print(\"número\");".getBytes(java.nio.charset.Charset.forName("windows-1252"));
        assertEquals(0, executar(arquivo("ansi.macs", ansi), "--tokens"));
        assertTrue(out().contains("1:7  STRING_LIT  \"número\""), out());
    }

    @Test
    @DisplayName("mais de um arquivo é erro de uso")
    void doisArquivos() throws IOException {
        assertEquals(2, executar(arquivo("a.macs", "x"), arquivo("b.macs", "y")));
        assertTrue(err().contains("uso: macsc"), err());
    }

    @Test
    @DisplayName("acentos aparecem corretos na saída de --tokens")
    void acentosNaSaida() throws IOException {
        assertEquals(0, executar(arquivo("s.macs", "print(\"olá\");"), "--tokens"));
        assertTrue(out().contains("1:7  STRING_LIT  \"olá\""), out());
    }

    @Test
    @DisplayName("BOM UTF-8 no início do arquivo é ignorado")
    void bomUtf8() throws IOException {
        byte[] comBom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'x', '=', '1', ';'};
        assertEquals(0, executar(arquivo("bom.macs", comBom), "--tokens"));
        assertTrue(out().startsWith("1:1  ID  x"), out());
    }
}
