package ie.bitstep.mango.flowsink.spring;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

/**
 * {@link ImportSelector} that conditionally imports mango4j instrumentation configurations based on which Spring APIs
 * are present on the classpath (Servlet, Spring MVC, or WebFlux).
 */
public class MangoFlowSinkImportSelector implements ImportSelector {
	@Override
	public String[] selectImports(AnnotationMetadata importingClassMetadata) {
		ClassLoader cl = classLoader();
		List<String> imports = new ArrayList<>();
		imports.add(MangoFlowSinkConfiguration.class.getName());

		if (ClassUtils.isPresent("jakarta.servlet.Filter", cl)) {
			imports.add(MangoServletTraceConfiguration.class.getName());
		}
		if (ClassUtils.isPresent("org.springframework.web.servlet.HandlerInterceptor", cl)) {
			imports.add(MangoServletFlowConfiguration.class.getName());
		}
		if (ClassUtils.isPresent("org.springframework.web.server.WebFilter", cl)) {
			imports.add(MangoWebFluxTraceConfiguration.class.getName());
		}
		return imports.toArray(String[]::new);
	}

	protected ClassLoader classLoader() {
		return getClass().getClassLoader();
	}
}
