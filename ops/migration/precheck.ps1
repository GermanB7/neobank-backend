param(
    [string]$ComposeFile = "compose.yml",
    [string]$DbService = "postgres",
    [string]$DbName = $env:DB_NAME,
    [string]$DbUser = $env:DB_USER
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($DbName)) { $DbName = "neobank" }
if ([string]::IsNullOrWhiteSpace($DbUser)) { $DbUser = "neobank" }

Write-Host "[precheck] verifying database connectivity..."
docker compose -f $ComposeFile exec -T $DbService psql -U $DbUser -d $DbName -c "SELECT now() AS db_time;"
if ($LASTEXITCODE -ne 0) { throw "Database connectivity check failed" }

Write-Host "[precheck] checking Flyway failed migrations..."
$failedCount = docker compose -f $ComposeFile exec -T $DbService psql -U $DbUser -d $DbName -t -A -c "SELECT count(*) FROM flyway_schema_history WHERE success = false;"
if ($LASTEXITCODE -ne 0) { throw "Failed to query flyway_schema_history" }
if ([int]$failedCount -gt 0) {
    throw "Found $failedCount failed Flyway migration record(s). Resolve before rollout."
}

Write-Host "[precheck] latest Flyway entries:"
docker compose -f $ComposeFile exec -T $DbService psql -U $DbUser -d $DbName -c "SELECT installed_rank, version, description, type, success, installed_on FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 15;"
if ($LASTEXITCODE -ne 0) { throw "Failed to fetch Flyway history" }

Write-Host "[precheck] OK - migration precheck passed."
Write-Host "[precheck] Reminder: ensure a fresh backup exists before deployment."

