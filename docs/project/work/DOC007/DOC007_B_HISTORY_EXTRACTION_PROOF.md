# DOC007-B — History-Preserving Extraction Proof

## Status

```text
DOC007_B_STATUS=CLOSED
EXTRACTION_PROOF=PASS
SOURCE_REVISION=a9bd2267ef8eb0b227ec28eff7fd261b8ddc2d98
EXTRACTION_TOOL=git-filter-repo
EXTRACTION_TOOL_VERSION=2.47.0
EXTRACTION_TOOL_REVISION=a40bce548d2c
EXTRACTION_SCOPE=docs/project/**
DESTINATION_REPOSITORY=guillermomolina/protos-project-docs
DESTINATION_CREATED=NO
```

## Purpose

DOC007-B verifies that the durable, non-normative `docs/project/**` corpus can be extracted from `guillermomolina/protos` while preserving its relevant Git history and without changing the source repository.

This proof does not create or populate the destination repository.

## Source snapshot

The proof was executed against:

```text
SOURCE_REVISION=a9bd2267ef8eb0b227ec28eff7fd261b8ddc2d98
SOURCE_PROJECT_FILES=293
SOURCE_REPOSITORY_SHALLOW=NO
```

The complete Git tree manifest for `docs/project/**` was recorded before filtering.

Its SHA-256 digest was:

```text
678dceec6fca8e4261e6a90bc522edbe11ca87e5fe6798336d1e757210228874
```

## Extraction procedure

A disposable full clone of the source repository was created outside the caller worktree.

The extraction was then performed with:

```text
git filter-repo --path docs/project/
```

The source checkout itself was never filtered.

The resulting filtered revision was:

```text
FILTERED_REVISION=0a686f60911aa6277e7a751b9d8f4774faeba26b
FILTERED_PROJECT_FILES=293
FILTERED_HISTORY_COMMITS=845
```

## Snapshot integrity

The filtered Git tree manifest for `docs/project/**` produced the same SHA-256 digest as the source manifest:

```text
SOURCE_TREE_SHA256=678dceec6fca8e4261e6a90bc522edbe11ca87e5fe6798336d1e757210228874
FILTERED_TREE_SHA256=678dceec6fca8e4261e6a90bc522edbe11ca87e5fe6798336d1e757210228874
TREE_MANIFEST_MATCH=PASS
```

The manifest comparison was empty.

Therefore the extraction preserved the current `docs/project/**` snapshot path-for-path, mode-for-mode, and blob-for-blob.

## Scope integrity

Inspection of all paths present in the filtered history found no path outside:

```text
docs/project/**
```

Result:

```text
OUT_OF_SCOPE_HISTORICAL_PATHS=NONE
SCOPE_INTEGRITY=PASS
```

## Historical preservation

Representative historical records were verified after Git history rewriting.

### TOOL005-B5

Source commit:

```text
24696a126b2e39b38447fe7ce2c12eb07c510449
```

Filtered commit:

```text
29151f66b1f2b5198d4be2c10af07267aafa746d
```

The filtered commit retains only:

```text
docs/project/work/TOOL005/TOOL005_B5_CLOSURE.md
```

This demonstrates the required behavior for a source commit that originally combined product changes and durable project-record changes: the extracted history preserves the durable project-record portion without retaining unrelated product files.

### TEST001-H

Source commit:

```text
dbea0b4df2bdc551f8c71df7745985844e03a78d
```

Filtered commit:

```text
d388400565b99d3eae510d15b9c072c1047d041e
```

The filtered commit retains:

```text
docs/project/work/TEST001/TEST001_H_PORTABLE_DISTRIBUTION.md
```

The record remains associated with its historical commit message:

```text
TEST001-H: validate portable Test Tool suite execution
```

## Source repository integrity

After completing all disposable extraction operations, the original source checkout remained at:

```text
a9bd2267ef8eb0b227ec28eff7fd261b8ddc2d98
```

Its worktree remained clean.

The `docs/project/**` tree manifest digest remained:

```text
678dceec6fca8e4261e6a90bc522edbe11ca87e5fe6798336d1e757210228874
```

Therefore:

```text
CALLER_WORKTREE_TOUCHED=NO
SOURCE_HISTORY_REWRITTEN=NO
SOURCE_SNAPSHOT_CHANGED=NO
```

## Conclusion

The bounded history-preserving extraction method required by DOC007 is proven viable.

```text
HISTORY_PRESERVATION=PASS
SNAPSHOT_INTEGRITY=PASS
SCOPE_INTEGRITY=PASS
SOURCE_INTEGRITY=PASS
DOC007_B_STATUS=CLOSED
DOC007_C_STATUS=READY
```

The approved destination repository is:

```text
guillermomolina/protos-project-docs
```

DOC007-C may proceed after this record is published in `guillermomolina/protos`.

The final destination import must be produced from a fresh execution-time source revision so that this proof record and any other project records published before cutover are included in the extracted history.
