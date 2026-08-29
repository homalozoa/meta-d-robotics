#!/usr/bin/env python3
"""Offline safety tests for the guarded RDK X5 pinmux tool."""

from __future__ import annotations

import hashlib
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

LAYER_DIR = Path(__file__).resolve().parents[1]
TOOL = LAYER_DIR / "recipes-d-robotics" / "io" / "files" / "rdk-x5-pinmux"
REQUIRED_TOOLS = ("dtc", "fdtget", "fdtput", "fdtoverlay")

PERIPHERAL_STATUS = {
    "i2c0": "okay",
    "i2c1": "disabled",
    "i2c5": "okay",
    "i2s1": "disabled",
    "spi1": "okay",
    "spi2": "disabled",
    "serial1": "okay",
    "serial2": "disabled",
    "serial3": "disabled",
    "serial6": "disabled",
    "serial7": "disabled",
    "pwm0": "disabled",
    "pwm1": "disabled",
    "pwm2": "disabled",
    "pwm3": "okay",
}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def compile_dts(source: Path, output: Path, plugin: bool = False) -> None:
    arguments = ["dtc", "-q"]
    if plugin:
        arguments.append("-@")
    arguments.extend(["-I", "dts", "-O", "dtb", "-o", str(output), str(source)])
    subprocess.run(arguments, check=True, capture_output=True, text=True)


@unittest.skipUnless(
    all(shutil.which(tool) for tool in REQUIRED_TOOLS),
    "device-tree command-line tools are required",
)
class RdkX5PinmuxTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory(prefix="rdk-x5-pinmux-test-")
        self.root = Path(self.temporary.name)
        socinfo = self.root / "sys/class/socinfo"
        hobot = self.root / "boot/hobot"
        overlays = self.root / "boot/overlays"
        socinfo.mkdir(parents=True)
        hobot.mkdir(parents=True)
        overlays.mkdir(parents=True)
        (socinfo / "soc_name").write_text("x5\n", encoding="utf-8")
        (socinfo / "board_id").write_text("0x0302\n", encoding="utf-8")

        nodes = []
        aliases = []
        for index, (name, status) in enumerate(PERIPHERAL_STATUS.items()):
            aliases.append(f"        {name} = &{name};")
            nodes.append(
                f'        {name}: device@{index:x} {{ status = "{status}"; }};'
            )
        base_source = self.root / "base.dts"
        base_source.write_text(
            "/dts-v1/;\n\n"
            "/ {\n"
            '    model = "D-Robotics RDK X5 test fixture";\n'
            "    aliases {\n"
            + "\n".join(aliases)
            + "\n    };\n"
            "    soc {\n"
            + "\n".join(nodes)
            + "\n    };\n"
            "};\n",
            encoding="utf-8",
        )
        self.dtb = hobot / "x5-rdk-v1p0.dtb"
        compile_dts(base_source, self.dtb)

        overlay_source = self.root / "overlay.dts"
        overlay_source.write_text(
            "/dts-v1/;\n/plugin/;\n\n"
            "/ {\n"
            "    fragment@0 {\n"
            '        target-path = "/";\n'
            "        __overlay__ { saha-test-marker = \"enabled\"; };\n"
            "    };\n"
            "};\n",
            encoding="utf-8",
        )
        self.overlay = overlays / "dtoverlay_pps_gpio.dtbo"
        compile_dts(overlay_source, self.overlay, plugin=True)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def run_tool(self, *arguments: str, expected: int = 0) -> subprocess.CompletedProcess[str]:
        result = subprocess.run(
            [sys.executable, str(TOOL), "--root", str(self.root), *arguments],
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, expected, result.stdout + result.stderr)
        return result

    def token_from(self, output: str) -> str:
        match = re.search(r"^confirmation: (APPLY .+)$", output, re.MULTILINE)
        self.assertIsNotNone(match, output)
        return match.group(1)

    def status(self, peripheral: str) -> str:
        result = subprocess.run(
            ["fdtget", "-t", "s", str(self.dtb), peripheral, "status"],
            check=True,
            capture_output=True,
            text=True,
        )
        return result.stdout.strip()

    def test_status_reports_board_peripherals_and_no_config(self) -> None:
        result = self.run_tool("status")
        self.assertIn("board-id: 302", result.stdout)
        self.assertIn("peripheral: i2c1=disabled", result.stdout)
        self.assertIn("peripheral: pwm3=okay", result.stdout)
        self.assertIn("overlays: none (no /boot/config.txt)", result.stdout)

    def test_peripheral_requires_current_hash_confirmation_and_can_restore(self) -> None:
        original_hash = sha256(self.dtb)
        plan = self.run_tool("peripheral", "enable", "i2c1")
        token = self.token_from(plan.stdout)
        self.assertIn("change: pwm3: okay -> disabled", plan.stdout)
        self.assertIn("change: i2c1: disabled -> okay", plan.stdout)
        self.assertEqual(sha256(self.dtb), original_hash)

        rejected = self.run_tool(
            "peripheral",
            "enable",
            "i2c1",
            "--apply",
            "--confirm",
            "APPLY stale-plan deadbeef",
            expected=1,
        )
        self.assertIn("confirmation token does not match", rejected.stderr)
        self.assertEqual(sha256(self.dtb), original_hash)

        applied = self.run_tool(
            "peripheral", "enable", "i2c1", "--apply", "--confirm", token
        )
        self.assertIn("result: applied; reboot required", applied.stdout)
        self.assertEqual(self.status("i2c1"), "okay")
        self.assertEqual(self.status("pwm3"), "disabled")
        backups = sorted((self.dtb.parent / ".saha-backups").glob("*.bak"))
        self.assertEqual(len(backups), 1)
        self.assertEqual(sha256(backups[0]), original_hash)

        restore_plan = self.run_tool("restore", backups[0].name)
        restore_token = self.token_from(restore_plan.stdout)
        restored = self.run_tool(
            "restore", backups[0].name, "--apply", "--confirm", restore_token
        )
        self.assertIn("result: restored; reboot required", restored.stdout)
        self.assertEqual(sha256(self.dtb), original_hash)

    def test_overlay_requires_validation_and_is_idempotent(self) -> None:
        config = self.root / "boot/config.txt"
        plan = self.run_tool("overlay", "enable", "dtoverlay_pps_gpio")
        token = self.token_from(plan.stdout)
        self.assertFalse(config.exists())

        applied = self.run_tool(
            "overlay",
            "enable",
            "dtoverlay_pps_gpio",
            "--apply",
            "--confirm",
            token,
        )
        self.assertIn("backup: none (new file)", applied.stdout)
        self.assertEqual(
            config.read_text(encoding="utf-8"),
            "[all]\ndtoverlay=dtoverlay_pps_gpio\n\n",
        )
        configured_hash = sha256(config)

        repeated = self.run_tool(
            "overlay",
            "enable",
            "dtoverlay_pps_gpio",
            "--apply",
            "--confirm",
            "this token must not be consumed for a no-op",
        )
        self.assertIn("already configured; no files changed", repeated.stdout)
        self.assertEqual(sha256(config), configured_hash)

    def test_unknown_board_is_rejected_before_planning(self) -> None:
        board_id = self.root / "sys/class/socinfo/board_id"
        board_id.write_text("0x0999\n", encoding="utf-8")
        result = self.run_tool(
            "peripheral", "enable", "i2c1", expected=1
        )
        self.assertIn("unsupported RDK X5 board ID", result.stderr)


if __name__ == "__main__":
    unittest.main()
