export type EntityChange = {
  entity: string;
  action: string;
  data: unknown;
};

export function subscribeToEntityChanges(callback: (change: EntityChange) => void): () => void {
  let active = true;
  let socket: WebSocket | null = null;
  let reconnectTimeout: ReturnType<typeof setTimeout> | null = null;

  const resolveBase = () => {
    return 'ws://localhost:8041';
  };

  const connect = () => {
    if (!active) {
      return;
    }
    const base = resolveBase();
    const url = `${base}/ws/entity`;

    try {
      socket = new WebSocket(url);
    } catch (error) {
      scheduleReconnect();
      return;
    }

    socket.onopen = () => {
      if (process.env.NODE_ENV === 'development') {
        console.debug('[WS] подключено');
      }
    };

    socket.onmessage = (event) => {
      try {
        const parsed: EntityChange = JSON.parse(event.data);
        if (process.env.NODE_ENV === 'development') {
          console.debug('[WS] message', parsed);
        }
        callback(parsed);
      } catch (error) {
        console.warn('Не удалось разобрать событие WebSocket', error, event.data);
      }
    };

    socket.onclose = () => {
      if (!active) {
        return;
      }
      scheduleReconnect();
    };

    socket.onerror = () => {
      if (!active) {
        return;
      }
      scheduleReconnect();
    };
  };

  const scheduleReconnect = () => {
    if (reconnectTimeout) {
      return;
    }
    reconnectTimeout = setTimeout(() => {
      reconnectTimeout = null;
      connect();
    }, 3000);
  };

  connect();

  return () => {
    active = false;
    if (reconnectTimeout) {
      clearTimeout(reconnectTimeout);
    }
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.close();
    }
  };
}
