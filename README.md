# URL Share Relay

A minimal Android share-target app that receives a URL from another application, fetches its page metadata, lets the user review it, and invokes the Android share sheet again.

## Open in Android Studio

Open the `UrlShareRelay` directory as an existing Gradle project.

## Build

```bash
./gradlew assembleDebug
```

## How it works

Chrome -> Android Share -> URL Share Relay -> fetch metadata -> preview/edit -> Android Share Sheet -> destination app

## Supported metadata

- Open Graph
- Twitter Cards
- HTML title
- Standard meta description
- Canonical URL

## Notes

The receiving application ultimately controls how its own rich preview is rendered. This application can supply title/text and URL through Android's sharing APIs, but it cannot force another application to display a particular preview format.
