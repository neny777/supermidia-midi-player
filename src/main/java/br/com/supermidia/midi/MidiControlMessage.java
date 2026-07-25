package br.com.supermidia.midi;

import javax.sound.midi.ShortMessage;

public record MidiControlMessage(int command, int channel, int data1, int data2) {
    public static MidiControlMessage from(ShortMessage message) {
        return new MidiControlMessage(
                message.getCommand(), message.getChannel(),
                message.getData1(), message.getData2());
    }
}
