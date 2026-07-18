package br.com.supermidia.lyrics;

import java.util.Objects;

public record LyricLine(long tick, String text) {
    public LyricLine {
        if (tick < 0) {
            throw new IllegalArgumentException("O tick da letra não pode ser negativo");
        }
        Objects.requireNonNull(text, "text");
        if (text.isBlank()) {
            throw new IllegalArgumentException("A linha da letra não pode estar vazia");
        }
    }

    @Override
    public String toString() {
        return text;
    }
}
