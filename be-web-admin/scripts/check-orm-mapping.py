#!/usr/bin/env python3
"""
Đối chiếu entity JPA với DDL thật (nguồn chân lý), chạy được mà KHÔNG cần JDK/MariaDB:

  1. Mỗi @Table phải tương ứng 1 CREATE TABLE trong sql/.
  2. Mỗi @Column/@JoinColumn phải là cột có thật của bảng đó.
  3. Mọi cột NOT NULL (trừ cột sinh / AUTO_INCREMENT) phải được map.
  4. Cột ENUM: tập giá trị trong columnDefinition phải trùng tập giá trị của DDL.
  5. columnDefinition cho text/json/char/datetime(6)/tinyint/bit(1)/decimal phải khớp DDL.

Dùng: python3 be-web-admin/scripts/check-orm-mapping.py   (exit 1 nếu có lỗi)
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]           # be-web-admin/
SQL_DIR = ROOT / "sql"
ENTITY_DIRS = [ROOT / "src/main/java/com/example/be_web_admin/model/jpa" / d
               for d in ("qbank", "ai", "moodle", "eval")]

CONSTRAINT_HEADS = ("PRIMARY KEY", "UNIQUE KEY", "KEY ", "CONSTRAINT ", "FULLTEXT KEY",
                    "INDEX ", "CHECK ", "FOREIGN KEY")


def strip_sql_comments(text: str) -> str:
    return "\n".join(line for line in text.splitlines() if not line.strip().startswith("--"))


def split_top_level(body: str) -> list[str]:
    items, depth, buf, quote = [], 0, [], None
    for ch in body:
        if quote:
            buf.append(ch)
            if ch == quote:
                quote = None
            continue
        if ch in "'\"":
            quote = ch
            buf.append(ch)
        elif ch == "(":
            depth += 1
            buf.append(ch)
        elif ch == ")":
            depth -= 1
            buf.append(ch)
        elif ch == "," and depth == 0:
            items.append("".join(buf))
            buf = []
        else:
            buf.append(ch)
    if "".join(buf).strip():
        items.append("".join(buf))
    return items


def parse_ddl() -> dict[str, dict]:
    tables: dict[str, dict] = {}
    for sql_file in sorted(SQL_DIR.glob("*.sql")):
        text = strip_sql_comments(sql_file.read_text(encoding="utf-8"))
        for match in re.finditer(r"CREATE TABLE IF NOT EXISTS\s+(\w+)\s*\((.*?)\)\s*ENGINE",
                                 text, re.S):
            name, body = match.group(1), match.group(2)
            columns = {}
            for item in split_top_level(body):
                chunk = item.strip()
                if not chunk or chunk.upper().startswith(CONSTRAINT_HEADS):
                    continue
                parts = chunk.split(None, 1)
                if len(parts) != 2:
                    continue
                col, rest = parts[0], parts[1]
                upper = rest.upper()
                enum_values = None
                enum_match = re.match(r"ENUM\s*\((.*?)\)", rest, re.S | re.I)
                if enum_match:
                    enum_values = [v for v in re.findall(r"'([^']*)'", enum_match.group(1))]
                columns[col] = {
                    "not_null": "NOT NULL" in upper,
                    "generated": " AS (" in upper or "GENERATED" in upper,
                    "auto": "AUTO_INCREMENT" in upper,
                    "enum": enum_values,
                    "raw": rest,
                }
            tables[name] = {"columns": columns, "file": sql_file.name}
    return tables


def find_annotation_args(text: str, annotation: str) -> list[str]:
    """Trả về phần trong ngoặc của mọi @annotation(...), có xử lý chuỗi và ngoặc lồng."""
    out = []
    for match in re.finditer(r"@" + annotation + r"\s*\(", text):
        i = match.end()
        depth, quote, start = 1, None, i
        while i < len(text) and depth > 0:
            ch = text[i]
            if quote:
                if ch == quote:
                    quote = None
            elif ch in "'\"":
                quote = ch
            elif ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
            i += 1
        out.append(text[start:i - 1])
    return out


def parse_entities() -> dict[str, dict]:
    entities = {}
    for directory in ENTITY_DIRS:
        for java in sorted(directory.glob("*.java")):
            text = java.read_text(encoding="utf-8")
            table_match = re.search(r'@Table\s*\(\s*name\s*=\s*"([^"]+)"', text)
            if not table_match:
                continue
            table = table_match.group(1)
            columns, enum_columns = {}, {}
            for args in find_annotation_args(text, "(?:Column|JoinColumn)"):
                name_match = re.search(r'name\s*=\s*"([^"]+)"', args)
                if not name_match:
                    continue
                col = name_match.group(1)
                columns[col] = args
                enum_ref = re.search(r"columnDefinition\s*=\s*([\w.]+)\.SQL_TYPE", args)
                enum_literal = re.search(r'columnDefinition\s*=\s*"enum\((.*?)\)"', args)
                if enum_ref:
                    enum_columns[col] = ("ref", enum_ref.group(1))
                elif enum_literal:
                    enum_columns[col] = ("literal", re.findall(r"'([^']*)'", enum_literal.group(1)))
            entities[table] = {
                "file": java.name,
                "columns": columns,
                "enums": enum_columns,
            }
    return entities


def enum_spec_constant(enum_type: str) -> list[str] | None:
    """Đọc SQL_TYPE từ file enum nguồn (không cần biên dịch)."""
    for java in (ROOT / "src/main/java/com/example/be_web_admin/enums").rglob("*.java"):
        if java.stem != enum_type:
            continue
        match = re.search(r'SQL_TYPE\s*=\s*"(enum\((.*?)\))"', java.read_text(encoding="utf-8"))
        if match:
            return re.findall(r"'([^']*)'", match.group(2))
    return None


def main() -> int:
    tables = parse_ddl()
    entities = parse_entities()
    errors, warnings = [], []

    for table, entity in entities.items():
        if table not in tables:
            errors.append(f"{entity['file']}: bảng {table} không có trong DDL")
            continue
        ddl_columns = tables[table]["columns"]

        for col, args in entity["columns"].items():
            if col not in ddl_columns:
                errors.append(f"{table}.{col}: entity map cột không tồn tại trong DDL "
                              f"({entity['file']})")
                continue
            ddl = ddl_columns[col]
            if ddl["generated"]:
                errors.append(f"{table}.{col}: là CỘT SINH, không được map vào entity")
            declared = re.search(r'columnDefinition\s*=\s*"([^"]+)"', args)
            if declared and ddl["enum"] is None:
                want = declared.group(1).split()[0].lower()
                have = ddl["raw"].split()[0].lower()
                if want != have:
                    warnings.append(f"{table}.{col}: columnDefinition '{want}' vs DDL '{have}'")

        for col, ddl in ddl_columns.items():
            if ddl["generated"] or ddl["auto"]:
                continue
            if ddl["not_null"] and col not in entity["columns"]:
                errors.append(f"{table}.{col}: NOT NULL nhưng entity không map "
                              f"({entity['file']})")

        for col, (kind, spec) in entity["enums"].items():
            ddl_values = ddl_columns.get(col, {}).get("enum")
            if ddl_values is None:
                errors.append(f"{table}.{col}: entity khai báo ENUM nhưng DDL không phải ENUM")
                continue
            if kind == "ref":
                declared = enum_spec_constant(spec.split(".")[-1])
                if declared is None:
                    errors.append(f"{table}.{col}: không tìm thấy SQL_TYPE của {spec}")
                    continue
            else:
                declared = spec
            if set(declared) != set(ddl_values):
                errors.append(f"{table}.{col}: tập ENUM lệch — entity {sorted(set(declared))} "
                              f"vs DDL {sorted(set(ddl_values))}")

    print(f"DDL: {len(tables)} bảng | entity: {len(entities)} bảng")
    for warning in warnings:
        print("  ! " + warning)
    if errors:
        print(f"\n{len(errors)} LỖI:")
        for error in errors:
            print("  - " + error)
        return 1
    print("OK: mọi cột entity đều có thật trong DDL, đủ cột NOT NULL, ENUM khớp.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
