#!/usr/bin/env python3
"""Audit prebuilt target ELF files installed by an RDK X5 recipe.

The vendor SDK contains a mixture of target runtimes, development files and
host-built utilities.  Recipes using this tool only install the selected
target closure, then this audit verifies that the result remains compatible
with the Yocto target sysroot.
"""

from __future__ import annotations

import argparse
import json
import os
from dataclasses import dataclass
from pathlib import Path
import re
import subprocess
import sys
from typing import Iterable, Sequence


class AuditError(Exception):
    """A prebuilt ELF failed a compatibility or provenance-safe check."""


@dataclass(frozen=True)
class DynamicInfo:
    """Dynamic-linker data emitted by ``readelf`` for one ELF file."""

    machine: str
    interpreter: str | None
    soname: str | None
    needed: tuple[str, ...]
    rpath: tuple[str, ...]
    runpath: tuple[str, ...]
    glibc_max: str | None
    glibcxx_max: str | None


_MACHINE_RE = re.compile(r"^\s*Machine:\s*(.+?)\s*$", re.MULTILINE)
_INTERPRETER_RE = re.compile(r"Requesting program interpreter:\s*([^\]\s]+)")
_NEEDED_RE = re.compile(r"\(NEEDED\).*?Shared library: \[([^\]]+)\]")
_SONAME_RE = re.compile(r"\(SONAME\).*?Library soname: \[([^\]]+)\]")
_RPATH_RE = re.compile(r"\(RPATH\).*?Library rpath: \[([^\]]*)\]")
_RUNPATH_RE = re.compile(r"\(RUNPATH\).*?Library runpath: \[([^\]]*)\]")
_GLIBC_RE = re.compile(r"\bGLIBC_([0-9]+(?:\.[0-9]+)*)\b")
_GLIBCXX_RE = re.compile(r"\bGLIBCXX_([0-9]+(?:\.[0-9]+)*)\b")


def version_key(value: str) -> tuple[int, ...]:
    """Turn a GLIBC-style version into a comparison-safe tuple."""

    return tuple(int(part) for part in value.split("."))


def maximum_version(values: Iterable[str]) -> str | None:
    """Return the highest version in ``values`` or ``None`` when empty."""

    versions = sorted(set(values), key=version_key)
    return versions[-1] if versions else None


def parse_machine(header_output: str) -> str:
    """Extract the machine field from ``readelf -h`` output."""

    match = _MACHINE_RE.search(header_output)
    if not match:
        raise AuditError("readelf header output has no Machine field")
    return match.group(1)


def parse_dynamic(
    header_output: str,
    program_output: str,
    dynamic_output: str,
    version_output: str,
) -> DynamicInfo:
    """Parse the subset of readelf output that affects runtime compatibility."""

    def split_paths(match: re.Match[str] | None) -> tuple[str, ...]:
        if not match or not match.group(1):
            return ()
        return tuple(path for path in match.group(1).split(":") if path)

    needed = tuple(sorted(set(_NEEDED_RE.findall(dynamic_output))))
    soname_match = _SONAME_RE.search(dynamic_output)
    interpreter_match = _INTERPRETER_RE.search(program_output)

    return DynamicInfo(
        machine=parse_machine(header_output),
        interpreter=interpreter_match.group(1) if interpreter_match else None,
        soname=soname_match.group(1) if soname_match else None,
        needed=needed,
        rpath=split_paths(_RPATH_RE.search(dynamic_output)),
        runpath=split_paths(_RUNPATH_RE.search(dynamic_output)),
        glibc_max=maximum_version(_GLIBC_RE.findall(version_output)),
        glibcxx_max=maximum_version(_GLIBCXX_RE.findall(version_output)),
    )


def is_elf(path: Path) -> bool:
    """Avoid treating arbitrary installed data as a readelf failure."""

    try:
        with path.open("rb") as source:
            return source.read(4) == b"\x7fELF"
    except OSError as error:
        raise AuditError(f"cannot read {path}: {error}") from error


def run_readelf(readelf: str, arguments: Sequence[str], path: Path) -> str:
    """Run the configured readelf, preserving actionable command failures."""

    command = [readelf, *arguments, os.fspath(path)]
    try:
        completed = subprocess.run(
            command,
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
    except OSError as error:
        raise AuditError(f"cannot execute {' '.join(command)}: {error}") from error

    if completed.returncode:
        detail = completed.stderr.strip() or completed.stdout.strip()
        raise AuditError(f"{' '.join(command)} failed: {detail}")
    return completed.stdout


def inspect_elf(readelf: str, path: Path) -> DynamicInfo:
    """Read one ELF file's architecture and dynamic-linker metadata."""

    return parse_dynamic(
        run_readelf(readelf, ("-hW",), path),
        run_readelf(readelf, ("-lW",), path),
        run_readelf(readelf, ("-dW",), path),
        run_readelf(readelf, ("-VW",), path),
    )


def iter_files(root: Path) -> Iterable[Path]:
    """Yield regular files and file symlinks in deterministic order."""

    if not root.is_dir():
        raise AuditError(f"ELF audit root does not exist or is not a directory: {root}")

    for directory, directory_names, file_names in os.walk(root, followlinks=False):
        directory_names.sort()
        for file_name in sorted(file_names):
            path = Path(directory, file_name)
            if path.is_file():
                yield path


def iter_unique_elfs(root: Path, readelf: str) -> Iterable[tuple[Path, DynamicInfo]]:
    """Yield each physical ELF below ``root`` once, following file symlinks."""

    seen: set[Path] = set()
    for path in iter_files(root):
        if not is_elf(path):
            continue
        resolved = path.resolve()
        if resolved in seen:
            continue
        seen.add(resolved)
        yield path, inspect_elf(readelf, path)


def validate_machine(path: Path, machine: str, required_machine: str) -> None:
    """Reject host or other-architecture objects before they reach a package."""

    if machine != required_machine:
        raise AuditError(
            f"{path}: expected ELF machine {required_machine!r}, found {machine!r}"
        )


def host_rpath_entries(paths: Iterable[str]) -> tuple[str, ...]:
    """Return RPATH/RUNPATH entries that reveal a build or developer host."""

    allowed_prefixes = (
        "$ORIGIN",
        "/lib",
        "/lib/",
        "/usr/lib",
        "/usr/lib/",
        "/usr/hobot/lib",
        "/usr/hobot/lib/",
    )
    return tuple(
        path
        for path in paths
        if path.startswith("/") and not path.startswith(allowed_prefixes)
    )


def parse_allow_needed(values: Iterable[str]) -> dict[str, str]:
    """Parse ``SONAME=reason`` values and require an auditable justification."""

    allowlist: dict[str, str] = {}
    for value in values:
        soname, separator, reason = value.partition("=")
        if not separator or not soname or not reason:
            raise AuditError(
                f"invalid --allow-needed {value!r}; use SONAME=explicit-reason"
            )
        if soname in allowlist and allowlist[soname] != reason:
            raise AuditError(f"conflicting reasons for allowed dependency {soname!r}")
        allowlist[soname] = reason
    return allowlist


def build_library_index(roots: Iterable[Path], readelf: str) -> dict[str, set[Path]]:
    """Index library file names and SONAMEs available to the target closure."""

    index: dict[str, set[Path]] = {}
    for root in roots:
        for path, info in iter_unique_elfs(root, readelf):
            index.setdefault(path.name, set()).add(path)
            if info.soname:
                index.setdefault(info.soname, set()).add(path)
    return index


def record_for_report(root: Path, path: Path, info: DynamicInfo) -> dict[str, object]:
    """Produce a stable JSON record with paths relative to the audited root."""

    return {
        "glibc_max": info.glibc_max,
        "glibcxx_max": info.glibcxx_max,
        "interpreter": info.interpreter,
        "machine": info.machine,
        "needed": list(info.needed),
        "path": os.fspath(path.relative_to(root)),
        "rpath": list(info.rpath),
        "runpath": list(info.runpath),
        "soname": info.soname,
    }


def audit(
    roots: Sequence[Path],
    library_roots: Sequence[Path],
    readelf: str,
    required_machine: str,
    allow_needed: dict[str, str],
    max_glibc: str | None,
    max_glibcxx: str | None,
) -> dict[str, object]:
    """Check installed target ELFs and return a deterministic audit report."""

    library_index = build_library_index(library_roots, readelf)
    errors: list[str] = []
    report_files: list[dict[str, object]] = []

    for root in roots:
        for path, info in iter_unique_elfs(root, readelf):
            try:
                validate_machine(path, info.machine, required_machine)
            except AuditError as error:
                errors.append(str(error))

            unsafe_paths = host_rpath_entries((*info.rpath, *info.runpath))
            if unsafe_paths:
                errors.append(f"{path}: host RPATH/RUNPATH entries: {', '.join(unsafe_paths)}")

            if max_glibc and info.glibc_max and version_key(info.glibc_max) > version_key(max_glibc):
                errors.append(
                    f"{path}: requires GLIBC_{info.glibc_max}, maximum is GLIBC_{max_glibc}"
                )
            if (
                max_glibcxx
                and info.glibcxx_max
                and version_key(info.glibcxx_max) > version_key(max_glibcxx)
            ):
                errors.append(
                    f"{path}: requires GLIBCXX_{info.glibcxx_max}, "
                    f"maximum is GLIBCXX_{max_glibcxx}"
                )

            for needed in info.needed:
                if needed not in library_index and needed not in allow_needed:
                    errors.append(f"{path}: unresolved DT_NEEDED {needed}")

            report_files.append(record_for_report(root, path, info))

    report = {
        "allow_needed": dict(sorted(allow_needed.items())),
        "files": sorted(report_files, key=lambda item: str(item["path"])),
        "library_roots": [os.fspath(root) for root in library_roots],
        "max_glibc": max_glibc,
        "max_glibcxx": max_glibcxx,
        "required_machine": required_machine,
        "roots": [os.fspath(root) for root in roots],
    }

    if errors:
        raise AuditError("\n".join(sorted(errors)))
    return report


def parse_arguments(arguments: Sequence[str]) -> argparse.Namespace:
    """Parse command-line arguments while keeping every exception actionable."""

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", action="append", required=True, type=Path)
    parser.add_argument("--library-root", action="append", type=Path)
    parser.add_argument("--readelf", default="readelf")
    parser.add_argument("--require-machine", default="AArch64")
    parser.add_argument("--allow-needed", action="append", default=[])
    parser.add_argument("--max-glibc")
    parser.add_argument("--max-glibcxx")
    parser.add_argument("--report", type=Path)
    return parser.parse_args(arguments)


def main(arguments: Sequence[str] | None = None) -> int:
    """Run the command-line audit and optionally write its JSON report."""

    parsed = parse_arguments(sys.argv[1:] if arguments is None else arguments)
    try:
        allow_needed = parse_allow_needed(parsed.allow_needed)
        roots = [root.resolve() for root in parsed.root]
        library_roots = [root.resolve() for root in (parsed.library_root or parsed.root)]
        report = audit(
            roots=roots,
            library_roots=library_roots,
            readelf=parsed.readelf,
            required_machine=parsed.require_machine,
            allow_needed=allow_needed,
            max_glibc=parsed.max_glibc,
            max_glibcxx=parsed.max_glibcxx,
        )
        if parsed.report:
            parsed.report.parent.mkdir(parents=True, exist_ok=True)
            parsed.report.write_text(
                json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8"
            )
    except AuditError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
