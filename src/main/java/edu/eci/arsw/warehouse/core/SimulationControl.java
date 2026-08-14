package edu.eci.arsw.warehouse.core;

/**
 * Controls whether the simulation is paused or running.
 * Robots check this at the start of each cycle before taking a new parcel.
 */
public class SimulationControl {

    private volatile boolean paused;

    // Sets the paused flag so robots stop at the next safe point.
    public synchronized void pause() {
        paused = true;
    }

    // Clears the paused flag and wakes all waiting robots.
    public synchronized void resume() {
        paused = false;
        notifyAll();
    }

    // Blocks the calling robot until the simulation is resumed.
    // Uses wait() instead of busy-waiting to avoid wasting CPU.
    public synchronized void awaitIfPaused() throws InterruptedException {
        while (paused) {
            wait();
        }
    }

    public synchronized boolean isPaused() {
        return paused;
    }
}
