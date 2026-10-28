package dev.workflowcrm.users.infrastructure;

import dev.workflowcrm.users.domain.UserProvisioningProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(UserProvisioningProperties.class)
class UserManagementConfiguration {}
