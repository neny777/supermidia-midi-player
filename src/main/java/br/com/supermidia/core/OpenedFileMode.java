package br.com.supermidia.core;

/**
 * O que fazer com um arquivo aberto de fora do programa, pelo Explorer.
 *
 * <p>São os dois usos reais: ouvir uma música avulsa, e montar o repertório clicando
 * nos arquivos um a um. Em ambos o arquivo começa a tocar se o Autoplay estiver ligado.</p>
 */
public enum OpenedFileMode {
    REPLACE("Substituir", "A playlist passa a ter só o arquivo aberto"),
    APPEND("Acrescentar", "O arquivo entra no fim da playlist e é preparado");

    private final String label;
    private final String description;

    OpenedFileMode(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /** Lê o modo guardado nas preferências; valor desconhecido volta ao padrão. */
    public static OpenedFileMode fromPreference(String stored) {
        if (stored != null) {
            for (OpenedFileMode mode : values()) {
                if (mode.name().equals(stored)) {
                    return mode;
                }
            }
        }
        return REPLACE;
    }

    @Override
    public String toString() {
        return label;
    }
}
