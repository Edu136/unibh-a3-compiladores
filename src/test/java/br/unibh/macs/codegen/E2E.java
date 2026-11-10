package br.unibh.macs.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.unibh.macs.lexer.Lexer;
import br.unibh.macs.parser.Parser;
import br.unibh.macs.semantic.Resultado;
import br.unibh.macs.semantic.SemanticAnalyzer;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Utilitário dos testes de ponta a ponta: compila um programa MACSLang até .exe,
 * executa com o stdin dado e devolve a saída e o código de saída.
 */
final class E2E {

    record Execucao(String saida, int codigo) {
    }

    /** Compartilhado: as ferramentas são verificadas uma única vez para todos os testes. */
    private static final Montador MONTADOR = new Montador();

    private E2E() {
    }

    /** Gera o assembly (falha o teste se houver erro de compilação). */
    static String asm(String fonte) {
        Resultado r = SemanticAnalyzer.analisar(Parser.analisar(Lexer.tokenizar(fonte)));
        assertEquals(List.of(), r.erros().stream().map(e -> e.formatar("teste")).toList());
        return CodeGenerator.gerar(r.programa(), r.resolucao());
    }

    static Execucao executar(String fonte, String stdin) throws Exception {
        // nome com espaço de propósito: a pasta do projeto também tem ("Trabalho - copiladores")
        Path dir = Files.createTempDirectory("macsc e2e ");
        Path asm = dir.resolve("prog.asm");
        Path exe = dir.resolve("prog.exe");
        Files.writeString(asm, asm(fonte), StandardCharsets.UTF_8);
        MONTADOR.gerarExe(asm, exe);

        Process p = new ProcessBuilder(exe.toString()).redirectErrorStream(true).start();
        try (OutputStream in = p.getOutputStream()) {
            in.write(stdin.getBytes(StandardCharsets.UTF_8));
        }
        byte[] bytes;
        try (InputStream out = p.getInputStream()) {
            bytes = out.readAllBytes();
        }
        if (!p.waitFor(10, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new AssertionError("o programa não terminou em 10 s (laço infinito?)");
        }
        String saida = new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n");
        return new Execucao(saida, p.exitValue());
    }
}
