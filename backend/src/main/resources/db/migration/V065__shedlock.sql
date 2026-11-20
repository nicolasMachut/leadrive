-- ShedLock : verrou distribue partage entre replicas pour @Scheduled.
-- Sans cette table, en multi-replica (Railway scale-out, rolling deploy),
-- les jobs s'executeraient en parallele = double envoi Twilio / Anthropic.
--
-- Schema impose par ShedLock JdbcTemplateLockProvider :
--   - name : nom logique du lock (1 par @SchedulerLock)
--   - lock_until : timestamp jusqu'auquel le lock est detenu
--   - locked_at : timestamp de prise du lock
--   - locked_by : identifiant du replica detenteur (auto = hostname)
CREATE TABLE shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
