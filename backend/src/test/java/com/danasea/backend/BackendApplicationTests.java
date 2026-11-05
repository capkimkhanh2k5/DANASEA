package com.danasea.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.danasea.backend.configs.AbstractExternalInfrastructureIntegrationTest;

@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests extends AbstractExternalInfrastructureIntegrationTest {

	@MockitoBean
	private io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager<byte[]> proxyManager;

	@Autowired
	private RequestMappingHandlerMapping requestMappingHandlerMapping;

	@Test
	void contextLoads() {
	}

	@Test
	void allApplicationEndpointsAreDiscoverable() throws Exception {
		Set<String> endpoints = requestMappingHandlerMapping.getHandlerMethods().entrySet().stream()
				.filter(entry -> entry.getValue().getBeanType().getPackageName().startsWith("com.danasea.backend"))
				.flatMap(entry -> entry.getKey().getPatternValues().stream()
						.flatMap(pattern -> entry.getKey().getMethodsCondition().getMethods().stream()
								.map(method -> method.name() + " " + pattern)))
				.collect(Collectors.toSet());

        Set<String> documented = new HashSet<>();
        Pattern endpointLine = Pattern.compile("^- \\[x\\] (GET|POST|PUT|PATCH|DELETE) (/[^\\s?]+).*$");
        for (String line : Files.readAllLines(Path.of("docs", "danasea-api-tracking.md"))) {
            var match = endpointLine.matcher(line);
            if (match.matches()) {
                documented.add(match.group(1) + " " + match.group(2));
            }
        }
        Files.createDirectories(Path.of("target", "test-artifacts"));
        Files.write(Path.of("target", "test-artifacts", "api-endpoints.txt"), endpoints.stream().sorted().toList());
        assertEquals(endpoints, documented, "Tracking must match all controller HTTP method/path mappings.");
	}

}
