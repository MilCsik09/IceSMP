#!/usr/bin/env bash
set -euo pipefail

: "${PROFILE_V2_HEAD:?}"
: "${RECOVERY_HEAD:?}"
: "${OLD_PLATFORM_HEAD:?}"
: "${TARGET_BRANCH:?}"
: "${REPORT_PR:?}"

python3 /tmp/player_profile_integration_driver.py
python3 /tmp/player_profile_candidate_hardening.py

test -z "$(git status --porcelain --untracked-files=no)"
git diff --check "$PROFILE_V2_HEAD...HEAD"
git merge-base --is-ancestor "$PROFILE_V2_HEAD" HEAD
git merge-base --is-ancestor "$RECOVERY_HEAD" HEAD
git merge-base --is-ancestor "$OLD_PLATFORM_HEAD" HEAD

python3 scripts/test_resource_pack.py
python3 scripts/resource_pack.py build \
  --source resource-pack \
  --output build/resource-pack/validation-pack.zip
python3 scripts/validate_fancynpcs_snapshot.py

./gradlew clean build --console=plain --no-daemon --stacktrace \
  2>&1 | tee /tmp/gradle-build.log

markers=(
  "MOTD regression suite passed."
  "Moderation regression suite passed."
  "Moderation review regression suite passed."
  "PersistentStoreCoordinator regression tests passed."
  "DEV-item reward regression suite passed."
  "AFK regression suite passed."
  "HUD regression suite passed."
  "Pause-menu dialog regression suite passed."
  "Faction passive regression suite passed."
  "Faction passive hardening regression suite passed."
  "Faction tax debt regression suite passed."
  "Relic refresh regression suite passed."
  "Relic refresh pipeline regression suite passed."
  "Lifecycle shutdown regression suite passed."
  "Quest NPC validation regression suite passed."
  "Resource pack regression suite passed."
  "Class/spec compatibility regression suite passed."
  "ClassSpecSection v2 domain regression tests passed."
  "Class/spec application regression suite passed."
  "ClassSpecSection lifecycle regression suite passed."
  "PlayerProfile domain regression suite passed."
  "PlayerProfile YAML regression suite passed."
  "PlayerProfile transaction regression suite passed."
  "PlayerProfile API regression suite passed."
  "Respec transaction regression suite passed."
  "Spell grant ledger regression suite passed."
)
for marker in "${markers[@]}"; do
  grep -F "$marker" /tmp/gradle-build.log
done

python3 scripts/check_player_profile_authority.py \
  --root . \
  --write-report build/player-profile-authority.json
python3 scripts/check_player_profile_openapi.py
python3 scripts/test_player_profile_authority.py
python3 scripts/check_consistency.py
python3 scripts/check_markdown_links.py --root .
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v

python3 scripts/generate_repository_inventory.py \
  --root . \
  --output build/repository-inventory \
  --mode strict
python3 scripts/check_documentation_coverage.py \
  --root . \
  --inventory build/repository-inventory/repository-inventory.json \
  --output build/repository-inventory \
  --mode strict
python3 - <<'PY'
import json
from pathlib import Path

inventory = json.loads(
    Path('build/repository-inventory/repository-inventory.json')
    .read_text(encoding='utf-8')
)
unresolved = [
    finding for finding in inventory.get('findings', [])
    if finding.get('severity') in {'FAIL', 'REVIEW_REQUIRED'}
]
if unresolved:
    raise SystemExit(json.dumps(unresolved, ensure_ascii=False, indent=2))
PY

# Preserve the exact workflow blobs from the fully validated candidate. The
# GitHub Actions token cannot publish workflow changes, so the branch is first
# advanced with an identical non-workflow tree. The blobs are then applied via
# the authenticated GitHub file API in a separate step outside this workflow.
cp .github/workflows/ci.yml /tmp/final-ci.yml
cp .github/workflows/repository-docs-inventory.yml /tmp/final-repository-docs-inventory.yml
validated_candidate="$(git rev-parse HEAD)"
validated_tree="$(git rev-parse HEAD^{tree})"

# Restore the target branch's existing workflow tree exactly. This guarantees
# that the normal fast-forward push contains no workflow create/update/delete.
git rm -r --ignore-unmatch .github/workflows
git checkout "$OLD_PLATFORM_HEAD" -- .github/workflows
git add -A .github/workflows
git diff --quiet "$OLD_PLATFORM_HEAD" -- .github/workflows
git commit -m "chore(ci): defer validated workflow publication"

code_head="$(git rev-parse HEAD)"
code_tree="$(git rev-parse HEAD^{tree})"
git merge-base --is-ancestor "$OLD_PLATFORM_HEAD" HEAD

git fetch --no-tags origin \
  "+refs/heads/$TARGET_BRANCH:refs/remotes/origin/$TARGET_BRANCH" \
  "+refs/heads/rework/class-spec-profile-v2:refs/remotes/origin/rework/class-spec-profile-v2" \
  "+refs/heads/agent/player-profile-root-recovered-20260803-v4:refs/remotes/origin/agent/player-profile-root-recovered-20260803-v4"
test "$(git rev-parse refs/remotes/origin/$TARGET_BRANCH)" = "$OLD_PLATFORM_HEAD"
test "$(git rev-parse refs/remotes/origin/rework/class-spec-profile-v2)" = "$PROFILE_V2_HEAD"
test "$(git rev-parse refs/remotes/origin/agent/player-profile-root-recovered-20260803-v4)" = "$RECOVERY_HEAD"

git push origin "HEAD:refs/heads/$TARGET_BRANCH"
remote="$(git ls-remote --heads origin "refs/heads/$TARGET_BRANCH" | awk '{print $1}')"
test "$remote" = "$code_head"

ci_sha="$(sha256sum /tmp/final-ci.yml | awk '{print $1}')"
inventory_sha="$(sha256sum /tmp/final-repository-docs-inventory.yml | awk '{print $1}')"
ci_b64="$(base64 -w0 /tmp/final-ci.yml)"
inventory_b64="$(base64 -w0 /tmp/final-repository-docs-inventory.yml)"

cat > /tmp/player-profile-code-publish.md <<EOF
<!-- player-profile-code-only-publication -->
### PlayerProfile validated code publication

- Validated candidate: \`$validated_candidate\`
- Validated candidate tree: \`$validated_tree\`
- Published code-only head: \`$code_head\`
- Published code-only tree: \`$code_tree\`
- Exact old #78 head: \`$OLD_PLATFORM_HEAD\`
- Workflow tree changed by this push: **no**
- Merge performed: **no**
- Force push used: **no**
EOF

gh api --method POST -H "Accept: application/vnd.github+json" \
  "repos/${GITHUB_REPOSITORY}/issues/${REPORT_PR}/comments" \
  -f body="$(cat /tmp/player-profile-code-publish.md)"

cat > /tmp/player-profile-ci-export.md <<EOF
<!-- player-profile-workflow-export-ci -->
### Validated workflow export: ci.yml

- SHA-256: \`$ci_sha\`
- Candidate: \`$validated_candidate\`

\`\`\`base64
$ci_b64
\`\`\`
EOF

gh api --method POST -H "Accept: application/vnd.github+json" \
  "repos/${GITHUB_REPOSITORY}/issues/${REPORT_PR}/comments" \
  -f body="$(cat /tmp/player-profile-ci-export.md)"

cat > /tmp/player-profile-inventory-export.md <<EOF
<!-- player-profile-workflow-export-inventory -->
### Validated workflow export: repository-docs-inventory.yml

- SHA-256: \`$inventory_sha\`
- Candidate: \`$validated_candidate\`

\`\`\`base64
$inventory_b64
\`\`\`
EOF

gh api --method POST -H "Accept: application/vnd.github+json" \
  "repos/${GITHUB_REPOSITORY}/issues/${REPORT_PR}/comments" \
  -f body="$(cat /tmp/player-profile-inventory-export.md)"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "validated_candidate=$validated_candidate"
    echo "validated_tree=$validated_tree"
    echo "code_head=$code_head"
    echo "code_tree=$code_tree"
    echo "ci_sha256=$ci_sha"
    echo "inventory_sha256=$inventory_sha"
  } >> "$GITHUB_OUTPUT"
fi

printf 'Published validated code-only head %s; workflow blobs exported.\n' "$code_head"
