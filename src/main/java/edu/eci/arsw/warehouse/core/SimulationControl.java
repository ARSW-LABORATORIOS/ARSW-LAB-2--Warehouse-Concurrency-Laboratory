package edu.eci.arsw.warehouse.core;

/**
 * Monitor-based pause/resume control.
 *
 * All state transitions and waits go through the same intrinsic lock (this),
 * so pause()/resume() and awaitIfPaused() coordinate without busy-waiting.
 */
public class SimulationControl {

    private boolean paused;

    public synchronized void pause() {
        paused = true;
    }

    public synchronized void resume() {
        paused = false;
        notifyAll();
    }

    public synchronized void awaitIfPaused() throws InterruptedException {
        while (paused) {
            wait();
        }
    }

    public synchronized boolean isPaused() {
        return paused;
    }
}
