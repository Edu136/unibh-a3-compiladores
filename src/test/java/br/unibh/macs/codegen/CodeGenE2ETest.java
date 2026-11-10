package br.unibh.macs.codegen;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Testes de ponta a ponta: compila, monta (nasm), liga (gcc i686), executa e confere a saída.
 * Pulados se a toolchain não estiver no PATH.
 */
class CodeGenE2ETest {

    @BeforeAll
    static void toolchainDisponivel() {
        assumeTrue(Montador.disponivel(), "nasm/gcc i686 não estão no PATH: testes e2e pulados");
    }

    /** Programa, stdin, saída esperada (linhas separadas por \n) e código de saída. */
    static Arguments caso(String fonte, String stdin, String saida, int codigo) {
        return Arguments.of(fonte, stdin, saida, codigo);
    }

    static Arguments caso(String fonte, String saida) {
        return caso(fonte, "", saida, 0);
    }

    private static void verificar(String fonte, String stdin, String saida, int codigo) throws Exception {
        E2E.Execucao r = E2E.executar(fonte, stdin);
        assertEquals(saida, r.saida(), "saída de: " + fonte);
        assertEquals(codigo, r.codigo(), "código de saída de: " + fonte);
    }

    // ---------- esqueleto ----------

    @Test
    void imprimeInteiro() throws Exception {
        verificar("print(42);", "", "42\n", 0);
    }

    @Test
    void asmTemEstruturaBasica() {
        String asm = E2E.asm("print(1);");
        assertTrue(asm.contains("global _main"), asm);
        assertTrue(asm.contains("section .data"), asm);
        assertTrue(asm.contains("section .text"), asm);
        assertTrue(asm.contains("_SetConsoleOutputCP@4"), asm);
    }

    // ---------- tabelas de cenários (preenchidas nos próximos ciclos) ----------

    static Stream<Arguments> casos() {
        return Stream.of(caso("print(7);", "7\n"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casos")
    void programa(String fonte, String stdin, String saida, int codigo) throws Exception {
        verificar(fonte, stdin, saida, codigo);
    }
}
