#!/usr/bin/env python3
"""
Populate a running instance with demo data through the public API.

This is deliberately NOT a Flyway migration. Migrations run in every
environment including production, cannot be conditionally skipped, and are
immutable once applied — none of which you want for throwaway demo rows, and
all of which are actively bad for demo accounts with known passwords.

Usage:
    python3 scripts/seed_demo.py                       # http://localhost:8080
    python3 scripts/seed_demo.py --base-url https://... --password 'sOmeThing'

Creates two users, three repositories, and in `billing-core` a merge request
that is approved and ready to merge, plus a second one still open.
"""
import argparse
import json
import sys
import urllib.error
import urllib.request

LOCAL_HOSTS = ("localhost", "127.0.0.1", "::1")


def call(base, path, token=None, body=None, method=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        base + "/api" + path, data=data, method=method or ("POST" if data is not None else "GET")
    )
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req) as res:
            raw = res.read().decode()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        return {"_status": e.code, "_error": e.read().decode()}


def login_or_register(base, email, password, name):
    res = call(base, "/auth/login", body={"email": email, "password": password})
    if res and "accessToken" in res:
        return res["accessToken"]
    res = call(base, "/auth/register", body={"email": email, "password": password, "displayName": name})
    if "accessToken" not in res:
        sys.exit(f"Could not create {email}: {res.get('_error', res)}")
    return res["accessToken"]


def column(name, type_, **kw):
    return dict(name=name, type=type_, **kw)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--password", help="Required when the target is not localhost.")
    args = parser.parse_args()

    base = args.base_url.rstrip("/")
    is_local = any(host in base for host in LOCAL_HOSTS)

    if not is_local and not args.password:
        sys.exit(
            f"Refusing to seed {base} with a default password.\n"
            "Pass --password explicitly, and only do this on an instance you are "
            "happy for anyone who reads this script to log in to."
        )
    password = args.password or "password"

    if not is_local:
        print(f"! Seeding a REMOTE instance: {base}", file=sys.stderr)

    jay = login_or_register(base, "jay@example.com", password, "Jay Raj Singh")
    alex = login_or_register(base, "alex@example.com", password, "Alex Mercer")

    def ensure_repo(name, engine):
        res = call(base, "/repositories", jay, {"name": name, "dbEngine": engine})
        if res and "id" in res:
            return res
        existing = call(base, "/repositories?page=0&size=50", jay) or []
        return next(r for r in existing if r["name"] == name)

    repo = ensure_repo("billing-core", "POSTGRESQL")
    ensure_repo("identity-service", "MYSQL")
    ensure_repo("ledger-warehouse", "GENERIC")
    rid = repo["id"]

    baseline = {"tables": [
        {"name": "users", "columns": [
            column("id", "UUID", primaryKey=True, nullable=False, unique=True),
            column("email", "VARCHAR(255)", nullable=False, unique=True),
            column("created_at", "TIMESTAMP", nullable=False, defaultValue="CURRENT_TIMESTAMP"),
        ], "indexes": [{"name": "idx_users_email", "columns": ["email"], "unique": True}]},
        {"name": "invoices", "columns": [
            column("id", "UUID", primaryKey=True, nullable=False, unique=True),
            column("user_id", "UUID", nullable=False),
            column("amount", "NUMERIC(12,2)", nullable=False),
        ], "indexes": [{"name": "idx_invoices_user", "columns": ["user_id"], "unique": False}]},
    ]}

    existing_branches = {b["name"] for b in call(base, f"/repositories/{rid}/branches?page=0&size=50", jay) or []}
    if "feature/widen-email" in existing_branches:
        print(f"{base} already has demo data; nothing to do.")
        print(f"  jay@example.com  / {password}")
        print(f"  alex@example.com / {password}")
        return

    def branch(name, source="main"):
        return call(base, f"/repositories/{rid}/branches", jay, {"name": name, "sourceBranch": source})

    def commit(name, schema, message):
        return call(base, f"/repositories/{rid}/versions", jay,
                    {"branchName": name, "schemaData": json.dumps(schema), "commitMessage": message})

    # main is protected, so the baseline arrives through a throwaway branch.
    branch("seed/base")
    commit("seed/base", baseline, "Baseline: users and invoices")
    call(base, f"/repositories/{rid}/merge", jay, {"sourceBranch": "seed/base", "targetBranch": "main"})

    feature = json.loads(json.dumps(baseline))
    feature["tables"][0]["columns"][1]["type"] = "VARCHAR(500)"
    feature["tables"][0]["columns"].insert(2, column("display_name", "VARCHAR(120)"))
    branch("feature/widen-email")
    commit("feature/widen-email", feature, "Widen users.email, add display_name")

    branch("feature/audit-log")

    branches = {b["name"]: b for b in call(base, f"/repositories/{rid}/branches?page=0&size=50", jay)}
    alex_id = next(u["id"] for u in call(base, "/auth/users", jay) if u["email"] == "alex@example.com")

    mr = call(base, "/merge-requests", jay, {
        "repositoryId": rid,
        "sourceBranchId": branches["feature/widen-email"]["id"],
        "targetBranchId": branches["main"]["id"],
        "requestedApproverId": alex_id,
    })
    call(base, f"/merge-requests/{mr['id']}/approve", alex, method="POST")

    call(base, "/merge-requests", jay, {
        "repositoryId": rid,
        "sourceBranchId": branches["feature/audit-log"]["id"],
        "targetBranchId": branches["main"]["id"],
    })

    print(f"Seeded {base}")
    print(f"  jay@example.com  / {password}")
    print(f"  alex@example.com / {password}")
    print("  billing-core: one approved merge request, one open")


if __name__ == "__main__":
    main()
