package io.quarkus.kubernetes.deployment;

import static io.quarkus.container.spi.ImageReference.DEFAULT_TAG;
import static io.quarkus.deployment.builditem.ApplicationInfoBuildItem.UNSET_VALUE;
import static io.quarkus.kubernetes.deployment.Constants.DEPLOYMENT_TARGET;
import static io.quarkus.kubernetes.deployment.Constants.OPENSHIFT;
import static io.quarkus.kubernetes.deployment.Constants.S2I;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkus.deployment.util.DeploymentUtil;

public class KubernetesConfigUtil {

    /**
     * It should be the same name as in VertxHttpProcessor.kubernetesForManagement.
     */
    public static final String MANAGEMENT_PORT_NAME = "management";

    private static final String DEKORATE_PREFIX = "dekorate.";
    private static final Pattern QUARKUS_DEPLOY_PATTERN = Pattern.compile("quarkus\\.([^\\.]+)\\.deploy");

    /**
     * Get the explicit configured deployment target, if any.
     * The explicit deployment target is determined using: `quarkus.kubernetes.deployment-target=<deployment-target>`
     */
    public static Optional<String> getExplicitlyConfiguredDeploymentTarget() {
        Config config = ConfigProvider.getConfig();
        return config.getOptionalValue(DEPLOYMENT_TARGET, String.class);
    }

    /**
     * The the explicitly configured deployment target list.
     * The configured deployment targets are determined using: `quarkus.kubernetes.deployment-target=<deployment-target>`
     */
    public static List<String> getExplictilyDeploymentTargets() {
        String deploymentTargets = getExplicitlyConfiguredDeploymentTarget().orElse("");
        if (deploymentTargets.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(deploymentTargets
                .split(Pattern.quote(",")))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toList());
    }

    /**
     * Get the user configured deployment target, if any.
     * The configured deployment target is determined using:
     * 1. the value of `quarkus.kubernetes.deployment-target=<deployment-target>`
     * 2. the presence of `quarkus.<deployment-target>.deploy=true`
     */
    public static Optional<String> getConfiguredDeploymentTarget() {
        Config config = ConfigProvider.getConfig();
        return getExplicitlyConfiguredDeploymentTarget().or(() -> DeploymentUtil.getEnabledDeployer());
    }

    /**
     * The the configured deployment target list.
     * The configured deployment targets are determined using:
     * 1. the value of `quarkus.kubernetes.deployment-target=<deployment-target>`
     * 2. the presenve of `quarkus.<deployment-target>.deploy=true`
     */
    public static List<String> getConfiguratedDeploymentTargets() {
        String deploymentTargets = getConfiguredDeploymentTarget().orElse("");
        if (deploymentTargets.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(deploymentTargets
                .split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toList());
    }

    public static boolean isDeploymentEnabled() {
        return DeploymentUtil.isDeploymentEnabled("kubernetes", "openshift", "knative", "kind", "minikube");
    }

    /*
     * Collects configuration properties for Kubernetes. Reads all properties and
     * matches properties that match known Dekorate generators. These properties may
     * or may not be prefixed with `quarkus.` though the prefixed ones take
     * precedence.
     *
     * @return A map containing the properties.
     */
    public static Map<String, Object> toMap(PlatformConfiguration... platformConfigurations) {
        Map<String, Object> result = new HashMap<>();

        // Most of quarkus prefixed properties are handled directly by the config items (KubernetesConfig, OpenshiftConfig, KnativeConfig)
        // We just need group, name & version parsed here, as we don't have decorators for these (low level properties).
        Map<String, Object> quarkusPrefixed = new HashMap<>();

        Arrays.stream(platformConfigurations).forEach(p -> {
            p.getPartOf().ifPresent(g -> quarkusPrefixed.put(DEKORATE_PREFIX + p.getConfigName() + ".part-of", g));
            p.getName().ifPresent(n -> quarkusPrefixed.put(DEKORATE_PREFIX + p.getConfigName() + ".name", n));
            p.getVersion()
                    .map(v -> v.equals(UNSET_VALUE) ? DEFAULT_TAG : v)
                    .ifPresent(v -> quarkusPrefixed.put(DEKORATE_PREFIX + p.getConfigName() + ".version", v));
        });

        result.putAll(quarkusPrefixed);
        result.putAll(toS2iProperties(quarkusPrefixed));
        return result;
    }

    public static boolean managementPortIsEnabled() {
        return ConfigProvider.getConfig().getOptionalValue("quarkus.management.enabled", Boolean.class).orElse(false);
    }

    private static Map<String, Object> toS2iProperties(Map<String, Object> map) {
        Map<String, Object> result = new HashMap<>();
        map.forEach((k, v) -> {
            if (k.contains(OPENSHIFT)) {
                result.put(k.replaceAll(OPENSHIFT, S2I), v);
            }
        });
        return result;
    }
}
