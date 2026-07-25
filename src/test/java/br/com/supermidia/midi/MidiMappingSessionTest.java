package br.com.supermidia.midi;

import org.junit.jupiter.api.Test;

import javax.sound.midi.ShortMessage;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MidiMappingSessionTest {
    private static final MidiLearnAction PLAY =
            new MidiLearnAction("transport.play", "Transporte · Play", MidiLearnAction.Kind.PLAY, -1);
    private static final MidiLearnAction STOP =
            new MidiLearnAction("transport.stop", "Transporte · Stop", MidiLearnAction.Kind.STOP, -1);
    private static final MidiLearnAction VOLUME =
            new MidiLearnAction("global.volume", "Geral · Volume",
                    MidiLearnAction.Kind.MASTER_VOLUME, -1);

    private static MidiControlMessage note(int noteNumber) {
        return new MidiControlMessage(ShortMessage.NOTE_ON, 0, noteNumber, 127);
    }

    private static MidiControlMessage controlChange(int controller, int value) {
        return new MidiControlMessage(ShortMessage.CONTROL_CHANGE, 0, controller, value);
    }

    @Test
    void rejectsEmptyActionList() {
        assertThrows(IllegalArgumentException.class, () -> new MidiMappingSession(List.of()));
    }

    @Test
    void walksThroughEveryActionAndCollectsBindings() {
        MidiMappingSession session = new MidiMappingSession(List.of(PLAY, STOP, VOLUME));

        assertEquals(1, session.position());
        assertEquals(3, session.total());
        assertEquals(PLAY, session.current().orElseThrow());

        assertTrue(session.submit(note(52)).isPresent());
        assertEquals(STOP, session.current().orElseThrow());

        assertTrue(session.submit(note(54)).isPresent());
        assertEquals(VOLUME, session.current().orElseThrow());

        assertTrue(session.submit(controlChange(28, 90)).isPresent());
        assertTrue(session.isFinished());

        Map<String, MidiBinding> result = session.result();
        assertEquals(3, result.size());
        assertEquals(MidiBinding.ValueMode.TRIGGER, result.get("transport.play").valueMode());
        assertEquals(MidiBinding.ValueMode.ABSOLUTE, result.get("global.volume").valueMode());
        assertEquals(28, result.get("global.volume").data1());
    }

    @Test
    void ignoresMessagesThatDoNotFitTheCurrentAction() {
        MidiMappingSession session = new MidiMappingSession(List.of(VOLUME, PLAY));

        assertTrue(session.submit(note(52)).isEmpty());
        assertEquals(VOLUME, session.current().orElseThrow());
        assertEquals(0, session.mappedCount());
    }

    @Test
    void skipLeavesActionUnmappedAndAdvances() {
        MidiMappingSession session = new MidiMappingSession(List.of(PLAY, STOP));

        session.skip();
        assertEquals(STOP, session.current().orElseThrow());
        assertTrue(session.submit(note(54)).isPresent());
        assertEquals(Map.of("transport.stop",
                new MidiBinding(ShortMessage.NOTE_ON, 0, 54, MidiBinding.ValueMode.TRIGGER)),
                session.result());
    }

    @Test
    void backDiscardsThePreviousBinding() {
        MidiMappingSession session = new MidiMappingSession(List.of(PLAY, STOP));
        session.submit(note(52));

        session.back();
        assertEquals(PLAY, session.current().orElseThrow());
        assertTrue(session.result().isEmpty());
        assertTrue(session.isAtFirstAction());
    }

    @Test
    void reusingTheSameControlReleasesTheEarlierAction() {
        MidiMappingSession session = new MidiMappingSession(List.of(PLAY, STOP));
        session.submit(note(52));
        session.submit(note(52));

        Map<String, MidiBinding> result = session.result();
        assertEquals(1, result.size());
        assertTrue(result.containsKey("transport.stop"));
        assertFalse(result.containsKey("transport.play"));
    }

    @Test
    void exportsTheCollectedBindingsAsProfile() {
        MidiMappingSession session = new MidiMappingSession(List.of(PLAY));
        session.submit(note(52));

        ControllerProfile profile = session.toProfile("SMC-Mixer");
        assertEquals("SMC-Mixer", profile.name());
        assertEquals(1, profile.size());
    }
}
