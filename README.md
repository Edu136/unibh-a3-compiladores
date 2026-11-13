# macsc — compilador MACSLang

Compilador da linguagem **MACSLang** escrito em Java. Lê um arquivo `.macs` e gera assembly x86 (NASM) para Windows.

Trabalho A3 — Teoria da Computação e Compiladores — UniBH.

## Fases

| Fase | Pacote | Situação |
|---|---|---|
| Análise léxica | `br.unibh.macs.lexer` | concluída |
| Análise sintática | `br.unibh.macs.parser` (árvore em `br.unibh.macs.ast`) | concluída |
| Análise semântica | `br.unibh.macs.semantic` | concluída |
| Geração de código | `br.unibh.macs.codegen` | concluída |

## Requisitos

- JDK 21
- Maven 3.9+
- Para gerar o executável (`--exe`): [NASM](https://www.nasm.us/) e o `gcc` do **MinGW-w64 i686** (32 bits) no PATH. O `macsc.bat` e o `build.bat` usam automaticamente `C:\tools\nasm` e `C:\tools\mingw32\bin` se essas pastas existirem.

## Compilar

```bat
build.bat
```

Gera `target\macsc.jar` e roda os testes. O `build.bat` usa o JDK indicado em `MACSC_JDK`, se definido.

## Usar

```bat
macsc.bat programa.macs [--tokens] [--ast] [--exe]
```

| Opção | Efeito |
|---|---|
| `--tokens` | imprime a lista de tokens (`linha:coluna  TIPO  lexema`) |
| `--ast` | imprime a árvore sintática anotada com os tipos |
| `--exe` | além do `.asm`, monta e liga o executável `programa.exe` |

Se não houver erros, o compilador grava `programa.asm` (NASM, x86 32 bits) na mesma pasta do fonte. Com `--exe`, também gera `programa.exe`:

```bat
macsc.bat fatorial.macs --exe
fatorial.exe
```

O `macsc.bat` usa o Java indicado em `MACSC_JAVA`, se definido.

Exemplo:

```text
> macsc.bat exemplo.macs --tokens
1:1  VAR  var
1:5  ID  x
1:6  COLON  :
1:8  INT  int
1:12  ASSIGN  =
1:14  INT_LIT  42
1:16  SEMICOLON  ;
1:17  EOF
```

## Análise léxica

- Palavras-chave: `var func if else while for return print input true false int float char bool string`
- Identificadores: `[A-Za-z_][A-Za-z0-9_]*` (sem acentos)
- Literais: inteiros (`42`, até 2147483647), reais (`3.14`, com dígitos dos dois lados do ponto), caracteres (`'a'`) e strings (`"texto"`, sem quebra de linha)
- Escapes em char e string: `\n \t \\ \" \' \0`
- Operadores: `+ - * / % = == != < <= > >= && || !`, atalhos de atribuição `++ -- += -= *= /= %=` e pontuação `( ) { } , ; :`
- Vale o maior casamento: `++`, `--` e `+=` são um token só. Por isso `5--3` é erro; para subtrair um negativo, escreva `5 - -3` ou `5 - (-3)`.
- Comentários: `// linha` e `/* bloco */`
- O código-fonte é lido em UTF-8 (com ou sem BOM), ou em Windows-1252 se não for UTF-8 válido. O analisador para no primeiro erro.

## Análise sintática

Analisador por descida recursiva (um método por regra), seguindo a gramática:

```
programa   = { declFuncao | comando } EOF ;
declFuncao = "func" ID "(" [ parametro { "," parametro } ] ")" ":" tipo bloco ;
parametro  = ID ":" tipo ;
tipo       = "int" | "float" | "char" | "bool" | "string" ;
bloco      = "{" { comando } "}" ;
comando    = declVar ";" | atribuicao ";" | chamada ";" | cmdPrint | cmdInput
           | cmdIf | cmdWhile | cmdFor | cmdReturn | bloco ;
declVar    = "var" ID ":" tipo [ "=" expr ] ;
atribuicao = ID ( "=" expr | opComposto expr | "++" | "--" ) ;
opComposto = "+=" | "-=" | "*=" | "/=" | "%=" ;
cmdPrint   = "print" "(" expr ")" ";" ;
cmdInput   = "input" "(" ID ")" ";" ;
cmdIf      = "if" "(" expr ")" bloco [ "else" ( bloco | cmdIf ) ] ;
cmdWhile   = "while" "(" expr ")" bloco ;
cmdFor     = "for" "(" [ declVar | atribuicao ] ";" [ expr ] ";" [ atribuicao ] ")" bloco ;
cmdReturn  = "return" expr ";" ;
expr       = ou ;
ou         = e { "||" e } ;
e          = igualdade { "&&" igualdade } ;
igualdade  = relacional { ( "==" | "!=" ) relacional } ;
relacional = aditiva [ ( "<" | "<=" | ">" | ">=" ) aditiva ] ;
aditiva    = mult { ( "+" | "-" ) mult } ;
mult       = unaria { ( "*" | "/" | "%" ) unaria } ;
unaria     = ( "!" | "-" ) unaria | primaria ;
primaria   = INT_LIT | FLOAT_LIT | CHAR_LIT | STRING_LIT | "true" | "false"
           | ID | chamada | "(" expr ")" ;
chamada    = ID "(" [ expr { "," expr } ] ")" ;
```

- Os atalhos são traduzidos no sintático: `x++` vira `x = x + 1` e `x op= e` vira `x = x op (e)`; o semântico e o gerador não mudam.
- Os atalhos são comandos, não expressões: `++x`, `y = x++;` e `print(x++);` são erro.
- Comparações não podem ser encadeadas (`a < b < c` é erro; use `a < b && b < c`).
- Funções só podem ser declaradas no nível global; comandos também podem ficar fora de funções.
- Erros indicam o que era esperado e o que foi encontrado, por exemplo `prog.macs:3:15: erro sintático: esperado ';', encontrado 'print'`.

## Análise semântica

Verifica escopos e tipos, anota o tipo de cada expressão e insere as conversões implícitas. **Mostra todos os erros semânticos** (não só o primeiro), em ordem de posição.

**Escopos**
- Global → função → cada bloco `{ }` → cabeçalho do `for`. Uma variável só existe depois da declaração e até o fim do seu bloco.
- Um bloco interno pode declarar uma variável com o mesmo nome de uma externa (ela esconde a externa).
- Funções não acessam variáveis globais. Podem ser chamadas antes de declaradas (inclusive recursivamente).
- Funções e variáveis têm espaços de nomes separados.

**Tipos**

| Operação | Operandos | Resultado |
|---|---|---|
| `+ - * /` | int e int | int |
| `+ - * /` | int/float com pelo menos um float | float (o int é convertido) |
| `%` | int e int | int |
| `+` | um dos lados string (o outro de qualquer tipo) | string (concatenação) |
| `< <= > >=` | numéricos, ou char e char | bool |
| `== !=` | mesmo tipo (numéricos podem misturar) | bool |
| `&& \|\| !` | bool | bool |
| `-` unário | int ou float | o mesmo |

- Atribuição, inicialização, argumentos e `return` aceitam o mesmo tipo ou int → float. float → int é erro.
- Condições de `if`, `while` e `for` precisam ser `bool`. `input` não aceita variável `bool`.
- Toda função precisa terminar em `return` em todos os caminhos (um `if` só garante retorno se tiver `else` e os dois lados retornarem).
- Variáveis sem valor inicial começam com `0`, `0.0`, `'\0'`, `false` ou `""`.

Exemplo de `--ast` (árvore anotada):

```text
> macsc.bat exemplo.macs --ast
Program
  VarDecl f: float
    Convert float
      Binary + : int
        IntLit 1 : int
        IntLit 2 : int
```

Exemplo de erros:

```text
> macsc.bat erros.macs
erros.macs:5:1: erro semântico: função 'dobro' pode terminar sem return
erros.macs:8:1: erro semântico: atribuição incompatível: 'total' é int, valor é float
erros.macs:10:14: erro semântico: variável 'desconhecida' não declarada
erros.macs:11:5: erro semântico: condição do if deve ser bool, encontrado int
```

### Códigos de saída

| Código | Significado |
|---|---|
| 0 | sucesso |
| 1 | erro de compilação (léxico, sintático ou semântico) |
| 2 | erro de uso ou de arquivo |

Erros são impressos como `arquivo:linha:coluna: erro <fase>: mensagem`.

## Geração de código

Gera assembly NASM x86 (32 bits) para Windows, ligado com a biblioteca C do Windows (msvcrt). Sem código intermediário e sem otimização.

**Modelo**
- O resultado de cada expressão fica em `eax` (int, bool, char e ponteiro de string) ou em `xmm0` (float, como double em SSE2). Nas operações binárias, o lado esquerdo é guardado na pilha enquanto o direito é calculado.
- Cada variável ocupa 8 bytes: as globais em `.bss` (`g_nome_slot`), e os parâmetros e as locais na pilha (`[ebp+...]` / `[ebp-...]`).
- Funções (`fn_nome`): os argumentos são calculados e empilhados da esquerda para a direita, 8 bytes cada, e quem chama limpa a pilha. O retorno vem em `eax`, ou em `xmm0` para float.
- `char` guarda o código Unicode e é impresso em UTF-8. `bool` é 0/1 e é impresso como `true`/`false`.
- Strings são `char*` terminados em 0. A concatenação aloca memória nova (`malloc`), que não é liberada.

**Execução**
- `print` imprime o valor e pula linha. Float sai com 6 casas (`2.500000`).
- `input` lê do teclado. Para string, lê a linha inteira, com espaços e acentos. Para char, lê um caractere ASCII. Se a leitura falhar, a variável fica com o valor padrão.
- `&&` e `||` só avaliam o lado direito quando é preciso.
- `/` e `%` entre inteiros seguem C e Java: truncam em direção a zero, e o resto tem o sinal do dividendo. Divisão ou resto por zero interrompe o programa com `erro de execução: divisão por zero (linha L)` e código 1.
- Inteiros têm 32 bits e estouram em silêncio, como em C e Java: `2147483647 + 1` dá `-2147483648`.

Exemplo, com o fatorial e a entrada `5`:

```text
> macsc.bat fatorial.macs --exe
> fatorial.exe
Digite um número: 
5
Fatorial de 1 = 1
Fatorial de 2 = 2
Fatorial de 3 = 6
Fatorial de 4 = 24
Fatorial de 5 = 120
```
