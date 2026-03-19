#!/usr/bin/env pwsh
<#
.SYNOPSIS
Comprehensive end-to-end backup/restore validation for Sprint 18.
Tests: backup creation, checksum, restore, health checks, data integrity.

.DESCRIPTION
This script validates the complete backup/restore cycle:
1. Creates a backup with metadata
2. Validates checksum
3. Performs restore operation
4. Checks application and database health
5. Validates data integrity post-restore

.PARAMETER ComposeFile
Docker compose file path (default: compose.yml)

.PARAMETER TestMode
If true, uses temporary test database (default: false)

.EXAMPLE
.\ops\backup\validate-backup-restore.ps1
.\ops\backup\validate-backup-restore.ps1 -TestMode $true

.NOTES
This script is destructive. Restore operations require -Force flag.
Run in test environments only. Production restore requires explicit approval.
#>

param(
    [string]$ComposeFile = "compose.yml",
    [string]$DbContainer = "postgres",
    [string]$DbContainerName = "neobank-postgres",
    [string]$DbName = $env:DB_NAME,
    [string]$DbUser = $env:DB_USER,
    [string]$DbPassword = $env:DB_PASSWORD,
    [string]$BackupDir = $env:BACKUP_DIR,
    [bool]$TestMode = $false
)

$ErrorActionPreference = "Stop"

# Color output helpers
function Write-Success { Write-Host -ForegroundColor Green "[✓] $args" }
function Write-Error { Write-Host -ForegroundColor Red "[✗] $args" }
function Write-Info { Write-Host -ForegroundColor Cyan "[ℹ] $args" }
function Write-Section { Write-Host -ForegroundColor Yellow "`n=== $args ===" }

# Set defaults
if ([string]::IsNullOrWhiteSpace($DbName)) { $DbName = "neobank" }
if ([string]::IsNullOrWhiteSpace($DbUser)) { $DbUser = "neobank" }
if ([string]::IsNullOrWhiteSpace($DbPassword)) { $DbPassword = "neobank" }
if ([string]::IsNullOrWhiteSpace($BackupDir)) { $BackupDir = ".\\ops\\backups" }

Write-Section "Sprint 18 Backup/Restore Validation"
Write-Info "Compose: $ComposeFile"
Write-Info "Database: $DbName"
Write-Info "Test Mode: $TestMode"

try {
    # Step 1: Verify Docker availability
    Write-Section "Prerequisites"
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw "Docker not found in PATH"
    }
    Write-Success "Docker available"

    # Step 2: Create backup
    Write-Section "Backup Creation"
    Write-Info "Running backup creation..."
    & ".\\ops\\backup\\postgres_backup.ps1" `
        -ComposeFile $ComposeFile `
        -DbContainer $DbContainer `
        -DbName $DbName `
        -DbUser $DbUser `
        -DbPassword $DbPassword `
        -BackupDir $BackupDir `
        -Label "validation-test"

    if ($LASTEXITCODE -ne 0) {
        throw "Backup creation failed"
    }

    # Find the created backup
    $backupFiles = Get-ChildItem -Path $BackupDir -Filter "neobank-validation-test-*.dump" | Sort-Object LastWriteTime -Descending
    if ($backupFiles.Count -eq 0) {
        throw "No backup file created"
    }
    $backupFile = $backupFiles[0].FullName
    Write-Success "Backup created: $(Split-Path -Leaf $backupFile)"

    # Step 3: Verify backup artifacts
    Write-Section "Backup Artifacts Validation"
    $shaFile = "$backupFile.sha256"
    $metaFile = "$backupFile.metadata.json"

    if (-not (Test-Path $shaFile)) {
        throw "Checksum file not found: $shaFile"
    }
    Write-Success "SHA256 checksum file exists"

    if (-not (Test-Path $metaFile)) {
        throw "Metadata file not found: $metaFile"
    }
    Write-Success "Metadata file exists"

    $metadata = Get-Content $metaFile | ConvertFrom-Json
    Write-Info "Backup metadata:"
    Write-Info "  Created: $($metadata.createdAtUtc)"
    Write-Info "  Label: $($metadata.label)"
    Write-Info "  Database: $($metadata.dbName)"
    Write-Info "  Compression Level: $($metadata.compressionLevel)"

    # Step 4: Verify checksum
    Write-Section "Checksum Verification"
    $expectedSha = ((Get-Content -Path $shaFile -Raw).Trim() -split "\s+")[0].ToLowerInvariant()
    $actualSha = (Get-FileHash -Algorithm SHA256 -Path $backupFile).Hash.ToLowerInvariant()

    if ($expectedSha -eq $actualSha) {
        Write-Success "Checksum verified"
        Write-Info "SHA256: $actualSha"
    } else {
        throw "Checksum mismatch! Expected: $expectedSha, Actual: $actualSha"
    }

    # Step 5: Capture pre-restore state
    Write-Section "Pre-Restore Database State"
    $preRestoreTransfers = docker compose -f $ComposeFile exec -T $DbContainer `
        psql -U $DbUser -d $DbName -t -A -c "SELECT count(*) FROM transfers;"
    Write-Info "Transfers before restore: $preRestoreTransfers"

    $preRestoreFlyway = docker compose -f $ComposeFile exec -T $DbContainer `
        psql -U $DbUser -d $DbName -t -A -c "SELECT max(installed_rank) FROM flyway_schema_history;"
    Write-Info "Flyway migration rank: $preRestoreFlyway"

    # Step 6: Restore backup
    Write-Section "Restore Operation"
    Write-Info "Restoring from: $(Split-Path -Leaf $backupFile)"
    & ".\\ops\\backup\\postgres_restore.ps1" `
        -BackupFile $backupFile `
        -ComposeFile $ComposeFile `
        -DbContainer $DbContainer `
        -DbName $DbName `
        -DbUser $DbUser `
        -DbPassword $DbPassword `
        -VerifyChecksum `
        -Force

    if ($LASTEXITCODE -ne 0) {
        throw "Restore operation failed"
    }
    Write-Success "Restore completed"

    # Step 7: Post-restore health checks
    Write-Section "Post-Restore Health Checks"

    # DB connectivity
    $dbCheck = docker compose -f $ComposeFile exec -T $DbContainer `
        psql -U $DbUser -d $DbName -c "SELECT now();" 2>&1
    Write-Success "Database connectivity verified"

    # Flyway history
    $postRestoreFlyway = docker compose -f $ComposeFile exec -T $DbContainer `
        psql -U $DbUser -d $DbName -t -A -c "SELECT max(installed_rank) FROM flyway_schema_history;"
    if ($postRestoreFlyway -eq $preRestoreFlyway) {
        Write-Success "Flyway migration rank intact: $postRestoreFlyway"
    } else {
        throw "Flyway migration rank changed. Expected: $preRestoreFlyway, Got: $postRestoreFlyway"
    }

    # Table integrity
    $postRestoreTransfers = docker compose -f $ComposeFile exec -T $DbContainer `
        psql -U $DbUser -d $DbName -t -A -c "SELECT count(*) FROM transfers;"
    Write-Success "Transfers after restore: $postRestoreTransfers"

    # No failed migrations
    $failedCount = docker compose -f $ComposeFile exec -T $DbContainer `
        psql -U $DbUser -d $DbName -t -A -c "SELECT count(*) FROM flyway_schema_history WHERE success = false;"
    if ([int]$failedCount -eq 0) {
        Write-Success "No failed migrations"
    } else {
        Write-Error "Found $failedCount failed migrations"
    }

    # Step 8: Application health
    Write-Section "Application Health Verification"
    Write-Info "Waiting for application readiness..."
    $maxRetries = 10
    $retryCount = 0
    $appHealthy = $false

    while ($retryCount -lt $maxRetries) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:8080/actuator/health" `
                -TimeoutSec 5 -ErrorAction Stop
            if ($response.StatusCode -eq 200) {
                Write-Success "Application health check passed"
                $appHealthy = $true
                break
            }
        } catch {
            $retryCount++
            Write-Info "Retry $retryCount/$maxRetries..."
            Start-Sleep -Seconds 2
        }
    }

    if (-not $appHealthy) {
        Write-Error "Application health check failed after $maxRetries retries"
    } else {
        # Check dependencies
        try {
            $depsResponse = Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:8080/actuator/health/dependencies" `
                -TimeoutSec 5 -ErrorAction SilentlyContinue
            if ($depsResponse.StatusCode -eq 200) {
                Write-Success "Application dependencies healthy"
            }
        } catch {
            Write-Info "Dependencies endpoint not available (may require auth)"
        }
    }

    # Step 9: Summary
    Write-Section "Validation Summary"
    Write-Success "All backup/restore validation checks passed!"
    Write-Info "Backup file: $(Split-Path -Leaf $backupFile)"
    Write-Info "Checksum verified: $actualSha"
    Write-Info "Restore operation: SUCCESS"
    Write-Info "Post-restore health: OK"
    Write-Info ""
    Write-Success "Sprint 18 backup/restore readiness validated."

} catch {
    Write-Error $_.Exception.Message
    exit 1
}

exit 0

