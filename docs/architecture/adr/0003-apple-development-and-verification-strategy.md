# ADR-003: Apple Development and Verification Strategy

## Status

Accepted

## Context

The primary development machine is Windows. Android and Wear OS development runs natively on
Windows without constraint. Apple development cannot assume continuous local access to a Mac.

Apple's macOS Software License Agreement restricts macOS to Apple-branded hardware, and only
licenses running additional macOS instances virtually on a Mac that is already running Apple
Software. A local macOS virtual machine on ordinary Windows hardware is therefore unsupported and
is explicitly not part of this strategy.

The project has deliberately decided **not** to purchase or maintain a dedicated Mac. Apple
clients remain fully native (see ADR-001); what this ADR governs is *when and how* that native
Apple code gets built and verified, given that a Mac is not continuously available.

## Decision

> The project uses Windows as the primary development environment. Android/Wear OS can be
> implemented and verified locally. Apple clients remain fully native, but Xcode-, simulator-,
> signing-, and device-dependent verification is performed in supported macOS environments on
> Apple hardware when required. Temporary or hosted Mac environments are acceptable; ownership of
> a dedicated Mac is not required.

Consequently:

- Android phone and Wear OS implementation, product/domain behavior, architectural rules, and
  platform-independent behavioral specifications are prioritized first, since they are fully
  workable on the primary development machine.
- Swift code that is genuinely independent of Apple-only frameworks (no SwiftUI, HealthKit,
  WatchConnectivity/WatchKit, Core Location, etc.) may be written and statically reviewed on
  Windows, and compiled/tested using the officially supported Windows Swift toolchain (SwiftPM,
  XCTest). This is a convenience for a narrow slice of code, not a claim that most Apple-side logic
  will end up framework-free — and it never constitutes iOS/watchOS verification.
- When actual Apple verification is required (Xcode build, simulator, signing, or device), it is
  performed in a supported macOS/Xcode environment running on genuine Apple hardware. Acceptable
  forms include a temporarily borrowed Mac, a hosted/cloud Mac (e.g. a real-Apple-hardware cloud
  provider), a rented Mac environment, or any other supported Apple-hardware-backed macOS
  environment. No single vendor or hardware purchase is prescribed by this decision.
- Unsupported local macOS virtualization on ordinary Windows hardware is explicitly excluded.

### Apple verification states

Apple-side work is tracked using explicit, non-collapsible verification states rather than a
single generic "verified" status:

- **DESIGNED** — architecture/behavior has been reasoned about; no code verification exists.
- **IMPLEMENTED** — Swift code exists.
- **HOST-VERIFIED** — pure Swift code was compiled/tested in a non-Apple environment (the Windows
  Swift toolchain) where technically valid.
- **XCODE BUILD VERIFIED** — the actual iOS/watchOS target compiled successfully using Xcode on
  macOS.
- **SIMULATOR VERIFIED** — relevant behavior ran in an iOS/watchOS simulator.
- **DEVICE VERIFIED** — relevant behavior was validated on real Apple hardware.

For Apple-framework-dependent functionality — SwiftUI lifecycle, HealthKit, watchOS workout
sessions, WatchConnectivity, Core Location behavior, background execution, and signing — Xcode/
macOS verification (at minimum XCODE BUILD VERIFIED, with SIMULATOR/DEVICE VERIFIED as
appropriate) is mandatory before that slice can be considered platform-complete. A broader project
slice may still be reported as, for example, "Android complete; Apple implementation pending
Xcode verification" — Apple's temporary unavailability does not block reporting or continuing
Android progress.

## Alternatives considered and rejected

- **Purchasing/maintaining a dedicated Mac** (physical Mac mini or an always-on hosted Mac
  subscription) — considered in an earlier discussion and rejected: the project does not want its
  architecture to depend on owning or continuously renting dedicated Apple hardware.
- **Local macOS virtualization on ordinary Windows hardware** — rejected as unsupported by Apple's
  software license agreement.
- **CI-only Apple development with no interactive verification path** — rejected as insufficient
  on its own: SwiftUI Previews, live simulator interaction, HealthKit/WatchConnectivity behavior,
  and debugging require an interactive Xcode session that CI cannot provide. CI remains a useful
  future complement, not a substitute, and is not established by this ADR.

## Consequences

- Apple verification may lag behind Android implementation.
- Some Apple slices may remain explicitly pending Xcode/device verification.
- CI or hosted Mac access may later improve feedback time.
- Platform behavior must not be inferred solely from Android.
- Shared behavior should be specified independently of native framework structure.

## Deliberately undecided

- Which specific hosted/temporary Mac mechanism (borrowed Mac, hosted cloud Mac, rented Mac
  environment, etc.) will be used — decided at the point actual Apple verification is needed, not
  now.
- Whether/when a CI service is introduced for Apple builds.
