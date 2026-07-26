package br.com.supermidia.midi;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MidiLearnActionTest {
    @Test
    void exposesTransportGlobalAndEightBankedMixerSlots() {
        List<MidiLearnAction> actions = MidiLearnAction.defaultActions();

        assertEquals(40, actions.size());
        assertTrue(actions.stream().anyMatch(action -> action.id().equals("transport.play")));
        assertTrue(actions.stream().anyMatch(action -> action.id().equals("global.transpose")));
        assertTrue(actions.stream().anyMatch(action -> action.id().equals("mixer.slot.8.solo")));
        assertTrue(actions.stream().anyMatch(action -> action.id().equals("transport.playStop")));
        assertTrue(actions.stream().anyMatch(action -> action.id().equals("global.transpose.up")));
        assertTrue(actions.stream().anyMatch(action -> action.id().equals("global.transpose.down")));
    }

    @Test
    void treatsTheNewButtonActionsAsTriggersNotContinuousControls() {
        List<MidiLearnAction> actions = MidiLearnAction.defaultActions();

        // Tom por botão e Play/Stop precisam ser gatilhos: se entrassem como contínuos,
        // o player esperaria um valor variável e o botão nunca dispararia a ação.
        for (String id : List.of("transport.playStop", "global.transpose.up", "global.transpose.down")) {
            MidiLearnAction action = actions.stream()
                    .filter(candidate -> candidate.id().equals(id))
                    .findFirst()
                    .orElseThrow();
            assertTrue(!action.kind().isContinuous(), id + " deveria ser gatilho");
        }
    }
}
