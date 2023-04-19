package io.github.stuartwdouglas.supply.model;

public class ArtifactState {
    String artifactBuild;
    boolean built;
    boolean deployed;
    boolean failed;

    public String getArtifactBuild() {
        return artifactBuild;
    }

    public ArtifactState setArtifactBuild(String artifactBuild) {
        this.artifactBuild = artifactBuild;
        return this;
    }

    public boolean isBuilt() {
        return built;
    }

    public ArtifactState setBuilt(boolean built) {
        this.built = built;
        return this;
    }

    public boolean isDeployed() {
        return deployed;
    }

    public ArtifactState setDeployed(boolean deployed) {
        this.deployed = deployed;
        return this;
    }

    public boolean isFailed() {
        return failed;
    }

    public ArtifactState setFailed(boolean failed) {
        this.failed = failed;
        return this;
    }
}
