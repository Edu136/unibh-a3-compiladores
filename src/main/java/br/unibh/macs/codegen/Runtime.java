package br.unibh.macs.codegen;

/**
 * Runtime auxiliar emitido em todo .asm: rotinas que o código gerado chama.
 * Todas seguem cdecl (quem chama limpa a pilha) e preservam ebx, esi e edi.
 */
final class Runtime {

    private Runtime() {
    }

    /** Seção .data: formatos do printf/scanf e textos fixos (o .asm é UTF-8). */
    static final String DADOS = """
            fmt_print_int   db `%d\\n`, 0
            fmt_print_str   db `%s\\n`, 0
            fmt_div_zero    db `erro de execução: divisão por zero (linha %d)\\n`, 0
            str_true        db `true`, 0
            str_false       db `false`, 0
            """;

    /** Seção .text: rotinas do runtime. */
    static final String CODIGO = """
            ; ---------------- runtime ----------------

            ; char* rt_str_bool(int b): "true" ou "false" (textos fixos, não alocados)
            rt_str_bool:
                mov eax, str_false
                cmp dword [esp+4], 0
                je .fim
                mov eax, str_true
            .fim:
                ret

            ; void rt_div_zero(int linha): mensagem e encerra com código 1.
            ; _exit é o símbolo da função C exit(), que esvazia o buffer do printf.
            rt_div_zero:
                push dword [esp+4]
                push fmt_div_zero
                call _printf
                add esp, 8
                push 1
                call _exit
            """;
}
