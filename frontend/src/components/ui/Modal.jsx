import React, { useEffect, useRef } from 'react';
import { X } from 'lucide-react';
import { IconButton } from './Button';

/**
 * Centred dialog. Closes on Escape and on backdrop click; focus moves inside on
 * open so keyboard users are not stranded behind the overlay.
 */
export function Modal({ title, icon: Icon, onClose, children, footer, wide = false }) {
  const panelRef = useRef(null);

  useEffect(() => {
    const onKeyDown = (e) => {
      if (e.key === 'Escape') {
        e.stopPropagation();
        onClose();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [onClose]);

  useEffect(() => {
    const focusable = panelRef.current?.querySelector(
      'input:not([disabled]), select:not([disabled]), textarea:not([disabled]), button:not([disabled])'
    );
    focusable?.focus();
  }, []);

  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div
        ref={panelRef}
        className={`modal ${wide ? 'modal-lg' : ''}`.trim()}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <div className="modal-header">
          {Icon && <Icon size={16} className="muted" />}
          <span className="modal-title">{title}</span>
          <IconButton icon={X} label="Close" size="xs" onClick={onClose} />
        </div>
        {children}
        {footer && <div className="modal-footer">{footer}</div>}
      </div>
    </div>
  );
}

/** Body + footer for the common "form in a modal" case. */
export function ModalForm({ onSubmit, children, footer }) {
  return (
    <form onSubmit={onSubmit} style={{ display: 'contents' }}>
      <div className="modal-body">{children}</div>
      <div className="modal-footer">{footer}</div>
    </form>
  );
}
