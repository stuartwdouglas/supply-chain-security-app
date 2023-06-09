package io.github.stuartwdouglas.supply;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.github.stuartwdouglas.supply.model.ComponentBuild;
import io.quarkiverse.githubapp.GitHubClientProvider;
import io.quarkiverse.githubapp.event.PullRequest;
import io.quarkiverse.githubapp.event.PullRequestReview;
import io.quarkiverse.githubapp.event.Star;
import io.quarkiverse.githubapp.event.WorkflowRun;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.util.HashUtil;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.cyclonedx.BomParserFactory;
import org.cyclonedx.exception.ParseException;
import org.cyclonedx.model.Bom;
import org.kohsuke.github.GHArtifact;
import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCheckRunBuilder;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.function.InputStreamFunction;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
@Startup
@ApplicationScoped
public class GithubIntegration {

    public static final String SUPPLY_CHAIN_CHECK_DONE = "supply-chain-check-done";
    @Inject
    KubernetesClient client;

    @Inject
    GitHubClientProvider gitHub;

    public static final String SUPPLY_CHAIN_CHECK = "Supply Chain Check";


    @PostConstruct
    public void setupWatch() {
        client.resources(ComponentBuild.class).watch(new Watcher<ComponentBuild>() {
            @Override
            public void eventReceived(Action action, ComponentBuild componentBuild) {

                try {
                    GHCheckRun checkRun = null;
                    var repo = gitHub.getApplicationClient().getRepository(componentBuild.getSpec().getScmURL());

                    for (var check : repo.getCheckRuns(componentBuild.getSpec().getTag())) {
                        if (check.getName().equals(SUPPLY_CHAIN_CHECK)) { //TODO: check app as well
                            checkRun = check;
                            break;
                        }
                    }

                    if (componentBuild.getStatus().getOutstanding() == 0) {
                        if (componentBuild.getMetadata().getAnnotations() != null) {
                            if (componentBuild.getMetadata().getAnnotations().containsKey(SUPPLY_CHAIN_CHECK_DONE)) {
                                return;
                            }
                        }
                    }
                    String summary = checkRun.getOutput().getSummary();
                    for (var i : componentBuild.getStatus().getArtifactState().entrySet()) {
                        if (i.getValue().isBuilt()) {
                            summary = summary.replace(i.getKey() + "\n", i.getKey() + "[TICK]\n");
                        }
                    }
                    checkRun.update().add(new GHCheckRunBuilder.Output(checkRun.getOutput().getTitle(), summary)).create();

                    componentBuild.getMetadata().getAnnotations().put(SUPPLY_CHAIN_CHECK_DONE, "true");
                    client.resource(componentBuild).update();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(WatcherException e) {

            }
        });
    }

    void onOpen(@PullRequest.Opened GHEventPayload.PullRequest pullRequest) throws IOException {
        pullRequest.getPullRequest().comment("Hello from my GitHub App");
        var cr = pullRequest.getRepository().createCheckRun(SUPPLY_CHAIN_CHECK, pullRequest.getPullRequest().getHead().getSha());
        Log.infof("Creating check for for %s", pullRequest.getPullRequest().getHead().getSha());
        cr.withStatus(GHCheckRun.Status.QUEUED);
        cr.create();
    }

    void onWorkflowCompleted(@WorkflowRun.Completed GHEventPayload.WorkflowRun wfr) throws IOException {
        handleWorkflowRun(wfr.getWorkflowRun());
    }


    //TODO: delete this, just for testing
    void onOpen(@PullRequestReview GHEventPayload.PullRequestReview prc) throws IOException {
        Log.infof("Event");
        for (var wf : prc.getRepository().listWorkflows()) {
            Log.infof("Workflow %s", wf.getName());
            for (var wfr : wf.listRuns()) {
                Log.infof("Workflow Run %s", wfr.getHeadSha());
                handleWorkflowRun(wfr);
            }
        }
    }

    private void handleWorkflowRun(GHWorkflowRun wfr) throws IOException {

        Log.infof("Workflow completed for %s", wfr.getHeadSha());
        GHCheckRun checkRun = null;
        for (var check : wfr.getRepository().getCheckRuns(wfr.getHeadSha())) {
            if (check.getName().equals(SUPPLY_CHAIN_CHECK)) { //TODO: check app as well
                checkRun = check;
                break;
            }
        }
        if (checkRun == null) {
            Log.error("Check run not found");
            return;
        }
        PagedIterable<GHArtifact> artifacts = wfr.listArtifacts();
        for (var artifact : artifacts) {
            Log.infof("Examining artifact %s", artifact.getName());
            if (artifact.getName().equals("sbom.json")) {
                Log.infof("Found sbom.json");
                Bom sbom = artifact.download(new InputStreamFunction<Bom>() {
                    @Override
                    public Bom apply(InputStream input) throws IOException {
                        try (ZipInputStream zip = new ZipInputStream(input)) {
                            var entry = zip.getNextEntry();
                            while (entry != null) {
                                if (entry.getName().equals("sbom.json")) {
                                    byte[] dd = zip.readAllBytes();
                                    System.out.println(new String(dd));
                                    try {
                                        return BomParserFactory.createParser(dd).parse(dd);
                                    } catch (ParseException e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                            }
                        }
                        return null;
                    }
                });
                Log.infof("Parsed SBOM");
                var conclusion = GHCheckRun.Conclusion.SUCCESS;
                List<String> failureList = new ArrayList<>();
                Map<String, List<String>> successList = new HashMap<>();
                for (var i : sbom.getComponents()) {
                    if (i.getPublisher().equals("community")) {
                        conclusion = GHCheckRun.Conclusion.FAILURE;
                        failureList.add(i.getGroup() + ":" + i.getName() + ":" + i.getVersion());
                    } else {
                        successList.computeIfAbsent(i.getPublisher(), s -> new ArrayList<>()).add(i.getGroup() + ":" + i.getName() + ":" + i.getVersion());
                    }
                }
                StringBuilder finalResult = new StringBuilder();

                if (conclusion == GHCheckRun.Conclusion.FAILURE) {
                    finalResult.append(String.format("""
                            <details>
                            <summary>There are %s untrusted artifacts in the result</summary>

                            ```diff
                            """, failureList.size()));
                    for (var i : failureList) {
                        finalResult.append("- ").append(i).append("\n");
                    }
                    finalResult.append("```\n</details>");

                    ComponentBuild componentBuild = new ComponentBuild();
                    componentBuild.getMetadata().setName("pull-request-build-" + wfr.getHeadSha());
                    for (var pr : wfr.getPullRequests()) {
                        componentBuild.getSpec().setPrURL(pr.getUrl().toExternalForm());
                    }
                    componentBuild.getSpec().setArtifacts(failureList);
                    componentBuild.getSpec().setTag(wfr.getHeadSha());
                    componentBuild.getSpec().setScmURL(wfr.getRepository().getName());
                    client.resources(ComponentBuild.class).resource(componentBuild).create();

                } else {
                    for (var e : successList.entrySet()) {
                        finalResult.append(String.format("""
                                <details>
                                <summary>There are %s artifacts from %s in the result/summary>

                                ```diff
                                """, e.getValue().size(), e.getKey()));
                        for (var i : e.getValue()) {
                            finalResult.append("+ ").append(i).append("\n");
                        }
                        finalResult.append("```\n</details>");
                    }
                }

                var output = new GHCheckRunBuilder.Output(failureList.size() > 0 ? "Build Contained Untrusted Dependencies" : "All dependencies are trusted", finalResult.toString());
                checkRun.update().withConclusion(conclusion).add(output).withStatus(GHCheckRun.Status.COMPLETED).create();
                break;
            }
        }
    }
}
