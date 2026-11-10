package br.unibh.macs.codegen;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Testes de ponta a ponta: compila, monta (nasm), liga (gcc i686), executa e confere a saída.
 * Pulados se a toolchain não estiver no PATH. Rodam em paralelo (cada um usa sua pasta temporária).
 */
@Execution(ExecutionMode.CONCURRENT)
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
        return Stream.of(
                // ---- int e bool ----
                caso("print(2 + 3 * 4); print((2 + 3) * 4); print(7 / 2); print(-7 / 2); "
                        + "print(7 % 3); print(-7 % 3); print(-(5));",
                        "14\n20\n3\n-3\n1\n-1\n-5\n"),
                caso("print(2147483647 + 1);", "-2147483648\n"),
                caso("print(1 < 2); print(2 <= 1); print(3 == 3); print(3 != 3); print(!true); "
                        + "print(true && false); print(false || true);",
                        "true\nfalse\ntrue\nfalse\nfalse\nfalse\ntrue\n"),
                caso("var z: int = 0; print(false && 1 / z == 0); print(true || 1 / z == 0);", "false\ntrue\n"),
                caso("var x: int; print(x); x = 5; x = x * 2; print(x); var b: bool; print(b);", "0\n10\nfalse\n"),
                caso("var z: int = 0;\nprint(1);\nprint(10 / z);", "",
                        "1\nerro de execução: divisão por zero (linha 3)\n", 1),
                caso("var z: int = 0; print(5 % z);", "", "erro de execução: divisão por zero (linha 1)\n", 1),
                caso("print(5 > 3 == true); print(1 >= 1); print(2 > 1 && 3 > 2 || false);", "true\ntrue\ntrue\n"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casos")
    void programa(String fonte, String stdin, String saida, int codigo) throws Exception {
        verificar(fonte, stdin, saida, codigo);
    }
}
