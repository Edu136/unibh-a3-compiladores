package br.unibh.macs.codegen;

/**
 * Runtime auxiliar emitido em todo .asm: rotinas que o código gerado chama.
 * Todas seguem cdecl (quem chama limpa a pilha) e preservam ebx, esi e edi.
 * A memória alocada (strings) não é liberada na v1.
 */
final class Runtime {

    private Runtime() {
    }

    /** Seção .data: formatos do printf/scanf e textos fixos (o .asm é UTF-8). */
    static final String DADOS = """
            fmt_print_int   db `%d\\n`, 0
            fmt_print_float db `%f\\n`, 0
            fmt_print_str   db `%s\\n`, 0
            fmt_scan_int    db `%d`, 0
            fmt_scan_float  db `%lf`, 0
            fmt_scan_char   db ` %c`, 0
            fmt_scan_str    db ` %255[^\\n]`, 0
            fmt_d           db `%d`, 0
            fmt_f           db `%f`, 0
            fmt_div_zero    db `erro de execução: divisão por zero (linha %d)\\n`, 0
            str_true        db `true`, 0
            str_false       db `false`, 0
            str_vazia       db 0
            bom_verificado  db 0
            """;

    /** Seção .text: rotinas do runtime. */
    static final String CODIGO = """
            ; ---------------- runtime ----------------

            ; char* rt_concat(char* a, char* b): aloca strlen(a)+strlen(b)+1 e junta as duas
            rt_concat:
                push ebp
                mov ebp, esp
                push ebx
                push esi
                push dword [ebp+8]
                call _strlen
                add esp, 4
                mov ebx, eax                ; ebx = strlen(a)
                push dword [ebp+12]
                call _strlen
                add esp, 4
                lea eax, [ebx+eax+1]
                push eax
                call _malloc
                add esp, 4
                mov esi, eax                ; esi = destino
                push dword [ebp+8]
                push esi
                call _strcpy
                add esp, 8
                push dword [ebp+12]
                push esi
                call _strcat
                add esp, 8
                mov eax, esi
                pop esi
                pop ebx
                pop ebp
                ret

            ; char* rt_str_int(int i)
            rt_str_int:
                push 12                     ; "-2147483648" + '\\0'
                call _malloc
                add esp, 4
                push eax                    ; guarda o buffer
                push dword [esp+8]          ; i
                push fmt_d
                push eax
                call _sprintf
                add esp, 12
                pop eax
                ret

            ; char* rt_str_float(double d) — mesmo formato do print (%f)
            rt_str_float:
                push 400                    ; %f de um double grande pode ter ~320 caracteres
                call _malloc
                add esp, 4
                push eax                    ; [esp]=buffer [esp+4]=retorno [esp+8..15]=d
                push dword [esp+12]         ; metade alta de d
                push dword [esp+12]         ; metade baixa de d
                push fmt_f
                push eax
                call _sprintf
                add esp, 16
                pop eax
                ret

            ; char* rt_str_bool(int b): "true" ou "false" (textos fixos, não alocados)
            rt_str_bool:
                mov eax, str_false
                cmp dword [esp+4], 0
                je .fim
                mov eax, str_true
            .fim:
                ret

            ; char* rt_str_char(int cp): código Unicode -> texto UTF-8 (1 a 3 bytes)
            rt_str_char:
                push 4
                call _malloc
                add esp, 4
                mov ecx, [esp+4]            ; cp
                cmp ecx, 0x80
                jae .dois
                mov [eax], cl               ; ASCII: 1 byte
                mov byte [eax+1], 0
                ret
            .dois:
                cmp ecx, 0x800
                jae .tres
                mov edx, ecx                ; 110xxxxx 10xxxxxx
                shr edx, 6
                or dl, 0xC0
                mov [eax], dl
                mov edx, ecx
                and dl, 0x3F
                or dl, 0x80
                mov [eax+1], dl
                mov byte [eax+2], 0
                ret
            .tres:
                mov edx, ecx                ; 1110xxxx 10xxxxxx 10xxxxxx
                shr edx, 12
                or dl, 0xE0
                mov [eax], dl
                mov edx, ecx
                shr edx, 6
                and dl, 0x3F
                or dl, 0x80
                mov [eax+1], dl
                mov edx, ecx
                and dl, 0x3F
                or dl, 0x80
                mov [eax+2], dl
                mov byte [eax+3], 0
                ret

            ; void rt_pular_bom(): só na primeira leitura, descarta um BOM UTF-8 (EF BB BF) no início
            ; da entrada (o pipe do PowerShell 5.1 envia um). Feito na primeira leitura, e não no início
            ; do programa, para não esperar o teclado antes de o prompt aparecer.
            rt_pular_bom:
                cmp byte [bom_verificado], 0
                jne .fim
                mov byte [bom_verificado], 1
                call ___iob_func            ; função C __iob_func(): &_iob[0] = stdin
                push eax                    ; [esp] = stdin
                push eax
                call _fgetc
                add esp, 4
                cmp eax, 0xEF
                je .bom
                cmp eax, -1                 ; EOF: nada a devolver
                je .sai
                push dword [esp]            ; stdin
                push eax
                call _ungetc                ; não era BOM: devolve o caractere
                add esp, 8
                jmp .sai
            .bom:
                push dword [esp]
                call _fgetc                 ; BB
                add esp, 4
                push dword [esp]
                call _fgetc                 ; BF
                add esp, 4
            .sai:
                add esp, 4
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
