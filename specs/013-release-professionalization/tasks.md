# Tasks: 013-release-professionalization

## 1. Spec / Plan
- [x] T001 Audit all 22 live releases + repo metadata (scripts/audit_releases.py); catalogue violations (private quotes, chat address, internal refs, informal titles, SEO gaps)
- [x] T002 spec.md (US1–US3, FR-001..009, failure modes incl. history-rewrite rejection)
- [x] T003 plan.md (D1–D8, verification gates, rollback snapshot)
- [x] T004 tasks.md + checklists/requirements.md

## 2. Content (scripts/spec013_bodies.py)
- [x] T010 Formal titles for all 22 releases (vX.Y.Z — description; QA = "QA build: <area>"); no spec numbers, no informal labels
- [x] T011 Rewrite 12 production bodies (overview → highlights/fixes → technical → upgrade notes → installation; v1.2 keeps a formalized Farsi summary)
- [x] T012 Rewrite 10 QA bodies (purpose → capabilities → verification scenarios); remove every "report back"/internal-spec reference
- [x] T013 Preserve technical facts: versionCodes, test counts, QA links, compare links, benchmark tables, install paths

## 3. Apply + verify (scripts/release_professionalize_spec013.py)
- [x] T020 Snapshot pre-rewrite state (titles/bodies/assets/flags → spec013_prerewrite_snapshot.json)
- [x] T021 PATCH all 22 releases ({tag_name, name, body} only)
- [x] T022 PATCH repo description + homepage; PUT 20 topics
- [x] T023 Verification pass: 22 releases, expected names, banned-phrase scan = 0, assets+prerelease flags unchanged, metadata applied

## 4. Repo docs
- [x] T030 README: demo link in the opening section + Cloudflare Workers badge (additive only)
- [x] T031 spec-001: roadmap ✓ 013 row, renumber A/B/C → 014/015/016, fix stale "Latest release" line → v1.4.2
- [x] T032 Commit + push (token hygiene, Art. IV); spec-013 status Implemented, boxes checked
