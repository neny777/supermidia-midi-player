package br.com.supermidia.midi;

import org.junit.jupiter.api.Test;

import javax.sound.midi.ShortMessage;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmcMixerLayoutTest {
    @Test
    void coversEveryLearnableAction() {
        ControllerProfile profile = SmcMixerLayout.profile();

        assertEquals(MidiLearnAction.defaultActions().size(), profile.size());
        for (MidiLearnAction action : MidiLearnAction.defaultActions()) {
            assertTrue(profile.bindings().containsKey(action.id()),
                    "sem vínculo para " + action.id());
        }
    }

    @Test
    void neverRepeatsTheSameControl() {
        Set<MidiBinding> used = new HashSet<>();
        for (MidiBinding binding : SmcMixerLayout.profile().bindings().values()) {
            assertTrue(used.add(binding), "controle repetido: " + binding.description());
        }
    }

    @Test
    void usesAbsoluteControlChangesForContinuousActionsAndNotesForTriggers() {
        ControllerProfile profile = SmcMixerLayout.profile();

        for (MidiLearnAction action : MidiLearnAction.defaultActions()) {
            MidiBinding binding = profile.bindings().get(action.id());
            if (action.kind().isContinuous()) {
                assertEquals(ShortMessage.CONTROL_CHANGE, binding.command(), action.id());
                assertEquals(MidiBinding.ValueMode.ABSOLUTE, binding.valueMode(), action.id());
            } else {
                assertEquals(ShortMessage.NOTE_ON, binding.command(), action.id());
                assertEquals(MidiBinding.ValueMode.TRIGGER, binding.valueMode(), action.id());
            }
        }
    }

    @Test
    void keepsFadersOnTheUnassignedControlChangeRange() {
        ControllerProfile profile = SmcMixerLayout.profile();

        for (int slot = 1; slot <= 8; slot++) {
            MidiBinding binding = profile.bindings().get("mixer.slot." + slot + ".volume");
            assertEquals(SmcMixerLayout.FIRST_FADER_CC + slot - 1, binding.data1());
            assertTrue(binding.data1() >= 20 && binding.data1() <= 31,
                    "fader fora da faixa livre do General MIDI");
        }
    }

    @Test
    void setupTableListsEveryFaderAndTransportButton() {
        String table = SmcMixerLayout.setupTable();

        assertTrue(table.contains("CC 20"));
        assertTrue(table.contains("CC 27"));
        assertTrue(table.contains("Nota 52"));
        assertTrue(table.contains("USB-C"));
    }
}
