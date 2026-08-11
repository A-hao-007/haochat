# HaoChat release process

Production releases use immutable Git and Docker identifiers. Do not deploy from an uncommitted working tree and do not use `latest` as a release identifier.

## Build

From a clean release commit:

```bash
deploy/scripts/release-build.sh v2026.08.1
```

The resulting image tag includes the Git SHA, for example `v2026.08.1-abcdef123456`. Metadata and exact image IDs are written under `/home/ubuntu/haochat-releases/<image-tag>/`.

## Deploy

```bash
deploy/scripts/release-deploy.sh v2026.08.1-abcdef123456
```

The deploy script verifies exact images, creates a fresh MySQL backup, records current containers, updates only the three application services, waits for readiness, and performs HTTP smoke checks. If readiness or smoke checks fail, it restores the previous image tag.

## Roll back

```bash
deploy/scripts/release-rollback.sh
```

This restores the previously recorded application images. Database restoration is deliberately separate because schema rollback must be reviewed against the migrations involved.

## Source control rules

- Create a feature branch for each change.
- Run backend and frontend checks before merging.
- Merge into `main`, create an annotated Git tag, then build that exact commit.
- Keep production secrets only in the ignored `.env` file.
- Retain at least the latest three application releases and the database backup taken before each deployment.
