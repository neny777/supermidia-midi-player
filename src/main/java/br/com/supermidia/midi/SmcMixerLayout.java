package br.com.supermidia.midi;

import javax.sound.midi.ShortMessage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Layout de referência para a M-Vave SMC-Mixer.
 *
 * <p>A controladora não tem um mapa de fábrica confiável e documentado: cada controle é
 * programável pelo editor do fabricante. Em vez de adivinhar o que ela envia, este layout
 * define o que o player espera receber. Basta programar a controladora uma vez com a
 * tabela de {@link #setupTable()} e o perfil de {@link #profile()} passa a valer para
 * qualquer unidade, em qualquer computador.</p>
 *
 * <p><strong>Todos os controles são Control Change no canal 1</strong>, inclusive os botões.
 * Um tipo só reduz pela metade o trabalho de programar 59 controles no editor, e a
 * identidade de cada um continua única porque o número do CC nunca se repete.</p>
 *
 * <p>Alguns números caem em faixas que o General MIDI reserva (CC 7 volume, CC 10 pan,
 * CC 64 sustain). Isso é inofensivo <em>enquanto o player não repassar a entrada para a
 * saída</em>: hoje os dois circuitos são independentes — o {@link MidiInputMonitor} entrega
 * as mensagens da controladora só ao player, e quem alimenta o sintetizador é o
 * sequenciador. Se um dia existir MIDI thru, estes CCs precisarão ser filtrados ou
 * realocados para a faixa livre (3, 9, 14, 15, 20–31, 85–90, 102–119).</p>
 */
public final class SmcMixerLayout {
    public static final String PROFILE_NAME = "SMC-Mixer · layout SuperMídia";
    public static final int MIDI_CHANNEL = 0;

    // Botões globais da base, na ordem física (CC 0-10).
    public static final int PLAY_STOP_CC = 0;
    public static final int PAUSE_CC = 1;
    public static final int PANIC_CC = 2;
    public static final int PREVIOUS_CC = 3;
    public static final int NEXT_CC = 4;
    public static final int BANK_PREVIOUS_CC = 5;
    public static final int BANK_NEXT_CC = 6;
    public static final int TRANSPOSE_UP_CC = 7;
    public static final int TRANSPOSE_DOWN_CC = 8;

    // Knobs (CC 11-18).
    public static final int MASTER_VOLUME_CC = 11;
    public static final int SPEED_CC = 12;
    public static final int TRANSPOSE_CC = 13;

    // Faders (CC 21-28).
    public static final int FIRST_FADER_CC = 21;

    // Botões de cada canal: M, S e R (CC 31-38, 41-48, 51-58).
    public static final int FIRST_MUTE_CC = 31;
    public static final int FIRST_SOLO_CC = 41;
    public static final int PLAY_CC = 51;
    public static final int STOP_CC = 52;

    // Botões quadrados (CC 61-68).
    public static final int BANK_TOGGLE_CC = 61;
    public static final int AUTOPLAY_CC = 62;

    private SmcMixerLayout() {
    }

    public static ControllerProfile profile() {
        Map<String, MidiBinding> bindings = new LinkedHashMap<>();
        for (MidiLearnAction action : MidiLearnAction.defaultActions()) {
            bindings.put(action.id(), bindingFor(action));
        }
        return new ControllerProfile(PROFILE_NAME, bindings);
    }

    private static MidiBinding bindingFor(MidiLearnAction action) {
        return switch (action.kind()) {
            case PLAY_STOP_TOGGLE -> trigger(PLAY_STOP_CC);
            case PAUSE -> trigger(PAUSE_CC);
            case PANIC -> trigger(PANIC_CC);
            case PREVIOUS -> trigger(PREVIOUS_CC);
            case NEXT -> trigger(NEXT_CC);
            case BANK_PREVIOUS -> trigger(BANK_PREVIOUS_CC);
            case BANK_NEXT -> trigger(BANK_NEXT_CC);
            case TRANSPOSE_UP -> trigger(TRANSPOSE_UP_CC);
            case TRANSPOSE_DOWN -> trigger(TRANSPOSE_DOWN_CC);
            case MASTER_VOLUME -> continuous(MASTER_VOLUME_CC);
            case SPEED -> continuous(SPEED_CC);
            case TRANSPOSE -> continuous(TRANSPOSE_CC);
            case BANK_VOLUME -> continuous(FIRST_FADER_CC + action.slot());
            case BANK_MUTE -> trigger(FIRST_MUTE_CC + action.slot());
            case BANK_SOLO -> trigger(FIRST_SOLO_CC + action.slot());
            case PLAY -> trigger(PLAY_CC);
            case STOP -> trigger(STOP_CC);
            case BANK_TOGGLE -> trigger(BANK_TOGGLE_CC);
            case AUTOPLAY_TOGGLE -> trigger(AUTOPLAY_CC);
        };
    }

    private static MidiBinding trigger(int controllerNumber) {
        return new MidiBinding(ShortMessage.CONTROL_CHANGE, MIDI_CHANNEL, controllerNumber,
                MidiBinding.ValueMode.TRIGGER);
    }

    private static MidiBinding continuous(int controllerNumber) {
        return new MidiBinding(ShortMessage.CONTROL_CHANGE, MIDI_CHANNEL, controllerNumber,
                MidiBinding.ValueMode.ABSOLUTE);
    }

    /** Tabela para programar a controladora no editor do fabricante. */
    public static String setupTable() {
        StringBuilder text = new StringBuilder();
        text.append("Layout SuperMídia para a M-Vave SMC-Mixer\n");
        text.append("-".repeat(64)).append('\n');
        text.append("Programe estes valores no MidiSuite, o editor da M-VAVE para esta\n");
        text.append("controladora, disponível em m-vave.com/download. Atenção: o CubeSuite,\n");
        text.append("citado com frequência em fóruns, é para pedaleiras e loopers e NÃO\n");
        text.append("reconhece a SMC-Mixer.\n\n");
        text.append("Usando outra controladora? Esta tabela não serve: cada fabricante tem\n");
        text.append("seu próprio editor e sua própria numeração. Procure o editor do seu\n");
        text.append("modelo, ou use o Assistente de mapeamento, que aprende o que a sua\n");
        text.append("controladora já envia sem exigir reprogramação.\n\n");
        text.append("TODOS os controles são Control Change, no canal 1. Um tipo só para\n");
        text.append("tudo — inclusive os botões — simplifica a programação.\n");

        text.append("\nFADERS E KNOBS — modo absoluto\n");
        text.append("-".repeat(64)).append('\n');
        for (int slot = 0; slot < 8; slot++) {
            text.append(String.format("  Fader %d .................. CC %d   (volume do canal %d)%n",
                    slot + 1, FIRST_FADER_CC + slot, slot + 1));
        }
        text.append(String.format("  Knob 1 ................... CC %d   (volume geral)%n", MASTER_VOLUME_CC));
        text.append(String.format("  Knob 2 ................... CC %d   (velocidade)%n", SPEED_CC));
        text.append(String.format("  Knob 3 ................... CC %d   (tom, contínuo)%n", TRANSPOSE_CC));

        text.append("\nBOTÕES DA BASE — modo momentâneo (envia ao pressionar)\n");
        text.append("-".repeat(64)).append('\n');
        text.append(String.format("  Play ..................... CC %d   (toca e para, no mesmo botão)%n",
                PLAY_STOP_CC));
        text.append(String.format("  Pause .................... CC %d%n", PAUSE_CC));
        text.append(String.format("  Record ................... CC %d   (vira o Panic)%n", PANIC_CC));
        text.append(String.format("  Rewind ................... CC %d   (música anterior)%n", PREVIOUS_CC));
        text.append(String.format("  Forward .................. CC %d   (próxima música)%n", NEXT_CC));
        text.append(String.format("  << ....................... CC %d   (banco 1–8)%n", BANK_PREVIOUS_CC));
        text.append(String.format("  >> ....................... CC %d   (banco 9–16)%n", BANK_NEXT_CC));
        text.append(String.format("  Triângulo para cima ...... CC %d   (tom +1 semitom)%n", TRANSPOSE_UP_CC));
        text.append(String.format("  Triângulo para baixo ..... CC %d   (tom −1 semitom)%n", TRANSPOSE_DOWN_CC));

        text.append("\nBOTÕES DE CADA CANAL — modo momentâneo\n");
        text.append("-".repeat(64)).append('\n');
        for (int slot = 0; slot < 8; slot++) {
            text.append(String.format("  M do canal %d ............. CC %d   (mute)%n",
                    slot + 1, FIRST_MUTE_CC + slot));
        }
        for (int slot = 0; slot < 8; slot++) {
            text.append(String.format("  S do canal %d ............. CC %d   (solo)%n",
                    slot + 1, FIRST_SOLO_CC + slot));
        }
        text.append(String.format("  Quadrado do canal 1 ...... CC %d   (alterna banco 1–8 / 9–16)%n",
                BANK_TOGGLE_CC));
        text.append(String.format("  Quadrado do canal 2 ...... CC %d   (autoplay)%n", AUTOPLAY_CC));

        text.append("\nOPCIONAIS — só se você quiser\n");
        text.append("-".repeat(64)).append('\n');
        text.append("Estas funções duplicam outras já cobertas acima. Programe apenas se\n");
        text.append("preferir separá-las; pular estas linhas não deixa nada sem controle.\n");
        text.append(String.format("  R do canal 1 ............. CC %d   (Play isolado)%n", PLAY_CC));
        text.append(String.format("  R do canal 2 ............. CC %d   (Stop isolado)%n", STOP_CC));
        text.append(String.format("  Knob 3 ................... CC %d   (tom contínuo, no lugar dos ▲▼)%n",
                TRANSPOSE_CC));

        text.append("\nObservações\n");
        text.append("-".repeat(64)).append('\n');
        text.append("- Ligue a controladora pelo cabo USB-C. O Java não enxerga MIDI por\n");
        text.append("  Bluetooth no Windows sem uma porta virtual intermediária.\n");
        text.append("- Programe cada botão com um CC diferente. Se dois controles enviarem\n");
        text.append("  o mesmo número, o player não tem como distingui-los — a mensagem que\n");
        text.append("  chega pelo cabo é idêntica.\n");
        text.append("- Os faders da SMC-Mixer suavizam o valor enviado; em movimentos rápidos\n");
        text.append("  o extremo 0 ou 127 pode não ser alcançado. Termine o curso com calma.\n");
        text.append("- Os botões BT e Shift não enviam mensagem própria e não entram na tabela.\n");
        return text.toString();
    }
}
