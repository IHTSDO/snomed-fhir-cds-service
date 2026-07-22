package org.snomed.cdsservice.config;

import org.snomed.cdsservice.service.rules.CodeResolver;
import org.snomed.cdsservice.service.rules.RuleCardFactory;
import org.snomed.cdsservice.service.rules.RuleEngine;
import org.snomed.cdsservice.service.rules.evaluator.CodedResourcePresentCriterionEvaluator;
import org.snomed.cdsservice.service.rules.evaluator.CodingBasedSemanticQualifierMatcher;
import org.snomed.cdsservice.service.rules.evaluator.ContextValueCriterionEvaluator;
import org.snomed.cdsservice.service.rules.evaluator.CriterionEvaluator;
import org.snomed.cdsservice.service.rules.evaluator.CriterionEvaluatorRegistry;
import org.snomed.cdsservice.service.rules.evaluator.ObservationComponentValueCriterionEvaluator;
import org.snomed.cdsservice.service.rules.evaluator.ObservationCriterionSupport;
import org.snomed.cdsservice.service.rules.evaluator.ObservationSemanticQualifierMatcher;
import org.snomed.cdsservice.service.rules.evaluator.ObservationValueCriterionEvaluator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Wires the generic, reusable rule engine as Spring beans. The engine mechanics (evaluators, registry,
 * engine, card factory) are plain classes so they stay unit-testable in isolation; this configuration is
 * the single place that assembles them for the running application. The {@link CodeResolver} is supplied
 * by the terminology-backed {@code EclCodeResolver} component.
 */
@Configuration
public class RulesEngineConfiguration {

	@Bean
	public ObservationSemanticQualifierMatcher observationSemanticQualifierMatcher() {
		return new CodingBasedSemanticQualifierMatcher();
	}

	@Bean
	public ObservationCriterionSupport observationCriterionSupport(CodeResolver codeResolver, ObservationSemanticQualifierMatcher qualifierMatcher) {
		return new ObservationCriterionSupport(codeResolver, qualifierMatcher);
	}

	@Bean
	public ObservationValueCriterionEvaluator observationValueCriterionEvaluator(ObservationCriterionSupport support) {
		return new ObservationValueCriterionEvaluator(support);
	}

	@Bean
	public ObservationComponentValueCriterionEvaluator observationComponentValueCriterionEvaluator(ObservationCriterionSupport support) {
		return new ObservationComponentValueCriterionEvaluator(support);
	}

	@Bean
	public CodedResourcePresentCriterionEvaluator codedResourcePresentCriterionEvaluator(CodeResolver codeResolver) {
		return new CodedResourcePresentCriterionEvaluator(codeResolver);
	}

	@Bean
	public ContextValueCriterionEvaluator contextValueCriterionEvaluator() {
		return new ContextValueCriterionEvaluator();
	}

	@Bean
	public CriterionEvaluatorRegistry criterionEvaluatorRegistry(List<CriterionEvaluator> evaluators) {
		return new CriterionEvaluatorRegistry(evaluators);
	}

	@Bean
	public RuleEngine ruleEngine(CriterionEvaluatorRegistry registry) {
		return new RuleEngine(registry);
	}

	@Bean
	public RuleCardFactory ruleCardFactory() {
		return new RuleCardFactory();
	}
}
