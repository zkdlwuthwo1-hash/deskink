# DeskInk beta test notes

**Status:** DeskInk does not currently have a published TestFlight build. These are preparation notes for a future beta, not an invitation or a release announcement. The current CI creates unsigned development IPAs.

## Publisher preparation

- Set up the app in App Store Connect under the publisher's Apple Developer Program membership.
- Choose an owned bundle identifier, configure distribution signing, and update the toolchain to Apple's current submission requirements.
- Supply beta review information explaining the Windows companion and USB pairing, with instructions that let reviewers test the app.
- Prepare a matching Windows runtime package and installation guide; validate on a PC without the development environment.
- Submit the first external build for review, then share an invitation. Testers need TestFlight, not the publisher's credentials.

See [Apple's TestFlight overview](https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview/). Builds are testable for up to 90 days; publish a replacement before an active beta expires.

## Physical-device checks

1. Fresh Windows setup, Apple Devices recognition, USB trust, and pairing.
2. Pen, highlighter, stroke eraser, laser, disappearing ink, undo/redo, clear, and hide/show.
3. Pencil double-tap on supported hardware and return to the previous tool after an eraser stroke.
4. Pinch zoom and two-finger pan: only the iPad view changes; Windows ink coordinates remain correct.
5. Pencil toolbar access, portrait/landscape layout, and left/right edges on different iPads.
6. Monitor shortcuts and Stream Deck: no terminal flash, separate drawings on each monitor.
7. PC ink responsiveness after a long drawing session, alongside iPad smoothness.
8. Whole-monitor sharing as received by another Zoom participant.
9. USB reconnect and app restarts. Windows app exit currently clears drawings; durable recovery is not implemented.

Reports should include app versions, device/OS versions, GPU, monitor resolution, and reproduction steps. Remove pairing codes, tokens, and personal screen content from attachments.

These notes describe DeskInk, not the upstream EternalMonitor release or its TestFlight builds.
