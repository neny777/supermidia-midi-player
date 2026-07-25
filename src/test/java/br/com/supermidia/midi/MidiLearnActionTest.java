package br.com.supermidia.midi;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MidiLearnActionTest {
    @Test
    void exposesTransportGlobalAndEightBankedMixerSlots() {
        List<MidiLearnAction> actions = MidiLearnAction.defaultActions();

        assertEquals(37, actions.size());
        assertTrue(actions.stream().anyMatch(action -> action.id().equals("transport.play")));
        assertTrue(actions.stream().anyMatch(action -> action.id().equals("global.transpose")));
        assertTrue(actions.stream().anyMatch(action -> action.id().equals("mixer.slot.8.solo")));
    }
}
