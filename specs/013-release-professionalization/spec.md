# Feature Specification: Public Release Professionalization & Repository Discoverability (spec-013)

**Feature Branch**: `013-release-professionalization`

**Created**: 2026-09-19

**Status**: Implemented

**Input**: Owner directive (2026-09-19): the GitHub release titles and bodies
are not formal, and they contain conversational content addressed to the owner
although the repository is public. Fix ALL releases, and work on SEO and better
discoverability of the repository.

## Problem Statement

The repository is public, but its 22 GitHub releases were written as if they
were chat messages to the owner. An audit (scripts/audit_releases.py,
2026-09-19) catalogued the violations:

1. **Private conversation content**: quotes of the owner's bug reports
   (v1.3.3: full quote; v1.4.1: "The bug you reported" + verbatim quote +
   "Exactly what you experienced"), requests restated as conversation
   (v1.3.4: "You asked to reduce the poll time"; v1.3.5: "You asked for a
   super UI upgrade").
2. **Direct chat address**: "Report back with what passed/failed" (8 QA
   builds), "(thank you!)" (v1.3-test2), "what you need to do".
3. **Internal process references as content**: section headers "App update
   needed? (Constitution Art. XI)", "Spec-driven per the project
   constitution", "findings feed spec 015-e2e-…".
4. **Private operational details**: v1.4.2 instructed re-pairing "the" phone
   with the revocation date (owner-specific device state).
5. **Informal titles**: "Ultra-Debug Release", "UX revolution", "super-UI",
   "unormal" (typo), "The stuck-phone unstick".
6. **SEO gaps**: repo description contains "my first Android app" (personal,
   keyword-poor); topics list is EMPTY; homepage is unset; README has no link
   to the live demo dashboard.

## User Scenarios & Testing

**US1 — A public visitor reads any release page.**
Given any of the 22 releases, the page reads as professional, self-contained
third-person documentation: what changed, why, how to install. No sentence
presupposes a private conversation with the maintainer, quotes owner
messages, or references internal governance artifacts.

**US2 — A search engine / GitHub topic browser finds the repository.**
The repo description states what the product is with searchable keywords;
20 relevant topics are set; the homepage points to the interactive demo;
the README links the demo in its opening section.

**US3 — Release mechanics are preserved.**
Tags, attached APK assets, prerelease/draft flags and target commits are
unchanged after the rewrite; only `name` and `body` change. The README's
version-history links (which point at tags) keep working.

## Functional Requirements

- **FR-001**: All 22 releases get a formal title (`vX.Y.Z — <description>`,
  no internal spec numbers) and a formal body: third-person, structured
  (overview → Highlights/Fixes → Technical notes → Upgrade notes; QA builds:
  purpose → capabilities → Verification scenarios → compatibility).
- **FR-002**: Banned content classes removed from every body (case-insensitive
  scan in the release gate): "you asked", "you reported", "your v1.2",
  "Art. XI", "constitution art", "report back", "thank you",
  "the user report", "my first android app", "unormal", "ultra-debug",
  "ritual", "exactly what you experienced", "spec-0", "specs/", "feeds spec",
  "re-pair the phone", "revoked on sep".
- **FR-003**: Technical facts preserved and accurate: versionCodes, test-gate
  counts, links to QA builds and compare views, benchmark tables, install
  paths (same-signing-key over-install vs uninstall).
- **FR-004**: PATCH only `tag_name` (required by the API), `name`, `body` —
  never assets, flags or targets; verify after write that every release
  still has exactly its original asset set and prerelease flag.
- **FR-005**: Repo description rewritten professionally with keywords
  (Android, parental control, Wi-Fi data limit, VPN, Cloudflare Workers,
  offline-first, bilingual), ≤ 350 chars.
- **FR-006**: Exactly 20 topics set (android, kotlin, parental-controls,
  screen-time, digital-wellbeing, child-safety, family, vpn, vpn-service,
  wifi, data-usage, mobile-data, cloudflare-workers, cloudflare-d1,
  serverless, offline-first, dashboard, bilingual, farsi, android-app).
- **FR-007**: Homepage set to the interactive parent-dashboard demo
  (https://wifi-data-guard.gigaspaceturnip.workers.dev/demo). Note: if the
  security hardening spec ever removes /demo, the homepage must be updated.
- **FR-008**: README: add a "Try the live demo" line to the opening section
  and a Cloudflare Workers badge (additive only — no anchor changes).
- **FR-009**: Roadmap renumbered: this spec takes 013; pending items become
  014-cloud-security-hardening, 015-play-store-hardening,
  016-e2e-verification-v1.4.2 (no spec directories exist for them yet, so
  renumbering is a spec-001 table edit).

## Failure Modes

- **PATCH drops assets / flags** → mitigated by patching only name/body/tag
  and re-fetching assets + prerelease flags in the verification pass (FR-004).
- **`tag_name` required by PATCH** → always send the release's existing tag.
- **Banned-word false positives** → curated list (FR-002); product UI glyphs
  (🔋 ⚡ 🧪 ⏱ ∞) and technical terms (mojibake, fail-closed) remain allowed.
- **Rewriting git history instead of releases** → explicitly rejected:
  force-pushing rewritten commits would detach existing tags/releases and
  buys nothing the release rewrite doesn't already deliver.
- **English-only bodies** → accepted: GitHub release pages are
  English-first; the project's bilingual commitment (Art. V) applies to app
  strings; v1.2's existing Farsi summary is preserved (formalized).
- **Token leakage in scripts** → bodies/scripts contain no secrets; token
  read from /home/z/my-project/.gh-token at runtime, never committed (Art. IV).

## Constitution Touches

I (full Spec Kit flow for this change), IV (no secrets in artifacts),
V (Farsi summary preserved in v1.2; bilingual UI untouched), XI (disclosure:
repo/metadata only — NO app update, NO worker change, nothing to install).

## Implementation Notes

- Release data lives in scripts/spec013_bodies.py (tag → {name, body});
  scripts/release_professionalize_spec013.py applies + verifies.
- The verification pass asserts: 22 releases, expected names, zero banned
  phrases, original asset names/prerelease flags intact, repo
  description/homepage/topics applied.
- Commit messages were audited and are already conventional-commits formal;
  no history rewrite (rejected above).
