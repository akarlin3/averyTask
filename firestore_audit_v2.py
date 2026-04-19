#!/usr/bin/env python3
"""
Firestore FK audit v2 for PrismTask / averytask-50dc5.

Fixes v1 bugs:
  - No longer iterates a hardcoded collection-name list.
    Uses user_doc_ref.collections() to enumerate what actually exists.
  - Discovers FK fields from real document keys (no assumed field names).
  - Handles UID passed directly to skip flaky auto-discovery.

Usage:
    python firestore_audit_v2.py <key.json> <uid>
    python firestore_audit_v2.py <key.json> <uid> --database prismtask
    python firestore_audit_v2.py <key.json> <uid> --raw   # dump first doc of each collection

Find your UID: Firebase Console -> Authentication -> Users
"""

import re
import sys
import os
import argparse
import json
from datetime import datetime, timezone

from google.cloud import firestore as gcloud_firestore
from google.oauth2 import service_account

PROJECT_ID   = "averytask-50dc5"
MAX_SAMPLE   = 20
CLOUD_ID_RE  = re.compile(r'^[A-Za-z0-9]{20,}$')

# Fields that are FK-candidates: anything containing "Id" as a word boundary,
# or ending in _id / CloudId / cloud_id, plus the literal "tags" array.
FK_NAME_RE = re.compile(
    r'(?i)(^|[_A-Z])id$|Id$|CloudId$|_id$|cloud_id$|^tags$'
)

def looks_like_fk(name):
    return bool(FK_NAME_RE.search(name))

def classify_scalar(value):
    """Return 'cloud', 'local', or 'null'."""
    if value is None:
        return 'null'
    if isinstance(value, (int, float)):
        return 'local'
    if isinstance(value, str):
        if not value:
            return 'null'
        if CLOUD_ID_RE.match(value):
            return 'cloud'
        try:
            int(value)
            return 'local'
        except ValueError:
            pass
        if len(value) < 10:
            return 'local'
        return 'cloud'
    return 'null'

def classify_field(value):
    """
    Returns (kind, detail):
      kind  = 'cloud' | 'local' | 'null' | 'array_cloud' | 'array_local' |
              'array_mixed' | 'array_empty' | 'other'
    """
    if isinstance(value, list):
        if not value:
            return 'array_empty', value
        kinds = [classify_scalar(e) for e in value]
        if all(k == 'cloud' for k in kinds):
            return 'array_cloud', value
        if all(k == 'local' for k in kinds):
            return 'array_local', value
        return 'array_mixed', value
    if isinstance(value, dict):
        return 'other', value
    return classify_scalar(value), value

def make_db(key_path, database_id='(default)'):
    sa_creds = service_account.Credentials.from_service_account_file(
        key_path,
        scopes=["https://www.googleapis.com/auth/cloud-platform"]
    )
    return gcloud_firestore.Client(
        project=PROJECT_ID,
        credentials=sa_creds,
        database=database_id
    )

# ── report helpers ──────────────────────────────────────────────────────────

def status_for(counts):
    local  = counts.get('local', 0) + counts.get('array_local', 0) + counts.get('array_mixed', 0)
    cloud  = counts.get('cloud', 0) + counts.get('array_cloud', 0)
    null   = counts.get('null', 0) + counts.get('array_empty', 0)
    if local == 0 and cloud == 0:
        return 'MISSING'
    if local == 0:
        return 'OK'
    if cloud == 0:
        return 'BROKEN'
    return 'MIXED'

# ── main ────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('key',  help='Path to service-account JSON key')
    parser.add_argument('uid',  help='Firebase Auth UID')
    parser.add_argument('--database', default='(default)',
                        help='Named Firestore database (default: "(default)")')
    parser.add_argument('--raw', action='store_true',
                        help='Also dump raw first-doc JSON for each collection')
    args = parser.parse_args()

    if not os.path.exists(args.key):
        sys.exit(f"ERROR: Key not found: {args.key}")

    print(f"Connecting  : {PROJECT_ID!r}  db={args.database!r}")
    db = make_db(args.key, args.database)

    user_ref = db.collection('users').document(args.uid)

    # ── discover actual subcollections ──────────────────────────────────────
    print(f"Listing subcollections of /users/{args.uid}/ ...")
    try:
        subcols = list(user_ref.collections())
    except Exception as e:
        sys.exit(f"ERROR listing subcollections: {e}\n"
                 "Check that the service account has 'Cloud Datastore User' IAM role.")

    if not subcols:
        print("WARNING: user_ref.collections() returned nothing.")
        print("  This may mean the UID is wrong, or the user doc has no subcollections.")
        print("  Falling back to listing ALL collections at root level to help debug:")
        for col in db.collections():
            sample = list(col.limit(3).stream())
            print(f"  /{col.id}/  ({len(sample)} docs sampled)")
            for doc in sample:
                subs = [s.id for s in doc.reference.collections()]
                print(f"    {doc.id}  subs={subs}")
        sys.exit(1)

    subcol_names = sorted(c.id for c in subcols)
    print(f"Found subcollections ({len(subcol_names)}): {subcol_names}\n")

    # ── audit each subcollection ─────────────────────────────────────────────
    # collection_name -> {
    #   'sampled': int,
    #   'all_fields': set,                 # every field seen across all docs
    #   'fk_fields': {
    #       field_name: {
    #           'counts': {kind: count},
    #           'examples': [(doc_id, value), ...]  # up to 3 broken examples
    #       }
    #   },
    #   'surprise_fk_fields': [field_name],  # FK-like but not expected
    #   'raw_first_doc': dict | None
    # }
    results = {}

    for col_ref in sorted(subcols, key=lambda c: c.id):
        name = col_ref.id
        print(f"  Auditing /{name}/ ...", end='', flush=True)

        # Sample — prefer most recent by updatedAt, fall back to unordered
        try:
            docs = list(
                col_ref.order_by('updatedAt',
                                 direction=gcloud_firestore.Query.DESCENDING)
                       .limit(MAX_SAMPLE)
                       .stream()
            )
        except Exception:
            try:
                docs = list(col_ref.limit(MAX_SAMPLE).stream())
            except Exception as e:
                print(f" ERROR: {e}")
                results[name] = {'error': str(e)}
                continue

        print(f" {len(docs)} docs")

        if not docs:
            results[name] = {'sampled': 0, 'all_fields': set(),
                             'fk_fields': {}, 'surprise_fk_fields': [],
                             'raw_first_doc': None}
            continue

        # Collect all field names across all docs
        all_fields = set()
        for doc in docs:
            all_fields.update((doc.to_dict() or {}).keys())

        fk_fields = {f: {'counts': {}, 'examples': []}
                     for f in all_fields if looks_like_fk(f)}

        for doc in docs:
            data = doc.to_dict() or {}
            for field, stat in fk_fields.items():
                if field not in data:
                    stat['counts']['null'] = stat['counts'].get('null', 0) + 1
                    continue
                kind, val = classify_field(data[field])
                stat['counts'][kind] = stat['counts'].get(kind, 0) + 1
                # Collect broken examples
                if kind in ('local', 'array_local', 'array_mixed') and len(stat['examples']) < 3:
                    stat['examples'].append((doc.id, data[field]))

        raw_first = docs[0].to_dict() if args.raw else None

        results[name] = {
            'sampled': len(docs),
            'all_fields': all_fields,
            'fk_fields': fk_fields,
            'surprise_fk_fields': [],   # filled below
            'raw_first_doc': raw_first,
        }

    # ── build report ─────────────────────────────────────────────────────────
    now_str  = datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M:%S UTC')
    lines    = []
    lines   += [f"# Firestore FK Audit v2 — {now_str}\n",
                f"Project: `{PROJECT_ID}` | UID: `{args.uid}` | DB: `{args.database}`\n",
                f"Subcollections found: {subcol_names}\n"]

    # Summary table
    lines += ["## Summary\n",
              "| Collection | Field | Sampled | Cloud | Local | Null | Status |",
              "|---|---|---|---|---|---|---|"]

    for name, res in sorted(results.items()):
        if 'error' in res:
            lines.append(f"| {name} | — | ERROR | — | — | — | ERROR |")
            continue
        if res['sampled'] == 0:
            lines.append(f"| {name} | — | 0 | — | — | — | EMPTY |")
            continue
        if not res['fk_fields']:
            lines.append(f"| {name} | (no FK fields found) | {res['sampled']} | — | — | — | — |")
            continue
        for field, stat in sorted(res['fk_fields'].items()):
            c = stat['counts']
            cloud = c.get('cloud', 0) + c.get('array_cloud', 0)
            local = c.get('local', 0) + c.get('array_local', 0) + c.get('array_mixed', 0)
            null  = c.get('null', 0) + c.get('array_empty', 0)
            lines.append(f"| {name} | {field} | {res['sampled']} | {cloud} | {local} | {null} | {status_for(c)} |")

    lines.append("")

    # Details
    lines.append("## Details\n")
    for name, res in sorted(results.items()):
        lines.append(f"### {name}\n")

        if 'error' in res:
            lines.append(f"**ERROR reading collection**: {res['error']}\n")
            continue

        if res['sampled'] == 0:
            lines.append("Collection is empty (0 documents returned).\n")
            continue

        lines.append(f"Sampled {res['sampled']} document(s).")
        all_f = sorted(res['all_fields'])
        lines.append(f"All fields seen: `{'`, `'.join(all_f) if all_f else '(none)'}`\n")

        if not res['fk_fields']:
            lines.append("No FK-looking fields detected in this collection.\n")
        else:
            for field, stat in sorted(res['fk_fields'].items()):
                c = stat['counts']
                cloud = c.get('cloud', 0) + c.get('array_cloud', 0)
                local = c.get('local', 0) + c.get('array_local', 0) + c.get('array_mixed', 0)
                null  = c.get('null', 0) + c.get('array_empty', 0)
                status = status_for(c)
                lines.append(f"#### `{field}` — **{status}**")
                lines.append(f"Cloud: {cloud} | Local: {local} | Null/missing: {null}")
                lines.append(f"Raw kind counts: {c}")
                if stat['examples']:
                    lines.append("\nBroken examples:")
                    for doc_id, val in stat['examples']:
                        lines.append(f"  - doc `{doc_id}`: `{repr(val)}`")
                lines.append("")

        if args.raw and res['raw_first_doc']:
            lines.append("<details><summary>Raw first document</summary>\n")
            lines.append("```json")
            lines.append(json.dumps(res['raw_first_doc'], default=str, indent=2))
            lines.append("```")
            lines.append("</details>\n")

    # Print to stdout too
    report = '\n'.join(lines)
    print("\n" + "="*60)
    print(report)
    print("="*60)

    out_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'firestore_audit_v2.md')
    with open(out_path, 'w', encoding='utf-8') as f:
        f.write(report)
    print(f"\nReport written: {out_path}")


if __name__ == '__main__':
    main()
