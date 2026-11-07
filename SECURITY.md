# Security

## Report a vulnerability

Do not open a public GitHub issue, discussion or pull request for a security problem.

Use the report form, which is anonymous and needs no account:

    optimize/share.sh --preview --problem --owner "<what you found, in your words>" "<what you checked>"

Read what it prints (it is exactly what would be sent), then:

    optimize/share.sh --send

If the computer is offline it shows a QR code; scan it with a phone and the page sends the same text.
The report holds the TV model, the steps and your words. Do not paste secrets, keys, addresses or Wi-Fi names into it.

## In scope

- The scripts in `optimize/` (they run on the owner's computer and talk to the TV over ADB).
- The Android apps in `android/` and the page they load (`dist/tv.html`), including how they fetch and update.
- A way for a report (`optimize/share.sh`) to carry personal data: addresses, names, Wi-Fi names, tokens, file paths.
- Anything that could leave the owner's TV or Wi-Fi in a worse state than before the setup.

## Out of scope

- Problems in Android, the TV maker's apps, or the third-party services the screens read (weather, prayer times, scores).
- A TV that an attacker already controls over ADB.
- Reports that need the owner to run something that is not from this repository.

## What to expect

A reply is not promised on a fixed day; this is a small project. A fix goes into `next` first, then into a release.
