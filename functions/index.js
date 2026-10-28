/**
 * Kinnect push notifications (Firebase Cloud Messaging).
 *
 * - notifyNewMessage: a new message in chats/{chatId}/messages notifies the other person
 *   ("Aisha Khan: Sending the photos tonight", or "Aisha took a screenshot" alerts).
 * - notifyIncomingCall: calls/{uid} being set rings that user even when the app is closed.
 *
 * Deploy with:  firebase deploy --only functions   (needs the Blaze plan; the free quota covers a demo)
 */
const { onValueCreated, onValueWritten } = require("firebase-functions/v2/database");
const { initializeApp } = require("firebase-admin/app");
const { getDatabase } = require("firebase-admin/database");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();
const db = () => getDatabase();

async function userName(uid) {
  const snap = await db().ref(`users/${uid}/fullName`).get();
  return snap.val() || "Someone";
}

async function tokenOf(uid) {
  const snap = await db().ref(`fcmTokens/${uid}`).get();
  return snap.val();
}

/** Data-only, high-priority messages so the app builds the notification even when closed. */
async function sendTo(uid, data) {
  const token = await tokenOf(uid);
  if (!token) return;
  try {
    await getMessaging().send({ token, data, android: { priority: "high" } });
  } catch (error) {
    // Token no longer valid (app reinstalled / logged out): forget it
    if (String(error.code).includes("registration-token-not-registered")) {
      await db().ref(`fcmTokens/${uid}`).remove();
    }
  }
}

exports.notifyNewMessage = onValueCreated("/chats/{chatId}/messages/{messageId}", async (event) => {
  const message = event.data.val();
  if (!message || !message.senderId) return;
  const [a, b] = event.params.chatId.split("_");
  const recipient = message.senderId === a ? b : a;
  if (!recipient) return;

  const senderName = await userName(message.senderId);
  const isScreenshot = message.type === "system" && String(message.text).includes("took a screenshot");
  let body;
  switch (message.type) {
    case "image": body = "Sent a photo"; break;
    case "post": body = "Shared a post"; break;
    default: body = message.text || "New message";
  }
  await sendTo(recipient, {
    type: "message",
    kind: isScreenshot ? "screenshot" : "message",
    fromUid: message.senderId,
    title: isScreenshot ? "Screenshot alert" : senderName,
    body: isScreenshot ? `${senderName} took a screenshot of your chat` : body,
  });
});

exports.notifyIncomingCall = onValueWritten("/calls/{uid}", async (event) => {
  const callId = event.data.after.val();
  if (!callId) return;
  const session = (await db().ref(`callSessions/${callId}`).get()).val();
  if (!session || session.status !== "ringing") return;
  await sendTo(event.params.uid, {
    type: "call",
    callId,
    title: await userName(session.callerId),
    video: String(!!session.video),
  });
});
