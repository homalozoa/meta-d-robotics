#!/usr/bin/env python3
"""Offline behavior tests for the RDK X5 NoC QoS policy."""

from __future__ import annotations

import os
import subprocess
import tempfile
import unittest
from pathlib import Path

LAYER_DIR = Path(__file__).resolve().parents[1]
QOS_TOOL = (
    LAYER_DIR / "recipes-d-robotics" / "policy" / "files" / "rdk-x5-qos"
)


class RdkX5QosTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory(prefix="rdk-x5-qos-test-")
        self.sysfs = Path(self.temporary.name)
        socinfo = self.sysfs / "class/socinfo"
        socinfo.mkdir(parents=True)
        (socinfo / "soc_name").write_text("D-Robotics X5\n", encoding="utf-8")
        self.qos_base = self.sysfs / "bus/platform/drivers/noc_qos"
        self.qos_base.mkdir(parents=True)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def add_engine(self, name: str, read_value: str, write_value: str) -> None:
        device = self.qos_base / name
        read_dir = device / "read_priority_qos_ctrl"
        write_dir = device / "write_priority_qos_ctrl"
        read_dir.mkdir(parents=True)
        write_dir.mkdir(parents=True)
        (read_dir / "priority").write_text(read_value + "\n", encoding="utf-8")
        (write_dir / "priority").write_text(write_value + "\n", encoding="utf-8")

    def run_tool(self, action: str, expected: int = 0) -> subprocess.CompletedProcess[str]:
        environment = os.environ.copy()
        environment["RDK_X5_QOS_SYSFS_ROOT"] = str(self.sysfs)
        result = subprocess.run(
            [str(QOS_TOOL), action],
            check=False,
            capture_output=True,
            text=True,
            env=environment,
        )
        self.assertEqual(result.returncode, expected, result.stdout + result.stderr)
        return result

    def test_apply_and_check_exact_vendor_priorities(self) -> None:
        self.add_engine("20510500.sif_qos", "read_priority : 0", "write_priority : 0")
        self.add_engine("20520000.bpu_qos", "read_priority : 7", "write_priority : 7")

        applied = self.run_tool("apply")
        self.assertIn("present=2 missing=9 failures=0 action=apply", applied.stdout)
        self.assertEqual(
            (self.qos_base / "20510500.sif_qos/read_priority_qos_ctrl/priority")
            .read_text(encoding="utf-8")
            .strip(),
            "7",
        )
        self.assertEqual(
            (self.qos_base / "20520000.bpu_qos/write_priority_qos_ctrl/priority")
            .read_text(encoding="utf-8")
            .strip(),
            "0",
        )
        checked = self.run_tool("check")
        self.assertIn("present=2 missing=9 failures=0 action=check", checked.stdout)

    def test_check_reports_mismatch_without_writing(self) -> None:
        self.add_engine("20510500.sif_qos", "read_priority : 3", "write_priority : 7")
        result = self.run_tool("check", expected=1)
        self.assertIn("mismatch: read=3/7 write=7/7", result.stderr)
        self.assertEqual(
            (self.qos_base / "20510500.sif_qos/read_priority_qos_ctrl/priority")
            .read_text(encoding="utf-8")
            .strip(),
            "read_priority : 3",
        )

    def test_non_x5_soc_is_rejected_before_writing(self) -> None:
        self.add_engine("20510500.sif_qos", "read_priority : 0", "write_priority : 0")
        (self.sysfs / "class/socinfo/soc_name").write_text(
            "unrelated-soc\n", encoding="utf-8"
        )
        result = self.run_tool("apply", expected=1)
        self.assertIn("expected an X5 SOC", result.stderr)


if __name__ == "__main__":
    unittest.main()
