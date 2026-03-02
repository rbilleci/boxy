# Branch Protection Rules for Boxy

This document describes the branch protection rules that should be applied to the `main` branch via the GitHub UI. These rules enforce code quality and prevent accidental pushes of broken code to production.

## Why Branch Protection?

Branch protection rules ensure that:
1. Code is reviewed before merging (at least one approval)
2. All CI checks pass before merging (tests, coverage, linting)
3. No commits can be force-pushed to `main` (prevents rewriting history)
4. The branch is always in a releasable state

## Rules to Apply to `main` Branch

### 1. Require Pull Request Reviews Before Merging

- **Number of required reviews**: 1 (minimum; 2+ recommended for larger team)
- **Dismiss stale PR approvals**: ENABLED
- **Require review from code owners**: ENABLED (if CODEOWNERS file exists)
- **Allow specified actors to bypass required pull requests**: Disable (or whitelist CI bots)

**Rationale**: Ensures at least one human reviews code before it reaches production.

### 2. Require Status Checks to Pass Before Merging

These GitHub Actions must pass:

| Check Name | Required | Description |
|-----------|----------|-------------|
| `build-and-test` | YES | Maven clean verify; compiles code, runs tests, checks coverage |
| `integration-tests` | YES | Integration tests against real MySQL (Testcontainers) |
| `code-quality` | NO | Optional: SpotBugs, Checkstyle, or similar (can be set up later) |

**How to configure**:
1. Go to Settings → Branches → main → Branch Protection Rules
2. Check "Require status checks to pass before merging"
3. Select the workflows/checks from the dropdown
4. Check "Require branches to be up to date before merging"

**Rationale**: Prevents merging code that fails tests or has other quality issues.

### 3. Require Code Reviews Passed by Code Owners

- **File**: `.github/CODEOWNERS`
- **Content**:
  ```
  # Default: requires review from repo maintainer
  * @rbilleci

  # Specific areas (optional)
  boxy-core/ @rbilleci
  boxy-mysql/ @rbilleci
  boxy-db/ @rbilleci
  docs/ @rbilleci
  ```

**Rationale**: Domain experts review their areas; reduces noise of irrelevant reviews.

### 4. Require Signed Commits

- **ENABLED**: Require commits to be signed

**How to setup**:
```bash
# Configure git to sign commits by default
git config --global user.signingkey YOUR_GPG_KEY_ID
git config --global commit.gpgsign true
```

**Rationale**: Cryptographically verifies commits are from trusted developers (advanced security).

### 5. Require Branches to be Up to Date Before Merging

- **ENABLED**: Require branches to be up to date before merging

**Rationale**: Prevents merging code that hasn't been rebased against the latest `main`, which might cause silent conflicts.

### 6. Enforce All Status Checks

- **ENABLED**: Include administrators in restrictions

**Rationale**: Even admins must follow the rules; prevents panic pushes.

### 7. Restrict Who Can Push to Matching Branches

- **Allow force pushes**: NO
- **Allow deletions**: NO

**Rationale**: Prevents rewriting history or accidentally deleting `main`.

## How to Apply These Rules via GitHub UI

1. Go to your repository on GitHub
2. Navigate to **Settings → Branches**
3. Click **Add rule**
4. Set **Branch name pattern** to `main`
5. Check the following:
   - [x] Require a pull request before merging
   - [x] Require status checks to pass before merging
   - [x] Require branches to be up to date before merging
   - [x] Include administrators
   - [x] Restrict who can push to matching branches
   - [x] Block force pushes
   - [x] Block deletions
6. Under "Require status checks to pass", select:
   - `build-and-test` (from GitHub Actions)
   - `integration-tests` (from GitHub Actions)
7. Click **Create**

## Bypassing Rules (Emergency Only)

In rare cases (e.g., zero-day security fix), an admin can temporarily disable the rule:

1. Go to Settings → Branches → main
2. Click the pencil icon to edit the rule
3. Uncheck constraints as needed
4. Save the rule
5. Merge the emergency fix
6. **Re-enable the rule immediately**

## Future Enhancements

Consider adding these rules later:

- **Require linear history**: Enforces fast-forward merges only (no merge commits)
- **Auto-delete head branches**: Automatically deletes PR branches after merge
- **Require conversation resolution**: All comments must be resolved before merge
- **Code owner status**: Treats CODEOWNERS reviews as required

## Workflows Enforced

See `.github/workflows/` for the GitHub Actions workflows that provide status checks:

- **build-and-test.yml**: Compiles, tests, coverage check
- **release.yml**: Tags releases and creates GitHub Releases

## Questions?

- **GitHub Help**: https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches
- **Branch Protection Guide**: https://github.blog/2021-04-27-setting-up-branch-protection-for-teams/
