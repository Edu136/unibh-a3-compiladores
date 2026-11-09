# macsc — compilador MACSLang

Compilador da linguagem **MACSLang** escrito em Java. Lê um arquivo `.macs` e gera assembly x86 (NASM) para Windows.

Trabalho A3 — Teoria da Computação e Compiladores — UniBH.

## Fases

| Fase | Pacote | Situação |
|---|---|---|
| Análise léxica | `br.unibh.macs.lexer` | concluída |
| Análise sintática | `br.unibh.macs.parser` (árvore em `br.unibh.macs.ast`) | concluída |
| Análise semântica | `br.unibh.macs.semantic` | — |
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
| `--ast` | imprime a árvore sintática |

Sem opções, o programa passa pelas análises léxica e sintática, e só os erros são mostrados.

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

Exemplo de `--ast`:

```text
> macsc.bat exemplo.macs --ast
Program
  VarDecl x: int
    Binary +
      IntLit 1
      Binary *
        IntLit 2
        IntLit 3
```

- Comparações não podem ser encadeadas (`a < b < c` é erro; use `a < b && b < c`).
- Funções só podem ser declaradas no nível global; comandos também podem ficar fora de funções.
- Erros indicam o que era esperado e o que foi encontrado, por exemplo `prog.macs:3:15: erro sintático: esperado ';', encontrado 'print'`.

### Códigos de saída

| Código | Significado |
|---|---|
| 0 | sucesso |
| 1 | erro de compilação (léxico, sintático ou semântico) |
| 2 | erro de uso ou de arquivo |

Erros são impressos como `arquivo:linha:coluna: erro <fase>: mensagem`.
