package io.github.stuartwdouglas.supply.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group(ModelConstants.GROUP)
@Version(ModelConstants.VERSION)
@JsonInclude(Include.NON_NULL)
public class ComponentBuild extends CustomResource<ComponentBuildSpec, ComponentBuildStatus>
        implements Namespaced {

    @Override
    protected ComponentBuildSpec initSpec() {
        return new ComponentBuildSpec();
    }

    @Override
    protected ComponentBuildStatus initStatus() {
        return new ComponentBuildStatus();
    }
}
