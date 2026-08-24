package org.snomed.cdsservice.service.rules;

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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

/**
 * Discovers diagnostic rule files at startup and registers one {@link RuleBasedCDSService} bean per
 * {@code service_id}. A domain {@code <name>} is defined by {@code <name>_criteria.tsv} +
 * {@code <name>_rules.tsv} in {@code rules.diagnostic.directory}; the registered beans are then picked up
 * by the normal {@code CDSService} bean discovery, exactly like the medication services.
 * <p>
 * Running as a {@link BeanDefinitionRegistryPostProcessor} lets these data-driven services exist as real
 * bean definitions before the service registry is built. Only the {@code service_id} and {@code hook} are
 * peeked from the file here; full loading and validation happen in each service's own initialisation so
 * structural or terminology problems fail the application fast.
 */
@Component
public class DiagnosticServiceRegistrar implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

	private static final String CRITERIA_SUFFIX = "_criteria.tsv";
	private static final String RULES_SUFFIX = "_rules.tsv";

	private Environment environment;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
		String directory = environment.getProperty("rules.diagnostic.directory");
		if (directory == null || directory.isBlank()) {
			logger.info("No rules.diagnostic.directory configured; no diagnostic CDS services registered.");
			return;
		}
		File dir = new File(directory);
		if (!dir.isDirectory()) {
			logger.warn("Diagnostic rules directory '{}' not found; no diagnostic CDS services registered.", directory);
			return;
		}
		String[] criteriaFiles = dir.list((d, name) -> name.endsWith(CRITERIA_SUFFIX));
		for (String criteriaFileName : new TreeSet<>(Arrays.asList(criteriaFiles == null ? new String[0] : criteriaFiles))) {
			String domain = criteriaFileName.substring(0, criteriaFileName.length() - CRITERIA_SUFFIX.length());
			File rulesFile = new File(dir, domain + RULES_SUFFIX);
			if (!rulesFile.isFile()) {
				logger.warn("Skipping diagnostic domain '{}': no {}{}.", domain, domain, RULES_SUFFIX);
				continue;
			}
			registerService(registry, domain, new File(dir, criteriaFileName).getPath(), rulesFile.getPath());
		}
	}

	private void registerService(BeanDefinitionRegistry registry, String domain, String criteriaPath, String rulesPath) {
		ServiceMeta meta = peekServiceMeta(rulesPath, domain);
		String beanName = "diagnosticCdsService_" + meta.serviceId();
		if (registry.containsBeanDefinition(beanName)) {
			throw new IllegalStateException("Two diagnostic domains declare service_id '%s'.".formatted(meta.serviceId()));
		}
		BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(RuleBasedCDSService.class)
				.addConstructorArgValue(meta.serviceId())
				.addConstructorArgValue(domain)
				.addConstructorArgValue(criteriaPath)
				.addConstructorArgValue(rulesPath)
				.addConstructorArgValue(meta.hook());
		registry.registerBeanDefinition(beanName, builder.getBeanDefinition());
		logger.info("Registered diagnostic CDS service definition '{}' (hook '{}') from '{}'.", meta.serviceId(), meta.hook(), rulesPath);
	}

	private record ServiceMeta(String serviceId, String hook) {
	}

	/** Reads only the header and the first data row to learn the service_id and hook. */
	private ServiceMeta peekServiceMeta(String rulesPath, String domain) {
		try (BufferedReader reader = new BufferedReader(new FileReader(rulesPath))) {
			String headerLine = reader.readLine();
			if (headerLine == null) {
				return new ServiceMeta(domain, "patient-view");
			}
			List<String> headers = Arrays.asList(headerLine.split("\t", -1));
			int serviceIdIndex = headers.indexOf("service_id");
			int hookIndex = headers.indexOf("hook");
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				String[] values = line.split("\t", -1);
				String serviceId = cell(values, serviceIdIndex);
				String hook = cell(values, hookIndex);
				return new ServiceMeta(
						serviceId.isBlank() ? domain : serviceId,
						hook.isBlank() ? "patient-view" : hook);
			}
			return new ServiceMeta(domain, "patient-view");
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read diagnostic rules file '%s'.".formatted(rulesPath), e);
		}
	}

	private String cell(String[] values, int index) {
		return index >= 0 && index < values.length ? values[index].trim() : "";
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		// No-op: bean definitions are registered in postProcessBeanDefinitionRegistry.
	}
}
