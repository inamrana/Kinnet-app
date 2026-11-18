# Kinnect

CS 4039 Software for Mobile Devices. The Kinnect social app talks to its own PHP + MySQL web
services (in `backend/`). Agora carries voice and video calls, and Firebase Cloud Messaging
delivers push notifications. The app caches what it loads in SQLite and queues actions made
offline, sending them once the network is back.

## Setup

The files below hold keys or machine-specific addresses. They are **not** in the repository, so every team member adds their own copy.

1. **Backend (XAMPP).** Copy (or symlink) the `backend/` folder to `C:\xampp\htdocs\kinnect`, start
   Apache and MySQL, then import the schema once:
   ```
   mysql -u root < backend/schema.sql
   ```
   If your MySQL user or password differs from XAMPP's defaults, copy `backend/config.local.example.php`
   to `backend/config.local.php` and fill it in.
2. **Server address.** Copy `server.properties.example` to `server.properties` in the project root
   and set `api.base` to your laptop's Wi-Fi address (from `ipconfig`), e.g.
   `api.base=http://192.168.18.82/kinnect/`. The phone and the laptop must be on the same network.
3. **Agora.** Copy `agora.properties.example` to `agora.properties` and paste the App ID of an Agora
   project created in *Testing mode* (App ID only, no certificate).
4. **Push notifications (optional).** In the Firebase console add an Android app with package
   `com.kinnect.i230577_i230839`, download `google-services.json` into `app/`, then create a
   service-account key (Project settings → Service accounts → Generate new private key) and save it
   as `backend/fcm-service-account.json`. Without that file the backend skips pushes silently. Everything
   else still works while the app is open, because the app polls the server.

Then open the project in Android Studio and run the `app` configuration.

## Web services

Every endpoint returns JSON (`{ ok: true, … }` or `{ ok: false, error }`). Apart from sign-up and
log-in, each request carries the session token as `Authorization: Bearer <token>`.

| Folder | What it does |
|---|---|
| `api/auth/` | Sign up, log in, log out, current user |
| `api/users/` | Profiles, profile and cover photos, search, suggestions, online status |
| `api/friends/` | Add friend → Requested → Friends, friend lists |
| `api/posts/`, `api/comments/` | Feed, photo posts, reactions, shares, comments and replies |
| `api/stories/` | 24-hour stories, close friends, views and reactions |
| `api/chats/`, `api/messages/` | Chats list, messages (text, photo, video, file, shared post), 5-minute edit/delete rule, sync |
| `api/calls/` | Start, answer, decline and end calls; the incoming-call check; call history (`logs.php`) |
| `api/listings/` | Marketplace: list, sell (with photo), remove your own listing |
| `api/notifications/`, `api/badges.php` | Notifications tab and the red badge counts |
| `api/fcm/` | Saves the device's push token |

Uploaded media is stored under `backend/uploads/` and served by URL; the tables store only paths.

### Calls

Calls are signalled through the `calls` table; the audio and video go through Agora, and the
channel name is the call id. While the app is open, it checks `calls/incoming.php` every few seconds
and shows the incoming-call screen. When the app is closed, the backend sends an FCM push instead. Both call screens
poll `calls/get.php` to follow *ringing → accepted → ended*. A call that rings for 40 seconds becomes
*missed*. The server records when each call was answered and ended, so `calls/logs.php` returns the
history with durations.

## Tests

`app/src/androidTest/.../BackendFlowTest.kt` has two Espresso tests that run against your PHP
backend (`api.base` in `server.properties`):

- Logging in and then logging out leaves an empty back stack.
- A message sent in a chat appears in the thread and is saved on the server.

They need one test account with a finished profile and one friend. Pass them as the instrumentation
arguments `testEmail`, `testPassword` and `testFriendUid` (the friend's numeric user id). In Android Studio
that's Run → Edit Configurations → Instrumentation arguments. Run the tests on a device or emulator that can
reach the backend; without the arguments they are skipped.
