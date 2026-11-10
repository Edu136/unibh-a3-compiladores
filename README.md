# macsc — compilador MACSLang

Compilador da linguagem **MACSLang** escrito em Java. Lê um arquivo `.macs` e gera assembly x86 (NASM) para Windows.

Trabalho A3 — Teoria da Computação e Compiladores — UniBH.

## Fases

| Fase | Pacote | Situação |
|---|---|---|
| Análise léxica | `br.unibh.macs.lexer` | concluída |
| Análise sintática | `br.unibh.macs.parser` (árvore em `br.unibh.macs.ast`) | concluída |
| Análise semântica | `br.unibh.macs.semantic` | concluída |
| Geração de código | `br.unibh.macs.codegen` | — |

## Requisitos

- JDK 21
- Maven 3.9+

## Compilar

```bat
build.bat
```

Gera `target\macsc.jar` e roda os testes. O `build.bat` usa o JDK indicado em `MACSC_JDK`, se definido.

## Usar

```bat
macsc.bat programa.macs [--tokens] [--ast]
```

| Opção | Efeito |
|---|---|
| `--tokens` | imprime a lista de tokens (`linha:coluna  TIPO  lexema`) |
| `--ast` | imprime a árvore sintática anotada com os tipos |

Sem opções, o programa passa pelas análises léxica, sintática e semântica, e só os erros são mostrados.

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
- Operadores: `+ - * / % = == != < <= > >= && || !` e pontuação `( ) { } , ; :`
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
atribuicao = ID "=" expr ;
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
