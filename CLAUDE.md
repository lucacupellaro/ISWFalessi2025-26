/# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

University exam project (ISW2 - Falessi, 2025-2026) for mining software repositories. The tool analyzes Apache projects (currently ZOOKEEPER) by fetching bug tickets from Jira, linking them to Git commits, and producing a labeled dataset (CSV) with software metrics for defect prediction.

## Build & Run

```bash
# Build
mvn compile

# Run Phase 1 (fetch Jira tickets, produce TicketRelease.csv)
mvn exec:java -Dexec.mainClass="org.example.Main" -Dexec.args="phase1"

# Run Phase 2 (clone repo, compute metrics, produce Dataset.csv)
mvn exec:java -Dexec.mainClass="org.example.Main" -Dexec.args="phase2"
```

Phase 1 skips if `src/main/java/file/TicketRelease.csv` already exists. Phase 2 requires that CSV to exist.

## Configuration

All settings live in `src/main/resource/projects.yml` (loaded via Jackson YAML into `AppConfig`). Key settings:
- `maxVersionsPercent` (default 33): percentage of oldest releases to analyze
- `maxTicketsPercent`: percentage of tickets to fetch
- `pageSize`: Jira pagination size
- Projects list with `key` (Jira project key), `repoName` (GitHub apache/ repo name), and `jql`

## Architecture

The codebase follows a layered pattern with no framework (plain Java 21, Maven):

- **`controller/`** - Orchestrators that wire everything together
  - `AppController` (Phase 1): fetches Jira tickets, applies proportion method for missing injection versions, outputs ticket records
  - `Phase2Controller` (Phase 2): clones repo locally, extracts classes per release, computes metrics, labels buggy classes, writes dataset CSV
- **`service/`** - Business logic
  - `VersionService`: loads and filters Jira releases by the configured percentage window
  - `LocalGitService`: bare-clones Apache repos to `/tmp/isw2-repos/`, reads commits/files via `git` CLI (no API calls)
  - `MetricsServices`: computes all metrics (LOC, NRevisions, NAuth, churn, age, change set, structural metrics via PMD)
  - `LabelingService`: marks classes as buggy based on injection/fix version ranges
  - `ProportionCalculator` / `InjectionVersionEstimator`: proportion method for estimating missing injection versions
  - `ConsistencyService`: validates ticket data, discards inconsistent records
- **`client/`** - HTTP clients for Jira REST API
- **`mapper/`** - DTOs and mapping from Jira JSON to domain objects
- **`domain/`** - Value objects (`BugTicket`, `ProjectVersion`, `JavaClass`, `GitCommit`, etc.)
- **`util/`** - CSV I/O (`PrintOnCsv` for Phase 1 tickets, `PrintDatasetCsv` for Phase 2 dataset), logging
- **`config/`** - YAML config loading

## Key Design Decisions

- Git operations use bare clones and CLI commands (no JGit/GitHub API for commit data) - all offline after initial clone
- Metrics are cumulative from release 0 up to each release (not per-release deltas)
- PMD 7.0 is used for code smells, cyclomatic complexity violations, and duplication detection
- The project language is Italian (logs, comments, variable names in some places)
- No test suite exists
