package org.snomed.cdsservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(CorsProperties.class)
@ConditionalOnProperty(prefix = "app.cors", name = "enabled", havingValue = "true")
public class CorsConfig implements WebMvcConfigurer {

	private final CorsProperties corsProperties;

	public CorsConfig(CorsProperties corsProperties) {
		this.corsProperties = corsProperties;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
				.allowedOriginPatterns(corsProperties.getAllowedOriginPatterns().toArray(new String[0]))
				.allowedMethods(corsProperties.getAllowedMethods().toArray(new String[0]))
				.allowedHeaders(corsProperties.getAllowedHeaders().toArray(new String[0]));
	}
}
