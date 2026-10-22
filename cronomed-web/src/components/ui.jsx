import { useEffect, useId, useRef, useState } from 'react';
import Icon from './Icon.jsx';
import { passwordChecks } from '../lib/validators.js';

export function Button({ variant = 'primary', size, block, loading, children, className = '', type = 'button', ...rest }) {
  const cls = ['btn', `btn-${variant}`, size ? `btn-${size}` : '', block ? 'btn-block' : '', className].filter(Boolean).join(' ');
  return (
    <button type={type} className={cls} disabled={loading || rest.disabled} {...rest}>
      {loading && <span className="spinner" aria-hidden="true" />}
      {children}
    </button>
  );
}

export function Badge({ tone = 'neutral', icon, children }) {
  return (
    <span className={`badge badge-${tone}`}>
      {icon && <Icon name={icon} size={14} strokeWidth={2.4} />}
      {children}
    </span>
  );
}

/** Campo com rotulo SEMPRE visivel (nunca so placeholder - RF14). */
export function Field({ label, hint, error, children, className = '', htmlFor }) {
  return (
    <div className={`field ${className}`.trim()}>
      {label && <label htmlFor={htmlFor}>{label}</label>}
      {children}
      {hint && !error && <span className="field-hint">{hint}</span>}
      {error && (
        <span className="field-error" role="alert">
          {error}
        </span>
      )}
    </div>
  );
}

export function TextField({ label, hint, error, className = '', locked, id, ...input }) {
  const auto = useId();
  const fid = id || auto;
  return (
    <Field label={label} hint={hint} error={error} className={className} htmlFor={fid}>
      <div className="input-wrap">
        <input id={fid} className="input" aria-invalid={error ? 'true' : undefined} {...input} readOnly={locked || input.readOnly} />
        {locked && <Icon name="lock" size={16} className="input-lock" />}
      </div>
    </Field>
  );
}

export function PasswordField({ label, hint, error, id, ...input }) {
  const auto = useId();
  const fid = id || auto;
  const [shown, setShown] = useState(false);
  return (
    <Field label={label} hint={hint} error={error} htmlFor={fid}>
      <div className="input-wrap">
        <input id={fid} className="input" type={shown ? 'text' : 'password'} aria-invalid={error ? 'true' : undefined} {...input} />
        <button type="button" className="input-action" onClick={() => setShown((v) => !v)} aria-label={shown ? 'Ocultar senha' : 'Mostrar senha'} aria-pressed={shown}>
          <Icon name={shown ? 'eyeOff' : 'eye'} size={20} />
        </button>
      </div>
    </Field>
  );
}

export function PasswordChecklist({ password, title = 'Crie uma senha com no mínimo:' }) {
  const checks = passwordChecks(password);
  return (
    <div>
      <p className="muted" style={{ fontSize: '.95rem', marginBottom: 10 }}>{title}</p>
      <ul className="pw-rules" style={{ listStyle: 'none', padding: 0, margin: 0 }}>
        {checks.map((c) => (
          <li key={c.id} className={`pw-rule${c.ok ? ' ok' : ''}`}>
            <span className="dot" aria-hidden="true">{c.ok && <Icon name="check" size={13} strokeWidth={3} />}</span>
            <span>
              {c.label}
              <span className="sr-only">{c.ok ? ' (atendido)' : ' (pendente)'}</span>
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

export function Toggle({ checked, onChange, label, disabled }) {
  return (
    <button type="button" role="switch" aria-checked={checked} aria-label={label} className="toggle" disabled={disabled} onClick={() => onChange(!checked)} />
  );
}

export function Modal({ title, subtitle, onClose, children, size, labelledBy }) {
  const ref = useRef(null);
  const titleId = useId();
  useEffect(() => {
    const previous = document.activeElement;
    const node = ref.current;
    const focusable = () => node.querySelectorAll('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])');
    const first = node.querySelector('input, select, textarea') || focusable()[0];
    if (first) first.focus();
    const onKey = (e) => {
      if (e.key === 'Escape') {
        e.stopPropagation();
        onClose();
      } else if (e.key === 'Tab') {
        const items = Array.from(focusable()).filter((el) => !el.disabled);
        if (items.length === 0) return;
        const firstEl = items[0];
        const lastEl = items[items.length - 1];
        if (e.shiftKey && document.activeElement === firstEl) {
          e.preventDefault();
          lastEl.focus();
        } else if (!e.shiftKey && document.activeElement === lastEl) {
          e.preventDefault();
          firstEl.focus();
        }
      }
    };
    document.addEventListener('keydown', onKey);
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = overflow;
      if (previous && previous.focus) previous.focus();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={`modal ${size || ''}`.trim()} role="dialog" aria-modal="true" aria-labelledby={labelledBy || titleId} ref={ref}>
        {title && (
          <div className="modal-head">
            <div>
              <h2 id={titleId}>{title}</h2>
              {subtitle && <p>{subtitle}</p>}
            </div>
            <button type="button" className="modal-close" onClick={onClose} aria-label="Fechar">
              <Icon name="x" size={22} />
            </button>
          </div>
        )}
        {children}
      </div>
    </div>
  );
}

/** Confirmacao em duas etapas para acoes destrutivas (principio de UX do projeto). */
export function ConfirmDialog({ title, message, confirmLabel = 'Confirmar', danger = true, loading, onConfirm, onCancel }) {
  return (
    <Modal title={title} onClose={onCancel} size="sm">
      <p className="muted" style={{ lineHeight: 1.55 }}>{message}</p>
      <div className="modal-actions">
        <Button variant="outline" onClick={onCancel}>Cancelar</Button>
        <Button variant={danger ? 'danger-solid' : 'primary'} loading={loading} onClick={onConfirm}>{confirmLabel}</Button>
      </div>
    </Modal>
  );
}

export function EmptyState({ icon = 'info', title, children, action }) {
  return (
    <div className="empty card">
      <div className="empty-icon"><Icon name={icon} size={28} /></div>
      <h3>{title}</h3>
      {children && <p>{children}</p>}
      {action}
    </div>
  );
}

export function Skeleton({ height = 120, className = '' }) {
  return <div className={`skeleton ${className}`.trim()} style={{ height }} aria-hidden="true" />;
}

export function ErrorNotice({ error, onRetry }) {
  if (!error) return null;
  return (
    <div className="banner danger" role="alert">
      <Icon name="alert" size={20} />
      <span className="grow">{error.message || 'Algo deu errado. Tente novamente.'}</span>
      {onRetry && (
        <button type="button" className="link-btn" onClick={onRetry}>
          Tentar de novo
        </button>
      )}
    </div>
  );
}

export function FormError({ error }) {
  if (!error) return null;
  return (
    <div className="form-error" role="alert">
      {typeof error === 'string' ? error : error.message}
      {error.fields && error.fields.length > 0 && (
        <ul style={{ margin: '6px 0 0', paddingLeft: 18 }}>
          {error.fields.map((f, i) => (
            <li key={i}>{f.message}</li>
          ))}
        </ul>
      )}
    </div>
  );
}
