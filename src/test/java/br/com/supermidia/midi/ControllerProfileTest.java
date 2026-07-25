package br.com.supermidia.midi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.midi.ShortMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerProfileTest {
    private static ControllerProfile sampleProfile() {
        Map<String, MidiBinding> bindings = new LinkedHashMap<>();
        bindings.put("transport.play",
                new MidiBinding(ShortMessage.NOTE_ON, 0, 52, MidiBinding.ValueMode.TRIGGER));
        bindings.put("global.volume",
                new MidiBinding(ShortMessage.CONTROL_CHANGE, 0, 28, MidiBinding.ValueMode.ABSOLUTE));
        return new ControllerProfile("SMC-Mixer", bindings);
    }

    @Test
    void requiresAName() {
        assertThrows(IllegalArgumentException.class, () -> new ControllerProfile(" ", Map.of()));
    }

    @Test
    void survivesEncodeAndDecode() {
        ControllerProfile decoded = ControllerProfile.decode(sampleProfile().encode()).orElseThrow();

        assertEquals("SMC-Mixer", decoded.name());
        assertEquals(2, decoded.size());
        assertEquals(52, decoded.bindings().get("transport.play").data1());
        assertEquals(MidiBinding.ValueMode.ABSOLUTE,
                decoded.bindings().get("global.volume").valueMode());
    }

    @Test
    void ignoresCommentsBlankLinesAndBrokenEntries() {
        String text = """
                # comentário
                perfil=Teclado
                transport.play=144:0:52:TRIGGER

                linha sem separador
                transport.stop=valor invalido
                """;

        ControllerProfile profile = ControllerProfile.decode(text).orElseThrow();
        assertEquals("Teclado", profile.name());
        assertEquals(1, profile.size());
    }

    @Test
    void returnsEmptyForContentWithoutBindings() {
        assertTrue(ControllerProfile.decode("perfil=Vazio").isEmpty());
        assertTrue(ControllerProfile.decode("").isEmpty());
        assertTrue(ControllerProfile.decode(null).isEmpty());
    }

    @Test
    void bindingsAreImmutable() {
        ControllerProfile profile = sampleProfile();
        assertThrows(UnsupportedOperationException.class,
                () -> profile.bindings().clear());
    }

    @Test
    void savesWithTheExpectedExtensionAndReloads(@TempDir Path directory) throws IOException {
        Path target = directory.resolve("controladora");
        sampleProfile().save(target);

        Path written = directory.resolve("controladora" + ControllerProfile.FILE_EXTENSION);
        assertTrue(Files.exists(written));

        Optional<ControllerProfile> reloaded = ControllerProfile.load(written);
        assertEquals(2, reloaded.orElseThrow().size());
    }
}
