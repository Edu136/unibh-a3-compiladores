package br.unibh.macs;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
        assertTrue(err().contains("uso: macsc <arquivo.macs> [--tokens] [--ast] [--exe] [--uai]"), err());
    }

    // ---------- geração de código ----------

    @Test
    @DisplayName("sem erros, o .asm é escrito ao lado do fonte (sem .exe)")
    void escreveAsmAoLadoDoFonte() throws IOException {
        String fonte = arquivo("ok.macs", "var x: int = 1; func f(): int { return 2; } print(x + f());");

        assertEquals(0, executar(fonte), err());
        Path asm = dir.resolve("ok.asm");
        assertTrue(Files.exists(asm));
        String conteudo = Files.readString(asm, StandardCharsets.UTF_8);
        assertTrue(conteudo.contains("global _main"), conteudo);
        assertTrue(conteudo.contains("fn_f:"), conteudo);
        assertTrue(conteudo.contains("g_x_0"), conteudo);
        assertFalse(Files.exists(dir.resolve("ok.exe")));
    }

    @Test
    @DisplayName("com erro de compilação, nem .asm nem .exe são gerados")
    void erroNaoGeraArquivos() throws IOException {
        assertEquals(1, executar(arquivo("ruim.macs", "print(x);"), "--exe"));
        assertFalse(Files.exists(dir.resolve("ruim.asm")));
        assertFalse(Files.exists(dir.resolve("ruim.exe")));
    }

    @Test
    @DisplayName("--exe gera um executável que roda")
    void exeRoda() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(br.unibh.macs.codegen.Montador.disponivel(),
                "nasm/gcc i686 não estão no PATH");
        assertEquals(0, executar(arquivo("ok.macs", "print(1 + 1);"), "--exe"), err());

        Path exe = dir.resolve("ok.exe");
        assertTrue(Files.exists(exe));
        Process p = new ProcessBuilder(exe.toString()).redirectErrorStream(true).start();
        String saida = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        assertEquals(0, p.waitFor());
        assertEquals("2", saida);
    }

    // ---------- fase sintática ----------

    @Test
    @DisplayName("--ast imprime a árvore anotada com tipos e conversões, e sai com 0")
    void astComSucesso() throws IOException {
        int rc = executar(arquivo("ok.macs", "var f: float = 1 + 2;"), "--ast");

        assertEquals(0, rc);
        assertEquals(String.join("\n",
                        "Program",
                        "  VarDecl f: float",
                        "    Convert float",
                        "      Binary + : int",
                        "        IntLit 1 : int",
                        "        IntLit 2 : int"),
                out().strip().replace("\r\n", "\n"));
        assertEquals("", err());
    }

    // ---------- fase semântica ----------

    @Test
    @DisplayName("todos os erros semânticos vão para o stderr, em ordem, e sai com 1")
    void errosSemanticos() throws IOException {
        int rc = executar(arquivo("s.macs", "print(a);\nprint(b);"));

        assertEquals(1, rc);
        String[] linhas = err().strip().split("\\R");
        assertEquals(2, linhas.length, err());
        assertTrue(linhas[0].endsWith("s.macs:1:7: erro semântico: variável 'a' não declarada"), linhas[0]);
        assertTrue(linhas[1].endsWith("s.macs:2:7: erro semântico: variável 'b' não declarada"), linhas[1]);
    }

    @Test
    @DisplayName("--ast com erro semântico não imprime a árvore")
    void astComErroSemantico() throws IOException {
        assertEquals(1, executar(arquivo("s.macs", "print(a);"), "--ast"));
        assertEquals("", out());
        assertTrue(err().contains("erro semântico"), err());
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
    @DisplayName("aninhamento profundo demais vira erro, sem stack trace")
    void aninhamentoProfundo() throws IOException {
        String fonte = "print(" + "- ".repeat(50_000) + "1);";
        assertEquals(1, executar(arquivo("fundo.macs", fonte)));
        // sem rótulo de fase: o estouro pode ocorrer no sintático, no semântico ou na impressão
        assertTrue(err().contains("fundo.macs: erro: programa aninhado demais"), err());
        assertFalse(err().contains("erro sintático"), err());
        assertFalse(err().contains("StackOverflowError"), err());
    }

    @Test
    @DisplayName("o compilador roda numa thread com pilha grande: 5000 níveis compilam")
    void pilhaGrandeNoMain() throws Exception {
        String fonte = "print(" + "- ".repeat(5_000) + "1);";
        String caminho = arquivo("medio.macs", fonte);
        assertEquals(0, Main.executarComPilhaGrande(new String[] {caminho},
                new java.io.PrintStream(outBytes, true, StandardCharsets.UTF_8),
                new java.io.PrintStream(errBytes, true, StandardCharsets.UTF_8)), err());
    }

    @Test
    @DisplayName("--tokens e --ast usam o mesmo fim de linha do sistema")
    void finaisDeLinhaConsistentes() throws IOException {
        assertEquals(0, executar(arquivo("ok.macs", "print(1);"), "--tokens", "--ast"));
        String semSeparadorDoSistema = out().replace(System.lineSeparator(), "");
        assertFalse(semSeparadorDoSistema.contains("\n") || semSeparadorDoSistema.contains("\r"), out());
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
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] programa = "var x: int;".getBytes(StandardCharsets.UTF_8);
        byte[] comBom = new byte[bom.length + programa.length];
        System.arraycopy(bom, 0, comBom, 0, bom.length);
        System.arraycopy(programa, 0, comBom, bom.length, programa.length);
        assertEquals(0, executar(arquivo("bom.macs", comBom), "--tokens"));
        assertTrue(out().startsWith("1:1  VAR  var"), out());
    }

    // ---------- modo mineiro (--uai) ----------

    @Test
    @DisplayName("M1: --tokens mostra o lexema mineiro com o tipo normal")
    void m1_tokensMostraLexemaMineiro() throws IOException {
        assertEquals(0, executar(arquivo("mineiro.macs", "trem x: int = 42;"), "--uai", "--tokens"), err());
        List<String> l = out().lines().toList();
        assertEquals("1:1  VAR  trem", l.get(0));
        assertEquals("1:6  ID  x", l.get(1));
    }

    @Test
    @DisplayName("M2: --ast de programa mineiro mostra os nós de sempre")
    void m2_astIgualAoNormal() throws IOException {
        assertEquals(0, executar(arquivo("mineiro.macs", "trem x: int = 1; prosa(x);"), "--uai", "--ast"), err());
        assertTrue(out().contains("VarDecl x: int"), out());
        assertTrue(out().contains("Print"), out());
    }

    @Test
    @DisplayName("M3: programa mineiro compila com --uai --exe e roda")
    void m3_programaMineiroRoda() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(br.unibh.macs.codegen.Montador.disponivel(),
                "nasm/gcc i686 não estão no PATH");
        String fonte = "trem n: int = 3; pra (trem i: int = 1; i <= n; i = i + 1) { prosa(i); }";
        assertEquals(0, executar(arquivo("mineiro.macs", fonte), "--uai", "--exe"), err());

        Path exe = dir.resolve("mineiro.exe");
        assertTrue(Files.exists(exe));
        Process p = new ProcessBuilder(exe.toString()).redirectErrorStream(true).start();
        String saida = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        assertEquals(0, p.waitFor());
        assertEquals(List.of("1", "2", "3"), saida.lines().toList());
    }

    @Test
    @DisplayName("M4: erro sintático com --uai ganha o prefixo")
    void m4_erroSintaticoComSotaque() throws IOException {
        assertEquals(1, executar(arquivo("mineiro.macs", "prosa(1)"), "--uai"));
        assertTrue(err().contains(":1:9: erro sintático: uai, sô! esperado ';', encontrado fim do arquivo"), err());
    }

    @Test
    @DisplayName("M5: erro semântico com --uai ganha o prefixo")
    void m5_erroSemanticoComSotaque() throws IOException {
        assertEquals(1, executar(arquivo("mineiro.macs", "prosa(x);"), "--uai"));
        assertTrue(err().contains(":1:7: erro semântico: uai, sô! variável 'x' não declarada"), err());
    }

    @Test
    @DisplayName("M6: erro léxico com --uai ganha o prefixo")
    void m6_erroLexicoComSotaque() throws IOException {
        assertEquals(1, executar(arquivo("mineiro.macs", "prosa(1 @ 2);"), "--uai"));
        assertTrue(err().contains(":1:9: erro léxico: uai, sô! caractere inesperado '@'"), err());
    }

    @Test
    @DisplayName("M7: sem --uai, mineirês é erro comum, sem sotaque")
    void m7_semUaiMineiresEErroComum() throws IOException {
        assertEquals(1, executar(arquivo("mineiro.macs", "trem x: int = 1;")));
        assertTrue(err().contains(
                ":1:6: erro sintático: esperado '=' ou '(' após identificador, encontrado identificador 'x'"), err());
        assertFalse(err().contains("uai"), err());
    }

    @Test
    @DisplayName("M8: com --uai, palavra mineira é reservada; sem --uai, é nome comum")
    void m8_palavraMineiraEReservada() throws IOException {
        String fonte = arquivo("mineiro.macs", "var se: int = 1;");
        assertEquals(1, executar(fonte, "--uai"));
        assertTrue(err().contains(":1:5: erro sintático: uai, sô! esperado identificador, encontrado 'se'"), err());
        assertEquals(0, executar(fonte), err());
    }
}
