; Gerado por macsc - MACSLang -> NASM x86 (32 bits, Windows)
; Montar: nasm -f win32 prog.asm -o prog.obj
; Ligar:  gcc -m32 prog.obj -o prog.exe

global _main
extern _printf, _scanf, _sprintf, _malloc, _strlen, _strcpy, _strcat, _strcmp, _exit
extern _fgetc, _ungetc, ___iob_func
extern _SetConsoleOutputCP@4, _SetConsoleCP@4

section .data
fmt_print_int   db `%d\n`, 0
fmt_print_float db `%f\n`, 0
fmt_print_str   db `%s\n`, 0
fmt_scan_int    db `%d`, 0
fmt_scan_float  db `%lf`, 0
fmt_scan_char   db ` %c`, 0
fmt_scan_str    db ` %255[^\n]`, 0
fmt_d           db `%d`, 0
fmt_f           db `%f`, 0
fmt_div_zero    db `erro de execução: divisão por zero (linha %d)\n`, 0
str_true        db `true`, 0
str_false       db `false`, 0
str_vazia       db 0
bom_verificado  db 0
str_0           db `Digite um número: `, 0
str_1           db `Fatorial de `, 0
str_2           db ` = `, 0

section .bss
g_numero_0: resq 1
g_i_1: resq 1

section .text
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
    push 12                     ; "-2147483648" + '\0'
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


; func fatorial(n: int): int
fn_fatorial:
    push ebp
    mov ebp, esp
    ; if
    mov eax, dword [ebp+8]          ; n
    push eax
    mov eax, 1
    mov ecx, eax
    pop eax
    cmp eax, ecx
    setle al
    movzx eax, al                   ; resultado bool: 0 ou 1
    test eax, eax
    jz L0                           ; condição falsa
    ; return
    mov eax, 1
    jmp fn_fatorial@fim
    jmp L1
L0:
L1:
    ; return
    mov eax, dword [ebp+8]          ; n
    push eax
    ; chamada fatorial(1 argumento(s))
    mov eax, dword [ebp+8]          ; n
    push eax
    mov eax, 1
    mov ecx, eax
    pop eax
    sub eax, ecx
    sub esp, 8
    mov dword [esp], eax
    call fn_fatorial
    add esp, 8                      ; descarta os argumentos
    mov ecx, eax
    pop eax
    imul eax, ecx
    jmp fn_fatorial@fim
fn_fatorial@fim:
    mov esp, ebp
    pop ebp
    ret

; ---------------- programa principal ----------------
_main:
    push ebp
    mov ebp, esp
    push 65001                      ; console em UTF-8 (saída)
    call _SetConsoleOutputCP@4      ; stdcall: a própria função limpa a pilha
    push 65001                      ; console em UTF-8 (entrada)
    call _SetConsoleCP@4
    ; var numero: int
    mov dword [g_numero_0], 0       ; numero = valor padrão
    ; print (string)
    mov eax, str_0
    push eax
    push fmt_print_str
    call _printf
    add esp, 8
    ; input numero (int)
    call rt_pular_bom               ; ignora BOM no início da entrada (só na 1ª leitura)
    mov dword [g_numero_0], 0       ; numero = valor padrão
    lea eax, [g_numero_0]
    push eax
    push fmt_scan_int
    call _scanf
    add esp, 8
    ; for
    ; var i: int
    mov eax, 1
    mov dword [g_i_1], eax          ; i
L2:
    mov eax, dword [g_i_1]          ; i
    push eax
    mov eax, dword [g_numero_0]     ; numero
    mov ecx, eax
    pop eax
    cmp eax, ecx
    setle al
    movzx eax, al                   ; resultado bool: 0 ou 1
    test eax, eax
    jz L3                           ; sai do laço
    ; print (string)
    ; concatenação
    ; concatenação
    ; concatenação
    mov eax, str_1
    push eax
    mov eax, dword [g_i_1]          ; i
    push eax
    call rt_str_int
    add esp, 4
    mov ecx, eax                    ; b
    pop eax                         ; a
    push ecx
    push eax
    call rt_concat                  ; rt_concat(a, b)
    add esp, 8
    push eax
    mov eax, str_2
    mov ecx, eax                    ; b
    pop eax                         ; a
    push ecx
    push eax
    call rt_concat                  ; rt_concat(a, b)
    add esp, 8
    push eax
    ; chamada fatorial(1 argumento(s))
    mov eax, dword [g_i_1]          ; i
    sub esp, 8
    mov dword [esp], eax
    call fn_fatorial
    add esp, 8                      ; descarta os argumentos
    push eax
    call rt_str_int
    add esp, 4
    mov ecx, eax                    ; b
    pop eax                         ; a
    push ecx
    push eax
    call rt_concat                  ; rt_concat(a, b)
    add esp, 8
    push eax
    push fmt_print_str
    call _printf
    add esp, 8
    ; i = ...
    mov eax, dword [g_i_1]          ; i
    push eax
    mov eax, 1
    mov ecx, eax
    pop eax
    add eax, ecx
    mov dword [g_i_1], eax          ; i
    jmp L2
L3:
    xor eax, eax                    ; return 0
    mov esp, ebp
    pop ebp
    ret
