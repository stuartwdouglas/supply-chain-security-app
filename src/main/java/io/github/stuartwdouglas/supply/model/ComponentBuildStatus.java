package io.github.stuartwdouglas.supply.model;

import java.util.Map;

public class ComponentBuildStatus {
    String state;
    int outstanding;
    String message;
    boolean resultNotified;
    Map<String, ArtifactState> artifactState;

    public String getState() {
        return state;
    }

    public ComponentBuildStatus setState(String state) {
        this.state = state;
        return this;
    }

    public int getOutstanding() {
        return outstanding;
    }

    public ComponentBuildStatus setOutstanding(int outstanding) {
        this.outstanding = outstanding;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public ComponentBuildStatus setMessage(String message) {
        this.message = message;
        return this;
    }

    public boolean isResultNotified() {
        return resultNotified;
    }

    public ComponentBuildStatus setResultNotified(boolean resultNotified) {
        this.resultNotified = resultNotified;
        return this;
    }

    public Map<String, ArtifactState> getArtifactState() {
        return artifactState;
    }

    public ComponentBuildStatus setArtifactState(Map<String, ArtifactState> artifactState) {
        this.artifactState = artifactState;
        return this;
    }
}
