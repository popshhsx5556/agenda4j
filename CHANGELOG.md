# Changelog

All notable changes to this project will be documented in this file.

## [0.1.1] - 2026-03-03

### Changed
- Reduced polling latency by switching to signal-driven refill waiting.
- Removed poll window cursor drift by calculating polling window from current time.
- Updated default polling throughput settings: `lockLimit=40`, `batchSize=20`.

## [0.1.0] - 2026-02-19

### Added
- Spring Boot auto-configuration registration via `AutoConfiguration.imports`.
- Core unit tests for interval and cron behavior.
- Mongo integration tests for claim/lock, cancel, and retry/reschedule semantics.
- GitHub Actions CI workflow for compile and test.
- Open-source baseline docs: `README.md`, `LICENSE`, `NOTICE`, `CHANGELOG.md`, `RELEASE.md`.
- Community docs: `CONTRIBUTING.txt`, `SECURITY.txt`, `CODE_OF_CONDUCT.txt`.

### Changed
- Replaced hardcoded worker id with configurable `agenda.worker-id` and generated fallback.
- Aligned lock semantics to `agenda.default-lock-lifetime` for claim lock duration.
- Unified Mongo index policy through starter index configuration.
- Renamed parent coordinates to `io.github.harutostudio:agenda4j-parent:0.1.0`.
- Switched project license to Apache License 2.0.

### Notes
- `0.1.x` is marked as evolving API.
