package br.unibh.macs.codegen;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

class MontadorTest {

    @Test
    void nasmAusente() {
        Montador m = new Montador(List.of("nasm-que-nao-existe"), List.of("gcc"));
        Montador.FalhaMontagem f = assertThrows(Montador.FalhaMontagem.class, m::verificarFerramentas);
        assertEquals("nasm não encontrado no PATH (instale o NASM ou ajuste o PATH)", f.getMessage());
        assertTrue(f.ferramentaAusente());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void gccQueNaoEhI686(@TempDir Path dir) throws Exception {
        // ferramentas falsas: respondem sempre a mesma coisa, ignorando os argumentos
        Path nasm = dir.resolve("nasm.bat");
        Path gcc = dir.resolve("gcc.bat");
        Files.writeString(nasm, "@echo NASM version falsa\r\n");
        Files.writeString(gcc, "@echo x86_64-w64-mingw32\r\n");
        Montador m = new Montador(List.of(nasm.toString()), List.of(gcc.toString()));
        Montador.FalhaMontagem f = assertThrows(Montador.FalhaMontagem.class, m::verificarFerramentas);
        assertEquals("gcc não é i686 (encontrado: x86_64-w64-mingw32); é preciso o MinGW-w64 de 32 bits",
                f.getMessage());
        assertTrue(f.ferramentaAusente());
    }
}
