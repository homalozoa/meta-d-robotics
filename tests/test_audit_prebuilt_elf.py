#!/usr/bin/env python3
"""Unit tests for the deterministic RDK X5 prebuilt-ELF audit parser."""

from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
import unittest


LAYER_DIR = Path(__file__).resolve().parents[1]
AUDITOR_PATH = LAYER_DIR / "scripts" / "audit-prebuilt-elf.py"
SPEC = importlib.util.spec_from_file_location("audit_prebuilt_elf", AUDITOR_PATH)
assert SPEC and SPEC.loader
AUDITOR = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = AUDITOR
SPEC.loader.exec_module(AUDITOR)


class PrebuiltElfAuditTests(unittest.TestCase):
    def test_parse_dynamic_metadata(self) -> None:
        info = AUDITOR.parse_dynamic(
            "  Machine:                           AArch64\n",
            "      [Requesting program interpreter: /lib/ld-linux-aarch64.so.1]\n",
            """
 0x0000000000000001 (NEEDED)             Shared library: [libc.so.6]
 0x0000000000000001 (NEEDED)             Shared library: [libhbmem.so.1]
 0x000000000000000e (SONAME)             Library soname: [libvendor.so.1]
 0x000000000000000f (RPATH)              Library rpath: [$ORIGIN:/usr/hobot/lib]
 0x000000000000001d (RUNPATH)            Library runpath: [$ORIGIN]
""",
            """
  Name: GLIBC_2.34
  Name: GLIBC_2.17
  Name: GLIBCXX_3.4.29
  Name: GLIBCXX_3.4.21
""",
        )

        self.assertEqual(info.machine, "AArch64")
        self.assertEqual(info.interpreter, "/lib/ld-linux-aarch64.so.1")
        self.assertEqual(info.soname, "libvendor.so.1")
        self.assertEqual(info.needed, ("libc.so.6", "libhbmem.so.1"))
        self.assertEqual(info.rpath, ("$ORIGIN", "/usr/hobot/lib"))
        self.assertEqual(info.runpath, ("$ORIGIN",))
        self.assertEqual(info.glibc_max, "2.34")
        self.assertEqual(info.glibcxx_max, "3.4.29")

    def test_machine_and_host_rpath_validation(self) -> None:
        with self.assertRaises(AUDITOR.AuditError):
            AUDITOR.validate_machine(Path("host-tool"), "Advanced Micro Devices X86-64", "AArch64")

        self.assertEqual(
            AUDITOR.host_rpath_entries(
                ("$ORIGIN", "/home/vendor/sdk/lib", "/opt/vendor/lib", "/usr/hobot/lib")
            ),
            ("/home/vendor/sdk/lib", "/opt/vendor/lib"),
        )

    def test_version_ordering_and_allowlist_reasons(self) -> None:
        self.assertEqual(AUDITOR.maximum_version(("2.9", "2.34", "2.17")), "2.34")
        self.assertEqual(
            AUDITOR.parse_allow_needed(("libc.so.6=glibc", "libgcc_s.so.1=libgcc")),
            {"libc.so.6": "glibc", "libgcc_s.so.1": "libgcc"},
        )
        with self.assertRaises(AUDITOR.AuditError):
            AUDITOR.parse_allow_needed(("libunexplained.so.1",))


if __name__ == "__main__":
    unittest.main()
