# Release Process — Boxy Event Queue

This document describes the release process for Boxy, including semantic versioning, publishing to Maven Central and GitHub Packages, and managing GitHub Releases.

## Versioning Policy

Boxy follows **Semantic Versioning 2.0.0** (https://semver.org/):

- **MAJOR.MINOR.PATCH** (e.g., `1.2.3`)
  - **MAJOR**: Increment for backwards-incompatible API changes (e.g., renamed stored procedures, breaking Java API changes)
  - **MINOR**: Increment for backwards-compatible new features (e.g., new stored procedures, new config options)
  - **PATCH**: Increment for bug fixes and patches (no new features)

### Snapshot Versions

Development versions use the `-SNAPSHOT` suffix (e.g., `1.0-SNAPSHOT`). Snapshots are published to:
- GitHub Packages (always)
- Maven Central snapshots repo (during release builds)

## Release Checklist

Before preparing a release, ensure:

1. All tests pass: `mvn clean verify`
2. Code review completed and merged into `main`
3. CHANGELOG.md updated with new features and fixes
4. No outstanding TODO comments or FIXME tags in production code
5. Documentation is current (README.md, docs/, etc.)
6. Coverage thresholds met (≥60% line coverage)

## How to Release

### Step 1: Prepare the Release

```bash
# From the main branch
git checkout main
git pull origin main

# Use Maven Release Plugin to bump version and create release tag
mvn release:prepare \
  -DnewVersion=1.2.3 \
  -DreleaseVersion=1.2.3 \
  -DdevelopmentVersion=1.3-SNAPSHOT
```

This command will:
- Bump the version in all pom.xml files to `1.2.3`
- Run `mvn clean verify` to ensure the release is solid
- Create a tag `v1.2.3` in Git
- Bump the version back to `1.3-SNAPSHOT` for next development cycle
- Create two commits: one for the release, one for the snapshot bump

### Step 2: Perform the Release

```bash
# Publish to Maven Central (requires GPG signing and OSSRH credentials)
mvn release:perform -Prelease

# OR publish to GitHub Packages only (requires GITHUB_TOKEN)
mvn release:perform
```

**Maven Central** (via OSSRH):
- Requires GPG key configured locally (see "GPG Setup" below)
- Requires Maven settings with OSSRH credentials (see "Maven Settings" below)
- Artifacts are automatically staged, verified, and promoted to Central
- Takes ~10-30 minutes to appear in Central repo search

**GitHub Packages**:
- Requires GITHUB_TOKEN with `write:packages` permission
- Artifacts available immediately
- Set up in ~/.m2/settings.xml (see "Maven Settings" below)

### Step 3: Create GitHub Release

```bash
# After release:perform completes, create a GitHub Release
gh release create v1.2.3 \
  --title "Release 1.2.3" \
  --generate-notes \
  --notes-file CHANGELOG.md
```

This creates a release on GitHub with:
- Auto-generated change notes (commits since last release)
- Contents of CHANGELOG.md
- Links to download artifacts

### Step 4: Announce

- Post to project channels (Slack, Discord, etc.)
- Update project documentation with new version
- Tag relevant issues as "released"

## Setup

### GPG Setup (for Maven Central)

1. Install GPG:
   ```bash
   # macOS
   brew install gnupg

   # Linux (Ubuntu/Debian)
   sudo apt-get install gnupg

   # Linux (Fedora/RHEL)
   sudo dnf install gnupg
   ```

2. Generate a key (if you don't have one):
   ```bash
   gpg --full-gen-key

   # Choose:
   # - Key type: RSA and RSA (default)
   # - Key size: 4096 bits
   # - Expiration: 4y (4 years)
   # - Real name: Your name
   # - Email: Your email (matches OSSRH account)
   # - Comment: (leave blank or add a note)
   # - Passphrase: Strong passphrase
   ```

3. List your keys and note the key ID:
   ```bash
   gpg --list-keys
   ```

4. Publish your public key to a keyserver (so OSSRH can verify):
   ```bash
   gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
   ```

5. Configure Maven to use your GPG key (add to ~/.m2/settings.xml):
   ```xml
   <profiles>
     <profile>
       <id>gpg</id>
       <properties>
         <gpg.keyname>YOUR_KEY_ID</gpg.keyname>
         <gpg.passphrase>YOUR_GPG_PASSPHRASE</gpg.passphrase>
       </properties>
     </profile>
   </profiles>
   <activeProfiles>
     <activeProfile>gpg</activeProfile>
   </activeProfiles>
   ```

### Maven Settings (~/.m2/settings.xml)

Add credentials for OSSRH (Maven Central) and GitHub Packages:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                              http://maven.apache.org/xsd/settings-1.0.0.xsd">
  <servers>
    <!-- OSSRH for Maven Central publishing -->
    <server>
      <id>ossrh</id>
      <username>YOUR_OSSRH_USERNAME</username>
      <password>YOUR_OSSRH_PASSWORD</password>
    </server>

    <!-- GitHub Packages for snapshot publishing -->
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>${env.GITHUB_TOKEN}</password>
    </server>
  </servers>

  <profiles>
    <profile>
      <id>gpg</id>
      <properties>
        <gpg.keyname>YOUR_GPG_KEY_ID</gpg.keyname>
        <gpg.passphrase>YOUR_GPG_PASSPHRASE</gpg.passphrase>
      </properties>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>gpg</activeProfile>
  </activeProfiles>
</settings>
```

### OSSRH Account Setup

1. Create a JIRA account at https://issues.sonatype.org/
2. Create a ticket to request Maven Central publishing access: https://issues.sonatype.org/browse/OSSRH
   - Project: Community Hosting (OSSRH)
   - Summary: "Publish mvsm.boxy to Maven Central"
   - Description: Include groupId (mvsm), project GitHub URL, and your email
3. Wait for approval (usually 1-2 business days)
4. Follow their reply to confirm control of the domain/GitHub repo
5. Once approved, use your JIRA credentials in settings.xml as shown above

## Troubleshooting

### "release:prepare" fails with Git errors

Ensure:
- You're on the `main` branch
- All changes are committed (`git status` is clean)
- You have push permission to the repository
- SSH key or GitHub token is configured

### "release:perform" fails with GPG error

- Verify GPG is installed: `gpg --version`
- Verify your key is in the keyring: `gpg --list-keys`
- Check gpg.keyname and gpg.passphrase in ~/.m2/settings.xml
- Try signing a test file: `gpg -u YOUR_KEY_ID --sign test.txt`

### Artifacts don't appear in Maven Central

- Check OSSRH Jira for status: https://issues.sonatype.org/
- Look for Nexus staging: https://s01.oss.sonatype.org/
- Central sync takes ~10-30 minutes after artifacts are released

## Rollback

If a release is bad and needs to be pulled:

1. Delete the tag locally and on GitHub:
   ```bash
   git tag -d v1.2.3
   git push origin :refs/tags/v1.2.3
   ```

2. Delete GitHub Release:
   ```bash
   gh release delete v1.2.3
   ```

3. Manually drop the staging repository from OSSRH: https://s01.oss.sonatype.org/
4. Revert any version bumps in Git if needed
5. Investigate and fix the issue
6. Re-release with a new patch version

## Automated Releases (Future)

Consider setting up GitHub Actions workflows for:
- Auto-publishing snapshots on every push to `main`
- Triggering releases via git tags or GitHub releases
- Auto-generating CHANGELOG.md from commits

See `.github/workflows/release.yml` for the planned release workflow template.
