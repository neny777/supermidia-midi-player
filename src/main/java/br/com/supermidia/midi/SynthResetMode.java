package br.com.supermidia.midi;

/**
 * Reinicialização enviada ao sintetizador antes de cada música.
 *
 * <p>A escolha certa depende do aparelho e do repertório, e não há resposta única. Um
 * módulo Roland pode ignorar o reset genérico e responder só ao GS; um Yamaha, só ao XG.
 * Muitos arquivos, por outro lado, já se inicializam sozinhos — mandam o próprio reset
 * no primeiro segundo — e nesses casos um reset a mais atropela o do arquivo.</p>
 *
 * <p>Por isso a opção fica com o usuário, em Configurações, em vez de fixa no código:
 * só quem tem o equipamento na mão pode dizer qual combinação funciona.</p>
 */
public enum SynthResetMode {
    /** Não envia reset. Deixa a inicialização inteiramente para o arquivo. */
    NONE("Nenhum — o arquivo se inicializa", null),

    /** Reset universal, entendido pela maioria dos aparelhos. */
    GENERAL_MIDI("General MIDI",
            new byte[] {(byte) 0xF0, 0x7E, 0x7F, 0x09, 0x01, (byte) 0xF7}),

    /** Reset da família Roland GS — VIMA, Sound Canvas e afins. */
    ROLAND_GS("Roland GS",
            new byte[] {(byte) 0xF0, 0x41, 0x10, 0x42, 0x12, 0x40, 0x00, 0x7F,
                    0x00, 0x41, (byte) 0xF7}),

    /** Reset da família Yamaha XG. */
    YAMAHA_XG("Yamaha XG",
            new byte[] {(byte) 0xF0, 0x43, 0x10, 0x4C, 0x00, 0x00, 0x7E, 0x00, (byte) 0xF7});

    private final String label;
    private final byte[] sysex;

    SynthResetMode(String label, byte[] sysex) {
        this.label = label;
        this.sysex = sysex;
    }

    public String label() {
        return label;
    }

    public boolean hasSysex() {
        return sysex != null;
    }

    /** Cópia dos bytes, para ninguém alterar a constante por engano. */
    public byte[] sysex() {
        if (sysex == null) {
            throw new IllegalStateException("O modo " + name() + " não envia SysEx");
        }
        return sysex.clone();
    }

    public static SynthResetMode fromName(String name, SynthResetMode fallback) {
        if (name == null) {
            return fallback;
        }
        for (SynthResetMode mode : values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return fallback;
    }

    @Override
    public String toString() {
        return label;
    }
}
