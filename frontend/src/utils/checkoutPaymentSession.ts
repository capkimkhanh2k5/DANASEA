import type { PaymentProviderType } from "../types/order";

interface PendingOrder {
  id: string;
  provider: PaymentProviderType;
}

interface PurchasedItem {
  id: string;
  quantity: number;
}

const unavailableStorage = new Map<string, string | null>();

function read(key: string) {
  if (unavailableStorage.has(key)) return unavailableStorage.get(key) ?? null;
  try {
    return sessionStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string | null) {
  try {
    if (value === null) sessionStorage.removeItem(key);
    else sessionStorage.setItem(key, value);
    unavailableStorage.delete(key);
  } catch {
    unavailableStorage.set(key, value);
  }
}

export function getPendingOrder(key: string): PendingOrder | null {
  const saved = read(key);
  try {
    const order = saved ? JSON.parse(saved) as PendingOrder | null : null;
    return typeof order?.id === "string" && typeof order.provider === "string" ? order : null;
  } catch {
    return null;
  }
}

export function savePendingOrder(key: string, order: PendingOrder | null) {
  write(key, order ? JSON.stringify(order) : null);
}

export function getPaymentIntentKey(orderId: string, provider: PaymentProviderType) {
  const storageKey = `danasea_checkout_intent:${orderId}:${provider}`;
  const pending = read(storageKey);
  if (pending) return pending;
  const key = globalThis.crypto?.randomUUID?.() ?? `pay-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
  write(storageKey, key);
  return key;
}

export function clearPaymentIntentKey(orderId: string, provider: PaymentProviderType, key: string) {
  const storageKey = `danasea_checkout_intent:${orderId}:${provider}`;
  if (read(storageKey) === key) write(storageKey, null);
}

export function savePurchasedItems(paymentId: string, items: PurchasedItem[]) {
  write(`danasea_checkout_cart:${paymentId}`, JSON.stringify(items));
}

export function takePurchasedItems(paymentId: string): PurchasedItem[] {
  const key = `danasea_checkout_cart:${paymentId}`;
  const saved = read(key);
  if (!saved) return [];
  write(key, null);
  try {
    const items: unknown = JSON.parse(saved);
    return Array.isArray(items) ? items : [];
  } catch {
    return [];
  }
}
