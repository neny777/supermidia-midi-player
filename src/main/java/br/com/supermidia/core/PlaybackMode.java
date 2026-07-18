package br.com.supermidia.core;

public enum PlaybackMode {
    MANUAL("Manual", "A próxima música ficará preparada"),
    AUTOMATIC("Automático", "A próxima música iniciará ao final");

    private final String label;
    private final String description;

    PlaybackMode(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public PlaybackMode toggle() {
        return this == MANUAL ? AUTOMATIC : MANUAL;
    }
}
