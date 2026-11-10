package br.unibh.macs.codegen;

import br.unibh.macs.ast.Expr;
import br.unibh.macs.ast.FuncDecl;
import br.unibh.macs.ast.Item;
import br.unibh.macs.ast.Program;
import br.unibh.macs.ast.Stmt;
import br.unibh.macs.semantic.Resolucao;

/**
 * Gerador de código: transforma a árvore anotada pelo semântico em assembly NASM
 * x86 de 32 bits para Windows.
 *
 * <p>Modelo: toda expressão deixa o resultado em {@code eax} (int, bool, char, ponteiro de
 * string) ou {@code xmm0} (float). Não há otimização (fora do escopo do enunciado).
 */
public final class CodeGenerator {

    private final Resolucao resolucao;
    private final StringBuilder data = new StringBuilder();
    private final StringBuilder text = new StringBuilder();

    private CodeGenerator(Resolucao resolucao) {
        this.resolucao = resolucao;
    }

    /** Gera o .asm completo. A árvore precisa vir do semântico sem erros. */
    public static String gerar(Program programa, Resolucao resolucao) {
        return new CodeGenerator(resolucao).executar(programa);
    }

    private String executar(Program programa) {
        data.append("fmt_int     db `%d\\n`, 0\n");

        rotulo("_main");
        emitir("push ebp");
        emitir("mov ebp, esp");
        emitir("push 65001", "console em UTF-8 (saída)");
        emitir("call _SetConsoleOutputCP@4", "stdcall: a função limpa a pilha");
        emitir("push 65001", "console em UTF-8 (entrada)");
        emitir("call _SetConsoleCP@4");
        for (Item item : programa.itens()) {
            if (item instanceof Stmt s) {
                stmt(s);
            } else {
                throw naoSuportado((FuncDecl) item);
            }
        }
        emitir("xor eax, eax", "return 0");
        emitir("mov esp, ebp");
        emitir("pop ebp");
        emitir("ret");

        return montarArquivo();
    }

    // ---------- comandos ----------

    private void stmt(Stmt stmt) {
        switch (stmt) {
            case Stmt.Print p -> {
                expr(p.expr());
                emitir("push eax");
                emitir("push fmt_int");
                emitir("call _printf");
                emitir("add esp, 8");
            }
            default -> throw naoSuportado(stmt);
        }
    }

    // ---------- expressões ----------

    private void expr(Expr expr) {
        switch (expr) {
            case Expr.IntLit i -> emitir("mov eax, " + i.valor());
            default -> throw naoSuportado(expr);
        }
    }

    // ---------- montagem do arquivo ----------

    private String montarArquivo() {
        StringBuilder asm = new StringBuilder();
        asm.append("; Gerado por macsc - MACSLang -> NASM x86 (32 bits, Windows)\n");
        asm.append("; Montar: nasm -f win32 prog.asm -o prog.obj\n");
        asm.append("; Ligar:  gcc -m32 prog.obj -o prog.exe\n\n");
        asm.append("global _main\n");
        asm.append("extern _printf, _SetConsoleOutputCP@4, _SetConsoleCP@4\n\n");
        asm.append("section .data\n").append(data).append('\n');
        asm.append("section .text\n").append(text);
        return asm.toString();
    }

    private void rotulo(String nome) {
        text.append(nome).append(":\n");
    }

    private void emitir(String instrucao) {
        text.append("    ").append(instrucao).append('\n');
    }

    private void emitir(String instrucao, String comentario) {
        text.append("    ").append(String.format("%-32s; %s", instrucao, comentario)).append('\n');
    }

    private static IllegalStateException naoSuportado(Object no) {
        return new IllegalStateException("gerador de código: construção ainda não suportada: "
                + no.getClass().getSimpleName());
    }
}
