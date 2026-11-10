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
        if (saida != null) { // null = só o código de saída importa (ex.: texto de infinito da msvcrt)
            assertEquals(saida, r.saida(), "saída de: " + fonte);
        }
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
                caso("print(5 > 3 == true); print(1 >= 1); print(2 > 1 && 3 > 2 || false);", "true\ntrue\ntrue\n"),

                // ---- controle de fluxo ----
                caso("var x: int = 5; "
                        + "if (x > 3) { print(1); } else if (x > 0) { print(2); } else { print(3); } x = 1; "
                        + "if (x > 3) { print(1); } else if (x > 0) { print(2); } else { print(3); } x = -1; "
                        + "if (x > 3) { print(1); } else if (x > 0) { print(2); } else { print(3); } "
                        + "if (x > 0) { print(4); }",
                        "1\n2\n3\n"),
                caso("var i: int = 1; var s: int = 0; while (i <= 10) { s = s + i; i = i + 1; } print(s);", "55\n"),
                caso("for (var i: int = 0; i < 3; i = i + 1) { print(i); }", "0\n1\n2\n"),
                caso("var i: int = 0; for (; i < 3; ) { print(i); i = i + 1; }", "0\n1\n2\n"),
                caso("var x: int = 1; { var x: int = 2; print(x); } print(x);", "2\n1\n"),
                caso("for (var i: int = 0; i < 2; i = i + 1) { var s: int; print(s); s = 9; }", "0\n0\n"),
                caso("for (var i: int = 0; i < 3; i = i + 1) { for (var j: int = 0; j < 2; j = j + 1) "
                        + "{ print(i * 10 + j); } }",
                        "0\n1\n10\n11\n20\n21\n"),
                caso("while (false) { print(1); } print(2);", "2\n"),

                // ---- funções ----
                caso("func sub(a: int, b: int): int { return a - b; } print(sub(10, 3));", "7\n"),
                caso("func fat(n: int): int { if (n <= 1) { return 1; } return n * fat(n - 1); } "
                        + "func fib(n: int): int { if (n < 2) { return n; } return fib(n - 1) + fib(n - 2); } "
                        + "print(fat(5)); print(fib(10));",
                        "120\n55\n"),
                caso("mostra(7); func mostra(n: int): int { print(n); return 0; }", "7\n"),
                caso("func soma(a: int, b: int): int { return a + b; } "
                        + "print(soma(soma(1, 2), soma(3, 4)) * 2);", "20\n"),
                caso("func f(): bool { print(99); return true; } print(false && f()); print(true || f());",
                        "false\ntrue\n"),
                caso("func g(a: int, b: int, c: int): int { var x: int = a * 100; var y: int = b * 10; "
                        + "return x + y + c; } print(g(1, 2, 3));", "123\n"),
                caso("func d(n: int): int { return 10 / n; }\nprint(d(0));", "",
                        "erro de execução: divisão por zero (linha 1)\n", 1),
                caso("func eh(n: int): bool { return n > 0; } if (eh(5)) { print(1); }", "1\n"),
                caso("func conta(n: int): int { var s: int = 0; for (var i: int = 1; i <= n; i = i + 1) "
                        + "{ var q: int = i * i; s = s + q; } return s; } print(conta(3)); print(conta(4));",
                        "14\n30\n"),

                // ---- float ----
                caso("print(2.5); print(1 + 2.5); print(7 / 2.0); var f: float = 3; print(f); print(-1.5);",
                        "2.500000\n3.500000\n3.500000\n3.000000\n-1.500000\n"),
                caso("print(1.5 < 2); print(2.0 == 2); print(0.1 + 0.2 == 0.3); print(2.5 >= 2.5); "
                        + "print(1.0 != 1);",
                        "true\ntrue\nfalse\ntrue\nfalse\n"),
                caso("func media(a: float, b: float): float { return (a + b) / 2; } print(media(3, 4));",
                        "3.500000\n"),
                caso("var f: float; input(f); print(f * 2);", "2.5\n", "5.000000\n", 0),
                caso("var f: float = 0; print(1.0 / f);", "", null, 0),
                caso("func mix(a: int, x: float, b: int): float { var t: float = x * a; return t + b; } "
                        + "print(mix(2, 1.5, 10)); var g: float; print(g);",
                        "13.000000\n0.000000\n"),
                caso("var n: int; input(n); print(n * 3);", "14\n", "42\n", 0),

                // ---- string e char ----
                caso("print(\"olá, mundo\");", "olá, mundo\n"),
                caso("print(\"n = \" + 1 + 2.5 + true + 'c');", "n = 12.500000truec\n"),
                caso("print(1 + 2 + \"a\"); print(\"a\" + 1 + 2);", "3a\na12\n"),
                caso("print(\"abc\" == \"abc\"); print(\"abc\" != \"abd\"); var s: string = \"ab\" + \"c\"; "
                        + "print(s == \"abc\");", "true\ntrue\ntrue\n"),
                caso("var c: char = 'á'; print(c); print('a' < 'b'); print(\"x\" + c);", "á\ntrue\nxá\n"),
                caso("var s: string; print(\"[\" + s + \"]\"); print(\"a\\tb\\\\c\\\"d\");", "[]\na\tb\\c\"d\n"),
                caso("var n: int; var s: string; input(n); input(s); print(s + \" \" + n);",
                        "7\nSão Paulo\n", "São Paulo 7\n", 0),
                caso("var c: char; input(c); print(c);", "x\n", "x\n", 0),
                caso("print(\"a`b\");", "a`b\n"),
                caso("func saud(n: string): string { return \"Olá, \" + n; } print(saud(\"Ana\"));", "Olá, Ana\n"),
                caso("var a: string = \"x\"; var b: string = a; a = a + \"y\"; print(a); print(b); "
                        + "print('\\n' == '\\n'); print(\"\" == \"\");", "xy\nx\ntrue\ntrue\n"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casos")
    void programa(String fonte, String stdin, String saida, int codigo) throws Exception {
        verificar(fonte, stdin, saida, codigo);
    }
}
