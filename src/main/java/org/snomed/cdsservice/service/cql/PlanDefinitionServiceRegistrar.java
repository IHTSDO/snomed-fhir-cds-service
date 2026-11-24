package org.snomed.cdsservice.service.cql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Discovers FHIR knowledge content at startup and registers one {@link PlanDefinitionCDSService} bean per
 * PlanDefinition. Each subdirectory of {@code rules.plandefinition.directory} is one content set (e.g. a FHIR
 * package, or a curated subset of one) holding the PlanDefinitions and everything they depend on.
 * <p>
 * Only entry points are registered: a PlanDefinition composed by another PlanDefinition of the same content set (an
 * action's {@code definitionCanonical}, e.g. a WHO decision table inside a national schedule) is served through it.
 * <p>
 * The hooks are the names of the {@code named-event} triggers in the PlanDefinition's actions, or
 * {@code rules.plandefinition.default-hook} when it declares none. A CDS Hooks service has one hook, so a
 * PlanDefinition with several (e.g. {@code encounter-start} and {@code encounter-discharge}) is registered once per
 * hook, as {@code <PlanDefinition id>-<hook>}; with one hook the service id is the PlanDefinition id. Only ids, urls,
 * triggers and action definitions are peeked here; loading and compilation happen in each service's own
 * initialisation.
 */
@Component
public class PlanDefinitionServiceRegistrar implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

	private Environment environment;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
		String directory = environment.getProperty("rules.plandefinition.directory");
		if (directory == null || directory.isBlank()) {
			logger.info("No rules.plandefinition.directory configured; no PlanDefinition CDS services registered.");
			return;
		}
		File[] contentSets = new File(directory).listFiles(File::isDirectory);
		if (contentSets == null) {
			logger.warn("PlanDefinition content directory '{}' not found; no PlanDefinition CDS services registered.", directory);
			return;
		}
		String defaultHook = environment.getProperty("rules.plandefinition.default-hook", "patient-view");
		Arrays.sort(contentSets, Comparator.comparing(File::getName));
		for (File contentSet : contentSets) {
			File[] files = contentSet.listFiles((dir, name) -> name.endsWith(".json"));
			if (files == null) {
				continue;
			}
			Arrays.sort(files, Comparator.comparing(File::getName));
			List<JsonNode> planDefinitions = new ArrayList<>();
			Set<String> composed = new HashSet<>();
			for (File file : files) {
				JsonNode resource = read(file);
				if ("PlanDefinition".equals(resource.path("resourceType").asText())) {
					planDefinitions.add(resource);
					collectComposedPlanDefinitions(resource.path("action"), composed);
				}
			}
			for (JsonNode planDefinition : planDefinitions) {
				if (!composed.contains(planDefinition.path("url").asText())) {
					String planDefinitionId = planDefinition.path("id").asText();
					List<String> hooks = hooks(planDefinition, defaultHook);
					for (String hook : hooks) {
						String serviceId = hooks.size() == 1 ? planDefinitionId : planDefinitionId + "-" + hook;
						register(registry, contentSet, serviceId, planDefinitionId, hook);
					}
				}
			}
		}
	}

	private void register(BeanDefinitionRegistry registry, File contentSet, String serviceId, String planDefinitionId, String hook) {
		String beanName = "planDefinitionCdsService_" + serviceId;
		if (registry.containsBeanDefinition(beanName)) {
			throw new IllegalStateException("Two content sets declare CDS service '%s'.".formatted(serviceId));
		}
		BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(PlanDefinitionCDSService.class)
				.addConstructorArgValue(serviceId)
				.addConstructorArgValue(contentSet.getPath())
				.addConstructorArgValue(planDefinitionId)
				.addConstructorArgValue(hook);
		registry.registerBeanDefinition(beanName, builder.getBeanDefinition());
		logger.info("Registered PlanDefinition CDS service definition '{}' (PlanDefinition/{}, hook '{}') from '{}'.",
				serviceId, planDefinitionId, hook, contentSet);
	}

	private void collectComposedPlanDefinitions(JsonNode actions, Set<String> composed) {
		for (JsonNode action : actions) {
			String definition = action.path("definitionCanonical").asText("");
			if (definition.contains("/PlanDefinition/")) {
				composed.add(definition.split("\\|")[0]);
			}
			collectComposedPlanDefinitions(action.path("action"), composed);
		}
	}

	static List<String> hooks(JsonNode planDefinition, String defaultHook) {
		Set<String> hooks = new LinkedHashSet<>();
		for (JsonNode action : planDefinition.path("action")) {
			for (JsonNode trigger : action.path("trigger")) {
				if ("named-event".equals(trigger.path("type").asText()) && trigger.hasNonNull("name")) {
					hooks.add(trigger.get("name").asText());
				}
			}
		}
		return hooks.isEmpty() ? List.of(defaultHook) : List.copyOf(hooks);
	}

	private JsonNode read(File file) {
		try {
			return objectMapper.readTree(file);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read FHIR resource '%s'.".formatted(file), e);
		}
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		// No-op: bean definitions are registered in postProcessBeanDefinitionRegistry.
	}
}
