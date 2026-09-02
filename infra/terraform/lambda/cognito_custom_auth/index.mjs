import { randomInt, timingSafeEqual } from "node:crypto";
import { PublishCommand, SNSClient } from "@aws-sdk/client-sns";

const sns = new SNSClient({});
const otpLength = Number(process.env.OTP_LENGTH ?? "6");
const maxAttempts = Number(process.env.OTP_MAX_ATTEMPTS ?? "5");
const ttlMinutes = Number(process.env.OTP_TTL_MINUTES ?? "5");
const messageTemplate = process.env.OTP_MESSAGE_TEMPLATE ?? "Your NetworkPeer verification code is {code}.";

function isE164(value) {
  return typeof value === "string" && /^\+[1-9]\d{1,14}$/.test(value);
}

function generateOtp() {
  return randomInt(0, 10 ** otpLength).toString().padStart(otpLength, "0");
}

function sameValue(expected, actual) {
  if (typeof expected !== "string" || typeof actual !== "string") return false;
  const expectedBuffer = Buffer.from(expected);
  const actualBuffer = Buffer.from(actual);
  return expectedBuffer.length === actualBuffer.length && timingSafeEqual(expectedBuffer, actualBuffer);
}

function otpMessage(otp) {
  return messageTemplate
    .replaceAll("{code}", otp)
    .replaceAll("{minutes}", String(ttlMinutes));
}

async function sendOtp(phoneNumber, otp) {
  const messageAttributes = {
    "AWS.SNS.SMS.SMSType": { DataType: "String", StringValue: "Transactional" },
  };
  if (process.env.OTP_SNS_SENDER_ID) {
    messageAttributes["AWS.SNS.SMS.SenderID"] = {
      DataType: "String",
      StringValue: process.env.OTP_SNS_SENDER_ID,
    };
  }
  if (process.env.OTP_SNS_ORIGINATION_NUMBER) {
    messageAttributes["AWS.SNS.SMS.OriginationNumber"] = {
      DataType: "String",
      StringValue: process.env.OTP_SNS_ORIGINATION_NUMBER,
    };
  }
  await sns.send(new PublishCommand({
    PhoneNumber: phoneNumber,
    Message: otpMessage(otp),
    MessageAttributes: messageAttributes,
  }));
}

function defineChallenge(event) {
  const challenges = event.request.session ?? [];
  const customChallenges = challenges.filter((challenge) => challenge.challengeName === "CUSTOM_CHALLENGE");
  const lastChallenge = customChallenges.at(-1);

  if (lastChallenge?.challengeResult === true) {
    event.response.issueTokens = true;
    event.response.failAuthentication = false;
    return event;
  }

  if (customChallenges.length >= maxAttempts) {
    event.response.issueTokens = false;
    event.response.failAuthentication = true;
    return event;
  }

  event.response.issueTokens = false;
  event.response.failAuthentication = false;
  event.response.challengeName = "CUSTOM_CHALLENGE";
  return event;
}

async function createChallenge(event) {
  const phoneNumber = event.request.userAttributes?.phone_number;
  if (!isE164(phoneNumber)) throw new Error("Cognito user does not have a valid E.164 phone number");

  const otp = generateOtp();
  await sendOtp(phoneNumber, otp);
  event.response.publicChallengeParameters = {
    delivery: "sms",
    otp_length: String(otpLength),
  };
  event.response.privateChallengeParameters = { answer: otp };
  event.response.challengeMetadata = "NETWORKPEER_OTP";
  return event;
}

function verifyChallenge(event) {
  event.response.answerCorrect = sameValue(
    event.request.privateChallengeParameters?.answer,
    event.request.challengeAnswer?.trim(),
  );
  return event;
}

export const handler = async (event) => {
  switch (event.triggerSource) {
    case "DefineAuthChallenge_Authentication":
      return defineChallenge(event);
    case "CreateAuthChallenge_Authentication":
      return createChallenge(event);
    case "VerifyAuthChallengeResponse_Authentication":
      return verifyChallenge(event);
    default:
      throw new Error(`Unsupported Cognito trigger source: ${event.triggerSource}`);
  }
};
