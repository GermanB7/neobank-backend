param(
    [string]$ComposeFile = "compose.yml",
    [string]$DbService = "postgres",
    [string]$DbName = $env:DB_NAME,
    [string]$DbUser = $env:DB_USER,
    [string]$BaseUrl = "http://localhost:8080",
    [switch]$SkipHttpCheck
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($DbName)) { $DbName = "neobank" }
if ([string]::IsNullOrWhiteSpace($DbUser)) { $DbUser = "neobank" }

Write-Host "[postcheck] checking Flyway failed migrations..."
$failedCount = docker compose -f $ComposeFile exec -T $DbService psql -U $DbUser -d $DbName -t -A -c "SELECT count(*) FROM flyway_schema_history WHERE success = false;"
if ($LASTEXITCODE -ne 0) { throw "Failed to query flyway_schema_history" }
if ([int]$failedCount -gt 0) {
    throw "Found $failedCount failed Flyway migration record(s). Investigate rollout before reopening traffic."
}

Write-Host "[postcheck] migration head:"
docker compose -f $ComposeFile exec -T $DbService psql -U $DbUser -d $DbName -c "SELECT installed_rank, version, description, installed_on FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
if ($LASTEXITCODE -ne 0) { throw "Failed to fetch migration head" }

if (-not $SkipHttpCheck) {
    Write-Host "[postcheck] probing application health..."
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/actuator/health" -TimeoutSec 10
        if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 300) {
            throw "Unexpected health status code: $($response.StatusCode)"
        }
        Write-Host "[postcheck] health endpoint OK ($($response.StatusCode))"
    } catch {
        throw "Application health check failed: $($_.Exception.Message)"
    }
}

Write-Host "[postcheck] OK - post-deploy checks passed."

