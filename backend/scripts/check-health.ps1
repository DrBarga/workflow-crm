$ErrorActionPreference = 'Stop'

$health = Invoke-RestMethod -Uri 'http://localhost:8080/actuator/health/readiness'

if ($health.status -ne 'UP') {
    throw "Readiness health is $($health.status)"
}

$requiredComponents = @('db', 'rabbit', 'redis', 'minio')
foreach ($component in $requiredComponents) {
    if ($health.components.$component.status -ne 'UP') {
        throw "$component health is not UP"
    }
}

$health | ConvertTo-Json -Depth 8
