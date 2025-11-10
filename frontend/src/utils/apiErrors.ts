import { ResponseError } from '../api/runtime';

export async function resolveApiErrorMessage(error: unknown, fallback = 'Не удалось выполнить запрос'): Promise<string> {
  if (error instanceof ResponseError) {
    try {
      const payload = await error.response.clone().json();
      if (payload && typeof payload === 'object') {
        const maybeMessage = (payload as any).message ?? (payload as any).error;
        if (typeof maybeMessage === 'string' && maybeMessage.trim().length > 0) {
          return maybeMessage;
        }
      }
      if (typeof payload === 'string' && payload.trim().length > 0) {
        return payload;
      }
    } catch (parseError) {
      try {
        const text = await error.response.clone().text();
        if (text && text.trim().length > 0) {
          return text;
        }
      } catch (_) {
        // ignore
      }
    }
  }

  if (error instanceof Error) {
    return error.message || fallback;
  }

  return fallback;
}
