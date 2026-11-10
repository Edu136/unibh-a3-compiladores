package br.unibh.macs;

import br.unibh.macs.ast.AstPrinter;
import br.unibh.macs.ast.Program;
import br.unibh.macs.erro.CompileError;
import br.unibh.macs.lexer.Lexer;
import br.unibh.macs.lexer.Token;
import br.unibh.macs.parser.Parser;
import br.unibh.macs.semantic.Resultado;
import br.unibh.macs.semantic.SemanticAnalyzer;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

/** Ponto de entrada do compilador: {@code macsc <arquivo.macs> [--tokens] [--ast]}. */
public final class Main {

    static final int SUCESSO = 0;
    static final int ERRO_COMPILACAO = 1;
    static final int ERRO_USO = 2;

    private static final String USO = "uso: macsc <arquivo.macs> [--tokens] [--ast]";
    private static final char BOM = '﻿';
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private Main() {
    }

    /** Pilha da thread do compilador: as fases são recursivas (parser, semântico, impressão). */
    private static final long PILHA_BYTES = 64L * 1024 * 1024;

    public static void main(String[] args) throws InterruptedException {
        System.exit(executarComPilhaGrande(args, System.out, System.err));
    }

    /** Roda {@link #run} numa thread com pilha grande, para programas muito aninhados compilarem. */
    static int executarComPilhaGrande(String[] args, PrintStream out, PrintStream err) throws InterruptedException {
        int[] codigo = {ERRO_USO};
        Runnable compilar = () -> {
            try {
                codigo[0] = run(args, out, err);
            } catch (RuntimeException | Error e) {
                // defeito do próprio compilador: mensagem curta, sem stack trace para o usuário
                err.println("erro interno do compilador: " + e);
                codigo[0] = ERRO_COMPILACAO;
            }
        };
        Thread t = new Thread(null, compilar, "macsc", PILHA_BYTES);
        t.start();
        t.join();
        return codigo[0];
    }

    /** Executa o compilador e devolve o código de saída (sem chamar System.exit). */
    static int run(String[] args, PrintStream out, PrintStream err) {
        String arquivo = null;
        boolean mostrarTokens = false;
        boolean mostrarAst = false;

        for (String arg : args) {
            if (arg.equals("--tokens")) {
                mostrarTokens = true;
            } else if (arg.equals("--ast")) {
                mostrarAst = true;
            } else if (arg.startsWith("--")) {
                err.println("opção desconhecida: " + arg);
                err.println(USO);
                return ERRO_USO;
            } else if (arquivo == null) {
                arquivo = arg;
            } else {
                err.println(USO);
                return ERRO_USO;
            }
        }
        if (arquivo == null) {
            err.println(USO);
            return ERRO_USO;
        }

        String fonte;
        try {
            fonte = lerFonte(Path.of(arquivo));
        } catch (InvalidPathException e) {
            err.println("caminho de arquivo inválido: " + arquivo);
            return ERRO_USO;
        } catch (NoSuchFileException e) {
            err.println("arquivo não encontrado: " + arquivo);
            return ERRO_USO;
        } catch (IOException e) {
            err.println("não foi possível ler " + arquivo + ": " + e.getMessage());
            return ERRO_USO;
        }

        try {
            List<Token> tokens = Lexer.tokenizar(fonte);
            if (mostrarTokens) {
                imprimirTokens(tokens, out);
            }
            Program programa = Parser.analisar(tokens);
            Resultado semantico = SemanticAnalyzer.analisar(programa);
            if (semantico.temErros()) {
                for (CompileError erro : semantico.erros()) {
                    err.println(erro.formatar(arquivo));
                }
                return ERRO_COMPILACAO;
            }
            if (mostrarAst) {
                // println linha a linha: mesmo fim de linha do sistema que o --tokens
                AstPrinter.imprimir(semantico.programa()).lines().forEach(out::println);
            }
            return SUCESSO;
        } catch (CompileError e) {
            err.println(e.formatar(arquivo));
            return ERRO_COMPILACAO;
        } catch (StackOverflowError e) {
            // as fases são recursivas; o estouro pode ocorrer em qualquer uma delas (sem rótulo de fase)
            err.println(arquivo + ": erro: programa aninhado demais"
                    + " (expressões ou blocos aninhados em excesso)");
            return ERRO_COMPILACAO;
        } catch (OutOfMemoryError e) {
            // ex.: --ast de um programa com centenas de milhares de níveis (indentação gigante)
            err.println(arquivo + ": erro: programa grande demais para compilar");
            return ERRO_COMPILACAO;
        }
    }

    /**
     * Lê o arquivo como UTF-8 (descartando o BOM que alguns editores gravam no início).
     * Se os bytes não forem UTF-8 válido, lê como Windows-1252 ("ANSI"), o padrão de
     * vários editores no Windows.
     */
    private static String lerFonte(Path caminho) throws IOException {
        byte[] bytes = Files.readAllBytes(caminho);
        String texto;
        try {
            texto = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            texto = new String(bytes, WINDOWS_1252);
        }
        return !texto.isEmpty() && texto.charAt(0) == BOM ? texto.substring(1) : texto;
    }

    /** Um token por linha: {@code linha:coluna  TIPO  lexema}. */
    private static void imprimirTokens(List<Token> tokens, PrintStream out) {
        for (Token t : tokens) {
            String posicao = t.linha() + ":" + t.coluna();
            if (t.lexema().isEmpty()) {
                out.println(posicao + "  " + t.type());
            } else {
                out.println(posicao + "  " + t.type() + "  " + t.lexema());
            }
        }
    }
}
