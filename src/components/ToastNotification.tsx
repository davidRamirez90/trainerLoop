import { useEffect, useState, useCallback } from 'react';

export type ToastType = 'success' | 'warning' | 'info' | 'error';

export interface Toast {
  id: string;
  message: string;
  type: ToastType;
  duration?: number;
}

interface ToastNotificationProps {
  toasts: Toast[];
  onRemove: (id: string) => void;
}

const getToastStyles = (type: ToastType): { icon: string; className: string } => {
  switch (type) {
    case 'success':
      return { icon: '✓', className: 'toast-success' };
    case 'warning':
      return { icon: '⚠', className: 'toast-warning' };
    case 'error':
      return { icon: '✕', className: 'toast-error' };
    case 'info':
    default:
      return { icon: 'ℹ', className: 'toast-info' };
  }
};

export const ToastNotification = ({ toasts, onRemove }: ToastNotificationProps) => {
  return (
    <div className="toast-container">
      {toasts.map((toast) => (
        <ToastItem key={toast.id} toast={toast} onRemove={onRemove} />
      ))}
    </div>
  );
};

const ToastItem = ({ 
  toast, 
  onRemove 
}: { 
  toast: Toast; 
  onRemove: (id: string) => void;
}) => {
  const [isExiting, setIsExiting] = useState(false);
  const { icon, className } = getToastStyles(toast.type);
  const duration = toast.duration ?? 4500;

  useEffect(() => {
    const timer = setTimeout(() => {
      setIsExiting(true);
      setTimeout(() => onRemove(toast.id), 300); // Wait for exit animation
    }, duration);

    return () => clearTimeout(timer);
  }, [toast.id, duration, onRemove]);

  return (
    <div className={`toast-item ${className} ${isExiting ? 'toast-exit' : 'toast-enter'}`}>
      <span className="toast-icon">{icon}</span>
      <span className="toast-message">{toast.message}</span>
      <button 
        className="toast-close" 
        onClick={() => {
          setIsExiting(true);
          setTimeout(() => onRemove(toast.id), 300);
        }}
        aria-label="Close notification"
      >
        ×
      </button>
    </div>
  );
};

// Hook for managing toasts
export const useToast = () => {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const addToast = useCallback((message: string, type: ToastType = 'info', duration?: number) => {
    const id = `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    setToasts((prev) => [...prev, { id, message, type, duration }]);
    return id;
  }, []);

  const removeToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const success = useCallback((message: string, duration?: number) => {
    return addToast(message, 'success', duration);
  }, [addToast]);

  const warning = useCallback((message: string, duration?: number) => {
    return addToast(message, 'warning', duration);
  }, [addToast]);

  const error = useCallback((message: string, duration?: number) => {
    return addToast(message, 'error', duration);
  }, [addToast]);

  const info = useCallback((message: string, duration?: number) => {
    return addToast(message, 'info', duration);
  }, [addToast]);

  return {
    toasts,
    removeToast,
    success,
    warning,
    error,
    info,
  };
};
