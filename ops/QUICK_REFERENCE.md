# Sprint 18 Operational Quick Reference

## Startup

```powershell
# Core stack
docker compose up -d
docker compose ps

# Monitoring (optional)
docker compose --profile monitoring up -d

# Health check
curl http://localhost:8080/actuator/health
```

---

## Deployment (3 Steps)

```powershell
# 1. Pre-Deploy
.\ops\backup\postgres_backup.ps1 -Label predeploy
.\ops\migration\precheck.ps1

# 2. Deploy
git pull origin main
docker compose build neobank-app
docker compose up -d neobank-app
docker compose logs -f neobank-app --tail 50

# 3. Post-Deploy
.\ops\migration\postcheck.ps1
curl "http://localhost:9090/api/v1/query?query=increase(http_server_requests_seconds_count{status=%225..%22}[5m])"
```

---

## Quick Diagnostics

| Problem | Command |
|---------|---------|
| App down? | `curl http://localhost:8080/actuator/health` |
| DB down? | `docker compose exec -T postgres psql -U neobank -d neobank -c "SELECT now();"` |
| 5xx errors? | `curl "http://localhost:9090/api/v1/query?query=increase(http_server_requests_seconds_count{status=%225..%22}[5m])"` |
| Outbox issues? | `docker compose exec -T postgres psql -U neobank -d neobank -c "SELECT status, count(*) FROM outbox_events GROUP BY status;"` |
| Kafka problems? | `docker compose logs kafka \| tail -20` |

---

## Incident Response (Immediate)

1. **Assess scope:**
   ```powershell
   curl http://localhost:8080/actuator/health
   docker compose ps
   docker compose logs neobank-app --tail 100 | grep ERROR
   ```

2. **Severity:**
   - App down? → SEV-1 (page on-call)
   - 5xx spike? → SEV-2 (investigate)
   - High latency? → SEV-3 (monitor)

3. **Action:**
   - Bug → Rollback previous version (see ROLLBACK_STRATEGY.md)
   - DB state → Restore from backup (see below)
   - Kafka stuck → Restart Kafka, requeue outbox

---

## Backup & Restore

```powershell
# Backup (always before deploy)
.\ops\backup\postgres_backup.ps1 -Label predeploy

# Restore (emergency only, requires app stop)
docker compose stop neobank-app
.\ops\backup\postgres_restore.ps1 -BackupFile ".\ops\backups\<file>.dump" -Force
docker compose up -d neobank-app
curl http://localhost:8080/actuator/health
```

---

## Migration Failure

```powershell
# Check what failed
docker compose exec -T postgres psql -U neobank -d neobank -c \
  "SELECT version, error_message FROM flyway_schema_history WHERE success=false;"

# Option A: Forward fix (preferred)
# Write V{N+1}__fix_*.sql, redeploy

# Option B: Restore from backup (if schema corrupted)
# See "Backup & Restore" above
```

---

## Outbox Backlog

```powershell
# Check status
curl "http://localhost:9090/api/v1/query?query=neobank_outbox_events_failed"

# If growing, check root cause
docker logs neobank-kafka | tail -20
docker compose logs neobank-app | grep -i outbox | tail -10

# Fix root cause, then requeue
docker compose exec -T postgres psql -U neobank -d neobank << EOF
UPDATE outbox_events 
SET status='PENDING', attempt_count=0, error_message=NULL 
WHERE status='FAILED' AND last_attempt_at > NOW() - INTERVAL '1 hour';
EOF
```

---

## Load Testing

```powershell
# Run baseline
.\ops\load\run-load-sanity.ps1 -BaseUrl http://localhost:8080

# Review results
Get-Content .\ops\load\results\login-burst-summary.json | ConvertFrom-Json
Get-Content .\ops\load\results\authenticated-endpoint-summary.json | ConvertFrom-Json
Get-Content .\ops\load\results\transfer-flow-summary.json | ConvertFrom-Json
```

---

## Alerts in Prometheus

| Alert | Problem | Fix |
|-------|---------|-----|
| NeobankAppDown | App not responding | `docker compose restart neobank-app` |
| High5xxRate | 10+ 5xx in 5min | Check app logs, consider rollback |
| OutboxFailedEventsPresent | Kafka down | Restart Kafka, requeue events |
| OutboxBacklogGrowing | Events stuck >3min | Check Kafka, processor logs |
| KafkaPublishErrorsDetected | Kafka unreachable | Network/Kafka restart |
| TransferFailureSpike | Many transfers failing | Check risk policies, transfer logs |

---

## Dashboards & Metrics

- **Grafana:** http://localhost:3000 (admin/admin)
  - Dashboard: "Neobank Operational Overview"
  - Key panels: Request rate, 5xx errors, outbox status, backlog age

- **Prometheus:** http://localhost:9090
  - Query example: `up{job="neobank-app"}`
  - Alert rules: http://localhost:9090/alerts

- **Alertmanager:** http://localhost:9093
  - Firing alerts: http://localhost:9093/#/alerts

---

## Rollback Decision (Quick Matrix)

| Issue | Safe Rollback? | Action |
|-------|----------------|--------|
| App code bug | YES (if schema compat) | Deploy previous app version |
| Migration bug | MAYBE (if additive only) | Forward-fix migration preferred |
| Data corruption | NO (need restore) | Stop writes, restore from backup, redeploy |
| Unknown | UNCERTAIN | Engage architect, avoid changes |

**See SPRINT18_ROLLBACK_STRATEGY.md for full decision matrix.**

---

## Scheduled Tasks

- **Weekly:** Run 1-2 load test scenarios, compare p95 latency to baseline
- **Monthly:** Execute one failure drill (DB, Redis, or Kafka unavailability)
- **Before each deploy:** Backup + precheck + postcheck
- **Post-incident:** Review incident log, update runbook if gaps found

---

## Documentation Links

- **Detailed Runbook:** SPRINT18_OPERATIONS_RUNBOOK_ENHANCED.md
- **Rollback Strategy:** SPRINT18_ROLLBACK_STRATEGY.md
- **Migration Governance:** SPRINT18_MIGRATION_GOVERNANCE.md
- **Failure Drills:** SPRINT18_FAILURE_DRILLS.md
- **Validation Checklist:** SPRINT18_VALIDATION_CHECKLIST.md

---

## Support & Escalation

- **L1 (First responder):** Triage, restart services, gather logs
- **L2 (Backend lead):** Deployment decisions, rollback approval
- **L3 (Architect):** Data integrity decisions, restore procedures

**Incident bridge:** `#incidents` Slack channel (establish on-call rotation)

---

**Last Updated:** Sprint 18  
**Print & Post:** Ops desk, on-call runbooks, team wiki

