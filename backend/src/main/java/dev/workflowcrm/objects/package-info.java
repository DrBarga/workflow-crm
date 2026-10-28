@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
            "users::api",
            "approvals::api",
            "notifications::api",
            "contacts::api",
            "integrations::api",
            "tasks::api",
            "files::api",
            "audit::api",
            "shared"
        })
package dev.workflowcrm.objects;
