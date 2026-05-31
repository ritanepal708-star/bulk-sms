# CSV SMS Sender

Native Java Android app for sending SMS messages from a CSV file.

## What it does

- Lets the user choose a CSV file from Android storage.
- Reads contacts from either:
  - `name,phone` header columns, or
  - two-column rows like `John,+9779800000000`.
- Supports `{name}` inside the message template.
- Shows a preview before sending.
- Requires user confirmation before sending.
- Sends messages one by one with a delay between messages.
- Includes a Stop Sending button.

Use this only for contacts who have agreed to receive your messages. SMS charges may apply depending on the SIM/network plan.

## CSV example

```csv
name,phone
Aarav,+9779800000000
Sita,+9779811111111
```

## Build in Android Studio

1. Open this folder in Android Studio.
2. Let Gradle sync.
3. Build > Build Bundle(s) / APK(s) > Build APK(s).

## Build with Codemagic

This project includes `codemagic.yaml`.

Recommended steps:

1. Upload/push this folder to a GitHub, GitLab, or Bitbucket repository.
2. Add the repository in Codemagic.
3. Select the workflow named `android-java-debug`.
4. Run the build.
5. Download the APK from the build artifacts.

## Important Android note

This app uses Android's `SEND_SMS` permission. It is intended for direct installation/testing and legitimate opt-in messaging. Google Play may restrict apps that request SMS permissions unless the app qualifies for an allowed policy use case.
