package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.ObjectExportSettings;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ObjectExportSettings.class)
class ObjectExportConfiguration {}
