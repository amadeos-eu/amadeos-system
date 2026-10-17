# amade-system

The system components of [amadeOS](https://github.com/datazuul/amadeos), built together as one Gradle
multi-project: one module and one Debian package per component. The standalone applications
(amade-files, amade-web, amade-pad, ...) keep their own repositories; the
[amadeos](https://github.com/datazuul/amadeos) repository builds the live image from all packages.

![License](https://img.shields.io/badge/license-EUPL--1.2-blue)
![Java](https://img.shields.io/badge/Java-25-orange)

## Modules

| Module | Package | What it is |
|---|---|---|
| `print` | `amade-print` | Print manager: CUPS printers with their state, the jobs of each printer (cancel, hold, release), pause and resume printers, default printer, test page, CUPS administration for adding printers |
| `sysinfo` | `amade-sysinfo` | System information: operating system, processor, memory, storage, graphics, network and battery on one page; copy or save as text |

Planned, moving here from the amadeos repository: the window manager and front panel, the display
manager (greeter), the session parts (screen lock, screensaver) and the Style Manager.

## Building

```sh
./gradlew build                 # all modules, with tests
./gradlew deb                   # build/distributions/amade-<module>_<version>_all.deb per module
./gradlew :print:installToAmadeOs   # one package into ../amadeos/config/packages.chroot/
./gradlew :sysinfo:run          # start a module from the build
```

Java 25 and `dpkg-deb` (for the packages) are needed. With
[amade-commons](https://github.com/datazuul/amade-commons) checked out next to this repository, the build
uses that checkout (composite build); otherwise the version from JitPack named in `build.gradle`.

## amade-print and CUPS

amade-print talks IPP (RFC 8010/8011) to CUPS itself, without `lpstat` or `cancel`: over the CUPS socket
`/run/cups/cups.sock`, or `http://localhost:631` when there is no socket. On the socket CUPS asks who is
calling and amade-print answers with `PeerCred`, as libcups does; CUPS checks that against the socket's
peer credentials. So the owner of a job can cancel it, and members of the group `lpadmin` can pause,
resume and choose the default printer, without a password.

Printers are added in the CUPS web interface (*Printer > Add or Configure Printers…*); driverless network
and USB printers (IPP Everywhere, AirPrint) usually appear by themselves. The test page is printed through
the JDK's print service.

## License

[EUPL-1.2](LICENSE)
