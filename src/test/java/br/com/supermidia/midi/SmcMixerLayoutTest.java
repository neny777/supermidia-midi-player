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
    void usesControlChangeForEveryControlWithTheModeMatchingTheAction() {
        ControllerProfile profile = SmcMixerLayout.profile();

        // O layout usa CC em tudo, inclusive nos botões: um tipo só reduz o trabalho
        // de programar a controladora. O que distingue botão de fader é o ValueMode.
        for (MidiLearnAction action : MidiLearnAction.defaultActions()) {
            MidiBinding binding = profile.bindings().get(action.id());
            assertEquals(ShortMessage.CONTROL_CHANGE, binding.command(), action.id());
            assertEquals(action.kind().isContinuous()
                            ? MidiBinding.ValueMode.ABSOLUTE
                            : MidiBinding.ValueMode.TRIGGER,
                    binding.valueMode(), action.id());
        }
    }

    @Test
    void keepsEveryControlNumberWithinTheValidControlChangeRange() {
        for (MidiBinding binding : SmcMixerLayout.profile().bindings().values()) {
            assertTrue(binding.data1() >= 0 && binding.data1() <= 119,
                    "CC fora da faixa utilizável: " + binding.description());
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

        assertTrue(table.contains("CC 21"), "primeiro fader");
        assertTrue(table.contains("CC 28"), "último fader");
        assertTrue(table.contains("CC 0"), "botão de play/stop");
        assertTrue(table.contains("USB-C"));
    }

    @Test
    void setupTablePointsToTheRightEditorAndWarnsOtherControllers() {
        String table = SmcMixerLayout.setupTable();

        // O CubeSuite não reconhece a SMC-Mixer; mandar o usuário até ele é um beco sem saída.
        assertTrue(table.contains("MidiSuite"), "cita o editor correto");
        assertTrue(table.contains("m-vave.com/download"), "diz onde baixar");
        assertTrue(table.contains("Usando outra controladora"),
                "avisa que a tabela não serve para outros modelos");
    }
}
