import { useEffect, useState } from 'react';
import type { Toast, ToastType } from '../hooks/useToast';

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
