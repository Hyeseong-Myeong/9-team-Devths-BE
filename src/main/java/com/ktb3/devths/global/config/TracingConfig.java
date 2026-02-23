package com.ktb3.devths.global.config;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.actuate.autoconfigure.tracing.ConditionalOnEnabledTracing;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetry;

@Configuration
@ConditionalOnEnabledTracing
public class TracingConfig {

	private static final List<String> EXCLUDED_PATHS = List.of(
		"/actuator/**",
		"/actuator/health",
		"/actuator/info",
		"/actuator/prometheus",
		"/actuator/metrics/**"
	);

	@Bean
	public ObservationPredicate skipActuatorEndpointsFromTracing() {
		PathMatcher pathMatcher = new AntPathMatcher();

		return (name, context) -> {
			if (context instanceof ServerRequestObservationContext serverContext) {
				String path = serverContext.getCarrier().getRequestURI();
				boolean shouldExclude = EXCLUDED_PATHS.stream()
					.anyMatch(pattern -> pathMatcher.match(pattern, path));
				// true를 반환하면 observation을 수행, false를 반환하면 건너뜀
				return !shouldExclude;
			}
			return true;
		};
	}

	@Bean
	public ObservedAspect observedAspect(ObservationRegistry registry) {
		return new ObservedAspect(registry);
	}

	/**
	 * JDBC DataSource를 OpenTelemetry로 wrap하여 DB 쿼리 추적
	 */
	@Bean
	@ConditionalOnClass(name = "io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetry")
	public BeanPostProcessor dataSourceInstrumentationBeanPostProcessor(OpenTelemetry openTelemetry) {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
				if (bean instanceof DataSource) {
					return JdbcTelemetry.create(openTelemetry).wrap((DataSource)bean);
				}
				return bean;
			}
		};
	}
}
