package br.unibh.macs.semantic;

import br.unibh.macs.ast.BinaryOp;
import br.unibh.macs.ast.Expr;
import br.unibh.macs.ast.FuncDecl;
import br.unibh.macs.ast.Item;
import br.unibh.macs.ast.Program;
import br.unibh.macs.ast.Stmt;
import br.unibh.macs.ast.Type;
import br.unibh.macs.erro.CompileError;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Analisador semântico: verifica escopos e tipos, anota o tipo de cada expressão, insere as
 * conversões int→float e liga cada nome ao seu símbolo.
 *
 * <p>Não para no primeiro erro: acumula todos. Uma expressão com erro fica com tipo null e
 * não gera novos erros (sem cascata).
 *
 * <p>Devolve um {@link Program} <b>novo</b>: os comandos são reconstruídos com as expressões
 * já analisadas (a árvore é imutável; só o tipo das expressões é preenchido).
 */
public final class SemanticAnalyzer {

    private final SymbolTable tabela = new SymbolTable();
    private final Resolucao resolucao = new Resolucao();
    private final List<CompileError> erros = new ArrayList<>();

    private int proximoSlotGlobal = 0;

    private SemanticAnalyzer() {
    }

    public static Resultado analisar(Program programa) {
        return new SemanticAnalyzer().executar(programa);
    }

    private Resultado executar(Program programa) {
        tabela.abrir(); // escopo global
        List<Item> itens = new ArrayList<>();
        for (Item item : programa.itens()) {
            itens.add(switch (item) {
                case FuncDecl f -> f;
                case Stmt s -> stmt(s);
            });
        }
        tabela.fechar();

        erros.sort(Comparator.comparingInt(CompileError::getLinha).thenComparingInt(CompileError::getColuna));
        return new Resultado(new Program(itens), resolucao, List.copyOf(erros));
    }

    // ---------- comandos ----------

    private Stmt stmt(Stmt stmt) {
        return switch (stmt) {
            case Stmt.VarDecl d -> declaracao(d);
            case Stmt.Assign a -> atribuicao(a);
            case Stmt.Print p -> new Stmt.Print(expr(p.expr()), p.linha(), p.coluna());
            case Stmt.Input i -> {
                VarSymbol s = resolver(i.nome(), i.linha(), i.coluna());
                Stmt.Input novo = new Stmt.Input(i.nome(), i.linha(), i.coluna());
                registrar(novo, s);
                yield novo;
            }
            case Stmt.If i -> new Stmt.If(expr(i.cond()), bloco(i.entao()),
                    i.senao() == null ? null : stmt(i.senao()), i.linha(), i.coluna());
            case Stmt.While w -> new Stmt.While(expr(w.cond()), bloco(w.corpo()), w.linha(), w.coluna());
            case Stmt.For f -> {
                tabela.abrir(); // a variável do init vale no cabeçalho e no corpo
                Stmt init = f.init() == null ? null : stmt(f.init());
                Expr cond = f.cond() == null ? null : expr(f.cond());
                Stmt.Assign passo = f.passo() == null ? null : atribuicao(f.passo());
                Stmt.Block corpo = bloco(f.corpo());
                tabela.fechar();
                yield new Stmt.For(init, cond, passo, corpo, f.linha(), f.coluna());
            }
            case Stmt.Block b -> bloco(b);
            case Stmt.CallStmt c -> c;
            case Stmt.Return r -> r;
        };
    }

    private Stmt.Block bloco(Stmt.Block b) {
        tabela.abrir();
        List<Stmt> stmts = new ArrayList<>();
        for (Stmt s : b.stmts()) {
            stmts.add(stmt(s));
        }
        tabela.fechar();
        return new Stmt.Block(stmts, b.linha(), b.coluna());
    }

    private Stmt.VarDecl declaracao(Stmt.VarDecl d) {
        // o inicializador é analisado antes de declarar: "var x: int = x;" é erro
        Expr init = null;
        if (d.init() != null) {
            init = compativel(expr(d.init()), d.tipo(), d.linha(), d.coluna(),
                    t -> "inicialização incompatível: '" + d.nome() + "' é " + d.tipo().nome() + ", valor é " + t.nome());
        }
        Stmt.VarDecl nova = new Stmt.VarDecl(d.nome(), d.tipo(), init, d.linha(), d.coluna());
        if (tabela.declaradaNoEscopoAtual(d.nome())) {
            erro(d.linha(), d.coluna(), "variável '" + d.nome() + "' já declarada neste escopo");
            return nova;
        }
        VarSymbol s = new VarSymbol(d.nome(), d.tipo(), true, proximoSlotGlobal++);
        tabela.declarar(s);
        registrar(nova, s);
        return nova;
    }

    private Stmt.Assign atribuicao(Stmt.Assign a) {
        VarSymbol s = resolver(a.nome(), a.linha(), a.coluna());
        Expr valor = expr(a.valor());
        if (s != null) {
            valor = compativel(valor, s.tipo(), a.linha(), a.coluna(),
                    t -> "atribuição incompatível: '" + a.nome() + "' é " + s.tipo().nome() + ", valor é " + t.nome());
        }
        Stmt.Assign nova = new Stmt.Assign(a.nome(), valor, a.linha(), a.coluna());
        registrar(nova, s);
        return nova;
    }

    // ---------- expressões ----------

    private Expr expr(Expr expr) {
        return switch (expr) {
            case Expr.IntLit i -> tipar(i, Type.INT);
            case Expr.FloatLit f -> tipar(f, Type.FLOAT);
            case Expr.CharLit c -> tipar(c, Type.CHAR);
            case Expr.StringLit s -> tipar(s, Type.STRING);
            case Expr.BoolLit b -> tipar(b, Type.BOOL);
            case Expr.Var v -> {
                VarSymbol s = resolver(v.nome(), v.linha(), v.coluna());
                if (s != null) {
                    v.setType(s.tipo());
                    registrar(v, s);
                }
                yield v;
            }
            case Expr.Unary u -> unaria(u);
            case Expr.Binary b -> binaria(b);
            case Expr.Convert c -> c;
            case Expr.Call c -> c;
        };
    }

    private Expr unaria(Expr.Unary u) {
        Expr operando = expr(u.operando());
        Expr.Unary nova = new Expr.Unary(u.op(), operando, u.linha(), u.coluna());
        Type t = operando.getType();
        if (t == null) {
            return nova; // erro já reportado no operando
        }
        boolean valido = switch (u.op()) {
            case NEG -> ehNumerico(t);
            case NOT -> t == Type.BOOL;
        };
        if (valido) {
            nova.setType(t);
        } else {
            erro(u.linha(), u.coluna(), "operador '" + u.op().simbolo() + "' não se aplica a " + t.nome());
        }
        return nova;
    }

    /** Aplica a tabela de tipos dos operadores binários (D2), promovendo int→float quando preciso. */
    private Expr binaria(Expr.Binary b) {
        Expr esq = expr(b.esq());
        Expr dir = expr(b.dir());
        Type te = esq.getType();
        Type td = dir.getType();
        if (te == null || td == null) {
            return new Expr.Binary(b.op(), esq, dir, b.linha(), b.coluna()); // sem cascata
        }

        Type resultado = null;
        boolean promover = false; // numéricos mistos: o lado int vira float

        switch (b.op()) {
            case ADD, SUB, MUL, DIV -> {
                if (b.op() == BinaryOp.ADD && (te == Type.STRING || td == Type.STRING)) {
                    resultado = Type.STRING; // concatenação aceita qualquer tipo
                } else if (ehNumerico(te) && ehNumerico(td)) {
                    resultado = (te == Type.INT && td == Type.INT) ? Type.INT : Type.FLOAT;
                    promover = true;
                }
            }
            case MOD -> {
                if (te == Type.INT && td == Type.INT) {
                    resultado = Type.INT;
                }
            }
            case LT, LE, GT, GE -> {
                if (ehNumerico(te) && ehNumerico(td)) {
                    resultado = Type.BOOL;
                    promover = true;
                } else if (te == Type.CHAR && td == Type.CHAR) {
                    resultado = Type.BOOL;
                }
            }
            case EQ, NE -> {
                if (ehNumerico(te) && ehNumerico(td)) {
                    resultado = Type.BOOL;
                    promover = true;
                } else if (te == td) {
                    resultado = Type.BOOL;
                }
            }
            case AND, OR -> {
                if (te == Type.BOOL && td == Type.BOOL) {
                    resultado = Type.BOOL;
                }
            }
        }

        if (resultado == null) {
            erro(b.linha(), b.coluna(), "operador '" + b.op().simbolo() + "' não se aplica a "
                    + te.nome() + " e " + td.nome());
            return new Expr.Binary(b.op(), esq, dir, b.linha(), b.coluna());
        }
        if (promover && te != td) {
            esq = promoverParaFloat(esq);
            dir = promoverParaFloat(dir);
        }
        Expr.Binary nova = new Expr.Binary(b.op(), esq, dir, b.linha(), b.coluna());
        nova.setType(resultado);
        return nova;
    }

    // ---------- regras auxiliares ----------

    /**
     * Verifica se o valor pode ir para um destino do tipo dado (igual, ou int→float com Convert).
     * Se não puder, registra o erro com a mensagem montada a partir do tipo do valor.
     */
    private Expr compativel(Expr valor, Type destino, int linha, int coluna, Function<Type, String> mensagem) {
        Type t = valor.getType();
        if (t == null || t == destino) {
            return valor;
        }
        if (destino == Type.FLOAT && t == Type.INT) {
            return new Expr.Convert(valor, Type.FLOAT);
        }
        erro(linha, coluna, mensagem.apply(t));
        return valor;
    }

    private static Expr promoverParaFloat(Expr e) {
        return e.getType() == Type.INT ? new Expr.Convert(e, Type.FLOAT) : e;
    }

    private static boolean ehNumerico(Type t) {
        return t == Type.INT || t == Type.FLOAT;
    }

    private static Expr tipar(Expr e, Type t) {
        e.setType(t);
        return e;
    }

    private VarSymbol resolver(String nome, int linha, int coluna) {
        VarSymbol s = tabela.buscar(nome);
        if (s == null) {
            erro(linha, coluna, "variável '" + nome + "' não declarada");
        }
        return s;
    }

    private void registrar(Object no, VarSymbol simbolo) {
        if (simbolo != null) {
            resolucao.registrar(no, simbolo);
        }
    }

    private void erro(int linha, int coluna, String mensagem) {
        erros.add(new CompileError(CompileError.Fase.SEMANTICO, linha, coluna, mensagem));
    }
}
