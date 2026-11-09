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
        assertTrue(err().contains("uso: macsc <arquivo.macs> [--tokens]"), err());
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
    @DisplayName("acentos aparecem corretos na saída de --tokens")
    void acentosNaSaida() throws IOException {
        assertEquals(0, executar(arquivo("s.macs", "\"olá\""), "--tokens"));
        assertTrue(out().startsWith("1:1  STRING_LIT  \"olá\""), out());
    }

    @Test
    @DisplayName("BOM UTF-8 no início do arquivo é ignorado")
    void bomUtf8() throws IOException {
        byte[] comBom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'x'};
        assertEquals(0, executar(arquivo("bom.macs", comBom), "--tokens"));
        assertTrue(out().startsWith("1:1  ID  x"), out());
    }
}
