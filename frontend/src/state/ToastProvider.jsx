import React, { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react';
import { CheckCircle2, AlertTriangle, Info, X } from 'lucide-react';

const ToastContext = createContext(null);

const ICONS = {
  success: CheckCircle2,
  error: AlertTriangle,
  info: Info,
};

const LIFETIME = { success: 3600, info: 3600, error: 6500 };

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const timers = useRef(new Map());
  const seq = useRef(0);

  const dismiss = useCallback((id) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
    const timer = timers.current.get(id);
    if (timer) {
      clearTimeout(timer);
      timers.current.delete(id);
    }
  }, []);

  const push = useCallback(
    (tone, message) => {
      if (!message) return;
      const id = ++seq.current;
      setToasts((prev) => [...prev.slice(-3), { id, tone, message: String(message) }]);
      timers.current.set(id, setTimeout(() => dismiss(id), LIFETIME[tone] ?? 4000));
    },
    [dismiss]
  );

  const toast = useMemo(
    () => ({
      success: (m) => push('success', m),
      error: (m) => push('error', m),
      info: (m) => push('info', m),
    }),
    [push]
  );

  return (
    <ToastContext.Provider value={toast}>
      {children}
      <div className="toast-viewport" role="status" aria-live="polite">
        {toasts.map(({ id, tone, message }) => {
          const Icon = ICONS[tone] ?? Info;
          return (
            <div key={id} className={`toast toast-${tone}`}>
              <Icon size={15} />
              <span className="toast-message">{message}</span>
              <button className="toast-close" onClick={() => dismiss(id)} aria-label="Dismiss">
                <X size={13} />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used inside <ToastProvider>');
  return ctx;
}
