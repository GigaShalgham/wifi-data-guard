# Implementation Plan: 013-release-professionalization

## Decisions

- **D1 — Scope = release metadata + repo metadata + README touch-ups.**
  No app code, no worker code, no git-history rewrite (rejected in the spec's
  failure modes). The deliverable is what a public visitor sees.
- **D2 — Data/logic split.** All 22 rewritten titles+bodies live in
  `scripts/spec013_bodies.py` (pure data module); the PATCH/verify logic in
  `scripts/release_professionalize_spec013.py`. Keeps the long content
  reviewable and the logic small.
- **D3 — PATCH semantics.** For each release: `PATCH /repos/…/releases/{id}`
  with `{tag_name, name, body}` only. `tag_name` is mandatory in PATCH —
  always the existing tag. Assets, prerelease/draft flags and targets are
  never included in the payload.
- **D4 — Structure per release class.**
  Production: `## vX.Y.Z — title` + overview paragraph + `### Highlights` /
  `### Fixes` / `### Technical` + `### Upgrade notes` + `### Installation`
  (with QA-build link where one exists).
  QA: `## vX.Y.Z-testN — QA build: <area>` + purpose paragraph + capabilities
  + `### Verification scenarios` (numbered, imperative, no addressee).
- **D5 — Tone rules.** Third person; imperative only in install/test
  instructions (standard for release notes); no conversational references; no
  internal governance headers; keep product UI glyph names (🔋 ⚡ 🧪 ⏱ ∞) and
  technical terms; keep the v1.2 Farsi summary (formalized).
- **D6 — Repo metadata.** Description (≤350 chars, keyword-rich, no personal
  framing); homepage = live /demo; 20 topics via `PUT /repos/…/topics`.
- **D7 — README is additive-only.** Demo line in the opening section + one
  badge. No anchor/TOC changes (links from elsewhere must keep resolving).
- **D8 — Roadmap renumber.** spec-001 table: pending A/B/C → 014/015/016;
  add the ✓ 013 row; also fix the stale "Latest release: v1.4.1" line to
  v1.4.2 (docs accuracy, found during this spec).

## Verification Gates

1. `audit_releases.py` re-run AFTER the rewrite: 22 releases; every name
   matches the expected formal title; banned-phrase scan (FR-002 list,
   case-insensitive) = 0 hits across all titles+bodies; asset names and
   prerelease flags identical to the pre-rewrite snapshot.
2. Repo metadata re-fetch: description, homepage, 20 topics applied.
3. README rendered diff check (additions only).
4. No secrets in any artifact (Art. IV) — grep the bodies module for token
   patterns before commit.

## Risks

- GitHub API rate limits → 22 PATCHes + a few GETs, well within limits.
- Live-toot: none — no worker/app changes, so no deployment risk at all.
- Rollback: the pre-rewrite snapshot (titles+bodies+assets+flags) is saved to
  `/home/z/my-project/scripts/spec013_prerewrite_snapshot.json` by the apply
  script; a restore script can PUT the originals back.
