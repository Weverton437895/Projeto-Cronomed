import CompartmentIcon from './CompartmentIcon.jsx';
import Icon from './Icon.jsx';

/** Icone do compartimento; sem compartimento conhecido (ex.: convidado), mostra um comprimido neutro. */
export default function MedIcon({ index, size = 44 }) {
  if (index) return <CompartmentIcon index={index} size={size} />;
  return (
    <span className="avatar" style={{ width: size, height: size }} aria-hidden="true">
      <Icon name="pill" size={Math.round(size * 0.5)} />
    </span>
  );
}
