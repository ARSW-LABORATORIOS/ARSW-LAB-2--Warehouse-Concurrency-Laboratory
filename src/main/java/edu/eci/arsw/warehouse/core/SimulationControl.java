package edu.eci.arsw.warehouse.core;

/**
 * Starter pause/resume control.
 *
 * This version intentionally uses active waiting so students can replace it with
 * a monitor-based design using synchronized + wait()/notifyAll().
 */
public class SimulationControl {

    private volatile boolean paused;

    public void pause() {
        paused = true;
    }

    public void resume() {
        paused = false;
    }

    public void awaitIfPaused() {
        // TODO LAB 2: replace busy waiting with monitor coordination.
        while (paused) {
            Thread.onSpinWait();
        }
    }

    public boolean isPaused() {
        return paused;
    }
}
