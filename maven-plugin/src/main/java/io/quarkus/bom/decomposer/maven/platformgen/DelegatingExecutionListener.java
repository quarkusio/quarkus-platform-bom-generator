package io.quarkus.bom.decomposer.maven.platformgen;

import java.util.ArrayList;
import java.util.List;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionListener;

public class DelegatingExecutionListener implements ExecutionListener {

    private final List<ExecutionListener> listeners = new ArrayList<>(2);

    public DelegatingExecutionListener add(ExecutionListener listener) {
        listeners.add(listener);
        return this;
    }

    @Override
    public void projectDiscoveryStarted(ExecutionEvent event) {
        listeners.forEach(l -> l.projectDiscoveryStarted(event));
    }

    @Override
    public void sessionStarted(ExecutionEvent event) {
        listeners.forEach(l -> l.sessionStarted(event));
    }

    @Override
    public void sessionEnded(ExecutionEvent event) {
        listeners.forEach(l -> l.sessionEnded(event));
    }

    @Override
    public void projectSkipped(ExecutionEvent event) {
        listeners.forEach(l -> l.projectSkipped(event));
    }

    @Override
    public void projectStarted(ExecutionEvent event) {
        listeners.forEach(l -> l.projectStarted(event));
    }

    @Override
    public void projectSucceeded(ExecutionEvent event) {
        listeners.forEach(l -> l.projectSucceeded(event));
    }

    @Override
    public void projectFailed(ExecutionEvent event) {
        listeners.forEach(l -> l.projectFailed(event));
    }

    @Override
    public void mojoSkipped(ExecutionEvent event) {
        listeners.forEach(l -> l.mojoSkipped(event));
    }

    @Override
    public void mojoStarted(ExecutionEvent event) {
        listeners.forEach(l -> l.mojoStarted(event));
    }

    @Override
    public void mojoSucceeded(ExecutionEvent event) {
        listeners.forEach(l -> l.mojoSucceeded(event));
    }

    @Override
    public void mojoFailed(ExecutionEvent event) {
        listeners.forEach(l -> l.mojoFailed(event));
    }

    @Override
    public void forkStarted(ExecutionEvent event) {
        listeners.forEach(l -> l.forkStarted(event));
    }

    @Override
    public void forkSucceeded(ExecutionEvent event) {
        listeners.forEach(l -> l.forkSucceeded(event));
    }

    @Override
    public void forkFailed(ExecutionEvent event) {
        listeners.forEach(l -> l.forkFailed(event));
    }

    @Override
    public void forkedProjectStarted(ExecutionEvent event) {
        listeners.forEach(l -> l.forkedProjectStarted(event));
    }

    @Override
    public void forkedProjectSucceeded(ExecutionEvent event) {
        listeners.forEach(l -> l.forkedProjectSucceeded(event));
    }

    @Override
    public void forkedProjectFailed(ExecutionEvent event) {
        listeners.forEach(l -> l.forkedProjectFailed(event));
    }
}
