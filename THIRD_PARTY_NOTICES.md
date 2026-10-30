# Third-party notices

The optional VPN build links [sing-box](https://github.com/SagerNet/sing-box), pinned to v1.11.15, Copyright SagerNet and contributors, licensed under GPL-3.0-or-later. Its [COPYING](https://github.com/SagerNet/sing-box/blob/v1.11.15/LICENSE) and transitive dependency notices must accompany distributions. The build script retrieves upstream source; no proprietary Happ code, icons, or binaries are included.

The platform bridge implements the public Go-mobile interfaces described by upstream Libbox and follows the Apple Network Extension model. Upstream API references: `experimental/libbox/platform.go`, `tun.go`, `service.go`, `setup.go`, `tun_darwin.go` in the pinned source. Existing platform implementation for comparison: [sing-box-for-apple](https://github.com/SagerNet/sing-box-for-apple), also GPL licensed.

The source created for AURA in this repository is offered under GPL-3.0-or-later to permit the combined sing-box build. See LICENSE. This does not grant rights to Apple SDKs or third-party trademarks. Full corresponding source and dependency licenses must be prepared before distributing binaries.
