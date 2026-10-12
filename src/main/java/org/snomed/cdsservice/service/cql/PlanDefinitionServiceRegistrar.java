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
import java.util.Arrays;
import java.util.Comparator;

/**
 * Discovers FHIR knowledge content at startup and registers one {@link PlanDefinitionCDSService} bean per
 * PlanDefinition. Each subdirectory of {@code rules.plandefinition.directory} is one content set (e.g. a FHIR
 * package, or a curated subset of one) holding the PlanDefinitions and everything they depend on.
 * <p>
 * The service id is the PlanDefinition id. The hook is the name of the first {@code named-event} trigger in the
 * PlanDefinition's actions, or {@code rules.plandefinition.default-hook} when it declares none. Only the id and
 * triggers are peeked here; loading and compilation happen in each service's own initialisation.
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
			for (File file : files) {
				JsonNode resource = read(file);
				if ("PlanDefinition".equals(resource.path("resourceType").asText())) {
					register(registry, contentSet, resource.path("id").asText(), hook(resource, defaultHook));
				}
			}
		}
	}

	private void register(BeanDefinitionRegistry registry, File contentSet, String planDefinitionId, String hook) {
		String beanName = "planDefinitionCdsService_" + planDefinitionId;
		if (registry.containsBeanDefinition(beanName)) {
			throw new IllegalStateException("Two content sets declare PlanDefinition '%s'.".formatted(planDefinitionId));
		}
		BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(PlanDefinitionCDSService.class)
				.addConstructorArgValue(planDefinitionId)
				.addConstructorArgValue(contentSet.getPath())
				.addConstructorArgValue(planDefinitionId)
				.addConstructorArgValue(hook);
		registry.registerBeanDefinition(beanName, builder.getBeanDefinition());
		logger.info("Registered PlanDefinition CDS service definition '{}' (hook '{}') from '{}'.", planDefinitionId, hook, contentSet);
	}

	private String hook(JsonNode planDefinition, String defaultHook) {
		for (JsonNode action : planDefinition.path("action")) {
			for (JsonNode trigger : action.path("trigger")) {
				if ("named-event".equals(trigger.path("type").asText()) && trigger.hasNonNull("name")) {
					return trigger.get("name").asText();
				}
			}
		}
		return defaultHook;
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
