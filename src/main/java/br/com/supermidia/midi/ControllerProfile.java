package br.com.supermidia.midi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Conjunto completo de vínculos de uma controladora, gravável em arquivo.
 *
 * <p>Permite guardar o mapeamento de cada equipamento, levar a configuração para
 * outro computador e voltar rapidamente ao mapa conhecido depois de um teste.</p>
 */
public record ControllerProfile(String name, Map<String, MidiBinding> bindings) {
    public static final String FILE_EXTENSION = ".smprofile";
    private static final String HEADER = "# SuperMidia MIDI Player · perfil de controladora";
    private static final String NAME_KEY = "perfil";

    public ControllerProfile {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("O nome do perfil é obrigatório.");
        }
        bindings = Collections.unmodifiableMap(new LinkedHashMap<>(
                bindings == null ? Map.of() : bindings));
    }

    public boolean isEmpty() {
        return bindings.isEmpty();
    }

    public int size() {
        return bindings.size();
    }

    public String encode() {
        StringBuilder text = new StringBuilder();
        text.append(HEADER).append('\n');
        text.append(NAME_KEY).append('=').append(name).append('\n');
        bindings.forEach((actionId, binding) ->
                text.append(actionId).append('=').append(binding.encode()).append('\n'));
        return text.toString();
    }

    public static Optional<ControllerProfile> decode(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        String profileName = "Perfil importado";
        Map<String, MidiBinding> bindings = new LinkedHashMap<>();
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = line.substring(0, separator).strip();
            String value = line.substring(separator + 1).strip();
            if (key.toLowerCase(Locale.ROOT).equals(NAME_KEY)) {
                if (!value.isBlank()) {
                    profileName = value;
                }
                continue;
            }
            MidiBinding.decode(value).ifPresent(binding -> bindings.put(key, binding));
        }

        return bindings.isEmpty()
                ? Optional.empty()
                : Optional.of(new ControllerProfile(profileName, bindings));
    }

    public void save(Path file) throws IOException {
        Files.writeString(withExtension(file), encode(), StandardCharsets.UTF_8);
    }

    public static Optional<ControllerProfile> load(Path file) throws IOException {
        return decode(Files.readString(file, StandardCharsets.UTF_8));
    }

    public static Path withExtension(Path file) {
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(FILE_EXTENSION) ? file : Path.of(file + FILE_EXTENSION);
    }
}
