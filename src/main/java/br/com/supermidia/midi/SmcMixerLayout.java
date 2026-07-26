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
 * <p>Os números escolhidos evitam os controladores de uso reservado do General MIDI:
 * a faixa CC 20–31 não é atribuída pela especificação, e as notas ficam abaixo da região
 * usada pelas letras e pela melodia dos arquivos de repertório.</p>
 */
public final class SmcMixerLayout {
    public static final String PROFILE_NAME = "SMC-Mixer · layout SuperMídia";
    public static final int MIDI_CHANNEL = 0;

    public static final int FIRST_FADER_CC = 20;
    public static final int MASTER_VOLUME_CC = 28;
    public static final int TRANSPOSE_CC = 29;
    public static final int SPEED_CC = 30;

    public static final int FIRST_MUTE_NOTE = 36;
    public static final int FIRST_SOLO_NOTE = 44;
    public static final int PLAY_NOTE = 52;
    public static final int PAUSE_NOTE = 53;
    public static final int STOP_NOTE = 54;
    public static final int PREVIOUS_NOTE = 55;
    public static final int NEXT_NOTE = 56;
    public static final int PANIC_NOTE = 57;
    public static final int AUTOPLAY_NOTE = 58;
    public static final int BANK_TOGGLE_NOTE = 59;
    public static final int BANK_PREVIOUS_NOTE = 60;
    public static final int BANK_NEXT_NOTE = 61;
    public static final int PLAY_STOP_NOTE = 62;
    public static final int TRANSPOSE_UP_NOTE = 63;
    public static final int TRANSPOSE_DOWN_NOTE = 64;

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
            case PLAY -> note(PLAY_NOTE);
            case PAUSE -> note(PAUSE_NOTE);
            case STOP -> note(STOP_NOTE);
            case PLAY_STOP_TOGGLE -> note(PLAY_STOP_NOTE);
            case TRANSPOSE_UP -> note(TRANSPOSE_UP_NOTE);
            case TRANSPOSE_DOWN -> note(TRANSPOSE_DOWN_NOTE);
            case PREVIOUS -> note(PREVIOUS_NOTE);
            case NEXT -> note(NEXT_NOTE);
            case PANIC -> note(PANIC_NOTE);
            case AUTOPLAY_TOGGLE -> note(AUTOPLAY_NOTE);
            case BANK_TOGGLE -> note(BANK_TOGGLE_NOTE);
            case BANK_PREVIOUS -> note(BANK_PREVIOUS_NOTE);
            case BANK_NEXT -> note(BANK_NEXT_NOTE);
            case TRANSPOSE -> controlChange(TRANSPOSE_CC);
            case SPEED -> controlChange(SPEED_CC);
            case MASTER_VOLUME -> controlChange(MASTER_VOLUME_CC);
            case BANK_VOLUME -> controlChange(FIRST_FADER_CC + action.slot());
            case BANK_MUTE -> note(FIRST_MUTE_NOTE + action.slot());
            case BANK_SOLO -> note(FIRST_SOLO_NOTE + action.slot());
        };
    }

    private static MidiBinding note(int noteNumber) {
        return new MidiBinding(ShortMessage.NOTE_ON, MIDI_CHANNEL, noteNumber,
                MidiBinding.ValueMode.TRIGGER);
    }

    private static MidiBinding controlChange(int controllerNumber) {
        return new MidiBinding(ShortMessage.CONTROL_CHANGE, MIDI_CHANNEL, controllerNumber,
                MidiBinding.ValueMode.ABSOLUTE);
    }

    /** Tabela para programar a controladora no editor do fabricante. */
    public static String setupTable() {
        StringBuilder text = new StringBuilder();
        text.append("Layout SuperMídia para a M-Vave SMC-Mixer\n");
        text.append("Programe estes valores no editor da controladora (CubeSuite / MidiSuite),\n");
        text.append("com a controladora em modo CC e todos os controles no canal 1.\n\n");

        text.append("FADERS E KNOBS — tipo CC, modo absoluto, canal 1\n");
        text.append("-".repeat(60)).append('\n');
        for (int slot = 0; slot < 8; slot++) {
            text.append(String.format("  Fader %d .................. CC %d%n",
                    slot + 1, FIRST_FADER_CC + slot));
        }
        text.append(String.format("  Fader master ............. CC %d   (volume geral)%n",
                MASTER_VOLUME_CC));
        text.append(String.format("  Knob de tom .............. CC %d   (transpose)%n", TRANSPOSE_CC));
        text.append(String.format("  Knob de velocidade ....... CC %d%n", SPEED_CC));

        text.append("\nBOTÕES — tipo Note, modo Single ou Push, canal 1\n");
        text.append("-".repeat(60)).append('\n');
        for (int slot = 0; slot < 8; slot++) {
            text.append(String.format("  Mute %d ................... Nota %d%n",
                    slot + 1, FIRST_MUTE_NOTE + slot));
        }
        for (int slot = 0; slot < 8; slot++) {
            text.append(String.format("  Solo %d ................... Nota %d%n",
                    slot + 1, FIRST_SOLO_NOTE + slot));
        }
        text.append(String.format("  Play ..................... Nota %d%n", PLAY_NOTE));
        text.append(String.format("  Pause .................... Nota %d%n", PAUSE_NOTE));
        text.append(String.format("  Stop ..................... Nota %d%n", STOP_NOTE));
        text.append(String.format("  Música anterior .......... Nota %d%n", PREVIOUS_NOTE));
        text.append(String.format("  Próxima música ........... Nota %d%n", NEXT_NOTE));
        text.append(String.format("  Panic .................... Nota %d%n", PANIC_NOTE));
        text.append(String.format("  Autoplay ................. Nota %d%n", AUTOPLAY_NOTE));
        text.append(String.format("  Alternar banco 1–8 / 9–16  Nota %d%n", BANK_TOGGLE_NOTE));
        text.append(String.format("  Banco 1–8 ................ Nota %d%n", BANK_PREVIOUS_NOTE));
        text.append(String.format("  Banco 9–16 ............... Nota %d%n", BANK_NEXT_NOTE));
        text.append(String.format("  Play/Stop (um botão) ..... Nota %d%n", PLAY_STOP_NOTE));
        text.append(String.format("  Tom +1 semitom ........... Nota %d%n", TRANSPOSE_UP_NOTE));
        text.append(String.format("  Tom −1 semitom ........... Nota %d%n", TRANSPOSE_DOWN_NOTE));

        text.append('\n');
        text.append("Observações\n");
        text.append("-".repeat(60)).append('\n');
        text.append("- Ligue a controladora pelo cabo USB-C. O Java não enxerga MIDI por\n");
        text.append("  Bluetooth no Windows sem uma porta virtual intermediária.\n");
        text.append("- Se preferir manter o mapa de fábrica, use o Assistente de mapeamento\n");
        text.append("  em vez desta tabela: ele aprende o que a controladora já envia.\n");
        text.append("- Os faders da SMC-Mixer suavizam o valor enviado; em movimentos rápidos\n");
        text.append("  o extremo 0 ou 127 pode não ser alcançado. Termine o curso com calma.\n");
        return text.toString();
    }
}
