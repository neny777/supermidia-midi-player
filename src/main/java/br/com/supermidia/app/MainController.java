package br.com.supermidia.app;

import br.com.supermidia.core.PlaybackMode;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.stage.FileChooser;

import java.io.File;

public final class MainController {
    @FXML
    private Label currentSongLabel;

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
    private ToggleButton automaticModeToggle;

    @FXML
    private Slider transposeSlider;

    @FXML
    private Slider speedSlider;

    @FXML
    private Slider masterVolumeSlider;

    @FXML
    private Button playButton;

    @FXML
    private Button pauseButton;

    @FXML
    private Button stopButton;

    private PlaybackMode playbackMode = PlaybackMode.MANUAL;

    @FXML
    private void initialize() {
        transposeSlider.valueProperty().addListener((ignored, oldValue, newValue) ->
                transposeValueLabel.setText(formatSemitones(newValue.intValue())));
        speedSlider.valueProperty().addListener((ignored, oldValue, newValue) ->
                speedValueLabel.setText(Math.round(newValue.doubleValue()) + "%"));
        masterVolumeSlider.valueProperty().addListener((ignored, oldValue, newValue) ->
                masterVolumeValueLabel.setText(Math.round(newValue.doubleValue()) + "%"));
        refreshPlaybackMode();
    }

    @FXML
    private void handleOpenMidi() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Abrir arquivo MIDI");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Arquivos MIDI", "*.mid", "*.midi", "*.kar"));

        File selectedFile = chooser.showOpenDialog(currentSongLabel.getScene().getWindow());
        if (selectedFile != null) {
            currentSongLabel.setText(selectedFile.getName());
            currentSongLabel.setTooltip(new javafx.scene.control.Tooltip(selectedFile.getAbsolutePath()));
            statusLabel.setText("PRONTA");
            playButton.setDisable(false);
            pauseButton.setDisable(true);
            stopButton.setDisable(false);
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
        statusLabel.setText("TOCANDO");
        playButton.setDisable(true);
        pauseButton.setDisable(false);
        stopButton.setDisable(false);
    }

    @FXML
    private void handlePause() {
        statusLabel.setText("PAUSADA");
        playButton.setDisable(false);
        pauseButton.setDisable(true);
    }

    @FXML
    private void handleStop() {
        statusLabel.setText("PRONTA");
        playButton.setDisable(false);
        pauseButton.setDisable(true);
    }

    private void refreshPlaybackMode() {
        automaticModeToggle.setSelected(playbackMode == PlaybackMode.AUTOMATIC);
        automaticModeToggle.setText(playbackMode.label());
        playbackModeLabel.setText(playbackMode.description());
    }

    private static String formatSemitones(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }
}
