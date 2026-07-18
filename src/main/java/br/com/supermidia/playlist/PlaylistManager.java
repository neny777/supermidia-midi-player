package br.com.supermidia.playlist;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class PlaylistManager {
    private final List<PlaylistItem> items = new ArrayList<>();
    private int currentIndex = -1;

    public int add(Path path) {
        PlaylistItem candidate = new PlaylistItem(path);
        int existingIndex = items.indexOf(candidate);
        if (existingIndex >= 0) {
            return existingIndex;
        }
        items.add(candidate);
        return items.size() - 1;
    }

    public void addAll(Collection<Path> paths) {
        paths.forEach(this::add);
    }

    public void replaceAll(Collection<PlaylistItem> replacement) {
        items.clear();
        for (PlaylistItem item : replacement) {
            if (!items.contains(item)) {
                items.add(item);
            }
        }
        currentIndex = -1;
    }

    public boolean removeAt(int index) {
        requireValidIndex(index);
        boolean removedCurrent = index == currentIndex;
        items.remove(index);

        if (items.isEmpty()) {
            currentIndex = -1;
        } else if (removedCurrent) {
            currentIndex = Math.min(index, items.size() - 1);
        } else if (index < currentIndex) {
            currentIndex--;
        }
        return removedCurrent;
    }

    public int moveUp(int index) {
        requireValidIndex(index);
        if (index == 0) {
            return index;
        }
        Collections.swap(items, index, index - 1);
        adjustCurrentIndexAfterSwap(index, index - 1);
        return index - 1;
    }

    public int moveDown(int index) {
        requireValidIndex(index);
        if (index == items.size() - 1) {
            return index;
        }
        Collections.swap(items, index, index + 1);
        adjustCurrentIndexAfterSwap(index, index + 1);
        return index + 1;
    }

    public PlaylistItem select(int index) {
        requireValidIndex(index);
        currentIndex = index;
        return items.get(index);
    }

    public Optional<PlaylistItem> current() {
        return itemAt(currentIndex);
    }

    public Optional<PlaylistItem> next() {
        return currentIndex < 0 ? Optional.empty() : itemAt(currentIndex + 1);
    }

    public Optional<PlaylistItem> previous() {
        return itemAt(currentIndex - 1);
    }

    public boolean hasNext() {
        return currentIndex >= 0 && currentIndex + 1 < items.size();
    }

    public boolean hasPrevious() {
        return currentIndex > 0;
    }

    public int currentIndex() {
        return currentIndex;
    }

    public int size() {
        return items.size();
    }

    public PlaylistItem get(int index) {
        requireValidIndex(index);
        return items.get(index);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public List<PlaylistItem> items() {
        return List.copyOf(items);
    }

    private Optional<PlaylistItem> itemAt(int index) {
        return index >= 0 && index < items.size()
                ? Optional.of(items.get(index))
                : Optional.empty();
    }

    private void adjustCurrentIndexAfterSwap(int firstIndex, int secondIndex) {
        if (currentIndex == firstIndex) {
            currentIndex = secondIndex;
        } else if (currentIndex == secondIndex) {
            currentIndex = firstIndex;
        }
    }

    private void requireValidIndex(int index) {
        if (index < 0 || index >= items.size()) {
            throw new IndexOutOfBoundsException("Índice fora da playlist: " + index);
        }
    }
}
