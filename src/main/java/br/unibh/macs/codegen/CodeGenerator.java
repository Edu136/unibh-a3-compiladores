package br.unibh.macs.codegen;

import br.unibh.macs.ast.BinaryOp;
import br.unibh.macs.ast.Expr;
import br.unibh.macs.ast.FuncDecl;
import br.unibh.macs.ast.Item;
import br.unibh.macs.ast.Program;
import br.unibh.macs.ast.Stmt;
import br.unibh.macs.ast.Type;
import br.unibh.macs.semantic.Resolucao;
import br.unibh.macs.semantic.VarSymbol;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Gerador de código: transforma a árvore anotada pelo semântico em assembly NASM
 * x86 de 32 bits para Windows.
 *
 * <p>Modelo (ADR-005): toda expressão deixa o resultado em {@code eax} (int, bool, char,
 * ponteiro de string) ou {@code xmm0} (float). Nas operações binárias o lado esquerdo é
 * guardado na pilha enquanto o direito é avaliado. Cada variável ocupa 8 bytes.
 * Não há otimização (fora do escopo do enunciado).
 */
public final class CodeGenerator {

    private final Resolucao resolucao;
    private final StringBuilder text = new StringBuilder();
    private final Set<String> globais = new LinkedHashSet<>();
    private int proximoRotulo = 0;

    private CodeGenerator(Resolucao resolucao) {
        this.resolucao = resolucao;
    }

    /** Gera o .asm completo. A árvore precisa vir do semântico sem erros. */
    public static String gerar(Program programa, Resolucao resolucao) {
        return new CodeGenerator(resolucao).executar(programa);
    }

    private String executar(Program programa) {
        rotulo("_main");
        emitir("push ebp");
        emitir("mov ebp, esp");
        emitir("push 65001", "console em UTF-8 (saída)");
        emitir("call _SetConsoleOutputCP@4", "stdcall: a própria função limpa a pilha");
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
            case Stmt.VarDecl d -> {
                VarSymbol s = resolucao.variavel(d);
                comentario("var " + d.nome() + ": " + d.tipo().nome());
                if (d.init() != null) {
                    expr(d.init());
                    guardar(s);
                } else {
                    guardarPadrao(s);
                }
            }
            case Stmt.Assign a -> {
                comentario(a.nome() + " = ...");
                expr(a.valor());
                guardar(resolucao.variavel(a));
            }
            case Stmt.Print p -> imprimir(p.expr());
            default -> throw naoSuportado(stmt);
        }
    }

    private void imprimir(Expr e) {
        comentario("print (" + e.getType().nome() + ")");
        expr(e);
        switch (e.getType()) {
            case INT -> {
                emitir("push eax");
                emitir("push fmt_print_int");
            }
            case BOOL -> {
                paraString(Type.BOOL);
                emitir("push eax");
                emitir("push fmt_print_str");
            }
            default -> throw naoSuportado(e);
        }
        emitir("call _printf");
        emitir("add esp, 8");
    }

    // ---------- expressões ----------

    private void expr(Expr expr) {
        switch (expr) {
            case Expr.IntLit i -> emitir("mov eax, " + i.valor());
            case Expr.BoolLit b -> emitir("mov eax, " + (b.valor() ? 1 : 0));
            case Expr.Var v -> carregar(resolucao.variavel(v));
            case Expr.Unary u -> {
                expr(u.operando());
                switch (u.op()) {
                    case NEG -> emitir("neg eax");
                    case NOT -> emitir("xor eax, 1", "!: 0 <-> 1");
                }
            }
            case Expr.Binary b -> binaria(b);
            default -> throw naoSuportado(expr);
        }
    }

    private void binaria(Expr.Binary b) {
        if (b.op() == BinaryOp.AND || b.op() == BinaryOp.OR) {
            logicaCurtoCircuito(b);
            return;
        }
        // lado esquerdo na pilha, direito em ecx, esquerdo de volta em eax
        expr(b.esq());
        emitir("push eax");
        expr(b.dir());
        emitir("mov ecx, eax");
        emitir("pop eax");
        switch (b.op()) {
            case ADD -> emitir("add eax, ecx");
            case SUB -> emitir("sub eax, ecx");
            case MUL -> emitir("imul eax, ecx");
            case DIV, MOD -> {
                verificarDivisaoPorZero(b.linha());
                emitir("cdq", "estende o sinal de eax para edx");
                emitir("idiv ecx", "quociente em eax, resto em edx");
                if (b.op() == BinaryOp.MOD) {
                    emitir("mov eax, edx");
                }
            }
            case EQ -> comparar("sete");
            case NE -> comparar("setne");
            case LT -> comparar("setl");
            case LE -> comparar("setle");
            case GT -> comparar("setg");
            case GE -> comparar("setge");
            default -> throw naoSuportado(b);
        }
    }

    private void comparar(String setcc) {
        emitir("cmp eax, ecx");
        emitir(setcc + " al");
        emitir("movzx eax, al", "resultado bool: 0 ou 1");
    }

    /** {@code a && b}: se a for falso, b nem é avaliado; {@code a || b}: se a for verdadeiro, idem. */
    private void logicaCurtoCircuito(Expr.Binary b) {
        String fim = novoRotulo();
        expr(b.esq());
        emitir("test eax, eax");
        emitir((b.op() == BinaryOp.AND ? "jz " : "jnz ") + fim, "curto-circuito");
        expr(b.dir());
        rotulo(fim);
    }

    private void verificarDivisaoPorZero(int linha) {
        String ok = novoRotulo();
        emitir("test ecx, ecx");
        emitir("jnz " + ok);
        emitir("push " + linha);
        emitir("call rt_div_zero", "não retorna");
        rotulo(ok);
    }

    /** Converte o valor em eax (do tipo dado) para string (ponteiro em eax). */
    private void paraString(Type tipo) {
        switch (tipo) {
            case BOOL -> {
                emitir("push eax");
                emitir("call rt_str_bool");
                emitir("add esp, 4");
            }
            default -> throw new IllegalStateException("conversão para string ainda não suportada: " + tipo);
        }
    }

    // ---------- variáveis ----------

    /** Endereço da variável (sem o tamanho), ex.: {@code g_x_0} ou {@code ebp-8}. */
    private String endereco(VarSymbol s) {
        if (s.global()) {
            String nome = "g_" + s.nome() + "_" + s.slot();
            globais.add(nome);
            return nome;
        }
        throw new IllegalStateException("variáveis locais ainda não suportadas: " + s.nome());
    }

    private void carregar(VarSymbol s) {
        emitir("mov eax, dword [" + endereco(s) + "]", s.nome());
    }

    private void guardar(VarSymbol s) {
        emitir("mov dword [" + endereco(s) + "], eax", s.nome());
    }

    private void guardarPadrao(VarSymbol s) {
        emitir("mov dword [" + endereco(s) + "], 0", s.nome() + " = valor padrão");
    }

    // ---------- montagem do arquivo ----------

    private String montarArquivo() {
        StringBuilder asm = new StringBuilder();
        asm.append("; Gerado por macsc - MACSLang -> NASM x86 (32 bits, Windows)\n");
        asm.append("; Montar: nasm -f win32 prog.asm -o prog.obj\n");
        asm.append("; Ligar:  gcc -m32 prog.obj -o prog.exe\n\n");
        asm.append("global _main\n");
        asm.append("extern _printf, _exit, _SetConsoleOutputCP@4, _SetConsoleCP@4\n\n");

        asm.append("section .data\n");
        asm.append(Runtime.DADOS).append('\n');

        asm.append("section .bss\n");
        for (String g : globais) {
            asm.append(g).append(": resq 1\n");
        }
        asm.append('\n');

        asm.append("section .text\n");
        asm.append(Runtime.CODIGO).append('\n');
        asm.append(text);
        return asm.toString();
    }

    private String novoRotulo() {
        return "L" + (proximoRotulo++);
    }

    private void rotulo(String nome) {
        text.append(nome).append(":\n");
    }

    private void comentario(String texto) {
        text.append("    ; ").append(texto).append('\n');
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
