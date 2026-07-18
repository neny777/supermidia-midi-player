package br.com.supermidia.app;

import br.com.supermidia.core.PlaybackMode;
import br.com.supermidia.midi.MidiOutputDevice;
import br.com.supermidia.midi.MidiPlaybackEngine;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiUnavailableException;
import java.io.File;
import java.io.IOException;
import java.util.List;

public final class MainController {
    @FXML
    private Label currentSongLabel;

    @FXML
    private Label currentSongHintLabel;

    @FXML
    private Label playbackModeLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label transposeValueLabel;

    @FXML
    private Label speedValueLabel;

    @FXML
    private Label masterVolumeValueLabel;

    @FXML
    private Label currentTimeLabel;

    @FXML
    private Label durationTimeLabel;

    @FXML
    private Label midiOutputStatusLabel;

    @FXML
    private ComboBox<MidiOutputDevice> midiOutputComboBox;

    @FXML
    private ToggleButton automaticModeToggle;

    @FXML
    private Slider transposeSlider;

    @FXML
    private Slider speedSlider;

    @FXML
    private Slider masterVolumeSlider;

    @FXML
    private Slider progressSlider;

    @FXML
    private Button playButton;

    @FXML
    private Button pauseButton;

    @FXML
    private Button stopButton;

    @FXML
    private Button panicButton;

    private PlaybackMode playbackMode = PlaybackMode.MANUAL;
    private MidiPlaybackEngine engine;
    private Timeline progressTimeline;
    private boolean refreshingOutputs;

    @FXML
    private void initialize() {
        configureControlListeners();
        refreshPlaybackMode();

        try {
            engine = new MidiPlaybackEngine();
            engine.setTranspose((int) transposeSlider.getValue());
            engine.setTempoFactor((float) (speedSlider.getValue() / 100.0));
            engine.setMasterVolume(masterVolumeSlider.getValue() / 100.0);
            engine.setPlaybackFinishedHandler(
                    () -> Platform.runLater(this::handlePlaybackFinished));
            startProgressUpdates();
            refreshMidiOutputs();
        } catch (MidiUnavailableException exception) {
            statusLabel.setText("MIDI INDISPONÍVEL");
            midiOutputStatusLabel.setText("Sequenciador MIDI não encontrado");
            disableMidiControls();
        }
        updateTransportControls();
    }

    public void shutdown() {
        if (progressTimeline != null) {
            progressTimeline.stop();
        }
        if (engine != null) {
            engine.close();
        }
    }

    @FXML
    private void handleOpenMidi() {
        if (engine == null) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Abrir arquivo MIDI");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Arquivos MIDI", "*.mid", "*.midi", "*.kar", "*.MID", "*.MIDI", "*.KAR"));
        File previousFile = engine.getLoadedFile();
        File previousDirectory = previousFile == null ? null : previousFile.getParentFile();
        if (previousDirectory != null && previousDirectory.isDirectory()) {
            chooser.setInitialDirectory(previousDirectory);
        }

        File selectedFile = chooser.showOpenDialog(currentSongLabel.getScene().getWindow());
        if (selectedFile == null) {
            return;
        }

        try {
            engine.load(selectedFile);
            currentSongLabel.setText(selectedFile.getName());
            currentSongLabel.setTooltip(new Tooltip(selectedFile.getAbsolutePath()));
            long duration = engine.getDurationMicroseconds();
            progressSlider.setMax(Math.max(1, duration));
            progressSlider.setValue(0);
            durationTimeLabel.setText(formatTime(duration));
            currentSongHintLabel.setText(formatTime(duration)
                    + (engine.hasOutput() ? " · pronta para tocar" : " · selecione uma saída MIDI"));
            statusLabel.setText("PRONTA");
            updateTransportControls();
        } catch (IOException | InvalidMidiDataException exception) {
            showError("Não foi possível abrir o MIDI",
                    "O arquivo selecionado não pôde ser lido como MIDI válido.", exception);
        }
    }

    @FXML
    private void handleRefreshMidiOutputs() {
        refreshMidiOutputs();
    }

    @FXML
    private void handleMidiOutputSelection() {
        if (engine == null || refreshingOutputs) {
            return;
        }
        MidiOutputDevice selection = midiOutputComboBox.getSelectionModel().getSelectedItem();
        try {
            engine.selectOutput(selection);
            if (selection == null) {
                midiOutputStatusLabel.setText("Saída MIDI desconectada");
            } else {
                midiOutputStatusLabel.setText("Saída: " + selection.name());
            }
            if (engine.hasSequence()) {
                currentSongHintLabel.setText(formatTime(engine.getDurationMicroseconds())
                        + (engine.hasOutput() ? " · pronta para tocar" : " · selecione uma saída MIDI"));
            }
            updateTransportControls();
        } catch (MidiUnavailableException exception) {
            midiOutputStatusLabel.setText("Falha ao conectar a saída MIDI");
            refreshingOutputs = true;
            midiOutputComboBox.getSelectionModel().clearSelection();
            refreshingOutputs = false;
            updateTransportControls();
            showError("Saída MIDI indisponível",
                    "O dispositivo pode estar sendo usado por outro programa.", exception);
        }
    }

    @FXML
    private void handlePlaybackMode() {
        playbackMode = automaticModeToggle.isSelected()
                ? PlaybackMode.AUTOMATIC
                : PlaybackMode.MANUAL;
        refreshPlaybackMode();
    }

    @FXML
    private void handlePlay() {
        if (engine == null) {
            return;
        }
        try {
            engine.play();
            statusLabel.setText("TOCANDO");
            updateTransportControls();
        } catch (IllegalStateException exception) {
            showError("Não foi possível iniciar",
                    exception.getMessage(), exception);
        }
    }

    @FXML
    private void handlePause() {
        if (engine != null) {
            engine.pause();
            statusLabel.setText("PAUSADA");
            updateTransportControls();
        }
    }

    @FXML
    private void handleStop() {
        if (engine != null) {
            engine.stop();
            progressSlider.setValue(0);
            currentTimeLabel.setText("00:00");
            statusLabel.setText("PRONTA");
            updateTransportControls();
        }
    }

    @FXML
    private void handlePanic() {
        if (engine != null) {
            engine.panic();
            midiOutputStatusLabel.setText("Panic enviado à saída MIDI");
        }
    }

    private void configureControlListeners() {
        transposeSlider.valueProperty().addListener((ignored, oldValue, newValue) -> {
            int transpose = newValue.intValue();
            transposeValueLabel.setText(formatSemitones(transpose));
            if (engine != null) {
                engine.setTranspose(transpose);
            }
        });
        speedSlider.valueProperty().addListener((ignored, oldValue, newValue) -> {
            long percent = Math.round(newValue.doubleValue());
            speedValueLabel.setText(percent + "%");
            if (engine != null) {
                engine.setTempoFactor((float) (percent / 100.0));
            }
        });
        masterVolumeSlider.valueProperty().addListener((ignored, oldValue, newValue) -> {
            long percent = Math.round(newValue.doubleValue());
            masterVolumeValueLabel.setText(percent + "%");
            if (engine != null) {
                engine.setMasterVolume(percent / 100.0);
            }
        });
        progressSlider.setOnMouseReleased(ignored -> seekToSliderPosition());
    }

    private void refreshMidiOutputs() {
        if (engine == null) {
            return;
        }

        refreshingOutputs = true;
        try {
            String previousName = midiOutputComboBox.getValue() == null
                    ? null : midiOutputComboBox.getValue().name();
            List<MidiOutputDevice> outputs = engine.listOutputDevices();
            midiOutputComboBox.setItems(FXCollections.observableArrayList(outputs));

            MidiOutputDevice preferred = findPreferredOutput(outputs, previousName);
            midiOutputComboBox.getSelectionModel().select(preferred);
            if (outputs.isEmpty()) {
                midiOutputStatusLabel.setText("Nenhuma saída MIDI encontrada");
            }
        } finally {
            refreshingOutputs = false;
        }
        handleMidiOutputSelection();
    }

    private MidiOutputDevice findPreferredOutput(List<MidiOutputDevice> outputs, String previousName) {
        if (previousName != null) {
            for (MidiOutputDevice output : outputs) {
                if (output.name().equals(previousName)) {
                    return output;
                }
            }
        }
        for (MidiOutputDevice output : outputs) {
            String normalizedName = output.name().toLowerCase();
            if (normalizedName.contains("jm-5") || normalizedName.contains("vima")) {
                return output;
            }
        }
        return outputs.isEmpty() ? null : outputs.getFirst();
    }

    private void startProgressUpdates() {
        progressTimeline = new Timeline(new KeyFrame(Duration.millis(150), ignored -> refreshProgress()));
        progressTimeline.setCycleCount(Timeline.INDEFINITE);
        progressTimeline.play();
    }

    private void refreshProgress() {
        if (engine == null || !engine.hasSequence() || progressSlider.isValueChanging()) {
            return;
        }
        long position = engine.getPositionMicroseconds();
        progressSlider.setValue(position);
        currentTimeLabel.setText(formatTime(position));
    }

    private void seekToSliderPosition() {
        if (engine != null && engine.hasSequence()) {
            engine.setPositionMicroseconds((long) progressSlider.getValue());
            currentTimeLabel.setText(formatTime((long) progressSlider.getValue()));
        }
    }

    private void handlePlaybackFinished() {
        if (engine == null || !engine.hasSequence()) {
            return;
        }
        statusLabel.setText("FINALIZADA");
        progressSlider.setValue(engine.getDurationMicroseconds());
        currentTimeLabel.setText(formatTime(engine.getDurationMicroseconds()));
        updateTransportControls();
    }

    private void updateTransportControls() {
        boolean available = engine != null;
        boolean loaded = available && engine.hasSequence();
        boolean outputConnected = available && engine.hasOutput();
        boolean running = available && engine.isRunning();

        playButton.setDisable(!loaded || !outputConnected || running);
        pauseButton.setDisable(!running);
        stopButton.setDisable(!loaded);
        progressSlider.setDisable(!loaded);
        panicButton.setDisable(!outputConnected);
    }

    private void disableMidiControls() {
        midiOutputComboBox.setDisable(true);
        playButton.setDisable(true);
        pauseButton.setDisable(true);
        stopButton.setDisable(true);
        panicButton.setDisable(true);
        progressSlider.setDisable(true);
    }

    private void refreshPlaybackMode() {
        automaticModeToggle.setSelected(playbackMode == PlaybackMode.AUTOMATIC);
        automaticModeToggle.setText(playbackMode.label());
        playbackModeLabel.setText(playbackMode.description());
    }

    private void showError(String title, String message, Exception exception) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("SuperMidia Live");
        alert.setHeaderText(title);
        alert.setContentText(message + (exception.getMessage() == null
                ? "" : "\n\nDetalhe: " + exception.getMessage()));
        if (currentSongLabel.getScene() != null) {
            alert.initOwner(currentSongLabel.getScene().getWindow());
        }
        alert.showAndWait();
    }

    private static String formatSemitones(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private static String formatTime(long microseconds) {
        long totalSeconds = Math.max(0, microseconds) / 1_000_000;
        long hours = totalSeconds / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                : String.format("%02d:%02d", minutes, seconds);
    }
}
