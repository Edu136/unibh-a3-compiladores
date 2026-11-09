# macsc — compilador MACSLang

Compilador da linguagem **MACSLang** escrito em Java. Lê um arquivo `.macs` e gera assembly x86 (NASM) para Windows.

Trabalho A3 — Teoria da Computação e Compiladores — UniBH.

## Fases

| Fase | Pacote | Situação |
|---|---|---|
| Análise léxica | `br.unibh.macs.lexer` | em desenvolvimento |
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

### Códigos de saída

| Código | Significado |
|---|---|
| 0 | sucesso |
| 1 | erro de compilação (léxico, sintático ou semântico) |
| 2 | erro de uso ou de arquivo |

Erros são impressos como `arquivo:linha:coluna: erro <fase>: mensagem`.
