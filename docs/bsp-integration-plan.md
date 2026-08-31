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
| Native Ethernet | Onboard gigabit MAC/PHY, with RDKOS defaulting to `192.168.127.10` | Pinned in-tree MAC/PHY driver and DT are built in; Saha deliberately uses DHCP, persistent systemd MAC policy, and includes `ethtool` | Link at 10/100/1000, DHCP, ping, driver/PHY statistics, cable hotplug, and stable MAC across two cold boots |
| USB device networking | RNDIS + ECM, `0525:a4a2`, device address `192.168.128.10/24` | Implemented without mass storage | Host enumerates both functions; DHCP-independent ping over `usb0`; clean restart and shutdown |
| USB host Ethernet | USB CDC/RNDIS plus common Realtek, ASIX, LAN78xx, SMSC, Davicom, CoreChips, MosChip and SR adapters | Curated version-neutral module roots and USB-only networkd DHCP policy implemented | Hotplug at least one RTL8152/8153 and one CDC/ASIX adapter; link, DHCP, ping, unplug/replug and reboot tests |
| USB serial and UVC | CDC ACM, CH341, CP210x, FTDI, PL2303 and USB Video Class | Curated Linux 6.1.83 module roots plus Wrynose `v4l2-ctl`/`media-ctl` diagnostics implemented | Hotplug representative serial adapters; stable `/dev/serial/by-id`, bounded loopback; enumerate a UVC camera and capture one frame |
| USB and serial-line CAN | candleLight/GS_USB, PEAK, EMS and SLCAN adapters | Curated Linux 6.1.83 SocketCAN module roots and matching Wrynose tools implemented; no interface is configured automatically | Hotplug one GS_USB device; explicit bitrate, internal or isolated-bus loopback, unplug/replug; SLCAN only on a known serial adapter |
| USB storage and HID | Generic USB mass storage, UAS, keyboard and mouse | Kernel support, `evdev`, and `evtest` are included; manual mounting is available and privileged desktop-style automount remains intentionally absent | Read/write/sync/unmount a disposable filesystem; keyboard/mouse event capture and hotplug |
| Wi-Fi and Bluetooth | AIC8800D80 SDIO firmware/driver and UART5 Bluetooth at 1.5 Mbaud, no flow control | Implemented | `wlan0` scan, `hci0` readiness, rfkill and BlueZ checks |
| Core board peripherals | HPU3501 RTC, ACT LED, TCAN4x5x, DT-declared SPI, I2C/GPIO tools | Implemented | RTC read, ACT trigger, SPI nodes, I2C enumeration, CAN loopback without external traffic |
| Onboard audio | Active DesignWare I2S0, ES8326 codec, and Hobot duplex-host sound card | Implemented and hardware validated; inactive virtual duplex/audio-gadget paths excluded | ALSA card/PCMs enumerate; mixer read; bounded capture/playback hardware-open tests pass with safe levels |
| HDMI and graphics | Galcore GPU, N2D, SII9022 bridge, VeriSilicon DRM pipeline, and matching Vivante EGL/GLES2/GBM/Nano2D runtime | Pinned kernel, audited runtime, selected build-only headers, and `rdk-x5-gpu-smoke` implemented; OpenCL/Vulkan/GLES1/GLES3 and a global development provider remain optional gaps; hardware validation pending | `/dev/dri` nodes, connector/mode enumeration, HDMI hotplug, bounded hardware EGL/GLES render plus Nano2D open/close |
| Native camera and codec pipeline | ISC/camera wrapper, VIN/SIF/ISP/VSE, OSD/GDC, VPU/JPU | Native module closure implemented; end-to-end hardware validation pending | Module binding, media graph, sensor probe, one captured frame, encode/decode smoke, then BPU inference |
| 40-pin control and overlays | `hobot-io`, pinmux overlays and GPIO/SPI/I2C/UART/PWM helpers | Official Python GPIO API, source-built production overlays, and guarded dry-run/confirm/backup workflow implemented; hardware validation pending | Python import/pin map, overlay round trip, reboot persistence, GPIO line test and one test per enabled bus |
| Power, thermal and QoS | CPU policy, thermal trip overrides and QoS setup from `hobot-configs` | Exact NoC QoS table implemented as a verified systemd oneshot; in-kernel schedutil and DT thermal cooling are active; guarded CPU/thermal overrides remain pending | QoS readback, thermal zones/cooling, supported-frequency membership, load throttling and idle stability |
| Suspend and wake button | RDKOS policy for products that expose a Sleep button | Not applicable to RDK X5 V1.0 board ID 302: it has no user-facing Sleep button.  The inherited vendor DT node is retained for ABI compatibility, not advertised as hardware. | No gate on this machine; define a separate machine/DT and run repeated suspend/resume tests before supporting an X5 Module Carrier Board |
| DSI panels and audio HATs | Versioned display/audio overlays | Missing and intentionally optional | Build each overlay from the pinned kernel DT headers and test only on matching hardware |
| High-level SP development API | `hobot-spdev` DNN/VIO/codec/display wrappers and Python modules | Not a BSP driver and not packaged: release 3.0.9 hard-codes Python 3.10/X11 and carries `cp310` prebuilt extensions, while Wrynose uses Python 3.14 | Split source-buildable C/C++ interfaces from Python; close every DNN/media/graphics ABI; build native Python 3.14 extensions and run camera/BPU examples |
| On-target kernel development | Release kernel headers for third-party modules | Kernel and external BPU modules build off-target reproducibly; a target SDK/header package is intentionally absent | Add only when an on-target module-build use case exists; compile and load a harmless out-of-tree test module against exactly 6.1.83 |

## Delivery order

1. Flash and validate the current image: native/USB Ethernet, USB storage/HID,
   USB serial, UVC, USB-CAN, onboard wireless, RTC, CAN, audio, display,
   camera, GPU and BPU.  Keep cellular modem stacks opt-in.
2. Verify thermal and CPU-frequency sysfs behavior under load, then port only
   guarded, exact-token CPU/thermal overrides from `x5-hobot-configs`.
3. Keep automatic suspend disabled on RDK X5 V1.0.  If an X5 Module Carrier
   Board machine is added, give it a separate DT contract and validate its
   Sleep button plus driver quiesce order before adding a suspend policy.
4. Validate the base HDMI/DRM/2D display closure, Vivante EGL/GLES2 runtime,
   and DRM diagnostics; review OpenCL/Vulkan only as separate opt-in features.
5. Validate the native camera, image-processing, VPU and JPU module closure;
   keep V4L2 and native HBN modes separately selectable because they conflict.
6. Validate the guarded `x5-hobot-io` pinmux and overlay workflow on both
   supported board IDs; keep permissive vendor udev rules and host-built
   archives excluded.
7. Split and port only the source-buildable `hobot-spdev` C/C++ API; do not
   reuse its Python 3.10 binaries in the Wrynose Python 3.14 image.
8. Add DSI and audio-HAT overlays as opt-in packages after base-board tests.

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
packages, credentials, USB gadget mass storage, and privileged removable-media
automount are not base BSP requirements.  The `hobot-miniboot` payload is kept
as release provenance only, kernel headers remain development-only, and DSI,
audio-HAT, cellular-modem, OpenCL/Vulkan, and prebuilt Python 3.10 components
require separate opt-in policy and review instead of being copied from the
RDKOS filesystem package wholesale.
