# Kinnect

CS 4039 Software for Mobile Devices — Assignment 2. The Kinnect social app (Assignment 1 UI)
connected to Firebase Authentication, Realtime Database and Cloud Messaging, with Agora voice
and video calls.

## Setup

These files hold keys and are **not** in the repository. Every team member adds their own copy.

1. **Firebase config.** In the Firebase console open the project, add an Android app with package
   `com.inamulhaq.i230839`, download `google-services.json` and put it in `app/`.
2. **Authentication.** Firebase console → Authentication → Sign-in method → enable **Email/Password**.
3. **Realtime Database.** Create a database, then open **Rules** and paste the contents of
   [`database.rules.json`](database.rules.json). Search, Marketplace and the feed need the indexes in it.
4. **Agora.** Copy `agora.properties.example` to `agora.properties` in the project root and paste
   the App ID of an Agora project created in *Testing mode* (App ID only, no certificate).
5. **Push notifications.** Messages and calls are pushed by the Cloud Functions in `functions/`.
   They need the Blaze plan (the free quota covers a demo):
   ```
   npm install -g firebase-tools
   firebase login
   firebase use <project-id>
   cd functions && npm install && cd ..
   firebase deploy --only functions,database
   ```

Then open the project in Android Studio and run the `app` configuration.

## Database layout

| Path | Contents |
|---|---|
| `users/{uid}` | Profile: names, username, bio, photos (Base64), `online`, `lastSeen` |
| `usernames/{username}` | Owner uid, keeps usernames unique (`.` stored as `,`) |
| `friendRequests/{to}/{from}`, `sentRequests/{from}/{to}`, `friends/{a}/{b}` | Friend system |
| `posts/{id}`, `postImages/{id}/{n}`, `comments/{postId}/{id}` | Feed, reactions, comments |
| `stories/{owner}/{id}`, `storyMedia/{id}`, `closeFriends/{uid}` | Stories (24 hours) |
| `chats/{chatId}/messages/{id}`, `userChats/{uid}/{other}` | Messaging and the Chats list |
| `callSessions/{id}`, `calls/{uid}`, `callLogs/{uid}/{id}` | Agora calls |
| `notifications/{uid}/{id}`, `fcmTokens/{uid}` | Notifications tab and push |
| `marketplace/{id}`, `recentSearches/{uid}` | Marketplace listings, Search history |

Photos are stored as small Base64 JPEGs inside the Realtime Database, so no Storage bucket is needed.

## Tests

`app/src/androidTest/.../BackendFlowTest.kt` has two Espresso tests that run against the real
Firebase project (log in → log out clears the back stack; a sent chat message appears and is saved).
They need one test account with a finished profile and one friend, passed as instrumentation arguments
`testEmail`, `testPassword` and `testFriendUid` (Android Studio: Run → Edit Configurations →
Instrumentation arguments). Run them on a device or emulator with internet access.
