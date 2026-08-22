export function normalizePhoneNumber(rawValue: string | null | undefined): string;
export function toE164Phone(
  countryCode: string | number | null | undefined,
  rawValue: string | null | undefined,
): string | null;
export function isPhoneNumberValid(rawValue: string | null | undefined): boolean;
export function formatPhoneNumber(rawValue: string | null | undefined, countryCode: string): string;
export function isOtpCodeValid(value: string | null | undefined, length?: number): boolean;
export function getDemoOtp(): string;
