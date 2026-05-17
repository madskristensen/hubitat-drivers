---
name: "community-json-pr-hygiene"
description: "Submit PRs to upstream community JSON registries without mangling formatting or diff hygiene"
domain: "pr-submission, json-editing, community-contrib"
confidence: "low"
source: "observed"
tools:
  # No MCP tools — this is a pure pattern skill
  # Agents will use native shell/text editors, git diff
---

## Context

When submitting a PR that edits a JSON file maintained by an upstream community (HPM `repositories.json`, npm registry index, GitHub Awesome lists in JSON form, public settings files), the **formatting of the source file matters**. The maintainers expect diffs to show only the intended changes — not hundreds of lines of reformatting.

Example failure mode: PR #106 to HubitatCommunity/hubitat-packagerepositories. The target `repositories.json` uses **tab indentation**. Using PowerShell's `ConvertFrom-Json | ConvertTo-Json` round-trip to edit it reformatted the entire file to spaces, resulting in a diff showing nearly every line changed when the intent was to add a single entry.

## When to Use

- **Submitting a PR that edits a JSON file** in an external/upstream repository (HPM registries, package indexes, shared settings files, Awesome lists in JSON form)
- **The upstream file uses specific formatting conventions** (tabs vs. spaces, sorted keys, blank lines, trailing newlines, specific key order)
- **You want the diff to show only the intended changes**, not reformat noise

## Patterns

### 1. Read as Raw Text, Not Parsed JSON

```powershell
# ❌ DO NOT DO THIS (will reformat the file)
$json = ConvertFrom-Json (Get-Content repositories.json -Raw)
# ... edit $json ...
$json | ConvertTo-Json -Depth 100 | Set-Content repositories.json
```

```powershell
# ✅ DO THIS (preserves exact formatting)
$content = Get-Content repositories.json -Raw
# ... use regex or string search to locate insertion point ...
$newContent = $content -replace 'search-pattern', 'replacement-text'
Set-Content -Path repositories.json -Value $newContent -NoNewline
```

### 2. Locate Insertion Point with Regex or String Search

```powershell
# Example: find the last entry in a JSON array and insert before the closing bracket
$pattern = '(\s+}\s*\])'  # Match last object's closing brace and array close
$replacement = @"
      },
      {
        "name": "My New Entry",
        "url": "https://..."
      }
    ]
"@

$newContent = $content -replace $pattern, $replacement
```

### 3. Verify Before Commit

```powershell
# After editing, **always** check the diff to ensure only intended lines changed
git diff repositories.json
# Should show only the 3-5 lines you added, not the entire file reformatted
```

### 4. Preserve Key Details

When writing the edited content back, preserve:
- **Indentation style** (tabs vs. spaces) — determine from existing file
- **Key order** — don't alphabetize unless the original is already alphabetized
- **Blank lines** — keep them exactly as they were
- **Trailing newlines** — check with `git diff --no-index` or `tail -c` to confirm

## Anti-Patterns

### ❌ JSON Parsing for Community Files

```powershell
# ConvertFrom-Json / ConvertTo-Json ALWAYS reformat:
# - Spaces instead of tabs
# - Default depth of 2 (arrays of objects truncate to string)
# - Keys reordered (no guarantee of order preservation)
# - Blank lines removed
# - Trailing newlines stripped

$json = ConvertFrom-Json (Get-Content file.json -Raw)
# Even with -Depth 100, the above problems still occur
$json | ConvertTo-Json -Depth 100 | Set-Content file.json
```

### ❌ Python json.dump() with indent=

```python
# Same problem in Python: reformats the file
import json
with open('repositories.json', 'r') as f:
    data = json.load(f)
# ... edit data ...
with open('repositories.json', 'w') as f:
    json.dump(data, f, indent=2)  # Reformat reintroduced!
```

### ❌ Node.js JSON.stringify()

```javascript
// Same issue — JSON.stringify() reformats
const data = JSON.parse(fs.readFileSync('file.json', 'utf8'));
// ... edit ...
fs.writeFileSync('file.json', JSON.stringify(data, null, 2));  // Reformatted!
```

## PowerShell-Specific Gotchas

1. **ConvertTo-Json defaults to depth 2** — even if you specify `-Depth 100`, the formatting changes occur (indentation, key order, blank lines).
2. **Test the round-trip locally** — before committing to an external repo, always:
   ```powershell
   git add repositories.json
   git diff --cached repositories.json
   ```
   If the diff shows hundreds of lines, STOP and revert. Use surgical text edits instead.

3. **Trailing newline handling** — PowerShell's `Set-Content` may not preserve trailing newlines. Use `-NoNewline` if the original file ends without a newline, or explicitly append `"`n"` if it should end with one.

## Cross-Platform Note

This problem is **not PowerShell-specific**:
- **Python `json.dump()`**: reformats indentation, key order, whitespace
- **Node.js `JSON.stringify()`**: same issues
- **Go `json.MarshalIndent()`**: same issues
- **Ruby `JSON.pretty_generate()`**: same issues

**The solution is universal:** treat community-maintained JSON files as **raw text**, use regex or string search to find the insertion point, splice in the new content, and verify the diff shows only intended changes.

## Examples

### HPM repositories.json Addition (PowerShell)

```powershell
$filePath = "repositories.json"
$content = Get-Content $filePath -Raw

# Find the position of the last array element's closing brace
# assuming each entry ends with "}\n" before the final "]"
$pattern = '(.*?})(\s*\])'  # Capture everything before final ], then the ]

# Construct the new entry (preserve the same indentation as surrounding entries)
$newEntry = @"
,
      {
        "author": "Mads Kristensen",
        "name": "Gemstone Lights",
        "category": "Lighting",
        "location": "https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/repositories.json",
        "description": "Control Gemstone Lights via local HTTP API"
      }
"@

# Insert before the closing bracket
$newContent = $content -replace $pattern, "`$1$newEntry`$2"

# Write back without adding extra newlines
Set-Content -Path $filePath -Value $newContent -NoNewline

# Verify the diff shows only the intended 6-8 lines
git diff $filePath
```

### Python Alternative (Surgical Text Replacement)

```python
import re

with open('repositories.json', 'r') as f:
    content = f.read()

# Locate insertion point and inject new entry
pattern = r'(.*?})(\s*\])'
new_entry = ''',
      {
        "author": "Mads Kristensen",
        "name": "Gemstone Lights",
        ...
      }'''

new_content = re.sub(pattern, r'\1' + new_entry + r'\2', content, flags=re.DOTALL)

with open('repositories.json', 'w') as f:
    f.write(new_content)

# Verify with `git diff`
```

## Summary

**Always use surgical text replacement for upstream community JSON files.** Parse-and-regenerate workflows (ConvertFrom-Json/ConvertTo-Json, json.load/json.dump, JSON.parse/JSON.stringify) will reformat the file and break diff hygiene. Read as raw text, locate the insertion point with regex or string search, splice in the new content, and verify with `git diff` before pushing.
