package br.com.supermidia.midi;

import javax.sound.midi.ShortMessage;

/**
 * Identifica um controle físico pelo tipo de mensagem que ele envia, sem considerar o valor.
 *
 * <p>Note Off é tratado como Note On para que pressionar e soltar um botão continue
 * representando o mesmo controle. O Pitch Bend não usa {@code data1}, porque nesse
 * comando os dois bytes de dados formam um único valor de 14 bits.</p>
 */
public record MidiSignature(int command, int channel, int data1) {
    public static final int ANY_DATA = -1;

    public static MidiSignature of(MidiControlMessage message) {
        int command = message.command() == ShortMessage.NOTE_OFF
                ? ShortMessage.NOTE_ON
                : message.command();
        int data1 = command == ShortMessage.PITCH_BEND ? ANY_DATA : message.data1();
        return new MidiSignature(command, message.channel(), data1);
    }

    /** Valor transportado pela mensagem, normalizado para a escala de 0 a 127. */
    public static int value(MidiControlMessage message) {
        if (message.command() == ShortMessage.PITCH_BEND) {
            return (((message.data2() << 7) | message.data1()) >> 7) & 0x7F;
        }
        return message.data2();
    }

    public String description() {
        String channelText = "canal " + (channel + 1);
        return switch (command) {
            case ShortMessage.PITCH_BEND -> "Pitch Bend · " + channelText;
            case ShortMessage.CONTROL_CHANGE -> "CC " + data1 + " · " + channelText;
            case ShortMessage.NOTE_ON -> "Nota " + data1 + " · " + channelText;
            case ShortMessage.PROGRAM_CHANGE -> "Program Change " + data1 + " · " + channelText;
            default -> "Comando " + command + " · " + channelText;
        };
    }
}
