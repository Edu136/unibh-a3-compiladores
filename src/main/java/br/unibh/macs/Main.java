package br.unibh.macs;

import br.unibh.macs.ast.AstPrinter;
import br.unibh.macs.ast.Program;
import br.unibh.macs.erro.CompileError;
import br.unibh.macs.lexer.Lexer;
import br.unibh.macs.lexer.Token;
import br.unibh.macs.parser.Parser;
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

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
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
            if (mostrarAst) {
                out.print(AstPrinter.imprimir(programa));
            }
            return SUCESSO;
        } catch (CompileError e) {
            err.println(e.formatar(arquivo));
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
