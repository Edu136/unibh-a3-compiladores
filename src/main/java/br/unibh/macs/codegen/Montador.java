package br.unibh.macs.codegen;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Transforma o .asm gerado em .exe chamando as ferramentas externas:
 * {@code nasm -f win32} (montador) e {@code gcc -m32} (ligador do MinGW-w64 i686).
 */
public final class Montador {

    /** Falha ao montar/ligar. {@code ferramentaAusente} distingue erro de ambiente de defeito no .asm. */
    public static final class FalhaMontagem extends Exception {
        private final boolean ferramentaAusente;

        FalhaMontagem(String mensagem, boolean ferramentaAusente) {
            super(mensagem);
            this.ferramentaAusente = ferramentaAusente;
        }

        public boolean ferramentaAusente() {
            return ferramentaAusente;
        }
    }

    private record Saida(int codigo, String texto) {
    }

    private final List<String> nasm;
    private final List<String> gcc;

    public Montador() {
        this(List.of("nasm"), List.of("gcc"));
    }

    /** Permite trocar os comandos (usado nos testes). */
    Montador(List<String> nasm, List<String> gcc) {
        this.nasm = nasm;
        this.gcc = gcc;
    }

    /** True se nasm e gcc i686 estão disponíveis. */
    public static boolean disponivel() {
        try {
            new Montador().verificarFerramentas();
            return true;
        } catch (FalhaMontagem e) {
            return false;
        }
    }

    /** Confere se nasm e gcc existem e se o gcc gera código de 32 bits (i686). */
    public void verificarFerramentas() throws FalhaMontagem {
        try {
            executar(comando(nasm, "-v"));
        } catch (IOException e) {
            throw new FalhaMontagem("nasm não encontrado no PATH (instale o NASM ou ajuste o PATH)", true);
        }
        Saida maquina;
        try {
            maquina = executar(comando(gcc, "-dumpmachine"));
        } catch (IOException e) {
            throw new FalhaMontagem("gcc não encontrado no PATH (instale o MinGW-w64 i686 ou ajuste o PATH)", true);
        }
        String alvo = maquina.texto().strip();
        if (!alvo.startsWith("i686")) {
            throw new FalhaMontagem("gcc não é i686 (encontrado: " + alvo + "); é preciso o MinGW-w64 de 32 bits", true);
        }
    }

    /** Monta {@code asm} e liga em {@code exe}. O .obj intermediário é apagado no fim. */
    public void gerarExe(Path asm, Path exe) throws FalhaMontagem {
        verificarFerramentas();
        Path obj = exe.resolveSibling(nomeSemExtensao(exe) + ".obj");
        try {
            Saida montagem = executar(comando(nasm, "-f", "win32", asm.toString(), "-o", obj.toString()));
            if (montagem.codigo() != 0) {
                throw new FalhaMontagem("nasm falhou:\n" + montagem.texto().strip(), false);
            }
            Saida ligacao = executar(comando(gcc, "-m32", obj.toString(), "-o", exe.toString()));
            if (ligacao.codigo() != 0) {
                throw new FalhaMontagem("gcc falhou:\n" + ligacao.texto().strip(), false);
            }
        } catch (IOException e) {
            throw new FalhaMontagem("não foi possível executar as ferramentas: " + e.getMessage(), true);
        } finally {
            try {
                Files.deleteIfExists(obj);
            } catch (IOException ignorado) {
                // o .obj é só intermediário
            }
        }
    }

    private static List<String> comando(List<String> base, String... args) {
        List<String> c = new ArrayList<>(base);
        c.addAll(List.of(args));
        return c;
    }

    private static Saida executar(List<String> comando) throws IOException {
        Process p = new ProcessBuilder(comando).redirectErrorStream(true).start();
        String texto;
        try (InputStream in = p.getInputStream()) {
            texto = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try {
            if (!p.waitFor(60, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("tempo esgotado executando " + comando.get(0));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrompido executando " + comando.get(0));
        }
        return new Saida(p.exitValue(), texto);
    }

    private static String nomeSemExtensao(Path p) {
        String nome = p.getFileName().toString();
        int ponto = nome.lastIndexOf('.');
        return ponto > 0 ? nome.substring(0, ponto) : nome;
    }
}
