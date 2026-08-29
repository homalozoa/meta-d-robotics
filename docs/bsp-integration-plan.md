# RDK X5 BSP integration plan

This plan tracks the gap between the D-Robotics RDKOS 3.5.0 RDK X5 release
and the Wrynose-based Yocto image.  The release pins and license checksums are
recorded in [release-3.5.0-source-matrix.md](release-3.5.0-source-matrix.md).

## Compatibility policy

- Keep the vendor kernel and device trees at the RDKOS 3.5.0 kernel contract
  (`6.1.83`).  Never use `AUTOREV` or mix files from another RDKOS release.
- Pin every independently versioned vendor repository with a named `SRCREV`.
- Translate Debian startup policy into native Yocto packages and systemd
  units; do not install vendor `.deb` packages into the root filesystem.
- Select stable module-provider package names and let Yocto resolve the exact
  kernel-version packages.  Do not use the broad `kernel-modules` package.
- Audit selected vendor ELF files for AArch64 architecture and the declared
  glibc ABI ceiling before packaging them.
- Keep board support in `MACHINE_ESSENTIAL_EXTRA_RDEPENDS`; keep optional
  accelerator, camera, display-panel, and HAT features in packagegroups or
  explicit image features.

These rules mirror the useful compatibility boundaries in mature BSP layers:
the machine selects hardware essentials, release metadata owns version pins,
and optional hardware surfaces remain independently testable.

## Status and gaps

| Area | RDKOS 3.5.0 contract | Yocto status | Hardware acceptance gate |
| --- | --- | --- | --- |
| Boot and TF image | X5 `Image`, board-selected DTB, CONFIG FAT partition, ext4 root | Implemented; protected whole-device flashing is provided by `meta-saha` | Cold boot, `sahaWorld` hostname, correct model/SOC/board ID, no failed systemd units |
| BPU | `bpu_hw_io_x5` plus DNN runtime | Implemented in the accelerator image | HIMLoco inference smoke test and driver/device inspection |
| USB device networking | RNDIS + ECM, `0525:a4a2`, device address `192.168.128.10/24` | Implemented without mass storage | Host enumerates both functions; DHCP-independent ping over `usb0`; clean restart and shutdown |
| Wi-Fi and Bluetooth | AIC8800D80 SDIO firmware/driver and UART5 Bluetooth at 1.5 Mbaud, no flow control | Implemented | `wlan0` scan, `hci0` readiness, rfkill and BlueZ checks |
| Core board peripherals | HPU3501 RTC, ACT LED, TCAN4x5x, DT-declared SPI, I2C/GPIO tools | Implemented | RTC read, ACT trigger, SPI nodes, I2C enumeration, CAN loopback without external traffic |
| Onboard audio | DesignWare I2S, ES8326 codec, duplex sound-card drivers | Missing | ALSA cards/PCMs enumerate; mixer read; bounded capture/playback tests with safe levels |
| HDMI and graphics | Galcore GPU, N2D, SII9022 bridge and VeriSilicon DRM pipeline | Missing | `/dev/dri` nodes, connector/mode enumeration, HDMI hotplug and framebuffer/DRM smoke |
| Native camera and codec pipeline | ISC/camera wrapper, VIN/SIF/ISP/VSE, OSD/GDC, VPU/JPU | Partial: selected sensor runtime and a small kernel-module root set | Module binding, media graph, sensor probe, one captured frame, encode/decode smoke, then BPU inference |
| 40-pin control and overlays | `hobot-io`, `hb_dtb_tool`, pinmux overlays and GPIO/SPI/I2C/UART/PWM helpers | Missing | Overlay round trip, reboot persistence, GPIO line test and one test per enabled bus |
| Power, thermal and QoS | CPU policy, QoS setup, suspend button and board status policy from `hobot-configs` | Not audited for Yocto policy yet | Thermal zones/cooling, frequency policy, suspend/resume and idle stability |
| DSI panels and audio HATs | Versioned display/audio overlays | Missing and intentionally optional | Build each overlay from the pinned kernel DT headers and test only on matching hardware |

## Delivery order

1. Add onboard audio as an explicit module and ALSA diagnostic package.
2. Add the base HDMI/DRM/2D display closure and standard DRM diagnostics.
3. Complete the native camera, image-processing, VPU and JPU module closure;
   keep V4L2 and native HBN modes separately selectable if they conflict.
4. Port the source-built portions of `x5-hobot-io`, including a safe overlay
   workflow; exclude permissive vendor udev rules and host-built archives.
5. Port only the board-relevant power, thermal and QoS policy from
   `x5-hobot-configs` as reviewable systemd units.
6. Add DSI and audio-HAT overlays as opt-in packages after base-board tests.

## Test cadence

Every feature lands as a separate Conventional Commit after:

1. metadata/static tests and `git diff --check`;
2. recipe build, packaging QA, license and SPDX tasks;
3. full accelerator-enabled `saha-image-robot` build;
4. rootfs manifest, service/module closure, image integrity and offline
   systemd verification;
5. protected TF-card flash followed by bounded UART hardware tests.

Hardware-dependent results remain explicitly pending until the named device is
observed on the board.  A successful build is not recorded as a hardware pass.

## Deliberate exclusions

Ubuntu desktop integration, APT/update services, first-run provisioning,
automatic rootfs resizing, VNC/autologin, NAND bootloader flashing, sample-only
packages, credentials, and USB mass storage are not base BSP requirements.
They require separate opt-in policy and review instead of being copied from the
RDKOS filesystem package wholesale.
