# macsc — compilador MACSLang

Compilador da linguagem **MACSLang** escrito em Java. Lê um arquivo `.macs` e gera assembly x86 (NASM) para Windows.

Trabalho A3 — Teoria da Computação e Compiladores — UniBH.

## Fases

| Fase | Pacote | Situação |
|---|---|---|
| Análise léxica | `br.unibh.macs.lexer` | concluída |
| Análise sintática | `br.unibh.macs.parser` | — |
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
macsc.bat programa.macs [--tokens]
```

| Opção | Efeito |
|---|---|
| `--tokens` | imprime a lista de tokens (`linha:coluna  TIPO  lexema`) |

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
- O código-fonte é lido em UTF-8 (com ou sem BOM). O analisador para no primeiro erro.

### Códigos de saída

| Código | Significado |
|---|---|
| 0 | sucesso |
| 1 | erro de compilação (léxico, sintático ou semântico) |
| 2 | erro de uso ou de arquivo |

Erros são impressos como `arquivo:linha:coluna: erro <fase>: mensagem`.
