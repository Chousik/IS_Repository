import { ReactNode } from 'react';

interface ModalProps {
  open: boolean;
  title?: string;
  children: ReactNode;
  footer?: ReactNode;
  onClose: () => void;
}

const Modal = ({ open, title, children, footer, onClose }: ModalProps) => {
  if (!open) return null;
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-body" onClick={(event) => event.stopPropagation()}>
        {title && <h3>{title}</h3>}
        <div>{children}</div>
        {footer && <div className="modal-footer">{footer}</div>}
      </div>
    </div>
  );
};

export default Modal;
