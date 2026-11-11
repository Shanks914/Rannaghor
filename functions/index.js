const crypto = require("node:crypto");
const nodemailer = require("nodemailer");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, Timestamp, FieldValue } = require("firebase-admin/firestore");
const { defineSecret } = require("firebase-functions/params");
const { HttpsError, onCall } = require("firebase-functions/v2/https");

initializeApp();
const db = getFirestore();
const smtpHost = defineSecret("SMTP_HOST");
const smtpPort = defineSecret("SMTP_PORT");
const smtpUser = defineSecret("SMTP_USER");
const smtpPassword = defineSecret("SMTP_PASSWORD");
const smtpFrom = defineSecret("SMTP_FROM");
const otpHmacSecret = defineSecret("OTP_HMAC_SECRET");
const codeCollection = db.collection("_privateEmailSignupCodes");
const rateCollection = db.collection("_privateSignupRateLimits");
const REGION = "us-central1";
const CODE_LIFETIME_MS = 10 * 60 * 1000;
const TICKET_LIFETIME_MS = 10 * 60 * 1000;
const MIN_RESEND_MS = 60 * 1000;
const MAX_DAILY_EMAILS = 8;
const MAX_CODE_ATTEMPTS = 5;

function normalizeEmail(value) {
  return typeof value === "string" ? value.trim().toLowerCase() : "";
}

function validEmail(email) {
  return email.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function digest(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

function keyedDigest(value) {
  return crypto.createHmac("sha256", otpHmacSecret.value()).update(value).digest("hex");
}

function matchesDigest(expected, actual) {
  if (typeof expected !== "string" || typeof actual !== "string") return false;
  const expectedBytes = Buffer.from(expected, "hex");
  const actualBytes = Buffer.from(actual, "hex");
  return expectedBytes.length === actualBytes.length
    && expectedBytes.length > 0
    && crypto.timingSafeEqual(expectedBytes, actualBytes);
}

function randomCode() {
  return crypto.randomInt(0, 1000000).toString().padStart(6, "0");
}

function requireAppCheck(request) {
  if (!request.app) throw new HttpsError("failed-precondition", "App verification failed.");
}

function requireEmail(value) {
  const email = normalizeEmail(value);
  if (!validEmail(email)) throw new HttpsError("invalid-argument", "Enter a valid email address.");
  return email;
}

async function enforceSendLimits(email, request) {
  const now = Date.now();
  const emailRef = rateCollection.doc("email_" + digest(email));
  const ip = request.rawRequest && request.rawRequest.ip ? request.rawRequest.ip : "unknown";
  const ipRef = rateCollection.doc("ip_" + digest(ip));
  await db.runTransaction(async (transaction) => {
    const snapshots = await transaction.getAll(emailRef, ipRef);
    const emailData = snapshots[0].exists ? snapshots[0].data() : {};
    const ipData = snapshots[1].exists ? snapshots[1].data() : {};
    const lastSentAt = emailData.lastSentAtMillis || 0;
    const ipLastSentAt = ipData.lastSentAtMillis || 0;
    const today = new Date(now).toISOString().slice(0, 10);
    const ipCount = ipData.day === today ? (ipData.count || 0) : 0;
    if (now - lastSentAt < MIN_RESEND_MS || now - ipLastSentAt < 5 * 1000) {
      throw new HttpsError("resource-exhausted", "Wait before requesting another code.");
    }
    if (ipCount >= MAX_DAILY_EMAILS) {
      throw new HttpsError("resource-exhausted", "Too many verification emails were requested from this connection today.");
    }
    transaction.set(emailRef, { lastSentAtMillis: now }, { merge: true });
    transaction.set(ipRef, {
      day: today,
      count: ipCount + 1,
      lastSentAtMillis: now,
      expiresAt: Timestamp.fromMillis(now + 3 * 24 * 60 * 60 * 1000)
    }, { merge: true });
  });
}

async function sendCode(email, code) {
  const port = Number(smtpPort.value());
  if (!Number.isFinite(port) || port < 1 || port > 65535) {
    throw new HttpsError("failed-precondition", "Email delivery is not configured.");
  }
  const transporter = nodemailer.createTransport({
    host: smtpHost.value(),
    port,
    secure: port === 465,
    auth: { user: smtpUser.value(), pass: smtpPassword.value() }
  });
  try {
    await transporter.sendMail({
      from: smtpFrom.value(),
      to: email,
      subject: "Your Rannaghor verification code",
      text: `Your Rannaghor verification code is ${code}. It expires in 10 minutes. If you did not request this, ignore this email.`,
      html: `<p>Your Rannaghor verification code is:</p><p style="font-size:28px;font-weight:bold;letter-spacing:8px">${code}</p><p>This code expires in 10 minutes. If you did not request this, ignore this email.</p>`
    });
  } catch (error) {
    console.error("Signup email delivery failed", error && error.message);
    throw new HttpsError("unavailable", "The verification email could not be sent. Try again later.");
  }
}

exports.requestEmailCode = onCall({
  region: REGION,
  enforceAppCheck: true,
  secrets: [smtpHost, smtpPort, smtpUser, smtpPassword, smtpFrom, otpHmacSecret]
}, async (request) => {
  requireAppCheck(request);
  const email = requireEmail(request.data && request.data.email);
  await enforceSendLimits(email, request);

  // Keep the response identical for existing and unused addresses to limit enumeration.
  try {
    await getAuth().getUserByEmail(email);
    return { sent: true };
  } catch (error) {
    if (error.code !== "auth/user-not-found") throw error;
  }

  const code = randomCode();
  const ref = codeCollection.doc(digest(email));
  const now = Date.now();
  await ref.set({
    email,
    codeDigest: keyedDigest(`${email}:${code}`),
    attempts: 0,
    verified: false,
    ticketDigest: null,
    expiresAt: Timestamp.fromMillis(now + CODE_LIFETIME_MS),
    ticketExpiresAt: null,
    createdAt: FieldValue.serverTimestamp()
  });
  await sendCode(email, code);
  return { sent: true };
});

exports.verifyEmailCode = onCall({ region: REGION, enforceAppCheck: true, secrets: [otpHmacSecret] }, async (request) => {
  requireAppCheck(request);
  const email = requireEmail(request.data && request.data.email);
  const code = request.data && request.data.code;
  if (typeof code !== "string" || !/^\d{6}$/.test(code)) {
    throw new HttpsError("invalid-argument", "Enter the six-digit code.");
  }
  const ref = codeCollection.doc(digest(email));
  const ticket = crypto.randomBytes(32).toString("hex");
  const now = Date.now();
  const result = await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    if (!snapshot.exists) return { error: "invalid" };
    const data = snapshot.data();
    if (!data.expiresAt || data.expiresAt.toMillis() <= now || data.attempts >= MAX_CODE_ATTEMPTS) {
      transaction.delete(ref);
      return { error: "expired" };
    }
    if (!matchesDigest(data.codeDigest, keyedDigest(`${email}:${code}`))) {
      const attempts = (data.attempts || 0) + 1;
      if (attempts >= MAX_CODE_ATTEMPTS) transaction.delete(ref);
      else transaction.update(ref, { attempts });
      return { error: "invalid" };
    }
    transaction.update(ref, {
      verified: true,
      codeDigest: null,
      ticketDigest: keyedDigest(`${email}:${ticket}`),
      ticketExpiresAt: Timestamp.fromMillis(now + TICKET_LIFETIME_MS)
    });
    return { ticket };
  });
  if (result.error) {
    throw new HttpsError("permission-denied", result.error === "expired" ? "The code expired. Request a new one." : "The code is incorrect.");
  }
  return { verificationTicket: result.ticket };
});

exports.completeEmailSignup = onCall({
  region: REGION,
  enforceAppCheck: true,
  secrets: [otpHmacSecret]
}, async (request) => {
  requireAppCheck(request);
  const email = requireEmail(request.data && request.data.email);
  const password = request.data && request.data.password;
  const ticket = request.data && request.data.verificationTicket;
  if (typeof password !== "string" || password.length < 6) {
    throw new HttpsError("invalid-argument", "Use a password with at least six characters.");
  }
  if (typeof ticket !== "string" || ticket.length < 32) {
    throw new HttpsError("permission-denied", "Verify the email code before creating an account.");
  }

  const ref = codeCollection.doc(digest(email));
  const now = Date.now();
  const authorized = await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    if (!snapshot.exists) return false;
    const data = snapshot.data();
    if (!data.verified || !data.ticketExpiresAt || data.ticketExpiresAt.toMillis() <= now
      || !matchesDigest(data.ticketDigest, keyedDigest(`${email}:${ticket}`))) return false;
    transaction.delete(ref);
    return true;
  });
  if (!authorized) throw new HttpsError("permission-denied", "The verification session expired. Verify your email again.");

  let user;
  try {
    user = await getAuth().createUser({ email, password, emailVerified: true });
  } catch (error) {
    if (error.code === "auth/email-already-exists") {
      throw new HttpsError("already-exists", "An account already uses this email. Log in instead.");
    }
    throw new HttpsError("internal", "The account could not be created.");
  }
  const customToken = await getAuth().createCustomToken(user.uid);
  return { customToken };
});
