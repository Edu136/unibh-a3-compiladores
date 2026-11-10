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
    private final StringBuilder constantes = new StringBuilder(); // .data do programa (floats, strings)
    private final Set<String> globais = new LinkedHashSet<>();
    private final java.util.Map<Long, String> floats = new java.util.HashMap<>();
    private final java.util.Map<String, String> strings = new java.util.HashMap<>();
    private int proximoRotulo = 0;

    // função sendo gerada (null = código global, em _main)
    private FuncDecl funcaoAtual = null;
    private int numParams = 0;

    private CodeGenerator(Resolucao resolucao) {
        this.resolucao = resolucao;
    }

    /** Gera o .asm completo. A árvore precisa vir do semântico sem erros. */
    public static String gerar(Program programa, Resolucao resolucao) {
        return new CodeGenerator(resolucao).executar(programa);
    }

    private String executar(Program programa) {
        // funções MACSLang
        for (Item item : programa.itens()) {
            if (item instanceof FuncDecl f) {
                funcao(f);
            }
        }

        // código global, na ordem do arquivo
        text.append("\n; ---------------- programa principal ----------------\n");
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
            }
        }
        emitir("xor eax, eax", "return 0");
        emitir("mov esp, ebp");
        emitir("pop ebp");
        emitir("ret");

        return montarArquivo();
    }

    // ---------- funções ----------

    /**
     * Quadro: [ebp+8+8*(n-1-i)] = parâmetro i (empilhados da esquerda para a direita),
     * [ebp-8*(k-n+1)] = local de slot k. Retorno em eax (ou xmm0 para float).
     */
    private void funcao(FuncDecl f) {
        funcaoAtual = f;
        numParams = f.params().size();
        int locais = contarDeclaracoes(f.corpo());

        String params = f.params().stream().map(p -> p.nome() + ": " + p.tipo().nome())
                .collect(java.util.stream.Collectors.joining(", "));
        text.append("\n; func ").append(f.nome()).append('(').append(params).append("): ")
                .append(f.retorno().nome()).append('\n');
        rotulo(rotuloFuncao(f.nome()));
        emitir("push ebp");
        emitir("mov ebp, esp");
        if (locais > 0) {
            emitir("sub esp, " + (8 * locais), locais + " variável(is) local(is) de 8 bytes");
        }
        stmt(f.corpo());
        rotulo(rotuloFim(f));
        emitir("mov esp, ebp");
        emitir("pop ebp");
        emitir("ret");

        funcaoAtual = null;
        numParams = 0;
    }

    /** Quantidade de declarações de variável no corpo (cada uma tem seu slot). */
    private static int contarDeclaracoes(Stmt s) {
        return switch (s) {
            case Stmt.VarDecl d -> 1;
            case Stmt.Block b -> b.stmts().stream().mapToInt(CodeGenerator::contarDeclaracoes).sum();
            case Stmt.If i -> contarDeclaracoes(i.entao()) + (i.senao() == null ? 0 : contarDeclaracoes(i.senao()));
            case Stmt.While w -> contarDeclaracoes(w.corpo());
            case Stmt.For f -> (f.init() == null ? 0 : contarDeclaracoes(f.init())) + contarDeclaracoes(f.corpo());
            default -> 0;
        };
    }

    private static String rotuloFuncao(String nome) {
        return "fn_" + nome;
    }

    /**
     * Rótulo do epílogo. Usa '@', que não existe em identificadores MACSLang: com "_fim", a
     * função f colidia com uma função chamada f_fim (fn_f_fim).
     */
    private static String rotuloFim(FuncDecl f) {
        return "fn_" + f.nome() + "@fim";
    }

    /** Argumentos da esquerda para a direita, 8 bytes cada; quem chama limpa a pilha. */
    private void chamada(Expr.Call c) {
        comentario("chamada " + c.nome() + "(" + c.args().size() + " argumento(s))");
        for (Expr arg : c.args()) {
            expr(arg);
            empilhar(arg.getType());
        }
        emitir("call " + rotuloFuncao(c.nome()));
        if (!c.args().isEmpty()) {
            emitir("add esp, " + (8 * c.args().size()), "descarta os argumentos");
        }
    }

    /** Empilha o valor atual (eax ou xmm0) ocupando 8 bytes. */
    private void empilhar(Type tipo) {
        emitir("sub esp, 8");
        if (tipo == Type.FLOAT) {
            emitir("movsd qword [esp], xmm0");
        } else {
            emitir("mov dword [esp], eax");
        }
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
            case Stmt.Input i -> ler(resolucao.variavel(i));
            case Stmt.Block b -> b.stmts().forEach(this::stmt);
            case Stmt.CallStmt c -> chamada(c.call()); // valor de retorno descartado
            case Stmt.Return r -> {
                comentario("return");
                expr(r.expr());
                emitir("jmp " + rotuloFim(funcaoAtual));
            }
            case Stmt.If i -> {
                String senao = novoRotulo();
                String fim = novoRotulo();
                comentario("if");
                expr(i.cond());
                emitir("test eax, eax");
                emitir("jz " + senao, "condição falsa");
                stmt(i.entao());
                emitir("jmp " + fim);
                rotulo(senao);
                if (i.senao() != null) {
                    stmt(i.senao());
                }
                rotulo(fim);
            }
            case Stmt.While w -> {
                String inicio = novoRotulo();
                String fim = novoRotulo();
                comentario("while");
                rotulo(inicio);
                expr(w.cond());
                emitir("test eax, eax");
                emitir("jz " + fim, "sai do laço");
                stmt(w.corpo());
                emitir("jmp " + inicio);
                rotulo(fim);
            }
            case Stmt.For f -> {
                String inicio = novoRotulo();
                String fim = novoRotulo();
                comentario("for");
                if (f.init() != null) {
                    stmt(f.init());
                }
                rotulo(inicio);
                if (f.cond() != null) {
                    expr(f.cond());
                    emitir("test eax, eax");
                    emitir("jz " + fim, "sai do laço");
                }
                stmt(f.corpo());
                if (f.passo() != null) {
                    stmt(f.passo());
                }
                emitir("jmp " + inicio);
                rotulo(fim);
            }
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
                emitir("call _printf");
                emitir("add esp, 8");
            }
            case FLOAT -> {
                emitir("sub esp, 8", "printf recebe o double inteiro (8 bytes)");
                emitir("movsd qword [esp], xmm0");
                emitir("push fmt_print_float");
                emitir("call _printf");
                emitir("add esp, 12");
            }
            case BOOL, CHAR, STRING -> {
                paraString(e.getType()); // bool vira "true"/"false"; char vira texto UTF-8
                emitir("push eax");
                emitir("push fmt_print_str");
                emitir("call _printf");
                emitir("add esp, 8");
            }
        }
    }

    /** input(x): a variável recebe o valor padrão antes (se a leitura falhar, fica com ele). */
    private void ler(VarSymbol s) {
        comentario("input " + s.nome() + " (" + s.tipo().nome() + ")");
        emitir("call rt_pular_bom", "ignora BOM no início da entrada (só na 1ª leitura)");
        guardarPadrao(s);
        if (s.tipo() == Type.STRING) {
            emitir("push 256");
            emitir("call _malloc", "buffer novo a cada leitura (não é liberado na v1)");
            emitir("add esp, 4");
            emitir("mov byte [eax], 0", "string vazia se a leitura falhar");
            emitir("mov dword [" + endereco(s) + "], eax");
            emitir("push eax");
            emitir("push fmt_scan_str", "\" %255[^\\n]\": pula o \\n anterior e lê a linha toda");
        } else {
            String formato = switch (s.tipo()) {
                case INT -> "fmt_scan_int";
                case FLOAT -> "fmt_scan_float";
                case CHAR -> "fmt_scan_char";
                default -> throw new IllegalStateException("input não aceita " + s.tipo()); // o semântico barra bool
            };
            emitir("lea eax, [" + endereco(s) + "]");
            emitir("push eax");
            emitir("push " + formato);
        }
        emitir("call _scanf");
        emitir("add esp, 8");
    }

    // ---------- expressões ----------

    private void expr(Expr expr) {
        switch (expr) {
            case Expr.IntLit i -> emitir("mov eax, " + i.valor());
            case Expr.BoolLit b -> emitir("mov eax, " + (b.valor() ? 1 : 0));
            case Expr.CharLit c -> emitir("mov eax, " + (int) c.valor(), "char (código Unicode)");
            case Expr.StringLit s -> emitir("mov eax, " + constanteString(s.valor()));
            case Expr.FloatLit f -> emitir("movsd xmm0, qword [" + constanteFloat(f.valor()) + "]", "" + f.valor());
            case Expr.Var v -> carregar(resolucao.variavel(v));
            case Expr.Convert c -> {
                expr(c.expr());
                emitir("cvtsi2sd xmm0, eax", "int -> float");
            }
            case Expr.Unary u -> {
                expr(u.operando());
                switch (u.op()) {
                    case NEG -> {
                        if (u.getType() == Type.FLOAT) {
                            emitir("movsd xmm1, xmm0");
                            emitir("xorpd xmm0, xmm0");
                            emitir("subsd xmm0, xmm1", "0.0 - x");
                        } else {
                            emitir("neg eax");
                        }
                    }
                    case NOT -> emitir("xor eax, 1", "!: 0 <-> 1");
                }
            }
            case Expr.Binary b -> binaria(b);
            case Expr.Call c -> chamada(c);
            default -> throw naoSuportado(expr);
        }
    }

    private void binaria(Expr.Binary b) {
        if (b.op() == BinaryOp.AND || b.op() == BinaryOp.OR) {
            logicaCurtoCircuito(b);
            return;
        }
        if (b.op() == BinaryOp.ADD && b.getType() == Type.STRING) {
            concatenacao(b);
            return;
        }
        if (b.esq().getType() == Type.FLOAT) { // o semântico garante: os dois lados são float
            binariaFloat(b);
            return;
        }
        // lado esquerdo na pilha, direito em ecx, esquerdo de volta em eax
        expr(b.esq());
        emitir("push eax");
        expr(b.dir());
        emitir("mov ecx, eax");
        emitir("pop eax");
        if (b.esq().getType() == Type.STRING) { // só == e != chegam aqui com strings
            emitir("push ecx");
            emitir("push eax");
            emitir("call _strcmp", "compara o conteúdo, não o endereço");
            emitir("add esp, 8");
            emitir("test eax, eax");
            emitir((b.op() == BinaryOp.EQ ? "sete" : "setne") + " al");
            emitir("movzx eax, al");
            return;
        }
        switch (b.op()) {
            case ADD -> emitir("add eax, ecx");
            case SUB -> emitir("sub eax, ecx");
            case MUL -> emitir("imul eax, ecx");
            case DIV, MOD -> {
                verificarDivisaoPorZero(b.linha());
                // divisor -1 tratado à parte: idiv de -2147483648 por -1 estoura (exceção de hardware).
                // Como em Java: x / -1 = -x (com estouro silencioso) e x % -1 = 0.
                String normal = novoRotulo();
                String fim = novoRotulo();
                emitir("cmp ecx, -1");
                emitir("jne " + normal);
                emitir(b.op() == BinaryOp.DIV ? "neg eax" : "xor eax, eax", "divisor -1");
                emitir("jmp " + fim);
                rotulo(normal);
                emitir("cdq", "estende o sinal de eax para edx");
                emitir("idiv ecx", "quociente em eax, resto em edx");
                if (b.op() == BinaryOp.MOD) {
                    emitir("mov eax, edx");
                }
                rotulo(fim);
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

    /** Operandos float: esquerdo na pilha (8 bytes), direito em xmm1, esquerdo de volta em xmm0. */
    private void binariaFloat(Expr.Binary b) {
        expr(b.esq());
        empilhar(Type.FLOAT);
        expr(b.dir());
        emitir("movsd xmm1, xmm0");
        emitir("movsd xmm0, qword [esp]");
        emitir("add esp, 8");
        switch (b.op()) {
            case ADD -> emitir("addsd xmm0, xmm1");
            case SUB -> emitir("subsd xmm0, xmm1");
            case MUL -> emitir("mulsd xmm0, xmm1");
            case DIV -> emitir("divsd xmm0, xmm1", "divisão por 0.0 dá infinito, sem erro");
            // comisd ajusta CF/ZF como uma comparação sem sinal; PF=1 quando "não ordenado" (NaN)
            case LT -> compararFloat("setb al");
            case LE -> compararFloat("setbe al");
            case GT -> compararFloat("seta al");
            case GE -> compararFloat("setae al");
            case EQ -> compararFloat("sete al", "setnp cl", "and al, cl");
            case NE -> compararFloat("setne al", "setp cl", "or al, cl");
            default -> throw naoSuportado(b);
        }
    }

    private void compararFloat(String... instrucoes) {
        emitir("comisd xmm0, xmm1");
        for (String i : instrucoes) {
            emitir(i);
        }
        emitir("movzx eax, al", "resultado bool: 0 ou 1");
    }

    /** Rótulo de uma constante double em .data (gravada pelos bits, sem arredondamento). */
    private String constanteFloat(double valor) {
        long bits = Double.doubleToRawLongBits(valor);
        return floats.computeIfAbsent(bits, b -> {
            String nome = "flt_" + floats.size();
            constantes.append(String.format("%-15s dq 0x%016X       ; %s%n", nome, b, valor));
            return nome;
        });
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

    /** {@code a + b} com string: cada lado vira texto e o runtime aloca a junção. */
    private void concatenacao(Expr.Binary b) {
        comentario("concatenação");
        expr(b.esq());
        paraString(b.esq().getType());
        emitir("push eax");
        expr(b.dir());
        paraString(b.dir().getType());
        emitir("mov ecx, eax", "b");
        emitir("pop eax", "a");
        emitir("push ecx");
        emitir("push eax");
        emitir("call rt_concat", "rt_concat(a, b)");
        emitir("add esp, 8");
    }

    /** Converte o valor atual (eax ou xmm0, do tipo dado) para string (ponteiro em eax). */
    private void paraString(Type tipo) {
        switch (tipo) {
            case STRING -> {
                // já é string
            }
            case INT, BOOL, CHAR -> {
                emitir("push eax");
                emitir("call " + switch (tipo) {
                    case INT -> "rt_str_int";
                    case BOOL -> "rt_str_bool";
                    default -> "rt_str_char";
                });
                emitir("add esp, 4");
            }
            case FLOAT -> {
                emitir("sub esp, 8");
                emitir("movsd qword [esp], xmm0");
                emitir("call rt_str_float");
                emitir("add esp, 8");
            }
        }
    }

    /** Rótulo de uma string literal em .data (UTF-8, terminada em 0). */
    private String constanteString(String valor) {
        if (valor.isEmpty()) {
            return "str_vazia";
        }
        return strings.computeIfAbsent(valor, v -> {
            String nome = "str_" + strings.size();
            constantes.append(String.format("%-15s db `%s`, 0%n", nome, escaparNasm(v)));
            return nome;
        });
    }

    /** Escapa para string NASM entre crases (que aceita \n, \t, \\, \` e \xNN). */
    private static String escaparNasm(String texto) {
        StringBuilder r = new StringBuilder();
        for (char c : texto.toCharArray()) {
            switch (c) {
                case '\\' -> r.append("\\\\");
                case '`' -> r.append("\\`");
                case '\n' -> r.append("\\n");
                case '\t' -> r.append("\\t");
                case '\r' -> r.append("\\r");
                default -> {
                    if (c < 0x20) {
                        r.append(String.format("\\x%02X", (int) c));
                    } else {
                        r.append(c); // acentos vão como UTF-8 (o .asm é gravado em UTF-8)
                    }
                }
            }
        }
        return r.toString();
    }

    // ---------- variáveis ----------

    /** Endereço da variável (sem o tamanho), ex.: {@code g_x_0} ou {@code ebp-8}. */
    private String endereco(VarSymbol s) {
        if (s.global()) {
            String nome = "g_" + s.nome() + "_" + s.slot();
            globais.add(nome);
            return nome;
        }
        if (s.slot() < numParams) {
            return "ebp+" + (8 + 8 * (numParams - 1 - s.slot())); // parâmetro
        }
        return "ebp-" + (8 * (s.slot() - numParams + 1)); // local
    }

    private void carregar(VarSymbol s) {
        if (s.tipo() == Type.FLOAT) {
            emitir("movsd xmm0, qword [" + endereco(s) + "]", s.nome());
        } else {
            emitir("mov eax, dword [" + endereco(s) + "]", s.nome());
        }
    }

    private void guardar(VarSymbol s) {
        if (s.tipo() == Type.FLOAT) {
            emitir("movsd qword [" + endereco(s) + "], xmm0", s.nome());
        } else {
            emitir("mov dword [" + endereco(s) + "], eax", s.nome());
        }
    }

    private void guardarPadrao(VarSymbol s) {
        String end = endereco(s);
        if (s.tipo() == Type.STRING) {
            emitir("mov dword [" + end + "], str_vazia", s.nome() + " = \"\"");
            return;
        }
        emitir("mov dword [" + end + "], 0", s.nome() + " = valor padrão");
        if (s.tipo() == Type.FLOAT) {
            emitir("mov dword [" + end + "+4], 0", "0.0 = 8 bytes zerados");
        }
    }

    // ---------- montagem do arquivo ----------

    private String montarArquivo() {
        StringBuilder asm = new StringBuilder();
        asm.append("; Gerado por macsc - MACSLang -> NASM x86 (32 bits, Windows)\n");
        asm.append("; Montar: nasm -f win32 prog.asm -o prog.obj\n");
        asm.append("; Ligar:  gcc -m32 prog.obj -o prog.exe\n\n");
        asm.append("global _main\n");
        asm.append("extern _printf, _scanf, _sprintf, _malloc, _strlen, _strcpy, _strcat, _strcmp, _exit\n");
        asm.append("extern _fgetc, _ungetc, ___iob_func\n");
        asm.append("extern _SetConsoleOutputCP@4, _SetConsoleCP@4\n\n");

        asm.append("section .data\n");
        asm.append(Runtime.DADOS).append(constantes).append('\n');

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
