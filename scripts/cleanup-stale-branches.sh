#!/bin/bash
# cleanup-stale-branches.sh
# Identifies and optionally deletes stale local branches matching patterns:
#   - codex/*
#   - jetbrains-junie/*
#
# These branches were created for feature development or experiments and are
# no longer needed. Keeping them clutters the branch list.
#
# Usage:
#   ./scripts/cleanup-stale-branches.sh         # List only (dry-run)
#   ./scripts/cleanup-stale-branches.sh delete  # Actually delete

set -e

readonly PATTERNS=("codex/*" "jetbrains-junie/*")
readonly DRY_RUN=${1:-""}

echo "=== Boxy Stale Branch Cleanup ==="
echo ""

# Collect branches to delete
branches_to_delete=()

for pattern in "${PATTERNS[@]}"; do
    echo "Searching for branches matching: $pattern"
    # Use git branch with --list to find matching branches
    # (Note: --list doesn't support glob patterns directly in older git)
    matching=$(git branch --list "$pattern" 2>/dev/null || true)
    
    if [ -n "$matching" ]; then
        while IFS= read -r branch; do
            if [ -n "$branch" ]; then
                branch=$(echo "$branch" | xargs)  # Trim whitespace
                branches_to_delete+=("$branch")
                echo "  Found: $branch"
            fi
        done <<< "$matching"
    fi
done

echo ""
echo "Total branches to delete: ${#branches_to_delete[@]}"
echo ""

if [ ${#branches_to_delete[@]} -eq 0 ]; then
    echo "No stale branches found. Repository is clean!"
    exit 0
fi

if [ "$DRY_RUN" = "delete" ]; then
    echo "Deleting stale branches..."
    for branch in "${branches_to_delete[@]}"; do
        echo "  Deleting: $branch"
        git branch -d "$branch" 2>&1 || echo "    (could not delete $branch; may have unpushed commits)"
    done
    echo ""
    echo "Cleanup complete!"
else
    echo "DRY RUN: No branches deleted. To actually delete, run:"
    echo "  ./scripts/cleanup-stale-branches.sh delete"
    echo ""
fi
