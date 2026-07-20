# Security incident response

Report suspected vulnerabilities privately to the repository owner; do not include live credentials, reset links, private formations, chat evidence or personal data in public issues.

1. Preserve timestamps, request/correlation IDs and narrowly necessary evidence. Do not copy secrets into tickets or chat.
2. Contain access: disable affected accounts, revoke sessions, rotate exposed database/SMTP/deployment credentials, and restrict network paths. Rotation precedes any Git history cleanup.
3. Determine affected users, matches, ratings, messages and time window from authoritative database/audit sources. Treat active match snapshots and report evidence as sensitive.
4. Eradicate the cause, add a regression test, review adjacent boundaries, and deploy through the normal reviewed pipeline.
5. Recover from a verified protected backup if integrity is uncertain; reconcile rating changes exactly once and validate Flyway state before reopening traffic.
6. Notify affected parties and authorities as required by applicable law and policy. State confirmed facts and uncertainty; do not claim guaranteed containment.
7. Record a blameless post-incident timeline, detection gaps, rotations, data handling, corrective owners and deadlines.

Keep an offline contact path and tested database restore procedure. Logs and browser artifacts have short retention; preserve them under access control only when an active investigation requires it.

Production rollback assets are deliberately separated: Vercel can promote the prior frontend deployment; `/usr/local/bin/gotg-deploy-backend` can atomically restore the prior versioned GOTG JAR; and the timestamped nginx backup can be restored only after `nginx -t`. Database incidents use the existing pgBackRest cluster recovery process—never Flyway clean or ad-hoc destructive reverse migrations. Do not restart, redeploy, or alter RomanLMS while containing a GOTG-only incident unless evidence proves shared infrastructure is affected.
